import SwiftUI
import AD GlassesShared

@main
struct AD GlassesKMPHostApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView()
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        if let destination = ProcessInfo.processInfo.environment["AD Glasses_SCREENSHOT_DESTINATION"],
           !destination.isEmpty {
            return MainViewControllerKt.MainViewControllerForDestination(destination: destination)
        }
        return MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
