import SwiftUI
import Translation
import UIKit

struct TranslationView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            LiveTranslateExperience()
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

private struct TranslationLanguageOption: Identifiable, Hashable {
    let language: Locale.Language
    let code: String
    let name: String

    var id: String { code }

    init(_ language: Locale.Language) {
        self.language = language
        code = language.minimalIdentifier
        name = Locale.current.localizedString(forIdentifier: language.minimalIdentifier)
            ?? Locale.current.localizedString(
                forLanguageCode: language.languageCode?.identifier ?? language.minimalIdentifier
            )
            ?? language.minimalIdentifier
    }
}

private struct GroqSourceLanguageOption: Identifiable, Hashable {
    let code: String
    let name: String

    var id: String { code }

    static let supported: [GroqSourceLanguageOption] = [
        GroqSourceLanguageOption(code: "", name: "Auto Detect"),
        GroqSourceLanguageOption(code: "hi", name: "Hindi"),
        GroqSourceLanguageOption(code: "bn", name: "Bengali"),
        GroqSourceLanguageOption(code: "mr", name: "Marathi"),
        GroqSourceLanguageOption(code: "gu", name: "Gujarati"),
        GroqSourceLanguageOption(code: "pa", name: "Punjabi"),
        GroqSourceLanguageOption(code: "ta", name: "Tamil"),
        GroqSourceLanguageOption(code: "te", name: "Telugu"),
        GroqSourceLanguageOption(code: "kn", name: "Kannada"),
        GroqSourceLanguageOption(code: "ml", name: "Malayalam"),
        GroqSourceLanguageOption(code: "ur", name: "Urdu"),
        GroqSourceLanguageOption(code: "ne", name: "Nepali"),
        GroqSourceLanguageOption(code: "ar", name: "Arabic"),
        GroqSourceLanguageOption(code: "es", name: "Spanish"),
        GroqSourceLanguageOption(code: "fr", name: "French"),
        GroqSourceLanguageOption(code: "de", name: "German"),
        GroqSourceLanguageOption(code: "ja", name: "Japanese"),
        GroqSourceLanguageOption(code: "ko", name: "Korean"),
        GroqSourceLanguageOption(code: "zh", name: "Chinese")
    ]
}

@MainActor
private struct LiveTranslateExperience: View {
    @EnvironmentObject private var translation: NativeTranslationController
    @EnvironmentObject private var app: AppModel

    @StateObject private var appleLive = LiveTranslationController()
    @StateObject private var groqLive = GroqLiveTranslationController()

    @AppStorage("translation.engine.v2") private var engineRaw = LiveTranslationEngine.groq.rawValue
    @AppStorage("translation.groqSourceLanguage.v2") private var groqSourceLanguage = ""
    @AppStorage("translation.appleSourceLanguage.v2") private var appleSourceLanguage = "hi"

    @State private var appleSourceLanguages = [TranslationLanguageOption]()
    @State private var isLoadingAppleLanguages = true
    @State private var languageError: String?
    @State private var liveStartError: String?

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                engineCard
                liveTranslationCard
            }
            .frame(maxWidth: 680)
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 28)
            .frame(maxWidth: .infinity)
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .task {
            migrateGroqSourceLanguagePreferenceIfNeeded()
            await loadAppleSourceLanguages()
        }
        .onChange(of: engineRaw) { _, _ in
            Task { await stopLiveTranslation() }
        }
        .onChange(of: groqSourceLanguage) { _, _ in
            guard groqLive.isRunning else { return }
            Task { await stopLiveTranslation() }
        }
        .onChange(of: appleSourceLanguage) { _, _ in
            guard appleLive.isRunning else { return }
            Task { await stopLiveTranslation() }
        }
        .onChange(of: app.isGlassesAssistantAudioActive) { _, isActive in
            guard isActive, isLiveRunning else { return }
            Task { await stopLiveTranslation() }
        }
        .onDisappear {
            Task { await stopLiveTranslation() }
        }
    }

    private var engineCard: some View {
        TranslateCard {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 3) {
                    Text("English Live Translation")
                        .font(.headline)
                    Text("Translate nearby speech into English. Auto Detect is the first-run choice; selecting a language gives Whisper a recognition hint and that choice stays selected until you change it.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }

                Picker("Engine", selection: engineBinding) {
                    ForEach(LiveTranslationEngine.allCases) { engine in
                        Text(engine.displayName).tag(engine)
                    }
                }
                .pickerStyle(.segmented)
                .disabled(isLiveRunning)

                if selectedEngine == .groq {
                    groqSettings
                } else {
                    appleSettings
                }
            }
        }
    }

    private var groqSettings: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 10) {
                Image(systemName: "waveform")
                    .foregroundStyle(.secondary)
                VStack(alignment: .leading, spacing: 3) {
                    Text("Speech model")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text("Whisper Large V3")
                        .font(.subheadline.weight(.semibold))
                }
                Spacer()
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(.green)
            }
            .padding(.horizontal, 12)
            .frame(maxWidth: .infinity, minHeight: 54, alignment: .leading)
            .background(
                Color(uiColor: .secondarySystemGroupedBackground),
                in: RoundedRectangle(cornerRadius: 12, style: .continuous)
            )

            Menu {
                ForEach(GroqSourceLanguageOption.supported) { option in
                    Button {
                        groqSourceLanguage = option.code
                    } label: {
                        if option.code == normalizedGroqSourceLanguage {
                            Label(option.name, systemImage: "checkmark")
                        } else {
                            Text(option.name)
                        }
                    }
                }
            } label: {
                settingsRow(title: "Spoken language", value: groqSourceLanguageName)
            }
            .buttonStyle(.plain)
            .disabled(isLiveRunning)

            Text("Whisper Large V3 recognizes each isolated speech turn so you can verify exactly what it heard. Your configured Groq cloud AI model then translates that recognized text to English. AD does not use Apple Translation or a second Whisper audio-translation request in this path.")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)

            HStack(spacing: 8) {
                Image(systemName: groqProfile != nil ? "checkmark.circle.fill" : "exclamationmark.triangle.fill")
                    .foregroundStyle(groqProfile != nil ? .green : .orange)
                Text(groqProfile.map { "Using Groq profile: \($0.name) · \($0.model)" }
                     ?? "Add a Groq profile and API key in Settings → AI & Models.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Label(
                normalizedGroqSourceLanguage.isEmpty
                    ? "Auto Detect → English"
                    : "\(groqSourceLanguageName) → English",
                systemImage: "globe"
            )
            .font(.footnote.weight(.medium))
        }
    }

    private var appleSettings: some View {
        VStack(alignment: .leading, spacing: 12) {
            if isLoadingAppleLanguages {
                HStack(spacing: 10) {
                    ProgressView()
                    Text("Checking offline speech and translation languages…")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            } else if let languageError {
                Label(languageError, systemImage: "exclamationmark.triangle.fill")
                    .font(.footnote)
                    .foregroundStyle(.orange)
                    .fixedSize(horizontal: false, vertical: true)
            } else {
                Menu {
                    ForEach(appleSourceLanguages) { option in
                        Button {
                            appleSourceLanguage = option.code
                        } label: {
                            if option.code == appleSourceLanguage {
                                Label(option.name, systemImage: "checkmark")
                            } else {
                                Text(option.name)
                            }
                        }
                    }
                } label: {
                    settingsRow(title: "Spoken language", value: appleSourceLanguageName)
                }
                .buttonStyle(.plain)
                .disabled(isLiveRunning)

                Label("\(appleSourceLanguageName) → English · on-device", systemImage: "iphone")
                    .font(.footnote.weight(.medium))
            }

            Text("Apple Offline uses SpeechAnalyzer plus Translation with the low-latency strategy. Language assets may need to download once before offline use.")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var liveTranslationCard: some View {
        TranslateCard {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 10) {
                    Image(systemName: "waveform.and.mic")
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(.blue)
                        .frame(width: 36, height: 36)
                        .background(.blue.opacity(0.10), in: RoundedRectangle(cornerRadius: 10))

                    VStack(alignment: .leading, spacing: 2) {
                        Text("Live Translation")
                            .font(.headline)
                        Text(selectedEngine == .groq
                             ? "\(groqSourceLanguageName) → English"
                             : "\(appleSourceLanguageName) → English")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                }

                Text(selectedEngine == .groq
                     ? "AD listens for one phrase, closes the microphone, lets Whisper recognize it, translates that text with your Groq cloud model, speaks the English result, then opens a fresh microphone turn. It never listens to its own translated speech."
                     : "AD transcribes and translates each completed utterance on the iPhone, speaks the English result, then resumes listening.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)

                Button {
                    Task {
                        if isLiveRunning {
                            await stopLiveTranslation()
                        } else {
                            await startLiveTranslation()
                        }
                    }
                } label: {
                    HStack(spacing: 9) {
                        Image(systemName: isLiveRunning ? "stop.circle.fill" : "waveform.and.mic")
                        Text(isLiveRunning ? "Stop Live Translation" : "Start Live Translation")
                    }
                    .font(.headline)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.vertical, 3)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .disabled(!isLiveRunning && startDisabled)

                HStack(spacing: 9) {
                    if isLiveRunning {
                        ProgressView()
                            .controlSize(.small)
                    } else {
                        Image(systemName: "checkmark.circle")
                            .foregroundStyle(.secondary)
                    }
                    Text(activeStatusMessage)
                        .font(.footnote.weight(.medium))
                        .foregroundStyle(isLiveRunning ? .primary : .secondary)
                }

                if let inputRouteName = activeInputRouteName, isLiveRunning {
                    HStack(spacing: 8) {
                        Image(systemName: "mic")
                            .foregroundStyle(.secondary)
                        Text("Listening through \(inputRouteName)")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }

                if selectedEngine == .apple, !appleLive.currentTranscript.isEmpty {
                    transcriptBlock(label: "Hearing", text: appleLive.currentTranscript, emphasized: false)
                }

                if !activeSourceText.isEmpty {
                    transcriptBlock(
                        label: selectedEngine == .groq ? "Recognized speech" : appleSourceLanguageName,
                        text: activeSourceText,
                        emphasized: false
                    )
                }

                if !activeTranslation.isEmpty {
                    transcriptBlock(label: "English", text: activeTranslation, emphasized: true)
                }

                if let liveStartError {
                    errorLabel(liveStartError)
                }
                if let activeErrorMessage {
                    errorLabel(activeErrorMessage)
                }
            }
        }
    }

    private func settingsRow(title: String, value: String) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text(value)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.primary)
            }
            Spacer()
            Image(systemName: "chevron.up.chevron.down")
                .font(.caption2.weight(.bold))
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 12)
        .frame(maxWidth: .infinity, minHeight: 54, alignment: .leading)
        .background(
            Color(uiColor: .secondarySystemGroupedBackground),
            in: RoundedRectangle(cornerRadius: 12, style: .continuous)
        )
    }

    private func transcriptBlock(label: String, text: String, emphasized: Bool) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(label)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
            Text(text)
                .font(emphasized ? .title3 : .body)
                .textSelection(.enabled)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(12)
        .background(
            Color(uiColor: .secondarySystemGroupedBackground),
            in: RoundedRectangle(cornerRadius: 12, style: .continuous)
        )
    }

    private func errorLabel(_ text: String) -> some View {
        Label(text, systemImage: "exclamationmark.triangle.fill")
            .font(.footnote)
            .foregroundStyle(.red)
            .fixedSize(horizontal: false, vertical: true)
    }

    private var selectedEngine: LiveTranslationEngine {
        LiveTranslationEngine(rawValue: engineRaw) ?? .groq
    }

    private var engineBinding: Binding<LiveTranslationEngine> {
        Binding(
            get: { selectedEngine },
            set: { engineRaw = $0.rawValue }
        )
    }

    private var selectedGroqModel: GroqWhisperModel {
        .largeV3
    }

    private var normalizedGroqSourceLanguage: String {
        GroqSourceLanguageOption.supported.contains(where: { $0.code == groqSourceLanguage })
            ? groqSourceLanguage
            : ""
    }

    private var groqSourceLanguageName: String {
        GroqSourceLanguageOption.supported
            .first(where: { $0.code == normalizedGroqSourceLanguage })?.name
            ?? "Auto Detect"
    }

    private var groqProfile: AIProfile? {
        if let active = app.aiProfiles.activeProfile,
           active.provider == .groq,
           app.aiProfiles.hasCredential(for: active.id) {
            return active
        }
        return app.aiProfiles.profiles.first {
            $0.provider == .groq && app.aiProfiles.hasCredential(for: $0.id)
        }
    }

    private var appleSourceLanguageName: String {
        appleSourceLanguages.first(where: { $0.code == appleSourceLanguage })?.name
            ?? Locale.current.localizedString(forIdentifier: appleSourceLanguage)
            ?? appleSourceLanguage
    }

    private var isLiveRunning: Bool {
        groqLive.isRunning || appleLive.isRunning
    }

    private var startDisabled: Bool {
        if app.isGlassesAssistantAudioActive || translation.isTranslating { return true }
        switch selectedEngine {
        case .groq:
            return groqProfile == nil
        case .apple:
            return isLoadingAppleLanguages || appleSourceLanguages.isEmpty
        }
    }

    private var activeStatusMessage: String {
        selectedEngine == .groq ? groqLive.statusMessage : appleLive.statusMessage
    }

    private var activeInputRouteName: String? {
        selectedEngine == .groq ? groqLive.inputRouteName : appleLive.inputRouteName
    }

    private var activeSourceText: String {
        selectedEngine == .groq ? groqLive.lastSourceText : appleLive.lastSourceText
    }

    private var activeTranslation: String {
        selectedEngine == .groq ? groqLive.lastTranslation : appleLive.lastTranslation
    }

    private var activeErrorMessage: String? {
        selectedEngine == .groq ? groqLive.errorMessage : appleLive.errorMessage
    }

    private func migrateGroqSourceLanguagePreferenceIfNeeded() {
        let defaults = UserDefaults.standard
        let currentKey = "translation.groqSourceLanguage.v2"
        guard defaults.object(forKey: currentKey) == nil else { return }

        // v1 accidentally used Hindi as the code-level default. Preserve a real persisted user
        // choice when one exists; otherwise the new first-run behavior remains Auto Detect.
        if let legacy = defaults.string(forKey: "translation.groqSourceLanguage.v1"),
           GroqSourceLanguageOption.supported.contains(where: { $0.code == legacy }) {
            groqSourceLanguage = legacy
        } else {
            groqSourceLanguage = ""
        }
    }

    private func loadAppleSourceLanguages() async {
        isLoadingAppleLanguages = true
        languageError = nil
        defer { isLoadingAppleLanguages = false }

        async let translationSupported = translation.supportedLanguages()
        async let speechSupported = SpeechAnalyzerTranscriber.supportedSpeechLocales()
        let translationLanguages = await translationSupported
        let speechBases = Set((await speechSupported).map { languageBase($0.identifier) })
        let english = Locale.Language(identifier: "en")

        var options = [TranslationLanguageOption]()
        var seen = Set<String>()
        for language in translationLanguages {
            if Task.isCancelled { return }
            let option = TranslationLanguageOption(language)
            let base = languageBase(option.code)
            guard base != "en", speechBases.contains(base), seen.insert(option.code).inserted else { continue }
            let status = await translation.availability(from: language, to: english)
            guard status != .unsupported else { continue }
            options.append(option)
        }

        options.sort { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
        appleSourceLanguages = options
        guard !options.isEmpty else {
            languageError = "No SpeechAnalyzer language on this iPhone currently overlaps with Apple Translation → English."
            return
        }

        if !options.contains(where: { $0.code == appleSourceLanguage }) {
            appleSourceLanguage = preferredOption(code: "hi", in: options)?.code ?? options[0].code
        }
    }

    private func preferredOption(
        code: String,
        in options: [TranslationLanguageOption]
    ) -> TranslationLanguageOption? {
        let base = languageBase(code)
        return options.first { languageBase($0.code) == base }
    }

    private func startLiveTranslation() async {
        liveStartError = nil
        guard !app.isGlassesAssistantAudioActive else {
            liveStartError = "Finish the current glasses voice turn before starting Live Translation."
            return
        }

        app.cancelResponse()
        app.speechOutput.stop()
        await app.stopTranscription()

        switch selectedEngine {
        case .groq:
            guard let profile = groqProfile else {
                liveStartError = GroqSpeechTranslationError.missingCredential.localizedDescription
                return
            }
            do {
                let credential = try app.aiProfiles.credential(for: profile.id)
                _ = await groqLive.start(
                    model: selectedGroqModel,
                    translationModel: profile.model,
                    credential: credential,
                    sourceLanguageCode: normalizedGroqSourceLanguage.isEmpty
                        ? nil
                        : normalizedGroqSourceLanguage,
                    speechOutput: app.speechOutput
                )
            } catch {
                liveStartError = error.localizedDescription
            }

        case .apple:
            guard appleSourceLanguages.contains(where: { $0.code == appleSourceLanguage }) else {
                liveStartError = "Choose an available spoken language for Apple Offline."
                return
            }
            _ = await appleLive.start(
                sourceLanguageCode: appleSourceLanguage,
                targetLanguageCode: "en",
                translation: translation,
                speechOutput: app.speechOutput
            )
        }
    }

    private func stopLiveTranslation() async {
        if groqLive.isRunning {
            await groqLive.stop()
        }
        if appleLive.isRunning {
            await appleLive.stop()
        }
    }

    private func languageBase(_ code: String) -> String {
        let normalized = code
            .replacingOccurrences(of: "_", with: "-")
            .lowercased()
        return normalized.split(separator: "-").first.map(String.init) ?? normalized
    }
}

private struct TranslateCard<Content: View>: View {
    let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        content
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                Color(uiColor: .secondarySystemGroupedBackground),
                in: RoundedRectangle(cornerRadius: 18, style: .continuous)
            )
    }
}
