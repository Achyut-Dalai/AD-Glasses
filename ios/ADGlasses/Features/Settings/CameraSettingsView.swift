import SwiftUI

@MainActor
struct CameraSettingsView: View {
    @ObservedObject var store: CameraCaptureSettingsStore
    @EnvironmentObject private var glasses: GlassesManager
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        List {
            Section {
                LabeledContent("Photo Capture Quality", value: "Full Sensor Resolution (100%)")
                Picker("AI Vision Quality", selection: $store.aiVisionQuality) {
                    ForEach(GlassesAIVisionQuality.allCases) { quality in
                        Text(quality.label).tag(quality)
                    }
                }
                .pickerStyle(.navigationLink)
            } header: {
                Text("Photography & Vision")
            } footer: {
                Text("Photos saved to your Library are always preserved in 100% original full sensor resolution. AI Vision Quality adjusts the frame sent to Cloud AI for faster voice answers vs reading fine print.")
            }

            Section {
                Picker("Video Duration Limit", selection: $store.videoDurationLimit) {
                    ForEach(GlassesVideoDurationLimit.allCases) { limit in
                        Text(limit.label).tag(limit)
                    }
                }
                .pickerStyle(.navigationLink)
            } header: {
                Text("Video Recording")
            } footer: {
                Text("Maximum duration for single video recordings triggered from the hardware button or app. Sensor records natively at full 1080p/4K bitrate to local flash NAND.")
            }

            Section {
                Picker("Audio Duration Limit", selection: $store.audioDurationLimit) {
                    ForEach(GlassesAudioDurationLimit.allCases) { limit in
                        Text(limit.label).tag(limit)
                    }
                }
                .pickerStyle(.navigationLink)
            } header: {
                Text("Audio Recording")
            } footer: {
                Text("Maximum duration for standalone audio recordings saved to glasses flash memory.")
            }



            Section {
                HStack(spacing: 12) {
                    Image(systemName: "wifi")
                        .foregroundStyle(.blue)
                        .font(.title3)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Wi-Fi Transfer Pipeline")
                            .font(.subheadline)
                            .fontWeight(.medium)
                        Text("AD Glasses save full 12MP captures directly to onboard storage. Transfers connect to the glasses Wi-Fi AP to download pristine files without lossy compression.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                .padding(.vertical, 4)
            } header: {
                Text("Hardware Architecture")
            }
        }
        .navigationTitle("Camera & Capture")
        .navigationBarTitleDisplayMode(.inline)
    }
}
