import SwiftUI
import Translation

@available(iOS 18.0, *)
struct NativeTranslationHost<Content: View>: View {
    @StateObject private var controller = NativeTranslationController()
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
    }
}
