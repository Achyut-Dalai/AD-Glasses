@preconcurrency import AVFoundation
import Foundation
import LiveKitWakeWord

enum LiveKitPhoneWakeWordError: LocalizedError {
    case modelMissing
    case calibrationMissing
    case invalidManifest
    case invalidModel
    case storageUnavailable
    case microphonePermissionDenied
    case failedToCreateAudioInput

    var errorDescription: String? {
        switch self {
        case .modelMissing:
            return "The Hey A D voice-activation model is not installed. Import a LiveKit .onnx model in Settings, or use a build that bundles one."
        case .calibrationMissing:
            return "The Hey A D voice-activation model has not been calibrated yet."
        case .invalidManifest:
            return "The bundled voice-activation configuration is invalid."
        case .invalidModel:
            return "The wake-word classifier could not be loaded."
        case .storageUnavailable:
            return "The wake-word classifier could not be stored on this iPhone."
        case .microphonePermissionDenied:
            return "Microphone permission is required for phone voice activation."
        case .failedToCreateAudioInput:
            return "The microphone audio stream could not be prepared for wake-word detection."
        }
    }
}

/// Local wake-word detection backed by LiveKit WakeWord + ONNX Runtime/CoreML.
///
/// AD Glasses drives `WakeWordModel` directly instead of using LiveKit's convenience listener.
/// That keeps the app's foreground-established recording lease alive across the wake-word →
/// Apple Speech → spoken-answer handoff, rather than allowing a library-owned listener to
/// deactivate the shared `AVAudioSession` between stages.
@MainActor
final class LiveKitPhoneWakeWordService: PhoneWakeWordDetecting {
    private static let defaultPhrase = "Hey A D"
    private static let manifestName = "manifest"
    private static let resourceDirectory = "WakeWords"

    private let defaults: UserDefaults
    private let fileManager: FileManager
    private let bundle: Bundle

    private var audioEngine: AVAudioEngine?
    private var inferencePipeline: LiveKitWakeWordInferencePipeline?
    private var cachedExecutor: LiveKitWakeWordModelExecutor?
    private var cachedModelKey: WakeWordModelCacheKey?
    private var lifecycleID = UUID()

    #if DEBUG || AD_PERSONAL_TEAM_BUILD
    private let debugPhraseKey = "livekit.wakePhrase.v1"
    private let debugThresholdKey = "livekit.wakeThreshold.v1"
    #endif

    init(
        defaults: UserDefaults = .standard,
        fileManager: FileManager = .default,
        bundle: Bundle = .main
    ) {
        self.defaults = defaults
        self.fileManager = fileManager
        self.bundle = bundle
    }

    var phrase: String {
        #if DEBUG || AD_PERSONAL_TEAM_BUILD
        if hasDebugOverride {
            return normalized(defaults.string(forKey: debugPhraseKey) ?? Self.defaultPhrase)
        }
        #endif
        return bundledManifest?.phrase ?? Self.defaultPhrase
    }

    var configurationState: PhoneWakeWordConfigurationState {
        do {
            _ = try classifierConfiguration()
            return .ready
        } catch LiveKitPhoneWakeWordError.modelMissing {
            return .missingModel
        } catch let error as LocalizedError {
            return .unavailable(error.errorDescription ?? "Phone voice activation is unavailable.")
        } catch {
            return .unavailable("Phone voice activation is unavailable.")
        }
    }

    func start(onDetection: @escaping @MainActor () -> Void) async throws {
        stop()
        let runID = UUID()
        lifecycleID = runID

        let configuration = try classifierConfiguration()
        guard await SpeechPermissions.requestMicrophone() else {
            throw LiveKitPhoneWakeWordError.microphonePermissionDenied
        }
        try ensureCurrent(runID)

        let executor: LiveKitWakeWordModelExecutor
        if cachedModelKey == configuration.cacheKey, let cachedExecutor {
            executor = cachedExecutor
        } else {
            do {
                executor = try await Task.detached(priority: .userInitiated) {
                    try LiveKitWakeWordModelExecutor(configuration: configuration)
                }.value
            } catch is CancellationError {
                throw CancellationError()
            } catch {
                throw LiveKitPhoneWakeWordError.invalidModel
            }
            try ensureCurrent(runID)
            cachedModelKey = configuration.cacheKey
            cachedExecutor = executor
        }

        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(
                .playAndRecord,
                mode: .measurement,
                options: [.defaultToSpeaker, .allowBluetoothHFP]
            )
            try session.setActive(true)
        } catch {
            failCurrentRun(runID)
            throw error
        }

        let engine = AVAudioEngine()
        let inputNode = engine.inputNode
        let hardwareFormat = inputNode.inputFormat(forBus: 0)
        guard hardwareFormat.sampleRate > 0 else {
            failCurrentRun(runID)
            throw LiveKitPhoneWakeWordError.failedToCreateAudioInput
        }

        let modelRate = Double(WakeWordModel.modelSampleRate)
        guard let targetFormat = AVAudioFormat(
            commonFormat: .pcmFormatInt16,
            sampleRate: modelRate,
            channels: 1,
            interleaved: true
        ),
        let converter = AVAudioConverter(from: hardwareFormat, to: targetFormat) else {
            failCurrentRun(runID)
            throw LiveKitPhoneWakeWordError.failedToCreateAudioInput
        }

        let pipeline = LiveKitWakeWordInferencePipeline(
            executor: executor,
            sampleRate: Int(WakeWordModel.modelSampleRate),
            threshold: configuration.threshold,
            debounce: configuration.debounce,
            windowSeconds: 2.0,
            onDetection: onDetection
        )

        inputNode.installTap(
            onBus: 0,
            bufferSize: 1_280,
            format: hardwareFormat
        ) { buffer, _ in
            pipeline.ingest(
                buffer: buffer,
                converter: converter,
                targetFormat: targetFormat
            )
        }

        engine.prepare()
        do {
            try engine.start()
            try ensureCurrent(runID)
            audioEngine = engine
            inferencePipeline = pipeline
        } catch {
            pipeline.stop()
            inputNode.removeTap(onBus: 0)
            engine.stop()
            failCurrentRun(runID)
            throw error
        }
    }

    func stop() {
        lifecycleID = UUID()
        inferencePipeline?.stop()
        inferencePipeline = nil
        if let audioEngine {
            audioEngine.inputNode.removeTap(onBus: 0)
            audioEngine.stop()
        }
        audioEngine = nil
        deactivateAudioSessionIfAllowed()
    }

    #if DEBUG || AD_PERSONAL_TEAM_BUILD
    func importModel(from sourceURL: URL, phrase: String) throws {
        guard sourceURL.pathExtension.lowercased() == "onnx" else {
            throw LiveKitPhoneWakeWordError.invalidModel
        }
        stop()
        try createModelDirectory()

        let accessed = sourceURL.startAccessingSecurityScopedResource()
        defer { if accessed { sourceURL.stopAccessingSecurityScopedResource() } }

        let temporaryURL = modelDirectoryURL
            .appendingPathComponent(UUID().uuidString)
            .appendingPathExtension("onnx")
        do {
            try fileManager.copyItem(at: sourceURL, to: temporaryURL)
            let size = try temporaryURL.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
            guard size > 0 else {
                try? fileManager.removeItem(at: temporaryURL)
                throw LiveKitPhoneWakeWordError.invalidModel
            }
            try fileManager.setAttributes(
                [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication],
                ofItemAtPath: temporaryURL.path
            )

            if fileManager.fileExists(atPath: debugModelURL.path) {
                _ = try fileManager.replaceItemAt(debugModelURL, withItemAt: temporaryURL)
            } else {
                try fileManager.moveItem(at: temporaryURL, to: debugModelURL)
            }
            defaults.set(normalized(phrase), forKey: debugPhraseKey)
            cachedExecutor = nil
            cachedModelKey = nil
        } catch let error as LiveKitPhoneWakeWordError {
            throw error
        } catch {
            try? fileManager.removeItem(at: temporaryURL)
            throw LiveKitPhoneWakeWordError.storageUnavailable
        }
    }

    func setDebugThreshold(_ threshold: Float) {
        defaults.set(min(max(threshold, 0.01), 0.99), forKey: debugThresholdKey)
    }
    #endif

    private var bundledManifest: BundledWakeWordManifest? {
        guard let url = bundle.url(
            forResource: Self.manifestName,
            withExtension: "json",
            subdirectory: Self.resourceDirectory
        ), let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(BundledWakeWordManifest.self, from: data)
    }

    private func classifierConfiguration() throws -> WakeWordClassifierConfiguration {
        #if DEBUG || AD_PERSONAL_TEAM_BUILD
        if hasDebugOverride {
            let threshold = defaults.object(forKey: debugThresholdKey) as? NSNumber
            return try makeConfiguration(
                url: debugModelURL,
                modelName: debugModelURL.deletingPathExtension().lastPathComponent,
                threshold: threshold?.floatValue ?? 0.75,
                debounce: 2.0
            )
        }
        #endif

        guard let manifestURL = bundle.url(
            forResource: Self.manifestName,
            withExtension: "json",
            subdirectory: Self.resourceDirectory
        ), let data = try? Data(contentsOf: manifestURL) else {
            throw LiveKitPhoneWakeWordError.modelMissing
        }

        let manifest: BundledWakeWordManifest
        do {
            manifest = try JSONDecoder().decode(BundledWakeWordManifest.self, from: data)
        } catch {
            throw LiveKitPhoneWakeWordError.invalidManifest
        }

        guard manifest.modelFile == URL(fileURLWithPath: manifest.modelFile).lastPathComponent,
              manifest.modelFile.hasSuffix(".onnx"),
              !manifest.modelName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw LiveKitPhoneWakeWordError.invalidManifest
        }
        guard let resourceRoot = bundle.resourceURL else {
            throw LiveKitPhoneWakeWordError.modelMissing
        }
        let modelURL = resourceRoot
            .appendingPathComponent(Self.resourceDirectory, isDirectory: true)
            .appendingPathComponent(manifest.modelFile, isDirectory: false)
        guard fileManager.fileExists(atPath: modelURL.path) else {
            throw LiveKitPhoneWakeWordError.modelMissing
        }
        guard let threshold = manifest.threshold else {
            throw LiveKitPhoneWakeWordError.calibrationMissing
        }
        return try makeConfiguration(
            url: modelURL,
            modelName: manifest.modelName,
            threshold: threshold,
            debounce: manifest.debounceSeconds
        )
    }

    private func makeConfiguration(
        url: URL,
        modelName: String,
        threshold: Float,
        debounce: TimeInterval
    ) throws -> WakeWordClassifierConfiguration {
        guard fileManager.fileExists(atPath: url.path) else {
            throw LiveKitPhoneWakeWordError.modelMissing
        }
        guard threshold > 0, threshold < 1,
              debounce >= 0.5, debounce <= 10 else {
            throw LiveKitPhoneWakeWordError.invalidManifest
        }
        let values = try? url.resourceValues(forKeys: [.fileSizeKey, .contentModificationDateKey])
        guard let size = values?.fileSize, size > 0 else {
            throw LiveKitPhoneWakeWordError.invalidModel
        }
        let key = WakeWordModelCacheKey(
            url: url,
            fileSize: size,
            modificationDate: values?.contentModificationDate,
            modelName: modelName
        )
        return WakeWordClassifierConfiguration(
            url: url,
            modelName: modelName,
            threshold: threshold,
            debounce: debounce,
            cacheKey: key
        )
    }

    private func ensureCurrent(_ runID: UUID) throws {
        guard lifecycleID == runID else { throw CancellationError() }
        try Task.checkCancellation()
    }

    private func failCurrentRun(_ runID: UUID) {
        guard lifecycleID == runID else { return }
        lifecycleID = UUID()
        deactivateAudioSessionIfAllowed()
    }

    private func normalized(_ phrase: String) -> String {
        let trimmed = phrase.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? Self.defaultPhrase : trimmed
    }

    private func deactivateAudioSessionIfAllowed() {
        guard !VoiceAudioSessionContinuity.shared.keepsRecordingSessionActive else { return }
        try? AVAudioSession.sharedInstance().setActive(
            false,
            options: .notifyOthersOnDeactivation
        )
    }

    #if DEBUG || AD_PERSONAL_TEAM_BUILD
    private var hasDebugOverride: Bool {
        fileManager.fileExists(atPath: debugModelURL.path)
    }

    private var debugModelURL: URL {
        modelDirectoryURL.appendingPathComponent("phone-wake-word.onnx", isDirectory: false)
    }

    private var modelDirectoryURL: URL {
        let root = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? fileManager.temporaryDirectory
        return root.appendingPathComponent("ADGlasses/WakeWords", isDirectory: true)
    }

    private func createModelDirectory() throws {
        do {
            try fileManager.createDirectory(
                at: modelDirectoryURL,
                withIntermediateDirectories: true,
                attributes: [
                    .protectionKey: FileProtectionType.completeUntilFirstUserAuthentication
                ]
            )
        } catch {
            throw LiveKitPhoneWakeWordError.storageUnavailable
        }
    }
    #endif
}

private struct BundledWakeWordManifest: Decodable {
    let phrase: String
    let modelFile: String
    let modelName: String
    let threshold: Float?
    let debounceSeconds: TimeInterval
}

private struct WakeWordModelCacheKey: Hashable, Sendable {
    let url: URL
    let fileSize: Int
    let modificationDate: Date?
    let modelName: String
}

private struct WakeWordClassifierConfiguration: Sendable {
    let url: URL
    let modelName: String
    let threshold: Float
    let debounce: TimeInterval
    let cacheKey: WakeWordModelCacheKey
}

/// Serializes all predictions for a cached `WakeWordModel`. A stopped pipeline can still have one
/// ONNX call completing; sharing this executor prevents a newly started pipeline from overlapping
/// that call on the same non-reentrant model instance.
private final class LiveKitWakeWordModelExecutor: @unchecked Sendable {
    private let model: WakeWordModel
    private let modelName: String
    private let queue = DispatchQueue(
        label: "com.achyutdalai.ADGlasses.livekitWakeWord.predict",
        qos: .userInitiated
    )

    init(configuration: WakeWordClassifierConfiguration) throws {
        model = try WakeWordModel(
            models: [configuration.url],
            sampleRate: WakeWordModel.modelSampleRate,
            executionProvider: .coreML
        )
        modelName = configuration.modelName
    }

    func predict(
        _ snapshot: [Int16],
        completion: @escaping @Sendable (Float?) -> Void
    ) {
        queue.async { [model, modelName] in
            let scores = try? model.predict(snapshot)
            completion(scores?[modelName])
        }
    }
}

private final class LiveKitWakeWordInferencePipeline: @unchecked Sendable {
    private let executor: LiveKitWakeWordModelExecutor
    private let threshold: Float
    private let debounce: TimeInterval
    private let ringLock = NSLock()
    private let onDetection: @MainActor () -> Void

    private var ring: [Int16]
    private var writeIndex = 0
    private var samplesWritten = 0
    private var lastPredictAt: CFAbsoluteTime = 0
    private var predictInFlight = false
    private var lastDetectionAt: CFAbsoluteTime?
    private var generation = UUID()

    /// LiveKit's Python listener ingests 80 ms frames. Matching that cadence substantially reduces
    /// continuous copies/inference attempts compared with a 20 ms loop without perceptible wake
    /// latency, especially on iPhone 13-class hardware.
    private let predictInterval: CFAbsoluteTime = 0.08

    init(
        executor: LiveKitWakeWordModelExecutor,
        sampleRate: Int,
        threshold: Float,
        debounce: TimeInterval,
        windowSeconds: Double,
        onDetection: @escaping @MainActor () -> Void
    ) {
        self.executor = executor
        self.threshold = threshold
        self.debounce = debounce
        self.onDetection = onDetection
        ring = [Int16](repeating: 0, count: max(1, Int(Double(sampleRate) * windowSeconds)))
    }

    func ingest(
        buffer inputBuffer: AVAudioPCMBuffer,
        converter: AVAudioConverter,
        targetFormat: AVAudioFormat
    ) {
        let ratio = targetFormat.sampleRate / inputBuffer.format.sampleRate
        let outputCapacity = AVAudioFrameCount(
            ceil(Double(inputBuffer.frameLength) * ratio)
        ) + 8
        guard let outputBuffer = AVAudioPCMBuffer(
            pcmFormat: targetFormat,
            frameCapacity: max(1, outputCapacity)
        ) else { return }

        var consumed = false
        var conversionError: NSError?
        let status = converter.convert(to: outputBuffer, error: &conversionError) { _, inputStatus in
            if consumed {
                inputStatus.pointee = .noDataNow
                return nil
            }
            consumed = true
            inputStatus.pointee = .haveData
            return inputBuffer
        }
        guard status != .error,
              conversionError == nil,
              let channel = outputBuffer.int16ChannelData else { return }

        let count = Int(outputBuffer.frameLength)
        guard count > 0 else { return }
        appendAndMaybePredict(samples: channel[0], count: count)
    }

    func stop() {
        ringLock.lock()
        generation = UUID()
        writeIndex = 0
        samplesWritten = 0
        lastPredictAt = 0
        predictInFlight = false
        lastDetectionAt = nil
        ring = [Int16](repeating: 0, count: ring.count)
        ringLock.unlock()
    }

    private func appendAndMaybePredict(samples: UnsafePointer<Int16>, count: Int) {
        ringLock.lock()
        let size = ring.count
        guard size > 0 else {
            ringLock.unlock()
            return
        }

        var index = writeIndex
        for offset in 0 ..< count {
            ring[index] = samples[offset]
            index += 1
            if index == size { index = 0 }
        }
        writeIndex = index
        samplesWritten = min(samplesWritten + count, size)

        let now = CFAbsoluteTimeGetCurrent()
        guard samplesWritten >= size,
              !predictInFlight,
              now - lastPredictAt >= predictInterval else {
            ringLock.unlock()
            return
        }

        predictInFlight = true
        lastPredictAt = now
        let currentGeneration = generation
        var snapshot = [Int16](repeating: 0, count: size)
        let tail = size - writeIndex
        snapshot.withUnsafeMutableBufferPointer { destination in
            ring.withUnsafeBufferPointer { source in
                guard let sourceBase = source.baseAddress,
                      let destinationBase = destination.baseAddress else { return }
                destinationBase.update(from: sourceBase + writeIndex, count: tail)
                if writeIndex > 0 {
                    (destinationBase + tail).update(from: sourceBase, count: writeIndex)
                }
            }
        }
        ringLock.unlock()

        executor.predict(snapshot) { [weak self] confidence in
            self?.receive(confidence: confidence, generation: currentGeneration)
        }
    }

    private func receive(confidence: Float?, generation predictionGeneration: UUID) {
        let now = CFAbsoluteTimeGetCurrent()
        ringLock.lock()
        guard generation == predictionGeneration else {
            ringLock.unlock()
            return
        }
        predictInFlight = false
        guard let confidence, confidence >= threshold else {
            ringLock.unlock()
            return
        }
        if let lastDetectionAt, now - lastDetectionAt < debounce {
            ringLock.unlock()
            return
        }
        lastDetectionAt = now
        ringLock.unlock()

        Task { @MainActor [onDetection] in
            onDetection()
        }
    }
}
