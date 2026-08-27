import Foundation

enum AppleSpeechTranscriber {
    @MainActor
    static func make(locale: Locale = .current) -> any SpeechTranscribing {
#if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            return SpeechAnalyzerTranscriber(locale: locale)
        }
#endif
        return LegacySpeechTranscriber(locale: locale)
    }
}
