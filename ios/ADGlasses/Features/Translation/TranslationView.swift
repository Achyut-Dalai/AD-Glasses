import SwiftUI
import Translation

struct TranslationView: View {
    @EnvironmentObject private var app: AppModel
    @Environment(\.dismiss) private var dismiss

    @State private var sourceText = ""

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Speak or type a phrase", text: $sourceText, axis: .vertical)
                        .lineLimit(3 ... 8)

                    Button(
                        app.isTranscribing ? "Stop listening" : "Listen on this iPhone",
                        systemImage: app.isTranscribing ? "stop.circle.fill" : "mic.fill"
                    ) {
                        Task {
                            await app.toggleTranscription()
                            if !app.isTranscribing, !app.transcript.isEmpty {
                                sourceText = app.transcript
                            }
                        }
                    }
                } header: {
                    Text("What was said")
                } footer: {
                    Text("Speech recognition and translation use Apple’s native frameworks. Language models may need to be downloaded by iOS the first time.")
                }

                Section("Translation") {
                    if #available(iOS 18.0, *) {
                        NativeTranslationControls(sourceText: sourceText)
                    } else if #available(iOS 17.4, *) {
                        SystemTranslationControls(sourceText: sourceText)
                    } else {
                        ContentUnavailableView(
                            "Translation unavailable",
                            systemImage: "translate",
                            description: Text("Update to iOS 17.4 or later to use Apple Translation.")
                        )
                    }
                }
            }
            .navigationTitle("Translate")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .onChange(of: app.transcript) { _, transcript in
                if app.isTranscribing, !transcript.isEmpty {
                    sourceText = transcript
                }
            }
            .onDisappear {
                Task { await app.stopTranscription() }
            }
        }
    }
}

@available(iOS 18.0, *)
private struct NativeTranslationControls: View {
    @EnvironmentObject private var translation: NativeTranslationController
    @EnvironmentObject private var app: AppModel

    let sourceText: String

    @AppStorage("translation.targetLanguage.v1") private var targetLanguage = "hi"
    @State private var result: TextTranslationResult?
    @State private var errorMessage: String?

    private let languages: [(code: String, name: String)] = [
        ("en", "English"),
        ("hi", "Hindi"),
        ("bn", "Bengali"),
        ("es", "Spanish"),
        ("fr", "French"),
        ("de", "German"),
        ("it", "Italian"),
        ("pt", "Portuguese"),
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

            Button(translation.isTranslating ? "Translating…" : "Translate") {
                Task {
                    do {
                        result = try await translation.translate(
                            sourceText,
                            to: Locale.Language(identifier: targetLanguage)
                        )
                    } catch {
                        errorMessage = error.localizedDescription
                    }
                }
            }
            .disabled(
                translation.isTranslating ||
                    sourceText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            )

            if let result {
                Text(result.translatedText)
                    .font(.title3)
                    .textSelection(.enabled)

                Button("Read aloud", systemImage: "speaker.wave.2") {
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
private struct SystemTranslationControls: View {
    let sourceText: String
    @State private var isPresented = false

    var body: some View {
        Button("Open Apple Translate", systemImage: "translate") {
            isPresented = true
        }
        .disabled(sourceText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        .translationPresentation(isPresented: $isPresented, text: sourceText)
    }
}
