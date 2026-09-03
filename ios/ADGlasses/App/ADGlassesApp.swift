import AppIntents
import BackgroundTasks
import Combine
import FoundationModels
import SwiftUI
import UIKit

@MainActor
final class AppOrientationController {
    static let shared = AppOrientationController()

    private(set) var supportedOrientations: UIInterfaceOrientationMask = .portrait

    private init() {}

    func usePortraitOnly() {
        update(to: .portrait)
    }

    func allowMediaOrientation() {
        update(to: .allButUpsideDown)
    }

    private func update(to orientations: UIInterfaceOrientationMask) {
        supportedOrientations = orientations
        for scene in UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }) {
            scene.requestGeometryUpdate(
                .iOS(interfaceOrientations: orientations)
            )
        }
    }
}

/// Owns the iOS 27 continued-processing lease used by long user-initiated work. The actual work
/// still belongs to the feature/model that started it; this object only keeps the system task alive,
/// reports progress, and closes it deterministically. If the scheduler can't grant a continued task,
/// the foreground operation is still allowed to proceed normally.
@MainActor
final class ADContinuedProcessingCoordinator {
    static let shared = ADContinuedProcessingCoordinator()
    static let identifier = "com.achyutdalai.ADGlasses.continued-processing"

    private var activeTask: BGContinuedProcessingTask?
    private var activeWorkID: UUID?
    private var pendingCompletion: Bool?
    private var progressFraction: Double = 0

    private init() {}

    func register() {
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: Self.identifier,
            using: nil
        ) { task in
            guard let continuedTask = task as? BGContinuedProcessingTask else {
                task.setTaskCompleted(success: false)
                return
            }
            Task { @MainActor in
                Self.shared.attach(continuedTask)
            }
        }
    }

    @discardableResult
    func begin(title: String, subtitle: String) -> UUID {
        if let activeWorkID {
            finish(activeWorkID, success: false)
        }

        let workID = UUID()
        activeWorkID = workID
        pendingCompletion = nil
        progressFraction = 0

        let request = BGContinuedProcessingTaskRequest(
            identifier: Self.identifier,
            title: title,
            subtitle: subtitle
        )
        // AD continued work is coupled to the foreground action that started it. Don't enqueue a
        // hardware/media job to run later after the relevant glasses session has changed.
        request.strategy = .fail

        // iOS 27 replaces synchronous submit(_:) with asynchronous task submission. Apple also
        // recommends keeping submission off the main thread, so this best-effort system lease is
        // requested independently from the user-visible foreground operation.
        Task.detached(priority: .utility) {
            do {
                try await BGTaskScheduler.shared.submitTaskRequest(request)
            } catch {
                // Continued processing is an enhancement, not a prerequisite for the user action.
                // The caller continues its foreground task and simply won't have a system lease.
            }
        }
        return workID
    }

    func update(_ workID: UUID, completed: Int, total: Int, subtitle: String? = nil) {
        guard activeWorkID == workID else { return }
        let denominator = max(total, 1)
        progressFraction = min(max(Double(completed) / Double(denominator), 0), 1)
        activeTask?.progress.totalUnitCount = 1_000
        activeTask?.progress.completedUnitCount = Int64(progressFraction * 1_000)
        if let subtitle {
            activeTask?.updateTitle("AD Glasses", subtitle: subtitle)
        }
    }

    func finish(_ workID: UUID, success: Bool) {
        guard activeWorkID == workID else { return }
        if let activeTask {
            activeTask.setTaskCompleted(success: success)
            clear()
        } else {
            // The scheduler may deliver the launch handler just after the foreground work finishes.
            // Remember the outcome so the task can be closed immediately if that happens.
            pendingCompletion = success
        }
    }

    private func attach(_ task: BGContinuedProcessingTask) {
        guard activeWorkID != nil else {
            task.setTaskCompleted(success: false)
            return
        }

        activeTask = task
        task.progress.totalUnitCount = 1_000
        task.progress.completedUnitCount = Int64(progressFraction * 1_000)
        task.expirationHandler = { [weak self] in
            Task { @MainActor in
                guard let self, let workID = self.activeWorkID else { return }
                self.finish(workID, success: false)
            }
        }

        if let pendingCompletion {
            task.setTaskCompleted(success: pendingCompletion)
            clear()
        }
    }

    private func clear() {
        activeTask = nil
        activeWorkID = nil
        pendingCompletion = nil
        progressFraction = 0
    }
}

@MainActor
final class ADGlassesAppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        ADContinuedProcessingCoordinator.shared.register()
        return true
    }

    func application(
        _ application: UIApplication,
        supportedInterfaceOrientationsFor window: UIWindow?
    ) -> UIInterfaceOrientationMask {
        AppOrientationController.shared.supportedOrientations
    }
}

private enum ADPendingSystemAction: String {
    case connect
    case ask
    case photo
    case video
    case audio
}

private enum ADPendingSystemActionStore {
    private static let key = "system.pending-action.v1"

    static func enqueue(_ action: ADPendingSystemAction) {
        UserDefaults.standard.set(action.rawValue, forKey: key)
        NotificationCenter.default.post(name: .adPendingSystemAction, object: nil)
    }

    static func consume() -> ADPendingSystemAction? {
        guard let raw = UserDefaults.standard.string(forKey: key),
              let action = ADPendingSystemAction(rawValue: raw) else {
            return nil
        }
        UserDefaults.standard.removeObject(forKey: key)
        return action
    }
}

private extension Notification.Name {
    static let adPendingSystemAction = Notification.Name("com.achyutdalai.ADGlasses.pending-system-action")
}

struct ConnectADGlassesIntent: AppIntent {
    static let title: LocalizedStringResource = "Connect AD Glasses"
    static let description = IntentDescription("Reconnects the most recently paired AD Glasses.")
    static let supportedModes: IntentModes = [.foreground(.immediate)]

    func perform() async throws -> some IntentResult {
        ADPendingSystemActionStore.enqueue(.connect)
        return .result()
    }
}

struct AskADGlassesIntent: AppIntent {
    static let title: LocalizedStringResource = "Ask AD Glasses"
    static let description = IntentDescription("Opens AD Glasses and starts a voice Ask turn.")
    static let supportedModes: IntentModes = [.foreground(.immediate)]

    func perform() async throws -> some IntentResult {
        ADPendingSystemActionStore.enqueue(.ask)
        return .result()
    }
}

struct TakeADGlassesPhotoIntent: AppIntent {
    static let title: LocalizedStringResource = "Take AD Glasses Photo"
    static let description = IntentDescription("Takes a photo using the connected AD Glasses.")
    static let supportedModes: IntentModes = [.foreground(.immediate)]

    func perform() async throws -> some IntentResult {
        ADPendingSystemActionStore.enqueue(.photo)
        return .result()
    }
}

struct ToggleADGlassesVideoIntent: AppIntent {
    static let title: LocalizedStringResource = "Toggle AD Glasses Video"
    static let description = IntentDescription("Starts or stops video recording on AD Glasses.")
    static let supportedModes: IntentModes = [.foreground(.immediate)]

    func perform() async throws -> some IntentResult {
        ADPendingSystemActionStore.enqueue(.video)
        return .result()
    }
}

struct ToggleADGlassesAudioIntent: AppIntent {
    static let title: LocalizedStringResource = "Toggle AD Glasses Audio"
    static let description = IntentDescription("Starts or stops audio recording on AD Glasses.")
    static let supportedModes: IntentModes = [.foreground(.immediate)]

    func perform() async throws -> some IntentResult {
        ADPendingSystemActionStore.enqueue(.audio)
        return .result()
    }
}

struct ADGlassesAppShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: ConnectADGlassesIntent(),
            phrases: ["Connect \(.applicationName)", "Reconnect \(.applicationName)"],
            shortTitle: "Connect Glasses",
            systemImageName: "eyeglasses"
        )
        AppShortcut(
            intent: AskADGlassesIntent(),
            phrases: ["Ask \(.applicationName)", "Ask with \(.applicationName)"],
            shortTitle: "Ask AD",
            systemImageName: "mic.fill"
        )
        AppShortcut(
            intent: TakeADGlassesPhotoIntent(),
            phrases: ["Take a photo with \(.applicationName)", "Click with \(.applicationName)"],
            shortTitle: "Take Photo",
            systemImageName: "camera.fill"
        )
        AppShortcut(
            intent: ToggleADGlassesVideoIntent(),
            phrases: ["Toggle video on \(.applicationName)"],
            shortTitle: "Toggle Video",
            systemImageName: "video.fill"
        )
        AppShortcut(
            intent: ToggleADGlassesAudioIntent(),
            phrases: ["Toggle recording on \(.applicationName)"],
            shortTitle: "Toggle Audio",
            systemImageName: "waveform"
        )
    }
}

/// Cloud remains the primary conversational backend. On Apple-Intelligence-capable hardware,
/// Apple Foundation Models provide a privacy-preserving resilience path for offline-safe text
/// requests when the configured cloud service fails before it streams any response. An iPhone 13
/// reports both Apple Intelligence model routes as unavailable and continues using the existing
/// cloud providers exactly as before.
struct AdaptiveAIClient: AIResponding {
    private let cloud = CloudAIClient()

    func response(
        to messages: [ConversationMessage],
        profile: AIProfile,
        credential: String
    ) async throws -> String {
        do {
            return try await cloud.response(to: messages, profile: profile, credential: credential)
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            if Self.canUseLocalFallback(after: error),
               let answer = try? await AppleFoundationModelResponder.response(to: messages) {
                return answer
            }
            throw error
        }
    }

    func streamingResponse(
        to messages: [ConversationMessage],
        profile: AIProfile,
        credential: String,
        onDelta: @escaping @MainActor @Sendable (String) -> Void
    ) async throws -> String {
        let streamState = await MainActor.run { ADStreamingFallbackState() }
        do {
            return try await cloud.streamingResponse(
                to: messages,
                profile: profile,
                credential: credential
            ) { delta in
                streamState.didStream = true
                onDelta(delta)
            }
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            let didStream = await MainActor.run { streamState.didStream }
            if !didStream,
               Self.canUseLocalFallback(after: error),
               let answer = try? await AppleFoundationModelResponder.response(to: messages) {
                await onDelta(answer)
                return answer
            }
            throw error
        }
    }

    private static func canUseLocalFallback(after error: Error) -> Bool {
        guard let configurationError = error as? AIConfigurationError else { return false }
        switch configurationError {
        case .invalidResponse, .requestFailed:
            return true
        default:
            return false
        }
    }
}

@MainActor
private final class ADStreamingFallbackState {
    var didStream = false
}

private enum AppleFoundationModelResponder {
    private static let instructions = """
    You are AD, the concise companion for AD Glasses. Answer only from general knowledge
    and the conversation supplied in the prompt. Never claim current weather, news, prices,
    scores, nearby places, live location, or other changing facts. Do not pretend to control
    the glasses. Keep the answer brief and useful.
    """

    static func response(to messages: [ConversationMessage]) async throws -> String? {
        guard messages.allSatisfy({ $0.imageAttachment == nil }),
              let latest = messages.last(where: { $0.role == .user })?.text,
              isOfflineSafe(latest) else {
            return nil
        }

        let recentConversation = messages.suffix(8).map { message in
            let speaker = message.role == .user ? "User" : "AD"
            return "\(speaker): \(message.text)"
        }
        .joined(separator: "\n")

        let onDeviceModel = SystemLanguageModel.default
        if case .available = onDeviceModel.availability {
            let session = LanguageModelSession(instructions: instructions)
            let generated = try await session.respond(to: recentConversation)
            return cleaned(generated.content)
        }

        if let cloudAnswer = try await privateCloudResponseIfAvailable(to: recentConversation) {
            return cloudAnswer
        }
        return nil
    }

    /// iOS 27 can use Private Cloud Compute as an additional Apple model route when the device,
    /// account, region, and app capability make it available. Runtime availability remains the
    /// source of truth so an iPhone 13 cleanly skips this route without model-name checks.
    private static func privateCloudResponseIfAvailable(to prompt: String) async throws -> String? {
        let model = PrivateCloudComputeLanguageModel()
        guard model.isAvailable else { return nil }
        let session = LanguageModelSession(model: model, instructions: instructions)
        let generated = try await session.respond(to: prompt)
        return cleaned(generated.content)
    }

    private static func cleaned(_ value: String) -> String? {
        let clean = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return clean.isEmpty ? nil : clean
    }

    private static func isOfflineSafe(_ text: String) -> Bool {
        let value = text.lowercased()
        let liveSignals = [
            "today", "tonight", "tomorrow", "yesterday", "latest", "current", "right now",
            "weather", "news", "score", "stock", "price", "exchange rate", "traffic",
            "near me", "nearby", "directions", "where am i", "open now", "flight"
        ]
        return !liveSignals.contains(where: value.contains)
    }
}

@main
struct ADGlassesApp: App {
    @UIApplicationDelegateAdaptor(ADGlassesAppDelegate.self) private var appDelegate
    @Environment(\.scenePhase) private var scenePhase

    @StateObject private var appModel: AppModel
    @StateObject private var libraryModel: LibraryModel
    @StateObject private var glassesManager: GlassesManager

    init() {
        let appModel = AppModel(aiClient: AdaptiveAIClient())
        let glassesManager = GlassesManager(providers: [
            HeyCyanGlassesProvider(),
            MetaGlassesProvider()
        ])
        appModel.attach(to: glassesManager)

        _appModel = StateObject(wrappedValue: appModel)
        _libraryModel = StateObject(wrappedValue: LibraryModel())
        _glassesManager = StateObject(wrappedValue: glassesManager)
    }

    var body: some Scene {
        WindowGroup {
            NativeTranslationHost {
                appRoot
            }
        }
    }

    private var appRoot: some View {
        AppRootView()
            .environmentObject(appModel)
            .environmentObject(glassesManager)
            .environmentObject(libraryModel)
            .task {
                appModel.setApplicationActive(scenePhase == .active)
                if scenePhase == .active {
                    performPendingSystemActionIfNeeded()
                }
            }
            .onChange(of: scenePhase) { _, phase in
                appModel.setApplicationActive(phase == .active)
                if phase == .active {
                    performPendingSystemActionIfNeeded()
                }
            }
            .onReceive(NotificationCenter.default.publisher(for: .adPendingSystemAction)) { _ in
                guard scenePhase == .active else { return }
                performPendingSystemActionIfNeeded()
            }
    }

    private func performPendingSystemActionIfNeeded() {
        guard let action = ADPendingSystemActionStore.consume() else { return }
        Task { @MainActor in
            switch action {
            case .connect:
                _ = await glassesManager.reconnectLastDevice()

            case .ask:
                appModel.clearTranscript()
                await appModel.startVoiceQuestion()

            case .photo:
                if !glassesManager.connectionState.isConnected {
                    _ = await glassesManager.reconnectLastDevice()
                }
                guard glassesManager.connectionState.isConnected else { return }
                _ = await glassesManager.requestPhotoCapture()

            case .video:
                if !glassesManager.connectionState.isConnected {
                    _ = await glassesManager.reconnectLastDevice()
                }
                guard glassesManager.connectionState.isConnected else { return }
                _ = await glassesManager.toggleVideoRecording()

            case .audio:
                if !glassesManager.connectionState.isConnected {
                    _ = await glassesManager.reconnectLastDevice()
                }
                guard glassesManager.connectionState.isConnected else { return }
                _ = await glassesManager.toggleAudioRecording()
            }
        }
    }
}
