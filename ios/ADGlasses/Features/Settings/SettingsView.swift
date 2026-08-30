import AVFoundation
import Speech
import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var app: AppModel
    @EnvironmentObject private var glasses: GlassesManager
    @Environment(\.dismiss) private var dismiss
    @State private var diagnosticsEnabled = false
    @State private var diagnosticsURL: URL?
    @State private var diagnosticsError: String?

    var body: some View {
        NavigationStack {
            List {
                Section("Glasses") {
                    ForEach(glasses.providers) { provider in
                        LabeledContent(
                            provider.displayName,
                            value: provider.connectionState.settingsLabel
                        )
                    }
                }

                Section("Assistant") {
                    NavigationLink {
                        CloudAISettingsView(store: app.aiProfiles)
                    } label: {
                        LabeledContent("Cloud AI", value: cloudAIStatus)
                    }

                    LabeledContent("Speech engine", value: app.speechEngineName)

                    NavigationLink {
                        SpeechVoiceSettingsView(controller: app.speechOutput)
                    } label: {
                        LabeledContent(
                            "Spoken voice",
                            value: selectedSpeechVoiceName
                        )
                    }
                }

                Section("Data and access") {
                    NavigationLink("Privacy") {
                        PrivacySettingsView()
                    }
                    NavigationLink("Storage") {
                        StorageSettingsView()
                    }
                    NavigationLink("Permissions") {
                        PermissionsSettingsView()
                    }
                    Button("Language") {
                        openSystemSettings()
                    }
                }

                Section {
                    NavigationLink("About AD Glasses") {
                        AboutSettingsView()
                    }
                }

                Section("Diagnostics") {
                    if glasses.supportsHardwareDiagnostics {
                        Toggle("Capture protocol packets", isOn: $diagnosticsEnabled)
                            .onChange(of: diagnosticsEnabled) { _, enabled in
                                Task {
                                    await glasses.setHardwareDiagnosticsEnabled(enabled)
                                    await refreshDiagnosticsURL()
                                }
                            }

                        if let diagnosticsURL {
                            ShareLink(item: diagnosticsURL) {
                                Label("Export hardware log", systemImage: "square.and.arrow.up")
                            }
                        }

                        Button("Clear hardware log") {
                            Task {
                                do {
                                    try await glasses.clearHardwareDiagnostics()
                                    await refreshDiagnosticsURL()
                                } catch {
                                    diagnosticsError = error.localizedDescription
                                }
                            }
                        }

                        Text("Enable this only while validating physical glasses. The bounded log includes raw BLE bytes and may contain device or glasses-network details. It never includes Cloud AI keys; share an export only with people you trust.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }

                    DisclosureGroup("Provider details") {
                        ForEach(glasses.providers) { provider in
                            LabeledContent(provider.displayName, value: provider.id)
                        }
                        Text("Technical identifiers appear only here for troubleshooting.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .task(id: glasses.selectedProviderID) {
                diagnosticsEnabled = await glasses.isHardwareDiagnosticsEnabled() ?? false
                await refreshDiagnosticsURL()
            }
            .alert("Diagnostics", isPresented: Binding(
                get: { diagnosticsError != nil },
                set: { if !$0 { diagnosticsError = nil } }
            )) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(diagnosticsError ?? "")
            }
        }
        .presentationDetents([.large])
    }

    private var cloudAIStatus: String {
        guard let profile = app.aiProfiles.activeProfile else { return "Not configured" }
        return app.aiProfiles.isConfigured ? profile.name : "Key required"
    }

    private var selectedSpeechVoiceName: String {
        app.speechOutput.voices.first {
            $0.identifier == app.speechOutput.selectedVoiceIdentifier
        }?.name ?? "System default"
    }

    private func openSystemSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }

    private func refreshDiagnosticsURL() async {
        do {
            diagnosticsURL = try await glasses.hardwareDiagnosticsURL()
        } catch {
            diagnosticsError = error.localizedDescription
        }
    }
}

private struct SpeechVoiceSettingsView: View {
    @ObservedObject var controller: SpeechOutputController
    @State private var errorMessage: String?

    var body: some View {
        List {
            Section("Voice") {
                Picker("Apple voice", selection: $controller.selectedVoiceIdentifier) {
                    ForEach(controller.voices) { voice in
                        VStack(alignment: .leading) {
                            Text(voice.name)
                            Text("\(voice.language) · \(voice.quality.label)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        .tag(voice.identifier)
                    }
                }
                .pickerStyle(.navigationLink)

                Button(controller.isSpeaking ? "Stop preview" : "Preview voice") {
                    if controller.isSpeaking {
                        controller.stop()
                    } else {
                        do {
                            try controller.speak("AD Glasses is ready when you need it.")
                        } catch {
                            errorMessage = error.localizedDescription
                        }
                    }
                }
            }

            Section {
                Text("Premium and Enhanced labels come directly from Apple. Ava, Zoe, Samantha, and Alex appear only when that voice is available on this iPhone; additional voices are managed in iOS Settings.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Spoken voice")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { controller.refreshVoices() }
        .alert("Spoken voice", isPresented: Binding(
            get: { errorMessage != nil },
            set: { if !$0 { errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(errorMessage ?? "")
        }
    }
}

private struct CloudAISettingsView: View {
    @ObservedObject var store: AIProfileStore
    @State private var errorMessage: String?

    var body: some View {
        List {
            if store.profiles.isEmpty {
                ContentUnavailableView(
                    "No Cloud AI profile",
                    systemImage: "cloud",
                    description: Text("Add a provider, model, and API key to enable assistant responses.")
                )
                .listRowBackground(Color.clear)
            } else {
                Section("Profiles") {
                    ForEach(store.profiles) { profile in
                        NavigationLink {
                            AIProfileEditorView(store: store, profile: profile)
                        } label: {
                            VStack(alignment: .leading, spacing: 3) {
                                HStack {
                                    Text(profile.name)
                                    if store.activeProfileID == profile.id {
                                        Text("Active")
                                            .font(.caption2.weight(.semibold))
                                            .foregroundStyle(.green)
                                    }
                                }
                                Text("\(profile.provider.displayName) · \(profile.model)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                        }
                        .swipeActions {
                            Button("Delete", role: .destructive) {
                                do {
                                    try store.delete(profile.id)
                                } catch {
                                    errorMessage = error.localizedDescription
                                }
                            }
                        }
                    }
                }
            }

            Section {
                NavigationLink {
                    AIProfileEditorView(
                        store: store,
                        profile: .new(existingCount: store.profiles.count)
                    )
                } label: {
                    Label("Add Cloud AI profile", systemImage: "plus")
                }
            }

            Section {
                Text("API keys are stored in the iOS Keychain. They are sent only to the selected profile endpoint when you ask AD Assistant for a response.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Cloud AI")
        .navigationBarTitleDisplayMode(.inline)
        .alert("Cloud AI", isPresented: Binding(
            get: { errorMessage != nil },
            set: { if !$0 { errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(errorMessage ?? "")
        }
    }
}

private struct AIProfileEditorView: View {
    @ObservedObject var store: AIProfileStore
    @Environment(\.dismiss) private var dismiss

    @State private var profile: AIProfile
    @State private var apiKey = ""
    @State private var makeActive: Bool
    @State private var errorMessage: String?
    @State private var alertTitle = "Cloud AI profile"
    @State private var isTesting = false

    init(store: AIProfileStore, profile: AIProfile) {
        self.store = store
        _profile = State(initialValue: profile)
        _makeActive = State(initialValue: store.activeProfileID == profile.id || store.profiles.isEmpty)
    }

    var body: some View {
        Form {
            Section("Profile") {
                TextField("Name", text: $profile.name)
                Picker("Provider", selection: $profile.provider) {
                    ForEach(AIProviderKind.allCases) { provider in
                        Text(provider.displayName).tag(provider)
                    }
                }
                TextField("Model", text: $profile.model)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            }

            Section("Endpoint") {
                if profile.provider.managesEndpoint {
                    LabeledContent("Managed by AD Glasses", value: profile.provider.defaultBaseURL)
                        .font(.footnote)
                } else {
                    TextField("https://example.com/v1", text: $profile.baseURL)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.URL)
                        .autocorrectionDisabled()
                }
            }

            Section("API key") {
                SecureField(apiKeyPrompt, text: $apiKey)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                Text(existingCredentialNote)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            Section {
                Toggle("Make active", isOn: $makeActive)
            }

            Section {
                Button {
                    Task { await saveAndTest() }
                } label: {
                    HStack(spacing: 10) {
                        if isTesting {
                            ProgressView()
                                .controlSize(.small)
                        }
                        Text(isTesting ? "Testing connection…" : "Save and test")
                    }
                }
                .disabled(isTesting)
            } footer: {
                Text("Saves this profile, then sends a short request through the same connection Assistant uses.")
            }
        }
        .navigationTitle(store.profiles.contains(where: { $0.id == profile.id }) ? "Edit profile" : "New profile")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button("Save", action: save)
                    .fontWeight(.semibold)
                    .disabled(isTesting)
            }
        }
        .onChange(of: profile.provider) { oldProvider, newProvider in
            if profile.model.isEmpty || profile.model == oldProvider.defaultModel {
                profile.model = newProvider.defaultModel
            }
            profile.baseURL = newProvider.defaultBaseURL
            if profile.name.isEmpty || profile.name == oldProvider.displayName {
                profile.name = newProvider.displayName
            }
        }
        .alert(alertTitle, isPresented: Binding(
            get: { errorMessage != nil },
            set: { if !$0 { errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(errorMessage ?? "")
        }
    }

    private var apiKeyPrompt: String {
        store.hasCredential(for: profile.id) ? "New key (optional)" : "API key"
    }

    private var existingCredentialNote: String {
        if store.hasCredential(for: profile.id) {
            return "A key is already stored. Leave this blank to keep it."
        }
        return "The key is stored in Keychain and is never shown again."
    }

    private func save() {
        do {
            _ = try saveProfile()
            dismiss()
        } catch {
            alertTitle = "Could not save profile"
            errorMessage = error.localizedDescription
        }
    }

    private func saveAndTest() async {
        isTesting = true
        defer { isTesting = false }

        do {
            let savedProfile = try saveProfile()
            let credential = try store.credential(for: savedProfile.id)
            _ = try await CloudAIClient().response(
                to: [ConversationMessage(role: .user, text: "Reply with OK.")],
                profile: savedProfile,
                credential: credential
            )
            alertTitle = "Connection succeeded"
            errorMessage = "\(savedProfile.provider.displayName) responded successfully using \(savedProfile.model)."
        } catch {
            alertTitle = "Connection failed"
            errorMessage = error.localizedDescription
        }
    }

    private func saveProfile() throws -> AIProfile {
        let savedProfile = try store.save(
            profile,
            apiKeyReplacement: apiKey,
            makeActive: makeActive
        )
        profile = savedProfile
        apiKey = ""
        return savedProfile
    }
}

private struct PrivacySettingsView: View {
    @EnvironmentObject private var app: AppModel
    @State private var confirmsDeletion = false

    var body: some View {
        List {
            Section("On this iPhone") {
                LabeledContent("Conversations", value: "\(app.conversations.count)")
                Text("Conversation text is stored locally so Assistant history survives app restarts.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            Section {
                Button("Delete all conversations", role: .destructive) {
                    confirmsDeletion = true
                }
                .disabled(app.conversations.isEmpty)
            } footer: {
                Text("This removes AD Glasses conversation history from this iPhone. It does not delete data held by an AI provider.")
            }
        }
        .navigationTitle("Privacy")
        .navigationBarTitleDisplayMode(.inline)
        .confirmationDialog(
            "Delete all conversations?",
            isPresented: $confirmsDeletion,
            titleVisibility: .visible
        ) {
            Button("Delete all", role: .destructive) {
                Task { await app.deleteAllConversations() }
            }
            Button("Cancel", role: .cancel) {}
        }
    }
}

private struct StorageSettingsView: View {
    @EnvironmentObject private var app: AppModel

    var body: some View {
        List {
            Section("Local data") {
                LabeledContent("Conversations", value: "\(app.conversations.count)")
                LabeledContent("Messages", value: "\(messageCount)")
            }

            Section {
                Text("Captures, recordings, and notes will report their storage here after their repositories are connected. No glasses storage value is guessed or cached by the app.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Storage")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var messageCount: Int {
        app.conversations.reduce(0) { $0 + $1.messages.count }
    }
}

private struct PermissionsSettingsView: View {
    var body: some View {
        List {
            Section("Voice") {
                LabeledContent("Microphone", value: microphoneStatus)
                LabeledContent("Speech recognition", value: speechStatus)
            }

            Section {
                Button("Open iOS Settings") {
                    guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
                    UIApplication.shared.open(url)
                }
            } footer: {
                Text("AD Glasses requests permissions only when a feature needs them. Bluetooth access is managed by iOS when the app scans for glasses.")
            }
        }
        .navigationTitle("Permissions")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var microphoneStatus: String {
        switch AVAudioApplication.shared.recordPermission {
        case .granted: return "Allowed"
        case .denied: return "Denied"
        case .undetermined: return "Not requested"
        @unknown default: return "Unknown"
        }
    }

    private var speechStatus: String {
        switch SFSpeechRecognizer.authorizationStatus() {
        case .authorized: return "Allowed"
        case .denied: return "Denied"
        case .restricted: return "Restricted"
        case .notDetermined: return "Not requested"
        @unknown default: return "Unknown"
        }
    }
}

private struct AboutSettingsView: View {
    var body: some View {
        List {
            Section {
                LabeledContent("App", value: "AD Glasses")
                LabeledContent("Version", value: version)
                LabeledContent("Interface", value: "Native SwiftUI")
                LabeledContent("Minimum iOS", value: "17")
            }

            Section {
                Text("AD Glasses is the quiet companion for your glasses: connection, captured media, voice, and continuity when you need to continue on iPhone.")
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("About")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var version: String {
        let short = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0"
        let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "1"
        return "\(short) (\(build))"
    }
}

private extension GlassesConnectionState {
    var settingsLabel: String {
        switch self {
        case .disconnected: return "Not connected"
        case .scanning: return "Scanning"
        case .connecting: return "Connecting"
        case .connected: return "Connected"
        case .unavailable: return "Unavailable"
        }
    }
}
