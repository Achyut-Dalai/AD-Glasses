import AVFoundation
import CoreLocation
import Speech
import SwiftUI

@MainActor
struct SettingsView: View {
    @EnvironmentObject private var app: AppModel
    @EnvironmentObject private var glasses: GlassesManager
    @EnvironmentObject private var phoneVoiceActivation: PhoneVoiceActivationController
    @Environment(\.dismiss) private var dismiss
    @StateObject private var grounding = GroundingSettingsStore()
    @State private var diagnosticsEnabled = false
    @State private var diagnosticsURL: URL?
    @State private var diagnosticsError: String?

    var body: some View {
        NavigationStack {
            List {
                Section("Glasses") {
                    ForEach(glasses.providers) { provider in
                        LabeledContent(provider.displayName, value: provider.connectionState.settingsLabel)
                    }
                }

                Section("Jarvis") {
                    NavigationLink {
                        CloudAISettingsView(store: app.aiProfiles)
                    } label: {
                        LabeledContent("Cloud AI", value: cloudAIStatus)
                    }

                    NavigationLink {
                        SearchAndMapsSettingsView(store: grounding)
                    } label: {
                        LabeledContent("Search & Maps", value: groundingStatus)
                    }

                    NavigationLink {
                        TransportGroundingSettingsView(store: TransportGroundingSettingsStore.shared)
                    } label: {
                        LabeledContent("Travel & Transit", value: "Rail · flights · realtime")
                    }

                    LabeledContent("Speech engine", value: app.speechEngineName)

                    NavigationLink {
                        SpeechVoiceSettingsView(controller: app.speechOutput)
                    } label: {
                        LabeledContent("Spoken voice", value: selectedSpeechVoiceName)
                    }

                    NavigationLink {
                        PhoneVoiceActivationSettingsView(controller: phoneVoiceActivation)
                    } label: {
                        LabeledContent("Phone voice activation", value: phoneVoiceActivation.configurationState.label)
                    }
                }

                Section("Data and access") {
                    NavigationLink("Privacy") { PrivacySettingsView() }
                    NavigationLink("Storage") { StorageSettingsView() }
                    NavigationLink("Permissions") { PermissionsSettingsView() }
                    Button("Language") { openSystemSettings() }
                }

                Section {
                    NavigationLink("About AD Glasses") { AboutSettingsView() }
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

                        Button {
                            diagnosticsEnabled = true
                            Task {
                                await glasses.setHardwareDiagnosticsEnabled(true)
                                await glasses.runPassiveDiscoveryDiagnostics()
                                await refreshDiagnosticsURL()
                            }
                        } label: {
                            if glasses.isPassiveDiagnosticsScanRunning {
                                HStack {
                                    ProgressView()
                                    Text("Passive scan in progress…")
                                }
                            } else {
                                Label("Run 60-second passive BLE scan", systemImage: "wave.3.right")
                            }
                        }
                        .disabled(glasses.isPassiveDiagnosticsScanRunning)

                        Text("The passive scan records advertisement time, peripheral identifier, RSSI, and whether the verified AD Glasses service was advertised. It never connects or writes.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)

                        Text("Enable this only while validating physical glasses. The bounded log includes raw BLE bytes and may contain device or glasses-network details. It never includes Cloud AI keys; share an export only with people you trust.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }

                    DisclosureGroup("Provider details") {
                        ForEach(glasses.providers) { provider in
                            LabeledContent(glasses.technicalProviderName(for: provider.id), value: provider.id)
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
                grounding.reload()
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

    private var groundingStatus: String {
        if grounding.tavilyEnabled {
            return grounding.hasTavilyAPIKey ? "Web + maps" : "Maps · web key needed"
        }
        return "Maps only"
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

@MainActor
private struct PhoneVoiceActivationSettingsView: View {
    @ObservedObject var controller: PhoneVoiceActivationController

    var body: some View {
        List {
            Section("Voice activation") {
                Toggle("Phone Voice Activation", isOn: voiceActivationBinding)
                    .disabled(voiceActivationControlIsDisabled)
                LabeledContent("Wake phrase", value: controller.phrase)
                LabeledContent("Listening", value: controller.isListening ? "On" : "Off")
                LabeledContent("Status", value: controller.configurationState.label)

                if controller.configurationState == .missingModel {
                    Text("This build does not contain the evaluated Jarvis classifier. Release builds use the Jarvis ONNX model bundled with AD Glasses; users are never asked to choose a model file.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }

                Text("Starts while AD Glasses is connected and the app is open, then stays available when you switch apps or lock your iPhone. It pauses for transcription and spoken responses; AI and Apple Speech do not start until the wake phrase is detected. It stops after a disconnect, when disabled, or if you force-quit the app.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            Section("LiveKit WakeWord") {
                LabeledContent("Engine", value: "LiveKit WakeWord")
                Text("Wake-word detection runs locally on this iPhone. No account, API key, hosted wake-word service, or user-selected model file is required.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Phone voice activation")
        .navigationBarTitleDisplayMode(.inline)
        .alert("Phone voice activation", isPresented: Binding(
            get: { controller.errorMessage != nil },
            set: { if !$0 { controller.errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(controller.errorMessage ?? "")
        }
    }

    private var voiceActivationBinding: Binding<Bool> {
        Binding(
            get: { controller.isEnabled },
            set: { controller.isEnabled = $0 }
        )
    }

    private var voiceActivationControlIsDisabled: Bool {
        switch controller.configurationState {
        case .ready: return false
        case .missingModel, .unavailable: return true
        }
    }
}

@MainActor
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
                        do { try controller.speak("Jarvis is ready when you need it.") }
                        catch { errorMessage = error.localizedDescription }
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

@MainActor
private struct CloudAISettingsView: View {
    @ObservedObject var store: AIProfileStore
    @State private var errorMessage: String?

    var body: some View {
        List {
            if store.profiles.isEmpty {
                ContentUnavailableView(
                    "No Cloud AI profile",
                    systemImage: "cloud",
                    description: Text("Add a provider and API key, then fetch the models available to that account.")
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
                                do { try store.delete(profile.id) }
                                catch { errorMessage = error.localizedDescription }
                            }
                        }
                    }
                }
            }

            Section {
                NavigationLink {
                    AIProfileEditorView(store: store, profile: .new(existingCount: store.profiles.count))
                } label: {
                    Label("Add Cloud AI profile", systemImage: "plus")
                }
            }

            Section {
                Text("API keys are stored in the iOS Keychain and are never displayed after saving. Model lists are fetched directly from the selected provider using that profile's key; manual model IDs remain available for providers with incomplete catalogs. Lens visual understanding uses this same active profile when its selected model supports image input.")
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

@MainActor
private struct AIProfileEditorView: View {
    @ObservedObject var store: AIProfileStore
    @Environment(\.dismiss) private var dismiss

    @State private var profile: AIProfile
    @State private var apiKey = ""
    @State private var makeActive: Bool
    @State private var availableModels = [String]()
    @State private var isLoadingModels = false
    @State private var modelStatus: String?
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
            }

            Section {
                if !availableModels.isEmpty {
                    Picker("Available model", selection: $profile.model) {
                        ForEach(modelChoices, id: \.self) { model in
                            Text(model).tag(model)
                        }
                    }
                    .pickerStyle(.navigationLink)
                }

                TextField("Model ID", text: $profile.model)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()

                Button {
                    Task { await fetchModels() }
                } label: {
                    HStack(spacing: 9) {
                        if isLoadingModels { ProgressView().controlSize(.small) }
                        Label(isLoadingModels ? "Fetching models…" : "Fetch available models", systemImage: "arrow.clockwise")
                    }
                }
                .disabled(isLoadingModels || isTesting)

                if let modelStatus {
                    Text(modelStatus)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            } header: {
                Text("Model")
            } footer: {
                Text("The provider model API is the source of truth. Keep manual model ID as a fallback for custom or newly released models. For Lens scene understanding, choose a model that accepts image input.")
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
                        if isTesting { ProgressView().controlSize(.small) }
                        Text(isTesting ? "Testing connection…" : "Save and test")
                    }
                }
                .disabled(isTesting || isLoadingModels)
            } footer: {
                Text("Saves this profile, then sends a short request through the same connection Jarvis uses.")
            }
        }
        .navigationTitle(store.profiles.contains(where: { $0.id == profile.id }) ? "Edit profile" : "New profile")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button("Save", action: save)
                    .fontWeight(.semibold)
                    .disabled(isTesting || isLoadingModels)
            }
        }
        .onChange(of: profile.provider) { oldProvider, newProvider in
            guard oldProvider != newProvider else { return }
            availableModels.removeAll()
            modelStatus = nil
            profile.model = newProvider.defaultModel
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

    private var modelChoices: [String] {
        if profile.model.isEmpty || availableModels.contains(profile.model) { return availableModels }
        return ([profile.model] + availableModels).removingDuplicates()
    }

    private var apiKeyPrompt: String {
        store.hasCredential(for: profile.id) ? "New key (optional)" : "API key"
    }

    private var existingCredentialNote: String {
        store.hasCredential(for: profile.id)
            ? "A key is already stored. Leave this blank to keep it. Saved keys are never read back into this field."
            : "The key is stored in Keychain and is never shown again."
    }

    private func fetchModels() async {
        isLoadingModels = true
        modelStatus = nil
        defer { isLoadingModels = false }

        do {
            var discoveryProfile = profile
            if discoveryProfile.provider.managesEndpoint {
                discoveryProfile.baseURL = discoveryProfile.provider.defaultBaseURL
            }
            let credential = try store.credentialForDiscovery(profile: discoveryProfile, replacement: apiKey)
            let models = try await AIModelCatalogClient().availableModels(profile: discoveryProfile, credential: credential)
            guard !Task.isCancelled else { return }
            availableModels = models
            if models.isEmpty {
                modelStatus = "The provider returned no conversational models. You can still enter a supported model ID manually."
            } else {
                let preferred = models.first(where: { $0 == profile.provider.defaultModel })
                    ?? models.first(where: { $0 == profile.model })
                    ?? models.first
                if let preferred, profile.model.isEmpty || !models.contains(profile.model) {
                    profile.model = preferred
                }
                modelStatus = "Found \(models.count) conversational model\(models.count == 1 ? "" : "s") for this key."
            }
        } catch {
            modelStatus = "Could not fetch models: \(error.localizedDescription)"
        }
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
        let savedProfile = try store.save(profile, apiKeyReplacement: apiKey, makeActive: makeActive)
        profile = savedProfile
        apiKey = ""
        return savedProfile
    }
}

@MainActor
private struct SearchAndMapsSettingsView: View {
    @ObservedObject var store: GroundingSettingsStore
    @ObservedObject private var location = GroundingLocationProvider.shared

    @State private var tavilyReplacement = ""
    @State private var statusMessage: String?
    @State private var errorMessage: String?
    @State private var isTestingTavily = false

    var body: some View {
        List {
            Section {
                Toggle("Use Tavily for live web evidence", isOn: tavilyEnabledBinding)
                LabeledContent("API key", value: store.hasTavilyAPIKey ? "Stored" : "Not configured")
                SecureField(store.hasTavilyAPIKey ? "Replacement key (optional)" : "Tavily API key", text: $tavilyReplacement)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()

                Button { saveTavilyKey() } label: {
                    Label(store.hasTavilyAPIKey ? "Save replacement key" : "Save Tavily key", systemImage: "key")
                }
                .disabled(tavilyReplacement.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)

                Button { Task { await testTavily() } } label: {
                    HStack(spacing: 9) {
                        if isTestingTavily { ProgressView().controlSize(.small) }
                        Text(isTestingTavily ? "Testing Tavily…" : "Test Tavily retrieval")
                    }
                }
                .disabled(!store.tavilyEnabled || !store.hasTavilyAPIKey || isTestingTavily)

                if store.hasTavilyAPIKey {
                    Button("Remove Tavily key", role: .destructive) {
                        do {
                            try store.clearTavilyAPIKey()
                            statusMessage = "Tavily key removed."
                        } catch {
                            errorMessage = error.localizedDescription
                        }
                    }
                }
            } header: {
                Text("Web search")
            } footer: {
                Text("Tavily is retrieval-only. AD Glasses requests source snippets and URLs with Tavily answer generation disabled; your selected Cloud AI model remains the only model that writes Jarvis's answer.")
            }

            Section {
                LabeledContent("Permission", value: locationPermissionLabel)
                if location.authorizationStatus == .notDetermined {
                    Button("Allow location for nearby places") { location.requestPermission() }
                } else if !location.isAuthorized {
                    Button("Open iOS Settings") {
                        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
                        UIApplication.shared.open(url)
                    }
                }
            } header: {
                Text("Location")
            } footer: {
                Text("Location is requested only from this Settings action. A Jarvis question never triggers the iOS permission prompt by itself.")
            }

            Section {
                TextField("Nominatim base URL", text: $store.nominatimBaseURL)
                    .textInputAutocapitalization(.never)
                    .keyboardType(.URL)
                    .autocorrectionDisabled()
                TextField("Overpass endpoint", text: $store.overpassEndpoint)
                    .textInputAutocapitalization(.never)
                    .keyboardType(.URL)
                    .autocorrectionDisabled()
                TextField("OSRM base URL", text: $store.osrmBaseURL)
                    .textInputAutocapitalization(.never)
                    .keyboardType(.URL)
                    .autocorrectionDisabled()

                Button("Save map service endpoints") { saveEndpoints() }
                Button("Restore public defaults") {
                    store.nominatimBaseURL = GroundingSettingsStore.defaultNominatimBaseURL
                    store.overpassEndpoint = GroundingSettingsStore.defaultOverpassEndpoint
                    store.osrmBaseURL = GroundingSettingsStore.defaultOSRMBaseURL
                    saveEndpoints()
                }
            } header: {
                Text("OpenStreetMap services")
            } footer: {
                Text("Nominatim is used only for user-triggered geocoding and is rate-limited to about one request per second. Nearby POIs use bounded Overpass queries; routes use FOSSGIS OSRM. Map-derived answers should attribute © OpenStreetMap contributors.")
            }

            if let statusMessage {
                Section {
                    Text(statusMessage)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .navigationTitle("Search & Maps")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { store.reload() }
        .alert("Search & Maps", isPresented: Binding(
            get: { errorMessage != nil },
            set: { if !$0 { errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(errorMessage ?? "")
        }
    }

    private var tavilyEnabledBinding: Binding<Bool> {
        Binding(get: { store.tavilyEnabled }, set: { store.setTavilyEnabled($0) })
    }

    private var locationPermissionLabel: String {
        switch location.authorizationStatus {
        case .authorizedAlways, .authorizedWhenInUse: return "Allowed"
        case .denied: return "Denied"
        case .restricted: return "Restricted"
        case .notDetermined: return "Not requested"
        @unknown default: return "Unknown"
        }
    }

    private func saveTavilyKey() {
        do {
            try store.replaceTavilyAPIKey(tavilyReplacement)
            tavilyReplacement = ""
            statusMessage = "Tavily key saved securely in Keychain."
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func saveEndpoints() {
        do {
            try store.saveEndpoints(
                nominatimBaseURL: store.nominatimBaseURL,
                overpassEndpoint: store.overpassEndpoint,
                osrmBaseURL: store.osrmBaseURL
            )
            statusMessage = "Search & Maps endpoints saved."
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func testTavily() async {
        isTestingTavily = true
        defer { isTestingTavily = false }
        do {
            let service = AssistantGroundingService(settings: store, location: location)
            let count = try await service.testTavily()
            statusMessage = count > 0
                ? "Tavily retrieval succeeded."
                : "Tavily responded, but no usable result was returned."
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

@MainActor
private struct TransportGroundingSettingsView: View {
    @ObservedObject var store: TransportGroundingSettingsStore
    @State private var railKey = ""
    @State private var aviationKey = ""
    @State private var railHost = ""
    @State private var aviationBaseURL = ""
    @State private var statusMessage: String?
    @State private var errorMessage: String?

    var body: some View {
        List {
            Section {
                LabeledContent("RapidAPI key", value: store.hasRailKey ? "Stored" : "Not configured")
                SecureField(store.hasRailKey ? "Replacement key (optional)" : "RapidAPI key", text: $railKey)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                TextField("RapidAPI host", text: $railHost)
                    .textInputAutocapitalization(.never)
                    .keyboardType(.URL)
                    .autocorrectionDisabled()
                Button("Save Rail settings") { saveRail() }
                if store.hasRailKey {
                    Button("Remove Rail key", role: .destructive) {
                        do {
                            try store.clearRailKey()
                            statusMessage = "Rail key removed."
                        } catch {
                            errorMessage = error.localizedDescription
                        }
                    }
                }
            } header: {
                Text("Indian Railways")
            } footer: {
                Text("Used only for an explicit train-number or 10-digit PNR request. The key is stored in Keychain and is never displayed after saving.")
            }

            Section {
                LabeledContent("AviationStack key", value: store.hasAviationKey ? "Stored" : "Not configured")
                SecureField(store.hasAviationKey ? "Replacement key (optional)" : "AviationStack access key", text: $aviationKey)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                TextField("AviationStack base URL", text: $aviationBaseURL)
                    .textInputAutocapitalization(.never)
                    .keyboardType(.URL)
                    .autocorrectionDisabled()
                Button("Save Flight settings") { saveAviation() }
                if store.hasAviationKey {
                    Button("Remove AviationStack key", role: .destructive) {
                        do {
                            try store.clearAviationKey()
                            statusMessage = "AviationStack key removed."
                        } catch {
                            errorMessage = error.localizedDescription
                        }
                    }
                }
            } header: {
                Text("Flights")
            } footer: {
                Text("Used only when Jarvis recognizes an explicit flight-status request. The selected Cloud AI model summarizes the structured provider record; it does not invent live flight data.")
            }

            Section {
                if store.gtfsFeeds.isEmpty {
                    Text("No realtime transit feeds configured.")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(store.gtfsFeeds) { feed in
                        NavigationLink {
                            GTFSFeedEditorView(store: store, feed: feed)
                        } label: {
                            VStack(alignment: .leading, spacing: 3) {
                                Text(feed.label)
                                Text(publicFeedURL(feed.url))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                        }
                        .swipeActions {
                            Button("Delete", role: .destructive) {
                                do { try store.deleteGTFSFeed(id: feed.id) }
                                catch { errorMessage = error.localizedDescription }
                            }
                        }
                    }
                }

                NavigationLink {
                    GTFSFeedEditorView(store: store, feed: nil)
                } label: {
                    Label("Add GTFS-Realtime feed", systemImage: "plus")
                }
            } header: {
                Text("GTFS-Realtime feeds")
            } footer: {
                Text("Add agency-provided GTFS-Realtime protobuf feeds for trip updates, service alerts, and nearby vehicle positions. Optional authentication headers are stored in Keychain. Feed URLs may contain query tokens; Jarvis strips query strings from source attribution.")
            }

            Section {
                Button("Restore service defaults") {
                    railHost = TransportGroundingSettingsStore.defaultRailHost
                    aviationBaseURL = TransportGroundingSettingsStore.defaultAviationBaseURL
                    do {
                        try store.saveRailHost(railHost)
                        try store.saveAviationBaseURL(aviationBaseURL)
                        statusMessage = "Service defaults restored."
                    } catch {
                        errorMessage = error.localizedDescription
                    }
                }

                if let statusMessage {
                    Text(statusMessage)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .navigationTitle("Travel & Transit")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            store.reload()
            railHost = store.railHost
            aviationBaseURL = store.aviationBaseURL
        }
        .alert("Travel & Transit", isPresented: Binding(
            get: { errorMessage != nil },
            set: { if !$0 { errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(errorMessage ?? "")
        }
    }

    private func saveRail() {
        do {
            try store.saveRailHost(railHost)
            if !railKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                try store.replaceRailKey(railKey)
                railKey = ""
            }
            railHost = store.railHost
            statusMessage = "Rail settings saved."
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func saveAviation() {
        do {
            try store.saveAviationBaseURL(aviationBaseURL)
            if !aviationKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                try store.replaceAviationKey(aviationKey)
                aviationKey = ""
            }
            aviationBaseURL = store.aviationBaseURL
            statusMessage = "Flight settings saved."
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func publicFeedURL(_ raw: String) -> String {
        guard var components = URLComponents(string: raw) else { return raw }
        components.query = nil
        components.user = nil
        components.password = nil
        return components.url?.absoluteString ?? raw
    }
}

@MainActor
private struct GTFSFeedEditorView: View {
    @ObservedObject var store: TransportGroundingSettingsStore
    @Environment(\.dismiss) private var dismiss
    @State private var draft: GTFSRealtimeFeedConfig
    @State private var headerValue = ""
    @State private var errorMessage: String?

    init(store: TransportGroundingSettingsStore, feed: GTFSRealtimeFeedConfig?) {
        self.store = store
        var value = feed ?? .new()
        value.headerValue = nil
        _draft = State(initialValue: value)
    }

    var body: some View {
        Form {
            Section("Feed") {
                TextField("Label", text: $draft.label)
                TextField("HTTPS feed URL", text: $draft.url)
                    .textInputAutocapitalization(.never)
                    .keyboardType(.URL)
                    .autocorrectionDisabled()
            }

            Section("Optional authentication header") {
                TextField("Header name, e.g. Authorization", text: Binding(
                    get: { draft.headerName ?? "" },
                    set: { draft.headerName = $0.isEmpty ? nil : $0 }
                ))
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()

                SecureField("Header value", text: $headerValue)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()

                Text("Leave the value blank when editing to keep the existing encrypted value, provided the header name is unchanged.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle(draft.label.isEmpty ? "Add realtime feed" : "Edit realtime feed")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button("Save") {
                    do {
                        try store.saveGTFSFeed(draft, headerValueReplacement: headerValue)
                        dismiss()
                    } catch {
                        errorMessage = error.localizedDescription
                    }
                }
                .fontWeight(.semibold)
            }
        }
        .alert("GTFS-Realtime feed", isPresented: Binding(
            get: { errorMessage != nil },
            set: { if !$0 { errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(errorMessage ?? "")
        }
    }
}

@MainActor
private struct PrivacySettingsView: View {
    @EnvironmentObject private var app: AppModel
    @State private var confirmsDeletion = false

    var body: some View {
        List {
            Section("On this iPhone") {
                LabeledContent("Conversations", value: "\(app.conversations.count)")
                Text("Conversation text is stored locally so Jarvis history survives app restarts.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            Section {
                Button("Delete all conversations", role: .destructive) { confirmsDeletion = true }
                    .disabled(app.conversations.isEmpty)
            } footer: {
                Text("This removes AD Glasses conversation history from this iPhone. It does not delete data held by an AI provider.")
            }
        }
        .navigationTitle("Privacy")
        .navigationBarTitleDisplayMode(.inline)
        .confirmationDialog("Delete all conversations?", isPresented: $confirmsDeletion, titleVisibility: .visible) {
            Button("Delete all", role: .destructive) { Task { await app.deleteAllConversations() } }
            Button("Cancel", role: .cancel) {}
        }
    }
}

@MainActor
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

@MainActor
private struct PermissionsSettingsView: View {
    @ObservedObject private var location = GroundingLocationProvider.shared

    var body: some View {
        List {
            Section("Voice") {
                LabeledContent("Microphone", value: microphoneStatus)
                LabeledContent("Speech recognition", value: speechStatus)
            }
            Section("Search & Maps") {
                LabeledContent("Location", value: locationStatus)
            }
            Section {
                Button("Open iOS Settings") {
                    guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
                    UIApplication.shared.open(url)
                }
            } footer: {
                Text("AD Glasses requests permissions only when a feature needs them. Location permission is requested explicitly from Search & Maps settings; asking Jarvis a location question never auto-prompts. Bluetooth access is managed by iOS when the app scans for glasses.")
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

    private var locationStatus: String {
        switch location.authorizationStatus {
        case .authorizedAlways, .authorizedWhenInUse: return "Allowed"
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
                Text("AD Glasses is the quiet companion for your glasses: connection, captured media, Jarvis, Lens, grounded search and maps, translation, and continuity when you need to continue on iPhone.")
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

private extension Array where Element: Hashable {
    func removingDuplicates() -> [Element] {
        var seen = Set<Element>()
        return filter { seen.insert($0).inserted }
    }
}
