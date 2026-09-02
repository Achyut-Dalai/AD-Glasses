import XCTest
import AVFoundation
@testable import ADGlasses

final class AssistantRoutingTests: XCTestCase {
    private let router = AssistantRequestRouter()

    func testDeterministicAssistantRouterSeparatesCommandsFromConversation() {
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
            router.route(AssistantRequest(text: "Record a video", source: .glassesVoice, hasImage: false)),
            .startVideo
        )
        XCTAssertEqual(
            router.route(AssistantRequest(text: "Stop video", source: .phoneVoice, hasImage: false)),
            .stopVideo
        )
        XCTAssertEqual(
            router.route(AssistantRequest(text: "Record audio", source: .glassesVoice, hasImage: false)),
            .startAudio
        )
        XCTAssertEqual(
            router.route(AssistantRequest(text: "Stop audio", source: .phoneVoice, hasImage: false)),
            .stopAudio
        )
        XCTAssertEqual(
            router.route(AssistantRequest(text: "Stop recording", source: .glassesVoice, hasImage: false)),
            .stopRecording
        )
        XCTAssertEqual(
            router.route(AssistantRequest(text: "Read this sign", source: .glassesVoice, hasImage: false)),
            .readVisibleText
        )
        XCTAssertEqual(
            router.route(AssistantRequest(text: "What am I looking at?", source: .phoneVoice, hasImage: false)),
            .visualQuestion
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

final class GroundingPolicyTests: XCTestCase {
    private let router = GroundingIntentRouter()

    func testGeneralWebRouterOnlyOwnsExplicitOrUnstructuredFreshness() {
        XCTAssertEqual(router.route("Search the web for the latest OpenAI update").intent, .search)
        XCTAssertEqual(router.route("Who is the current president of France?").intent, .search)
        // Score and weather prompts are owned by StructuredGroundingService. Keeping them direct
        // here prevents Tavily from becoming a hidden fallback for a deterministic capability.
        XCTAssertEqual(router.route("What's the NBA score?").intent, .direct)
        XCTAssertEqual(router.route("What's the weather today?").intent, .direct)
    }

    func testSpatialRequestsRequireHighConfidenceLanguage() {
        let nearby = router.route("Find a pharmacy near me")
        XCTAssertEqual(nearby.intent, .spatial)
        XCTAssertEqual(nearby.spatialAction, .nearby)
        XCTAssertEqual(nearby.poiCategory, "pharmacy")
        XCTAssertTrue(nearby.useCurrentLocation)

        let route = router.route("Directions to Connaught Place")
        XCTAssertEqual(route.intent, .spatial)
        XCTAssertEqual(route.spatialAction, .route)
        XCTAssertEqual(route.routeDestination, "connaught place")
        XCTAssertTrue(route.useCurrentLocation)
    }

    func testTechnicalLanguageDoesNotAccidentallyOpenGroundingServices() {
        XCTAssertEqual(router.route("How does an HTTP route work in Swift?").intent, .direct)
        XCTAssertEqual(router.route("What is the current variable value in this code?").intent, .direct)
        XCTAssertEqual(router.route("Find the nearest node in this graph algorithm").intent, .direct)
    }

    func testOrdinaryConversationStaysDirect() {
        XCTAssertEqual(router.route("Explain photosynthesis simply").intent, .direct)
        XCTAssertEqual(router.route("Help me rewrite this sentence").intent, .direct)
    }

    func testGeminiModelIdentifiersAreNormalized() {
        XCTAssertEqual(
            AIProfileStore.normalizedModel("models/gemini-3.7-flash:generateContent", provider: .google),
            "gemini-3.7-flash"
        )
        XCTAssertEqual(
            AIProfileStore.normalizedModel(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.7-pro",
                provider: .google
            ),
            "gemini-3.7-pro"
        )
    }

    func testCloudTokenPolicyIsBoundedAndRequiresExplicitDeepIntent() {
        XCTAssertEqual(
            CloudModelPolicy.outputTokenLimit(CloudModelPolicy.mode(for: "What's ahead of me?")),
            CloudModelPolicy.conciseOutputTokens
        )
        XCTAssertEqual(
            CloudModelPolicy.outputTokenLimit(
                CloudModelPolicy.mode(for: "Research thoroughly and compare the evidence")
            ),
            CloudModelPolicy.reasonedOutputTokens
        )
        XCTAssertLessThan(CloudModelPolicy.conciseOutputTokens, CloudModelPolicy.reasonedOutputTokens)
    }
}

final class CloudStreamingTests: XCTestCase {
    func testGroqCRLFEventSeparatorsNormalizeToBlankLines() {
        XCTAssertEqual(ServerSentEventFraming.normalize("\r"), "")
        XCTAssertEqual(
            ServerSentEventFraming.normalize("data: {\"choices\":[]}\r"),
            "data: {\"choices\":[]}"
        )
        XCTAssertEqual(ServerSentEventFraming.normalize("data: [DONE]"), "data: [DONE]")
        XCTAssertTrue(ServerSentEventFraming.isCompleteDataLine("{\"choices\":[]}"))
        XCTAssertTrue(ServerSentEventFraming.isCompleteDataLine("[DONE]"))
        XCTAssertFalse(ServerSentEventFraming.isCompleteDataLine("{\"choices\":"))
    }

    @MainActor
    func testGroqStreamsOnlyFinalContentAndRequestsHiddenReasoning() async throws {
        GroqStreamingStub.shared.reset()
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [GroqStreamingStubURLProtocol.self]
        let client = CloudAIClient(session: URLSession(configuration: configuration))
        let profile = AIProfile(
            id: UUID(),
            name: "Groq",
            provider: .groq,
            baseURL: AIProviderKind.groq.defaultBaseURL,
            model: AIProviderKind.groq.defaultModel
        )
        var deltas = [String]()

        let answer = try await client.streamingResponse(
            to: [ConversationMessage(role: .user, text: "Explain photosynthesis simply")],
            profile: profile,
            credential: "test-key"
        ) { deltas.append($0) }

        XCTAssertEqual(answer, "Visible answer")
        XCTAssertEqual(deltas, ["Visible answer"])
        XCTAssertEqual(GroqStreamingStub.shared.requestCount, 1)

        var payload: [String: Any] = ["stream": true]
        CloudModelPolicy.applyOpenAICompatibleTuning(
            to: &payload,
            profile: profile,
            mode: .conciseConversation
        )
        XCTAssertEqual(payload["include_reasoning"] as? Bool, false)
        XCTAssertEqual(payload["reasoning_effort"] as? String, "low")
        XCTAssertEqual(payload["stream"] as? Bool, true)
    }
}

final class AssistantCompletionSanitizerTests: XCTestCase {
    func testCompletedThinkingBlockNeverReachesVisibleAnswer() {
        let raw = "<think>Private chain of thought.</think>Paris is the capital of France."
        XCTAssertEqual(AssistantCompletionSanitizer.clean(raw), "Paris is the capital of France.")
    }

    func testUnfinishedThinkingIsHeldDuringStreaming() {
        XCTAssertEqual(AssistantCompletionSanitizer.cleanForStreaming("<thi"), "")
        XCTAssertEqual(AssistantCompletionSanitizer.cleanForStreaming("<think>Private reasoning"), "")
    }

    func testReasoningLabelRequiresASeparateFinalAnswer() {
        XCTAssertEqual(AssistantCompletionSanitizer.cleanForStreaming("Reasoning: private notes"), "")
        XCTAssertEqual(
            AssistantCompletionSanitizer.clean("Reasoning: private notes\nFinal answer: Safe answer."),
            "Safe answer."
        )
    }

    func testReasoningOnlyAssistantHistoryIsNotSentBackToProvider() {
        let messages = [
            ConversationMessage(role: .user, text: "Question"),
            ConversationMessage(role: .assistant, text: "<think>Private notes only</think>"),
            ConversationMessage(role: .user, text: "Follow-up")
        ]
        XCTAssertEqual(ConversationContextPolicy.requestMessages(from: messages).map(\.text), ["Question", "Follow-up"])
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

    func testGlassesVoiceTurnPreservesExistingTypedDraft() async throws {
        let defaults = try XCTUnwrap(UserDefaults(suiteName: UUID().uuidString))
        let transcriber = FakeExternalAudioTranscriber(finalTranscript: "What is ahead?")
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
        app.chatDraft = "Keep this unsent draft"
        let format = try XCTUnwrap(AVAudioFormat(
            commonFormat: .pcmFormatFloat32,
            sampleRate: 16_000,
            channels: 1,
            interleaved: false
        ))

        provider.emit(.started(format: format))
        for _ in 0 ..< 100 where transcriber.externalStartCount == 0 {
            await Task.yield()
        }
        provider.emit(.ended)

        for _ in 0 ..< 200 where app.conversation.isEmpty || app.isGenerating {
            await Task.yield()
        }

        XCTAssertEqual(app.conversation.last?.role, .user)
        XCTAssertEqual(app.conversation.last?.text, "What is ahead?")
        XCTAssertEqual(app.chatDraft, "Keep this unsent draft")
    }

    func testGlassesTurnFinalizesManualDictationBeforeStartingExternalAudio() async throws {
        let defaults = try XCTUnwrap(UserDefaults(suiteName: UUID().uuidString))
        let transcriber = FakeExternalAudioTranscriber(
            finalTranscript: "Question from glasses",
            phoneFinalTranscript: "Manual AD draft"
        )
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

        app.clearTranscript()
        await app.startTranscription()
        XCTAssertTrue(app.isManualTranscription)

        provider.emit(.started(format: format))
        for _ in 0 ..< 100 where transcriber.externalStartCount == 0 {
            await Task.yield()
        }
        provider.emit(.ended)
        for _ in 0 ..< 200 where app.conversation.isEmpty || app.isGenerating {
            await Task.yield()
        }

        XCTAssertEqual(transcriber.phoneStopCount, 1)
        XCTAssertEqual(app.chatDraft, "Manual AD draft")
        XCTAssertEqual(app.conversation.last?.text, "Question from glasses")
    }

    func testPhoneVoiceQuestionSendsImmediatelyAndPreservesTypedDraft() async throws {
        let defaults = try XCTUnwrap(UserDefaults(suiteName: UUID().uuidString))
        let transcriber = FakeExternalAudioTranscriber(
            finalTranscript: "",
            phoneFinalTranscript: "What is in front of me?"
        )
        let storeURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
            .appendingPathComponent("conversations.json")
        let app = AppModel(
            transcriber: transcriber,
            aiProfiles: AIProfileStore(defaults: defaults),
            speechOutput: SpeechOutputController(defaults: defaults),
            conversationStore: ConversationStore(fileURL: storeURL)
        )
        app.chatDraft = "Keep this typed draft"

        await app.startVoiceQuestion()
        XCTAssertTrue(app.isPhoneVoiceQuestionActive)

        await app.finishVoiceQuestion()
        for _ in 0 ..< 200 where app.conversation.isEmpty || app.isGenerating {
            await Task.yield()
        }

        XCTAssertFalse(app.isPhoneVoiceQuestionActive)
        XCTAssertEqual(transcriber.phoneStopCount, 1)
        XCTAssertEqual(app.conversation.first?.role, .user)
        XCTAssertEqual(app.conversation.first?.text, "What is in front of me?")
        XCTAssertEqual(app.chatDraft, "Keep this typed draft")
    }

    func testGlassesVoiceSessionResetsWhenConnectionDropsWithoutEndedEvent() async throws {
        let defaults = try XCTUnwrap(UserDefaults(suiteName: UUID().uuidString))
        let transcriber = FakeExternalAudioTranscriber(finalTranscript: "Partial glasses speech")
        let provider = FakeAssistantAudioProvider()
        let manager = GlassesManager(providers: [provider])
        let app = AppModel(
            transcriber: transcriber,
            aiProfiles: AIProfileStore(defaults: defaults),
            speechOutput: SpeechOutputController(defaults: defaults)
        )
        app.attach(to: manager)
        let format = try XCTUnwrap(AVAudioFormat(
            commonFormat: .pcmFormatFloat32,
            sampleRate: 16_000,
            channels: 1,
            interleaved: false
        ))

        provider.emit(.started(format: format))
        for _ in 0 ..< 100 where transcriber.externalStartCount == 0 {
            await Task.yield()
        }
        XCTAssertTrue(app.isGlassesAssistantAudioActive)

        await manager.disconnect()
        for _ in 0 ..< 200 where app.isGlassesAssistantAudioActive {
            await Task.yield()
        }

        XCTAssertFalse(app.isGlassesAssistantAudioActive)
        XCTAssertFalse(app.isTranscribing)
        XCTAssertEqual(transcriber.externalFinishCount, 1)
        XCTAssertTrue(app.conversation.isEmpty)
    }

}

@MainActor
private final class FakeExternalAudioTranscriber: ExternalAudioSpeechTranscribing {
    let engineName = "Fake external speech"
    var onUpdate: ((SpeechTranscriptionSnapshot) -> Void)?
    var onError: ((Error) -> Void)?
    private(set) var snapshot: SpeechTranscriptionSnapshot
    private(set) var phoneStartCount = 0
    private(set) var phoneStopCount = 0
    private(set) var externalStartCount = 0
    private(set) var appendCount = 0
    private(set) var externalFinishCount = 0
    private let finalTranscript: String
    private let phoneFinalTranscript: String

    init(finalTranscript: String, phoneFinalTranscript: String = "") {
        self.finalTranscript = finalTranscript
        self.phoneFinalTranscript = phoneFinalTranscript
        snapshot = SpeechTranscriptionSnapshot(
            transcript: "",
            isRunning: false,
            engineName: engineName
        )
    }

    func start() async throws {
        phoneStartCount += 1
        snapshot.isRunning = true
        onUpdate?(snapshot)
    }

    func stop() async {
        guard snapshot.isRunning else { return }
        phoneStopCount += 1
        if !phoneFinalTranscript.isEmpty {
            snapshot.transcript = phoneFinalTranscript
        }
        snapshot.isRunning = false
        onUpdate?(snapshot)
    }

    func resetTranscript() {
        snapshot.transcript = ""
        onUpdate?(snapshot)
    }

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
    func disconnect() async {
        connectionState = .disconnected
        onConnectionStateChange?(.disconnected)
    }
    func requestPhotoCapture() async throws { photoRequestCount += 1 }

    func emit(_ event: GlassesAssistantAudioEvent) {
        onAssistantAudioEvent?(event)
    }
}

private final class GroqStreamingStub: @unchecked Sendable {
    static let shared = GroqStreamingStub()
    private let lock = NSLock()
    private var count = 0
    private var payload: [String: Any]?

    var requestCount: Int {
        lock.lock()
        defer { lock.unlock() }
        return count
    }

    var latestPayload: [String: Any]? {
        lock.lock()
        defer { lock.unlock() }
        return payload
    }

    func reset() {
        lock.lock()
        count = 0
        payload = nil
        lock.unlock()
    }

    func response(for request: URLRequest) -> (HTTPURLResponse, Data) {
        let body = request.httpBody.flatMap { try? JSONSerialization.jsonObject(with: $0) } as? [String: Any]
        lock.lock()
        count += 1
        payload = body
        lock.unlock()

        let eventStream =
            "data: {\"choices\":[{\"delta\":{\"reasoning\":\"Private thought\"}}]}\r\n\r\n" +
            "data: {\"choices\":[{\"delta\":{\"content\":\"Visible answer\"}}]}\r\n\r\n" +
            "data: [DONE]\r\n\r\n"
        let data = Data(eventStream.utf8)
        let response = HTTPURLResponse(
            url: request.url!,
            statusCode: 200,
            httpVersion: "HTTP/1.1",
            headerFields: ["Content-Type": "text/event-stream"]
        )!
        return (response, data)
    }
}

private final class GroqStreamingStubURLProtocol: URLProtocol, @unchecked Sendable {
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        let (response, data) = GroqStreamingStub.shared.response(for: request)
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: data)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}
