@preconcurrency import AVFoundation
import Combine
import Foundation
import NaturalLanguage
import Translation

@available(iOS 18.0, *)
@MainActor
final class NativeTranslationController: ObservableObject {
    private struct PendingRequest {
        let id: UUID
        let text: String
        let shouldPrepareTranslation: Bool
        let continuation: CheckedContinuation<TextTranslationResult, Error>
    }

    @Published private(set) var configuration: TranslationSession.Configuration?
    @Published private(set) var isTranslating = false
    @Published private(set) var statusMessage: String?

    private var pendingRequest: PendingRequest?

    func translate(
        _ text: String,
        from sourceLanguage: Locale.Language? = nil,
        to targetLanguage: Locale.Language
    ) async throws -> TextTranslationResult {
        guard pendingRequest == nil, !isTranslating else {
            throw TextTranslationError.operationInProgress
        }
        let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else {
            throw TranslationError.nothingToTranslate
        }

        isTranslating = true
        statusMessage = "Checking language support…"
        do {
            try await preflight(value, from: sourceLanguage, to: targetLanguage)
        } catch is CancellationError {
            isTranslating = false
            statusMessage = nil
            throw CancellationError()
        } catch {
            isTranslating = false
            statusMessage = nil
            throw error
        }

        let requestID = UUID()
        return try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                pendingRequest = PendingRequest(
                    id: requestID,
                    text: value,
                    shouldPrepareTranslation: sourceLanguage != nil,
                    continuation: continuation
                )

                let nextConfiguration: TranslationSession.Configuration
                if #available(iOS 26.4, *) {
                    nextConfiguration = TranslationSession.Configuration(
                        source: sourceLanguage,
                        target: targetLanguage,
                        preferredStrategy: .lowLatency
                    )
                } else {
                    nextConfiguration = TranslationSession.Configuration(
                        source: sourceLanguage,
                        target: targetLanguage
                    )
                }

                if configuration == nextConfiguration {
                    configuration?.invalidate()
                } else {
                    configuration = nextConfiguration
                }
            }
        } onCancel: {
            Task { @MainActor [weak self] in
                self?.finish(id: requestID, result: .failure(CancellationError()))
            }
        }
    }

    /// Called only by the root SwiftUI `translationTask`; it is the Apple-owned model/download
    /// session for the current language pair.
    func performPendingRequest(using session: TranslationSession) async {
        guard let pendingRequest else { return }
        do {
            // `prepareTranslation()` requires a concrete source language. When the source is
            // automatic (`nil`), `translate(_:)` must receive the sample text first so Apple's
            // Translation framework can identify the language and request any required assets.
            if pendingRequest.shouldPrepareTranslation {
                statusMessage = "Preparing Apple Translation…"
                try await session.prepareTranslation()
                try Task.checkCancellation()
            }

            statusMessage = pendingRequest.shouldPrepareTranslation
                ? "Translating…"
                : "Detecting language and translating…"
            let response = try await session.translate(pendingRequest.text)
            finish(
                id: pendingRequest.id,
                result: .success(
                    TextTranslationResult(
                        sourceText: response.sourceText,
                        translatedText: response.targetText,
                        sourceLanguage: response.sourceLanguage.minimalIdentifier,
                        targetLanguage: response.targetLanguage.minimalIdentifier
                    )
                )
            )
        } catch is CancellationError {
            finish(id: pendingRequest.id, result: .failure(CancellationError()))
        } catch let error as TextTranslationError {
            finish(id: pendingRequest.id, result: .failure(error))
        } catch {
            finish(
                id: pendingRequest.id,
                result: .failure(TextTranslationError.translationFailed(error.localizedDescription))
            )
        }
    }

    func availability(
        from sourceLanguage: Locale.Language,
        to targetLanguage: Locale.Language
    ) async -> LanguageAvailability.Status {
        await LanguageAvailability().status(from: sourceLanguage, to: targetLanguage)
    }

    private func preflight(
        _ text: String,
        from sourceLanguage: Locale.Language?,
        to targetLanguage: Locale.Language
    ) async throws {
        let availability = LanguageAvailability()
        let status: LanguageAvailability.Status

        if let sourceLanguage {
            status = await availability.status(from: sourceLanguage, to: targetLanguage)
        } else {
            do {
                status = try await availability.status(for: text, to: targetLanguage)
            } catch is CancellationError {
                throw CancellationError()
            } catch {
                throw TextTranslationError.sourceLanguageUndetermined
            }
        }

        switch status {
        case .installed:
            statusMessage = "Translating…"
        case .supported:
            statusMessage = "Preparing language download…"
        case .unsupported:
            throw TextTranslationError.unsupportedLanguagePair
        @unknown default:
            throw TextTranslationError.unsupportedLanguagePair
        }
    }

    private func finish(
        id: UUID,
        result: Result<TextTranslationResult, Error>
    ) {
        guard let request = pendingRequest, request.id == id else { return }
        pendingRequest = nil
        isTranslating = false
        statusMessage = nil
        request.continuation.resume(with: result)
    }
}

@available(iOS 18.0, *)
@MainActor
final class LiveTranslationController: ObservableObject {
    @Published private(set) var isRunning = false
    @Published private(set) var statusMessage = "Not running"
    @Published private(set) var currentTranscript = ""
    @Published private(set) var lastSourceText = ""
    @Published private(set) var lastTranslation = ""
    @Published private(set) var inputRouteName: String?
    @Published private(set) var errorMessage: String?

    private var transcriber: (any SpeechTranscribing)?
    private weak var translation: NativeTranslationController?
    private weak var speechOutput: SpeechOutputController?
    private var sourceLanguageCode = ""
    private var targetLanguageCode = ""
    private var finalizeTask: Task<Void, Never>?
    private var isProcessingTurn = false

    private let endOfUtteranceDelay: Duration = .milliseconds(1400)

    @discardableResult
    func start(
        sourceLanguageCode: String,
        targetLanguageCode: String,
        translation: NativeTranslationController,
        speechOutput: SpeechOutputController
    ) async -> Bool {
        guard !isRunning else { return true }

        errorMessage = nil
        currentTranscript = ""
        lastSourceText = ""
        lastTranslation = ""
        inputRouteName = nil

        guard languageBase(sourceLanguageCode) != languageBase(targetLanguageCode) else {
            errorMessage = "Choose two different languages for Live Translation."
            return false
        }
        guard !translation.isTranslating else {
            errorMessage = TextTranslationError.operationInProgress.localizedDescription
            return false
        }

        let sourceLanguage = Locale.Language(identifier: sourceLanguageCode)
        let targetLanguage = Locale.Language(identifier: targetLanguageCode)
        let availability = await translation.availability(
            from: sourceLanguage,
            to: targetLanguage
        )
        guard availability != .unsupported else {
            errorMessage = TextTranslationError.unsupportedLanguagePair.localizedDescription
            return false
        }
        guard speechOutput.preferredVoice(languageCode: targetLanguageCode) != nil else {
            errorMessage = SpeechOutputError.noVoiceAvailable.localizedDescription
            return false
        }

        let transcriber = AppleSpeechTranscriber.make(
            locale: Locale(identifier: sourceLanguageCode)
        )
        transcriber.onUpdate = { [weak self] snapshot in
            self?.handleSpeechUpdate(snapshot)
        }
        transcriber.onError = { [weak self] error in
            guard let self, isRunning else { return }
            errorMessage = error.localizedDescription
        }

        self.transcriber = transcriber
        self.translation = translation
        self.speechOutput = speechOutput
        self.sourceLanguageCode = sourceLanguageCode
        self.targetLanguageCode = targetLanguageCode
        statusMessage = "Starting \(languageName(sourceLanguageCode)) listening…"
        isRunning = true

        do {
            try await transcriber.start()
            updateInputRoute()
            statusMessage = listeningStatus
            return true
        } catch is CancellationError {
            await transcriber.stop()
            resetSessionState()
            return false
        } catch {
            await transcriber.stop()
            errorMessage = error.localizedDescription
            resetSessionState(keepError: true)
            return false
        }
    }

    func stop() async {
        finalizeTask?.cancel()
        finalizeTask = nil
        isRunning = false
        isProcessingTurn = false
        speechOutput?.stop()
        if let transcriber {
            transcriber.onUpdate = nil
            transcriber.onError = nil
            await transcriber.stop()
        }
        resetSessionState(keepError: true)
        statusMessage = "Not running"
    }

    private func handleSpeechUpdate(_ snapshot: SpeechTranscriptionSnapshot) {
        guard isRunning, snapshot.isRunning, !isProcessingTurn else { return }
        let text = snapshot.transcript.trimmingCharacters(in: .whitespacesAndNewlines)
        currentTranscript = text
        guard !text.isEmpty else { return }

        finalizeTask?.cancel()
        finalizeTask = Task { [weak self] in
            do {
                try await Task.sleep(for: self?.endOfUtteranceDelay ?? .milliseconds(1400))
            } catch {
                return
            }
            await self?.processTurn(candidate: text)
        }
    }

    private func processTurn(candidate: String) async {
        guard isRunning, !isProcessingTurn,
              let transcriber,
              let translation,
              let speechOutput else { return }

        isProcessingTurn = true
        defer { isProcessingTurn = false }

        await transcriber.stop()
        guard isRunning, !Task.isCancelled else { return }

        let finalized = transcriber.snapshot.transcript
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let sourceText = finalized.isEmpty ? candidate : finalized
        currentTranscript = ""
        guard !sourceText.isEmpty else {
            await resumeListening()
            return
        }

        if !isLikelySourceLanguage(sourceText) {
            statusMessage = "Heard a different language — ignored"
            do {
                try await Task.sleep(for: .milliseconds(350))
            } catch {
                return
            }
            await resumeListening()
            return
        }

        lastSourceText = sourceText
        statusMessage = "Translating \(languageName(sourceLanguageCode)) → \(languageName(targetLanguageCode))…"
        do {
            let result = try await translation.translate(
                sourceText,
                from: Locale.Language(identifier: sourceLanguageCode),
                to: Locale.Language(identifier: targetLanguageCode)
            )
            try Task.checkCancellation()
            guard isRunning else { return }

            lastTranslation = result.translatedText
            statusMessage = "Speaking \(languageName(targetLanguageCode))…"
            try speechOutput.speak(
                result.translatedText,
                languageCode: result.targetLanguage
            )

            while speechOutput.isSpeaking {
                try Task.checkCancellation()
                try await Task.sleep(for: .milliseconds(80))
            }
        } catch is CancellationError {
            return
        } catch {
            errorMessage = error.localizedDescription
        }

        guard isRunning, !Task.isCancelled else { return }
        await resumeListening()
    }

    private func resumeListening() async {
        guard isRunning, let transcriber else { return }
        transcriber.resetTranscript()
        currentTranscript = ""
        do {
            try await transcriber.start()
            updateInputRoute()
            statusMessage = listeningStatus
        } catch is CancellationError {
            return
        } catch {
            errorMessage = error.localizedDescription
            isRunning = false
            transcriber.onUpdate = nil
            transcriber.onError = nil
            await transcriber.stop()
            resetSessionState(keepError: true)
        }
    }

    /// The speech recognizer itself is fixed to the selected source locale. NaturalLanguage is a
    /// second, conservative guard: it rejects only when another language is strongly identified,
    /// so short or ambiguous source-language phrases are not thrown away unnecessarily.
    private func isLikelySourceLanguage(_ text: String) -> Bool {
        guard text.count >= 6 else { return true }

        let recognizer = NLLanguageRecognizer()
        recognizer.processString(text)
        let hypotheses = recognizer.languageHypotheses(withMaximum: 3)
        guard !hypotheses.isEmpty else { return true }

        let expectedBase = languageBase(sourceLanguageCode)
        let expectedScore = hypotheses
            .filter { languageBase($0.key.rawValue) == expectedBase }
            .map(\.value)
            .max() ?? 0
        if expectedScore >= 0.20 { return true }

        let strongestOther = hypotheses
            .filter { languageBase($0.key.rawValue) != expectedBase }
            .map(\.value)
            .max() ?? 0
        return strongestOther < 0.75
    }

    private var listeningStatus: String {
        "Listening for \(languageName(sourceLanguageCode))…"
    }

    private func updateInputRoute() {
        inputRouteName = AVAudioSession.sharedInstance().currentRoute.inputs.first?.portName
            ?? "iPhone microphone"
    }

    private func languageName(_ code: String) -> String {
        let base = languageBase(code)
        return Locale.current.localizedString(forLanguageCode: base)?.capitalized ?? code
    }

    private func languageBase(_ code: String) -> String {
        code.lowercased().split(separator: "-").first.map(String.init) ?? code.lowercased()
    }

    private func resetSessionState(keepError: Bool = false) {
        finalizeTask?.cancel()
        finalizeTask = nil
        transcriber = nil
        translation = nil
        speechOutput = nil
        sourceLanguageCode = ""
        targetLanguageCode = ""
        currentTranscript = ""
        inputRouteName = nil
        isRunning = false
        isProcessingTurn = false
        if !keepError {
            errorMessage = nil
        }
    }
}
