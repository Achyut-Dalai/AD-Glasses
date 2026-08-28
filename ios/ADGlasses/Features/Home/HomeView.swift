import SwiftUI

struct HomeView: View {
    @EnvironmentObject private var app: AppModel
    @EnvironmentObject private var glasses: GlassesManager

    @State private var selectedTab: AppTab = .home
    @State private var showsDeviceCenter: Bool
    @State private var showsSettings = false

    init(initialShowsDeviceCenter: Bool = false) {
        _showsDeviceCenter = State(initialValue: initialShowsDeviceCenter)
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
            SettingsSheet()
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
                    DeviceHero(openDeviceCenter: openDeviceCenter)

                    VStack(alignment: .leading, spacing: 12) {
                        SectionHeading(
                            title: "Everyday actions",
                            subtitle: "Capture, understand, and return to what matters."
                        )

                        LazyVGrid(columns: columns, spacing: 12) {
                            FeatureTile(
                                title: "Ask",
                                detail: "Continue a thought",
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

                        LensTile(
                            availability: availability(for: .lens),
                            action: { unavailableFeature = .lens }
                        )
                    }

                }
                .frame(maxWidth: 700)
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 28)
                .frame(maxWidth: .infinity)
            }
            .background(HomeAmbientBackground())
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

private struct DeviceHero: View {
    @EnvironmentObject private var glasses: GlassesManager
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    let openDeviceCenter: () -> Void

    var body: some View {
        Button(action: openDeviceCenter) {
            VStack(spacing: 0) {
                ZStack {
                    Color.clear

                    Circle()
                        .fill(Color.primary.opacity(0.035))
                        .frame(width: 210, height: 210)
                        .blur(radius: 28)
                        .offset(x: 120, y: -72)

                    Ellipse()
                        .fill(Color.white.opacity(0.18))
                        .frame(width: 280, height: 100)
                        .blur(radius: 24)
                        .offset(x: -90, y: 76)

                    Image("GlassesHero")
                        .resizable()
                        .scaledToFit()
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .accessibilityHidden(true)
                }
                .frame(height: 150)
                .clipped()

                HStack(spacing: 12) {
                    if glasses.connectionState.isBusy {
                        ProgressView()
                            .controlSize(.small)
                            .frame(width: 24, height: 24)
                    } else {
                        Circle()
                            .fill(iconTint)
                            .frame(width: 9, height: 9)
                            .frame(width: 24, height: 24)
                    }

                    VStack(alignment: .leading, spacing: 2) {
                        Text(glasses.connectionState.isConnected ? "AD Glasses" : "Your glasses")
                            .font(.headline)
                            .foregroundStyle(.primary)
                            .lineLimit(1)
                        Text(heroMessage)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(2)
                    }

                    Spacer(minLength: 6)

                    Image(systemName: "chevron.right")
                        .font(.subheadline.bold())
                        .foregroundStyle(.tertiary)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 13)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .modifier(HomeHeroSurface(reduceTransparency: reduceTransparency))
        .overlay {
            RoundedRectangle(cornerRadius: 26)
                .strokeBorder(Color.primary.opacity(0.08), lineWidth: 0.75)
        }
        .accessibilityHint("Opens glasses connections")
    }

    private var iconTint: Color {
        switch glasses.connectionState {
        case .connected: return .green
        case .scanning, .connecting: return .blue
        case .unavailable: return .orange
        case .disconnected: return .secondary
        }
    }

    private var heroMessage: String {
        switch glasses.connectionState {
        case .connected:
            return "Ready to capture, listen, and assist"
        case .scanning:
            return "Looking for nearby glasses…"
        case .connecting:
            return "Keep your glasses close while the connection finishes."
        case .unavailable:
            return "Open connections to review setup"
        case .disconnected:
            return "Tap to connect your glasses"
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

private struct SettingsSheet: View {
    @EnvironmentObject private var app: AppModel
    @EnvironmentObject private var glasses: GlassesManager
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section("Connections") {
                    ForEach(glasses.providers) { provider in
                        LabeledContent {
                            Text(provider.connectionState.compactLabel)
                                .foregroundStyle(.secondary)
                        } label: {
                            Text(consumerProviderName(id: provider.id, technicalName: provider.displayName))
                        }
                    }
                }

                Section("Assistant") {
                    LabeledContent("AI service", value: "Not configured")
                    LabeledContent("Speech engine", value: app.speechEngineName)
                    Text("Voice input currently uses the iPhone microphone. A verified glasses-audio provider can later supply the same speech boundary.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }

                Section("About") {
                    LabeledContent("App", value: "AD Glasses")
                    LabeledContent("Interface", value: "Native SwiftUI")
                    LabeledContent("Minimum iOS", value: "17")
                }

                Section("Diagnostics") {
                    ForEach(glasses.providers) { provider in
                        LabeledContent(provider.displayName, value: provider.id)
                    }
                    Text("Technical integration names are shown here only for troubleshooting.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

private struct SectionHeading: View {
    let title: String
    let subtitle: String

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(title)
                .font(.title3.bold())
            Text(subtitle)
                .font(.subheadline)
                .foregroundStyle(.secondary)
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
        .background(Color(uiColor: .secondarySystemBackground).opacity(0.92), in: RoundedRectangle(cornerRadius: 19))
        .accessibilityHint(availability == .unsupported ? "Requires supported glasses. \(detail)" : detail)
    }
}

private struct LensTile: View {
    let availability: FeatureAvailability
    let action: () -> Void

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

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
        .background {
            ZStack {
                RoundedRectangle(cornerRadius: 26, style: .continuous)
                    .fill(Color(uiColor: .secondarySystemBackground))

                LinearGradient(
                    colors: [
                        Color.indigo.opacity(0.13),
                        Color.blue.opacity(0.055),
                        .clear
                    ],
                    startPoint: .topTrailing,
                    endPoint: .bottomLeading
                )
                .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))

                RadialGradient(
                    colors: [Color.cyan.opacity(0.10), .clear],
                    center: .trailing,
                    startRadius: 0,
                    endRadius: 170
                )
                .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
            }
        }
        .overlay {
            RoundedRectangle(cornerRadius: 26, style: .continuous)
                .strokeBorder(Color.indigo.opacity(0.16), lineWidth: 0.75)
        }
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
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .fill(Color.primary.opacity(0.04))

            Image(systemName: "viewfinder")
                .font(.system(size: 76, weight: .ultraLight))
                .foregroundStyle(Color.indigo.opacity(isUnavailable ? 0.13 : 0.28))

            Circle()
                .fill(
                    RadialGradient(
                        colors: [
                            Color.cyan.opacity(isUnavailable ? 0.05 : 0.18),
                            Color.indigo.opacity(isUnavailable ? 0.04 : 0.11),
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
                            colors: [.clear, Color.cyan.opacity(0.72), .clear],
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
    var body: some View {
        ZStack {
            Color(uiColor: .systemGroupedBackground)
            RadialGradient(
                colors: [Color.primary.opacity(0.035), .clear],
                center: .topTrailing,
                startRadius: 0,
                endRadius: 300
            )
            RadialGradient(
                colors: [Color.indigo.opacity(0.035), .clear],
                center: .bottomLeading,
                startRadius: 0,
                endRadius: 360
            )
        }
        .ignoresSafeArea()
    }
}

private struct HomeHeroSurface: ViewModifier {
    let reduceTransparency: Bool

    @ViewBuilder
    func body(content: Content) -> some View {
        if reduceTransparency {
            content
                .background(Color(uiColor: .secondarySystemBackground), in: RoundedRectangle(cornerRadius: 26))
                .clipShape(RoundedRectangle(cornerRadius: 26))
        } else if #available(iOS 26.0, *) {
            content
                .glassEffect(.regular.interactive(), in: .rect(cornerRadius: 26))
        } else {
            content
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 26))
                .clipShape(RoundedRectangle(cornerRadius: 26))
        }
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
