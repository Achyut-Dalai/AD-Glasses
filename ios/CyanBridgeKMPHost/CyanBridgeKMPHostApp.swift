import SwiftUI
import CyanBridgeShared

@main
struct CyanBridgeKMPHostApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView()
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        if let destination = ProcessInfo.processInfo.environment["CYANBRIDGE_SCREENSHOT_DESTINATION"],
           !destination.isEmpty {
            return MainViewControllerKt.MainViewControllerForDestination(destination: destination)
        }
        return MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
