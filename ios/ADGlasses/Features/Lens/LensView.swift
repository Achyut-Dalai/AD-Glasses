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

struct LensView: View {
    @EnvironmentObject private var app: AppModel
    @EnvironmentObject private var glasses: GlassesManager
    @Environment(\.dismiss) private var dismiss
    @StateObject private var lens = LensSessionController()

    @State private var selectedItem: PhotosPickerItem?
    @State private var question = ""
    @State private var errorMessage: String?
    @State private var isCapturingFromGlasses = false
    @State private var lastLoadedVisualCaptureID: UUID?

    var body: some View {
        let photoPickerTitle = lens.image == nil ? "Choose a photo" : "Choose another photo"

        NavigationStack {
            List {
                Section {
                    imageArea
                }
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)

                Section("Image") {
                    if glasses.connectionState.isConnected,
                       glasses.supports(.camera) {
                        Button {
                            Task { await captureFromGlasses() }
                        } label: {
                            if isCapturingFromGlasses {
                                HStack(spacing: 10) {
                                    ProgressView()
                                    Text("Capturing with AD Glasses…")
                                }
                            } else {
                                Label("Capture with AD Glasses", systemImage: "camera.viewfinder")
                            }
                        }
                        .disabled(isCapturingFromGlasses)
                    }

                    PhotosPicker(selection: $selectedItem, matching: .images) {
                        Label(
                            photoPickerTitle,
                            systemImage: "photo.on.rectangle"
                        )
                    }

                    if lens.image != nil {
                        Button("Read text on this iPhone", systemImage: "text.viewfinder") {
                            lens.extractText()
                        }

                        if let image = lens.image {
                            LabeledContent(
                                "Orientation",
                                value: image.pixelHeight > image.pixelWidth ? "Portrait" :
                                    image.pixelWidth > image.pixelHeight ? "Landscape" : "Square"
                            )
                            LabeledContent(
                                "Lens copy",
                                value: "\(image.pixelWidth) × \(image.pixelHeight)"
                            )
                        }
                    }

                }

                if lens.image != nil {
                    Section {
                        TextField("For example, read or translate the text", text: $question, axis: .vertical)
                            .lineLimit(2 ... 4)

                        Button(
                            app.isTranscribing ? "Stop listening" : "Ask by voice",
                            systemImage: app.isTranscribing ? "stop.circle.fill" : "mic.fill"
                        ) {
                            Task {
                                await app.toggleTranscription()
                                if !app.isTranscribing, !app.transcript.isEmpty {
                                    question = app.transcript
                                }
                            }
                        }

                        Button("Use this question", systemImage: "arrow.right.circle.fill") {
                            performLocalQuestion()
                        }
                        .disabled(question.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    } header: {
                        Text("Ask by voice or text")
                    } footer: {
                        Text("Lens uses an orientation-correct, size-bounded copy for OCR or AI. It never replaces the original photo kept in Photos or Library.")
                    }
                }

                if !lens.recognizedText.isEmpty {
                    Section("Recognized text") {
                        Text(lens.recognizedText)
                            .textSelection(.enabled)

                        Button("Read aloud", systemImage: "speaker.wave.2") {
                            do {
                                try app.speechOutput.speak(lens.recognizedText)
                            } catch {
                                errorMessage = error.localizedDescription
                            }
                        }
                    }

                    Section("Translate") {
                        translationControls
                    }
                }
            }
            .navigationTitle("Lens")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .onChange(of: selectedItem) { _, item in
                guard let item else { return }
                Task {
                    do {
                        guard let data = try await item.loadTransferable(type: Data.self) else {
                            throw LensImageError.invalidImage
                        }
                        lens.load(data)
                    } catch {
                        errorMessage = error.localizedDescription
                    }
                }
            }
            .onChange(of: lens.state) { _, state in
                if case .failed(let reason) = state {
                    errorMessage = reason
                }
            }
            .onChange(of: glasses.latestVisualCapture?.id) { _, _ in
                guard let capture = glasses.latestVisualCapture else { return }
                loadVisualCapture(capture)
            }
            .onDisappear {
                Task { await app.stopTranscription() }
            }
            .alert("Lens", isPresented: Binding(
                get: { errorMessage != nil },
                set: { if !$0 { errorMessage = nil } }
            )) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(errorMessage ?? "")
            }
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
        loadVisualCapture(capture)
    }

    private func loadVisualCapture(_ capture: GlassesVisualCapture) {
        guard lastLoadedVisualCaptureID != capture.id else { return }
        lastLoadedVisualCaptureID = capture.id
        lens.load(capture.jpegData)
    }

    @ViewBuilder
    private var imageArea: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 24)
                .fill(.quaternary)
            if let image = lens.image,
               let uiImage = UIImage(data: image.jpegData) {
                Image(uiImage: uiImage)
                    .resizable()
                    .scaledToFit()
                    .clipShape(RoundedRectangle(cornerRadius: 24))
                    .accessibilityLabel("Selected Lens image")
            } else {
                ContentUnavailableView(
                    "Choose what Lens should read",
                    systemImage: "viewfinder",
                    description: Text("Image preparation and text recognition stay on this iPhone.")
                )
            }

            if lens.state == .preparing || lens.state == .recognizingText {
                ProgressView(lens.state == .preparing ? "Preparing image" : "Reading text")
                    .padding(14)
                    .background(.regularMaterial, in: Capsule())
            }
        }
        .frame(minHeight: 240)
        .padding(.horizontal, 16)
    }

    @ViewBuilder
    private var translationControls: some View {
        if #available(iOS 18.0, *) {
            NativeLensTranslationControls(text: lens.recognizedText)
        } else if #available(iOS 17.4, *) {
            SystemLensTranslationButton(text: lens.recognizedText)
        } else {
            Text("Native translation requires iOS 17.4 or later.")
                .foregroundStyle(.secondary)
        }
    }

    private func performLocalQuestion() {
        let value = question.lowercased()
        if value.contains("read") || value.contains("text") || value.contains("translate") {
            lens.extractText()
        } else {
            errorMessage = "This question needs general image understanding. For now, ask Lens to read or translate visible text."
        }
    }
}

@available(iOS 18.0, *)
private struct NativeLensTranslationControls: View {
    @EnvironmentObject private var translation: NativeTranslationController
    @EnvironmentObject private var app: AppModel

    let text: String

    @AppStorage("lens.translation.targetLanguage.v1") private var targetLanguage = "en"
    @State private var result: TextTranslationResult?
    @State private var errorMessage: String?

    private let languages: [(code: String, name: String)] = [
        ("en", "English"),
        ("hi", "Hindi"),
        ("bn", "Bengali"),
        ("es", "Spanish"),
        ("fr", "French"),
        ("de", "German"),
        ("ja", "Japanese"),
        ("ko", "Korean"),
        ("zh-Hans", "Chinese (Simplified)")
    ]

    var body: some View {
        Group {
            Picker("Translate to", selection: $targetLanguage) {
                ForEach(languages, id: \.code) { language in
                    Text(language.name).tag(language.code)
                }
            }

            Button(translation.isTranslating ? "Translating…" : "Translate on this iPhone") {
                Task {
                    do {
                        result = try await translation.translate(
                            text,
                            to: Locale.Language(identifier: targetLanguage)
                        )
                    } catch {
                        errorMessage = error.localizedDescription
                    }
                }
            }
            .disabled(translation.isTranslating)

            if let result {
                Text(result.translatedText)
                    .textSelection(.enabled)
                Button("Read translation aloud", systemImage: "speaker.wave.2") {
                    do {
                        try app.speechOutput.speak(
                            result.translatedText,
                            languageCode: result.targetLanguage
                        )
                    } catch {
                        errorMessage = error.localizedDescription
                    }
                }
            }
        }
        .alert("Translation", isPresented: Binding(
            get: { errorMessage != nil },
            set: { if !$0 { errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(errorMessage ?? "")
        }
    }
}

@available(iOS 17.4, *)
private struct SystemLensTranslationButton: View {
    let text: String
    @State private var isPresented = false

    var body: some View {
        Button("Open Apple Translate", systemImage: "translate") {
            isPresented = true
        }
        .translationPresentation(isPresented: $isPresented, text: text)
    }
}
