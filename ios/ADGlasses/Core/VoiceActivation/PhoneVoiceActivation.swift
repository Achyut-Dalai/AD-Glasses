import Combine
import Foundation

enum PhoneWakeWordConfigurationState: Equatable, Sendable {
    case ready
    case missingModel
    case unavailable(String)

    var label: String {
        switch self {
        case .ready: return "Ready"
        case .missingModel: return "Wake-word model required"
        case .unavailable(let reason): return reason
        }
    }
}

@MainActor
protocol PhoneWakeWordDetecting: AnyObject {
    var phrase: String { get }
    var configurationState: PhoneWakeWordConfigurationState { get }

    func start(onDetection: @escaping @MainActor () -> Void) async throws
    func stop()
    #if DEBUG
    func importModel(from sourceURL: URL, phrase: String) throws
    #endif
}

/// Owns product policy for phone wake-word listening. The engine itself remains replaceable.
/// Listening starts in foreground, remains connection-bound, and may then continue while the user
/// switches apps or locks the phone. It is suspended for competing audio tasks without releasing
/// the foreground-established recording-session lease.
@MainActor
final class PhoneVoiceActivationController: ObservableObject {
    @Published var isEnabled: Bool {
        didSet {
            defaults.set(isEnabled, forKey: enabledPreferenceKey)
            evaluate()
        }
    }
    @Published private(set) var isListening = false
    @Published private(set) var configurationState: PhoneWakeWordConfigurationState
    @Published var errorMessage: String?

    var phrase: String { service.phrase }

    private let service: any PhoneWakeWordDetecting
    private weak var glasses: GlassesManager?
    private weak var app: AppModel?
    private let defaults: UserDefaults
    private let enabledPreferenceKey = "phoneVoiceActivation.enabled.v1"
    private var applicationIsActive = false
    private var hasForegroundRecordingLease = false
    private var isHandlingWakeTurn = false
    private var isExternallySuspended = false
    private var startListeningTask: Task<Void, Never>?
    private var wakeTurnTimeoutTask: Task<Void, Never>?
    private var cancellables = Set<AnyCancellable>()

    private let initialSpeechTimeout: Duration = .seconds(6)
    private let endOfSpeechDelay: Duration = .milliseconds(1400)

    init(
        service: any PhoneWakeWordDetecting,
        glasses: GlassesManager,
        app: AppModel,
        defaults: UserDefaults = .standard
    ) {
        self.service = service
        self.glasses = glasses
        self.app = app
        self.defaults = defaults
        isEnabled = defaults.bool(forKey: enabledPreferenceKey)
        configurationState = service.configurationState

        glasses.$activeProviderID
            .combineLatest(glasses.$providers)
            .sink { [weak self] _, _ in self?.evaluate() }
            .store(in: &cancellables)

        Publishers.CombineLatest3(
            app.$isTranscribing,
            app.$isGlassesAssistantAudioActive,
            app.speechOutput.$isSpeaking
        )
            .sink { [weak self] _, _, _ in self?.evaluate() }
            .store(in: &cancellables)

        app.$transcript
            .sink { [weak self] transcript in self?.transcriptDidChange(transcript) }
            .store(in: &cancellables)
    }

    func setApplicationActive(_ active: Bool) {
        applicationIsActive = active
        evaluate()
    }

    /// Temporarily yields the microphone/audio session to a foreground feature such as Live
    /// Translation without changing the user's persisted wake-word preference.
    func setExternalAudioSuspended(_ suspended: Bool) {
        guard isExternallySuspended != suspended else { return }
        isExternallySuspended = suspended
        evaluate()
    }

    #if DEBUG
    func importModel(from sourceURL: URL, phrase: String) {
        stopListening()
        do {
            try service.importModel(from: sourceURL, phrase: phrase)
            errorMessage = nil
            refreshConfiguration()
        } catch {
            errorMessage = error.localizedDescription
            refreshConfiguration()
        }
    }
    #endif

    func refreshConfiguration() {
        configurationState = service.configurationState
        evaluate()
    }

    private func evaluate() {
        configurationState = service.configurationState
        guard let glasses, let app else {
            stopListening()
            return
        }

        if isExternallySuspended {
            cancelWakeTurn()
            stopListening()
            releaseForegroundRecordingLease(app: app)
            return
        }

        let shouldListen = isEnabled &&
            configurationState == .ready &&
            glasses.connectionState.isConnected &&
            !app.isTranscribing &&
            !app.isGlassesAssistantAudioActive &&
            !app.speechOutput.isSpeaking

        guard isEnabled,
              configurationState == .ready,
              glasses.connectionState.isConnected else {
            cancelWakeTurn()
            stopListening()
            releaseForegroundRecordingLease(app: app)
            return
        }

        if shouldListen && (applicationIsActive || hasForegroundRecordingLease) {
            startListeningIfNeeded()
        } else {
            stopListening()
        }
    }

    private func startListeningIfNeeded() {
        guard !isListening, startListeningTask == nil else { return }

        startListeningTask = Task { [weak self] in
            guard let self else { return }
            do {
                try await service.start { [weak self] in self?.wakeWordDetected() }
                try Task.checkCancellation()

                isListening = true
                if applicationIsActive, !hasForegroundRecordingLease {
                    hasForegroundRecordingLease = true
                    VoiceAudioSessionContinuity.shared.holdRecordingSession()
                }
                errorMessage = nil
            } catch is CancellationError {
                service.stop()
                isListening = false
            } catch {
                service.stop()
                isListening = false
                errorMessage = error.localizedDescription
            }

            startListeningTask = nil
            evaluate()
        }
    }

    private func stopListening() {
        startListeningTask?.cancel()
        service.stop()
        isListening = false
    }

    private func wakeWordDetected() {
        guard isListening,
              !isHandlingWakeTurn,
              let app,
              !app.isGenerating,
              !app.speechOutput.isSpeaking else { return }
        isHandlingWakeTurn = true
        stopListening()
        Task {
            let didStart = await app.startPhoneVoiceTranscriptionFromWakeWord()
            guard didStart else {
                isHandlingWakeTurn = false
                evaluate()
                return
            }
            scheduleWakeTurnCompletion(after: initialSpeechTimeout)
            evaluate()
        }
    }

    private func transcriptDidChange(_ transcript: String) {
        guard isHandlingWakeTurn,
              !transcript.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        scheduleWakeTurnCompletion(after: endOfSpeechDelay)
    }

    private func scheduleWakeTurnCompletion(after delay: Duration) {
        wakeTurnTimeoutTask?.cancel()
        wakeTurnTimeoutTask = Task { [weak self] in
            do {
                try await Task.sleep(for: delay)
            } catch {
                return
            }
            await self?.completeWakeTurn()
        }
    }

    private func completeWakeTurn() async {
        guard isHandlingWakeTurn, let app else { return }
        wakeTurnTimeoutTask?.cancel()
        wakeTurnTimeoutTask = nil
        isHandlingWakeTurn = false
        await app.finishPhoneVoiceTranscriptionFromWakeWord()
        evaluate()
    }

    private func cancelWakeTurn() {
        wakeTurnTimeoutTask?.cancel()
        wakeTurnTimeoutTask = nil
        isHandlingWakeTurn = false
    }

    private func releaseForegroundRecordingLease(app: AppModel) {
        guard hasForegroundRecordingLease else { return }
        hasForegroundRecordingLease = false
        VoiceAudioSessionContinuity.shared.releaseRecordingSession(
            deactivateIfIdle: !app.isTranscribing &&
                !app.isGlassesAssistantAudioActive &&
                !app.speechOutput.isSpeaking
        )
    }
}
