import SwiftUI
import Translation
import UIKit

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

@available(iOS 18.0, *)
private struct NativeTranslateExperience: View {
    @EnvironmentObject private var translation: NativeTranslationController
    @EnvironmentObject private var app: AppModel

    @StateObject private var liveTranslation = LiveTranslationController()

    @AppStorage("translation.sourceLanguage.v1") private var sourceLanguage = "hi"
    @AppStorage("translation.targetLanguage.v1") private var targetLanguage = "en"

    @State private var allLanguages = [TranslationLanguageOption]()
    @State private var targetLanguages = [TranslationLanguageOption]()
    @State private var isLoadingLanguages = true
    @State private var isLoadingTargets = false
    @State private var languageError: String?
    @State private var liveStartError: String?

    @State private var phrase = ""
    @State private var phraseResult: TextTranslationResult?
    @State private var phraseError: String?

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                languageCard
                liveTranslationCard
                phraseCard
            }
            .frame(maxWidth: 680)
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 28)
            .frame(maxWidth: .infinity)
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .task { await loadLanguages() }
        .onChange(of: sourceLanguage) { _, _ in
            resetPhraseResult()
            Task { await refreshTargets() }
        }
        .onChange(of: targetLanguage) { _, _ in
            resetPhraseResult()
        }
        .onChange(of: app.isGlassesAssistantAudioActive) { _, isActive in
            guard isActive, liveTranslation.isRunning else { return }
            Task { await stopLiveTranslation() }
        }
        .onDisappear {
            Task { await stopLiveTranslation() }
        }
    }

    private var languageCard: some View {
        TranslateCard {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Languages")
                            .font(.headline)
                        Text("Choose exactly what AD Glasses should listen for and speak back.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    if isLoadingLanguages || isLoadingTargets {
                        ProgressView()
                            .controlSize(.small)
                    }
                }

                if let languageError {
                    Label(languageError, systemImage: "exclamationmark.triangle.fill")
                        .font(.footnote)
                        .foregroundStyle(.red)
                } else if allLanguages.isEmpty {
                    HStack(spacing: 10) {
                        ProgressView()
                        Text("Loading Apple Translation languages…")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                } else {
                    HStack(spacing: 10) {
                        languageMenu(
                            title: "From",
                            selectionName: sourceLanguageName,
                            options: allLanguages,
                            selection: sourceLanguage
                        ) { option in
                            sourceLanguage = option.code
                        }

                        Button {
                            swapLanguages()
                        } label: {
                            Image(systemName: "arrow.left.arrow.right")
                                .font(.subheadline.weight(.semibold))
                                .frame(width: 42, height: 42)
                        }
                        .buttonStyle(.bordered)
                        .buttonBorderShape(.circle)
                        .disabled(
                            liveTranslation.isRunning ||
                                translation.isTranslating ||
                                isLoadingTargets ||
                                targetLanguages.isEmpty
                        )
                        .accessibilityLabel("Swap languages")

                        languageMenu(
                            title: "To",
                            selectionName: targetLanguageName,
                            options: targetLanguages,
                            selection: targetLanguage
                        ) { option in
                            targetLanguage = option.code
                        }
                    }
                }
            }
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
                        Text("\(sourceLanguageName) → \(targetLanguageName)")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                }

                Text("Only speech identified as \(sourceLanguageName) is translated. Speech in other languages is ignored, so your \(targetLanguageName) reply is not translated back.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)

                Button {
                    Task {
                        if liveTranslation.isRunning {
                            await stopLiveTranslation()
                        } else {
                            await startLiveTranslation()
                        }
                    }
                } label: {
                    HStack(spacing: 9) {
                        Image(systemName: liveTranslation.isRunning ? "stop.circle.fill" : "waveform.and.mic")
                        Text(liveTranslation.isRunning ? "Stop Live Translation" : "Start Live Translation")
                    }
                    .font(.headline)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.vertical, 3)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .disabled(
                    !liveTranslation.isRunning && (
                        translation.isTranslating ||
                            isLoadingLanguages ||
                            isLoadingTargets ||
                            targetLanguages.isEmpty
                    )
                )

                HStack(spacing: 9) {
                    if liveTranslation.isRunning {
                        ProgressView()
                            .controlSize(.small)
                    } else {
                        Image(systemName: "checkmark.circle")
                            .foregroundStyle(.secondary)
                    }
                    Text(liveTranslation.statusMessage)
                        .font(.footnote.weight(.medium))
                        .foregroundStyle(liveTranslation.isRunning ? .primary : .secondary)
                }

                if let inputRouteName = liveTranslation.inputRouteName,
                   liveTranslation.isRunning {
                    HStack(spacing: 8) {
                        Image(systemName: "mic")
                            .foregroundStyle(.secondary)
                        Text("Listening through \(inputRouteName)")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }

                if !liveTranslation.currentTranscript.isEmpty {
                    transcriptBlock(
                        label: "Hearing",
                        text: liveTranslation.currentTranscript,
                        emphasized: false
                    )
                }

                if !liveTranslation.lastSourceText.isEmpty {
                    transcriptBlock(
                        label: sourceLanguageName,
                        text: liveTranslation.lastSourceText,
                        emphasized: false
                    )
                }

                if !liveTranslation.lastTranslation.isEmpty {
                    transcriptBlock(
                        label: targetLanguageName,
                        text: liveTranslation.lastTranslation,
                        emphasized: true
                    )
                }

                if let liveStartError {
                    Label(liveStartError, systemImage: "exclamationmark.triangle.fill")
                        .font(.footnote)
                        .foregroundStyle(.red)
                        .fixedSize(horizontal: false, vertical: true)
                }

                if let error = liveTranslation.errorMessage {
                    Label(error, systemImage: "exclamationmark.triangle.fill")
                        .font(.footnote)
                        .foregroundStyle(.red)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    private var phraseCard: some View {
        TranslateCard {
            VStack(alignment: .leading, spacing: 12) {
                VStack(alignment: .leading, spacing: 3) {
                    Text("Translate a phrase")
                        .font(.headline)
                    Text("For typed or pasted text without starting the microphone.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }

                TextField(
                    "Type \(sourceLanguageName) text",
                    text: $phrase,
                    axis: .vertical
                )
                .lineLimit(3 ... 7)
                .padding(12)
                .background(
                    Color(uiColor: .secondarySystemGroupedBackground),
                    in: RoundedRectangle(cornerRadius: 12, style: .continuous)
                )
                .disabled(liveTranslation.isRunning || translation.isTranslating)

                Text("Enter text in \(sourceLanguageName), then tap Translate to \(targetLanguageName).")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                Button {
                    Task { await translatePhrase() }
                } label: {
                    HStack(spacing: 8) {
                        if translation.isTranslating && !liveTranslation.isRunning {
                            ProgressView()
                                .controlSize(.small)
                        } else {
                            Image(systemName: "translate")
                        }
                        Text(translation.isTranslating ? "Translating…" : "Translate to \(targetLanguageName)")
                    }
                    .font(.headline)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.vertical, 2)
                }
                .buttonStyle(.bordered)
                .controlSize(.large)
                .disabled(
                    liveTranslation.isRunning ||
                        translation.isTranslating ||
                        isLoadingTargets ||
                        phrase.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
                        targetLanguages.isEmpty
                )

                if translation.isTranslating,
                   !liveTranslation.isRunning,
                   let statusMessage = translation.statusMessage {
                    HStack(spacing: 9) {
                        ProgressView()
                            .controlSize(.small)
                        Text(statusMessage)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }

                if let phraseError {
                    Label(phraseError, systemImage: "exclamationmark.triangle.fill")
                        .font(.footnote)
                        .foregroundStyle(.red)
                        .fixedSize(horizontal: false, vertical: true)
                }

                if let phraseResult {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(targetLanguageName)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.secondary)
                        Text(phraseResult.translatedText)
                            .font(.title3)
                            .textSelection(.enabled)
                            .frame(maxWidth: .infinity, alignment: .leading)

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
                        .buttonStyle(.bordered)
                        .disabled(
                            app.isGenerating ||
                                app.isTranscribing ||
                                app.isStoppingTranscription ||
                                app.isGlassesAssistantAudioActive ||
                                app.speechOutput.isSpeaking ||
                                liveTranslation.isRunning
                        )
                    }
                    .padding(12)
                    .background(
                        .thinMaterial,
                        in: RoundedRectangle(cornerRadius: 12, style: .continuous)
                    )
                }
            }
        }
    }

    private func languageMenu(
        title: String,
        selectionName: String,
        options: [TranslationLanguageOption],
        selection: String,
        onSelect: @escaping (TranslationLanguageOption) -> Void
    ) -> some View {
        Menu {
            ForEach(options) { option in
                Button {
                    onSelect(option)
                } label: {
                    if option.code == selection {
                        Label(option.name, systemImage: "checkmark")
                    } else {
                        Text(option.name)
                    }
                }
            }
        } label: {
            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                HStack(spacing: 5) {
                    Text(selectionName)
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(1)
                    Spacer(minLength: 2)
                    Image(systemName: "chevron.up.chevron.down")
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(.secondary)
                }
            }
            .padding(.horizontal, 12)
            .frame(maxWidth: .infinity, minHeight: 54, alignment: .leading)
            .background(
                Color(uiColor: .secondarySystemGroupedBackground),
                in: RoundedRectangle(cornerRadius: 12, style: .continuous)
            )
        }
        .buttonStyle(.plain)
        .disabled(liveTranslation.isRunning || translation.isTranslating || options.isEmpty)
    }

    private func transcriptBlock(
        label: String,
        text: String,
        emphasized: Bool
    ) -> some View {
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

    private var sourceLanguageName: String {
        allLanguages.first(where: { $0.code == sourceLanguage })?.name
            ?? Locale.current.localizedString(forIdentifier: sourceLanguage)
            ?? sourceLanguage
    }

    private var targetLanguageName: String {
        targetLanguages.first(where: { $0.code == targetLanguage })?.name
            ?? allLanguages.first(where: { $0.code == targetLanguage })?.name
            ?? Locale.current.localizedString(forIdentifier: targetLanguage)
            ?? targetLanguage
    }

    private func loadLanguages() async {
        isLoadingLanguages = true
        languageError = nil

        let supported = await translation.supportedLanguages()
        guard !Task.isCancelled else { return }
        let options = makeOptions(from: supported)
        guard !options.isEmpty else {
            allLanguages = []
            targetLanguages = []
            languageError = "Apple Translation did not report any supported languages on this iPhone."
            isLoadingLanguages = false
            return
        }

        allLanguages = options
        if !options.contains(where: { $0.code == sourceLanguage }) {
            sourceLanguage = preferredOption(code: "hi", in: options)?.code
                ?? preferredOption(code: "en", in: options)?.code
                ?? options[0].code
        }

        await refreshTargets()
        isLoadingLanguages = false
    }

    private func refreshTargets() async {
        guard !allLanguages.isEmpty else { return }
        let requestedSource = sourceLanguage
        isLoadingTargets = true
        defer {
            if sourceLanguage == requestedSource {
                isLoadingTargets = false
            }
        }

        let targets = await translation.supportedTargets(
            from: Locale.Language(identifier: requestedSource)
        )
        guard !Task.isCancelled, sourceLanguage == requestedSource else { return }

        let options = makeOptions(from: targets)
        targetLanguages = options
        guard !options.isEmpty else {
            languageError = "Apple Translation did not report a valid target language for \(sourceLanguageName)."
            return
        }

        languageError = nil
        if !options.contains(where: { $0.code == targetLanguage }) {
            targetLanguage = preferredOption(code: "en", in: options)?.code
                ?? preferredOption(code: "hi", in: options)?.code
                ?? options[0].code
        }
    }

    private func makeOptions(
        from languages: [Locale.Language]
    ) -> [TranslationLanguageOption] {
        var seen = Set<String>()
        return languages
            .map(TranslationLanguageOption.init)
            .filter { seen.insert($0.code).inserted }
            .sorted {
                $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending
            }
    }

    private func preferredOption(
        code: String,
        in options: [TranslationLanguageOption]
    ) -> TranslationLanguageOption? {
        let requested = code.lowercased()
        let requestedBase = requested.split(separator: "-").first
        return options.first { option in
            let candidate = option.code.lowercased()
            return candidate == requested || candidate.split(separator: "-").first == requestedBase
        }
    }

    private func swapLanguages() {
        guard !liveTranslation.isRunning,
              !translation.isTranslating,
              !targetLanguage.isEmpty else { return }
        let previousSource = sourceLanguage
        sourceLanguage = targetLanguage
        targetLanguage = previousSource
    }

    private func startLiveTranslation() async {
        liveStartError = nil
        guard sourceLanguage != targetLanguage else {
            liveStartError = "Choose two different languages."
            return
        }
        guard !app.isGlassesAssistantAudioActive else {
            liveStartError = "Finish the current glasses voice turn before starting Live Translation."
            return
        }

        phraseError = nil
        phraseResult = nil
        app.cancelResponse()
        app.speechOutput.stop()
        await app.stopTranscription()

        let started = await liveTranslation.start(
            sourceLanguageCode: sourceLanguage,
            targetLanguageCode: targetLanguage,
            translation: translation,
            speechOutput: app.speechOutput
        )
        if !started {
        }
    }

    private func stopLiveTranslation() async {
        if liveTranslation.isRunning {
            await liveTranslation.stop()
        }
    }

    private func translatePhrase() async {
        let value = phrase.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return }
        guard sourceLanguage != targetLanguage else {
            phraseError = "Choose two different languages."
            return
        }

        let requestedSource = sourceLanguage
        let requestedTarget = targetLanguage
        phraseResult = nil
        phraseError = nil
        do {
            let result = try await translation.translate(
                value,
                from: Locale.Language(identifier: requestedSource),
                to: Locale.Language(identifier: requestedTarget)
            )
            guard sourceLanguage == requestedSource,
                  targetLanguage == requestedTarget else { return }
            phraseResult = result
        } catch is CancellationError {
            return
        } catch {
            guard sourceLanguage == requestedSource,
                  targetLanguage == requestedTarget else { return }
            phraseError = error.localizedDescription
        }
    }

    private func resetPhraseResult() {
        phraseResult = nil
        phraseError = nil
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
                Color(uiColor: .systemBackground),
                in: RoundedRectangle(cornerRadius: 18, style: .continuous)
            )
            .overlay {
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .stroke(Color.primary.opacity(0.06), lineWidth: 1)
            }
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
                Text("Native in-app translation requires iOS 18 or later.")
            }
        }
    }
}
