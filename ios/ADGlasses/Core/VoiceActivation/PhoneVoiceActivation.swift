import Combine
import Foundation
import UIKit

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
    #if DEBUG || AD_PERSONAL_TEAM_BUILD
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
    private let lensProcessor = LensImageProcessor()
    private let visualAI = JarvisVisualAIClient()
    private let enabledPreferenceKey = "phoneVoiceActivation.enabled.v1"
    private var applicationIsActive = false
    private var hasForegroundRecordingLease = false
    private var isHandlingWakeTurn = false
    private var isExternallySuspended = false
    private var startListeningTask: Task<Void, Never>?
    private var wakeTurnTimeoutTask: Task<Void, Never>?
    private var liveTranslationMonitorTask: Task<Void, Never>?
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

        app.$isManualTranscription
            .sink { [weak self] _ in self?.evaluate() }
            .store(in: &cancellables)

        app.$transcript
            .sink { [weak self] transcript in self?.transcriptDidChange(transcript) }
            .store(in: &cancellables)
    }

    deinit {
        startListeningTask?.cancel()
        wakeTurnTimeoutTask?.cancel()
        liveTranslationMonitorTask?.cancel()
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

    #if DEBUG || AD_PERSONAL_TEAM_BUILD
    func importModel(from sourceURL: URL, phrase: String) {
        stopListening()
        do {
            try service.importModel(from: sourceURL, phrase: phrase)
            errorMessage = nil
            refreshConfiguration()
            if configurationState == .ready {
                isEnabled = true
            }
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

        if app.isGlassesAssistantAudioActive, isHandlingWakeTurn {
            cancelWakeTurn()
        }

        let shouldListen = isEnabled &&
            configurationState == .ready &&
            glasses.connectionState.isConnected &&
            !app.isManualTranscription &&
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
        guard isHandlingWakeTurn, let app, let glasses else { return }
        wakeTurnTimeoutTask?.cancel()
        wakeTurnTimeoutTask = nil
        isHandlingWakeTurn = false

        // Finalize Apple Speech first so deterministic shortcuts operate on the same final text that
        // a normal Jarvis request would send. The shortcut path never contacts Cloud AI unless the
        // command explicitly asks Lens to understand the captured scene.
        await app.stopTranscription()
        let command = app.transcript.trimmingCharacters(in: .whitespacesAndNewlines)
        if !command.isEmpty, await performWakeShortcut(command, app: app, glasses: glasses) {
            app.clearTranscript()
            evaluate()
            return
        }

        await app.finishPhoneVoiceTranscriptionFromWakeWord()
        evaluate()
    }

    private func performWakeShortcut(
        _ command: String,
        app: AppModel,
        glasses: GlassesManager
    ) async -> Bool {
        let text = Self.normalized(command)
        guard !text.isEmpty, !Self.looksLikeAQuestionAboutHowTo(text) else { return false }

        if let answer = phoneOrGlassesContextAnswer(for: text, glasses: glasses) {
            await speakAndWait(answer, app: app)
            return true
        }

        if Self.isPhotoCaptureCommand(text) {
            guard glasses.connectionState.isConnected, glasses.supports(.camera) else {
                await speakAndWait("Connect AD Glasses with camera support before taking a photo.", app: app)
                return true
            }
            let succeeded = await glasses.requestPhotoCapture()
            await speakAndWait(
                succeeded ? "Photo taken." : (glasses.errorMessage ?? "The photo could not be taken."),
                app: app
            )
            return true
        }

        if Self.isStartVideoCommand(text) {
            guard glasses.connectionState.isConnected else {
                await speakAndWait("Connect AD Glasses before starting video recording.", app: app)
                return true
            }
            if glasses.isVideoRecording {
                await speakAndWait("Video is already recording.", app: app)
                return true
            }
            let succeeded = await glasses.toggleVideoRecording()
            await speakAndWait(
                succeeded ? "Video recording started." : (glasses.errorMessage ?? "Video recording could not start."),
                app: app
            )
            return true
        }

        if Self.isStopVideoCommand(text) {
            guard glasses.connectionState.isConnected else {
                await speakAndWait("AD Glasses are not connected.", app: app)
                return true
            }
            if !glasses.isVideoRecording {
                await speakAndWait("Video recording is already stopped.", app: app)
                return true
            }
            let succeeded = await glasses.toggleVideoRecording()
            await speakAndWait(
                succeeded ? "Video recording stopped." : (glasses.errorMessage ?? "Video recording could not stop."),
                app: app
            )
            return true
        }

        if Self.isStartAudioCommand(text) {
            guard glasses.connectionState.isConnected else {
                await speakAndWait("Connect AD Glasses before starting audio recording.", app: app)
                return true
            }
            if glasses.isAudioRecording {
                await speakAndWait("Audio is already recording.", app: app)
                return true
            }
            let succeeded = await glasses.toggleAudioRecording()
            await speakAndWait(
                succeeded ? "Audio recording started." : (glasses.errorMessage ?? "Audio recording could not start."),
                app: app
            )
            return true
        }

        if Self.isStopAudioCommand(text) {
            guard glasses.connectionState.isConnected else {
                await speakAndWait("AD Glasses are not connected.", app: app)
                return true
            }
            if !glasses.isAudioRecording {
                await speakAndWait("Audio recording is already stopped.", app: app)
                return true
            }
            let succeeded = await glasses.toggleAudioRecording()
            await speakAndWait(
                succeeded ? "Audio recording stopped." : (glasses.errorMessage ?? "Audio recording could not stop."),
                app: app
            )
            return true
        }

        if let target = Self.visibleTextTranslationTarget(in: text) {
            guard let targetCode = Self.languageCode(for: target) else {
                await speakAndWait("I could not identify that target language.", app: app)
                return true
            }
            return await readVisibleText(translatingTo: targetCode, app: app, glasses: glasses)
        }

        if Self.isLensReadCommand(text) {
            return await readVisibleText(translatingTo: nil, app: app, glasses: glasses)
        }

        if Self.isVisualQuestion(text) {
            return await answerVisualQuestion(command, app: app, glasses: glasses)
        }

        if let translation = Self.translationPhrase(in: text) {
            guard let targetCode = Self.languageCode(for: translation.targetLanguage) else {
                await speakAndWait("I could not identify that target language.", app: app)
                return true
            }
            do {
                let result = try await AssistantTranslationBridge.shared.translate(
                    translation.text,
                    targetLanguageCode: targetCode
                )
                await speakAndWait(result.translatedText, app: app, languageCode: result.targetLanguage)
            } catch {
                await speakAndWait(error.localizedDescription, app: app)
            }
            return true
        }

        if Self.isStopLiveTranslationCommand(text) {
            if AssistantTranslationBridge.shared.isLiveRunning {
                await AssistantTranslationBridge.shared.stopLive()
                setExternalAudioSuspended(false)
                await speakAndWait("Live Translation stopped.", app: app)
            } else {
                await speakAndWait("Live Translation is not running.", app: app)
            }
            return true
        }

        if Self.isStartLiveTranslationCommand(text) {
            let pair = Self.liveTranslationPair(in: text)
            let source = pair?.sourceCode ?? defaults.string(forKey: "translation.sourceLanguage.v1") ?? "hi"
            let target = pair?.targetCode ?? defaults.string(forKey: "translation.targetLanguage.v1") ?? "en"
            guard Self.languageBase(source) != Self.languageBase(target) else {
                await speakAndWait("Choose two different languages for Live Translation.", app: app)
                return true
            }

            let sourceName = Self.languageDisplayName(source)
            let targetName = Self.languageDisplayName(target)
            await speakAndWait("Starting Live Translation from \(sourceName) to \(targetName).", app: app)

            setExternalAudioSuspended(true)
            let started = await AssistantTranslationBridge.shared.startLive(
                sourceLanguageCode: source,
                targetLanguageCode: target,
                speechOutput: app.speechOutput
            )
            if !started {
                setExternalAudioSuspended(false)
                await speakAndWait(
                    "Live Translation could not start. Check the language pair and iOS version in Translate.",
                    app: app
                )
                return true
            }

            monitorLiveTranslationUntilStopped()
            return true
        }

        return false
    }

    private func answerVisualQuestion(
        _ question: String,
        app: AppModel,
        glasses: GlassesManager
    ) async -> Bool {
        guard glasses.connectionState.isConnected, glasses.supports(.camera) else {
            await speakAndWait("Connect AD Glasses with camera support first.", app: app)
            return true
        }
        guard let profile = app.aiProfiles.activeProfile else {
            await speakAndWait("Configure Cloud AI in Settings before asking visual questions.", app: app)
            return true
        }
        let credential: String
        do {
            credential = try app.aiProfiles.credential(for: profile.id)
        } catch {
            await speakAndWait(error.localizedDescription, app: app)
            return true
        }
        guard let capture = await glasses.requestVisualCapture() else {
            await speakAndWait(glasses.errorMessage ?? "Lens could not capture what you are looking at.", app: app)
            return true
        }

        do {
            let prepared = try await lensProcessor.prepare(capture.jpegData)
            let answer = try await visualAI.answer(
                question: question,
                imageJPEGData: prepared.jpegData,
                profile: profile,
                credential: credential
            )
            await speakAndWait(answer, app: app)
        } catch {
            await speakAndWait(error.localizedDescription, app: app)
        }
        return true
    }

    private func readVisibleText(
        translatingTo targetLanguageCode: String?,
        app: AppModel,
        glasses: GlassesManager
    ) async -> Bool {
        guard glasses.connectionState.isConnected, glasses.supports(.camera) else {
            await speakAndWait("Connect AD Glasses with camera support first.", app: app)
            return true
        }
        guard let capture = await glasses.requestVisualCapture() else {
            await speakAndWait(glasses.errorMessage ?? "Lens could not capture what you are looking at.", app: app)
            return true
        }

        do {
            let prepared = try await lensProcessor.prepare(capture.jpegData)
            let recognized = try await lensProcessor.recognizeText(in: prepared)
            if let targetLanguageCode {
                let result = try await AssistantTranslationBridge.shared.translate(recognized, targetLanguageCode: targetLanguageCode)
                await speakAndWait(result.translatedText, app: app, languageCode: result.targetLanguage)
            } else {
                await speakAndWait("It says: \(String(recognized.prefix(5_000)))", app: app)
            }
        } catch {
            await speakAndWait(error.localizedDescription, app: app)
        }
        return true
    }

    private func phoneOrGlassesContextAnswer(for text: String, glasses: GlassesManager) -> String? {
        if Self.containsAny(text, ["glasses battery", "battery on my glasses", "battery of my glasses"]) {
            guard let battery = glasses.batteryStatus else {
                return glasses.connectionState.isConnected ? "I cannot read the glasses battery right now." : "AD Glasses are not connected."
            }
            return battery.isCharging ? "AD Glasses are at \(battery.level) percent and charging." : "AD Glasses are at \(battery.level) percent."
        }

        if Self.containsAny(text, ["are my glasses connected", "glasses connected", "connection status"]) {
            return glasses.connectionState.isConnected ? "AD Glasses are connected." : "AD Glasses are not connected."
        }
        if Self.containsAny(text, ["am i recording video", "is video recording", "video recording status"]) {
            return glasses.isVideoRecording ? "Video is recording." : "Video is not recording."
        }
        if Self.containsAny(text, ["am i recording audio", "is audio recording", "audio recording status"]) {
            return glasses.isAudioRecording ? "Audio is recording." : "Audio is not recording."
        }
        if Self.containsAny(text, ["phone battery", "iphone battery", "battery on my phone", "battery of my phone"]) {
            let device = UIDevice.current
            device.isBatteryMonitoringEnabled = true
            let level = device.batteryLevel
            guard level >= 0 else { return "The iPhone battery level is unavailable right now." }
            let percent = Int((level * 100).rounded())
            switch device.batteryState {
            case .charging: return "Your iPhone is at \(percent) percent and charging."
            case .full: return "Your iPhone is fully charged at \(percent) percent."
            case .unplugged, .unknown: return "Your iPhone is at \(percent) percent."
            @unknown default: return "Your iPhone is at \(percent) percent."
            }
        }
        if Self.containsAny(text, ["low power mode", "battery saver"]) {
            return ProcessInfo.processInfo.isLowPowerModeEnabled ? "Low Power Mode is on." : "Low Power Mode is off."
        }
        if Self.containsAny(text, ["is my phone overheating", "is my iphone overheating", "phone thermal", "iphone thermal", "phone too hot"]) {
            switch ProcessInfo.processInfo.thermalState {
            case .nominal: return "The iPhone thermal state is nominal."
            case .fair: return "The iPhone is warmer than nominal, but the thermal state is only fair."
            case .serious: return "The iPhone thermal state is serious. Performance may be reduced."
            case .critical: return "The iPhone thermal state is critical. Let it cool down."
            @unknown default: return "The iPhone thermal state is unavailable."
            }
        }
        if Self.containsAny(text, ["what time is it", "current time", "time now"]) {
            let formatter = DateFormatter(); formatter.locale = .current; formatter.timeZone = .current; formatter.timeStyle = .short
            return "It is \(formatter.string(from: Date()))."
        }
        if Self.containsAny(text, ["what date is it", "today's date", "todays date", "current date"]) {
            let formatter = DateFormatter(); formatter.locale = .current; formatter.timeZone = .current; formatter.dateStyle = .full
            return "Today is \(formatter.string(from: Date()))."
        }
        if Self.containsAny(text, ["phone language", "iphone language", "system language"]) {
            let identifier = Locale.preferredLanguages.first ?? Locale.current.identifier
            let name = Locale.current.localizedString(forIdentifier: identifier) ?? identifier
            return "Your preferred iPhone language is \(name)."
        }
        if Self.containsAny(text, ["ios version", "iphone software version", "phone software version"]) {
            return "This iPhone is running \(UIDevice.current.systemName) \(UIDevice.current.systemVersion)."
        }
        return nil
    }

    private func speakAndWait(_ text: String, app: AppModel, languageCode: String? = nil) async {
        do {
            try app.speechOutput.speak(text, languageCode: languageCode)
            while app.speechOutput.isSpeaking {
                try Task.checkCancellation()
                try await Task.sleep(for: .milliseconds(80))
            }
        } catch is CancellationError {
            app.speechOutput.stop()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func monitorLiveTranslationUntilStopped() {
        liveTranslationMonitorTask?.cancel()
        liveTranslationMonitorTask = Task { [weak self] in
            guard let self else { return }
            while !Task.isCancelled, AssistantTranslationBridge.shared.isLiveRunning {
                do { try await Task.sleep(for: .milliseconds(300)) }
                catch { return }
            }
            guard !Task.isCancelled else { return }
            setExternalAudioSuspended(false)
        }
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
            deactivateIfIdle: !app.isTranscribing && !app.isGlassesAssistantAudioActive && !app.speechOutput.isSpeaking
        )
    }

    private static func normalized(_ raw: String) -> String {
        raw.lowercased()
            .replacingOccurrences(of: "’", with: "'")
            .replacingOccurrences(of: "\n", with: " ")
            .split(whereSeparator: { $0.isWhitespace })
            .joined(separator: " ")
            .trimmingCharacters(in: CharacterSet(charactersIn: " .,!?:;\""))
    }

    private static func containsAny(_ text: String, _ values: [String]) -> Bool { values.contains(where: text.contains) }

    private static func looksLikeAQuestionAboutHowTo(_ text: String) -> Bool {
        text.hasPrefix("how ") || text.hasPrefix("how do ") || text.hasPrefix("how can ") ||
            text.hasPrefix("can i ") || text.hasPrefix("what happens if ")
    }

    private static func isPhotoCaptureCommand(_ text: String) -> Bool {
        let words = Set(text.components(separatedBy: CharacterSet.alphanumerics.inverted).filter { !$0.isEmpty })
        guard words.isDisjoint(with: ["not", "dont", "don't", "never"]), !words.contains("how") else { return false }
        return !words.isDisjoint(with: ["take", "capture", "click", "snap", "shoot"]) &&
            !words.isDisjoint(with: ["photo", "picture", "photograph"])
    }

    private static func isStartVideoCommand(_ text: String) -> Bool {
        containsAny(text, ["start video", "start video recording", "begin video recording", "record a video", "record video", "start recording video"]) &&
            !containsAny(text, ["stop", "don't", "do not"])
    }

    private static func isStopVideoCommand(_ text: String) -> Bool {
        containsAny(text, ["stop video", "stop video recording", "end video recording", "stop recording video"])
    }

    private static func isStartAudioCommand(_ text: String) -> Bool {
        containsAny(text, ["start audio", "start audio recording", "begin audio recording", "record audio", "start recording audio", "start voice recording"]) &&
            !containsAny(text, ["stop", "don't", "do not"])
    }

    private static func isStopAudioCommand(_ text: String) -> Bool {
        containsAny(text, ["stop audio", "stop audio recording", "end audio recording", "stop recording audio", "stop voice recording"])
    }

    private static func isLensReadCommand(_ text: String) -> Bool {
        containsAny(text, ["read this", "read the text", "read what i see", "read what i'm seeing", "what does this say", "scan this text", "scan the text", "read this sign", "read this page", "read this label"])
    }

    private static func isVisualQuestion(_ text: String) -> Bool {
        containsAny(text, [
            "what am i looking at", "what am i seeing", "what do you see", "what can you see",
            "describe what i see", "describe what i'm seeing", "describe what i am seeing",
            "describe what i'm looking at", "describe what i am looking at", "describe the scene",
            "what is in front of me", "what's in front of me", "whats in front of me",
            "what is this", "what's this", "whats this", "identify this object", "identify this",
            "explain what i'm looking at", "explain what i am looking at",
            "is there anything important here", "anything important in front of me"
        ]) && !isLensReadCommand(text)
    }

    private static func visibleTextTranslationTarget(in text: String) -> String? {
        guard containsAny(text, ["translate this", "translate what i see", "translate what i'm seeing", "translate this sign", "translate this page", "translate this text"]),
              let range = text.range(of: " to ", options: .backwards) else { return nil }
        return String(text[range.upperBound...]).trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func translationPhrase(in text: String) -> (text: String, targetLanguage: String)? {
        if text.hasPrefix("translate "), let range = text.range(of: " to ", options: .backwards) {
            let start = text.index(text.startIndex, offsetBy: "translate ".count)
            guard start < range.lowerBound else { return nil }
            let phrase = String(text[start..<range.lowerBound]).trimmingCharacters(in: .whitespacesAndNewlines)
            let language = String(text[range.upperBound...]).trimmingCharacters(in: .whitespacesAndNewlines)
            guard !phrase.isEmpty, !language.isEmpty, !["this", "what i see", "what i'm seeing"].contains(phrase) else { return nil }
            return (phrase, language)
        }
        if text.hasPrefix("say "), let range = text.range(of: " in ", options: .backwards) {
            let start = text.index(text.startIndex, offsetBy: "say ".count)
            guard start < range.lowerBound else { return nil }
            let phrase = String(text[start..<range.lowerBound]).trimmingCharacters(in: .whitespacesAndNewlines)
            let language = String(text[range.upperBound...]).trimmingCharacters(in: .whitespacesAndNewlines)
            guard !phrase.isEmpty, !language.isEmpty else { return nil }
            return (phrase, language)
        }
        return nil
    }

    private static func isStartLiveTranslationCommand(_ text: String) -> Bool {
        containsAny(text, ["start live translation", "start translation mode", "begin live translation", "turn on live translation", "start live translate"])
    }

    private static func isStopLiveTranslationCommand(_ text: String) -> Bool {
        containsAny(text, ["stop live translation", "stop translation mode", "end live translation", "turn off live translation"])
    }

    private static func liveTranslationPair(in text: String) -> (sourceCode: String, targetCode: String)? {
        guard let fromRange = text.range(of: " from "),
              let toRange = text.range(of: " to ", range: fromRange.upperBound..<text.endIndex) else { return nil }
        let sourceName = String(text[fromRange.upperBound..<toRange.lowerBound]).trimmingCharacters(in: .whitespacesAndNewlines)
        let targetName = String(text[toRange.upperBound...]).trimmingCharacters(in: .whitespacesAndNewlines)
        guard let sourceCode = languageCode(for: sourceName), let targetCode = languageCode(for: targetName) else { return nil }
        return (sourceCode, targetCode)
    }

    private static func languageCode(for raw: String) -> String? {
        let value = raw.lowercased().trimmingCharacters(in: CharacterSet(charactersIn: " .,!?:;"))
        let aliases: [String: String] = [
            "english": "en", "en": "en", "hindi": "hi", "hi": "hi", "bengali": "bn", "bangla": "bn", "bn": "bn",
            "spanish": "es", "es": "es", "french": "fr", "fr": "fr", "german": "de", "de": "de", "italian": "it", "it": "it",
            "portuguese": "pt", "pt": "pt", "japanese": "ja", "ja": "ja", "korean": "ko", "ko": "ko",
            "chinese": "zh-Hans", "mandarin": "zh-Hans", "simplified chinese": "zh-Hans", "traditional chinese": "zh-Hant",
            "arabic": "ar", "ar": "ar", "russian": "ru", "ru": "ru", "turkish": "tr", "tr": "tr", "dutch": "nl", "nl": "nl",
            "polish": "pl", "pl": "pl", "thai": "th", "th": "th", "vietnamese": "vi", "vi": "vi", "indonesian": "id", "id": "id",
            "ukrainian": "uk", "uk": "uk"
        ]
        return aliases[value]
    }

    private static func languageDisplayName(_ code: String) -> String {
        Locale.current.localizedString(forIdentifier: code) ?? Locale.current.localizedString(forLanguageCode: languageBase(code)) ?? code
    }

    private static func languageBase(_ code: String) -> String {
        code.lowercased().split(separator: "-").first.map(String.init) ?? code.lowercased()
    }
}
