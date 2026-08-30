@preconcurrency import AVFoundation
import Combine
import Foundation

@MainActor
final class AppModel: ObservableObject {
    @Published private(set) var transcript = ""
    @Published private(set) var isTranscribing = false
    @Published private(set) var speechEngineName = "Apple Speech"
    @Published var speechError: String?
    @Published var chatDraft = ""
    @Published private(set) var conversation: [ConversationMessage] = []
    @Published private(set) var conversations: [ConversationThread] = []
    @Published private(set) var currentConversationID = UUID()
    @Published private(set) var isGenerating = false
    @Published private(set) var isConversationStoreReady = false
    @Published var conversationNotice: String?

    let aiProfiles: AIProfileStore
    let speechOutput: SpeechOutputController

    private let transcriber: any SpeechTranscribing
    private let conversationStore: ConversationStore
    private let aiClient: any AIResponding
    private let requestRouter: AssistantRequestRouter
    private var generationTask: Task<Void, Never>?
    private var generationID: UUID?
    private var conversationLoadTask: Task<[ConversationThread], Error>?
    private var didLoadConversations = false
    private var userChangedConversationBeforeLoad = false
    private var glassesSpeechStartTask: Task<Void, Never>?
    private var glassesAssistantSessionID: UUID?
    private var pendingGlassesAudio = [AVAudioPCMBuffer]()
    private var isGlassesSpeechReady = false
    private var glassesStreamDidEnd = false
    private var applicationIsActive = true
    private let maximumPendingGlassesPackets = 100

    init(
        transcriber: (any SpeechTranscribing)? = nil,
        aiProfiles: AIProfileStore? = nil,
        speechOutput: SpeechOutputController? = nil,
        conversationStore: ConversationStore = ConversationStore(),
        aiClient: (any AIResponding)? = nil,
        requestRouter: AssistantRequestRouter = AssistantRequestRouter()
    ) {
        let selectedTranscriber = transcriber ?? AppleSpeechTranscriber.make()
        self.transcriber = selectedTranscriber
        self.aiProfiles = aiProfiles ?? AIProfileStore()
        self.speechOutput = speechOutput ?? SpeechOutputController()
        self.conversationStore = conversationStore
        self.aiClient = aiClient ?? CloudAIClient()
        self.requestRouter = requestRouter
        speechEngineName = selectedTranscriber.engineName

        selectedTranscriber.onUpdate = { [weak self] snapshot in
            guard let self else { return }
            transcript = snapshot.transcript
            isTranscribing = snapshot.isRunning
            speechEngineName = snapshot.engineName
        }

        selectedTranscriber.onError = { [weak self] error in
            self?.speechError = error.localizedDescription
        }

        conversationLoadTask = Task { try await conversationStore.load() }
        Task { [weak self] in await self?.loadConversationsIfNeeded() }
    }

    deinit {
        generationTask?.cancel()
        conversationLoadTask?.cancel()
        glassesSpeechStartTask?.cancel()
    }

    func toggleTranscription() async {
        speechError = nil
        if transcriber.snapshot.isRunning {
            await transcriber.stop()
            return
        }

        do {
            try await transcriber.start()
        } catch {
            speechError = error.localizedDescription
        }
    }

    func stopTranscription() async {
        guard transcriber.snapshot.isRunning else { return }
        await transcriber.stop()
    }

    func clearTranscript() {
        transcript = ""
        transcriber.resetTranscript()
    }

    func useTranscriptAsDraft() {
        guard !transcript.isEmpty else { return }
        chatDraft = transcript
    }

    func sendChatMessage(
        source: AssistantRequestSource = .chat,
        speakResponse: Bool = false
    ) {
        let text = chatDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        let route = requestRouter.route(
            AssistantRequest(text: text, source: source, hasImage: false)
        )
        guard route == .conversation, generationTask == nil else { return }
        chatDraft = ""

        let id = UUID()
        generationID = id
        isGenerating = true
        generationTask = Task { [weak self] in
            await self?.send(text, generationID: id, speakResponse: speakResponse)
        }
    }

    func attach(to glassesManager: GlassesManager) {
        glassesManager.onAssistantAudioEvent = { [weak self] event in
            self?.consume(event)
        }
    }

    func setApplicationActive(_ active: Bool) {
        applicationIsActive = active
        if !active, glassesAssistantSessionID != nil {
            Task { [weak self] in await self?.cancelGlassesAssistantSession() }
        }
    }

    func cancelResponse() {
        generationTask?.cancel()
        generationTask = nil
        generationID = nil
        isGenerating = false
    }

    func startNewConversation() {
        cancelResponse()
        userChangedConversationBeforeLoad = true
        currentConversationID = UUID()
        conversation.removeAll()
        chatDraft = ""
        conversationNotice = nil
        clearTranscript()
    }

    func openConversation(_ id: UUID) {
        guard let thread = conversations.first(where: { $0.id == id }) else { return }
        cancelResponse()
        userChangedConversationBeforeLoad = true
        currentConversationID = thread.id
        conversation = thread.messages
        conversationNotice = nil
    }

    func deleteConversation(_ id: UUID) async {
        await loadConversationsIfNeeded()
        if currentConversationID == id {
            cancelResponse()
        }
        conversations.removeAll { $0.id == id }
        if currentConversationID == id {
            currentConversationID = UUID()
            conversation.removeAll()
            conversationNotice = nil
        }
        await saveConversations()
    }

    func deleteAllConversations() async {
        cancelResponse()
        conversationLoadTask?.cancel()
        conversationLoadTask = nil
        didLoadConversations = true
        isConversationStoreReady = true
        userChangedConversationBeforeLoad = true
        conversations.removeAll()
        currentConversationID = UUID()
        conversation.removeAll()
        conversationNotice = nil
        do {
            try await conversationStore.deleteAll()
        } catch {
            conversationNotice = "Could not clear local conversations: \(error.localizedDescription)"
        }
    }

    private func send(_ text: String, generationID: UUID, speakResponse: Bool) async {
        defer { finishGeneration(generationID) }
        await loadConversationsIfNeeded()
        guard self.generationID == generationID, !Task.isCancelled else { return }

        conversation.append(ConversationMessage(role: .user, text: text))
        await persistCurrentConversation()

        guard let profile = aiProfiles.activeProfile else {
            conversationNotice = "Saved locally. Configure Cloud AI in Settings to receive a response."
            return
        }

        let credential: String
        do {
            credential = try aiProfiles.credential(for: profile.id)
        } catch {
            conversationNotice = error.localizedDescription
            return
        }

        conversationNotice = nil

        do {
            let answer = try await aiClient.response(
                to: conversation,
                profile: profile,
                credential: credential
            )
            try Task.checkCancellation()
            guard self.generationID == generationID else { throw CancellationError() }
            conversation.append(ConversationMessage(role: .assistant, text: answer))
            await persistCurrentConversation()
            if speakResponse {
                do {
                    try speechOutput.speak(answer)
                } catch {
                    conversationNotice = "The answer is saved, but it could not be spoken: \(error.localizedDescription)"
                }
            }
        } catch is CancellationError {
            conversationNotice = "Response stopped. Your message remains saved locally."
        } catch {
            conversationNotice = error.localizedDescription
        }
    }

    private func finishGeneration(_ id: UUID) {
        guard generationID == id else { return }
        generationID = nil
        generationTask = nil
        isGenerating = false
    }

    private func consume(_ event: GlassesAssistantAudioEvent) {
        switch event {
        case .started:
            beginGlassesAssistantSession()
        case .pcmBuffer(let buffer):
            consumeGlassesAudio(buffer)
        case .ended:
            endGlassesAssistantSession()
        }
    }

    private func beginGlassesAssistantSession() {
        guard applicationIsActive else {
            speechError = "Open AD Glasses to use the glasses Assistant button or “Hey Cyan”."
            return
        }
        guard let streamingTranscriber = transcriber as? any ExternalAudioSpeechTranscribing else {
            speechError = "The selected Apple speech engine cannot accept glasses audio."
            return
        }

        cancelResponse()
        speechOutput.stop()
        speechError = nil
        clearTranscript()
        glassesSpeechStartTask?.cancel()
        let sessionID = UUID()
        glassesAssistantSessionID = sessionID
        pendingGlassesAudio.removeAll(keepingCapacity: true)
        isGlassesSpeechReady = false
        glassesStreamDidEnd = false

        glassesSpeechStartTask = Task { [weak self] in
            guard let self else { return }
            do {
                try await streamingTranscriber.startExternalAudio()
                guard glassesAssistantSessionID == sessionID, !Task.isCancelled else {
                    await streamingTranscriber.finishExternalAudio()
                    return
                }
                isGlassesSpeechReady = true
                let bufferedAudio = pendingGlassesAudio
                pendingGlassesAudio.removeAll(keepingCapacity: true)
                bufferedAudio.forEach(streamingTranscriber.appendExternalAudio)
                if glassesStreamDidEnd {
                    await finishGlassesAssistantSession(sessionID: sessionID)
                }
            } catch is CancellationError {
                return
            } catch {
                guard glassesAssistantSessionID == sessionID else { return }
                speechError = error.localizedDescription
                resetGlassesAssistantState()
            }
        }
    }

    private func consumeGlassesAudio(_ buffer: AVAudioPCMBuffer) {
        guard glassesAssistantSessionID != nil, !glassesStreamDidEnd,
              let streamingTranscriber = transcriber as? any ExternalAudioSpeechTranscribing else {
            return
        }
        if isGlassesSpeechReady {
            streamingTranscriber.appendExternalAudio(buffer)
            return
        }

        if pendingGlassesAudio.count == maximumPendingGlassesPackets {
            pendingGlassesAudio.removeFirst()
        }
        pendingGlassesAudio.append(buffer)
    }

    private func endGlassesAssistantSession() {
        guard let sessionID = glassesAssistantSessionID else { return }
        glassesStreamDidEnd = true
        guard isGlassesSpeechReady else { return }
        Task { [weak self] in
            await self?.finishGlassesAssistantSession(sessionID: sessionID)
        }
    }

    private func finishGlassesAssistantSession(sessionID: UUID) async {
        guard glassesAssistantSessionID == sessionID,
              let streamingTranscriber = transcriber as? any ExternalAudioSpeechTranscribing else {
            return
        }
        isGlassesSpeechReady = false
        await streamingTranscriber.finishExternalAudio()
        guard glassesAssistantSessionID == sessionID else { return }

        let text = transcriber.snapshot.transcript
            .trimmingCharacters(in: .whitespacesAndNewlines)
        resetGlassesAssistantState()
        guard !text.isEmpty else {
            speechError = "I didn’t hear a question from the glasses. Try again."
            return
        }
        chatDraft = text
        sendChatMessage(source: .glassesVoice, speakResponse: true)
    }

    private func cancelGlassesAssistantSession() async {
        glassesSpeechStartTask?.cancel()
        if let streamingTranscriber = transcriber as? any ExternalAudioSpeechTranscribing {
            await streamingTranscriber.finishExternalAudio()
        }
        resetGlassesAssistantState()
    }

    private func resetGlassesAssistantState() {
        glassesSpeechStartTask?.cancel()
        glassesSpeechStartTask = nil
        glassesAssistantSessionID = nil
        pendingGlassesAudio.removeAll(keepingCapacity: true)
        isGlassesSpeechReady = false
        glassesStreamDidEnd = false
    }

    private func loadConversationsIfNeeded() async {
        guard !didLoadConversations else { return }
        guard let conversationLoadTask else {
            didLoadConversations = true
            isConversationStoreReady = true
            return
        }

        do {
            let loaded = try await conversationLoadTask.value
            guard !didLoadConversations else { return }
            didLoadConversations = true
            self.conversationLoadTask = nil
            conversations = loaded
            if !userChangedConversationBeforeLoad, let latest = loaded.first {
                currentConversationID = latest.id
                conversation = latest.messages
            }
            isConversationStoreReady = true
        } catch is CancellationError {
            didLoadConversations = true
            self.conversationLoadTask = nil
            isConversationStoreReady = true
        } catch {
            didLoadConversations = true
            self.conversationLoadTask = nil
            isConversationStoreReady = true
            conversationNotice = "Could not load local conversations: \(error.localizedDescription)"
        }
    }

    private func persistCurrentConversation() async {
        guard !conversation.isEmpty else { return }
        let now = Date()
        let title = conversation.first(where: { $0.role == .user })?.text
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .prefix(52)
        let fallbackCreatedAt = conversation.first?.createdAt ?? now
        let existing = conversations.first(where: { $0.id == currentConversationID })
        let thread = ConversationThread(
            id: currentConversationID,
            title: title.map(String.init) ?? "Conversation",
            messages: conversation,
            createdAt: existing?.createdAt ?? fallbackCreatedAt,
            updatedAt: now
        )

        conversations.removeAll { $0.id == currentConversationID }
        conversations.insert(thread, at: 0)
        await saveConversations()
    }

    private func saveConversations() async {
        do {
            try await conversationStore.save(conversations)
        } catch {
            conversationNotice = "Could not save this conversation: \(error.localizedDescription)"
        }
    }
}
