import SwiftUI

struct HomeView: View {
    @EnvironmentObject private var app: AppModel
    @EnvironmentObject private var glasses: GlassesManager
    @State private var showsBuildInfo = false

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 16) {
                    statusOverview
                    transcriptCard
                    heyCyanCard
                    metaCard
                }
                .frame(maxWidth: 680)
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 24)
                .frame(maxWidth: .infinity)
            }
            .navigationTitle("AD Glasses")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showsBuildInfo = true
                    } label: {
                        Image(systemName: "gearshape")
                    }
                    .accessibilityLabel("App information")
                }
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                voiceControlBar
            }
            .sheet(isPresented: $showsBuildInfo) {
                buildInfoSheet
            }
        }
    }

    private var statusOverview: some View {
        SurfaceCard {
            VStack(alignment: .leading, spacing: 14) {
                Text("Ready on iPhone")
                    .font(.title2.bold())

                Text("Native speech and glasses connections stay separate, so either side can evolve without changing the app shell.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)

                HStack(spacing: 10) {
                    StatusPill(
                        title: "HeyCyan",
                        value: glasses.connectionState.compactLabel,
                        systemImage: glasses.connectionState.systemImage
                    )

                    StatusPill(
                        title: "Speech",
                        value: app.isTranscribing ? "Listening" : "Ready",
                        systemImage: app.isTranscribing ? "waveform" : "mic"
                    )
                }
            }
        }
    }

    private var transcriptCard: some View {
        SurfaceCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Label("Voice", systemImage: "waveform")
                        .font(.headline)
                    Spacer()
                    Text(app.speechEngineName)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                if app.transcript.isEmpty {
                    Text("Tap the microphone below to start Apple-native transcription.")
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, minHeight: 72, alignment: .topLeading)
                } else {
                    Text(app.transcript)
                        .textSelection(.enabled)
                        .frame(maxWidth: .infinity, minHeight: 72, alignment: .topLeading)
                }

                if let speechError = app.speechError {
                    Label(speechError, systemImage: "exclamationmark.triangle")
                        .font(.footnote)
                        .foregroundStyle(.red)
                }

                if !app.transcript.isEmpty {
                    Button("Clear transcript", role: .destructive) {
                        app.clearTranscript()
                    }
                    .font(.subheadline)
                }
            }
        }
    }

    private var heyCyanCard: some View {
        SurfaceCard {
            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("HeyCyan")
                            .font(.headline)
                        Label(
                            glasses.connectionState.label,
                            systemImage: glasses.connectionState.systemImage
                        )
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    }

                    Spacer()

                    if case .connected = glasses.connectionState {
                        Button("Disconnect") {
                            Task { await glasses.disconnect() }
                        }
                        .buttonStyle(.bordered)
                    } else {
                        Button {
                            Task { await glasses.scanHeyCyan() }
                        } label: {
                            if glasses.connectionState == .scanning {
                                ProgressView()
                            } else {
                                Text("Scan")
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(glasses.connectionState == .scanning)
                    }
                }

                Text("CoreBluetooth is the transport boundary. Vendor commands stay out of the UI and are added only when their protocol is verified.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)

                if glasses.devices.isEmpty {
                    Text("No scanned devices yet.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .padding(.vertical, 4)
                } else {
                    Divider()
                    ForEach(glasses.devices) { device in
                        Button {
                            Task { await glasses.connect(to: device) }
                        } label: {
                            HStack(spacing: 12) {
                                Image(systemName: "eyeglasses")
                                    .font(.title3)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(device.name)
                                        .foregroundStyle(.primary)
                                    if let signal = device.signalStrength {
                                        Text("Signal \(signal) dBm")
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                }
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .font(.caption.bold())
                                    .foregroundStyle(.tertiary)
                            }
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }

                if let error = glasses.errorMessage {
                    Label(error, systemImage: "exclamationmark.triangle")
                        .font(.footnote)
                        .foregroundStyle(.red)
                }
            }
        }
    }

    private var metaCard: some View {
        SurfaceCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Label("Meta", systemImage: "eyeglasses")
                        .font(.headline)
                    Spacer()
                    Text(glasses.meta.connectionState.compactLabel)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                }

                Text("The Meta provider is already separated from the app UI. No Meta SDK is bundled in this build; a verified integration can be added later without changing this screen's architecture.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var voiceControlBar: some View {
        VStack(spacing: 0) {
            Divider()
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(app.isTranscribing ? "Listening" : "Ask AD")
                        .font(.headline)
                    Text(app.isTranscribing ? "Tap to stop transcription" : "Apple-native speech")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Spacer()

                Button {
                    Task { await app.toggleTranscription() }
                } label: {
                    Image(systemName: app.isTranscribing ? "stop.fill" : "mic.fill")
                        .font(.title3.weight(.semibold))
                        .frame(width: 48, height: 48)
                }
                .buttonStyle(.borderedProminent)
                .buttonBorderShape(.circle)
                .accessibilityLabel(app.isTranscribing ? "Stop listening" : "Start listening")
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .frame(maxWidth: 680)
            .frame(maxWidth: .infinity)
        }
        .background(.ultraThinMaterial)
    }

    private var buildInfoSheet: some View {
        NavigationStack {
            List {
                Section("iOS") {
                    LabeledContent("UI", value: "SwiftUI")
                    LabeledContent("Minimum", value: "iOS 17")
                    LabeledContent("Speech", value: app.speechEngineName)
                }

                Section("Glasses") {
                    LabeledContent("Primary", value: "HeyCyan")
                    LabeledContent("Available provider", value: "Meta")
                    Text("Meta SDK is not bundled in this build.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }

                Section {
                    Text("This iOS app uses native Apple frameworks and keeps vendor integrations behind adapters.")
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("About this build")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { showsBuildInfo = false }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

private struct SurfaceCard<Content: View>: View {
    @ViewBuilder let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        content
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }
}

private struct StatusPill: View {
    let title: String
    let value: String
    let systemImage: String

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: systemImage)
            VStack(alignment: .leading, spacing: 1) {
                Text(title)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                Text(value)
                    .font(.caption.weight(.semibold))
                    .lineLimit(1)
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}

private extension GlassesConnectionState {
    var compactLabel: String {
        switch self {
        case .disconnected:
            return "Ready"
        case .scanning:
            return "Scanning"
        case .connecting:
            return "Connecting"
        case .connected:
            return "Connected"
        case .unavailable:
            return "Unavailable"
        }
    }

    var systemImage: String {
        switch self {
        case .disconnected:
            return "eyeglasses"
        case .scanning:
            return "dot.radiowaves.left.and.right"
        case .connecting:
            return "arrow.triangle.2.circlepath"
        case .connected:
            return "checkmark.circle.fill"
        case .unavailable:
            return "exclamationmark.triangle"
        }
    }
}

#Preview {
    HomeView()
        .environmentObject(AppModel())
        .environmentObject(GlassesManager())
}
