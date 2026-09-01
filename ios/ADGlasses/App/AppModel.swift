@preconcurrency import AVFoundation
import Combine
import Foundation
import UIKit

@MainActor
final class AppModel: ObservableObject {
    @Published private(set) var transcript = ""
    @Published private(set) var isTranscribing = false
    @Published private(set) var isStoppingTranscription = false
    @Published private(set) var speechEngineName = "Apple Speech"
    @Published var speechError: String?
    @Published var chatDraft = ""
    @Published private(set) var conversation: [ConversationMessage] = []
    @Published private(set) var conversations: [ConversationThread] = []
    @Published private(set) var currentConversationID = UUID()
    @Published private(set) var isGenerating = false
    @Published private(set) var isGlassesAssistantAudioActive = false
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
    private var responseBackgroundTaskID: UIBackgroundTaskIdentifier = .invalid
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
    private weak var glassesManager: GlassesManager?

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

    func startTranscription() async {
        guard !isStoppingTranscription, !transcriber.snapshot.isRunning else { return }
        speechError = nil
        do {
            try await transcriber.start()
        } catch {
            speechError = error.localizedDescription
        }
    }

    func stopTranscription() async {
        guard transcriber.snapshot.isRunning, !isStoppingTranscription else { return }
        speechError = nil
        isStoppingTranscription = true
        defer { isStoppingTranscription = false }
        await transcriber.stop()
    }

    func toggleTranscription() async {
        if transcriber.snapshot.isRunning {
            await stopTranscription()
        } else {
            await startTranscription()
        }
    }

    @discardableResult
    func startPhoneVoiceTranscriptionFromWakeWord() async -> Bool {
        guard !isStoppingTranscription, !transcriber.snapshot.isRunning else { return false }
        cancelResponse()
        speechOutput.stop()
        clearTranscript()
        do {
            try await transcriber.start()
            return true
        } catch {
            speechError = error.localizedDescription
            return false
        }
    }

    func finishPhoneVoiceTranscriptionFromWakeWord() async {
        if transcriber.snapshot.isRunning {
            await stopTranscription()
        }
        let text = transcript.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else {
            speechError = "I didn’t hear a question. Try the wake phrase again."
            return
        }
        chatDraft = text
        sendChatMessage(source: .phoneVoice, speakResponse: true)
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
        guard route != .clarify, generationTask == nil else { return }
        chatDraft = ""

        let id = UUID()
        generationID = id
        isGenerating = true
        if speakResponse {
            beginVoiceResponseBackgroundTask()
        }
        generationTask = Task { [weak self] in
            guard let self else { return }
            switch route {
            case .capturePhoto:
                await executePhotoCapture(
                    text,
                    generationID: id,
                    speakResponse: speakResponse
                )
            case .conversation:
                await send(text, generationID: id, speakResponse: speakResponse)
            case .visualQuestion:
                conversationNotice = "Lens needs an image before it can answer that question."
                finishGeneration(id)
            case .clarify:
                finishGeneration(id)
            }
        }
    }

    func attach(to glassesManager: GlassesManager) {
        self.glassesManager = glassesManager
        glassesManager.onAssistantAudioEvent = { [weak self] event in
            self?.consume(event)
        }
    }

    func setApplicationActive(_ active: Bool) {
        applicationIsActive = active
    }

    func cancelResponse() {
        let hadActiveResponse = generationTask != nil
        generationTask?.cancel()
        generationTask = nil
        generationID = nil
        isGenerating = false
        endVoiceResponseBackgroundTask()
        if hadActiveResponse {
            conversationNotice = "Response stopped. Your message remains saved locally."
        }
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
            guard self.generationID == generationID else { return }
            conversationNotice = "Response stopped. Your message remains saved locally."
        } catch {
            guard self.generationID == generationID else { return }
            conversationNotice = error.localizedDescription
        }
    }

    private func executePhotoCapture(
        _ text: String,
        generationID: UUID,
        speakResponse: Bool
    ) async {
        defer { finishGeneration(generationID) }
        await loadConversationsIfNeeded()
        guard self.generationID == generationID, !Task.isCancelled else { return }

        conversation.append(ConversationMessage(role: .user, text: text))
        await persistCurrentConversation()

        let didCapture = await glassesManager?.requestPhotoCapture() == true
        try? Task.checkCancellation()
        guard self.generationID == generationID, !Task.isCancelled else { return }

        let answer: String
        if didCapture {
            answer = "Photo taken. It is saved on AD Glasses and will appear after your next Library sync."
            conversationNotice = nil
        } else {
            answer = glassesManager?.errorMessage ?? "Connect AD Glasses before taking a photo."
            conversationNotice = answer
        }
        conversation.append(ConversationMessage(role: .assistant, text: answer))
        await persistCurrentConversation()

        if speakResponse {
            do {
                try speechOutput.speak(answer)
            } catch {
                conversationNotice = "The result is saved, but it could not be spoken: \(error.localizedDescription)"
            }
        }
    }

    private func finishGeneration(_ id: UUID) {
        guard generationID == id else { return }
        generationID = nil
        generationTask = nil
        isGenerating = false
        endVoiceResponseBackgroundTask()
    }

    /// Gives a glasses/phone voice turn a finite opportunity to finish its network response and
    /// persist it when the user locks the screen or changes apps. Spoken output then continues
    /// under the app's background-audio mode; this is not used as an indefinite execution claim.
    private func beginVoiceResponseBackgroundTask() {
        endVoiceResponseBackgroundTask()
        responseBackgroundTaskID = UIApplication.shared.beginBackgroundTask(
            withName: "Finish AD Glasses voice response"
        ) { [weak self] in
            guard let self else { return }
            generationTask?.cancel()
            endVoiceResponseBackgroundTask()
        }
    }

    private func endVoiceResponseBackgroundTask() {
        guard responseBackgroundTaskID != .invalid else { return }
        UIApplication.shared.endBackgroundTask(responseBackgroundTaskID)
        responseBackgroundTaskID = .invalid
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
        isGlassesAssistantAudioActive = true
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
        isGlassesAssistantAudioActive = false
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
