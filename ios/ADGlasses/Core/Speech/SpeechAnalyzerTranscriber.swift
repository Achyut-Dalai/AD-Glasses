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
    private var endpointTask: Task<Void, Never>?
    private var finalizedTranscript = ""
    private var lastEndpointTranscript = ""
    private var inputSource: InputSource?

    private let transcriptSilenceDelay: Duration = .milliseconds(1_200)
    private let initialNoSpeechDelay: Duration = .seconds(6)

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

    static func supportedSpeechLocales() async -> [Locale] {
        guard SpeechTranscriber.isAvailable else { return [] }
        return await SpeechTranscriber.supportedLocales
    }

    static func installedSpeechLocales() async -> [Locale] {
        guard SpeechTranscriber.isAvailable else { return [] }
        return await SpeechTranscriber.installedLocales
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

        // Locale-dependent SpeechAnalyzer modules use app-scoped asset reservations. Keep existing
        // useful reservations when the device permits it, but free stale reservations if the app is
        // already at Apple's per-device reservation limit.
        statusUpdate?("Preparing \(languageName) speech model…")
        do {
            try await reserve(locale)
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            throw SpeechAnalyzerAssetError.reservationFailed(
                languageName,
                Self.diagnosticDescription(for: error)
            )
        }

        let status = await AssetInventory.status(forModules: [module])
        switch status {
        case .installed:
            statusUpdate?("\(languageName) speech model ready")
            return locale
        case .unsupported:
            throw SpeechAnalyzerAssetError.unsupportedLanguage(languageName)
        case .supported, .downloading:
            statusUpdate?("Downloading \(languageName) speech model…")
        @unknown default:
            throw SpeechAnalyzerAssetError.installationFailed(
                languageName,
                "Apple Speech returned an unknown asset state."
            )
        }

        do {
            if let request = try await AssetInventory.assetInstallationRequest(supporting: [module]) {
                try await request.downloadAndInstall()
            }
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            throw SpeechAnalyzerAssetError.installationFailed(
                languageName,
                Self.diagnosticDescription(for: error)
            )
        }

        let finalStatus = await AssetInventory.status(forModules: [module])
        if finalStatus == .installed {
            statusUpdate?("\(languageName) speech model ready")
            return locale
        }
        if finalStatus == .unsupported {
            throw SpeechAnalyzerAssetError.unsupportedLanguage(languageName)
        }

        let installedLocales = await SpeechTranscriber.installedLocales
        let installedList = installedLocales
            .map(\.identifier)
            .sorted()
            .joined(separator: ", ")
        throw SpeechAnalyzerAssetError.installationIncomplete(
            languageName,
            locale.identifier,
            String(describing: finalStatus),
            installedList.isEmpty ? "none" : installedList
        )
    }

    func start() async throws {
        try await startWithPreparedLocale(nil)
    }

    func start(preparedLocale: Locale) async throws {
        try await startWithPreparedLocale(preparedLocale)
    }

    private func startWithPreparedLocale(_ preparedLocale: Locale?) async throws {
        if snapshot.isRunning { return }
        try await SpeechPermissions.requestAll()
        await stop()
        _ = try await prepareAnalyzerPipeline(preparedLocale: preparedLocale)

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

        // A smaller tap keeps partial-result cadence close to Android's 50 ms Moonshine capture
        // instead of handing SpeechAnalyzer quarter-second-scale Bluetooth chunks.
        inputNode.installTap(onBus: 0, bufferSize: 1_024, format: format) { buffer, _ in
            audioContinuation.yield(buffer)
        }

        audioEngine.prepare()
        do {
            try audioEngine.start()
            inputSource = .phoneMicrophone
            snapshot.isRunning = true
            armInitialEndpoint()
        } catch {
            inputNode.removeTap(onBus: 0)
            audioContinuation.finish()
            await cancelPreparedPipeline()
            SpeechInputAudioSession.deactivate()
            throw error
        }
    }

    func stop() async {
        endpointTask?.cancel()
        endpointTask = nil
        lastEndpointTranscript = ""

        let wasUsingPhoneMicrophone = inputSource == .phoneMicrophone
        if wasUsingPhoneMicrophone {
            if audioEngine.isRunning {
                audioEngine.stop()
            }
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
            SpeechInputAudioSession.deactivate()
        }
    }

    func startExternalAudio() async throws {
        if snapshot.isRunning { await stop() }
        try await SpeechPermissions.requestRecognition()
        _ = try await prepareAnalyzerPipeline()
        inputSource = .externalPCM
        snapshot.isRunning = true
        armInitialEndpoint()
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
        lastEndpointTranscript = ""
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

    /// Mirrors the Android Moonshine endpoint policy: six seconds for a completely silent turn,
    /// then 1.2 seconds of transcript stability after speech begins. This is transcript-driven,
    /// so normal Bluetooth noise does not keep recognition alive forever.
    private func armInitialEndpoint() {
        endpointTask?.cancel()
        endpointTask = Task { @MainActor [weak self] in
            do {
                try await Task.sleep(for: self?.initialNoSpeechDelay ?? .seconds(6))
            } catch {
                return
            }
            guard let self,
                  snapshot.isRunning,
                  snapshot.transcript.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                return
            }
            await stop()
        }
    }

    private func noteTranscriptActivity(_ text: String) {
        let clean = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty, clean != lastEndpointTranscript else { return }
        lastEndpointTranscript = clean
        endpointTask?.cancel()
        endpointTask = Task { @MainActor [weak self] in
            do {
                try await Task.sleep(for: self?.transcriptSilenceDelay ?? .milliseconds(1_200))
            } catch {
                return
            }
            guard let self,
                  snapshot.isRunning,
                  snapshot.transcript.trimmingCharacters(in: .whitespacesAndNewlines) == clean else {
                return
            }
            await stop()
        }
    }

    private static func join(_ prefix: String, _ suffix: String) -> String {
        guard !prefix.isEmpty else { return suffix }
        guard !suffix.isEmpty else { return prefix }
        if prefix.last?.isWhitespace == true || suffix.first?.isWhitespace == true {
            return prefix + suffix
        }
        return prefix + " " + suffix
    }

    private func prepareAnalyzerPipeline(preparedLocale: Locale? = nil) async throws -> AVAudioFormat {
        let locale: Locale
        if let preparedLocale {
            locale = preparedLocale
        } else {
            locale = try await prepareAssets()
        }
        let module = SpeechTranscriber(locale: locale, preset: .progressiveTranscription)
        guard await AssetInventory.status(forModules: [module]) == .installed else {
            let installed = await SpeechTranscriber.installedLocales
                .map(\.identifier)
                .sorted()
                .joined(separator: ", ")
            throw SpeechAnalyzerAssetError.installationIncomplete(
                languageDisplayName(locale),
                locale.identifier,
                "not installed before analyzer start",
                installed.isEmpty ? "none" : installed
            )
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

    private static func diagnosticDescription(for error: Error) -> String {
        let nsError = error as NSError
        let userInfo = nsError.userInfo
            .map { key, value in "\(key): \(value)" }
            .sorted()
            .joined(separator: ", ")
        let formattedUserInfo = userInfo.isEmpty ? "[:]" : "[\(userInfo)]"
        return "NSError(domain: \(nsError.domain), code: \(nsError.code), " +
            "localizedDescription: \(nsError.localizedDescription), userInfo: \(formattedUserInfo))"
    }

    private func cancelPreparedPipeline() async {
        endpointTask?.cancel()
        endpointTask = nil
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
    case reservationFailed(String, String)
    case installationFailed(String, String)
    case installationIncomplete(String, String, String, String)

    var errorDescription: String? {
        switch self {
        case .moduleUnavailable:
            return "Apple SpeechAnalyzer is not available on this iPhone."
        case .unsupportedLanguage(let language):
            return "Apple SpeechAnalyzer does not support \(language) on this iPhone."
        case .reservationFailed(let language, let reason):
            return "Apple could not reserve the \(language) SpeechAnalyzer model. \(reason)"
        case .installationFailed(let language, let reason):
            return "The \(language) speech model could not be installed. \(reason)"
        case .installationIncomplete(let language, let locale, let status, let installedLocales):
            return "Apple did not finish installing the \(language) SpeechAnalyzer model (\(locale)). Asset status: \(status). Installed SpeechTranscriber locales: \(installedLocales)."
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
