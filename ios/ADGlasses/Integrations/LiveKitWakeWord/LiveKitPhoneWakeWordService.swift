@preconcurrency import AVFoundation
import Foundation
import LiveKitWakeWord

enum LiveKitPhoneWakeWordError: LocalizedError {
    case modelMissing
    case invalidModel
    case storageUnavailable
    case microphonePermissionDenied
    case failedToCreateAudioInput

    var errorDescription: String? {
        switch self {
        case .modelMissing:
            return "Import a LiveKit-compatible .onnx wake-word classifier first."
        case .invalidModel:
            return "Choose a LiveKit/openWakeWord-compatible classifier with the .onnx extension."
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
/// We intentionally drive `WakeWordModel` ourselves instead of using LiveKit's convenience
/// `WakeWordListener`. AD Glasses owns the recording-session lease across the wake-word -> Apple
/// Speech handoff, and keeping AVAudioSession lifecycle here prevents the wake-word engine from
/// deactivating the microphone in the middle of that handoff.
@MainActor
final class LiveKitPhoneWakeWordService: PhoneWakeWordDetecting {
    private let defaults: UserDefaults
    private let fileManager: FileManager
    private let phraseKey = "livekit.wakePhrase.v1"

    private var audioEngine: AVAudioEngine?
    private var inferencePipeline: LiveKitWakeWordInferencePipeline?

    init(defaults: UserDefaults = .standard, fileManager: FileManager = .default) {
        self.defaults = defaults
        self.fileManager = fileManager
        if defaults.string(forKey: phraseKey) == nil {
            defaults.set("AD", forKey: phraseKey)
        }
    }

    var phrase: String {
        defaults.string(forKey: phraseKey) ?? "AD"
    }

    var configurationState: PhoneWakeWordConfigurationState {
        guard fileManager.fileExists(atPath: modelURL.path),
              (try? modelURL.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0) ?? 0 > 0 else {
            return .missingModel
        }
        return .ready
    }

    func start(onDetection: @escaping @MainActor () -> Void) async throws {
        stop()
        guard fileManager.fileExists(atPath: modelURL.path) else {
            throw LiveKitPhoneWakeWordError.modelMissing
        }
        guard await SpeechPermissions.requestMicrophone() else {
            throw LiveKitPhoneWakeWordError.microphonePermissionDenied
        }
        try Task.checkCancellation()

        let classifierURL = modelURL
        let model = try await Task.detached(priority: .userInitiated) {
            try WakeWordModel(
                models: [classifierURL],
                sampleRate: WakeWordModel.modelSampleRate,
                executionProvider: .coreML
            )
        }.value
        try Task.checkCancellation()

        let session = AVAudioSession.sharedInstance()
        try session.setCategory(
            .playAndRecord,
            mode: .measurement,
            options: [.defaultToSpeaker, .allowBluetoothHFP]
        )
        try session.setActive(true)

        let engine = AVAudioEngine()
        let inputNode = engine.inputNode
        let hardwareFormat = inputNode.inputFormat(forBus: 0)
        guard hardwareFormat.sampleRate > 0 else {
            deactivateAudioSessionIfAllowed()
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
            deactivateAudioSessionIfAllowed()
            throw LiveKitPhoneWakeWordError.failedToCreateAudioInput
        }

        let pipeline = LiveKitWakeWordInferencePipeline(
            model: model,
            sampleRate: Int(WakeWordModel.modelSampleRate),
            threshold: 0.75,
            debounce: 2.0,
            windowSeconds: 2.0,
            onDetection: onDetection
        )

        inputNode.installTap(
            onBus: 0,
            bufferSize: 1024,
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
            audioEngine = engine
            inferencePipeline = pipeline
        } catch {
            inputNode.removeTap(onBus: 0)
            pipeline.stop()
            deactivateAudioSessionIfAllowed()
            throw error
        }
    }

    func stop() {
        if let audioEngine {
            audioEngine.inputNode.removeTap(onBus: 0)
            audioEngine.stop()
        }
        audioEngine = nil
        inferencePipeline?.stop()
        inferencePipeline = nil
        deactivateAudioSessionIfAllowed()
    }

    func importModel(from sourceURL: URL, phrase: String) throws {
        guard sourceURL.pathExtension.lowercased() == "onnx" else {
            throw LiveKitPhoneWakeWordError.invalidModel
        }
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

            if fileManager.fileExists(atPath: modelURL.path) {
                _ = try fileManager.replaceItemAt(modelURL, withItemAt: temporaryURL)
            } else {
                try fileManager.moveItem(at: temporaryURL, to: modelURL)
            }
            defaults.set(normalized(phrase), forKey: phraseKey)
        } catch let error as LiveKitPhoneWakeWordError {
            throw error
        } catch {
            try? fileManager.removeItem(at: temporaryURL)
            throw LiveKitPhoneWakeWordError.storageUnavailable
        }
    }

    private var modelURL: URL {
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

    private func normalized(_ phrase: String) -> String {
        let trimmed = phrase.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? "AD" : trimmed
    }

    private func deactivateAudioSessionIfAllowed() {
        guard !VoiceAudioSessionContinuity.shared.keepsRecordingSessionActive else { return }
        try? AVAudioSession.sharedInstance().setActive(
            false,
            options: .notifyOthersOnDeactivation
        )
    }
}

private final class LiveKitWakeWordInferencePipeline: @unchecked Sendable {
    private let model: WakeWordModel
    private let threshold: Float
    private let debounce: TimeInterval
    private let ringLock = NSLock()
    private let workQueue = DispatchQueue(
        label: "com.achyutdalai.ADGlasses.livekitWakeWord.predict",
        qos: .userInteractive
    )
    private let onDetection: @MainActor () -> Void

    private var ring: [Int16]
    private var writeIndex = 0
    private var samplesWritten = 0
    private var lastPredictAt: CFAbsoluteTime = 0
    private var predictInFlight = false
    private var lastDetectionAt: Date?
    private var generation = UUID()

    private let predictInterval: CFAbsoluteTime = 0.02

    init(
        model: WakeWordModel,
        sampleRate: Int,
        threshold: Float,
        debounce: TimeInterval,
        windowSeconds: Double,
        onDetection: @escaping @MainActor () -> Void
    ) {
        self.model = model
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

        workQueue.async { [weak self] in
            self?.predict(snapshot: snapshot, generation: currentGeneration)
        }
    }

    private func predict(snapshot: [Int16], generation predictionGeneration: UUID) {
        defer { finishPrediction(generation: predictionGeneration) }

        let scores: [String: Float]
        do {
            scores = try model.predict(snapshot)
        } catch {
            return
        }
        guard let confidence = scores.values.max(), confidence >= threshold else { return }

        let now = Date()
        ringLock.lock()
        guard generation == predictionGeneration else {
            ringLock.unlock()
            return
        }
        if let lastDetectionAt,
           now.timeIntervalSince(lastDetectionAt) < debounce {
            ringLock.unlock()
            return
        }
        lastDetectionAt = now
        ringLock.unlock()

        Task { @MainActor [onDetection] in
            onDetection()
        }
    }

    private func finishPrediction(generation predictionGeneration: UUID) {
        ringLock.lock()
        if generation == predictionGeneration {
            predictInFlight = false
        }
        ringLock.unlock()
    }
}
