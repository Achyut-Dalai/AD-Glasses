import AVFoundation
import Foundation
import Speech

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
    private let requestedLocale: Locale
    private let analyzer: SpeechAnalyzerTranscriber
    private let legacy: LegacySpeechTranscriber
    private var active: any ExternalAudioSpeechTranscribing
    private var isUsingLegacy = false

    var engineName: String { active.engineName }
    var snapshot: SpeechTranscriptionSnapshot { active.snapshot }
    var onUpdate: ((SpeechTranscriptionSnapshot) -> Void)?
    var onError: ((Error) -> Void)?

    init(locale: Locale) {
        requestedLocale = locale
        let analyzer = SpeechAnalyzerTranscriber(locale: locale)
        let legacy = LegacySpeechTranscriber(locale: locale)
        self.analyzer = analyzer
        self.legacy = legacy
        active = analyzer

        analyzer.onUpdate = { [weak self] snapshot in
            guard let self, !self.isUsingLegacy else { return }
            self.onUpdate?(snapshot)
        }
        analyzer.onError = { [weak self] error in
            guard let self, !self.isUsingLegacy else { return }
            self.onError?(error)
        }
        legacy.onUpdate = { [weak self] snapshot in
            guard let self, self.isUsingLegacy else { return }
            self.onUpdate?(snapshot)
        }
        legacy.onError = { [weak self] error in
            guard let self, self.isUsingLegacy else { return }
            self.onError?(error)
        }
    }

    func start() async throws {
        await selectEngineForNextTurn()
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
        await selectEngineForNextTurn()
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

    /// Regional English SpeechAnalyzer assets have been unreliable on some iOS 26 devices.
    /// If that exact model is not already installed, use SFSpeechRecognizer immediately instead
    /// of holding the user's first Ask turn behind the asset installation retry window.
    private func selectEngineForNextTurn() async {
        guard !isUsingLegacy,
              Self.isRegionalEnglish(requestedLocale),
              SpeechTranscriber.isAvailable,
              let locale = await SpeechTranscriber.supportedLocale(equivalentTo: requestedLocale) else {
            return
        }

        let module = SpeechTranscriber(locale: locale, preset: .progressiveTranscription)
        guard await AssetInventory.status(forModules: [module]) != .installed else { return }
        await fallBackToLegacy()
    }

    private func fallBackToLegacy() async {
        guard !isUsingLegacy else { return }
        isUsingLegacy = true
        active = legacy
        await analyzer.stop()
        analyzer.resetTranscript()
        legacy.resetTranscript()
    }

    private static func isRegionalEnglish(_ locale: Locale) -> Bool {
        let normalized = locale.identifier
            .replacingOccurrences(of: "_", with: "-")
            .lowercased()
        let components = normalized.split(separator: "-")
        guard components.first == "en", components.count > 1 else { return false }
        return components[1] != "us"
    }
}
#endif
