import Foundation

/// SpeechAnalyzer-only factory for AD voice input.
///
/// iOS 26+ uses SpeechAnalyzer directly. AD never falls back to the legacy
/// `SFSpeechRecognizer` recognition pipeline when a model is downloading, unavailable, or fails
/// to prepare. On older systems the voice feature reports that SpeechAnalyzer requires iOS 26.
enum AppleSpeechTranscriber {
    /// Assistant voice input is an English product surface today. Do not inherit `Locale.current`:
    /// a device region such as India can otherwise silently select en-IN and force a different
    /// speech asset than the app actually intends to use.
    static let assistantLocale = Locale(identifier: "en-US")

    @MainActor
    static func make(locale: Locale = assistantLocale) -> any SpeechTranscribing {
#if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            return SpeechAnalyzerTranscriber(locale: locale)
        }
#endif
        return SpeechAnalyzerUnavailableTranscriber()
    }
}

@MainActor
private final class SpeechAnalyzerUnavailableTranscriber: SpeechTranscribing {
    let engineName = "SpeechAnalyzer unavailable"
    var onUpdate: ((SpeechTranscriptionSnapshot) -> Void)?
    var onError: ((Error) -> Void)?

    private(set) var snapshot = SpeechTranscriptionSnapshot(
        transcript: "",
        isRunning: false,
        engineName: "SpeechAnalyzer unavailable"
    )

    func start() async throws {
        throw SpeechTranscriptionError.recognizerUnavailable
    }

    func stop() async {}

    func resetTranscript() {
        snapshot.transcript = ""
        onUpdate?(snapshot)
    }
}
