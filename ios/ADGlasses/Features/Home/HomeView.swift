import AVKit
import SwiftUI
import UIKit

struct HomeView: View {
    @EnvironmentObject private var app: AppModel
    @EnvironmentObject private var glasses: GlassesManager
    @EnvironmentObject private var library: LibraryModel

    @State private var selectedTab: AppTab = .home
    @State private var showsDeviceCenter: Bool
    @State private var showsSettings = false
    @State private var showsLens = false
    @State private var showsTranslation = false
    @State private var showsSoundbite = false

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
                openLens: { showsLens = true },
                openTranslation: { showsTranslation = true },
                openSoundbite: { showsSoundbite = true },
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
        .sheet(isPresented: $showsLens) {
            LensView()
                .environmentObject(app)
        }
        .sheet(isPresented: $showsTranslation) {
            TranslationView()
                .environmentObject(app)
        }
        .sheet(isPresented: $showsSoundbite) {
            SoundbiteView()
                .environmentObject(app)
                .environmentObject(library)
        }
    }
}

private enum AppTab: Hashable {
    case home
    case assistant
    case library
}

private struct HomeScreen: View {
    @EnvironmentObject private var app: AppModel
    @EnvironmentObject private var glasses: GlassesManager
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    let openLens: () -> Void
    let openTranslation: () -> Void
    let openSoundbite: () -> Void
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
                            availability: .available,
                            action: openLens
                        )

                        LazyVGrid(columns: columns, spacing: 12) {
                            FeatureTile(
                                title: "Ask",
                                detail: askDetail,
                                systemImage: app.isPhoneVoiceQuestionActive ? "waveform" : "mic.fill",
                                tint: app.isPhoneVoiceQuestionActive ? .red : .indigo,
                                availability: .available,
                                action: askByVoice
                            )

                            ForEach(ProductFeature.hardwareActions) { feature in
                                FeatureTile(
                                    title: feature.title,
                                    detail: feature == .video && glasses.isVideoRecording
                                        ? "Recording · tap to stop"
                                        : feature == .audio && glasses.isAudioRecording
                                            ? "Recording · tap to stop"
                                            : feature.detail,
                                    systemImage: feature.systemImage,
                                    tint: feature == .video && glasses.isVideoRecording
                                        ? .red
                                        : feature == .audio && glasses.isAudioRecording
                                            ? .red
                                            : feature.tint,
                                    availability: availability(for: feature),
                                    action: { perform(feature) }
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
                .padding(.bottom, 10)
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

    private var askDetail: String {
        if app.isPhoneVoiceQuestionActive {
            return app.isStoppingTranscription ? "Sending…" : "Listening · sends automatically"
        }
        if app.isGenerating { return "Thinking…" }
        if app.speechOutput.isSpeaking { return "Speaking…" }
        return "Ask by voice"
    }

    private func askByVoice() {
        Task {
            guard !app.isPhoneVoiceQuestionActive else { return }
            app.clearTranscript()
            await app.startVoiceQuestion()
        }
    }

    private func availability(for feature: ProductFeature) -> FeatureAvailability {
        if feature == .translate || feature == .soundbites { return .available }
        guard let capability = feature.capability else { return .available }
        if feature == .photo, glasses.supports(.photoCapture) {
            return .available
        }
        if feature == .video, glasses.supports(.videoRecording) {
            return .available
        }
        if feature == .audio, glasses.supports(.audioRecording) {
            return .available
        }
        if glasses.supports(capability) {
            return .notImplemented
        }
        return .unsupported
    }

    private func unavailableMessage(for feature: ProductFeature) -> String {
        if (feature == .photo || feature == .video || feature == .audio),
           let errorMessage = glasses.errorMessage {
            return errorMessage
        }
        guard let capability = feature.capability else {
            return "This feature is not available in the current build."
        }

        if glasses.supports(capability) {
            return "AD Glasses support this capability, but the iOS action is not ready yet."
        }

        return "AD Glasses do not currently expose this capability to the app."
    }

    private func perform(_ feature: ProductFeature) {
        if feature == .translate {
            openTranslation()
            return
        }
        if feature == .soundbites {
            openSoundbite()
            return
        }
        guard feature == .photo || feature == .video || feature == .audio else {
            unavailableFeature = feature
            return
        }
        guard glasses.connectionState.isConnected else {
            openDeviceCenter()
            return
        }

        Task {
            let succeeded: Bool
            if feature == .photo {
                succeeded = await glasses.requestPhotoCapture()
            } else if feature == .video {
                succeeded = await glasses.toggleVideoRecording()
            } else {
                succeeded = await glasses.toggleAudioRecording()
            }
            if !succeeded {
                unavailableFeature = feature
            }
        }
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
        .contentShape(.interaction, Capsule())
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

                    if let batteryStatus = glasses.batteryStatus {
                        Text("·")
                            .foregroundStyle(.tertiary)

                        Label {
                            Text("\(batteryStatus.level)%")
                        } icon: {
                            HStack(spacing: 2) {
                                Image(systemName: "battery.100percent")
                                if batteryStatus.isCharging {
                                    Image(systemName: "bolt.fill")
                                }
                            }
                        }
                        .accessibilityLabel(
                            batteryStatus.isCharging
                                ? "Battery \(batteryStatus.level) percent, charging"
                                : "Battery \(batteryStatus.level) percent"
                        )
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
    @EnvironmentObject private var library: LibraryModel

    let openDeviceCenter: () -> Void
    let openSettings: () -> Void
    @State private var showsMediaSync = false

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
                            description: "Photos and videos synced from your glasses will appear here.",
                            kinds: [.photo, .video]
                        )
                    } label: {
                        LibraryRow(
                            title: "Captures",
                            subtitle: countLabel(for: [.photo, .video], empty: "Photos and videos"),
                            systemImage: "photo.on.rectangle.angled",
                            tint: .blue
                        )
                    }

                    NavigationLink {
                        LibraryCollectionView(
                            title: "Recordings",
                            systemImage: "waveform",
                            description: "Audio sessions synced or recorded by AD Glasses will appear here.",
                            kinds: [.audio]
                        )
                    } label: {
                        LibraryRow(
                            title: "Recordings",
                            subtitle: countLabel(for: [.audio], empty: "Audio sessions"),
                            systemImage: "waveform",
                            tint: .purple
                        )
                    }

                    NavigationLink {
                        LibraryCollectionView(
                            title: "Notes & Transcripts",
                            systemImage: "note.text",
                            description: "Soundbites and transcripts will appear here.",
                            kinds: [.transcript]
                        )
                    } label: {
                        LibraryRow(
                            title: "Notes & transcripts",
                            subtitle: countLabel(for: [.transcript], empty: "Soundbites and text"),
                            systemImage: "note.text",
                            tint: .orange
                        )
                    }
                }

                Section {
                    Text("Synced photos, videos, and recordings are kept as original files. Photo Auto Enhance creates a separate processed copy and never replaces the original.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Library")
            .alert("Library", isPresented: Binding(
                get: { library.errorMessage != nil },
                set: { if !$0 { library.errorMessage = nil } }
            )) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(library.errorMessage ?? "")
            }
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: openSettings) {
                        Image(systemName: "gearshape")
                    }
                    .accessibilityLabel("Settings")
                }
            }
            .sheet(isPresented: $showsMediaSync) {
                MediaSyncSheet()
                    .environmentObject(glasses)
                    .environmentObject(library)
            }
        }
    }

    @ViewBuilder
    private var syncRow: some View {
        Button(action: openSyncDestination) {
            VStack(alignment: .leading, spacing: 4) {
                Label("Sync from glasses", systemImage: "arrow.triangle.2.circlepath")
                    .foregroundStyle(.primary)
                Text(syncStatus)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func openSyncDestination() {
        guard glasses.connectionState.isConnected,
              glasses.supportsMediaTransfer else {
            openDeviceCenter()
            return
        }
        showsMediaSync = true
    }

    private var syncStatus: String {
        if !glasses.connectionState.isConnected {
            return "Connect a supported pair to check sync availability"
        }
        if !glasses.supports(.mediaTransfer) {
            return "Media transfer is not available for this integration yet"
        }
        return "Ready to check for new photos, videos, and recordings"
    }

    private func countLabel(for kinds: Set<LibraryItemKind>, empty: String) -> String {
        let count = library.items.filter { kinds.contains($0.kind) }.count
        return count == 0 ? empty : "\(count) saved"
    }
}

private struct MediaSyncSheet: View {
    @EnvironmentObject private var glasses: GlassesManager
    @EnvironmentObject private var library: LibraryModel
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase

    @State private var items = [GlassesMediaItem]()
    @State private var isPreparing = true
    @State private var isSyncing = false
    @State private var transferNeedsCleanup = false
    @State private var didFinishTransfer = false
    @State private var errorMessage: String?
    @State private var completionMessage: String?
    @State private var syncTask: Task<Void, Never>?
    @State private var didCopyNetworkPassword = false
    @State private var revealsNetworkPassword = false

    var body: some View {
        NavigationStack {
            Group {
                if let manualJoin = manualNetworkJoin {
                    VStack(spacing: 18) {
                        Image(systemName: "wifi")
                            .font(.system(size: 34, weight: .semibold))
                            .foregroundStyle(.blue)

                        VStack(spacing: 8) {
                            Text("Switch to the glasses Wi-Fi")
                                .font(.title3.weight(.semibold))
                            Text("Settings opens automatically. Choose Wi-Fi, select the network below, then return to AD Glasses. If iOS restores another Settings page, return to the main Settings page first.")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                                .multilineTextAlignment(.center)
                        }

                        VStack(alignment: .leading, spacing: 10) {
                            LabeledContent("Network") {
                                Text(manualJoin.ssid)
                                    .font(.callout.monospaced())
                                    .textSelection(.enabled)
                            }
                            LabeledContent("Password") {
                                Text(
                                    revealsNetworkPassword
                                        ? manualJoin.passphrase
                                        : String(repeating: "•", count: max(8, manualJoin.passphrase.count))
                                )
                                    .font(.callout.monospaced())
                                    .textSelection(.enabled)
                            }
                        }
                        .padding(14)
                        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 16))

                        HStack(spacing: 18) {
                            Button(revealsNetworkPassword ? "Hide password" : "Show password") {
                                revealsNetworkPassword.toggle()
                            }
                            Button {
                                UIPasteboard.general.setItems(
                                    [[UIPasteboard.typeAutomatic: manualJoin.passphrase]],
                                    options: [
                                        .localOnly: true,
                                        .expirationDate: Date().addingTimeInterval(120)
                                    ]
                                )
                                didCopyNetworkPassword = true
                            } label: {
                                Label(
                                    didCopyNetworkPassword ? "Copied" : "Copy password",
                                    systemImage: didCopyNetworkPassword ? "checkmark" : "doc.on.doc"
                                )
                            }
                        }

                        HStack(spacing: 10) {
                            ProgressView()
                                .controlSize(.small)
                            Text("Waiting for you to return from Settings")
                                .font(.footnote.weight(.medium))
                        }

                        Text("The password is copied in case iPhone asks for it. Once the glasses report their network address, sync continues automatically.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                    }
                    .padding(24)
                    .onAppear {
                        // The coordinator copies the current session password before opening
                        // Settings so the first render after returning reflects the real state.
                        didCopyNetworkPassword = true
                    }
                } else if isPreparing {
                    SyncProgressView(state: glasses.mediaTransferState)
                } else if isSyncing {
                    SyncProgressView(state: glasses.mediaTransferState)
                } else if let errorMessage {
                    ContentUnavailableView(
                        "Sync couldn’t start",
                        systemImage: "exclamationmark.triangle",
                        description: Text(errorMessage)
                    )
                } else if let completionMessage {
                    ContentUnavailableView(
                        "Sync complete",
                        systemImage: "checkmark.circle.fill",
                        description: Text("\(completionMessage). The original media is now saved in your Library.")
                    )
                } else if unsyncedItems.isEmpty {
                    ContentUnavailableView(
                        "Nothing to sync",
                        systemImage: "checkmark.circle",
                        description: Text(nothingToSyncDescription)
                    )
                } else {
                    List {
                        Section("On AD Glasses") {
                            ForEach(items) { item in
                                HStack(spacing: 12) {
                                    Image(systemName: item.kind.systemImage)
                                        .foregroundStyle(item.kind.tint)
                                        .frame(width: 24)
                                    Text(item.fileName)
                                        .lineLimit(1)
                                    Spacer()
                                    if isImported(item) {
                                        Image(systemName: "checkmark.circle.fill")
                                            .foregroundStyle(.green)
                                            .accessibilityLabel("Already synced")
                                    }
                                }
                            }
                        }

                        Section {
                            Button {
                                syncTask = Task { await syncNewItems() }
                            } label: {
                                if isSyncing {
                                    HStack {
                                        ProgressView()
                                        Text(glasses.mediaTransferState.label)
                                    }
                                } else {
                                    Label(syncButtonTitle, systemImage: "arrow.down.circle.fill")
                                }
                            }
                            .disabled(isSyncing || unsyncedItems.isEmpty || didFinishTransfer)
                        } footer: {
                            Text("Original files are copied to this iPhone. Sync never deletes media from the glasses.")
                        }
                    }
                }
            }
            .navigationTitle("Sync from glasses")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(isSyncing ? "Cancel" : "Close") { close() }
                }
            }
            .interactiveDismissDisabled(isPreparing || isSyncing)
            .task { await prepare() }
            .onChange(of: scenePhase) { _, phase in
                guard phase == .active, manualNetworkJoin != nil else { return }
                glasses.continueMediaTransferAfterManualNetworkJoin()
            }
            .onDisappear {
                syncTask?.cancel()
                if transferNeedsCleanup {
                    glasses.cancelMediaTransfer()
                }
            }
        }
    }

    private var unsyncedItems: [GlassesMediaItem] {
        items.filter { !isImported($0) }
    }

    private var manualNetworkJoin: (ssid: String, passphrase: String)? {
        guard case .awaitingManualNetworkJoin(let ssid, let passphrase) = glasses.mediaTransferState else {
            return nil
        }
        return (ssid, passphrase)
    }

    private var syncButtonTitle: String {
        let count = unsyncedItems.count
        return count == 1 ? "Sync 1 new item" : "Sync \(count) new items"
    }

    private var nothingToSyncDescription: String {
        items.isEmpty
            ? "AD Glasses has no new photos, videos, or recordings to sync."
            : "Everything currently reported by AD Glasses is already saved in your Library."
    }

    private func isImported(_ item: GlassesMediaItem) -> Bool {
        library.contains(
            sourceProviderID: item.providerID,
            sourceReference: item.remoteIdentifier
        )
    }

    private func prepare() async {
        transferNeedsCleanup = true
        do {
            items = try await glasses.prepareMediaTransfer()
            if unsyncedItems.isEmpty {
                // A successful empty/already-imported manifest is a completed check, not an open
                // transfer session. Exit immediately so the glasses leave transfer mode and the
                // sheet cannot appear to sync forever while there is nothing to download.
                if !items.isEmpty {
                    glasses.cancelMediaTransfer()
                }
                transferNeedsCleanup = false
                didFinishTransfer = true
            }
        } catch is CancellationError {
            glasses.cancelMediaTransfer()
            transferNeedsCleanup = false
        } catch {
            errorMessage = error.localizedDescription
            transferNeedsCleanup = false
        }
        isPreparing = false
    }

    private func syncNewItems() async {
        let pending = unsyncedItems
        guard !pending.isEmpty else { return }
        isSyncing = true
        errorMessage = nil
        completionMessage = nil
        var completed = 0
        var shouldFinishTransfer = true

        do {
            for item in pending {
                try Task.checkCancellation()
                let temporaryURL = FileManager.default.temporaryDirectory
                    .appendingPathComponent(UUID().uuidString)
                    .appendingPathExtension(URL(fileURLWithPath: item.fileName).pathExtension)
                defer { try? FileManager.default.removeItem(at: temporaryURL) }

                try await glasses.downloadMediaItem(item, to: temporaryURL)
                _ = try await library.ingest(
                    fileURL: temporaryURL,
                    title: URL(fileURLWithPath: item.fileName).deletingPathExtension().lastPathComponent,
                    kind: item.kind.libraryKind,
                    sourceProviderID: item.providerID,
                    sourceReference: item.remoteIdentifier
                )
                completed += 1
            }
            completionMessage = completed == 1 ? "1 new item synced" : "\(completed) new items synced"
        } catch is CancellationError {
            glasses.cancelMediaTransfer()
            shouldFinishTransfer = false
        } catch {
            errorMessage = error.localizedDescription
        }

        if shouldFinishTransfer {
            await glasses.finishMediaTransfer()
        }
        transferNeedsCleanup = false
        didFinishTransfer = true
        isSyncing = false
    }

    private func close() {
        syncTask?.cancel()
        if isPreparing || isSyncing {
            glasses.cancelMediaTransfer()
            transferNeedsCleanup = false
            dismiss()
            return
        }
        guard transferNeedsCleanup else {
            dismiss()
            return
        }
        Task {
            await glasses.finishMediaTransfer()
            transferNeedsCleanup = false
            dismiss()
        }
    }
}

private struct SyncProgressView: View {
    let state: GlassesMediaTransferState

    var body: some View {
        VStack(spacing: 24) {
            ZStack {
                Circle()
                    .fill(.thinMaterial)
                    .frame(width: 88, height: 88)
                ProgressView()
                    .controlSize(.large)
                    .tint(.primary)
            }
            .accessibilityHidden(true)

            VStack(spacing: 8) {
                Text(title)
                    .font(.title2.weight(.semibold))
                    .multilineTextAlignment(.center)
                Text(description)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }

            Text(state.label)
                .font(.footnote.weight(.medium))
                .foregroundStyle(.secondary)
                .padding(.horizontal, 14)
                .padding(.vertical, 9)
                .background(.thinMaterial, in: Capsule())
        }
        .padding(32)
        .frame(maxWidth: 420)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(title). \(state.label)")
    }

    private var title: String {
        switch state {
        case .checkingLibrary:
            return "Checking your glasses"
        case .preparing:
            return "Preparing a secure sync"
        case .joiningNetwork:
            return "Connecting to your glasses"
        case .downloading:
            return "Syncing your media"
        case .finishing:
            return "Finishing safely"
        default:
            return "Preparing sync"
        }
    }

    private var description: String {
        switch state {
        case .checkingLibrary:
            return "Looking for new photos, videos, and recordings."
        case .preparing, .joiningNetwork:
            return "Keep AD Glasses nearby. This usually takes only a few seconds."
        case .downloading:
            return "Saving the original file securely to your Library."
        case .finishing:
            return "Closing the temporary transfer connection."
        default:
            return "Getting everything ready."
        }
    }

    private var stageSymbol: String {
        switch state {
        case .checkingLibrary: return "checklist"
        case .joiningNetwork: return "wifi"
        case .downloading: return "arrow.down.circle"
        case .finishing: return "checkmark.shield"
        default: return "eyeglasses"
        }
    }
}

private extension GlassesMediaKind {
    var libraryKind: LibraryItemKind {
        switch self {
        case .photo: return .photo
        case .video: return .video
        case .audio: return .audio
        }
    }

    var systemImage: String {
        switch self {
        case .photo: return "photo"
        case .video: return "video"
        case .audio: return "waveform"
        }
    }

    var tint: Color {
        switch self {
        case .photo: return .blue
        case .video: return .indigo
        case .audio: return .purple
        }
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
    @EnvironmentObject private var library: LibraryModel

    let title: String
    let systemImage: String
    let description: String
    let kinds: Set<LibraryItemKind>

    private var items: [LibraryItem] {
        library.items.filter { kinds.contains($0.kind) }
    }

    var body: some View {
        Group {
            if items.isEmpty {
                ContentUnavailableView(
                    "Nothing here yet",
                    systemImage: systemImage,
                    description: Text(description)
                )
            } else {
                List(items) { item in
                    NavigationLink {
                        LibraryItemDetailView(item: item)
                    } label: {
                        LibraryItemRow(item: item)
                    }
                    .contextMenu {
                        Button(
                            item.isFavorite ? "Remove favorite" : "Favorite",
                            systemImage: item.isFavorite ? "star.slash" : "star"
                        ) {
                            Task { await library.toggleFavorite(item) }
                        }
                        ShareLink(item: library.fileURL(for: item))
                    }
                }
            }
        }
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct LibraryItemRow: View {
    let item: LibraryItem

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: item.kind.systemImage)
                .foregroundStyle(item.kind.tint)
                .frame(width: 28)
            VStack(alignment: .leading, spacing: 3) {
                Text(item.title)
                    .lineLimit(1)
                Text(item.createdAt.formatted(date: .abbreviated, time: .shortened))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            if item.isFavorite {
                Image(systemName: "star.fill")
                    .font(.caption)
                    .foregroundStyle(.yellow)
                    .accessibilityLabel("Favorite")
            }
        }
    }
}

private enum PhotoDisplayVariant: String, CaseIterable, Identifiable {
    case original
    case enhanced

    var id: String { rawValue }

    var title: String {
        switch self {
        case .original: return "Original"
        case .enhanced: return "Enhanced"
        }
    }
}

private struct LibraryItemDetailView: View {
    @EnvironmentObject private var library: LibraryModel
    let item: LibraryItem

    @State private var transcript: String?
    @State private var photo: UIImage?
    @State private var enhancedPhoto: UIImage?
    @State private var enhancedPhotoURL: URL?
    @State private var photoDisplayVariant: PhotoDisplayVariant = .original
    @State private var isEnhancingPhoto = false
    @State private var photoEnhancementError: String?
    @State private var player: AVPlayer?
    @State private var isPlayingAudio = false
    @State private var loadError: String?

    var body: some View {
        Group {
            if item.kind == .transcript {
                if let transcript {
                    ScrollView {
                        Text(transcript)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .textSelection(.enabled)
                            .padding()
                    }
                } else if let loadError {
                    ContentUnavailableView(
                        "Could not open transcript",
                        systemImage: "exclamationmark.triangle",
                        description: Text(loadError)
                    )
                } else {
                    ProgressView("Opening")
                }
            } else if let loadError {
                ContentUnavailableView(
                    "Could not open \(item.kind.displayName.lowercased())",
                    systemImage: "exclamationmark.triangle",
                    description: Text(loadError)
                )
            } else if item.kind == .photo, let photo {
                ZStack(alignment: .bottom) {
                    GeometryReader { proxy in
                        Image(uiImage: displayedPhoto ?? photo)
                            .resizable()
                            .scaledToFit()
                            .frame(
                                width: proxy.size.width,
                                height: proxy.size.height,
                                alignment: .center
                            )
                    }

                    photoControls
                        .padding(.horizontal, 16)
                        .padding(.bottom, 12)
                }
                .background(.black)
            } else if item.kind == .video, let player {
                VideoPlayer(player: player)
                    .background(.black)
                    .onAppear { player.play() }
            } else if item.kind == .audio, let player {
                VStack(spacing: 24) {
                    Image(systemName: "waveform.circle.fill")
                        .font(.system(size: 72))
                        .foregroundStyle(.purple)
                    Button(isPlayingAudio ? "Pause" : "Play") {
                        if isPlayingAudio {
                            player.pause()
                        } else {
                            player.play()
                        }
                        isPlayingAudio.toggle()
                    }
                    .buttonStyle(.borderedProminent)
                }
            } else {
                ProgressView("Opening")
            }
        }
        .navigationTitle(item.title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                ShareLink(item: shareURL)
            }
        }
        .task {
            do {
                let url = library.fileURL(for: item)
                switch item.kind {
                case .transcript:
                    let data = try await Task.detached { try Data(contentsOf: url) }.value
                    guard data.count <= 1_048_576,
                          let value = String(data: data, encoding: .utf8) else {
                        throw LibraryStoreError.invalidSourceFile
                    }
                    transcript = value
                case .photo:
                    let data = try await Task.detached { try Data(contentsOf: url) }.value
                    guard let value = UIImage(data: data) else {
                        throw LibraryStoreError.invalidSourceFile
                    }
                    photo = value
                    await loadExistingEnhancedPhotoIfAvailable()
                case .video, .audio:
                    player = AVPlayer(url: url)
                }
            } catch {
                loadError = error.localizedDescription
            }
        }
        .onAppear {
            if item.kind == .photo || item.kind == .video {
                AppOrientationController.shared.allowMediaOrientation()
            }
        }
        .onDisappear {
            player?.pause()
            if item.kind == .photo || item.kind == .video {
                AppOrientationController.shared.usePortraitOnly()
            }
        }
        .alert("Photo enhancement", isPresented: Binding(
            get: { photoEnhancementError != nil },
            set: { if !$0 { photoEnhancementError = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(photoEnhancementError ?? "")
        }
    }

    private var displayedPhoto: UIImage? {
        if photoDisplayVariant == .enhanced, let enhancedPhoto {
            return enhancedPhoto
        }
        return photo
    }

    private var shareURL: URL {
        if item.kind == .photo,
           photoDisplayVariant == .enhanced,
           let enhancedPhotoURL {
            return enhancedPhotoURL
        }
        return library.fileURL(for: item)
    }

    @ViewBuilder
    private var photoControls: some View {
        VStack(spacing: 10) {
            if enhancedPhoto != nil {
                Picker("Photo version", selection: $photoDisplayVariant) {
                    ForEach(PhotoDisplayVariant.allCases) { variant in
                        Text(variant.title).tag(variant)
                    }
                }
                .pickerStyle(.segmented)
                .accessibilityLabel("Photo version")
            } else {
                Button {
                    Task { await autoEnhancePhoto() }
                } label: {
                    HStack(spacing: 10) {
                        if isEnhancingPhoto {
                            ProgressView()
                                .controlSize(.small)
                        } else {
                            Image(systemName: "wand.and.sparkles")
                        }
                        Text(isEnhancingPhoto ? "Enhancing…" : "Auto Enhance")
                    }
                }
                .buttonStyle(.borderedProminent)
                .disabled(isEnhancingPhoto)
            }
        }
        .padding(10)
        .frame(maxWidth: 360)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private func loadExistingEnhancedPhotoIfAvailable() async {
        guard let url = await library.enhancedPhotoURL(for: item) else { return }
        do {
            let data = try await Task.detached { try Data(contentsOf: url) }.value
            guard let value = UIImage(data: data) else { return }
            enhancedPhotoURL = url
            enhancedPhoto = value
        } catch {
            photoEnhancementError = "The saved enhanced copy could not be opened: \(error.localizedDescription)"
        }
    }

    private func autoEnhancePhoto() async {
        guard !isEnhancingPhoto else { return }
        isEnhancingPhoto = true
        photoEnhancementError = nil
        defer { isEnhancingPhoto = false }

        do {
            let url = try await library.enhancePhoto(item)
            let data = try await Task.detached { try Data(contentsOf: url) }.value
            guard let value = UIImage(data: data) else {
                throw PhotoEnhancementError.encodingFailed
            }
            enhancedPhotoURL = url
            enhancedPhoto = value
            photoDisplayVariant = .enhanced
        } catch is CancellationError {
            return
        } catch {
            photoEnhancementError = error.localizedDescription
        }
    }
}

private extension LibraryItemKind {
    var displayName: String {
        switch self {
        case .photo: return "Photo"
        case .video: return "Video"
        case .audio: return "Recording"
        case .transcript: return "Transcript"
        }
    }

    var systemImage: String {
        switch self {
        case .photo: return "photo"
        case .video: return "video"
        case .audio: return "waveform"
        case .transcript: return "note.text"
        }
    }

    var tint: Color {
        switch self {
        case .photo: return .blue
        case .video: return .pink
        case .audio: return .purple
        case .transcript: return .orange
        }
    }
}

private struct DeviceCenterSheet: View {
    @EnvironmentObject private var glasses: GlassesManager
    @Environment(\.dismiss) private var dismiss
    @AppStorage("setup.iphoneAudioReviewed.v1") private var didReviewIPhoneAudioSetup = false
    @State private var confirmsForget = false
    @State private var confirmsRestart = false
    @State private var confirmsFactoryReset = false
    @State private var showsIPhoneAudioSetup = false

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
                                    Text(provider.displayName)
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

                Section("AD Glasses") {
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
                                        Text(device.name)
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

                    if glasses.hasRememberedDevice {
                        Button("Forget saved glasses", systemImage: "trash", role: .destructive) {
                            confirmsForget = true
                        }
                    }

                    if glasses.connectionState.isConnected,
                       glasses.supportsDeviceManagement {
                        Button("Restart AD Glasses", systemImage: "power") {
                            confirmsRestart = true
                        }

                        Button(
                            "Factory reset AD Glasses",
                            systemImage: "arrow.counterclockwise",
                            role: .destructive
                        ) {
                            confirmsFactoryReset = true
                        }
                    }
                }

                if glasses.connectionState.isConnected,
                   glasses.batteryStatus != nil || glasses.deviceInformation != nil {
                    Section("Device status") {
                        if let battery = glasses.batteryStatus {
                            LabeledContent(
                                "Battery",
                                value: battery.isCharging
                                    ? "\(battery.level)% · Charging"
                                    : "\(battery.level)%"
                            )
                        }

                        if let information = glasses.deviceInformation {
                            if !information.firmwareVersion.isEmpty {
                                LabeledContent("Firmware", value: information.firmwareVersion)
                            }
                            if !information.hardwareVersion.isEmpty {
                                LabeledContent("Hardware", value: information.hardwareVersion)
                            }
                            if !information.networkFirmwareVersion.isEmpty {
                                LabeledContent(
                                    "Wireless firmware",
                                    value: information.networkFirmwareVersion
                                )
                            }
                            if !information.networkHardwareVersion.isEmpty {
                                LabeledContent(
                                    "Wireless hardware",
                                    value: information.networkHardwareVersion
                                )
                            }
                        }
                    }
                }

                if glasses.connectionState.isConnected,
                   glasses.supports(.volumeControl) {
                    Section("Audio") {
                        NavigationLink {
                            GlassesVolumeSettingsView()
                        } label: {
                            LabeledContent("Glasses volume", value: "Music, calls, system")
                        }
                    }
                }

                if glasses.connectionState.isConnected,
                   glasses.supportsGlassesVoiceWake {
                    Section("Voice activation") {
                        Toggle(
                            "Glasses voice wake",
                            isOn: Binding(
                                get: { glasses.glassesVoiceWakeEnabled },
                                set: { enabled in
                                    Task { await glasses.setGlassesVoiceWakeEnabled(enabled) }
                                }
                            )
                        )

                        Text("Controls the wake phrase built into the glasses. It is Off by default; the Assistant button remains available.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }

                Section("Learn") {
                    NavigationLink {
                        GlassesControlsGuideView()
                    } label: {
                        Label("Buttons and gestures", systemImage: "hand.tap")
                    }

                    NavigationLink {
                        IPhoneAudioSetupView(showsDismissButton: false)
                    } label: {
                        Label("iPhone audio setup", systemImage: "ear.badge.waveform")
                    }
                }

                if !glasses.deviceManagementPlaceholders.isEmpty {
                    Section {
                        ForEach(glasses.deviceManagementPlaceholders) { placeholder in
                            Button(action: {}) {
                                HStack(spacing: 12) {
                                    Image(systemName: placeholder.operation.systemImage)
                                        .frame(width: 22)
                                    Text(placeholder.operation.title)
                                    Spacer()
                                    Text("Not available yet")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            .disabled(true)
                            .accessibilityHint(placeholder.reason)
                        }
                    } header: {
                        Text("Device controls")
                    } footer: {
                        Text("Firmware update remains unavailable until its update and recovery behavior is validated on your physical pair.")
                    }
                }

                if let error = glasses.errorMessage {
                    Section {
                        Label(error, systemImage: "exclamationmark.triangle.fill")
                            .font(.footnote)
                            .foregroundStyle(.red)
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
            .confirmationDialog(
                "Forget saved glasses?",
                isPresented: $confirmsForget,
                titleVisibility: .visible
            ) {
                Button("Forget glasses", role: .destructive) {
                    Task { await glasses.forgetLastDevice() }
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("AD Glasses will disconnect and stop reconnecting automatically. You can scan and connect again at any time.")
            }
            .confirmationDialog(
                "Restart AD Glasses?",
                isPresented: $confirmsRestart,
                titleVisibility: .visible
            ) {
                Button("Restart") {
                    Task { _ = await glasses.restartGlasses() }
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("The glasses will restart and Bluetooth may disconnect briefly.")
            }
            .confirmationDialog(
                "Factory reset AD Glasses?",
                isPresented: $confirmsFactoryReset,
                titleVisibility: .visible
            ) {
                Button("Factory reset", role: .destructive) {
                    Task { _ = await glasses.factoryResetGlasses() }
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("This sends the verified factory-reset command. The glasses will restart and reinitialize; which stored settings, pairing state, or media are cleared is determined by the glasses firmware.")
            }
            .onAppear { offerIPhoneAudioSetupIfNeeded() }
            .onChange(of: glasses.connectionState) { _, state in
                guard state.isConnected else { return }
                offerIPhoneAudioSetupIfNeeded()
            }
            .sheet(isPresented: $showsIPhoneAudioSetup) {
                NavigationStack {
                    IPhoneAudioSetupView(showsDismissButton: true)
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func offerIPhoneAudioSetupIfNeeded() {
        guard glasses.connectionState.isConnected,
              !didReviewIPhoneAudioSetup else { return }
        showsIPhoneAudioSetup = true
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

private struct IPhoneAudioSetupView: View {
    @Environment(\.dismiss) private var dismiss
    @AppStorage("setup.iphoneAudioReviewed.v1") private var didReviewSetup = false

    let showsDismissButton: Bool

    var body: some View {
        List {
            Section("Identify the audio device") {
                SetupInstructionRow(
                    number: 1,
                    text: "Open Settings, then Bluetooth. Tap the info button beside the connected glasses audio device."
                )
                SetupInstructionRow(
                    number: 2,
                    text: "Choose Device Type, then Headphone. This helps iPhone interpret third-party Bluetooth audio correctly."
                )
            }

            Section("Reduce loud audio") {
                SetupInstructionRow(
                    number: 3,
                    text: "In Settings, open Sounds & Haptics, Headphone Safety, then turn on Reduce Loud Audio."
                )
                SetupInstructionRow(
                    number: 4,
                    text: "Choose the listening limit you prefer. iPhone applies it to Bluetooth headphone audio without AD Glasses changing the speaker firmware."
                )
            }

            Section {
                Button("I've reviewed these settings", systemImage: "checkmark.circle.fill") {
                    didReviewSetup = true
                    dismiss()
                }
            } footer: {
                Text("You can return here from Glasses > iPhone audio setup. AD Glasses cannot read or change the Headphone Safety setting.")
            }
        }
        .navigationTitle("iPhone audio setup")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if showsDismissButton {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Not now") { dismiss() }
                }
            }
        }
    }
}

private struct SetupInstructionRow: View {
    let number: Int
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Text("\(number)")
                .font(.caption.bold())
                .foregroundStyle(.white)
                .frame(width: 24, height: 24)
                .background(.blue, in: Circle())
                .accessibilityHidden(true)

            Text(text)
                .fixedSize(horizontal: false, vertical: true)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Step \(number). \(text)")
    }
}

private struct GlassesControlsGuideView: View {
    var body: some View {
        List {
            Section("Camera and power button") {
                ControlGuideRow(action: "Press once", result: "Take a photo")
                ControlGuideRow(action: "Press twice", result: "Start video; press once to stop")
                ControlGuideRow(action: "Hold 3 seconds while off", result: "Turn on")
                ControlGuideRow(action: "Hold 5 seconds", result: "Turn off")
            }

            Section("Assistant button") {
                ControlGuideRow(action: "Press once", result: "Start the glasses Assistant")
                ControlGuideRow(action: "Press twice", result: "Capture for visual assistance")
                ControlGuideRow(action: "Hold 3 seconds", result: "Start or stop an audio recording")
            }

            Section("Touch area") {
                ControlGuideRow(action: "Tap twice", result: "Play or pause")
                ControlGuideRow(action: "Tap three times", result: "Previous track")
                ControlGuideRow(action: "Press and hold", result: "Next track")
                ControlGuideRow(action: "Swipe forward or back", result: "Raise or lower volume")
            }

            Section("Emergency hardware actions") {
                ControlGuideRow(action: "Hold power 10 seconds", result: "Factory-reset the glasses")
                ControlGuideRow(action: "Hold power 16 seconds", result: "Force-restart and return to pairing")
                Text("Long holds can erase media or pairing. Use them only when recovery is necessary.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Buttons & gestures")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct ControlGuideRow: View {
    let action: String
    let result: String

    var body: some View {
        LabeledContent(action) {
            Text(result)
                .multilineTextAlignment(.trailing)
        }
    }
}

private extension GlassesDeviceManagementOperation {
    var title: String {
        switch self {
        case .firmwareUpdate: return "Firmware update"
        case .factoryReset: return "Factory reset"
        case .forcedRestart: return "Forced restart"
        case .customWakePhrase: return "Custom wake phrase"
        }
    }

    var systemImage: String {
        switch self {
        case .firmwareUpdate: return "arrow.triangle.2.circlepath"
        case .factoryReset: return "arrow.counterclockwise"
        case .forcedRestart: return "power"
        case .customWakePhrase: return "waveform.badge.mic"
        }
    }
}

private struct GlassesVolumeSettingsView: View {
    @EnvironmentObject private var glasses: GlassesManager
    @State private var draftValues: [GlassesVolumeChannel: Double] = [:]
    @State private var editingChannel: GlassesVolumeChannel?

    var body: some View {
        List {
            if let profile = glasses.volumeProfile {
                Section {
                    ForEach(GlassesVolumeChannel.allCases, id: \.self) { channel in
                        volumeRow(channel, profile: profile)
                    }
                } footer: {
                    Text("These are the three levels reported by the glasses. Touch gestures continue to control the active music volume during playback.")
                }
            } else {
                Section {
                    HStack(spacing: 12) {
                        ProgressView()
                        Text("Reading volume levels from your glasses…")
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
        }
        .navigationTitle("Glasses Volume")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await glasses.refreshVolumeProfile()
            synchronizeDrafts()
        }
        .onChange(of: glasses.volumeProfile) { _, _ in
            guard editingChannel == nil else { return }
            synchronizeDrafts()
        }
    }

    private func volumeRow(
        _ channel: GlassesVolumeChannel,
        profile: GlassesVolumeProfile
    ) -> some View {
        let level = profile[channel]
        return VStack(alignment: .leading, spacing: 8) {
            HStack {
                Label(channel.title, systemImage: channel.systemImage)
                Spacer()
                Text("\(Int(draftValues[channel] ?? Double(level.current)))")
                    .monospacedDigit()
                    .foregroundStyle(.secondary)
            }

            Slider(
                value: Binding(
                    get: { draftValues[channel] ?? Double(level.current) },
                    set: { draftValues[channel] = $0 }
                ),
                in: Double(level.minimum) ... Double(level.maximum),
                step: 1
            ) { isEditing in
                editingChannel = isEditing ? channel : nil
                guard !isEditing else { return }
                let value = Int(draftValues[channel] ?? Double(level.current))
                Task {
                    await glasses.setVolume(value, for: channel)
                    synchronizeDrafts()
                }
            }
            .accessibilityLabel("\(channel.title) volume")
            .accessibilityValue("\(Int(draftValues[channel] ?? Double(level.current))) of \(level.maximum)")
        }
        .padding(.vertical, 4)
    }

    private func synchronizeDrafts() {
        guard let profile = glasses.volumeProfile else { return }
        for channel in GlassesVolumeChannel.allCases {
            draftValues[channel] = Double(profile[channel].current)
        }
    }
}

private extension GlassesVolumeChannel {
    var systemImage: String {
        switch self {
        case .music: return "music.note"
        case .calls: return "phone"
        case .system: return "speaker.wave.2"
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
            .contentShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        }
        .buttonStyle(.plain)
        .modifier(
            HomeGlassSurface(
                cornerRadius: 20,
                reduceTransparency: reduceTransparency
            )
        )
        .contentShape(
            .interaction,
            RoundedRectangle(cornerRadius: 20, style: .continuous)
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
            .contentShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
        }
        .buttonStyle(.plain)
        .modifier(
            HomeGlassSurface(
                cornerRadius: 26,
                reduceTransparency: reduceTransparency
            )
        )
        .contentShape(
            .interaction,
            RoundedRectangle(cornerRadius: 26, style: .continuous)
        )
        .accessibilityHint(
            availability == .unsupported
                ? "Requires glasses with a camera."
                : "Open Lens to explore what you are looking at."
        )
    }

    private var copy: some View {
        VStack(alignment: .leading, spacing: 10) {
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
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
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
                .glassEffect(.regular, in: Capsule())
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
                content
                    .glassEffect(.regular, in: .rect(cornerRadius: cornerRadius))
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
        case .lens: return .camera
        case .video: return .videoRecording
        case .photo: return .photoCapture
        case .audio: return .audioRecording
        case .translate, .soundbites: return .microphoneAudio
        }
    }
}

private extension GlassesCapability {
    var title: String {
        switch self {
        case .bluetoothConnection: return "Bluetooth connection"
        case .photoCapture: return "Photo capture"
        case .videoRecording: return "Video recording"
        case .audioRecording: return "Audio recording"
        case .microphoneAudio: return "Glasses audio"
        case .camera: return "Camera"
        case .mediaTransfer: return "Media transfer"
        case .deviceInformation: return "Device information"
        case .volumeControl: return "Volume control"
        case .notifications: return "Notifications"
        }
    }

    var systemImage: String {
        switch self {
        case .bluetoothConnection: return "antenna.radiowaves.left.and.right"
        case .photoCapture: return "camera"
        case .videoRecording: return "video"
        case .audioRecording: return "waveform.circle"
        case .microphoneAudio: return "waveform"
        case .camera: return "camera"
        case .mediaTransfer: return "arrow.triangle.2.circlepath"
        case .deviceInformation: return "info.circle"
        case .volumeControl: return "speaker.wave.2"
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
        case .unavailable: return "Unavailable"
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
