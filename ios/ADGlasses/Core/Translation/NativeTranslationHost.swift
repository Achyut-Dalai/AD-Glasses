import SwiftUI
import Translation

struct NativeTranslationHost<Content: View>: View {
    @StateObject private var controller = NativeTranslationController()
    @StateObject private var liveTranslation = LiveTranslationController()
    private let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        content
            .environmentObject(controller)
            .translationTask(controller.configuration) { session in
                await controller.performPendingRequest(using: session)
            }
            .task { [weak controller, weak liveTranslation] in
                guard let controller, let liveTranslation else { return }
                AssistantTranslationBridge.shared.install(
                    translate: { [weak controller] text, sourceLanguageCode, targetLanguageCode in
                        guard let controller else { throw TextTranslationError.hostUnavailable }
                        return try await controller.translate(
                            text,
                            from: sourceLanguageCode.map { Locale.Language(identifier: $0) },
                            to: Locale.Language(identifier: targetLanguageCode)
                        )
                    },
                    startLive: { [weak controller, weak liveTranslation] sourceLanguageCode, targetLanguageCode, speechOutput in
                        guard let controller, let liveTranslation else { return false }
                        return await liveTranslation.start(
                            sourceLanguageCode: sourceLanguageCode,
                            targetLanguageCode: targetLanguageCode,
                            translation: controller,
                            speechOutput: speechOutput
                        )
                    },
                    stopLive: { [weak liveTranslation] in
                        await liveTranslation?.stop()
                    },
                    isLiveRunning: { [weak liveTranslation] in
                        liveTranslation?.isRunning ?? false
                    }
                )
            }
            .onDisappear {
                Task { @MainActor in
                    await AssistantTranslationBridge.shared.stopLive()
                    AssistantTranslationBridge.shared.clear()
                }
            }
    }
}
