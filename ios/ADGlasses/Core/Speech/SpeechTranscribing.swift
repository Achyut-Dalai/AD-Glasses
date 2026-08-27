import AVFoundation
import Foundation
import Speech

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
            AVAudioSession.sharedInstance().requestRecordPermission { granted in
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
}
