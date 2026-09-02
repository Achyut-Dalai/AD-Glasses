import SwiftUI
import Translation
import UIKit

struct TranslationView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Group {
#if compiler(>=6.2)
                if #available(iOS 26.0, *) {
                    LiveTranslateExperience()
                } else {
                    translationUnavailable
                }
#else
                translationUnavailable
#endif
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

    private var translationUnavailable: some View {
        ContentUnavailableView(
            "Live Translation unavailable",
            systemImage: "waveform.and.mic",
            description: Text("Live Translation requires iOS 26 or later with Apple SpeechAnalyzer support.")
        )
    }
}

@available(iOS 26.0, *)
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

#if compiler(>=6.2)
@available(iOS 26.0, *)
private struct LiveTranslateExperience: View {
    @EnvironmentObject private var translation: NativeTranslationController
    @EnvironmentObject private var app: AppModel

    @StateObject private var liveTranslation = LiveTranslationController()

    @AppStorage("translation.sourceLanguage.v1") private var sourceLanguage = "hi"
    @AppStorage("translation.targetLanguage.v1") private var targetLanguage = "en"

    @State private var allTranslationLanguages = [TranslationLanguageOption]()
    @State private var sourceLanguages = [TranslationLanguageOption]()
    @State private var targetLanguages = [TranslationLanguageOption]()
    @State private var installedSpeechLanguageBases = Set<String>()
    @State private var isLoadingLanguages = true
    @State private var isLoadingTargets = false
    @State private var languageError: String?
    @State private var liveStartError: String?

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                languageCard
                liveTranslationCard
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
            Task { await refreshTargets() }
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
                        Text("The From list only includes languages Apple SpeechAnalyzer reports as transcribable on this iPhone.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
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
                        .fixedSize(horizontal: false, vertical: true)
                } else if sourceLanguages.isEmpty {
                    HStack(spacing: 10) {
                        ProgressView()
                        Text("Checking Apple speech and translation languages…")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                } else {
                    HStack(spacing: 10) {
                        languageMenu(
                            title: "From",
                            selectionName: sourceLanguageName,
                            options: sourceLanguages,
                            selection: sourceLanguage
                        ) { option in
                            sourceLanguage = option.code
                        }

                        languageMenu(
                            title: "To",
                            selectionName: targetLanguageName,
                            options: targetLanguages,
                            selection: targetLanguage
                        ) { option in
                            targetLanguage = option.code
                        }
                    }

                    HStack(spacing: 8) {
                        Image(systemName: sourceSpeechModelInstalled ? "checkmark.circle.fill" : "arrow.down.circle")
                            .foregroundStyle(sourceSpeechModelInstalled ? .green : .secondary)
                        Text(sourceSpeechModelInstalled
                             ? "SpeechAnalyzer model installed"
                             : "SpeechAnalyzer model will be downloaded by Apple when Live Translation starts")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
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

                Text("AD listens for \(sourceLanguageName), translates each completed utterance to \(targetLanguageName), speaks the result, then resumes listening.")
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
                            sourceLanguages.isEmpty ||
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
        sourceLanguages.first(where: { $0.code == sourceLanguage })?.name
            ?? Locale.current.localizedString(forIdentifier: sourceLanguage)
            ?? sourceLanguage
    }

    private var targetLanguageName: String {
        targetLanguages.first(where: { $0.code == targetLanguage })?.name
            ?? allTranslationLanguages.first(where: { $0.code == targetLanguage })?.name
            ?? Locale.current.localizedString(forIdentifier: targetLanguage)
            ?? targetLanguage
    }

    private var sourceSpeechModelInstalled: Bool {
        installedSpeechLanguageBases.contains(languageBase(sourceLanguage))
    }

    private func loadLanguages() async {
        isLoadingLanguages = true
        languageError = nil

        async let translationSupported = translation.supportedLanguages()
        async let speechSupported = SpeechAnalyzerTranscriber.supportedSpeechLocales()
        async let speechInstalled = SpeechAnalyzerTranscriber.installedSpeechLocales()

        let translationOptions = makeOptions(from: await translationSupported)
        let supportedSpeechBases = Set((await speechSupported).map { languageBase($0.identifier) })
        installedSpeechLanguageBases = Set((await speechInstalled).map { languageBase($0.identifier) })

        guard !Task.isCancelled else { return }
        allTranslationLanguages = translationOptions
        sourceLanguages = translationOptions.filter {
            supportedSpeechBases.contains(languageBase($0.code))
        }

        guard !sourceLanguages.isEmpty else {
            targetLanguages = []
            languageError = "Apple SpeechTranscriber did not report any languages that can also be used by Apple Translation on this iPhone."
            isLoadingLanguages = false
            return
        }

        if !sourceLanguages.contains(where: { $0.code == sourceLanguage }) {
            sourceLanguage = preferredInstalledSource(code: "hi")?.code
                ?? preferredInstalledSource(code: "en")?.code
                ?? sourceLanguages.first(where: { installedSpeechLanguageBases.contains(languageBase($0.code)) })?.code
                ?? sourceLanguages[0].code
        }

        await refreshTargets()
        isLoadingLanguages = false
    }

    private func refreshTargets() async {
        guard sourceLanguages.contains(where: { $0.code == sourceLanguage }) else { return }
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
            languageError = "Apple Translation did not report a target language for \(sourceLanguageName)."
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

    private func preferredInstalledSource(code: String) -> TranslationLanguageOption? {
        preferredOption(code: code, in: sourceLanguages).flatMap { option in
            installedSpeechLanguageBases.contains(languageBase(option.code)) ? option : nil
        }
    }

    private func preferredOption(
        code: String,
        in options: [TranslationLanguageOption]
    ) -> TranslationLanguageOption? {
        let requestedBase = languageBase(code)
        return options.first { languageBase($0.code) == requestedBase }
    }

    private func startLiveTranslation() async {
        liveStartError = nil
        guard languageBase(sourceLanguage) != languageBase(targetLanguage) else {
            liveStartError = "Choose two different languages."
            return
        }
        guard !app.isGlassesAssistantAudioActive else {
            liveStartError = "Finish the current glasses voice turn before starting Live Translation."
            return
        }

        app.cancelResponse()
        app.speechOutput.stop()
        await app.stopTranscription()

        _ = await liveTranslation.start(
            sourceLanguageCode: sourceLanguage,
            targetLanguageCode: targetLanguage,
            translation: translation,
            speechOutput: app.speechOutput
        )
    }

    private func stopLiveTranslation() async {
        if liveTranslation.isRunning {
            await liveTranslation.stop()
        }
    }

    private func languageBase(_ code: String) -> String {
        let normalized = code
            .replacingOccurrences(of: "_", with: "-")
            .lowercased()
        return normalized.split(separator: "-").first.map(String.init) ?? normalized
    }
}
#endif

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
