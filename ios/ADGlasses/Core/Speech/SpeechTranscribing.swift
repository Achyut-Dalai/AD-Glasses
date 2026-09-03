import AVFoundation
import Foundation

/// Audio-session policy for an intentional, finite speech turn.
///
/// AD can receive Assistant audio over BLE while spoken output travels over the glasses' separate
/// Classic-Bluetooth audio profile. Phone microphone turns open HFP only while recording and never
/// override the output route. All potentially blocking AVAudioSession activation/deactivation work
/// runs off the main actor so iOS cannot stall the UI while negotiating Bluetooth audio.
enum SpeechInputAudioSession {
    static func activate(preferBluetoothHFP: Bool = true) async throws {
        try await Task.detached(priority: .userInitiated) {
            let session = AVAudioSession.sharedInstance()

            // Keep the input route simple: make a communication session HFP-eligible, activate it,
            // then prefer the Bluetooth headset microphone when iOS exposes one. Never use
            // `.defaultToSpeaker` and never force `.speaker` from a transient currentRoute snapshot.
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
        }.value
    }

    static func deactivate() async {
        await Task.detached(priority: .userInitiated) {
            let session = AVAudioSession.sharedInstance()
            try? session.setPreferredInput(nil)
            try? session.setActive(false, options: .notifyOthersOnDeactivation)
        }.value
    }

    static func hasBluetoothOutput(_ route: AVAudioSessionRouteDescription) -> Bool {
        route.outputs.contains { isBluetoothOutputPort($0.portType) }
    }

    static func isBluetoothOutputPort(_ portType: AVAudioSession.Port) -> Bool {
        switch portType {
        case .bluetoothHFP, .bluetoothA2DP, .bluetoothLE:
            return true
        default:
            return false
        }
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
            return "SpeechAnalyzer is unavailable. Voice input requires iOS 27 or later."
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

/// Shared PCM converter used by SpeechAnalyzer input paths. This is kept as an internal helper so
/// both phone microphone buffers and provider-delivered glasses PCM use the same proven conversion
/// behavior without duplicating AVAudioConverter state.
@MainActor
final class SpeechBufferConverter {
    enum ConversionError: LocalizedError {
        case failedToCreateConverter
        case failedToCreateBuffer
        case conversionFailed(NSError?)

        var errorDescription: String? {
            switch self {
            case .failedToCreateConverter:
                return "Could not create an audio converter for SpeechAnalyzer."
            case .failedToCreateBuffer:
                return "Could not allocate an audio buffer for SpeechAnalyzer."
            case .conversionFailed(let error):
                return error?.localizedDescription ?? "Audio conversion for SpeechAnalyzer failed."
            }
        }
    }

    private var converter: AVAudioConverter?

    func convertBuffer(_ buffer: AVAudioPCMBuffer, to format: AVAudioFormat) throws -> AVAudioPCMBuffer {
        let inputFormat = buffer.format
        guard inputFormat != format else {
            return buffer
        }

        if converter == nil || converter?.inputFormat != inputFormat || converter?.outputFormat != format {
            converter = AVAudioConverter(from: inputFormat, to: format)
            converter?.primeMethod = .none
        }

        guard let converter else {
            throw ConversionError.failedToCreateConverter
        }

        let sampleRateRatio = converter.outputFormat.sampleRate / converter.inputFormat.sampleRate
        let scaledInputFrameLength = Double(buffer.frameLength) * sampleRateRatio
        let frameCapacity = max(1, AVAudioFrameCount(scaledInputFrameLength.rounded(.up)))

        guard let conversionBuffer = AVAudioPCMBuffer(
            pcmFormat: converter.outputFormat,
            frameCapacity: frameCapacity
        ) else {
            throw ConversionError.failedToCreateBuffer
        }

        var conversionError: NSError?
        var bufferProcessed = false

        let status = converter.convert(to: conversionBuffer, error: &conversionError) { _, inputStatus in
            defer { bufferProcessed = true }
            inputStatus.pointee = bufferProcessed ? .noDataNow : .haveData
            return bufferProcessed ? nil : buffer
        }

        guard status != .error else {
            throw ConversionError.conversionFailed(conversionError)
        }

        return conversionBuffer
    }

    func reset() {
        converter = nil
    }
}
