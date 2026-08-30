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
private final class FakeAssistantAudioProvider: GlassesProvider, GlassesAssistantAudioProviding {
    let id = "assistant-audio"
    let displayName = "Assistant Audio"
    let capabilities: Set<GlassesCapability> = [.bluetoothConnection]
    var connectionState: GlassesConnectionState = .connected("Test glasses")
    var onConnectionStateChange: ((GlassesConnectionState) -> Void)?
    var onAssistantAudioEvent: ((GlassesAssistantAudioEvent) -> Void)?

    func scan() async throws -> [GlassesDevice] { [] }
    func connect(to device: GlassesDevice) async throws {}
    func disconnect() async { connectionState = .disconnected }

    func emit(_ event: GlassesAssistantAudioEvent) {
        onAssistantAudioEvent?(event)
    }
}
