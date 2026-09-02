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
    @Published private(set) var isPhoneVoiceQuestionActive = false
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
    private var phoneVoiceInputMode: PhoneVoiceInputMode?

    // HeyCyan Opus packets represent 20 ms of 16-kHz mono audio. Keep up to ~30 seconds while
    // SpeechAnalyzer performs a first/cold asset or pipeline start, matching Android Moonshine's
    // bounded 30-second queue instead of dropping the first words after only ~2 seconds.
    private let maximumPendingGlassesPackets = 1_500
    private weak var glassesManager: GlassesManager?
    private var glassesConnectionCancellable: AnyCancellable?
    private var speechOutputCancellable: AnyCancellable?

    private enum LocalAssistantAction {
        case capturePhoto
        case startVideo
        case stopVideo
        case startAudio
        case stopAudio
        case stopRecording
        case readVisibleText
        case visualQuestion
    }

    private struct LocalAssistantResult {
        let answer: String
    }

    private enum PhoneVoiceInputMode {
        case draft
        case question
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
                    finalizePhoneVoiceInput()
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

        // A spoken glasses response is asynchronous: AVSpeechSynthesizer returns as soon as the
        // utterance is queued. Keep the finite background task alive until playback really ends so
        // locking the phone or switching apps does not silence a response between queue and speech.
        speechOutputCancellable = self.speechOutput.$isSpeaking
            .removeDuplicates()
            .sink { [weak self] isSpeaking in
                guard let self, !isSpeaking, generationTask == nil else { return }
                endVoiceResponseBackgroundTask()
            }

        conversationLoadTask = Task { try await conversationStore.load() }
        Task { [weak self] in await self?.loadConversationsIfNeeded() }
    }

    deinit {
        generationTask?.cancel()
        conversationLoadTask?.cancel()
        glassesSpeechStartTask?.cancel()
        glassesConnectionCancellable?.cancel()
        speechOutputCancellable?.cancel()
    }

    func startTranscription() async {
        await startPhoneVoiceInput(mode: .draft)
    }

    /// Starts a complete Assistant turn. Silence endpointing or an explicit stop sends the final
    /// transcript immediately, matching the glasses/Android Ask interaction instead of producing
    /// an unsent composer draft.
    func startVoiceQuestion() async {
        await startPhoneVoiceInput(mode: .question)
    }

    private func startPhoneVoiceInput(mode: PhoneVoiceInputMode) async {
        guard !isPreparingTranscription,
              !isStoppingTranscription,
              !transcriber.snapshot.isRunning,
              generationTask == nil,
              !isGlassesAssistantAudioActive else { return }
        speechError = nil
        isPreparingTranscription = true
        phoneVoiceInputMode = mode
        isPhoneVoiceQuestionActive = mode == .question
        speechOutput.stop()
        defer { isPreparingTranscription = false }

        do {
            try await transcriber.start()
            isManualTranscription = transcriber.snapshot.isRunning
        } catch {
            phoneVoiceInputMode = nil
            isPhoneVoiceQuestionActive = false
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
            finalizePhoneVoiceInput()
            return
        }
        speechError = nil
        isStoppingTranscription = true
        defer {
            isStoppingTranscription = false
        }
        await transcriber.stop()
        finalizePhoneVoiceInput()
    }

    func finishManualTranscriptionAsDraft() async {
        guard isManualTranscription else { return }
        await stopTranscription()
    }

    func finishVoiceQuestion() async {
        guard isPhoneVoiceQuestionActive else { return }
        await stopTranscription()
    }

    /// Abandons a phone-microphone turn without dispatching its partial transcript. Normal voice
    /// questions finish through silence endpointing; this is only the user's escape hatch.
    func cancelVoiceQuestion() async {
        guard isPhoneVoiceQuestionActive else { return }
        phoneVoiceInputMode = nil
        isPhoneVoiceQuestionActive = false
        isManualTranscription = false
        isStoppingTranscription = true
        await transcriber.stop()
        isStoppingTranscription = false
        clearTranscript()
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
        speakResponse: Bool = true
    ) {
        let text = chatDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        let routed = requestRouter.route(
            AssistantRequest(text: text, source: source, hasImage: false)
        )
        let route = effectiveAssistantRoute(routed, text: text, source: source)
        guard !text.isEmpty, route != .clarify, generationTask == nil else { return }
        speechOutput.stop()
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
                await executeLocalAction(.capturePhoto, text: text, generationID: id, speakResponse: speakResponse)
            case .startVideo:
                await executeLocalAction(.startVideo, text: text, generationID: id, speakResponse: speakResponse)
            case .stopVideo:
                await executeLocalAction(.stopVideo, text: text, generationID: id, speakResponse: speakResponse)
            case .startAudio:
                await executeLocalAction(.startAudio, text: text, generationID: id, speakResponse: speakResponse)
            case .stopAudio:
                await executeLocalAction(.stopAudio, text: text, generationID: id, speakResponse: speakResponse)
            case .stopRecording:
                await executeLocalAction(.stopRecording, text: text, generationID: id, speakResponse: speakResponse)
            case .readVisibleText:
                await executeLocalAction(.readVisibleText, text: text, generationID: id, speakResponse: speakResponse)
            case .visualQuestion:
                await executeLocalAction(.visualQuestion, text: text, generationID: id, speakResponse: speakResponse)
            case .conversation:
                await send(text, generationID: id, speakResponse: speakResponse)
            case .clarify:
                finishGeneration(id)
            }
        }
    }

    /// Product-level command policy sits after lexical routing and before execution. The glasses
    /// use a compact "click" command for photo capture. Recording-stop commands are intentionally
    /// not executable from voice because those recording modes already own the relevant audio path;
    /// their physical/app buttons remain the deterministic controls.
    private func effectiveAssistantRoute(
        _ routed: AssistantRoute,
        text: String,
        source: AssistantRequestSource
    ) -> AssistantRoute {
        let normalized = text.lowercased()
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { !$0.isEmpty }
            .joined(separator: " ")

        if normalized == "click" { return .capturePhoto }
        if routed == .capturePhoto { return .conversation }

        switch source {
        case .phoneVoice, .glassesVoice:
            if routed == .stopVideo || routed == .stopAudio || routed == .stopRecording {
                return .conversation
            }
        case .chat, .lensImage:
            break
        }
        return routed
    }

    private func finalizePhoneVoiceInput() {
        guard let mode = phoneVoiceInputMode else {
            isManualTranscription = false
            isPhoneVoiceQuestionActive = false
            return
        }
        phoneVoiceInputMode = nil
        isManualTranscription = false
        isPhoneVoiceQuestionActive = false

        let text = transcript.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else {
            if mode == .question {
                speechError = "I didn’t hear a question. Try again."
            }
            return
        }

        switch mode {
        case .draft:
            useTranscriptAsDraft()
        case .question:
            let preservedDraft = chatDraft
            chatDraft = text
            sendChatMessage(source: .phoneVoice, speakResponse: true)
            chatDraft = preservedDraft
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
        let hadActiveResponse = generationTask != nil || speechOutput.isSpeaking
        generationTask?.cancel()
        speechOutput.stop()
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
        let attachments = conversations
            .first(where: { $0.id == id })?
            .messages
            .compactMap(\.imageAttachment) ?? []
        conversations.removeAll { $0.id == id }
        if currentConversationID == id {
            currentConversationID = UUID()
            conversation.removeAll()
            conversationNotice = nil
        }
        do {
            try await conversationStore.save(conversations)
            if !attachments.isEmpty {
                try await conversationStore.deleteImageAttachments(attachments)
            }
        } catch {
            conversationNotice = "Could not delete this conversation: \(error.localizedDescription)"
        }
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

    func conversationImageData(for attachment: ConversationImageAttachment) async -> Data? {
        try? await conversationStore.loadImageAttachment(attachment)
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
            let assistantMessageID = UUID()
            let assistantMessageDate = Date()
            let visibleResponse = AssistantVisibleResponseBuffer()
            let speechBuffer = AssistantStreamingSpeechBuffer()
            var streamedText = ""
            var speechFailure: String?

            let answer = try await aiClient.streamingResponse(
                to: conversation,
                profile: profile,
                credential: credential
            ) { [weak self] delta in
                guard let self,
                      self.generationID == generationID,
                      !Task.isCancelled else { return }
                let visibleDelta = visibleResponse.accept(delta)
                guard !visibleDelta.isEmpty else { return }
                streamedText.append(visibleDelta)
                let message = ConversationMessage(
                    id: assistantMessageID,
                    role: .assistant,
                    text: streamedText,
                    createdAt: assistantMessageDate
                )
                if let index = conversation.firstIndex(where: { $0.id == assistantMessageID }) {
                    conversation[index] = message
                } else {
                    conversation.append(message)
                }

                if speakResponse, speechFailure == nil {
                    for segment in speechBuffer.accept(visibleDelta) {
                        do {
                            try speechOutput.enqueue(segment)
                        } catch {
                            speechFailure = error.localizedDescription
                            break
                        }
                    }
                }
            }
            try Task.checkCancellation()
            guard self.generationID == generationID else { throw CancellationError() }
            let finalized = try visibleResponse.finish(answer)
            if !finalized.remainingDelta.isEmpty {
                streamedText.append(finalized.remainingDelta)
                if speakResponse, speechFailure == nil {
                    for segment in speechBuffer.accept(finalized.remainingDelta) {
                        do {
                            try speechOutput.enqueue(segment)
                        } catch {
                            speechFailure = error.localizedDescription
                            break
                        }
                    }
                }
            }
            let finalMessage = ConversationMessage(
                id: assistantMessageID,
                role: .assistant,
                text: finalized.text,
                createdAt: assistantMessageDate
            )
            if let index = conversation.firstIndex(where: { $0.id == assistantMessageID }) {
                conversation[index] = finalMessage
            } else {
                conversation.append(finalMessage)
            }
            await persistCurrentConversation()
            if speakResponse {
                if speechFailure == nil {
                    for segment in speechBuffer.finish() {
                        do {
                            try speechOutput.enqueue(segment)
                        } catch {
                            speechFailure = error.localizedDescription
                            break
                        }
                    }
                }
                if let speechFailure {
                    conversationNotice = "The answer is saved, but it could not be spoken: \(speechFailure)"
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

    private func executeLocalAction(
        _ action: LocalAssistantAction,
        text: String,
        generationID: UUID,
        speakResponse: Bool
    ) async {
        defer { finishGeneration(generationID) }
        await loadConversationsIfNeeded()
        guard self.generationID == generationID, !Task.isCancelled else { return }

        let userMessageID = UUID()
        let userMessageDate = Date()
        conversation.append(
            ConversationMessage(
                id: userMessageID,
                role: .user,
                text: text,
                createdAt: userMessageDate
            )
        )
        await persistCurrentConversation()
        conversationNotice = nil

        let result = await localActionResult(
            action,
            originalText: text,
            generationID: generationID,
            userMessageID: userMessageID,
            userMessageDate: userMessageDate
        )
        guard self.generationID == generationID, !Task.isCancelled else { return }
        conversation.append(ConversationMessage(role: .assistant, text: result.answer))
        await persistCurrentConversation()

        if speakResponse {
            do {
                try speechOutput.speak(result.answer)
            } catch {
                conversationNotice = "The result is saved, but it could not be spoken: \(error.localizedDescription)"
            }
        }
    }

    private func localActionResult(
        _ action: LocalAssistantAction,
        originalText: String,
        generationID: UUID,
        userMessageID: UUID,
        userMessageDate: Date
    ) async -> LocalAssistantResult {
        guard let glassesManager else {
            return LocalAssistantResult(answer: "Connect AD Glasses first.")
        }

        switch action {
        case .capturePhoto:
            let succeeded = await glassesManager.requestPhotoCapture()
            return LocalAssistantResult(
                answer: succeeded
                    ? "Photo taken. It is saved on AD Glasses and will appear after your next Library sync."
                    : (glassesManager.errorMessage ?? "The photo could not be taken.")
            )

        case .startVideo:
            if glassesManager.isVideoRecording {
                return LocalAssistantResult(answer: "Video is already recording.")
            }
            let succeeded = await glassesManager.toggleVideoRecording()
            return LocalAssistantResult(
                answer: succeeded
                    ? "Video recording started."
                    : (glassesManager.errorMessage ?? "Video recording could not start.")
            )

        case .stopVideo:
            if !glassesManager.isVideoRecording {
                return LocalAssistantResult(answer: "Video recording is already stopped.")
            }
            let succeeded = await glassesManager.toggleVideoRecording()
            return LocalAssistantResult(
                answer: succeeded
                    ? "Video recording stopped."
                    : (glassesManager.errorMessage ?? "Video recording could not stop.")
            )

        case .startAudio:
            if glassesManager.isAudioRecording {
                return LocalAssistantResult(answer: "Audio is already recording.")
            }
            let succeeded = await glassesManager.toggleAudioRecording()
            return LocalAssistantResult(
                answer: succeeded
                    ? "Audio recording started."
                    : (glassesManager.errorMessage ?? "Audio recording could not start.")
            )

        case .stopAudio:
            if !glassesManager.isAudioRecording {
                return LocalAssistantResult(answer: "Audio recording is already stopped.")
            }
            let succeeded = await glassesManager.toggleAudioRecording()
            return LocalAssistantResult(
                answer: succeeded
                    ? "Audio recording stopped."
                    : (glassesManager.errorMessage ?? "Audio recording could not stop.")
            )

        case .stopRecording:
            if glassesManager.isVideoRecording {
                let succeeded = await glassesManager.toggleVideoRecording()
                return LocalAssistantResult(
                    answer: succeeded
                        ? "Video recording stopped."
                        : (glassesManager.errorMessage ?? "Video recording could not stop.")
                )
            }
            if glassesManager.isAudioRecording {
                let succeeded = await glassesManager.toggleAudioRecording()
                return LocalAssistantResult(
                    answer: succeeded
                        ? "Audio recording stopped."
                        : (glassesManager.errorMessage ?? "Audio recording could not stop.")
                )
            }
            return LocalAssistantResult(answer: "Nothing is recording.")

        case .readVisibleText:
            guard let capture = await glassesManager.requestVisualCapture() else {
                return LocalAssistantResult(
                    answer: glassesManager.errorMessage ?? "Lens could not capture what you are looking at."
                )
            }
            do {
                let prepared = try await lensProcessor.prepare(capture.jpegData)
                await attachPreparedImage(
                    prepared,
                    generationID: generationID,
                    userMessageID: userMessageID,
                    userMessageText: originalText,
                    userMessageDate: userMessageDate
                )
                do {
                    let recognizedText = try await lensProcessor.recognizeText(in: prepared)
                        .trimmingCharacters(in: .whitespacesAndNewlines)
                    return LocalAssistantResult(
                        answer: recognizedText.isEmpty
                            ? "I could not find readable text in front of you."
                            : recognizedText
                    )
                } catch {
                    return LocalAssistantResult(answer: error.localizedDescription)
                }
            } catch {
                return LocalAssistantResult(answer: error.localizedDescription)
            }

        case .visualQuestion:
            guard let profile = aiProfiles.activeProfile else {
                return LocalAssistantResult(
                    answer: "Configure Cloud AI in Settings before asking visual questions."
                )
            }
            let credential: String
            do {
                credential = try aiProfiles.credential(for: profile.id)
            } catch {
                return LocalAssistantResult(answer: error.localizedDescription)
            }
            guard let capture = await glassesManager.requestVisualCapture() else {
                return LocalAssistantResult(
                    answer: glassesManager.errorMessage ?? "Lens could not capture what you are looking at."
                )
            }
            do {
                let prepared = try await lensProcessor.prepare(capture.jpegData)
                await attachPreparedImage(
                    prepared,
                    generationID: generationID,
                    userMessageID: userMessageID,
                    userMessageText: originalText,
                    userMessageDate: userMessageDate
                )
                do {
                    let answer = try await visualAI.answer(
                        question: originalText,
                        imageJPEGData: prepared.jpegData,
                        profile: profile,
                        credential: credential
                    )
                    return LocalAssistantResult(answer: answer)
                } catch {
                    return LocalAssistantResult(answer: error.localizedDescription)
                }
            } catch {
                return LocalAssistantResult(answer: error.localizedDescription)
            }
        }
    }

    private func attachPreparedImage(
        _ prepared: LensPreparedImage,
        generationID: UUID,
        userMessageID: UUID,
        userMessageText: String,
        userMessageDate: Date
    ) async {
        do {
            let attachment = try await conversationStore.saveImageAttachment(
                prepared.jpegData,
                pixelWidth: prepared.pixelWidth,
                pixelHeight: prepared.pixelHeight
            )

            guard self.generationID == generationID,
                  !Task.isCancelled,
                  let index = conversation.firstIndex(where: { $0.id == userMessageID }) else {
                try? await conversationStore.deleteImageAttachments([attachment])
                return
            }

            conversation[index] = ConversationMessage(
                id: userMessageID,
                role: .user,
                text: userMessageText,
                createdAt: userMessageDate,
                imageAttachment: attachment
            )
            await persistCurrentConversation()
        } catch is CancellationError {
            return
        } catch {
            conversationNotice = "Image captured, but its chat preview could not be saved: \(error.localizedDescription)"
        }
    }

    private func finishGeneration(_ id: UUID) {
        guard generationID == id else { return }
        generationID = nil
        generationTask = nil
        isGenerating = false
        if !speechOutput.isSpeaking {
            endVoiceResponseBackgroundTask()
        }
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

/// Holds cumulative provider text until it is safe to expose. Structured reasoning is already
/// excluded by provider parsers; this additionally blocks models that place `<think>` blocks or
/// reasoning labels inside ordinary answer content.
@MainActor
private final class AssistantVisibleResponseBuffer {
    private var raw = ""
    private var emitted = ""

    func accept(_ delta: String) -> String {
        guard !delta.isEmpty else { return "" }
        raw.append(delta)
        let candidate = AssistantCompletionSanitizer.cleanForStreaming(raw)
        guard !candidate.isEmpty, candidate.hasPrefix(emitted) else { return "" }
        let next = String(candidate.dropFirst(emitted.count))
        emitted = candidate
        return next
    }

    func finish(_ finalRaw: String) throws -> (text: String, remainingDelta: String) {
        let inspected = AssistantCompletionSanitizer.inspect(finalRaw)
        guard !inspected.text.isEmpty else {
            switch inspected.rejectionReason {
            case .reasoningOnly, .unfinishedReasoning:
                throw AIConfigurationError.requestFailed("The AI didn’t produce a final answer. Please try again.")
            case .systemPromptEcho:
                throw AIConfigurationError.requestFailed("The AI returned an invalid response. Please try again.")
            case .empty, .none:
                throw AIConfigurationError.invalidResponse
            }
        }
        guard inspected.text.hasPrefix(emitted) else {
            // Never replace already displayed or spoken words with a contradictory sanitized form.
            throw AIConfigurationError.requestFailed("The AI returned an unstable response. Please try again.")
        }
        let remainder = String(inspected.text.dropFirst(emitted.count))
        emitted = inspected.text
        return (inspected.text, remainder)
    }
}

/// Converts provider deltas into short, natural TTS units. It waits for punctuation when possible,
/// but forces the first phrase at a bounded size so speech can begin before generation completes.
@MainActor
private final class AssistantStreamingSpeechBuffer {
    private var pending = ""
    private var emittedAny = false

    func accept(_ delta: String) -> [String] {
        pending.append(delta)
        return drain(final: false)
    }

    func finish() -> [String] {
        drain(final: true)
    }

    private func drain(final: Bool) -> [String] {
        var segments = [String]()
        while !pending.isEmpty {
            let characters = Array(pending)
            let forcedLimit = emittedAny ? 140 : 72
            let minimumForced = emittedAny ? 72 : 42
            let searchLimit = min(characters.count, forcedLimit)

            var cut: Int?
            for index in 0..<searchLimit where index >= 8 {
                let character = characters[index]
                if character == "." || character == "!" || character == "?" || character == "\n" {
                    cut = index + 1
                    break
                }
            }

            if cut == nil, characters.count >= forcedLimit {
                if forcedLimit > minimumForced {
                    for index in stride(from: forcedLimit - 1, through: minimumForced, by: -1)
                    where characters[index].isWhitespace || ",;:—–".contains(characters[index]) {
                        cut = characters[index].isWhitespace ? index : index + 1
                        break
                    }
                }
                cut = cut ?? forcedLimit
            }

            if cut == nil, final {
                cut = min(characters.count, 180)
            }
            guard let cut, cut > 0 else { break }

            let rawSegment = String(characters[..<cut])
            pending = String(characters[cut...]).trimmingCharacters(in: .whitespacesAndNewlines)
            let clean = Self.cleanForSpeech(rawSegment)
            if !clean.isEmpty {
                segments.append(clean)
                emittedAny = true
            }
        }
        return segments
    }

    private static func cleanForSpeech(_ raw: String) -> String {
        raw
            .replacingOccurrences(
                of: #"\[([^\]]+)\]\([^\)]+\)"#,
                with: "$1",
                options: .regularExpression
            )
            .replacingOccurrences(
                of: #"https?://\S+"#,
                with: "",
                options: .regularExpression
            )
            .replacingOccurrences(of: #"[*_`#>]"#, with: "", options: .regularExpression)
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
