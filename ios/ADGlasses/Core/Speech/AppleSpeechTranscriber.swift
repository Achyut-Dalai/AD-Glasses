import Foundation

/// Selects the native Apple speech implementation for the current OS.
///
/// On iOS 26+, AD uses SpeechAnalyzer directly. There is no runtime fallback to
/// `SFSpeechRecognizer` for transient model/download/setup states: those states belong to the
/// SpeechAnalyzer lifecycle and must be surfaced/recovered there instead of silently changing the
/// recognition engine. The legacy recognizer exists only for OS versions that do not provide
/// SpeechAnalyzer.
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
        // Required compatibility path for systems where SpeechAnalyzer does not exist.
        return LegacySpeechTranscriber(locale: locale)
    }
}
