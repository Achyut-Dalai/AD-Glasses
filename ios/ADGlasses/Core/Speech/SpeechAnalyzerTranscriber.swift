@preconcurrency import AVFoundation
import Foundation
import Speech

#if compiler(>=6.2)
@available(iOS 26.0, *)
@MainActor
final class SpeechAnalyzerTranscriber: ExternalAudioSpeechTranscribing {
    let engineName = "SpeechAnalyzer (on-device)"
    var onUpdate: ((SpeechTranscriptionSnapshot) -> Void)?
    var onError: ((Error) -> Void)?

    private(set) var snapshot: SpeechTranscriptionSnapshot {
        didSet { onUpdate?(snapshot) }
    }

    private let requestedLocale: Locale
    private let audioEngine = AVAudioEngine()
    private let bufferConverter = SpeechBufferConverter()
    private var analyzer: SpeechAnalyzer?
    private var transcriber: DictationTranscriber?
    private var analyzerInputContinuation: AsyncStream<AnalyzerInput>.Continuation?
    private var audioContinuation: AsyncStream<AVAudioPCMBuffer>.Continuation?
    private var audioTask: Task<Void, Never>?
    private var resultTask: Task<Void, Never>?
    private var phoneEndpointTask: Task<Void, Never>?
    private var externalEndpointTask: Task<Void, Never>?
    private var finalizedTranscript = ""
    private var lastPhoneEndpointTranscript = ""
    private var inputSource: InputSource?
    private var preparedLocale: Locale?

    private let phoneTranscriptSilenceDelay: Duration = .milliseconds(1_200)
    private let externalAcousticSilenceDelay: Duration = .milliseconds(1_100)
    private let initialNoSpeechDelay: Duration = .seconds(6)
    private let postDownloadStatusChecks = 60

    // Deliberately forgiving. If the environment is noisier than this, the logical early endpoint
    // simply stays disabled and the verified glasses 0x73/0x0A end event remains the fallback. This
    // is safer than cutting quiet or distant speech because a fixed transcript stopped changing.
    private let externalSilencePeakThresholdDBFS: Float = -56

    private enum InputSource {
        case phoneMicrophone
        case externalPCM
    }

    private static let assistantContextualStrings = [
        "AD Glasses",
        "click",
        "photo",
        "picture",
        "take photo",
        "video",
        "record",
        "recording",
        "start recording",
        "stop recording",
        "stop",
        "read",
        "read text",
        "Lens",
        "translate",
        "translation"
    ]

    init(locale: Locale) {
        requestedLocale = locale
        snapshot = SpeechTranscriptionSnapshot(
            transcript: "",
            isRunning: false,
            engineName: engineName
        )
    }

    static func supportedSpeechLocales() async -> [Locale] {
        await DictationTranscriber.supportedLocales
    }

    static func installedSpeechLocales() async -> [Locale] {
        await DictationTranscriber.installedLocales
    }

    /// Ensures the requested SpeechAnalyzer language asset is ready once per transcriber lifetime.
    /// A successful preparation is cached so later Assistant turns do not repeatedly re-enter the
    /// installation/status path for the same en-US model.
    func prepareAssets(statusUpdate: ((String) -> Void)? = nil) async throws -> Locale {
        if let preparedLocale {
            statusUpdate?("\(languageDisplayName(preparedLocale)) speech model ready")
            return preparedLocale
        }

        guard let locale = await DictationTranscriber.supportedLocale(equivalentTo: requestedLocale) else {
            throw SpeechAnalyzerAssetError.unsupportedLanguage(languageDisplayName(requestedLocale))
        }

        let module = makeTranscriber(locale: locale)
        let languageName = languageDisplayName(locale)

        statusUpdate?("Preparing \(languageName) speech model…")
        do {
            try await reserve(locale)
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            await Self.logAssetDiagnostic(
                locale: locale,
                status: await AssetInventory.status(forModules: [module]),
                error: error,
                stage: "reservation"
            )
            throw SpeechAnalyzerAssetError.reservationFailed(languageName)
        }

        let status = await AssetInventory.status(forModules: [module])
        switch status {
        case .installed:
            preparedLocale = locale
            statusUpdate?("\(languageName) speech model ready")
            return locale
        case .unsupported:
            throw SpeechAnalyzerAssetError.unsupportedLanguage(languageName)
        case .supported, .downloading:
            statusUpdate?("Downloading \(languageName) speech model…")
        @unknown default:
            throw SpeechAnalyzerAssetError.installationFailed(
                languageName,
                "SpeechAnalyzer returned an unknown asset state."
            )
        }

        var installationError: Error?
        do {
            if let request = try await AssetInventory.assetInstallationRequest(supporting: [module]) {
                let progressTask = Task { @MainActor in
                    var lastPercent = -1
                    while !Task.isCancelled {
                        let percent = max(
                            0,
                            min(100, Int((request.progress.fractionCompleted * 100).rounded()))
                        )
                        if percent != lastPercent {
                            lastPercent = percent
                            statusUpdate?("Downloading \(languageName) speech model… \(percent)%")
                        }
                        do {
                            try await Task.sleep(for: .milliseconds(250))
                        } catch {
                            return
                        }
                    }
                }
                defer { progressTask.cancel() }
                try await request.downloadAndInstall()
                progressTask.cancel()
                await progressTask.value
            }
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            installationError = error
            let state = await AssetInventory.status(forModules: [module])
            await Self.logAssetDiagnostic(
                locale: locale,
                status: state,
                error: error,
                stage: "downloadAndInstall"
            )
            guard state == .downloading else {
                throw SpeechAnalyzerAssetError.installationFailed(
                    languageName,
                    error.localizedDescription
                )
            }
        }

        let installedLocale = try await waitForInstalledModel(
            module,
            locale: locale,
            languageName: languageName,
            statusUpdate: statusUpdate,
            initialError: installationError
        )
        preparedLocale = installedLocale
        return installedLocale
    }

    func start() async throws {
        try await startWithPreparedLocale(nil)
    }

    func start(preparedLocale: Locale) async throws {
        try await startWithPreparedLocale(preparedLocale)
    }

    private func startWithPreparedLocale(_ explicitPreparedLocale: Locale?) async throws {
        if snapshot.isRunning { return }
        try await SpeechPermissions.requestAll()
        await stop()

        let locale: Locale
        if let explicitPreparedLocale {
            locale = explicitPreparedLocale
        } else {
            locale = try await prepareAssets()
        }
        _ = try await prepareAnalyzerPipeline(preparedLocale: locale)

        do {
            try SpeechInputAudioSession.activate()
        } catch {
            await cancelPreparedPipeline()
            throw error
        }

        let inputNode = audioEngine.inputNode
        let format = inputNode.outputFormat(forBus: 0)
        guard format.sampleRate > 0 else {
            await cancelPreparedPipeline()
            SpeechInputAudioSession.deactivate()
            throw SpeechTranscriptionError.failedToCreateAudioInput
        }

        guard let audioContinuation else {
            await cancelPreparedPipeline()
            SpeechInputAudioSession.deactivate()
            throw SpeechTranscriptionError.failedToCreateAudioInput
        }

        inputNode.installTap(onBus: 0, bufferSize: 1_024, format: format) { buffer, _ in
            audioContinuation.yield(buffer)
        }

        audioEngine.prepare()
        do {
            try audioEngine.start()
            inputSource = .phoneMicrophone
            snapshot.isRunning = true
            armInitialPhoneEndpoint()
        } catch {
            inputNode.removeTap(onBus: 0)
            audioContinuation.finish()
            await cancelPreparedPipeline()
            SpeechInputAudioSession.deactivate()
            throw error
        }
    }

    func stop() async {
        cancelEndpointTasks()
        lastPhoneEndpointTranscript = ""

        let wasUsingPhoneMicrophone = inputSource == .phoneMicrophone
        if wasUsingPhoneMicrophone {
            if audioEngine.isRunning {
                audioEngine.stop()
            }
            audioEngine.inputNode.removeTap(onBus: 0)
            audioEngine.reset()
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
            SpeechInputAudioSession.deactivate()
        }
    }

    func startExternalAudio() async throws {
        if snapshot.isRunning { await stop() }
        try await SpeechPermissions.requestRecognition()
        let locale = try await prepareAssets()
        _ = try await prepareAnalyzerPipeline(preparedLocale: locale)
        inputSource = .externalPCM
        snapshot.isRunning = true
    }

    func appendExternalAudio(_ buffer: AVAudioPCMBuffer) {
        guard inputSource == .externalPCM else { return }
        audioContinuation?.yield(buffer)
        noteExternalAudioActivity(buffer)
    }

    func finishExternalAudio() async {
        guard inputSource == .externalPCM else { return }
        await stop()
    }

    func resetTranscript() {
        finalizedTranscript = ""
        lastPhoneEndpointTranscript = ""
        externalEndpointTask?.cancel()
        externalEndpointTask = nil
        snapshot.transcript = ""
    }

    private func startResultTask(for module: DictationTranscriber) {
        finalizedTranscript = ""
        resultTask = Task { [weak self] in
            guard let self else { return }
            var volatileTranscript = ""

            do {
                for try await result in module.results {
                    let text = String(result.text.characters)
                    if result.isFinal {
                        finalizedTranscript = Self.join(finalizedTranscript, text)
                        volatileTranscript = ""
                    } else {
                        volatileTranscript = text
                    }
                    let combined = Self.join(finalizedTranscript, volatileTranscript)
                    snapshot.transcript = combined
                    noteTranscriptActivity(combined)
                }
            } catch is CancellationError {
                return
            } catch {
                onError?(error)
            }
        }
    }

    private func armInitialPhoneEndpoint() {
        phoneEndpointTask?.cancel()
        phoneEndpointTask = Task { @MainActor [weak self] in
            do {
                try await Task.sleep(for: self?.initialNoSpeechDelay ?? .seconds(6))
            } catch {
                return
            }
            guard let self,
                  inputSource == .phoneMicrophone,
                  snapshot.isRunning,
                  snapshot.transcript.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                return
            }
            await stop()
        }
    }

    private func noteTranscriptActivity(_ text: String) {
        let clean = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return }

        switch inputSource {
        case .phoneMicrophone:
            guard clean != lastPhoneEndpointTranscript else { return }
            lastPhoneEndpointTranscript = clean
            phoneEndpointTask?.cancel()
            phoneEndpointTask = Task { @MainActor [weak self] in
                do {
                    try await Task.sleep(for: self?.phoneTranscriptSilenceDelay ?? .milliseconds(1_200))
                } catch {
                    return
                }
                guard let self,
                      inputSource == .phoneMicrophone,
                      snapshot.isRunning,
                      snapshot.transcript.trimmingCharacters(in: .whitespacesAndNewlines) == clean else {
                    return
                }
                await stop()
            }

        case .externalPCM:
            // A new recognition result means the utterance is still evolving. Never use text
            // stability alone as an endpoint: SpeechAnalyzer can pause result delivery while the
            // person is still speaking, especially with faint/distant input.
            externalEndpointTask?.cancel()
            externalEndpointTask = nil

        case nil:
            break
        }
    }

    private func noteExternalAudioActivity(_ buffer: AVAudioPCMBuffer) {
        guard inputSource == .externalPCM,
              snapshot.isRunning,
              !snapshot.transcript.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              let peakDBFS = Self.peakDBFS(buffer) else {
            return
        }

        if peakDBFS > externalSilencePeakThresholdDBFS {
            externalEndpointTask?.cancel()
            externalEndpointTask = nil
            return
        }

        guard externalEndpointTask == nil else { return }
        let endpointTranscript = snapshot.transcript.trimmingCharacters(in: .whitespacesAndNewlines)
        externalEndpointTask = Task { @MainActor [weak self] in
            do {
                try await Task.sleep(for: self?.externalAcousticSilenceDelay ?? .milliseconds(1_100))
            } catch {
                return
            }
            guard let self,
                  inputSource == .externalPCM,
                  snapshot.isRunning,
                  snapshot.transcript.trimmingCharacters(in: .whitespacesAndNewlines) == endpointTranscript else {
                return
            }
            await stop()
        }
    }

    private static func peakDBFS(_ buffer: AVAudioPCMBuffer) -> Float? {
        let frameCount = Int(buffer.frameLength)
        let channelCount = Int(buffer.format.channelCount)
        guard frameCount > 0, channelCount > 0 else { return nil }

        var peak: Float = 0
        switch buffer.format.commonFormat {
        case .pcmFormatFloat32:
            guard let channels = buffer.floatChannelData else { return nil }
            for channel in 0..<channelCount {
                let samples = channels[channel]
                for frame in 0..<frameCount {
                    peak = max(peak, abs(samples[frame]))
                }
            }

        case .pcmFormatInt16:
            guard let channels = buffer.int16ChannelData else { return nil }
            for channel in 0..<channelCount {
                let samples = channels[channel]
                for frame in 0..<frameCount {
                    let magnitude = Float(abs(Int(samples[frame]))) / Float(Int16.max)
                    peak = max(peak, magnitude)
                }
            }

        case .pcmFormatInt32:
            guard let channels = buffer.int32ChannelData else { return nil }
            for channel in 0..<channelCount {
                let samples = channels[channel]
                for frame in 0..<frameCount {
                    let magnitude = Float(abs(Int64(samples[frame]))) / Float(Int32.max)
                    peak = max(peak, magnitude)
                }
            }

        default:
            return nil
        }

        guard peak > 0 else { return -120 }
        return Float(20.0 * log10(Double(peak)))
    }

    private static func join(_ prefix: String, _ suffix: String) -> String {
        guard !prefix.isEmpty else { return suffix }
        guard !suffix.isEmpty else { return prefix }
        if prefix.last?.isWhitespace == true || suffix.first?.isWhitespace == true {
            return prefix + suffix
        }
        return prefix + " " + suffix
    }

    private func waitForInstalledModel(
        _ module: DictationTranscriber,
        locale: Locale,
        languageName: String,
        statusUpdate: ((String) -> Void)?,
        initialError: Error?
    ) async throws -> Locale {
        for check in 0..<postDownloadStatusChecks {
            try Task.checkCancellation()
            let status = await AssetInventory.status(forModules: [module])
            switch status {
            case .installed:
                statusUpdate?("\(languageName) speech model ready")
                return locale
            case .unsupported:
                throw SpeechAnalyzerAssetError.unsupportedLanguage(languageName)
            case .downloading:
                statusUpdate?("Finishing \(languageName) speech model download…")
            case .supported:
                statusUpdate?("Waiting for SpeechAnalyzer to finish \(languageName) model setup…")
            @unknown default:
                await Self.logAssetDiagnostic(
                    locale: locale,
                    status: status,
                    error: initialError,
                    stage: "status-monitor"
                )
                throw SpeechAnalyzerAssetError.installationFailed(
                    languageName,
                    "SpeechAnalyzer returned an unknown asset state."
                )
            }

            if check + 1 < postDownloadStatusChecks {
                try await Task.sleep(for: .milliseconds(500))
            }
        }

        let finalStatus = await AssetInventory.status(forModules: [module])
        await Self.logAssetDiagnostic(
            locale: locale,
            status: finalStatus,
            error: initialError,
            stage: "status-monitor-timeout"
        )
        throw SpeechAnalyzerAssetError.installationPending(languageName)
    }

    private func prepareAnalyzerPipeline(preparedLocale: Locale) async throws -> AVAudioFormat {
        let module = makeTranscriber(locale: preparedLocale)

        // The same pipeline is used by phone Ask/dictation and provider PCM. No flow bypasses
        // SpeechAnalyzer preheat or model retention.
        guard let analyzerFormat = await SpeechAnalyzer.bestAvailableAudioFormat(compatibleWith: [module]) else {
            throw SpeechTranscriptionError.failedToCreateAudioInput
        }

        let options = SpeechAnalyzer.Options(
            priority: .userInitiated,
            modelRetention: .processLifetime
        )
        let analyzer = SpeechAnalyzer(modules: [module], options: options)
        let context = AnalysisContext()
        context.contextualStrings[.general] = Self.assistantContextualStrings
        try await analyzer.setContext(context)

        let (analyzerInputs, analyzerInputContinuation) = AsyncStream.makeStream(of: AnalyzerInput.self)
        let (audioStream, audioContinuation) = AsyncStream.makeStream(of: AVAudioPCMBuffer.self)
        self.analyzer = analyzer
        transcriber = module
        self.analyzerInputContinuation = analyzerInputContinuation
        self.audioContinuation = audioContinuation

        try await analyzer.prepareToAnalyze(in: analyzerFormat)
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

    private func makeTranscriber(locale: Locale) -> DictationTranscriber {
        // Start from the accurate short-dictation configuration, then request volatile results for
        // live UI without enabling `frequentFinalization` (the DictationTranscriber equivalent of
        // trading accuracy for speed). `farField` asks Apple's model to better accommodate quiet or
        // distant delivery without changing the PCM samples with guessed gain/EQ.
        let preset = DictationTranscriber.Preset.shortDictation
        return DictationTranscriber(
            locale: locale,
            contentHints: preset.contentHints.union([.farField]),
            transcriptionOptions: preset.transcriptionOptions,
            reportingOptions: [.volatileResults],
            attributeOptions: preset.attributeOptions
        )
    }

    private func reserve(_ locale: Locale) async throws {
        let normalizedRequested = Self.normalizedIdentifier(locale)
        var reservations = await AssetInventory.reservedLocales

        if reservations.contains(where: { Self.normalizedIdentifier($0) == normalizedRequested }) {
            return
        }

        if reservations.count >= AssetInventory.maximumReservedLocales {
            for reservedLocale in reservations
            where Self.normalizedIdentifier(reservedLocale) != normalizedRequested {
                _ = await AssetInventory.release(reservedLocale: reservedLocale)
            }
            reservations = await AssetInventory.reservedLocales
            if reservations.contains(where: { Self.normalizedIdentifier($0) == normalizedRequested }) {
                return
            }
        }

        _ = try await AssetInventory.reserve(locale: locale)
    }

    private static func normalizedIdentifier(_ locale: Locale) -> String {
        locale.identifier
            .replacingOccurrences(of: "_", with: "-")
            .lowercased()
    }

    private func languageDisplayName(_ locale: Locale) -> String {
        Locale.current.localizedString(forIdentifier: locale.identifier)
            ?? locale.localizedString(forIdentifier: locale.identifier)
            ?? locale.identifier
    }

    private static func logAssetDiagnostic(
        locale: Locale,
        status: AssetInventory.Status,
        error: Error?,
        stage: String
    ) async {
        let nsError = error.map { $0 as NSError }
        let installed = await DictationTranscriber.installedLocales
            .map(\.identifier)
            .sorted()
            .joined(separator: ", ")
        let reserved = await AssetInventory.reservedLocales
            .map(\.identifier)
            .sorted()
            .joined(separator: ", ")
        var message = "[AD SpeechAnalyzer] stage=\(stage) locale=\(locale.identifier) status=\(status) installed=[\(installed)] reserved=[\(reserved)]"
        if let nsError {
            message += " NSError(domain=\(nsError.domain), code=\(nsError.code), description=\(nsError.localizedDescription), userInfo=\(nsError.userInfo))"
        }
        NSLog("%@", message)
    }

    private func cancelEndpointTasks() {
        phoneEndpointTask?.cancel()
        phoneEndpointTask = nil
        externalEndpointTask?.cancel()
        externalEndpointTask = nil
    }

    private func cancelPreparedPipeline() async {
        cancelEndpointTasks()
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
    case unsupportedLanguage(String)
    case reservationFailed(String)
    case installationFailed(String, String)
    case installationPending(String)

    var errorDescription: String? {
        switch self {
        case .unsupportedLanguage(let language):
            return "SpeechAnalyzer does not support \(language) on this iPhone."
        case .reservationFailed(let language):
            return "SpeechAnalyzer could not prepare the \(language) model. Try again after freeing storage or restarting the iPhone."
        case .installationFailed(let language, let reason):
            return "The \(language) SpeechAnalyzer model could not be installed. \(reason)"
        case .installationPending(let language):
            return "SpeechAnalyzer is still downloading the \(language) model. Keep this iPhone online and try voice input again shortly."
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
                return "Could not create an audio converter for SpeechAnalyzer."
            case .failedToCreateBuffer:
                return "Could not allocate an audio buffer for SpeechAnalyzer."
            case .conversionFailed(let error):
                return error?.localizedDescription ?? "Audio conversion for SpeechAnalyzer failed."
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
