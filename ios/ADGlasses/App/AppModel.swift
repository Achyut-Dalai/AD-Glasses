@preconcurrency import AVFoundation
import Combine
import Foundation
import UIKit

@MainActor
final class AppModel: ObservableObject {
    @Published private(set) var transcript = ""
    @Published private(set) var isTranscribing = false
    @Published private(set) var isPreparingTranscription = false
    @Published private(set) var isManualTranscription = false
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
    private let lensProcessor = LensImageProcessor()
    private let visualAI = ADVisualAIClient()
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

    // HeyCyan Opus packets represent 20 ms of 16-kHz mono audio. Keep up to ~30 seconds while
    // SpeechAnalyzer performs a first/cold asset or pipeline start, matching Android Moonshine's
    // bounded 30-second queue instead of dropping the first words after only ~2 seconds.
    private let maximumPendingGlassesPackets = 1_500
    private weak var glassesManager: GlassesManager?
    private var glassesConnectionCancellable: AnyCancellable?

    private enum LocalAssistantAction {
        case capturePhoto
        case startVideo
        case stopVideo
        case startAudio
        case stopAudio
        case readVisibleText
        case visualQuestion
    }

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
            let wasRunning = isTranscribing
            transcript = snapshot.transcript
            isTranscribing = snapshot.isRunning
            speechEngineName = snapshot.engineName

            // Apple recognition now endpoints itself after transcript stability. Convert that engine
            // transition into product semantics: manual dictation becomes a draft; a glasses-button
            // voice turn is finalized and dispatched even if the firmware's trailing ended event is
            // delayed.
            if wasRunning, !snapshot.isRunning, !isStoppingTranscription {
                if isManualTranscription {
                    isManualTranscription = false
                    useTranscriptAsDraft()
                } else if let sessionID = glassesAssistantSessionID, isGlassesSpeechReady {
                    Task { [weak self] in
                        await self?.finishGlassesAssistantSession(sessionID: sessionID)
                    }
                }
            }
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
        glassesConnectionCancellable?.cancel()
    }

    func startTranscription() async {
        guard !isPreparingTranscription,
              !isStoppingTranscription,
              !transcriber.snapshot.isRunning,
              generationTask == nil,
              !isGlassesAssistantAudioActive else { return }
        speechError = nil
        isPreparingTranscription = true
        speechOutput.stop()
        defer { isPreparingTranscription = false }

        do {
            try await transcriber.start()
            isManualTranscription = transcriber.snapshot.isRunning
        } catch {
            isManualTranscription = false
            speechError = error.localizedDescription
        }
    }

    func stopTranscription() async {
        if isStoppingTranscription {
            while isStoppingTranscription {
                await Task.yield()
            }
            return
        }
        guard transcriber.snapshot.isRunning else {
            isManualTranscription = false
            return
        }
        let shouldPreserveManualDraft = isManualTranscription
        speechError = nil
        isStoppingTranscription = true
        defer {
            isStoppingTranscription = false
            isManualTranscription = false
        }
        await transcriber.stop()
        if shouldPreserveManualDraft {
            useTranscriptAsDraft()
        }
    }

    func finishManualTranscriptionAsDraft() async {
        guard isManualTranscription else { return }
        await stopTranscription()
    }

    func toggleTranscription() async {
        guard !isPreparingTranscription else { return }
        if transcriber.snapshot.isRunning {
            await stopTranscription()
        } else {
            await startTranscription()
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

    func sendChatMessage(
        source: AssistantRequestSource = .chat,
        speakResponse: Bool = false
    ) {
        let text = chatDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        let localAction = localAssistantAction(for: text)
        let route = requestRouter.route(
            AssistantRequest(text: text, source: source, hasImage: false)
        )
        guard (!text.isEmpty && (localAction != nil || route != .clarify)), generationTask == nil else { return }
        chatDraft = ""

        let id = UUID()
        generationID = id
        isGenerating = true
        if speakResponse {
            beginVoiceResponseBackgroundTask()
        }
        generationTask = Task { [weak self] in
            guard let self else { return }
            if let localAction {
                await executeLocalAction(localAction, text: text, generationID: id, speakResponse: speakResponse)
                return
            }
            switch route {
            case .capturePhoto:
                await executePhotoCapture(text, generationID: id, speakResponse: speakResponse)
            case .conversation:
                await send(text, generationID: id, speakResponse: speakResponse)
            case .visualQuestion:
                await executeLocalAction(.visualQuestion, text: text, generationID: id, speakResponse: speakResponse)
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

        glassesConnectionCancellable?.cancel()
        glassesConnectionCancellable = glassesManager.$activeProviderID
            .combineLatest(glassesManager.$providers)
            .sink { [weak self, weak glassesManager] _, _ in
                guard let self, let glassesManager,
                      isGlassesAssistantAudioActive,
                      !glassesManager.connectionState.isConnected else {
                    return
                }
                Task { [weak self] in
                    await self?.cancelGlassesAssistantSession()
                }
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
            conversationNotice = "Response stopped."
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
        await executeLocalAction(.capturePhoto, text: text, generationID: generationID, speakResponse: speakResponse)
    }

    private func executeLocalAction(
        _ action: LocalAssistantAction,
        text: String,
        generationID: UUID,
        speakResponse: Bool
    ) async {
        defer { finishGeneration(generationID) }
        await loadConversationsIfNeeded()
        guard self.generationID == generationID, !Task.isCancelled else { return }

        conversation.append(ConversationMessage(role: .user, text: text))
        await persistCurrentConversation()

        let answer = await localActionAnswer(action, originalText: text)
        guard self.generationID == generationID, !Task.isCancelled else { return }
        conversation.append(ConversationMessage(role: .assistant, text: answer))
        await persistCurrentConversation()
        conversationNotice = nil

        if speakResponse {
            do {
                try speechOutput.speak(answer)
            } catch {
                conversationNotice = "The result is saved, but it could not be spoken: \(error.localizedDescription)"
            }
        }
    }

    private func localActionAnswer(_ action: LocalAssistantAction, originalText: String) async -> String {
        guard let glassesManager else {
            return "Connect AD Glasses first."
        }

        switch action {
        case .capturePhoto:
            let succeeded = await glassesManager.requestPhotoCapture()
            return succeeded
                ? "Photo taken. It is saved on AD Glasses and will appear after your next Library sync."
                : (glassesManager.errorMessage ?? "The photo could not be taken.")

        case .startVideo:
            if glassesManager.isVideoRecording { return "Video is already recording." }
            let succeeded = await glassesManager.toggleVideoRecording()
            return succeeded ? "Video recording started." : (glassesManager.errorMessage ?? "Video recording could not start.")

        case .stopVideo:
            if !glassesManager.isVideoRecording { return "Video recording is already stopped." }
            let succeeded = await glassesManager.toggleVideoRecording()
            return succeeded ? "Video recording stopped." : (glassesManager.errorMessage ?? "Video recording could not stop.")

        case .startAudio:
            if glassesManager.isAudioRecording { return "Audio is already recording." }
            let succeeded = await glassesManager.toggleAudioRecording()
            return succeeded ? "Audio recording started." : (glassesManager.errorMessage ?? "Audio recording could not start.")

        case .stopAudio:
            if !glassesManager.isAudioRecording { return "Audio recording is already stopped." }
            let succeeded = await glassesManager.toggleAudioRecording()
            return succeeded ? "Audio recording stopped." : (glassesManager.errorMessage ?? "Audio recording could not stop.")

        case .readVisibleText:
            guard let capture = await glassesManager.requestVisualCapture() else {
                return glassesManager.errorMessage ?? "Lens could not capture what you are looking at."
            }
            do {
                let prepared = try await lensProcessor.prepare(capture.jpegData)
                let text = try await lensProcessor.recognizeText(in: prepared)
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                return text.isEmpty ? "I could not find readable text in front of you." : text
            } catch {
                return error.localizedDescription
            }

        case .visualQuestion:
            guard let profile = aiProfiles.activeProfile else {
                return "Configure Cloud AI in Settings before asking visual questions."
            }
            let credential: String
            do {
                credential = try aiProfiles.credential(for: profile.id)
            } catch {
                return error.localizedDescription
            }
            guard let capture = await glassesManager.requestVisualCapture() else {
                return glassesManager.errorMessage ?? "Lens could not capture what you are looking at."
            }
            do {
                let prepared = try await lensProcessor.prepare(capture.jpegData)
                return try await visualAI.answer(
                    question: originalText,
                    imageJPEGData: prepared.jpegData,
                    profile: profile,
                    credential: credential
                )
            } catch {
                return error.localizedDescription
            }
        }
    }

    private func localAssistantAction(for rawText: String) -> LocalAssistantAction? {
        let text = Self.normalizedCommand(rawText)
        guard !text.isEmpty,
              !text.contains("how do i"),
              !text.contains("how to"),
              !text.contains("can you explain") else { return nil }

        let words = Set(text.split(separator: " ").map(String.init))
        if words.isDisjoint(with: ["not", "dont", "never"]) {
            let captureVerb = !words.isDisjoint(with: ["take", "capture", "click", "snap", "shoot"])
            let photoSubject = !words.isDisjoint(with: ["photo", "picture", "photograph"])
            if captureVerb && photoSubject { return .capturePhoto }
        }

        if Self.containsAny(text, ["stop video", "stop recording video", "end video", "finish video"]) {
            return .stopVideo
        }
        if Self.containsAny(text, ["start video", "record video", "start recording video", "begin video"]) {
            return .startVideo
        }
        if Self.containsAny(text, ["stop audio", "stop audio recording", "stop recording audio", "end audio recording"]) {
            return .stopAudio
        }
        if Self.containsAny(text, ["start audio", "record audio", "start audio recording", "start recording audio", "begin audio recording"]) {
            return .startAudio
        }

        if Self.containsAny(text, [
            "read this", "read the text", "read this text", "read this sign", "read what i see",
            "what does this say", "scan this text"
        ]) {
            return .readVisibleText
        }

        if Self.containsAny(text, [
            "what am i looking at", "what do you see", "describe what i see", "describe the scene",
            "describe what is in front of me", "what is in front of me", "what is this object",
            "identify this object", "what is this", "explain what i am looking at", "anything important here",
            "translate what i see", "translate this sign", "translate this text"
        ]) {
            return .visualQuestion
        }

        return nil
    }

    private static func normalizedCommand(_ value: String) -> String {
        value.lowercased()
            .replacingOccurrences(of: "’", with: "'")
            .replacingOccurrences(of: "'", with: "")
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { !$0.isEmpty }
            .joined(separator: " ")
    }

    private static func containsAny(_ text: String, _ phrases: [String]) -> Bool {
        phrases.contains(where: text.contains)
    }

    private func finishGeneration(_ id: UUID) {
        guard generationID == id else { return }
        generationID = nil
        generationTask = nil
        isGenerating = false
        endVoiceResponseBackgroundTask()
    }

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

        let interruptedManualTranscription = isManualTranscription
        isManualTranscription = false
        cancelResponse()
        speechOutput.stop()
        speechError = nil
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
                if transcriber.snapshot.isRunning || isStoppingTranscription {
                    await stopTranscription()
                    guard glassesAssistantSessionID == sessionID, !Task.isCancelled else { return }
                }
                if interruptedManualTranscription {
                    useTranscriptAsDraft()
                }
                clearTranscript()

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
        let preservedDraft = chatDraft
        chatDraft = text
        sendChatMessage(source: .glassesVoice, speakResponse: true)
        chatDraft = preservedDraft
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
