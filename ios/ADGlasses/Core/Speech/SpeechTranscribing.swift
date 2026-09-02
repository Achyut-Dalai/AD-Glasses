import AVFoundation
import Foundation
import Speech

/// Audio-session policy for an intentional, finite speech turn.
///
/// The always-on wake path is gone. Voice input now opens Bluetooth HFP only while the user is
/// actively dictating or the assistant is handling a glasses-triggered turn, then releases it as
/// soon as recognition finishes. Prefer the connected glasses/headset microphone when iOS exposes
/// one, matching Android's communication-input policy without keeping that route alive all day.
@MainActor
enum SpeechInputAudioSession {
    static func activate(preferBluetoothHFP: Bool = true) throws {
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(
            .playAndRecord,
            mode: .voiceChat,
            options: [.duckOthers, .allowBluetoothHFP]
        )
        try session.setActive(true, options: .notifyOthersOnDeactivation)

        guard preferBluetoothHFP,
              let bluetoothInput = session.availableInputs?.first(where: {
                  $0.portType == .bluetoothHFP
              }) else {
            return
        }
        try? session.setPreferredInput(bluetoothInput)
    }

    static func deactivate() {
        let session = AVAudioSession.sharedInstance()
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
            return "Microphone permission is required for transcription."
        case .speechPermissionDenied:
            return "Speech recognition permission is required for transcription."
        case .recognizerUnavailable:
            return "Apple speech recognition is currently unavailable."
        case .localeUnsupported:
            return "The current language is not supported by the selected Apple speech engine."
        case .failedToCreateAudioInput:
            return "The microphone audio stream could not be created."
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
/// Assistant stream. It keeps vendor packets out of Apple speech implementations and avoids
/// opening the iPhone microphone for audio that is already arriving from the glasses.
@MainActor
protocol ExternalAudioSpeechTranscribing: SpeechTranscribing {
    func startExternalAudio() async throws
    func appendExternalAudio(_ buffer: AVAudioPCMBuffer)
    func finishExternalAudio() async
}

enum SpeechPermissions {
    static func requestSpeechRecognition() async -> Bool {
        await withCheckedContinuation { continuation in
            SFSpeechRecognizer.requestAuthorization { status in
                continuation.resume(returning: status == .authorized)
            }
        }
    }

    static func requestMicrophone() async -> Bool {
        await withCheckedContinuation { continuation in
            AVAudioApplication.requestRecordPermission { granted in
                continuation.resume(returning: granted)
            }
        }
    }

    static func requestAll() async throws {
        guard await requestMicrophone() else {
            throw SpeechTranscriptionError.microphonePermissionDenied
        }
        guard await requestSpeechRecognition() else {
            throw SpeechTranscriptionError.speechPermissionDenied
        }
    }

    static func requestRecognition() async throws {
        guard await requestSpeechRecognition() else {
            throw SpeechTranscriptionError.speechPermissionDenied
        }
    }
}
