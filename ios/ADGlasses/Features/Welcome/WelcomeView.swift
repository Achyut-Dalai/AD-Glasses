import SwiftUI

struct AppRootView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var showsWelcome: Bool
    @State private var opensDeviceCenter = false

    init() {
#if DEBUG
        _showsWelcome = State(
            initialValue: !ProcessInfo.processInfo.arguments.contains("-skip-welcome")
        )
#else
        _showsWelcome = State(initialValue: true)
#endif
    }

    var body: some View {
        ZStack {
            if showsWelcome {
                WelcomeView(
                    onConnected: enterApp,
                    onConnectManually: {
                        opensDeviceCenter = true
                        enterApp()
                    },
                    onContinue: enterApp
                )
                .transition(.opacity)
            } else {
                HomeView(initialShowsDeviceCenter: opensDeviceCenter)
                    .transition(.opacity)
            }
        }
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.32), value: showsWelcome)
    }

    private func enterApp() {
        showsWelcome = false
    }
}

private struct WelcomeView: View {
    @EnvironmentObject private var glasses: GlassesManager
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    let onConnected: () -> Void
    let onConnectManually: () -> Void
    let onContinue: () -> Void

    @State private var phase: WelcomePhase = .connecting

    var body: some View {
        ZStack {
            background

            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    brand
                    headline
                        .padding(.top, 14)
                    glassesStage
                    statusArea
                }
                .frame(maxWidth: 620)
                .padding(.horizontal, 20)
                .padding(.top, 6)
                .padding(.bottom, 22)
                .frame(maxWidth: .infinity)
                .frame(minHeight: UIScreen.main.bounds.height - 24, alignment: .top)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .task { await attemptAutomaticConnection() }
    }

    private var background: some View {
        ZStack {
            Color(uiColor: .systemBackground)
            RadialGradient(
                colors: [Color.primary.opacity(0.045), .clear],
                center: .topTrailing,
                startRadius: 0,
                endRadius: 370
            )
            RadialGradient(
                colors: [Color.secondary.opacity(0.035), .clear],
                center: .bottomLeading,
                startRadius: 0,
                endRadius: 320
            )
        }
        .ignoresSafeArea()
    }

    private var brand: some View {
        HStack(spacing: 10) {
            Image("BrandIcon")
                .resizable()
                .scaledToFill()
                .frame(width: 36, height: 36)
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .accessibilityHidden(true)

            Text("AD GLASSES")
                .font(.subheadline.weight(.bold))
                .tracking(1.5)
        }
        .accessibilityElement(children: .combine)
    }

    private var headline: some View {
        VStack(alignment: .leading, spacing: 10) {
            VStack(alignment: .leading, spacing: 0) {
                Text("Your glasses.")
                Text("Your AI.")
                    .foregroundStyle(.blue)
                Text("Your data.")
            }
            .font(.system(.largeTitle, design: .rounded, weight: .bold))
            .tracking(-1)
            .fixedSize(horizontal: false, vertical: true)

            Text("See more, remember more, and keep the moments that matter.")
                .font(.body)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var glassesStage: some View {
        ZStack {
            Circle()
                .fill(Color.primary.opacity(0.035))
                .frame(width: 240, height: 240)
                .blur(radius: 30)
                .offset(x: 130, y: -80)

            Ellipse()
                .fill(Color.white.opacity(0.16))
                .frame(width: 300, height: 110)
                .blur(radius: 26)
                .offset(x: -95, y: 90)

            Image("GlassesHero")
                .resizable()
                .scaledToFit()
                .padding(.horizontal, 8)
                .padding(.vertical, 16)
                .accessibilityLabel("Smart glasses")
        }
        .frame(height: 260)
        .welcomeSurface(reduceTransparency: reduceTransparency, cornerRadius: 30)
        .overlay {
            RoundedRectangle(cornerRadius: 30)
                .strokeBorder(Color.primary.opacity(0.08), lineWidth: 0.75)
        }
        .shadow(color: .black.opacity(0.06), radius: 24, y: 12)
    }

    @ViewBuilder
    private var statusArea: some View {
        switch phase {
        case .connecting:
            VStack(spacing: 10) {
                ProgressView()
                    .progressViewStyle(.circular)
                    .controlSize(.large)
                    .tint(.primary)

                Text("Connecting")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .accessibilityElement(children: .combine)
            .accessibilityLabel("Connecting to glasses")

        case .needsChoice:
            VStack(spacing: 12) {
                Button(action: onConnectManually) {
                    Text("Connect glasses")
                        .foregroundStyle(Color(uiColor: .systemBackground))
                        .frame(maxWidth: .infinity)
                }
                    .buttonStyle(.borderedProminent)
                    .tint(Color(uiColor: .label))
                    .controlSize(.large)

                Button(action: onContinue) {
                    Text("Continue without glasses")
                        .frame(maxWidth: .infinity)
                }
                    .buttonStyle(.bordered)
                    .controlSize(.large)
            }
            .buttonBorderShape(.roundedRectangle(radius: 15))
        }
    }

    private func attemptAutomaticConnection() async {
        phase = .connecting

        // Keep the launch moment visible without delaying Bluetooth work. The old serial delay
        // added nearly a second before CoreBluetooth was even asked to restore the remembered
        // glasses.
        let minimumDisplay = Task {
            try? await Task.sleep(for: .milliseconds(900))
        }

        if glasses.connectionState.isConnected {
            await minimumDisplay.value
            onConnected()
            return
        }

        let reconnected = await glasses.reconnectLastDevice()
        await minimumDisplay.value
        guard !Task.isCancelled else { return }

        if reconnected {
            if !reduceMotion {
                try? await Task.sleep(for: .milliseconds(280))
            }
            onConnected()
        } else {
            phase = .needsChoice("We couldn't connect automatically. Choose how you would like to continue.")
        }
    }
}

private enum WelcomePhase: Equatable {
    case connecting
    case needsChoice(String)
}

private extension View {
    @ViewBuilder
    func welcomeSurface(reduceTransparency: Bool, cornerRadius: CGFloat) -> some View {
        if #available(iOS 26.0, *), !reduceTransparency {
            glassEffect(.regular, in: .rect(cornerRadius: cornerRadius))
        } else {
            background(.regularMaterial, in: RoundedRectangle(cornerRadius: cornerRadius))
                .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
        }
    }
}
