import SwiftUI
import Translation

struct TranslationView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Group {
                if #available(iOS 18.0, *) {
                    NativeTranslateExperience()
                } else if #available(iOS 17.4, *) {
                    LegacyTranslateExperience()
                } else {
                    ContentUnavailableView(
                        "Translation unavailable",
                        systemImage: "translate",
                        description: Text("Update to iOS 17.4 or later to use Apple Translation.")
                    )
                }
            }
            .navigationTitle("Translate")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

@available(iOS 18.0, *)
private struct NativeTranslateExperience: View {
    @EnvironmentObject private var translation: NativeTranslationController
    @EnvironmentObject private var app: AppModel
    @EnvironmentObject private var phoneVoiceActivation: PhoneVoiceActivationController

    @StateObject private var liveTranslation = LiveTranslationController()

    @AppStorage("translation.sourceLanguage.v1") private var sourceLanguage = "hi"
    @AppStorage("translation.targetLanguage.v1") private var targetLanguage = "en"

    @State private var phrase = ""
    @State private var phraseResult: TextTranslationResult?
    @State private var phraseError: String?

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
        Form {
            Section("Languages") {
                Picker("From", selection: $sourceLanguage) {
                    ForEach(languages, id: \.code) { language in
                        Text(language.name).tag(language.code)
                    }
                }
                .disabled(liveTranslation.isRunning)

                Picker("To", selection: $targetLanguage) {
                    ForEach(languages, id: \.code) { language in
                        Text(language.name).tag(language.code)
                    }
                }
                .disabled(liveTranslation.isRunning)

                Button("Swap languages", systemImage: "arrow.up.arrow.down") {
                    let previousSource = sourceLanguage
                    sourceLanguage = targetLanguage
                    targetLanguage = previousSource
                }
                .disabled(liveTranslation.isRunning)
            }

            Section {
                Button {
                    Task {
                        if liveTranslation.isRunning {
                            await stopLiveTranslation()
                        } else {
                            await startLiveTranslation()
                        }
                    }
                } label: {
                    Label(
                        liveTranslation.isRunning ? "Stop Live Translation" : "Start Live Translation",
                        systemImage: liveTranslation.isRunning ? "stop.circle.fill" : "waveform.and.mic"
                    )
                }
                .buttonStyle(.borderedProminent)
                .disabled(!liveTranslation.isRunning && translation.isTranslating)

                HStack(spacing: 10) {
                    if liveTranslation.isRunning {
                        ProgressView()
                            .controlSize(.small)
                    }
                    Text(liveTranslation.statusMessage)
                        .font(.footnote.weight(.medium))
                        .foregroundStyle(liveTranslation.isRunning ? .primary : .secondary)
                }

                if let inputRouteName = liveTranslation.inputRouteName,
                   liveTranslation.isRunning {
                    LabeledContent("Listening through", value: inputRouteName)
                        .font(.footnote)
                }

                if !liveTranslation.currentTranscript.isEmpty {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Hearing")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.secondary)
                        Text(liveTranslation.currentTranscript)
                    }
                }

                if !liveTranslation.lastSourceText.isEmpty {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(sourceLanguageName)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.secondary)
                        Text(liveTranslation.lastSourceText)
                    }
                }

                if !liveTranslation.lastTranslation.isEmpty {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(targetLanguageName)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.secondary)
                        Text(liveTranslation.lastTranslation)
                            .font(.title3)
                            .textSelection(.enabled)
                    }
                }

                if let error = liveTranslation.errorMessage {
                    Label(error, systemImage: "exclamationmark.triangle.fill")
                        .font(.footnote)
                        .foregroundStyle(.red)
                }
            } header: {
                Text("Live Translation")
            } footer: {
                Text("Speech identified as \(sourceLanguageName) is translated to \(targetLanguageName). Other languages are ignored. AD Glasses uses the active iPhone audio route, so a connected Bluetooth microphone can be used when iOS selects it.")
            }

            Section {
                TextField("Type or paste a phrase", text: $phrase, axis: .vertical)
                    .lineLimit(2 ... 6)
                    .disabled(liveTranslation.isRunning)

                Button(translation.isTranslating ? "Translating…" : "Translate phrase") {
                    Task { await translatePhrase() }
                }
                .disabled(
                    liveTranslation.isRunning ||
                    translation.isTranslating ||
                    phrase.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                )

                if translation.isTranslating,
                   !liveTranslation.isRunning,
                   let statusMessage = translation.statusMessage {
                    HStack(spacing: 10) {
                        ProgressView()
                            .controlSize(.small)
                        Text(statusMessage)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }

                if let phraseResult {
                    Text(phraseResult.translatedText)
                        .font(.title3)
                        .textSelection(.enabled)

                    Button("Read aloud", systemImage: "speaker.wave.2") {
                        do {
                            try app.speechOutput.speak(
                                phraseResult.translatedText,
                                languageCode: phraseResult.targetLanguage
                            )
                        } catch {
                            phraseError = error.localizedDescription
                        }
                    }
                }
            } header: {
                Text("Translate a phrase")
            } footer: {
                Text("Use this for a quick typed or pasted translation without starting continuous listening.")
            }
        }
        .onChange(of: sourceLanguage) { _, _ in resetPhraseResult() }
        .onChange(of: targetLanguage) { _, _ in resetPhraseResult() }
        .onChange(of: app.isGlassesAssistantAudioActive) { _, isActive in
            guard isActive, liveTranslation.isRunning else { return }
            Task { await stopLiveTranslation() }
        }
        .onDisappear {
            Task { await stopLiveTranslation() }
        }
        .alert("Translation", isPresented: Binding(
            get: { phraseError != nil },
            set: { if !$0 { phraseError = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(phraseError ?? "")
        }
    }

    private var sourceLanguageName: String {
        languages.first(where: { $0.code == sourceLanguage })?.name ?? sourceLanguage
    }

    private var targetLanguageName: String {
        languages.first(where: { $0.code == targetLanguage })?.name ?? targetLanguage
    }

    private func startLiveTranslation() async {
        guard sourceLanguage != targetLanguage else {
            phraseError = "Choose two different languages."
            return
        }
        guard !app.isGlassesAssistantAudioActive else {
            phraseError = "Finish the current glasses Assistant audio turn before starting Live Translation."
            return
        }

        phraseError = nil
        phraseResult = nil
        app.cancelResponse()
        app.speechOutput.stop()
        await app.stopTranscription()
        phoneVoiceActivation.setExternalAudioSuspended(true)

        let started = await liveTranslation.start(
            sourceLanguageCode: sourceLanguage,
            targetLanguageCode: targetLanguage,
            translation: translation,
            speechOutput: app.speechOutput
        )
        if !started {
            phoneVoiceActivation.setExternalAudioSuspended(false)
        }
    }

    private func stopLiveTranslation() async {
        if liveTranslation.isRunning {
            await liveTranslation.stop()
        }
        phoneVoiceActivation.setExternalAudioSuspended(false)
    }

    private func translatePhrase() async {
        let value = phrase.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return }
        guard sourceLanguage != targetLanguage else {
            phraseError = "Choose two different languages."
            return
        }

        phraseResult = nil
        phraseError = nil
        do {
            phraseResult = try await translation.translate(
                value,
                from: Locale.Language(identifier: sourceLanguage),
                to: Locale.Language(identifier: targetLanguage)
            )
        } catch is CancellationError {
            return
        } catch {
            phraseError = error.localizedDescription
        }
    }

    private func resetPhraseResult() {
        phraseResult = nil
        phraseError = nil
    }
}

@available(iOS 17.4, *)
private struct LegacyTranslateExperience: View {
    @State private var text = ""
    @State private var isPresented = false

    var body: some View {
        Form {
            Section {
                TextField("Type or paste a phrase", text: $text, axis: .vertical)
                    .lineLimit(2 ... 6)

                Button("Open Apple Translate", systemImage: "translate") {
                    isPresented = true
                }
                .disabled(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                .translationPresentation(isPresented: $isPresented, text: text)
            } footer: {
                Text("Live Translation requires iOS 18 or later.")
            }
        }
    }
}
