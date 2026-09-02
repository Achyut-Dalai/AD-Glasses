import Foundation

/// SpeechAnalyzer-only factory for AD voice input.
///
/// AD Glasses now targets iOS 26+, so there is no legacy recognizer, older-OS fallback, or runtime
/// availability branch. Model preparation/download failures stay inside the SpeechAnalyzer lifecycle
/// instead of silently switching engines.
enum AppleSpeechTranscriber {
    /// Assistant voice input is an English product surface today. Do not inherit `Locale.current`:
    /// a device region such as India can otherwise silently select en-IN and force a different
    /// speech asset than the app actually intends to use.
    static let assistantLocale = Locale(identifier: "en-US")

    @MainActor
    static func make(locale: Locale = assistantLocale) -> any SpeechTranscribing {
        SpeechAnalyzerTranscriber(locale: locale)
    }
}
