import AVFoundation
import Foundation

/// SpeechAnalyzer-only factory for AD voice input.
///
/// AD Glasses targets iOS 27+, so there is no legacy recognizer, older-OS fallback, or runtime
/// availability branch. Model preparation/download failures stay inside the SpeechAnalyzer lifecycle
/// instead of silently switching engines.
enum AppleSpeechTranscriber {
    /// AD's Assistant is an English surface, but the primary user/device locale is India. Use the
    /// English-India SpeechAnalyzer asset explicitly instead of inheriting Locale.current or forcing
    /// en-US. This improves recognition of Indian names, pronunciation, and accent without changing
    /// the language of the Assistant response.
    static let assistantLocale = Locale(identifier: "en-IN")

    @MainActor
    static func make(
        locale: Locale = assistantLocale,
        groqCredentialProvider: (@MainActor @Sendable () -> String?)? = nil
    ) -> any SpeechTranscribing {
        SpeechAnalyzerTranscriber(
            locale: locale,
            groqCredentialProvider: groqCredentialProvider
        )
    }
}
