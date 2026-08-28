import SwiftUI

@main
struct ADGlassesApp: App {
    @StateObject private var appModel = AppModel()
    @StateObject private var glassesManager = GlassesManager(
        providers: [
            HeyCyanGlassesProvider(),
            MetaGlassesProvider()
        ]
    )

    var body: some Scene {
        WindowGroup {
            AppRootView()
                .environmentObject(appModel)
                .environmentObject(glassesManager)
        }
    }
}
