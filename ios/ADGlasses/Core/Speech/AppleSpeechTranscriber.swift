import AVFoundation
import Foundation

#if compiler(>=6.2)
@available(iOS 26.0, *)
@MainActor
private final class ResilientAppleSpeechTranscriber: ExternalAudioSpeechTranscribing {
    private enum Backend: Equatable {
        case speechAnalyzer
        case legacy
    }

    private enum InputKind {
        case phoneMicrophone
        case externalPCM
    }

    var engineName: String { snapshot.engineName }
    var onUpdate: ((SpeechTranscriptionSnapshot) -> Void)?
    var onError: ((Error) -> Void)?

    private(set) var snapshot: SpeechTranscriptionSnapshot {
        didSet { onUpdate?(snapshot) }
    }

    private let analyzer: SpeechAnalyzerTranscriber
    private let legacy: LegacySpeechTranscriber
    private var activeBackend: Backend?
    private var preferLegacyForSession = false

    init(locale: Locale) {
        analyzer = SpeechAnalyzerTranscriber(locale: locale)
        legacy = LegacySpeechTranscriber(locale: locale)
        snapshot = SpeechTranscriptionSnapshot(
            transcript: "",
            isRunning: false,
            engineName: "Apple Speech"
        )

        analyzer.onUpdate = { [weak self] update in
            self?.receive(update, from: .speechAnalyzer)
        }
        analyzer.onError = { [weak self] error in
            self?.receive(error, from: .speechAnalyzer)
        }
        legacy.onUpdate = { [weak self] update in
            self?.receive(update, from: .legacy)
        }
        legacy.onError = { [weak self] error in
            self?.receive(error, from: .legacy)
        }
    }

    func start() async throws {
        guard !snapshot.isRunning else { return }
        try await startInput(.phoneMicrophone)
    }

    func stop() async {
        switch activeBackend {
        case .speechAnalyzer:
            await analyzer.stop()
        case .legacy:
            await legacy.stop()
        case nil:
            break
        }
        activeBackend = nil
        if snapshot.isRunning {
            snapshot.isRunning = false
        }
    }

    func resetTranscript() {
        analyzer.resetTranscript()
        legacy.resetTranscript()
        if !snapshot.transcript.isEmpty {
            snapshot.transcript = ""
        }
    }

    func startExternalAudio() async throws {
        guard !snapshot.isRunning else { return }
        try await startInput(.externalPCM)
    }

    func appendExternalAudio(_ buffer: AVAudioPCMBuffer) {
        switch activeBackend {
        case .speechAnalyzer:
            analyzer.appendExternalAudio(buffer)
        case .legacy:
            legacy.appendExternalAudio(buffer)
        case nil:
            break
        }
    }

    func finishExternalAudio() async {
        switch activeBackend {
        case .speechAnalyzer:
            await analyzer.finishExternalAudio()
        case .legacy:
            await legacy.finishExternalAudio()
        case nil:
            break
        }
        activeBackend = nil
        if snapshot.isRunning {
            snapshot.isRunning = false
        }
    }

    private func startInput(_ input: InputKind) async throws {
        if preferLegacyForSession {
            try await startLegacy(input)
            return
        }

        activeBackend = .speechAnalyzer
        do {
            switch input {
            case .phoneMicrophone:
                try await analyzer.start()
            case .externalPCM:
                try await analyzer.startExternalAudio()
            }
        } catch is CancellationError {
            activeBackend = nil
            throw CancellationError()
        } catch {
            // SpeechAnalyzer's language assets are system-managed and may temporarily report
            // supported/downloading even after earlier turns succeeded. Voice input must not become
            // unusable just because that optional on-device backend is being repaired or fetched.
            // Fall back to the already-supported SFSpeechRecognizer path for the remainder of this
            // app session so phone Ask and glasses PCM keep the same Assistant behavior.
            preferLegacyForSession = true
            activeBackend = nil
            NSLog(
                "%@",
                "[AD Speech] SpeechAnalyzer setup failed; using Apple Speech for this session: \(error.localizedDescription)"
            )
            try await startLegacy(input)
        }
    }

    private func startLegacy(_ input: InputKind) async throws {
        activeBackend = .legacy
        do {
            switch input {
            case .phoneMicrophone:
                try await legacy.start()
            case .externalPCM:
                try await legacy.startExternalAudio()
            }
        } catch is CancellationError {
            activeBackend = nil
            throw CancellationError()
        } catch {
            activeBackend = nil
            throw error
        }
    }

    private func receive(_ update: SpeechTranscriptionSnapshot, from backend: Backend) {
        guard activeBackend == backend else { return }
        snapshot = update
    }

    private func receive(_ error: Error, from backend: Backend) {
        guard activeBackend == backend else { return }
        if backend == .speechAnalyzer {
            // A runtime SpeechAnalyzer failure cannot be replayed without losing already-consumed
            // audio, but the next turn can recover automatically through the legacy recognizer.
            preferLegacyForSession = true
        }
        onError?(error)
    }
}
#endif

enum AppleSpeechTranscriber {
    /// Assistant voice input is an English product surface today. Do not inherit `Locale.current`:
    /// a device region such as India can otherwise silently select en-IN and force a different
    /// speech asset than the app actually intends to use.
    static let assistantLocale = Locale(identifier: "en-US")

    @MainActor
    static func make(locale: Locale = assistantLocale) -> any SpeechTranscribing {
#if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            // Prefer SpeechAnalyzer when its system-managed model is ready, but do not make a
            // transient asset download/state failure fatal. The fallback supports both phone mic
            // and provider-decoded external PCM, so every Assistant entry point stays functional.
            return ResilientAppleSpeechTranscriber(locale: locale)
        }
#endif
        return LegacySpeechTranscriber(locale: locale)
    }
}
