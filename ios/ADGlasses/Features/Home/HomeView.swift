import Foundation
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
                    assistantCard
                    glassesCard
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
                HStack(spacing: 12) {
                    Image(systemName: "eyeglasses")
                        .font(.title2.weight(.semibold))
                        .frame(width: 42, height: 42)
                        .background(.thinMaterial, in: Circle())

                    VStack(alignment: .leading, spacing: 2) {
                        Text("AD Glasses")
                            .font(.title2.bold())
                        Text(glasses.connectionState.compactLabel)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }

                HStack(spacing: 10) {
                    StatusPill(
                        title: "Glasses",
                        value: glasses.connectionState.compactLabel,
                        systemImage: glasses.connectionState.systemImage
                    )

                    StatusPill(
                        title: "Voice",
                        value: app.isTranscribing ? "Listening" : "Ready",
                        systemImage: app.isTranscribing ? "waveform" : "mic"
                    )
                }
            }
        }
    }

    private var assistantCard: some View {
        SurfaceCard {
            VStack(spacing: 16) {
                AssistantAudioVisual(isActive: app.isTranscribing)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, app.transcript.isEmpty ? 18 : 4)

                if !app.transcript.isEmpty {
                    Divider()

                    Text(app.transcript)
                        .textSelection(.enabled)
                        .frame(maxWidth: .infinity, minHeight: 60, alignment: .topLeading)

                    Button("Clear transcript", role: .destructive) {
                        app.clearTranscript()
                    }
                    .font(.subheadline)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                if let speechError = app.speechError {
                    Label(speechError, systemImage: "exclamationmark.triangle")
                        .font(.footnote)
                        .foregroundStyle(.red)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
    }

    private var glassesCard: some View {
        SurfaceCard {
            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .center) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Your glasses")
                            .font(.headline)
                        Label(
                            glasses.connectionState.compactLabel,
                            systemImage: glasses.connectionState.systemImage
                        )
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    }

                    Spacer()

                    if case .connected(_) = glasses.connectionState {
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
                                Text("Find glasses")
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(glasses.connectionState == .scanning)
                    }
                }

                if glasses.devices.isEmpty {
                    HStack(spacing: 10) {
                        Image(systemName: "dot.radiowaves.left.and.right")
                            .foregroundStyle(.secondary)
                        Text("Nearby glasses will appear here when you scan.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 4)
                } else {
                    Divider()

                    ForEach(glasses.devices.indices, id: \.self) { index in
                        let device = glasses.devices[index]

                        Button {
                            Task { await glasses.connect(to: device) }
                        } label: {
                            HStack(spacing: 12) {
                                Image(systemName: "eyeglasses")
                                    .font(.title3)

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(glasses.devices.count > 1 ? "AD Glasses \(index + 1)" : "AD Glasses")
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

    private var voiceControlBar: some View {
        VStack(spacing: 0) {
            Divider()

            HStack(spacing: 14) {
                Button {
                    Task { await app.toggleTranscription() }
                } label: {
                    Image(systemName: app.isTranscribing ? "stop.fill" : "mic.fill")
                        .font(.title3.weight(.semibold))
                        .frame(width: 52, height: 52)
                }
                .buttonStyle(.borderedProminent)
                .buttonBorderShape(.circle)
                .accessibilityLabel(app.isTranscribing ? "Stop listening" : "Start listening")

                VStack(alignment: .leading, spacing: 2) {
                    Text(app.isTranscribing ? "Listening" : "Tap to talk")
                        .font(.headline)
                    Text(app.isTranscribing ? "Speak naturally" : "Ask AD anything")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Spacer()
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
                Section("App") {
                    LabeledContent("UI", value: "SwiftUI")
                    LabeledContent("Minimum", value: "iOS 17")
                    LabeledContent("Speech", value: app.speechEngineName)
                }

                Section("Diagnostics") {
                    LabeledContent("Glasses provider", value: "HeyCyan")
                    LabeledContent("Additional provider", value: "Meta")

                    if glasses.devices.isEmpty {
                        Text("No discovered peripherals")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(glasses.devices) { device in
                            LabeledContent("Peripheral", value: device.name)
                        }
                    }
                }

                Section {
                    Text("Provider and transport details are kept here for diagnostics instead of being exposed as product branding in the main interface.")
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

private struct AssistantAudioVisual: View {
    let isActive: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30.0, paused: reduceMotion)) { timeline in
            let time = timeline.date.timeIntervalSinceReferenceDate
            let pulse = reduceMotion ? 0.35 : (sin(time * (isActive ? 2.2 : 0.9)) + 1.0) / 2.0

            ZStack {
                Circle()
                    .fill(Color.accentColor.opacity(isActive ? 0.10 + (pulse * 0.06) : 0.06 + (pulse * 0.025)))
                    .frame(width: 128, height: 128)
                    .scaleEffect(reduceMotion ? CGFloat(1) : CGFloat(0.96 + (pulse * 0.06)))

                Circle()
                    .stroke(Color.accentColor.opacity(isActive ? 0.28 : 0.16), lineWidth: 1)
                    .frame(width: 102, height: 102)

                HStack(alignment: .center, spacing: 5) {
                    ForEach(0..<7, id: \.self) { index in
                        Capsule(style: .continuous)
                            .fill(Color.accentColor)
                            .frame(width: 5, height: barHeight(index: index, time: time))
                    }
                }
                .frame(height: 48)
            }
            .frame(width: 148, height: 148)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(isActive ? "Listening" : "Voice ready")
    }

    private func barHeight(index: Int, time: TimeInterval) -> CGFloat {
        let restingHeights: [CGFloat] = [14, 22, 30, 38, 30, 22, 14]
        guard !reduceMotion else { return restingHeights[index] }

        let speed = isActive ? 5.0 : 1.6
        let amplitude: CGFloat = isActive ? 30 : 9
        let baseline: CGFloat = isActive ? 10 : restingHeights[index] - 4
        let wave = (sin((time * speed) + (Double(index) * 0.72)) + 1.0) / 2.0

        return baseline + (CGFloat(wave) * amplitude)
    }
}

private struct SurfaceCard<Content: View>: View {
    let content: Content

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
        case .connecting(_):
            return "Connecting"
        case .connected(_):
            return "Connected"
        case .unavailable(_):
            return "Unavailable"
        }
    }

    var systemImage: String {
        switch self {
        case .disconnected:
            return "eyeglasses"
        case .scanning:
            return "dot.radiowaves.left.and.right"
        case .connecting(_):
            return "arrow.triangle.2.circlepath"
        case .connected(_):
            return "checkmark.circle.fill"
        case .unavailable(_):
            return "exclamationmark.triangle"
        }
    }
}

#Preview {
    HomeView()
        .environmentObject(AppModel())
        .environmentObject(GlassesManager())
}
