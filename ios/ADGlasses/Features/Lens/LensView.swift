import Foundation
import PhotosUI
import SwiftUI
import Translation

@MainActor
final class LensSessionController: ObservableObject {
    enum State: Equatable {
        case idle
        case preparing
        case ready
        case recognizingText
        case failed(String)
    }

    @Published private(set) var state: State = .idle
    @Published private(set) var image: LensPreparedImage?
    @Published private(set) var recognizedText = ""

    private let processor: LensImageProcessor
    private var task: Task<Void, Never>?

    init(processor: LensImageProcessor = LensImageProcessor()) {
        self.processor = processor
    }

    deinit { task?.cancel() }

    func load(_ data: Data) {
        task?.cancel()
        state = .preparing
        recognizedText = ""
        image = nil
        task = Task { [weak self, processor] in
            do {
                let prepared = try await processor.prepare(data)
                try Task.checkCancellation()
                self?.image = prepared
                self?.state = .ready
            } catch is CancellationError {
                return
            } catch {
                self?.state = .failed(error.localizedDescription)
            }
        }
    }

    func extractText() {
        guard let image else { return }
        task?.cancel()
        state = .recognizingText
        task = Task { [weak self, processor] in
            do {
                let text = try await processor.recognizeText(in: image)
                try Task.checkCancellation()
                self?.recognizedText = text
                self?.state = .ready
            } catch is CancellationError {
                return
            } catch {
                self?.state = .failed(error.localizedDescription)
            }
        }
    }
}

/// Provider-aware image understanding used by Lens visual questions.
/// Images are sent only for an explicit Lens/visual request and are never persisted by this client.
struct ADVisualAIClient: Sendable {
    private let session: URLSession
    private static let maximumJPEGBytes = 8 * 1_024 * 1_024
    private static let outputTokenLimit = 700

    init(session: URLSession = .shared) {
        self.session = session
    }

    func answer(
        question: String,
        imageJPEGData: Data,
        profile: AIProfile,
        credential: String
    ) async throws -> String {
        let prompt = question.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !prompt.isEmpty else {
            throw AIConfigurationError.requestFailed("Ask Lens a question about the image first.")
        }
        guard !imageJPEGData.isEmpty, imageJPEGData.count <= Self.maximumJPEGBytes else {
            throw AIConfigurationError.requestFailed("The prepared Lens image is too large to send safely.")
        }

        do {
            switch profile.provider {
            case .openAI:
                return try await openAI(prompt: prompt, imageJPEGData: imageJPEGData, profile: profile, credential: credential)
            case .google:
                return try await gemini(prompt: prompt, imageJPEGData: imageJPEGData, profile: profile, credential: credential)
            case .deepSeek, .openRouter, .groq, .custom:
                return try await compatible(prompt: prompt, imageJPEGData: imageJPEGData, profile: profile, credential: credential)
            }
        } catch let error as AIConfigurationError {
            if case .requestFailed(let message) = error, Self.looksLikeUnsupportedImageError(message) {
                throw AIConfigurationError.requestFailed(
                    "The selected model \(profile.model) does not appear to support image input. Choose a vision-capable model in Cloud AI settings."
                )
            }
            throw error
        }
    }

    private func openAI(prompt: String, imageJPEGData: Data, profile: AIProfile, credential: String) async throws -> String {
        let url = try endpoint(base: profile.baseURL, suffix: "/responses")
        let payload: [String: Any] = [
            "model": profile.model,
            "instructions": Self.visualInstruction,
            "input": [[
                "role": "user",
                "content": [
                    ["type": "input_text", "text": prompt],
                    ["type": "input_image", "image_url": dataURL(for: imageJPEGData), "detail": "auto"]
                ]
            ]],
            "max_output_tokens": Self.outputTokenLimit
        ]
        let root = try await post(url: url, bearer: credential, headers: [:], payload: payload, label: "OpenAI visual understanding")
        if let text = (root["output_text"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines), !text.isEmpty {
            return try visibleAnswer(text)
        }
        if let output = root["output"] as? [[String: Any]] {
            let text = output.compactMap { $0["content"] as? [[String: Any]] }
                .flatMap { $0 }
                .filter { ($0["type"] as? String) == "output_text" }
                .compactMap { $0["text"] as? String }
                .joined()
                .trimmingCharacters(in: .whitespacesAndNewlines)
            if !text.isEmpty { return try visibleAnswer(text) }
        }
        throw AIConfigurationError.invalidResponse
    }

    private func gemini(prompt: String, imageJPEGData: Data, profile: AIProfile, credential: String) async throws -> String {
        let model = AIProfileStore.normalizedModel(profile.model, provider: .google)
        let url = try endpoint(base: profile.baseURL, suffix: "/models/\(model):generateContent")
        let payload: [String: Any] = [
            "systemInstruction": ["parts": [["text": Self.visualInstruction]]],
            "contents": [[
                "role": "user",
                "parts": [
                    ["text": prompt],
                    ["inline_data": ["mime_type": "image/jpeg", "data": imageJPEGData.base64EncodedString()]]
                ]
            ]],
            "generationConfig": ["maxOutputTokens": Self.outputTokenLimit]
        ]
        let root = try await post(url: url, bearer: nil, headers: ["x-goog-api-key": credential], payload: payload, label: "Google Gemini visual understanding")
        guard let candidates = root["candidates"] as? [[String: Any]],
              let content = candidates.first?["content"] as? [String: Any],
              let parts = content["parts"] as? [[String: Any]] else {
            throw AIConfigurationError.invalidResponse
        }
        let text = parts.compactMap { $0["text"] as? String }.joined().trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { throw AIConfigurationError.invalidResponse }
        return try visibleAnswer(text)
    }

    private func compatible(prompt: String, imageJPEGData: Data, profile: AIProfile, credential: String) async throws -> String {
        let url = try endpoint(base: profile.baseURL, suffix: "/chat/completions")
        var payload: [String: Any] = [
            "model": profile.model,
            "messages": [
                ["role": "system", "content": Self.visualInstruction],
                ["role": "user", "content": [
                    ["type": "text", "text": prompt],
                    ["type": "image_url", "image_url": ["url": dataURL(for: imageJPEGData), "detail": "auto"]]
                ]]
            ]
        ]
        CloudModelPolicy.applyOpenAICompatibleTuning(
            to: &payload,
            profile: profile,
            mode: .conciseConversation,
            outputTokenLimit: Self.outputTokenLimit
        )

        let root = try await post(url: url, bearer: credential, headers: [:], payload: payload, label: "\(profile.provider.displayName) visual understanding")
        guard let choices = root["choices"] as? [[String: Any]],
              let message = choices.first?["message"] as? [String: Any] else {
            throw AIConfigurationError.invalidResponse
        }
        if let text = (message["content"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines), !text.isEmpty {
            return try visibleAnswer(text)
        }
        if let parts = message["content"] as? [[String: Any]] {
            let text = parts.compactMap { $0["text"] as? String }.joined().trimmingCharacters(in: .whitespacesAndNewlines)
            if !text.isEmpty { return try visibleAnswer(text) }
        }
        throw AIConfigurationError.invalidResponse
    }

    private func visibleAnswer(_ raw: String) throws -> String {
        let inspected = AssistantCompletionSanitizer.inspect(raw)
        guard !inspected.text.isEmpty else {
            if inspected.rejectionReason == .reasoningOnly || inspected.rejectionReason == .unfinishedReasoning {
                throw AIConfigurationError.requestFailed("The AI didn’t produce a final answer. Please try again.")
            }
            throw AIConfigurationError.invalidResponse
        }
        return inspected.text
    }

    private func endpoint(base: String, suffix: String) throws -> URL {
        var value = base.trimmingCharacters(in: .whitespacesAndNewlines)
        while value.hasSuffix("/") { value.removeLast() }
        guard let components = URLComponents(string: value),
              components.scheme?.lowercased() == "https",
              components.host?.isEmpty == false,
              components.user == nil, components.password == nil,
              let url = URL(string: value + suffix) else {
            throw AIConfigurationError.invalidEndpoint
        }
        return url
    }

    private func post(url: URL, bearer: String?, headers: [String: String], payload: [String: Any], label: String) async throws -> [String: Any] {
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 60
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let bearer { request.setValue("Bearer \(bearer)", forHTTPHeaderField: "Authorization") }
        headers.forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }
        request.httpBody = try JSONSerialization.data(withJSONObject: payload)

        do {
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else { throw AIConfigurationError.invalidResponse }
            guard data.count <= 2_000_000 else {
                throw AIConfigurationError.requestFailed("\(label) returned an unexpectedly large response.")
            }
            let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] ?? [:]
            guard 200..<300 ~= http.statusCode else {
                let message = ((root["error"] as? [String: Any])?["message"] as? String)
                    ?? (root["message"] as? String)
                    ?? "\(label) returned HTTP \(http.statusCode)."
                throw AIConfigurationError.requestFailed(message)
            }
            return root
        } catch let error as AIConfigurationError { throw error }
        catch is CancellationError { throw CancellationError() }
        catch { throw AIConfigurationError.requestFailed("Could not reach \(label): \(error.localizedDescription)") }
    }

    private func dataURL(for jpegData: Data) -> String {
        "data:image/jpeg;base64,\(jpegData.base64EncodedString())"
    }

    private static var visualInstruction: String {
        "You are AD, the visual companion for AD Glasses. Answer only from the supplied image and the user's question. Be concise and practical. If something is uncertain, say so. Never claim to recognize a person's identity from appearance alone. Do not invent text, objects, hazards, locations, or events that are not visible in the image."
    }

    private static func looksLikeUnsupportedImageError(_ message: String) -> Bool {
        let value = message.lowercased()
        return value.contains("image input") || value.contains("vision") || value.contains("multimodal") ||
            value.contains("image_url") || value.contains("does not support image") ||
            value.contains("unsupported modality") || value.contains("input modalities") ||
            value.contains("content must be a string") || value.contains("expected a string") ||
            value.contains("content: string") || value.contains("messages 1 content") ||
            value.contains("messages.1.content") || value.contains("messages[1].content") ||
            value.contains("string or null expected")
    }
}

struct LensView: View {
    @EnvironmentObject private var app: AppModel
    @EnvironmentObject private var glasses: GlassesManager
    @Environment(\.dismiss) private var dismiss
    @StateObject private var lens = LensSessionController()

    @State private var selectedItem: PhotosPickerItem?
    @State private var question = ""
    @State private var visualAnswer = ""
    @State private var errorMessage: String?
    @State private var isCapturingFromGlasses = false
    @State private var isAskingVisualAI = false
    @State private var lastLoadedVisualCaptureID: UUID?
    @State private var preservedAssistantDraftForVoiceQuestion: String?

    private var ownsLensVoiceQuestion: Bool {
        preservedAssistantDraftForVoiceQuestion != nil
    }

    private var isRecordingLensVoiceQuestion: Bool {
        ownsLensVoiceQuestion && app.isManualTranscription && app.isTranscribing
    }

    var body: some View {
        let photoPickerTitle = lens.image == nil ? "Choose a photo" : "Choose another photo"

        NavigationStack {
            List {
                Section { imageArea }
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)

                Section("Image") {
                    if glasses.connectionState.isConnected, glasses.supports(.camera) {
                        Button { Task { await captureFromGlasses() } } label: {
                            if isCapturingFromGlasses {
                                HStack(spacing: 10) { ProgressView(); Text("Capturing with AD Glasses…") }
                            } else {
                                Label("Capture with AD Glasses", systemImage: "camera.viewfinder")
                            }
                        }
                        .disabled(isCapturingFromGlasses || isAskingVisualAI)
                    }

                    PhotosPicker(selection: $selectedItem, matching: .images) {
                        Label(photoPickerTitle, systemImage: "photo.on.rectangle")
                    }
                    .disabled(isAskingVisualAI)

                    if lens.image != nil {
                        Button("Read text on this iPhone", systemImage: "text.viewfinder") { lens.extractText() }
                            .disabled(isAskingVisualAI)
                        if let image = lens.image {
                            LabeledContent("Orientation", value: image.pixelHeight > image.pixelWidth ? "Portrait" : image.pixelWidth > image.pixelHeight ? "Landscape" : "Square")
                            LabeledContent("Lens copy", value: "\(image.pixelWidth) × \(image.pixelHeight)")
                        }
                    }
                }

                if lens.image != nil {
                    Section {
                        Button("What am I looking at?", systemImage: "eye") {
                            question = "What am I looking at?"
                            Task { await performQuestion() }
                        }
                        .disabled(isAskingVisualAI)

                        TextField("Ask about what you see", text: $question, axis: .vertical)
                            .lineLimit(2 ... 4)
                            .disabled(isAskingVisualAI)

                        Button(isRecordingLensVoiceQuestion ? "Stop listening" : ownsLensVoiceQuestion ? "Finish voice question" : "Ask by voice", systemImage: isRecordingLensVoiceQuestion ? "stop.circle.fill" : "mic.fill") {
                            Task { await toggleLensVoiceQuestion() }
                        }
                        .disabled(isAskingVisualAI || (app.isTranscribing && !isRecordingLensVoiceQuestion && !ownsLensVoiceQuestion))

                        Button { Task { await performQuestion() } } label: {
                            if isAskingVisualAI {
                                HStack(spacing: 10) { ProgressView(); Text("Asking AD…") }
                            } else {
                                Label("Ask AD", systemImage: "sparkles")
                            }
                        }
                        .disabled(question.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isAskingVisualAI)
                    } header: {
                        Text("Ask by voice or text")
                    } footer: {
                        Text("Reading text stays on this iPhone. General visual questions send only the prepared Lens image and your question to the active Cloud AI model.")
                    }
                }

                if !visualAnswer.isEmpty {
                    Section("AD") {
                        Text(visualAnswer).textSelection(.enabled)
                        Button("Read answer aloud", systemImage: "speaker.wave.2") {
                            do { try app.speechOutput.speak(visualAnswer) }
                            catch { errorMessage = error.localizedDescription }
                        }
                    }
                }

                if !lens.recognizedText.isEmpty {
                    Section("Recognized text") {
                        Text(lens.recognizedText).textSelection(.enabled)
                        Button("Read aloud", systemImage: "speaker.wave.2") {
                            do { try app.speechOutput.speak(lens.recognizedText) }
                            catch { errorMessage = error.localizedDescription }
                        }
                    }
                    Section("Translate") { translationControls }
                }
            }
            .navigationTitle("Lens")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
            .onChange(of: selectedItem) { _, item in
                guard let item else { return }
                Task {
                    do {
                        guard let data = try await item.loadTransferable(type: Data.self) else { throw LensImageError.invalidImage }
                        visualAnswer = ""
                        lens.load(data)
                    } catch { errorMessage = error.localizedDescription }
                }
            }
            .onChange(of: lens.state) { _, state in
                if case .failed(let reason) = state { errorMessage = reason }
            }
            .onChange(of: glasses.latestVisualCapture?.id) { _, _ in
                guard let capture = glasses.latestVisualCapture else { return }
                loadVisualCapture(capture)
            }
            .onDisappear {
                Task { await finishLensVoiceQuestionIfNeeded() }
            }
            .alert("Lens", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
                Button("OK", role: .cancel) {}
            } message: { Text(errorMessage ?? "") }
        }
    }

    private func captureFromGlasses() async {
        guard !isCapturingFromGlasses else { return }
        isCapturingFromGlasses = true
        defer { isCapturingFromGlasses = false }
        guard let capture = await glasses.requestVisualCapture() else {
            errorMessage = glasses.errorMessage ?? "AD Glasses could not capture an image."
            return
        }
        visualAnswer = ""
        loadVisualCapture(capture)
    }

    private func loadVisualCapture(_ capture: GlassesVisualCapture) {
        guard lastLoadedVisualCaptureID != capture.id else { return }
        lastLoadedVisualCaptureID = capture.id
        visualAnswer = ""
        lens.load(capture.jpegData)
    }

    @ViewBuilder
    private var imageArea: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 24).fill(.quaternary)
            if let image = lens.image, let uiImage = UIImage(data: image.jpegData) {
                Image(uiImage: uiImage).resizable().scaledToFit().clipShape(RoundedRectangle(cornerRadius: 24)).accessibilityLabel("Selected Lens image")
            } else {
                ContentUnavailableView("Choose what Lens should see", systemImage: "viewfinder", description: Text("Read text locally or ask AD a visual question."))
            }
            if lens.state == .preparing || lens.state == .recognizingText {
                ProgressView(lens.state == .preparing ? "Preparing image" : "Reading text")
                    .padding(14).background(.regularMaterial, in: Capsule())
            }
        }
        .frame(minHeight: 240)
        .padding(.horizontal, 16)
    }

    private var translationControls: some View {
        NativeLensTranslationControls(text: lens.recognizedText)
    }

    private func toggleLensVoiceQuestion() async {
        if let preserved = preservedAssistantDraftForVoiceQuestion {
            if app.isManualTranscription {
                await app.stopTranscription()
                let value = app.transcript.trimmingCharacters(in: .whitespacesAndNewlines)
                if !value.isEmpty { question = value }
            } else if !app.isTranscribing {
                let value = app.transcript.trimmingCharacters(in: .whitespacesAndNewlines)
                if !value.isEmpty { question = value }
            }
            app.chatDraft = preserved
            preservedAssistantDraftForVoiceQuestion = nil
            return
        }

        guard !app.isTranscribing else { return }
        preservedAssistantDraftForVoiceQuestion = app.chatDraft
        await app.startTranscription()
        if !app.isManualTranscription {
            preservedAssistantDraftForVoiceQuestion = nil
        }
    }

    private func finishLensVoiceQuestionIfNeeded() async {
        guard let preserved = preservedAssistantDraftForVoiceQuestion else { return }
        if app.isManualTranscription {
            await app.stopTranscription()
            let value = app.transcript.trimmingCharacters(in: .whitespacesAndNewlines)
            if !value.isEmpty { question = value }
        }
        app.chatDraft = preserved
        preservedAssistantDraftForVoiceQuestion = nil
    }

    private func performQuestion() async {
        let value = question.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty, let image = lens.image else { return }
        let normalized = value.lowercased()
        if normalized.contains("read") || normalized.contains("text") || normalized.contains("translate") {
            visualAnswer = ""
            lens.extractText()
            return
        }
        guard !isAskingVisualAI else { return }
        guard let profile = app.aiProfiles.activeProfile else {
            errorMessage = "Configure a Cloud AI profile in Settings before asking general visual questions."
            return
        }
        let credential: String
        do { credential = try app.aiProfiles.credential(for: profile.id) }
        catch { errorMessage = error.localizedDescription; return }

        isAskingVisualAI = true
        visualAnswer = ""
        defer { isAskingVisualAI = false }
        do {
            visualAnswer = try await ADVisualAIClient().answer(question: value, imageJPEGData: image.jpegData, profile: profile, credential: credential)
        } catch is CancellationError {
            return
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

private struct NativeLensTranslationControls: View {
    @EnvironmentObject private var translation: NativeTranslationController
    @EnvironmentObject private var app: AppModel
    let text: String
    @AppStorage("lens.translation.targetLanguage.v1") private var targetLanguage = "en"
    @State private var result: TextTranslationResult?
    @State private var errorMessage: String?
    private let languages: [(code: String, name: String)] = [
        ("en", "English"), ("hi", "Hindi"), ("bn", "Bengali"), ("es", "Spanish"),
        ("fr", "French"), ("de", "German"), ("ja", "Japanese"), ("ko", "Korean"),
        ("zh-Hans", "Chinese (Simplified)")
    ]

    var body: some View {
        Group {
            Picker("Translate to", selection: $targetLanguage) {
                ForEach(languages, id: \.code) { Text($0.name).tag($0.code) }
            }
            Button(translation.isTranslating ? "Translating…" : "Translate on this iPhone") {
                Task {
                    do { result = try await translation.translate(text, to: Locale.Language(identifier: targetLanguage)) }
                    catch { errorMessage = error.localizedDescription }
                }
            }
            .disabled(translation.isTranslating)
            if let result {
                Text(result.translatedText).textSelection(.enabled)
                Button("Read translation aloud", systemImage: "speaker.wave.2") {
                    do { try app.speechOutput.speak(result.translatedText, languageCode: result.targetLanguage) }
                    catch { errorMessage = error.localizedDescription }
                }
            }
        }
        .alert("Translation", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("OK", role: .cancel) {}
        } message: { Text(errorMessage ?? "") }
    }
}
