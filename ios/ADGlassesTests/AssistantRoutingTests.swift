import XCTest
import AVFoundation
@testable import ADGlasses

final class AssistantRoutingTests: XCTestCase {
    private let router = AssistantRequestRouter()

    func testRoutingUsesActualInputShapeInsteadOfKeywordGuessing() {
        XCTAssertEqual(
            router.route(AssistantRequest(text: "Find a cafe", source: .chat, hasImage: false)),
            .conversation
        )
        XCTAssertEqual(
            router.route(AssistantRequest(text: "What is this?", source: .lensImage, hasImage: true)),
            .visualQuestion
        )
        XCTAssertEqual(
            router.route(AssistantRequest(text: "   ", source: .phoneVoice, hasImage: false)),
            .clarify
        )
        XCTAssertEqual(
            router.route(AssistantRequest(text: "Click a photo", source: .glassesVoice, hasImage: false)),
            .capturePhoto
        )
        XCTAssertEqual(
            router.route(AssistantRequest(text: "How do I take a photo?", source: .chat, hasImage: false)),
            .conversation
        )
        XCTAssertEqual(
            router.route(AssistantRequest(text: "Don't take a picture", source: .glassesVoice, hasImage: false)),
            .conversation
        )
    }

    func testConversationRequestBudgetKeepsNewestMessages() {
        let messages = (0..<55).map { index in
            ConversationMessage(
                role: index.isMultiple(of: 2) ? .user : .assistant,
                text: "message-\(index)"
            )
        }

        let result = ConversationContextPolicy.requestMessages(from: messages)

        XCTAssertEqual(result.count, ConversationContextPolicy.maximumMessages)
        XCTAssertEqual(result.first?.text, "message-15")
        XCTAssertEqual(result.last?.text, "message-54")
    }

    func testConversationRequestBudgetAlwaysKeepsNewestMessage() {
        let messages = [
            ConversationMessage(role: .user, text: "old"),
            ConversationMessage(
                role: .user,
                text: String(repeating: "x", count: ConversationContextPolicy.maximumCharacters + 10)
            )
        ]

        let result = ConversationContextPolicy.requestMessages(from: messages)

        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result.first?.id, messages.last?.id)
    }
}

@MainActor
final class GlassesAssistantPipelineTests: XCTestCase {
    func testSpokenPhotoCommandExecutesLocalGlassesActionWithoutCloudProfile() async throws {
        let defaults = try XCTUnwrap(UserDefaults(suiteName: UUID().uuidString))
        let provider = FakeAssistantAudioProvider()
        let manager = GlassesManager(providers: [provider])
        let storeURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
            .appendingPathComponent("conversations.json")
        let app = AppModel(
            transcriber: FakeExternalAudioTranscriber(finalTranscript: ""),
            aiProfiles: AIProfileStore(defaults: defaults),
            speechOutput: SpeechOutputController(defaults: defaults),
            conversationStore: ConversationStore(fileURL: storeURL)
        )
        app.attach(to: manager)
        app.chatDraft = "Click a photo"

        app.sendChatMessage(source: .glassesVoice, speakResponse: false)
        for _ in 0 ..< 200 where app.isGenerating {
            await Task.yield()
        }

        XCTAssertEqual(provider.photoRequestCount, 1)
        XCTAssertEqual(app.conversation.map(\.role), [.user, .assistant])
        XCTAssertEqual(app.conversation.last?.text, "Photo taken. It is saved on AD Glasses and will appear after your next Library sync.")
    }

    func testDecodedProviderAudioBecomesOneGlassesVoiceConversationTurn() async throws {
        let defaults = try XCTUnwrap(UserDefaults(suiteName: UUID().uuidString))
        let transcriber = FakeExternalAudioTranscriber(finalTranscript: "What can I see?")
        let provider = FakeAssistantAudioProvider()
        let manager = GlassesManager(providers: [provider])
        let storeURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
            .appendingPathComponent("conversations.json")
        let app = AppModel(
            transcriber: transcriber,
            aiProfiles: AIProfileStore(defaults: defaults),
            speechOutput: SpeechOutputController(defaults: defaults),
            conversationStore: ConversationStore(fileURL: storeURL)
        )
        app.attach(to: manager)

        let format = try XCTUnwrap(AVAudioFormat(
            commonFormat: .pcmFormatFloat32,
            sampleRate: 16_000,
            channels: 1,
            interleaved: false
        ))
        let buffer = try XCTUnwrap(AVAudioPCMBuffer(pcmFormat: format, frameCapacity: 320))
        buffer.frameLength = 320

        provider.emit(.started(format: format))
        provider.emit(.pcmBuffer(buffer))
        provider.emit(.ended)

        for _ in 0 ..< 100 where app.conversation.isEmpty {
            await Task.yield()
        }

        XCTAssertEqual(transcriber.externalStartCount, 1)
        XCTAssertEqual(transcriber.appendCount, 1)
        XCTAssertEqual(transcriber.externalFinishCount, 1)
        XCTAssertEqual(app.conversation.last?.role, .user)
        XCTAssertEqual(app.conversation.last?.text, "What can I see?")
    }

    func testPhoneWakeListeningContinuesWhenAppMovesToBackground() throws {
        let defaults = try XCTUnwrap(UserDefaults(suiteName: UUID().uuidString))
        defaults.set(true, forKey: "phoneVoiceActivation.enabled.v1")
        let service = FakePhoneWakeWordService()
        let provider = FakeAssistantAudioProvider()
        let manager = GlassesManager(providers: [provider])
        let app = AppModel(
            transcriber: FakeExternalAudioTranscriber(finalTranscript: ""),
            aiProfiles: AIProfileStore(defaults: defaults),
            speechOutput: SpeechOutputController(defaults: defaults)
        )
        let controller = PhoneVoiceActivationController(
            service: service,
            glasses: manager,
            app: app,
            defaults: defaults
        )

        controller.setApplicationActive(true)
        XCTAssertTrue(controller.isListening)
        XCTAssertEqual(service.startCount, 1)

        controller.setApplicationActive(false)
        XCTAssertTrue(controller.isListening)
        XCTAssertEqual(service.stopCount, 0)

        controller.isEnabled = false
        XCTAssertFalse(controller.isListening)
        XCTAssertEqual(service.stopCount, 1)
    }
}

@MainActor
private final class FakePhoneWakeWordService: PhoneWakeWordDetecting {
    let phrase = "AD"
    let configurationState = PhoneWakeWordConfigurationState.ready
    private(set) var startCount = 0
    private(set) var stopCount = 0

    func start(onDetection: @escaping @MainActor () -> Void) throws {
        startCount += 1
    }

    func stop() {
        stopCount += 1
    }

    func saveAccessKey(_ value: String) throws {}
    func importModel(from sourceURL: URL, phrase: String) throws {}
    func trainModel(phrase: String, language: String) async throws {}
}

@MainActor
private final class FakeExternalAudioTranscriber: ExternalAudioSpeechTranscribing {
    let engineName = "Fake external speech"
    var onUpdate: ((SpeechTranscriptionSnapshot) -> Void)?
    var onError: ((Error) -> Void)?
    private(set) var snapshot: SpeechTranscriptionSnapshot
    private(set) var externalStartCount = 0
    private(set) var appendCount = 0
    private(set) var externalFinishCount = 0
    private let finalTranscript: String

    init(finalTranscript: String) {
        self.finalTranscript = finalTranscript
        snapshot = SpeechTranscriptionSnapshot(
            transcript: "",
            isRunning: false,
            engineName: engineName
        )
    }

    func start() async throws {}
    func stop() async { snapshot.isRunning = false }
    func resetTranscript() { snapshot.transcript = "" }

    func startExternalAudio() async throws {
        externalStartCount += 1
        snapshot.isRunning = true
        onUpdate?(snapshot)
    }

    func appendExternalAudio(_ buffer: AVAudioPCMBuffer) {
        appendCount += 1
    }

    func finishExternalAudio() async {
        externalFinishCount += 1
        snapshot.transcript = finalTranscript
        snapshot.isRunning = false
        onUpdate?(snapshot)
    }
}

@MainActor
private final class FakeAssistantAudioProvider:
    GlassesProvider,
    GlassesAssistantAudioProviding,
    GlassesPhotoCapturing
{
    let id = "assistant-audio"
    let displayName = "Assistant Audio"
    let capabilities: Set<GlassesCapability> = [.bluetoothConnection, .photoCapture]
    var connectionState: GlassesConnectionState = .connected("Test glasses")
    var onConnectionStateChange: ((GlassesConnectionState) -> Void)?
    var onAssistantAudioEvent: ((GlassesAssistantAudioEvent) -> Void)?
    private(set) var photoRequestCount = 0

    func scan() async throws -> [GlassesDevice] { [] }
    func connect(to device: GlassesDevice) async throws {}
    func disconnect() async { connectionState = .disconnected }
    func requestPhotoCapture() async throws { photoRequestCount += 1 }

    func emit(_ event: GlassesAssistantAudioEvent) {
        onAssistantAudioEvent?(event)
    }
}
