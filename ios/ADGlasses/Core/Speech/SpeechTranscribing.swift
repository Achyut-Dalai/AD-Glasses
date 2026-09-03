import AVFoundation
import Foundation

/// Audio-session policy for an intentional, finite speech turn.
///
/// The always-on wake path is gone. Voice input now opens Bluetooth HFP only while the user is
/// actively dictating or the assistant is handling a phone-microphone turn, then releases it as
/// soon as recognition finishes. Prefer the connected glasses/headset microphone when iOS exposes
/// one, matching Android's communication-input policy without keeping that route alive all day.
@MainActor
enum SpeechInputAudioSession {
    static func activate(preferBluetoothHFP: Bool = true) throws {
        let session = AVAudioSession.sharedInstance()

        // Do not set `.defaultToSpeaker` up front. With a connected HFP accessory that option can
        // leave spoken output on the iPhone even after selecting the Bluetooth microphone. First
        // establish a communication session that is eligible for HFP, then choose the HFP input.
        // Because HFP is bidirectional, iOS routes the matching output to the headset as well.
        try session.setCategory(
            .playAndRecord,
            mode: .voiceChat,
            options: [.duckOthers, .allowBluetoothHFP]
        )
        try session.setActive(true, options: .notifyOthersOnDeactivation)

        if preferBluetoothHFP,
           let bluetoothInput = session.availableInputs?.first(where: {
               $0.portType == .bluetoothHFP
           }) {
            try session.setPreferredInput(bluetoothInput)
            try? session.overrideOutputAudioPort(.none)
            return
        }

        // No HFP route is available. Explicitly choose the iPhone speaker as the phone-only
        // fallback rather than making it the default before Bluetooth route selection.
        try? session.setPreferredInput(nil)
        try? session.overrideOutputAudioPort(.speaker)
    }

    static func deactivate() {
        let session = AVAudioSession.sharedInstance()
        try? session.overrideOutputAudioPort(.none)
        try? session.setPreferredInput(nil)
        try? session.setActive(false, options: .notifyOthersOnDeactivation)
    }
}

struct SpeechTranscriptionSnapshot: Equatable, Sendable {
    var transcript: String
    var isRunning: Bool
    var engineName: String
}

enum SpeechTranscriptionError: LocalizedError {
    case microphonePermissionDenied
    case speechPermissionDenied
    case recognizerUnavailable
    case localeUnsupported
    case failedToCreateAudioInput

    var errorDescription: String? {
        switch self {
        case .microphonePermissionDenied:
            return "Microphone permission is required for voice input."
        case .speechPermissionDenied:
            // Retained for protocol/source compatibility. SpeechAnalyzer itself does not use the
            // legacy SFSpeechRecognizer authorization path.
            return "SpeechAnalyzer permission is unavailable."
        case .recognizerUnavailable:
            return "SpeechAnalyzer is unavailable. Voice input requires iOS 26 or later."
        case .localeUnsupported:
            return "The current language is not supported by SpeechAnalyzer."
        case .failedToCreateAudioInput:
            return "The voice audio stream could not be created."
        }
    }
}

@MainActor
protocol SpeechTranscribing: AnyObject {
    var engineName: String { get }
    var snapshot: SpeechTranscriptionSnapshot { get }
    var onUpdate: ((SpeechTranscriptionSnapshot) -> Void)? { get set }
    var onError: ((Error) -> Void)? { get set }

    func start() async throws
    func stop() async
    func resetTranscript()
}

/// Optional input seam for providers that already deliver decoded PCM, such as HeyCyan's BLE
/// Assistant stream. It keeps vendor packets out of SpeechAnalyzer and avoids opening the iPhone
/// microphone for audio that is already arriving from the glasses.
@MainActor
protocol ExternalAudioSpeechTranscribing: SpeechTranscribing {
    func startExternalAudio() async throws
    func appendExternalAudio(_ buffer: AVAudioPCMBuffer)
    func finishExternalAudio() async
}

enum SpeechPermissions {
    static func requestMicrophone() async -> Bool {
        await withCheckedContinuation { continuation in
            AVAudioApplication.requestRecordPermission { granted in
                continuation.resume(returning: granted)
            }
        }
    }

    /// SpeechAnalyzer transcriber modules run on device and do not use the legacy
    /// `SFSpeechRecognizer.requestAuthorization` service permission. Phone input only needs access
    /// to the microphone. External glasses PCM needs no input-device permission at all.
    static func requestAll() async throws {
        guard await requestMicrophone() else {
            throw SpeechTranscriptionError.microphonePermissionDenied
        }
    }

    static func requestRecognition() async throws {
        // Intentionally empty for SpeechAnalyzer external PCM.
    }
}
