import Foundation

enum AppleSpeechTranscriber {
    @MainActor
    static func make(locale: Locale = .current) -> any SpeechTranscribing {
#if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            return ResilientAppleSpeechTranscriber(locale: locale)
        }
#endif
        return LegacySpeechTranscriber(locale: locale)
    }
}

#if compiler(>=6.2)
@available(iOS 26.0, *)
@MainActor
private final class ResilientAppleSpeechTranscriber: ExternalAudioSpeechTranscribing {
    private let analyzer: SpeechAnalyzerTranscriber
    private let legacy: LegacySpeechTranscriber
    private var active: any ExternalAudioSpeechTranscribing
    private var isUsingLegacy = false

    var engineName: String { active.engineName }
    var snapshot: SpeechTranscriptionSnapshot { active.snapshot }
    var onUpdate: ((SpeechTranscriptionSnapshot) -> Void)?
    var onError: ((Error) -> Void)?

    init(locale: Locale) {
        let analyzer = SpeechAnalyzerTranscriber(locale: locale)
        let legacy = LegacySpeechTranscriber(locale: locale)
        self.analyzer = analyzer
        self.legacy = legacy
        active = analyzer

        analyzer.onUpdate = { [weak self] snapshot in
            guard let self, !isUsingLegacy else { return }
            onUpdate?(snapshot)
        }
        analyzer.onError = { [weak self] error in
            guard let self, !isUsingLegacy else { return }
            onError?(error)
        }
        legacy.onUpdate = { [weak self] snapshot in
            guard let self, isUsingLegacy else { return }
            onUpdate?(snapshot)
        }
        legacy.onError = { [weak self] error in
            guard let self, isUsingLegacy else { return }
            onError?(error)
        }
    }

    func start() async throws {
        if isUsingLegacy {
            try await legacy.start()
            return
        }

        do {
            try await analyzer.start()
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            await fallBackToLegacy()
            try await legacy.start()
        }
    }

    func stop() async {
        await active.stop()
    }

    func resetTranscript() {
        analyzer.resetTranscript()
        legacy.resetTranscript()
    }

    func startExternalAudio() async throws {
        if isUsingLegacy {
            try await legacy.startExternalAudio()
            return
        }

        do {
            try await analyzer.startExternalAudio()
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            await fallBackToLegacy()
            try await legacy.startExternalAudio()
        }
    }

    func appendExternalAudio(_ buffer: AVAudioPCMBuffer) {
        active.appendExternalAudio(buffer)
    }

    func finishExternalAudio() async {
        await active.finishExternalAudio()
    }

    private func fallBackToLegacy() async {
        await analyzer.stop()
        analyzer.resetTranscript()
        legacy.resetTranscript()
        isUsingLegacy = true
        active = legacy
    }
}
#endif
