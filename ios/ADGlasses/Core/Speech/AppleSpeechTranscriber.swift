import Foundation

enum AppleSpeechTranscriber {
    @MainActor
    static func make(locale: Locale = .current) -> any SpeechTranscribing {
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
