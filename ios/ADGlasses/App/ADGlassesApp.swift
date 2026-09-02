import SwiftUI
import UIKit

@MainActor
final class AppOrientationController {
    static let shared = AppOrientationController()

    private(set) var supportedOrientations: UIInterfaceOrientationMask = .portrait

    private init() {}

    func usePortraitOnly() {
        update(to: .portrait)
    }

    func allowMediaOrientation() {
        update(to: .allButUpsideDown)
    }

    private func update(to orientations: UIInterfaceOrientationMask) {
        supportedOrientations = orientations
        for scene in UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }) {
            scene.requestGeometryUpdate(
                .iOS(interfaceOrientations: orientations)
            )
        }
    }
}

final class ADGlassesAppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        supportedInterfaceOrientationsFor window: UIWindow?
    ) -> UIInterfaceOrientationMask {
        AppOrientationController.shared.supportedOrientations
    }
}

@main
struct ADGlassesApp: App {
    @UIApplicationDelegateAdaptor(ADGlassesAppDelegate.self) private var appDelegate
    @Environment(\.scenePhase) private var scenePhase

    @StateObject private var appModel: AppModel
    @StateObject private var libraryModel: LibraryModel
    @StateObject private var glassesManager: GlassesManager

    init() {
        let appModel = AppModel()
        let glassesManager = GlassesManager(providers: [
            HeyCyanGlassesProvider(),
            MetaGlassesProvider()
        ])
        appModel.attach(to: glassesManager)

        _appModel = StateObject(wrappedValue: appModel)
        _libraryModel = StateObject(wrappedValue: LibraryModel())
        _glassesManager = StateObject(wrappedValue: glassesManager)
    }

    var body: some Scene {
        WindowGroup {
            if #available(iOS 18.0, *) {
                NativeTranslationHost {
                    appRoot
                }
            } else {
                appRoot
            }
        }
    }

    private var appRoot: some View {
        AppRootView()
            .environmentObject(appModel)
            .environmentObject(glassesManager)
            .environmentObject(libraryModel)
            .task {
                appModel.setApplicationActive(scenePhase == .active)
            }
            .onChange(of: scenePhase) { _, phase in
                appModel.setApplicationActive(phase == .active)
            }
    }
}
