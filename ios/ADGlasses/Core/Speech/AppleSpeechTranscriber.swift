import Foundation

enum AppleSpeechTranscriber {
    /// Assistant voice input is an English product surface today. Do not inherit `Locale.current`:
    /// a device region such as India can otherwise silently select en-IN and force a different
    /// SpeechAnalyzer asset than the app actually intends to use.
    static let assistantLocale = Locale(identifier: "en-US")

    @MainActor
    static func make(locale: Locale = assistantLocale) -> any SpeechTranscribing {
#if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            return SpeechAnalyzerTranscriber(locale: locale)
        }
#endif
        // iOS 17–25 compatibility path. On iOS 26+, SpeechAnalyzer is the only runtime engine;
        // we do not silently switch engines when an asset is missing or installation fails.
        return LegacySpeechTranscriber(locale: locale)
    }
}
