import SwiftUI
import CyanBridgeShared

@main
struct CyanBridgeKMPHostApp: App {
    var body: some Scene {
        WindowGroup {
            CyanBridgeKMPHomeView()
        }
    }
}

private struct CyanBridgeKMPHomeView: View {
    private let shared = CyanBridgeSharedBootstrap.shared

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Text(shared.applicationName())
                        .font(.largeTitle.bold())
                        .foregroundStyle(.cyan)

                    Text("Kotlin Multiplatform iOS foundation")
                        .font(.title3.weight(.semibold))
                        .fixedSize(horizontal: false, vertical: true)

                    Text("This host reads app defaults from the shared Kotlin framework without depending on the limited vendor transport.")
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)

                    Divider()

                    SharedValueRow(label: "Accent profile", value: shared.defaultAccentProfileId())
                    SharedValueRow(label: "Initial destination", value: shared.defaultDestinationId())

                    Text("Shared meeting-summary formatting")
                        .font(.headline)
                        .fixedSize(horizontal: false, vertical: true)

                    Text(shared.meetingSummaryPreviewMarkdown())
                        .font(.caption.monospaced())
                        .foregroundStyle(.secondary)
                        .lineLimit(12)
                        .textSelection(.enabled)

                    Label("Glasses transport is not enabled yet", systemImage: "exclamationmark.triangle")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(.orange)

                    Text("Device connection and media transfer will be added behind the shared bridge contract after the vendor SDK is validated on physical hardware.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(24)
            }
            .navigationTitle("CyanBridge")
        }
    }
}

private struct SharedValueRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack {
            Text(label)
                .foregroundStyle(.secondary)
            Spacer()
            Text(value)
                .fontWeight(.semibold)
        }
    }
}
