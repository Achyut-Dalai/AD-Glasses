import SwiftUI

@main
struct ADGlassesApp: App {
    @StateObject private var appModel = AppModel()
    @StateObject private var glassesManager = GlassesManager()

    var body: some Scene {
        WindowGroup {
            HomeView()
                .environmentObject(appModel)
                .environmentObject(glassesManager)
        }
    }
}
