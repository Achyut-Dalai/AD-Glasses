import Combine
import Foundation
import Translation

@available(iOS 18.0, *)
@MainActor
final class NativeTranslationController: ObservableObject {
    private struct PendingRequest {
        let id: UUID
        let text: String
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
            statusMessage = "Preparing Apple Translation…"
            try await session.prepareTranslation()
            try Task.checkCancellation()
            statusMessage = "Translating…"
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
