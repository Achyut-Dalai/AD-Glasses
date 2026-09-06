import SwiftUI

struct SoundbiteView: View {
    @EnvironmentObject private var app: AppModel
    @EnvironmentObject private var library: LibraryModel
    @Environment(\.dismiss) private var dismiss

    @State private var title = ""
    @State private var saved = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Optional title", text: $title)

                    Button(
                        app.isTranscribing ? "Stop listening" : "Start listening",
                        systemImage: app.isTranscribing ? "stop.circle.fill" : "waveform.circle.fill"
                    ) {
                        Task {
                            if !app.isTranscribing {
                                app.clearTranscript()
                            }
                            await app.toggleTranscription()
                        }
                    }
                    .tint(app.isTranscribing ? .red : .orange)
                } header: {
                    Text("Soundbite")
                } footer: {
                    Text("Soundbites turns speech into a local note. The same note store will accept glasses audio once its BLE Opus stream is verified.")
                }

                Section("Transcript") {
                    if app.transcript.isEmpty {
                        ContentUnavailableView(
                            "Nothing heard yet",
                            systemImage: "quote.bubble",
                            description: Text("Start listening and speak naturally.")
                        )
                    } else {
                        Text(app.transcript)
                            .textSelection(.enabled)
                    }
                }

                Section {
                    Button("Save to Library", systemImage: "square.and.arrow.down") {
                        Task { await save() }
                    }
                    .disabled(app.transcript.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
            .navigationTitle("Soundbite")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .onDisappear {
                Task { await app.stopTranscription() }
            }
            .alert("Soundbite saved", isPresented: $saved) {
                Button("Done") { dismiss() }
                Button("Keep recording", role: .cancel) {
                    app.clearTranscript()
                    title = ""
                }
            } message: {
                Text("The transcript is now available in Library.")
            }
            .alert("Soundbite", isPresented: Binding(
                get: { errorMessage != nil },
                set: { if !$0 { errorMessage = nil } }
            )) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(errorMessage ?? "")
            }
        }
    }

    private func save() async {
        await app.stopTranscription()
        if await library.saveSoundbite(title: title, transcript: app.transcript) {
            saved = true
        } else {
            errorMessage = library.errorMessage ?? "The Soundbite could not be saved."
        }
    }
}
