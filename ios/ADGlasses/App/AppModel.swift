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
    @Published var conversationNotice: String?

    let aiProfiles: AIProfileStore

    private let transcriber: any SpeechTranscribing
    private let conversationStore: ConversationStore
    private let aiClient: any AIResponding
    private var generationTask: Task<Void, Never>?

    init(
        transcriber: (any SpeechTranscribing)? = nil,
        aiProfiles: AIProfileStore? = nil,
        conversationStore: ConversationStore = ConversationStore(),
        aiClient: (any AIResponding)? = nil
    ) {
        let selectedTranscriber = transcriber ?? AppleSpeechTranscriber.make()
        self.transcriber = selectedTranscriber
        self.aiProfiles = aiProfiles ?? AIProfileStore()
        self.conversationStore = conversationStore
        self.aiClient = aiClient ?? CloudAIClient()
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

        Task { [weak self] in
            await self?.loadConversations()
        }
    }

    deinit {
        generationTask?.cancel()
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

    func clearTranscript() {
        transcript = ""
        transcriber.resetTranscript()
    }

    func useTranscriptAsDraft() {
        guard !transcript.isEmpty else { return }
        chatDraft = transcript
    }

    func sendChatMessage() {
        let text = chatDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !isGenerating else { return }
        chatDraft = ""

        generationTask = Task { [weak self] in
            await self?.send(text)
        }
    }

    func cancelResponse() {
        generationTask?.cancel()
        generationTask = nil
        isGenerating = false
    }

    func startNewConversation() {
        generationTask?.cancel()
        generationTask = nil
        isGenerating = false
        currentConversationID = UUID()
        conversation.removeAll()
        chatDraft = ""
        conversationNotice = nil
        clearTranscript()
    }

    func openConversation(_ id: UUID) {
        guard let thread = conversations.first(where: { $0.id == id }) else { return }
        generationTask?.cancel()
        generationTask = nil
        isGenerating = false
        currentConversationID = thread.id
        conversation = thread.messages
        conversationNotice = nil
    }

    func deleteConversation(_ id: UUID) async {
        conversations.removeAll { $0.id == id }
        if currentConversationID == id {
            currentConversationID = UUID()
            conversation.removeAll()
            conversationNotice = nil
        }
        await saveConversations()
    }

    func deleteAllConversations() async {
        generationTask?.cancel()
        generationTask = nil
        isGenerating = false
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

    private func send(_ text: String) async {
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

        isGenerating = true
        conversationNotice = nil
        defer {
            isGenerating = false
            generationTask = nil
        }

        do {
            let answer = try await aiClient.response(
                to: conversation,
                profile: profile,
                credential: credential
            )
            try Task.checkCancellation()
            conversation.append(ConversationMessage(role: .assistant, text: answer))
            await persistCurrentConversation()
        } catch is CancellationError {
            conversationNotice = "Response stopped. Your message remains saved locally."
        } catch {
            conversationNotice = error.localizedDescription
        }
    }

    private func loadConversations() async {
        do {
            let loaded = try await conversationStore.load()
            conversations = loaded
            if let latest = loaded.first {
                currentConversationID = latest.id
                conversation = latest.messages
            }
        } catch {
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
