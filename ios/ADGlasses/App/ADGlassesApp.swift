import SwiftUI

@main
struct ADGlassesApp: App {
    @Environment(\.scenePhase) private var scenePhase

    @StateObject private var appModel: AppModel
    @StateObject private var libraryModel: LibraryModel
    @StateObject private var glassesManager: GlassesManager
    @StateObject private var phoneVoiceActivation: PhoneVoiceActivationController

    init() {
        let appModel = AppModel()
        let glassesManager = GlassesManager(providers: [
            HeyCyanGlassesProvider(),
            MetaGlassesProvider()
        ])
        appModel.attach(to: glassesManager)
        let phoneVoiceActivation = PhoneVoiceActivationController(
            service: PorcupinePhoneWakeWordService(),
            glasses: glassesManager,
            app: appModel
        )

        _appModel = StateObject(wrappedValue: appModel)
        _libraryModel = StateObject(wrappedValue: LibraryModel())
        _glassesManager = StateObject(wrappedValue: glassesManager)
        _phoneVoiceActivation = StateObject(wrappedValue: phoneVoiceActivation)
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
            .environmentObject(phoneVoiceActivation)
            .task {
                appModel.setApplicationActive(scenePhase == .active)
                phoneVoiceActivation.setApplicationActive(scenePhase == .active)
            }
            .onChange(of: scenePhase) { _, phase in
                appModel.setApplicationActive(phase == .active)
                phoneVoiceActivation.setApplicationActive(phase == .active)
            }
    }
}
