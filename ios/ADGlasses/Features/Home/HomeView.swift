import SwiftUI

struct HomeView: View {
    @EnvironmentObject private var app: AppModel
    @EnvironmentObject private var glasses: GlassesManager

    var body: some View {
        NavigationStack {
            List {
                speechSection
                glassesSection
                supportSection
            }
            .navigationTitle("AD Glasses")
        }
    }

    private var speechSection: some View {
        Section("Native speech") {
            LabeledContent("Engine", value: app.speechEngineName)
            LabeledContent("Status", value: app.isTranscribing ? "Listening" : "Idle")

            if app.transcript.isEmpty {
                Text("Start listening to test Apple's native speech-to-text pipeline.")
                    .foregroundStyle(.secondary)
            } else {
                Text(app.transcript)
                    .textSelection(.enabled)
            }

            HStack {
                Button(app.isTranscribing ? "Stop" : "Start listening") {
                    Task { await app.toggleTranscription() }
                }
                .buttonStyle(.borderedProminent)

                if !app.transcript.isEmpty {
                    Button("Clear") { app.clearTranscript() }
                }
            }

            if let speechError = app.speechError {
                Text(speechError)
                    .font(.footnote)
                    .foregroundStyle(.red)
            }
        }
    }

    private var glassesSection: some View {
        Section("HeyCyan") {
            LabeledContent("Support", value: "Primary")
            LabeledContent("Connection", value: glasses.connectionState.label)

            Text("Native CoreBluetooth transport. Until verified HeyCyan service identifiers are added, scanning intentionally shows nearby BLE candidates and sends no vendor commands.")
                .font(.footnote)
                .foregroundStyle(.secondary)

            HStack {
                Button("Scan") {
                    Task { await glasses.scanHeyCyan() }
                }
                .disabled(glasses.connectionState == .scanning)

                if case .connected = glasses.connectionState {
                    Button("Disconnect") {
                        Task { await glasses.disconnect() }
                    }
                }
            }

            ForEach(glasses.devices) { device in
                Button {
                    Task { await glasses.connect(to: device) }
                } label: {
                    HStack {
                        VStack(alignment: .leading) {
                            Text(device.name)
                            Text(device.id.uuidString)
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        if let signal = device.signalStrength {
                            Text("\(signal) dBm")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                .buttonStyle(.plain)
            }

            if let error = glasses.errorMessage {
                Text(error)
                    .font(.footnote)
                    .foregroundStyle(.red)
            }
        }
    }

    private var supportSection: some View {
        Section("Glasses support") {
            ForEach(Array(glasses.supportSummary.enumerated()), id: \.offset) { _, item in
                LabeledContent(item.name, value: item.level.rawValue)
            }
            Text("Meta is only an experimental extension point. No Meta SDK is linked or bundled.")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }
}

#Preview {
    HomeView()
        .environmentObject(AppModel())
        .environmentObject(GlassesManager())
}
