import SwiftUI

struct HomeView: View {
    @EnvironmentObject private var app: AppModel
    @EnvironmentObject private var glasses: GlassesManager

    @State private var selectedTab: AppTab = .home
    @State private var showsDeviceCenter: Bool
    @State private var showsSettings = false

    init(initialShowsDeviceCenter: Bool = false) {
        _showsDeviceCenter = State(initialValue: initialShowsDeviceCenter)
#if DEBUG
        _showsSettings = State(
            initialValue: ProcessInfo.processInfo.arguments.contains("-open-settings")
        )
#endif
    }

    var body: some View {
        TabView(selection: $selectedTab) {
            HomeScreen(
                openAssistant: { selectedTab = .assistant },
                openDeviceCenter: { showsDeviceCenter = true },
                openSettings: { showsSettings = true }
            )
            .tag(AppTab.home)
            .tabItem { Label("Home", systemImage: "house") }

            AssistantView(openSettings: { showsSettings = true })
                .tag(AppTab.assistant)
                .tabItem { Label("Assistant", systemImage: "sparkles") }

            LibraryScreen(
                openDeviceCenter: { showsDeviceCenter = true },
                openSettings: { showsSettings = true }
            )
            .tag(AppTab.library)
            .tabItem { Label("Library", systemImage: "rectangle.stack") }
        }
        .sheet(isPresented: $showsDeviceCenter) {
            DeviceCenterSheet()
                .environmentObject(glasses)
        }
        .sheet(isPresented: $showsSettings) {
            SettingsView()
                .environmentObject(app)
                .environmentObject(glasses)
        }
    }
}

private enum AppTab: Hashable {
    case home
    case assistant
    case library
}

private struct HomeScreen: View {
    @EnvironmentObject private var glasses: GlassesManager
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    let openAssistant: () -> Void
    let openDeviceCenter: () -> Void
    let openSettings: () -> Void

    @State private var unavailableFeature: ProductFeature?

    private var columns: [GridItem] {
        if dynamicTypeSize.isAccessibilitySize {
            return [GridItem(.flexible())]
        }
        return [GridItem(.adaptive(minimum: 148), spacing: 12)]
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 18) {
                    VStack(alignment: .leading, spacing: 12) {
                        LensTile(
                            availability: availability(for: .lens),
                            action: { unavailableFeature = .lens }
                        )

                        LazyVGrid(columns: columns, spacing: 12) {
                            FeatureTile(
                                title: "Ask",
                                detail: "Continue from your glasses",
                                systemImage: "sparkles",
                                tint: .indigo,
                                availability: .available,
                                action: openAssistant
                            )

                            ForEach(ProductFeature.hardwareActions) { feature in
                                FeatureTile(
                                    title: feature.title,
                                    detail: feature.detail,
                                    systemImage: feature.systemImage,
                                    tint: feature.tint,
                                    availability: availability(for: feature),
                                    action: { unavailableFeature = feature }
                                )
                            }
                        }

                    }

                }
                .frame(maxWidth: 700)
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 28)
                .frame(maxWidth: .infinity)
            }
            .background(HomeAmbientBackground())
            .safeAreaInset(edge: .bottom, spacing: 8) {
                HStack {
                    Spacer(minLength: 0)
                    ConnectionPill(openDeviceCenter: openDeviceCenter)
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 4)
            }
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    HStack(alignment: .firstTextBaseline, spacing: 5) {
                        Text("AD")
                            .font(.headline.weight(.black))
                        Text("GLASSES")
                            .font(.caption2.weight(.bold))
                            .tracking(1.7)
                            .foregroundStyle(.secondary)
                    }
                    .accessibilityElement(children: .combine)
                    .accessibilityLabel("AD Glasses")
                }

                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: openSettings) {
                        Image(systemName: "gearshape")
                    }
                    .accessibilityLabel("Settings")
                }
            }
            .alert(item: $unavailableFeature) { feature in
                Alert(
                    title: Text(feature.title),
                    message: Text(unavailableMessage(for: feature)),
                    dismissButton: .default(Text("OK"))
                )
            }
        }
    }

    private func availability(for feature: ProductFeature) -> FeatureAvailability {
        guard let capability = feature.capability else { return .available }
        if glasses.supports(capability) {
            return .notImplemented
        }
        return .unsupported
    }

    private func unavailableMessage(for feature: ProductFeature) -> String {
        guard let capability = feature.capability else {
            return "This feature is not available in the current build."
        }

        if glasses.supports(capability) {
            return "Your glasses support this capability, but the iOS action is not ready yet."
        }

        return "Your glasses do not currently expose this capability to the app."
    }
}

private struct ConnectionPill: View {
    @EnvironmentObject private var glasses: GlassesManager
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    let openDeviceCenter: () -> Void

    var body: some View {
        Button(action: openDeviceCenter) {
            statusContent
                .font(.caption.weight(.semibold))
                .foregroundStyle(.primary)
                .padding(.horizontal, 13)
                .padding(.vertical, 9)
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .modifier(ConnectionPillSurface(reduceTransparency: reduceTransparency))
        .accessibilityHint("Opens glasses connection controls")
    }

    @ViewBuilder
    private var statusContent: some View {
        Group {
            switch glasses.connectionState {
            case .connected:
                HStack(spacing: 7) {
                    Circle()
                        .fill(Color.green)
                        .frame(width: 8, height: 8)
                        .accessibilityHidden(true)

                    Text("Connected")

                    if let batteryLevel = glasses.batteryLevel {
                        Text("·")
                            .foregroundStyle(.tertiary)

                        Label {
                            Text("\(batteryLevel)%")
                        } icon: {
                            Image(systemName: "battery.100percent")
                        }
                        .accessibilityLabel("Battery \(batteryLevel) percent")
                    }
                }

            case .scanning:
                HStack(spacing: 7) {
                    ProgressView()
                        .controlSize(.small)
                    Text("Finding glasses")
                }

            case .connecting:
                HStack(spacing: 7) {
                    ProgressView()
                        .controlSize(.small)
                    Text("Connecting")
                }

            case .disconnected, .unavailable:
                HStack(spacing: 7) {
                    Circle()
                        .fill(Color.red)
                        .frame(width: 8, height: 8)
                        .accessibilityHidden(true)

                    Text("Connect")
                }
            }
        }
    }
}

private struct LibraryScreen: View {
    @EnvironmentObject private var glasses: GlassesManager

    let openDeviceCenter: () -> Void
    let openSettings: () -> Void

    var body: some View {
        NavigationStack {
            List {
                Section {
                    syncRow
                }

                Section("Saved on this iPhone") {
                    NavigationLink {
                        LibraryCollectionView(
                            title: "Captures",
                            systemImage: "photo.on.rectangle.angled",
                            description: "Photos and videos synced from your glasses will appear here."
                        )
                    } label: {
                        LibraryRow(
                            title: "Captures",
                            subtitle: "Photos and videos",
                            systemImage: "photo.on.rectangle.angled",
                            tint: .blue
                        )
                    }

                    NavigationLink {
                        LibraryCollectionView(
                            title: "Recordings & Transcripts",
                            systemImage: "waveform",
                            description: "Saved audio sessions and their transcription text will appear here."
                        )
                    } label: {
                        LibraryRow(
                            title: "Recordings & transcripts",
                            subtitle: "Audio sessions and text",
                            systemImage: "waveform",
                            tint: .purple
                        )
                    }

                    NavigationLink {
                        LibraryCollectionView(
                            title: "Notes & Summaries",
                            systemImage: "note.text",
                            description: "Notes and summaries created from transcripts will appear here."
                        )
                    } label: {
                        LibraryRow(
                            title: "Notes & summaries",
                            subtitle: "Ideas and meeting notes",
                            systemImage: "note.text",
                            tint: .orange
                        )
                    }
                }

                Section {
                    Text("Library storage is intentionally separate from glasses transport. A future media-transfer provider can populate these collections without changing their screens.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Library")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: openSettings) {
                        Image(systemName: "gearshape")
                    }
                    .accessibilityLabel("Settings")
                }
            }
        }
    }

    @ViewBuilder
    private var syncRow: some View {
        Button(action: openDeviceCenter) {
            VStack(alignment: .leading, spacing: 4) {
                Label("Sync from glasses", systemImage: "arrow.triangle.2.circlepath")
                    .foregroundStyle(.primary)
                Text(syncStatus)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var syncStatus: String {
        if !glasses.connectionState.isConnected {
            return "Connect a supported pair to check sync availability"
        }
        if !glasses.supports(.mediaTransfer) {
            return "Media transfer is not available for this integration yet"
        }
        return "Open glasses connections to begin"
    }
}

private struct LibraryRow: View {
    let title: String
    let subtitle: String
    let systemImage: String
    let tint: Color

    var body: some View {
        Label {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        } icon: {
            Image(systemName: systemImage)
                .foregroundStyle(tint)
        }
        .padding(.vertical, 4)
    }
}

private struct LibraryCollectionView: View {
    let title: String
    let systemImage: String
    let description: String

    var body: some View {
        ContentUnavailableView(
            "Nothing here yet",
            systemImage: systemImage,
            description: Text(description)
        )
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct DeviceCenterSheet: View {
    @EnvironmentObject private var glasses: GlassesManager
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section("Connections") {
                    ForEach(glasses.providers) { provider in
                        Button {
                            glasses.selectProvider(provider.id)
                        } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(consumerProviderName(id: provider.id, technicalName: provider.displayName))
                                        .foregroundStyle(.primary)
                                    Text(provider.connectionState.compactLabel)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                if provider.id == glasses.selectedProviderID {
                                    Image(systemName: "checkmark")
                                        .font(.subheadline.bold())
                                        .foregroundStyle(.blue)
                                }
                            }
                        }
                    }
                }

                Section("Your glasses") {
                    connectionAction

                    if !glasses.devices.isEmpty {
                        ForEach(glasses.devices) { device in
                            Button {
                                Task { await glasses.connect(to: device) }
                            } label: {
                                HStack(spacing: 12) {
                                    Image(systemName: "dot.radiowaves.left.and.right")
                                        .foregroundStyle(.blue)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(consumerDeviceName(device))
                                            .foregroundStyle(.primary)
                                        if let strength = device.signalStrength {
                                            Text(signalDescription(strength))
                                                .font(.caption)
                                                .foregroundStyle(.secondary)
                                        }
                                    }
                                    Spacer()
                                    Image(systemName: "chevron.right")
                                        .font(.caption.bold())
                                        .foregroundStyle(.tertiary)
                                }
                            }
                            .disabled(glasses.connectionState.isBusy)
                        }
                    }
                }

                if let error = glasses.errorMessage {
                    Section {
                        Label(error, systemImage: "exclamationmark.triangle.fill")
                            .font(.footnote)
                            .foregroundStyle(.red)
                    }
                }

                Section("Available capabilities") {
                    if glasses.selectedProvider.capabilities.isEmpty {
                        Text("No capabilities are configured for this integration in the current build.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(glasses.selectedProvider.capabilities.sorted(by: { $0.title < $1.title }), id: \.self) { capability in
                            Label(capability.title, systemImage: capability.systemImage)
                        }
                    }
                }
            }
            .navigationTitle("Glasses")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    @ViewBuilder
    private var connectionAction: some View {
        switch glasses.connectionState {
        case .connected:
            Button("Disconnect", systemImage: "xmark.circle", role: .destructive) {
                Task { await glasses.disconnect() }
            }
        case .scanning:
            HStack {
                ProgressView()
                Text("Scanning nearby glasses…")
            }
        case .connecting:
            HStack {
                ProgressView()
                Text("Connecting to glasses…")
            }
        case .unavailable(let reason):
            Label(reason, systemImage: "exclamationmark.triangle")
                .foregroundStyle(.secondary)
        case .disconnected:
            Button("Scan for nearby glasses", systemImage: "dot.radiowaves.left.and.right") {
                Task { await glasses.scan() }
            }
        }
    }

    private func signalDescription(_ value: Int) -> String {
        switch value {
        case let value where value >= -55: return "Strong signal"
        case -70 ..< -55: return "Good signal"
        default: return "Weak signal"
        }
    }
}

private struct FeatureTile: View {
    let title: String
    let detail: String
    let systemImage: String
    let tint: Color
    let availability: FeatureAvailability
    let action: () -> Void

    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Image(systemName: systemImage)
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(tint)
                        .frame(width: 38, height: 38)
                        .background(tint.opacity(0.10), in: RoundedRectangle(cornerRadius: 11))
                    Spacer()
                    if availability == .unsupported {
                        Image(systemName: "lock.fill")
                            .font(.caption2.weight(.bold))
                            .foregroundStyle(.tertiary)
                            .accessibilityHidden(true)
                    }
                }

                VStack(alignment: .leading, spacing: 3) {
                    Text(title)
                        .font(.headline)
                        .foregroundStyle(.primary)
                    Text(detail)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }
            }
            .padding(14)
            .frame(maxWidth: .infinity, minHeight: 118, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .modifier(
            HomeGlassSurface(
                cornerRadius: 20,
                reduceTransparency: reduceTransparency,
                interactive: true
            )
        )
        .accessibilityHint(availability == .unsupported ? "Requires supported glasses. \(detail)" : detail)
    }
}

private struct LensTile: View {
    let availability: FeatureAvailability
    let action: () -> Void

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    var body: some View {
        Button(action: action) {
            Group {
                if dynamicTypeSize.isAccessibilitySize {
                    VStack(alignment: .leading, spacing: 18) {
                        copy
                        LensVisionField(isUnavailable: availability == .unsupported)
                            .frame(maxWidth: .infinity, alignment: .trailing)
                    }
                } else {
                    HStack(spacing: 16) {
                        copy
                        Spacer(minLength: 8)
                        LensVisionField(isUnavailable: availability == .unsupported)
                    }
                }
            }
            .padding(18)
            .frame(maxWidth: .infinity, minHeight: 148, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .modifier(
            HomeGlassSurface(
                cornerRadius: 26,
                reduceTransparency: reduceTransparency,
                interactive: true
            )
        )
        .accessibilityHint(
            availability == .unsupported
                ? "Requires glasses with a camera."
                : "Open Lens to explore what you are looking at."
        )
    }

    private var copy: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 9) {
                Text("Lens")
                    .font(.title2.bold())
                    .foregroundStyle(.primary)

                LensStatusPill(availability: availability)
            }

            Text("Look. Ask. Understand.")
                .font(.headline)
                .foregroundStyle(.primary)

            Text("Explore what’s in front of you.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

private struct LensStatusPill: View {
    let availability: FeatureAvailability

    var body: some View {
        Text(label)
            .font(.caption2.weight(.semibold))
            .foregroundStyle(tint)
            .padding(.horizontal, 8)
            .padding(.vertical, 5)
            .background(tint.opacity(0.10), in: Capsule())
    }

    private var label: String {
        switch availability {
        case .available: return "Ready"
        case .notImplemented: return "Preview"
        case .unsupported: return "Needs camera"
        }
    }

    private var tint: Color {
        switch availability {
        case .available: return .green
        case .notImplemented: return .indigo
        case .unsupported: return .secondary
        }
    }
}

private struct LensVisionField: View {
    let isUnavailable: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var scanForward = false

    var body: some View {
        ZStack {
            Circle()
                .fill(
                    RadialGradient(
                        colors: [
                            Color.indigo.opacity(isUnavailable ? 0.16 : 0.24),
                            Color.purple.opacity(isUnavailable ? 0.07 : 0.12),
                            .clear
                        ],
                        center: .center,
                        startRadius: 0,
                        endRadius: 64
                    )
                )
                .frame(width: 124, height: 124)

            Image(systemName: "viewfinder")
                .font(.system(size: 76, weight: .ultraLight))
                .foregroundStyle(Color.indigo.opacity(isUnavailable ? 0.23 : 0.34))

            Circle()
                .fill(
                    RadialGradient(
                        colors: [
                            Color.indigo.opacity(isUnavailable ? 0.04 : 0.14),
                            Color.primary.opacity(isUnavailable ? 0.02 : 0.06),
                            .clear
                        ],
                        center: .center,
                        startRadius: 0,
                        endRadius: 48
                    )
                )
                .frame(width: 82, height: 82)

            Circle()
                .stroke(Color.primary.opacity(isUnavailable ? 0.08 : 0.15), lineWidth: 1)
                .frame(width: 58, height: 58)

            Image(systemName: isUnavailable ? "lock.fill" : "camera.aperture")
                .font(.system(size: isUnavailable ? 20 : 31, weight: .medium))
                .foregroundStyle(isUnavailable ? Color.secondary : Color.primary)

            if !isUnavailable {
                Capsule(style: .continuous)
                    .fill(
                        LinearGradient(
                            colors: [.clear, Color.indigo.opacity(0.72), .clear],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .frame(width: 78, height: 2)
                    .offset(y: scanForward ? 26 : -26)
                    .opacity(reduceMotion ? 0.45 : 0.85)
            }
        }
        .frame(width: 126, height: 112)
        .clipped()
        .animation(
            reduceMotion || isUnavailable
                ? nil
                : .easeInOut(duration: 1.75).repeatForever(autoreverses: true),
            value: scanForward
        )
        .onAppear {
            scanForward = !reduceMotion && !isUnavailable
        }
        .onChange(of: reduceMotion) { _, _ in
            scanForward = !reduceMotion && !isUnavailable
        }
        .onChange(of: isUnavailable) { _, _ in
            scanForward = !reduceMotion && !isUnavailable
        }
        .accessibilityHidden(true)
    }
}

private struct HomeAmbientBackground: View {
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        ZStack {
            Color(uiColor: .systemBackground)
            RadialGradient(
                colors: [
                    Color.primary.opacity(colorScheme == .dark ? 0.055 : 0.025),
                    .clear
                ],
                center: .topTrailing,
                startRadius: 0,
                endRadius: 300
            )
            RadialGradient(
                colors: [
                    Color.indigo.opacity(colorScheme == .dark ? 0.14 : 0.055),
                    .clear
                ],
                center: .bottomLeading,
                startRadius: 0,
                endRadius: 360
            )
        }
        .ignoresSafeArea()
    }
}

private struct ConnectionPillSurface: ViewModifier {
    let reduceTransparency: Bool

    @ViewBuilder
    func body(content: Content) -> some View {
        if reduceTransparency {
            content
                .background(Color(uiColor: .secondarySystemBackground), in: Capsule())
                .overlay {
                    Capsule()
                        .stroke(Color.primary.opacity(0.08), lineWidth: 0.5)
                }
                .shadow(color: .black.opacity(0.07), radius: 8, y: 4)
        } else if #available(iOS 26.0, *) {
            content
                .glassEffect(.regular.interactive(), in: Capsule())
                .shadow(color: .black.opacity(0.07), radius: 8, y: 4)
        } else {
            content
                .background(.ultraThinMaterial, in: Capsule())
                .overlay {
                    Capsule()
                        .stroke(Color.primary.opacity(0.08), lineWidth: 0.5)
                }
                .shadow(color: .black.opacity(0.07), radius: 8, y: 4)
        }
    }
}

private struct HomeGlassSurface: ViewModifier {
    let cornerRadius: CGFloat
    let reduceTransparency: Bool
    let interactive: Bool

    @Environment(\.colorScheme) private var colorScheme

    @ViewBuilder
    func body(content: Content) -> some View {
        Group {
            if reduceTransparency {
                content
                    .background(
                        Color(uiColor: .secondarySystemBackground),
                        in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            } else if #available(iOS 26.0, *) {
                if interactive {
                    content
                        .glassEffect(.regular.interactive(), in: .rect(cornerRadius: cornerRadius))
                } else {
                    content
                        .glassEffect(.regular, in: .rect(cornerRadius: cornerRadius))
                }
            } else {
                content
                    .background(
                        .ultraThinMaterial,
                        in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            }
        }
        .overlay {
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .strokeBorder(
                    colorScheme == .dark
                        ? Color.white.opacity(0.10)
                        : Color.white.opacity(0.72),
                    lineWidth: 0.75
                )
                .allowsHitTesting(false)
        }
        .shadow(
            color: Color.black.opacity(colorScheme == .dark ? 0.16 : 0.04),
            radius: 14,
            x: 0,
            y: 7
        )
    }
}

private enum FeatureAvailability: Equatable {
    case available
    case unsupported
    case notImplemented

    var label: String {
        switch self {
        case .available: return "Available"
        case .unsupported: return "Not available"
        case .notImplemented: return "iOS adapter pending"
        }
    }
}

private enum ProductFeature: String, Identifiable {
    case lens
    case photo
    case video
    case translate
    case soundbites
    case audio

    var id: String { rawValue }

    static let hardwareActions: [ProductFeature] = [.photo, .video, .translate, .soundbites, .audio]

    var title: String {
        switch self {
        case .lens: return "Lens"
        case .photo: return "Photo"
        case .video: return "Video"
        case .translate: return "Translate"
        case .soundbites: return "Soundbites"
        case .audio: return "Audio"
        }
    }

    var detail: String {
        switch self {
        case .lens: return "Ask about what you’re looking at"
        case .photo: return "Take a photo"
        case .video: return "Record from glasses"
        case .translate: return "Live conversation"
        case .soundbites: return "Turn speech into notes"
        case .audio: return "Record from glasses"
        }
    }

    var systemImage: String {
        switch self {
        case .lens: return "viewfinder"
        case .photo: return "camera.fill"
        case .video: return "video.fill"
        case .translate: return "translate"
        case .soundbites: return "quote.bubble.fill"
        case .audio: return "waveform.circle.fill"
        }
    }

    var tint: Color {
        switch self {
        case .lens: return .indigo
        case .photo: return .teal
        case .video: return .pink
        case .translate: return .indigo
        case .soundbites: return .orange
        case .audio: return .red
        }
    }

    var capability: GlassesCapability? {
        switch self {
        case .lens, .photo, .video: return .camera
        case .translate, .soundbites, .audio: return .microphoneAudio
        }
    }
}

private extension GlassesCapability {
    var title: String {
        switch self {
        case .bluetoothConnection: return "Bluetooth connection"
        case .microphoneAudio: return "Glasses audio"
        case .camera: return "Camera"
        case .mediaTransfer: return "Media transfer"
        case .deviceInformation: return "Device information"
        case .notifications: return "Notifications"
        }
    }

    var systemImage: String {
        switch self {
        case .bluetoothConnection: return "antenna.radiowaves.left.and.right"
        case .microphoneAudio: return "waveform"
        case .camera: return "camera"
        case .mediaTransfer: return "arrow.triangle.2.circlepath"
        case .deviceInformation: return "info.circle"
        case .notifications: return "bell"
        }
    }
}

private extension GlassesConnectionState {
    var compactLabel: String {
        switch self {
        case .disconnected: return "Not connected"
        case .scanning: return "Scanning"
        case .connecting: return "Connecting"
        case .connected: return "Connected"
        case .unavailable: return "Not configured"
        }
    }

    var systemImage: String {
        switch self {
        case .disconnected: return "circle"
        case .scanning: return "dot.radiowaves.left.and.right"
        case .connecting: return "arrow.triangle.2.circlepath"
        case .connected: return "checkmark.circle.fill"
        case .unavailable: return "exclamationmark.triangle"
        }
    }
}

private func consumerProviderName(id: String, technicalName: String) -> String {
    if id == "heycyan" {
        return "AD Glasses"
    }
    return technicalName
}

private func consumerDeviceName(_ device: GlassesDevice) -> String {
    if device.providerID == "heycyan" {
        return "AD Glasses"
    }
    return device.name
}
