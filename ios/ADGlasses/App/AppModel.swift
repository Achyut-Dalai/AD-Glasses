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
    @Published var conversationNotice: String?

    private let transcriber: any SpeechTranscribing

    init(transcriber: (any SpeechTranscribing)? = nil) {
        let selectedTranscriber = transcriber ?? AppleSpeechTranscriber.make()
        self.transcriber = selectedTranscriber
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
        guard !text.isEmpty else { return }

        conversation.append(
            ConversationMessage(role: .user, text: text)
        )
        chatDraft = ""
        conversationNotice = "Your message is saved in this conversation. Configure an AI service to receive a response."
    }

    func startNewConversation() {
        conversation.removeAll()
        chatDraft = ""
        conversationNotice = nil
        clearTranscript()
    }
}
