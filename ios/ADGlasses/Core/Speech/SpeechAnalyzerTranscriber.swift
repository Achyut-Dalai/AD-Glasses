@preconcurrency import AVFoundation
import Foundation
import Speech

#if compiler(>=6.2)
@available(iOS 26.0, *)
@MainActor
final class SpeechAnalyzerTranscriber: ExternalAudioSpeechTranscribing {
    let engineName = "Apple SpeechAnalyzer (on-device)"
    var onUpdate: ((SpeechTranscriptionSnapshot) -> Void)?
    var onError: ((Error) -> Void)?

    private(set) var snapshot: SpeechTranscriptionSnapshot {
        didSet { onUpdate?(snapshot) }
    }

    private let requestedLocale: Locale
    private let audioEngine = AVAudioEngine()
    private let bufferConverter = SpeechBufferConverter()
    private var analyzer: SpeechAnalyzer?
    private var transcriber: SpeechTranscriber?
    private var analyzerInputContinuation: AsyncStream<AnalyzerInput>.Continuation?
    private var audioContinuation: AsyncStream<AVAudioPCMBuffer>.Continuation?
    private var audioTask: Task<Void, Never>?
    private var resultTask: Task<Void, Never>?
    private var finalizedTranscript = ""
    private var inputSource: InputSource?

    private enum InputSource {
        case phoneMicrophone
        case externalPCM
    }

    init(locale: Locale) {
        requestedLocale = locale
        snapshot = SpeechTranscriptionSnapshot(
            transcript: "",
            isRunning: false,
            engineName: engineName
        )
    }

    func prepareAssets(statusUpdate: ((String) -> Void)? = nil) async throws -> Locale {
        guard SpeechTranscriber.isAvailable else {
            throw SpeechAnalyzerAssetError.moduleUnavailable
        }
        guard let locale = await SpeechTranscriber.supportedLocale(equivalentTo: requestedLocale) else {
            throw SpeechAnalyzerAssetError.unsupportedLanguage(languageDisplayName(requestedLocale))
        }

        let module = SpeechTranscriber(locale: locale, preset: .progressiveTranscription)
        let languageName = languageDisplayName(locale)
        var lastDownloadError: Error?

        for attempt in 0 ..< 3 {
            try Task.checkCancellation()
            var status = await AssetInventory.status(forModules: [module])

            switch status {
            case .installed:
                statusUpdate?("\(languageName) speech model ready")
                return locale
            case .unsupported:
                throw SpeechAnalyzerAssetError.unsupportedLanguage(languageName)
            case .supported:
                statusUpdate?(attempt == 0
                    ? "Downloading \(languageName) speech model…"
                    : "Retrying \(languageName) speech model download…")
                do {
                    if let request = try await AssetInventory.assetInstallationRequest(supporting: [module]) {
                        try await request.downloadAndInstall()
                    }
                    lastDownloadError = nil
                } catch is CancellationError {
                    throw CancellationError()
                } catch {
                    lastDownloadError = error
                }
            case .downloading:
                statusUpdate?("Downloading \(languageName) speech model…")
            @unknown default:
                throw SpeechAnalyzerAssetError.installationFailed(
                    languageName,
                    "Apple Speech returned an unknown asset state."
                )
            }

            status = await AssetInventory.status(forModules: [module])
            if status == .installed {
                statusUpdate?("\(languageName) speech model ready")
                return locale
            }
            if status == .unsupported {
                throw SpeechAnalyzerAssetError.unsupportedLanguage(languageName)
            }

            // Apple's initial installation request can return while the system is still retrying
            // the download. Keep observing the official AssetInventory state instead of trying to
            // start SpeechAnalyzer against an asset that is not installed yet.
            for _ in 0 ..< 45 {
                try Task.checkCancellation()
                try await Task.sleep(for: .seconds(1))
                status = await AssetInventory.status(forModules: [module])
                switch status {
                case .installed:
                    statusUpdate?("\(languageName) speech model ready")
                    return locale
                case .downloading:
                    statusUpdate?("Downloading \(languageName) speech model…")
                case .unsupported:
                    throw SpeechAnalyzerAssetError.unsupportedLanguage(languageName)
                case .supported:
                    // The system is no longer actively downloading. Let the outer loop request
                    // another consolidated installation attempt.
                    break
                @unknown default:
                    break
                }
                if status == .supported { break }
            }
        }

        let finalStatus = await AssetInventory.status(forModules: [module])
        if finalStatus == .installed { return locale }

        if let lastDownloadError {
            throw SpeechAnalyzerAssetError.installationFailed(
                languageName,
                lastDownloadError.localizedDescription
            )
        }
        throw SpeechAnalyzerAssetError.installationTimedOut(languageName)
    }

    func start() async throws {
        if snapshot.isRunning { return }
        try await SpeechPermissions.requestAll()
        await stop()
        _ = try await prepareAnalyzerPipeline()

        let audioSession = AVAudioSession.sharedInstance()
        do {
            try audioSession.setCategory(
                .playAndRecord,
                mode: .spokenAudio,
                options: [.duckOthers, .allowBluetoothHFP]
            )
            try audioSession.setActive(true, options: .notifyOthersOnDeactivation)
        } catch {
            await cancelPreparedPipeline()
            throw error
        }

        let inputNode = audioEngine.inputNode
        let format = inputNode.outputFormat(forBus: 0)
        guard format.sampleRate > 0 else {
            await cancelPreparedPipeline()
            throw SpeechTranscriptionError.failedToCreateAudioInput
        }

        guard let audioContinuation else {
            await cancelPreparedPipeline()
            throw SpeechTranscriptionError.failedToCreateAudioInput
        }

        inputNode.installTap(onBus: 0, bufferSize: 4096, format: format) { buffer, _ in
            audioContinuation.yield(buffer)
        }

        audioEngine.prepare()
        do {
            try audioEngine.start()
            inputSource = .phoneMicrophone
            snapshot.isRunning = true
        } catch {
            inputNode.removeTap(onBus: 0)
            audioContinuation.finish()
            await cancelPreparedPipeline()
            throw error
        }
    }

    func stop() async {
        let wasUsingPhoneMicrophone = inputSource == .phoneMicrophone
        if wasUsingPhoneMicrophone, audioEngine.isRunning {
            audioEngine.stop()
            audioEngine.inputNode.removeTap(onBus: 0)
        }

        audioContinuation?.finish()
        await audioTask?.value
        audioTask = nil
        audioContinuation = nil

        if let analyzer {
            do {
                try await analyzer.finalizeAndFinishThroughEndOfInput()
            } catch {
                onError?(error)
            }
        }

        await resultTask?.value
        resultTask = nil
        analyzerInputContinuation = nil
        transcriber = nil
        analyzer = nil
        bufferConverter.reset()
        inputSource = nil
        snapshot.isRunning = false
        if wasUsingPhoneMicrophone {
            VoiceAudioSessionContinuity.shared.deactivateIfAllowed()
        }
    }

    func startExternalAudio() async throws {
        if snapshot.isRunning { await stop() }
        try await SpeechPermissions.requestRecognition()
        _ = try await prepareAnalyzerPipeline()
        inputSource = .externalPCM
        snapshot.isRunning = true
    }

    func appendExternalAudio(_ buffer: AVAudioPCMBuffer) {
        guard inputSource == .externalPCM else { return }
        audioContinuation?.yield(buffer)
    }

    func finishExternalAudio() async {
        guard inputSource == .externalPCM else { return }
        await stop()
    }

    func resetTranscript() {
        finalizedTranscript = ""
        snapshot.transcript = ""
    }

    private func startResultTask(for module: SpeechTranscriber) {
        finalizedTranscript = ""
        resultTask = Task { [weak self] in
            guard let self else { return }
            var volatileTranscript = ""

            do {
                for try await result in module.results {
                    let text = String(result.text.characters)
                    if result.isFinal {
                        finalizedTranscript += text
                        volatileTranscript = ""
                    } else {
                        volatileTranscript = text
                    }
                    snapshot.transcript = finalizedTranscript + volatileTranscript
                }
            } catch is CancellationError {
                return
            } catch {
                onError?(error)
            }
        }
    }

    private func prepareAnalyzerPipeline() async throws -> AVAudioFormat {
        let locale = try await prepareAssets()
        let module = SpeechTranscriber(locale: locale, preset: .progressiveTranscription)
        guard await AssetInventory.status(forModules: [module]) == .installed else {
            throw SpeechAnalyzerAssetError.installationTimedOut(languageDisplayName(locale))
        }
        guard let analyzerFormat = await SpeechAnalyzer.bestAvailableAudioFormat(compatibleWith: [module]) else {
            throw SpeechTranscriptionError.failedToCreateAudioInput
        }

        let analyzer = SpeechAnalyzer(modules: [module])
        let (analyzerInputs, analyzerInputContinuation) = AsyncStream.makeStream(of: AnalyzerInput.self)
        let (audioStream, audioContinuation) = AsyncStream.makeStream(of: AVAudioPCMBuffer.self)
        self.analyzer = analyzer
        transcriber = module
        self.analyzerInputContinuation = analyzerInputContinuation
        self.audioContinuation = audioContinuation

        try await analyzer.start(inputSequence: analyzerInputs)
        startResultTask(for: module)
        audioTask = Task { [weak self] in
            guard let self else { return }
            for await buffer in audioStream {
                do {
                    let converted = try bufferConverter.convertBuffer(buffer, to: analyzerFormat)
                    analyzerInputContinuation.yield(AnalyzerInput(buffer: converted))
                } catch {
                    onError?(error)
                }
            }
            analyzerInputContinuation.finish()
        }
        return analyzerFormat
    }

    private func languageDisplayName(_ locale: Locale) -> String {
        Locale.current.localizedString(forIdentifier: locale.identifier)
            ?? locale.localizedString(forIdentifier: locale.identifier)
            ?? locale.identifier
    }

    private func cancelPreparedPipeline() async {
        audioContinuation?.finish()
        await audioTask?.value
        audioTask = nil
        audioContinuation = nil
        analyzerInputContinuation?.finish()
        if let analyzer {
            await analyzer.cancelAndFinishNow()
        }
        resultTask?.cancel()
        await resultTask?.value
        resultTask = nil
        analyzerInputContinuation = nil
        transcriber = nil
        self.analyzer = nil
        bufferConverter.reset()
        inputSource = nil
    }
}

@available(iOS 26.0, *)
private enum SpeechAnalyzerAssetError: LocalizedError {
    case moduleUnavailable
    case unsupportedLanguage(String)
    case installationFailed(String, String)
    case installationTimedOut(String)

    var errorDescription: String? {
        switch self {
        case .moduleUnavailable:
            return "Apple SpeechAnalyzer is not available on this iPhone."
        case .unsupportedLanguage(let language):
            return "Apple SpeechAnalyzer does not support \(language) on this iPhone."
        case .installationFailed(let language, let reason):
            return "The \(language) speech model could not finish installing. \(reason)"
        case .installationTimedOut(let language):
            return "The \(language) speech model is not installed yet. Keep this iPhone online and try again after the download finishes."
        }
    }
}

@available(iOS 26.0, *)
@MainActor
private final class SpeechBufferConverter {
    enum ConversionError: LocalizedError {
        case failedToCreateConverter
        case failedToCreateBuffer
        case conversionFailed(NSError?)

        var errorDescription: String? {
            switch self {
            case .failedToCreateConverter:
                return "Could not create an audio converter for Apple SpeechAnalyzer."
            case .failedToCreateBuffer:
                return "Could not allocate an audio buffer for Apple SpeechAnalyzer."
            case .conversionFailed(let error):
                return error?.localizedDescription ?? "Audio conversion for Apple SpeechAnalyzer failed."
            }
        }
    }

    private var converter: AVAudioConverter?

    func convertBuffer(_ buffer: AVAudioPCMBuffer, to format: AVAudioFormat) throws -> AVAudioPCMBuffer {
        let inputFormat = buffer.format
        guard inputFormat != format else {
            return buffer
        }

        if converter == nil || converter?.inputFormat != inputFormat || converter?.outputFormat != format {
            converter = AVAudioConverter(from: inputFormat, to: format)
            converter?.primeMethod = .none
        }

        guard let converter else {
            throw ConversionError.failedToCreateConverter
        }

        let sampleRateRatio = converter.outputFormat.sampleRate / converter.inputFormat.sampleRate
        let scaledInputFrameLength = Double(buffer.frameLength) * sampleRateRatio
        let frameCapacity = max(1, AVAudioFrameCount(scaledInputFrameLength.rounded(.up)))

        guard let conversionBuffer = AVAudioPCMBuffer(
            pcmFormat: converter.outputFormat,
            frameCapacity: frameCapacity
        ) else {
            throw ConversionError.failedToCreateBuffer
        }

        var conversionError: NSError?
        var bufferProcessed = false

        let status = converter.convert(to: conversionBuffer, error: &conversionError) { _, inputStatus in
            defer { bufferProcessed = true }
            inputStatus.pointee = bufferProcessed ? .noDataNow : .haveData
            return bufferProcessed ? nil : buffer
        }

        guard status != .error else {
            throw ConversionError.conversionFailed(conversionError)
        }

        return conversionBuffer
    }

    func reset() {
        converter = nil
    }
}
#endif
