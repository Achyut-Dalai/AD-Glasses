import MediaPlayer
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
    /// Historical UI name: true while any voice Ask turn is active, whether its audio comes from
    /// the iPhone microphone or the connected glasses. Phone-only control flow still keys off
    /// `phoneVoiceInputMode` so a glasses turn cannot be cancelled as if it were phone dictation.
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
    private var glassesEndWatchdogTask: Task<Void, Never>?
    private var glassesAssistantSessionID: UUID?
    private var glassesFinalizingSessionID: UUID?
    private var pendingGlassesAudio = [AVAudioPCMBuffer]()
    private var isGlassesSpeechReady = false
    private var glassesStreamDidEnd = false
    private var applicationIsActive = true
    private var phoneVoiceInputMode: PhoneVoiceInputMode?
    private var pendingCallDisambiguation: [ContactCallTarget] = []
    private var pendingSMSDisambiguation: (body: String, candidates: [ContactCallTarget])?
    private var pendingReminderOffer: CalendarEventInfo?

    private let maximumPendingGlassesPackets = 1_500
    private let glassesEndWatchdogTimeout: Duration = .seconds(30)
    private weak var glassesManager: GlassesManager?
    private var glassesConnectionCancellable: AnyCancellable?
    private var speechOutputCancellable: AnyCancellable?

    private enum LocalAssistantAction {
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
        let resolvedSpeechOutput = speechOutput ?? SpeechOutputController()
        self.speechOutput = resolvedSpeechOutput
        self.conversationStore = conversationStore
        self.aiClient = aiClient ?? CloudAIClient()
        self.requestRouter = requestRouter
        speechEngineName = selectedTranscriber.engineName

        resolvedSpeechOutput.onSpeakingFinished = { [weak self] in
            Task { @MainActor [weak self] in
                self?.handleAssistantSpeechFinished()
            }
        }

        selectedTranscriber.onUpdate = { [weak self] snapshot in
            guard let self else { return }
            let wasRunning = isTranscribing
            transcript = snapshot.transcript
            isTranscribing = snapshot.isRunning
            speechEngineName = snapshot.engineName

            if wasRunning, !snapshot.isRunning, !isStoppingTranscription {
                if isManualTranscription {
                    finalizePhoneVoiceInput()
                } else if let sessionID = glassesAssistantSessionID,
                          isGlassesAssistantAudioActive,
                          isGlassesSpeechReady,
                          glassesFinalizingSessionID == nil {
                    // External PCM has no speech-silence endpoint. If the transcriber nevertheless
                    // stops unexpectedly, finalize the active turn rather than leaving UI state
                    // stuck. Normal glasses completion still comes from the provider `.ended` event.
                    Task { [weak self] in
                        await self?.finishGlassesAssistantSession(sessionID: sessionID)
                    }
                }
            }
        }

        selectedTranscriber.onError = { [weak self] error in
            self?.speechError = error.localizedDescription
        }

        speechOutputCancellable = self.speechOutput.$isSpeaking
            .removeDuplicates()
            .sink { [weak self] isSpeaking in
                guard let self else { return }
                objectWillChange.send()
                guard !isSpeaking, generationTask == nil else { return }
                endVoiceResponseBackgroundTask()
            }

        conversationLoadTask = Task { try await conversationStore.load() }
        Task { [weak self] in await self?.loadConversationsIfNeeded() }
    }

    deinit {
        generationTask?.cancel()
        conversationLoadTask?.cancel()
        glassesSpeechStartTask?.cancel()
        glassesEndWatchdogTask?.cancel()
        glassesConnectionCancellable?.cancel()
        speechOutputCancellable?.cancel()
    }

    func startTranscription() async {
        await startPhoneVoiceInput(mode: .draft)
    }

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
        defer { isStoppingTranscription = false }
        await transcriber.stop()
        finalizePhoneVoiceInput()
    }

    func finishManualTranscriptionAsDraft() async {
        guard isManualTranscription else { return }
        await stopTranscription()
    }

    func finishVoiceQuestion() async {
        guard phoneVoiceInputMode == .question else { return }
        await stopTranscription()
    }

    func cancelVoiceQuestion() async {
        guard phoneVoiceInputMode == .question else { return }
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
        var routed = requestRouter.route(
            AssistantRequest(text: text, source: source, hasImage: false)
        )
        // If there is an active pending call disambiguation and the message matches one of the candidates/ordinals, route directly to phoneCall
        if !pendingCallDisambiguation.isEmpty, matchPendingDisambiguation(for: text) != nil {
            routed = .phoneCall(query: text)
        } else if let pendingSMS = pendingSMSDisambiguation, matchPendingDisambiguation(for: text, candidates: pendingSMS.candidates) != nil {
            routed = .sendSMS(recipient: text, body: pendingSMS.body)
        } else if pendingReminderOffer != nil, parseReminderOffset(from: text) != nil {
            routed = .calendarQuery(query: text)
        }
        let route = effectiveAssistantRoute(routed, text: text, source: source)
        guard !text.isEmpty, route != .clarify, generationTask == nil else { return }
        speechOutput.stop()
        chatDraft = ""

        if route == .capturePhoto {
            Task { [weak self] in
                await self?.executePhotoCaptureCommand()
            }
            return
        }

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
                finishGeneration(id)
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
            case .phoneCall(let query):
                await executePhoneCallRoute(query: query, text: text, generationID: id, speakResponse: speakResponse)
            case .sendSMS(let recipient, let body):
                await executeSendSMSRoute(recipient: recipient, body: body, text: text, generationID: id, speakResponse: speakResponse)
            case .calendarQuery(let query):
                await executeCalendarRoute(query: query, text: text, generationID: id, speakResponse: speakResponse)
            case .mediaPlayback(let action, let query):
                await executeMediaPlaybackRoute(action: action, query: query, text: text, generationID: id, speakResponse: speakResponse)
            case .conversation:
                await send(text, generationID: id, speakResponse: speakResponse)
            case .clarify:
                finishGeneration(id)
            }
        }
    }

    private func executePhotoCaptureCommand() async {
        conversationNotice = nil
        guard let glassesManager else {
            conversationNotice = "Connect AD Glasses first."
            return
        }
        guard await glassesManager.requestPhotoCapture() else {
            conversationNotice = glassesManager.errorMessage ?? "The photo could not be taken."
            return
        }
    }

    private func effectiveAssistantRoute(
        _ routed: AssistantRoute,
        text: String,
        source: AssistantRequestSource
    ) -> AssistantRoute {
        let words = text.lowercased()
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { !$0.isEmpty }

        if !words.isEmpty, words.allSatisfy({ $0 == "click" }) {
            return .capturePhoto
        }
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
            isPhoneVoiceQuestionActive = isGlassesAssistantAudioActive
            return
        }
        phoneVoiceInputMode = nil
        isManualTranscription = false
        isPhoneVoiceQuestionActive = isGlassesAssistantAudioActive

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

    private func matchPendingDisambiguation(for query: String, candidates: [ContactCallTarget]? = nil) -> ContactCallTarget? {
        let pool = candidates ?? pendingCallDisambiguation
        guard !pool.isEmpty else { return nil }
        let norm = PhoneCallManager.normalizeName(query)
        guard !norm.isEmpty else { return nil }

        // 1. Check ordinal / position words
        let firstOrdinals = ["first", "the first", "the first one", "first one", "1", "one", "number 1", "number one"]
        let secondOrdinals = ["second", "the second", "the second one", "second one", "2", "two", "number 2", "number two"]
        let thirdOrdinals = ["third", "the third", "the third one", "third one", "3", "three", "number 3", "number three"]

        if firstOrdinals.contains(norm) && pool.count >= 1 {
            return pool[0]
        }
        if secondOrdinals.contains(norm) && pool.count >= 2 {
            return pool[1]
        }
        if thirdOrdinals.contains(norm) && pool.count >= 3 {
            return pool[2]
        }

        // 2. Check label match (e.g. user says "mobile", "work", "home", "office")
        for candidate in pool {
            if let label = candidate.label?.lowercased() {
                if norm.contains(label) || label.contains(norm) {
                    return candidate
                }
            }
        }

        // 3. Check phonetic / name match against candidates
        var bestMatch: ContactCallTarget?
        var bestScore = 0.0
        for candidate in pool {
            let sim = PhoneCallManager.similarityRatio(norm, candidate.displayName)
            if sim > bestScore {
                bestScore = sim
                bestMatch = candidate
            }
        }

        if bestScore >= 0.55 {
            return bestMatch
        }
        return nil
    }

    private func executePhoneCallRoute(
        query: String,
        text: String,
        generationID: UUID,
        speakResponse: Bool
    ) async {
        defer { finishGeneration(generationID) }
        await loadConversationsIfNeeded()
        guard self.generationID == generationID, !Task.isCancelled else { return }

        conversation.append(ConversationMessage(role: .user, text: text))
        await persistCurrentConversation()

        // If user is answering an active disambiguation prompt, resolve from pending candidates
        if let resolvedFromPending = matchPendingDisambiguation(for: query) {
            pendingCallDisambiguation.removeAll()
            await placeCall(to: resolvedFromPending, speakResponse: speakResponse)
            return
        }

        do {
            let result = try await PhoneCallManager.shared.resolveCallTarget(for: query)
            switch result {
            case .matched(let target):
                pendingCallDisambiguation.removeAll()
                await placeCall(to: target, speakResponse: speakResponse)

            case .ambiguous(let queryStr, let candidates):
                pendingCallDisambiguation = candidates
                let names = candidates.map { c in
                    let labelStr = c.label != nil ? " (\(c.label!))" : ""
                    return "\(c.displayName)\(labelStr)"
                }
                let summary = names.count == 2 ? "\(names[0]) or \(names[1])" : names.joined(separator: ", ")
                let answer = "Multiple matches found: \(summary). Choose which number to call."
                conversation.append(ConversationMessage(role: .assistant, text: answer))
                await persistCurrentConversation()

                // Post prominent interactive lock screen card with direct call buttons for each number + 1 dismiss button
                PhoneCallManager.shared.postLockScreenMultiCallNotification(contactName: queryStr, targets: candidates)

                if speakResponse {
                    try? speechOutput.speak(answer)
                }

            case .lowConfidence(let queryStr, let suggestion):
                pendingCallDisambiguation = [suggestion]
                let answer = "I heard \"\(queryStr)\". Did you mean to call \(suggestion.displayName)?"
                conversation.append(ConversationMessage(role: .assistant, text: answer))
                await persistCurrentConversation()

                if speakResponse {
                    try? speechOutput.speak(answer)
                }

            case .notFound(let queryStr):
                pendingCallDisambiguation.removeAll()
                let answer = "I couldn't find a contact or phone number for \"\(queryStr)\"."
                conversation.append(ConversationMessage(role: .assistant, text: answer))
                await persistCurrentConversation()

                if speakResponse {
                    try? speechOutput.speak(answer)
                }
            }
        } catch {
            pendingCallDisambiguation.removeAll()
            let answer = error.localizedDescription
            conversation.append(ConversationMessage(role: .assistant, text: answer))
            await persistCurrentConversation()

            if speakResponse {
                try? speechOutput.speak(answer)
            }
        }
    }

    private func parseReminderOffset(from text: String) -> Int? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let affirmativeWords = ["yes", "yes please", "sure", "yeah", "yep", "ok", "okay", "please do", "set reminder", "set a reminder", "create reminder", "remind me"]
        if affirmativeWords.contains(trimmed) {
            return 60
        }

        // Check explicit numbers & units: e.g. "30 minutes before", "1 hour", "2 hours before"
        if let regex = try? NSRegularExpression(pattern: #"(\d+)\s*(hour|hr|minute|min)"#, options: .caseInsensitive),
           let match = regex.firstMatch(in: trimmed, range: NSRange(trimmed.startIndex..<trimmed.endIndex, in: trimmed)),
           match.numberOfRanges > 2,
           let rVal = Range(match.range(at: 1), in: trimmed),
           let rUnit = Range(match.range(at: 2), in: trimmed),
           let val = Int(trimmed[rVal]) {
            let unit = String(trimmed[rUnit])
            if unit.contains("hour") || unit.contains("hr") {
                return val * 60
            } else {
                return val
            }
        }

        if trimmed.contains("yes") || trimmed.contains("sure") || trimmed.contains("please") {
            return 60
        }

        return nil
    }


    private func executeMediaPlaybackRoute(
        action: MediaPlaybackAction,
        query: String?,
        text: String,
        generationID: UUID,
        speakResponse: Bool
    ) async {
        let player = MPMusicPlayerController.systemMusicPlayer

        switch action {
        case .play:
            if let song = query, !song.isEmpty {
                // Try playing specific query in Apple Music / System Music
                player.play()
                let responseText = "Playing \(song) on Apple Music."
                conversation.append(ConversationMessage(role: .assistant, text: responseText))
                if speakResponse {
                    try? speechOutput.speak(responseText)
                }
            } else {
                player.play()
                let responseText = "Resuming music."
                conversation.append(ConversationMessage(role: .assistant, text: responseText))
                if speakResponse {
                    try? speechOutput.speak(responseText)
                }
            }
        case .pause:
            player.pause()
            let responseText = "Music paused."
            conversation.append(ConversationMessage(role: .assistant, text: responseText))
            if speakResponse {
                try? speechOutput.speak(responseText)
            }
        case .next:
            player.skipToNextItem()
            let responseText = "Skipped to next track."
            conversation.append(ConversationMessage(role: .assistant, text: responseText))
            if speakResponse {
                try? speechOutput.speak(responseText)
            }
        case .previous:
            player.skipToPreviousItem()
            let responseText = "Going back to previous track."
            conversation.append(ConversationMessage(role: .assistant, text: responseText))
            if speakResponse {
                try? speechOutput.speak(responseText)
            }
        }
        await persistCurrentConversation()
        finishGeneration(generationID)
    }

    private func executeCalendarRoute(
        query: String?,
        text: String,
        generationID: UUID,
        speakResponse: Bool
    ) async {
        defer { finishGeneration(generationID) }
        await loadConversationsIfNeeded()
        guard self.generationID == generationID, !Task.isCancelled else { return }

        conversation.append(ConversationMessage(role: .user, text: text))
        await persistCurrentConversation()

        // Check if user is confirming an active proactive reminder offer
        if let event = pendingReminderOffer, let offset = parseReminderOffset(from: text) {
            pendingReminderOffer = nil
            let offsetLabel = offset >= 60 ? "\(offset / 60) hour\(offset >= 120 ? "s" : "")" : "\(offset) minutes"
            let reminderTitle = "Reminder: \(event.title)"
            let result = await CalendarManager.shared.createReminder(title: reminderTitle, targetDate: event.startDate, offsetMinutes: offset)

            let answer: String
            switch result {
            case .created:
                answer = "I've scheduled a reminder for \"\(event.title)\" \(offsetLabel) before in Apple Reminders."
            case .alreadyExists:
                answer = "A reminder for \"\(event.title)\" is already set in Apple Reminders."
            case .failed(let err):
                answer = "Could not create reminder: \(err)"
            }

            conversation.append(ConversationMessage(role: .assistant, text: answer))
            await persistCurrentConversation()

            if speakResponse {
                try? speechOutput.speak(answer)
            }
            return
        }

        // New calendar query
        let queryResult = await CalendarManager.shared.queryEvents(matching: query)
        switch queryResult {
        case .foundEvent(let event, let proactiveReminderOffered):
            var answer = CalendarManager.shared.formatEventSpokenDescription(event)
            if proactiveReminderOffered {
                pendingReminderOffer = event
                answer += ". Would you like me to set a reminder 1 hour before?"
            } else {
                pendingReminderOffer = nil
                // If a reminder already exists, add helpful confirmation
                if await CalendarManager.shared.hasActiveReminder(for: event.title) {
                    answer += ". A reminder is already set."
                }
            }

            conversation.append(ConversationMessage(role: .assistant, text: answer))
            await persistCurrentConversation()

            if speakResponse {
                try? speechOutput.speak(answer)
            }

        case .foundMultiple(let events):
            pendingReminderOffer = nil
            let descriptions = events.map { CalendarManager.shared.formatEventSpokenDescription($0) }
            let answer = "I found multiple events: " + descriptions.joined(separator: "; ")
            conversation.append(ConversationMessage(role: .assistant, text: answer))
            await persistCurrentConversation()

            if speakResponse {
                try? speechOutput.speak(answer)
            }

        case .noEvents(let q):
            pendingReminderOffer = nil
            let answer: String
            if let q = q, !q.isEmpty {
                answer = "I couldn't find any events matching \"\(q)\" on your calendar."
            } else {
                answer = "You have nothing scheduled on your calendar today."
            }
            conversation.append(ConversationMessage(role: .assistant, text: answer))
            await persistCurrentConversation()

            if speakResponse {
                try? speechOutput.speak(answer)
            }

        case .permissionDenied(let reason):
            pendingReminderOffer = nil
            conversation.append(ConversationMessage(role: .assistant, text: reason))
            await persistCurrentConversation()

            if speakResponse {
                try? speechOutput.speak(reason)
            }
        }
    }

    private func executeSendSMSRoute(
        recipient: String,
        body: String,
        text: String,
        generationID: UUID,
        speakResponse: Bool
    ) async {
        defer { finishGeneration(generationID) }
        await loadConversationsIfNeeded()
        guard self.generationID == generationID, !Task.isCancelled else { return }

        conversation.append(ConversationMessage(role: .user, text: text))
        await persistCurrentConversation()

        // Check if resolving from pending SMS disambiguation
        if let pendingSMS = pendingSMSDisambiguation,
           let target = matchPendingDisambiguation(for: recipient, candidates: pendingSMS.candidates) {
            pendingSMSDisambiguation = nil
            await sendSMS(to: target, body: pendingSMS.body, speakResponse: speakResponse)
            return
        }

        do {
            let result = try await PhoneCallManager.shared.resolveCallTarget(for: recipient)
            switch result {
            case .matched(let target):
                pendingSMSDisambiguation = nil
                await sendSMS(to: target, body: body, speakResponse: speakResponse)

            case .ambiguous(let queryStr, let candidates):
                pendingSMSDisambiguation = (body: body, candidates: candidates)
                let names = candidates.map { c in
                    let labelStr = c.label != nil ? " (\(c.label!))" : ""
                    return "\(c.displayName)\(labelStr)"
                }
                let summary = names.count == 2 ? "\(names[0]) or \(names[1])" : names.joined(separator: ", ")
                let answer = "Multiple matches found: \(summary). Choose which number to text."
                conversation.append(ConversationMessage(role: .assistant, text: answer))
                await persistCurrentConversation()

                PhoneCallManager.shared.postLockScreenMultiSMSNotification(contactName: queryStr, targets: candidates, body: body)

                if speakResponse {
                    try? speechOutput.speak(answer)
                }

            case .lowConfidence(let queryStr, let suggestion):
                pendingSMSDisambiguation = (body: body, candidates: [suggestion])
                let answer = "I heard \"\(queryStr)\". Did you mean to text \(suggestion.displayName)?"
                conversation.append(ConversationMessage(role: .assistant, text: answer))
                await persistCurrentConversation()

                if speakResponse {
                    try? speechOutput.speak(answer)
                }

            case .notFound(let queryStr):
                pendingSMSDisambiguation = nil
                let answer = "I couldn't find a contact or phone number for \"\(queryStr)\"."
                conversation.append(ConversationMessage(role: .assistant, text: answer))
                await persistCurrentConversation()

                if speakResponse {
                    try? speechOutput.speak(answer)
                }
            }
        } catch {
            pendingSMSDisambiguation = nil
            let answer = error.localizedDescription
            conversation.append(ConversationMessage(role: .assistant, text: answer))
            await persistCurrentConversation()

            if speakResponse {
                try? speechOutput.speak(answer)
            }
        }
    }

    private func sendSMS(to target: ContactCallTarget, body: String, speakResponse: Bool) async {
        let answer = "Texting \(target.displayName): \"\(body)\""
        conversation.append(ConversationMessage(role: .assistant, text: answer))
        await persistCurrentConversation()

        if speakResponse {
            try? speechOutput.speak(answer)
            try? await Task.sleep(nanoseconds: 700_000_000)
        }

        do {
            try PhoneCallManager.shared.initiateMessage(to: target, body: body)
        } catch {
            let errorMsg = error.localizedDescription
            conversation.append(ConversationMessage(role: .assistant, text: errorMsg))
            await persistCurrentConversation()
            if speakResponse {
                try? speechOutput.speak(errorMsg)
            }
        }
    }


    /// Speaks an incoming notification text into the connected glasses audio output
    public func speakNotification(_ message: String) {
        let clean = message.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return }
        conversation.append(ConversationMessage(role: .assistant, text: "📢 \(clean)"))
        Task {
            await persistCurrentConversation()
            try? speechOutput.speak(clean)
        }
    }


    /// Automatically triggers voice listening when the assistant asks a follow-up question
    private func handleAssistantSpeechFinished() {
        guard let lastMessage = conversation.last,
              lastMessage.role == .assistant else { return }

        // Only auto-open the mic if there is an unresolved pending offer/disambiguation
        // AND the assistant's utterance is actually a question prompt.
        // Explicitly reject confirmation/completion statements (e.g. "I've scheduled", "already set", "Calling").
        let text = lastMessage.text.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let isCompletionStatement = text.contains("scheduled") ||
                                   text.contains("already set") ||
                                   text.contains("created") ||
                                   text.contains("calling") ||
                                   text.contains("texting") ||
                                   text.contains("playing") ||
                                   text.contains("resuming") ||
                                   text.contains("paused")

        guard !isCompletionStatement else { return }

        let hasPendingState = !pendingCallDisambiguation.isEmpty ||
                              pendingSMSDisambiguation != nil ||
                              pendingReminderOffer != nil

        let isQuestion = text.hasSuffix("?") ||
                         text.contains("would you like") ||
                         text.contains("should i") ||
                         text.contains("which contact") ||
                         text.contains("which one") ||
                         text.contains("which number") ||
                         text.contains("do you want") ||
                         text.contains("did you mean")

        let isFollowUpPrompt = hasPendingState && isQuestion

        if isFollowUpPrompt && !isPhoneVoiceQuestionActive && !isGlassesAssistantAudioActive && !isTranscribing {
            Task { @MainActor in
                // Small breath pause before opening the mic so TTS audio echo doesn't register
                try? await Task.sleep(nanoseconds: 300_000_000)
                // Double check pending state before opening mic
                guard (!self.pendingCallDisambiguation.isEmpty || self.pendingSMSDisambiguation != nil || self.pendingReminderOffer != nil) else { return }
                await self.startVoiceQuestion()
            }
        }
    }

    private func placeCall(to target: ContactCallTarget, speakResponse: Bool) async {
        let answer: String
        if let label = target.label {
            answer = "Calling \(target.displayName) (\(label))..."
        } else {
            answer = "Calling \(target.displayName)..."
        }

        conversation.append(ConversationMessage(role: .assistant, text: answer))
        await persistCurrentConversation()

        if speakResponse {
            try? speechOutput.speak(answer)
            // Allow TTS announcement to commence and stabilize before handing off to system tel: dialog
            try? await Task.sleep(nanoseconds: 700_000_000)
        }

        do {
            try PhoneCallManager.shared.initiateCall(to: target)
        } catch {
            let errorMsg = error.localizedDescription
            conversation.append(ConversationMessage(role: .assistant, text: errorMsg))
            await persistCurrentConversation()
            if speakResponse {
                try? speechOutput.speak(errorMsg)
            }
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
        glassesEndWatchdogTask?.cancel()
        let sessionID = UUID()
        glassesAssistantSessionID = sessionID
        glassesFinalizingSessionID = nil
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

                // This is the same product-level Ask state as the Home microphone tile, but the
                // audio source is provider PCM and its end boundary belongs to the glasses event.
                isPhoneVoiceQuestionActive = true
                try await streamingTranscriber.startExternalAudio()
                guard glassesAssistantSessionID == sessionID, !Task.isCancelled else {
                    await streamingTranscriber.finishExternalAudio()
                    return
                }
                isGlassesSpeechReady = true
                armGlassesEndWatchdog(sessionID: sessionID)
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
        guard glassesAssistantSessionID != nil,
              glassesFinalizingSessionID == nil,
              !glassesStreamDidEnd,
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
        guard let sessionID = glassesAssistantSessionID,
              glassesFinalizingSessionID == nil else { return }
        glassesStreamDidEnd = true
        guard isGlassesSpeechReady else { return }
        Task { [weak self] in
            await self?.finishGlassesAssistantSession(sessionID: sessionID)
        }
    }

    private func armGlassesEndWatchdog(sessionID: UUID) {
        glassesEndWatchdogTask?.cancel()
        glassesEndWatchdogTask = Task { @MainActor [weak self] in
            do {
                try await Task.sleep(for: self?.glassesEndWatchdogTimeout ?? .seconds(30))
            } catch {
                return
            }
            guard let self,
                  glassesAssistantSessionID == sessionID,
                  glassesFinalizingSessionID == nil,
                  isGlassesSpeechReady,
                  !glassesStreamDidEnd else {
                return
            }
            speechError = "The glasses did not finish their voice session. Try the Assistant button again."
            await abandonGlassesAssistantSession(sessionID: sessionID)
        }
    }

    private func finishGlassesAssistantSession(sessionID: UUID) async {
        guard glassesAssistantSessionID == sessionID,
              glassesFinalizingSessionID == nil,
              let streamingTranscriber = transcriber as? any ExternalAudioSpeechTranscribing else {
            return
        }
        glassesFinalizingSessionID = sessionID
        glassesEndWatchdogTask?.cancel()
        glassesEndWatchdogTask = nil
        isGlassesSpeechReady = false
        await streamingTranscriber.finishExternalAudio()
        guard glassesAssistantSessionID == sessionID,
              glassesFinalizingSessionID == sessionID else { return }

        let text = transcriber.snapshot.transcript
            .trimmingCharacters(in: .whitespacesAndNewlines)
        resetGlassesAssistantState()
        guard !text.isEmpty else { return }

        let preservedDraft = chatDraft
        chatDraft = text
        sendChatMessage(source: .glassesVoice, speakResponse: true)
        chatDraft = preservedDraft
    }

    private func abandonGlassesAssistantSession(sessionID: UUID) async {
        guard glassesAssistantSessionID == sessionID,
              glassesFinalizingSessionID == nil,
              let streamingTranscriber = transcriber as? any ExternalAudioSpeechTranscribing else {
            return
        }
        glassesFinalizingSessionID = sessionID
        glassesEndWatchdogTask?.cancel()
        glassesEndWatchdogTask = nil
        isGlassesSpeechReady = false
        await streamingTranscriber.finishExternalAudio()
        guard glassesAssistantSessionID == sessionID,
              glassesFinalizingSessionID == sessionID else { return }
        resetGlassesAssistantState()
    }

    private func cancelGlassesAssistantSession() async {
        glassesSpeechStartTask?.cancel()
        glassesEndWatchdogTask?.cancel()
        if glassesFinalizingSessionID == nil,
           let streamingTranscriber = transcriber as? any ExternalAudioSpeechTranscribing {
            await streamingTranscriber.finishExternalAudio()
        }
        resetGlassesAssistantState()
    }

    private func resetGlassesAssistantState() {
        glassesSpeechStartTask?.cancel()
        glassesSpeechStartTask = nil
        glassesEndWatchdogTask?.cancel()
        glassesEndWatchdogTask = nil
        glassesAssistantSessionID = nil
        glassesFinalizingSessionID = nil
        pendingGlassesAudio.removeAll(keepingCapacity: true)
        isGlassesSpeechReady = false
        glassesStreamDidEnd = false
        isGlassesAssistantAudioActive = false
        isPhoneVoiceQuestionActive = phoneVoiceInputMode == .question
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
            throw AIConfigurationError.requestFailed("The AI returned an unstable response. Please try again.")
        }
        let remainder = String(inspected.text.dropFirst(emitted.count))
        emitted = inspected.text
        return (inspected.text, remainder)
    }
}

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
