import Combine
import Foundation

@MainActor
final class CameraCaptureSettingsStore: ObservableObject {
    private static let photoQualityKey = "adglasses.settings.camera.photo_quality"
    private static let aiVisionQualityKey = "adglasses.settings.camera.ai_vision_quality"
    private static let ingestionModeKey = "adglasses.settings.camera.ingestion_mode"
    private static let videoDurationLimitKey = "adglasses.settings.camera.video_duration_limit"
    private static let audioDurationLimitKey = "adglasses.settings.camera.audio_duration_limit"
    private static let fullQualityExportDefaultKey = "adglasses.settings.camera.full_quality_export"

    /// Physical photos are always captured and saved in full pristine sensor resolution
    public let photoQuality: GlassesPhotoQuality = .fullResolution

    @Published var aiVisionQuality: GlassesAIVisionQuality {
        didSet {
            UserDefaults.standard.set(aiVisionQuality.rawValue, forKey: Self.aiVisionQualityKey)
        }
    }

    @Published var ingestionMode: GlassesCaptureIngestionMode {
        didSet {
            UserDefaults.standard.set(ingestionMode.rawValue, forKey: Self.ingestionModeKey)
        }
    }

    @Published var videoDurationLimit: GlassesVideoDurationLimit {
        didSet {
            UserDefaults.standard.set(videoDurationLimit.rawValue, forKey: Self.videoDurationLimitKey)
        }
    }

    @Published var audioDurationLimit: GlassesAudioDurationLimit {
        didSet {
            UserDefaults.standard.set(audioDurationLimit.rawValue, forKey: Self.audioDurationLimitKey)
        }
    }

    @Published var fullQualityPreservedByDefault: Bool {
        didSet {
            UserDefaults.standard.set(fullQualityPreservedByDefault, forKey: Self.fullQualityExportDefaultKey)
        }
    }

    init() {
        let savedAIVisionQuality = UserDefaults.standard.string(forKey: Self.aiVisionQualityKey)
            .flatMap(GlassesAIVisionQuality.init(rawValue:)) ?? .fast

        let savedIngestionMode = UserDefaults.standard.string(forKey: Self.ingestionModeKey)
            .flatMap(GlassesCaptureIngestionMode.init(rawValue:)) ?? .wifiSync

        let savedVideoDuration = UserDefaults.standard.integer(forKey: Self.videoDurationLimitKey)
        let resolvedVideoDuration = GlassesVideoDurationLimit(rawValue: savedVideoDuration) ?? .sixtySeconds

        let savedAudioDuration = UserDefaults.standard.integer(forKey: Self.audioDurationLimitKey)
        let resolvedAudioDuration = GlassesAudioDurationLimit(rawValue: savedAudioDuration) ?? .oneHour

        let savedFullQuality = UserDefaults.standard.object(forKey: Self.fullQualityExportDefaultKey) as? Bool ?? true

        self.aiVisionQuality = savedAIVisionQuality
        self.ingestionMode = savedIngestionMode
        self.videoDurationLimit = resolvedVideoDuration
        self.audioDurationLimit = resolvedAudioDuration
        self.fullQualityPreservedByDefault = savedFullQuality
    }
}
