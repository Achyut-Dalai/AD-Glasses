//
//  SpeechTranscribing.swift
//  ADGlasses
//

@preconcurrency import AVFoundation
import Foundation
import Speech

struct SpeechTranscriptionSnapshot: Equatable {
    var transcript: String
    var isRunning: Bool
    var engineName: String
}

@MainActor
protocol SpeechTranscribing: AnyObject {
    var snapshot: SpeechTranscriptionSnapshot { get }
    var engineName: String { get }
    var onUpdate: ((SpeechTranscriptionSnapshot) -> Void)? { get set }
    var onError: ((Error) -> Void)? { get set }

    func start() async throws
    func stop() async
    func resetTranscript()
}

@MainActor
protocol ExternalAudioSpeechTranscribing: SpeechTranscribing {
    func startExternalAudio() async throws
    func appendExternalAudio(_ buffer: AVAudioPCMBuffer)
    func finishExternalAudio() async
}

enum SpeechTranscriptionError: LocalizedError, Equatable {
    case permissionDenied
    case speechRecognitionUnavailable
    case failedToCreateAudioInput
    case failedToStartAudioEngine
    case emptyAudioStream

    var errorDescription: String? {
        switch self {
        case .permissionDenied:
            return "Microphone or speech recognition permission was denied."
        case .speechRecognitionUnavailable:
            return "Speech recognition is currently unavailable on this device."
        case .failedToCreateAudioInput:
            return "Unable to access the device microphone."
        case .failedToStartAudioEngine:
            return "Failed to start the audio recording engine."
        case .emptyAudioStream:
            return "No audio was captured for speech recognition."
        }
    }
}

@MainActor
enum SpeechInputAudioSession {
    private static var activeCount = 0

    static func activate() throws {
        activeCount += 1
        guard activeCount == 1 else { return }

        let session = AVAudioSession.sharedInstance()
        try session.setCategory(
            .playAndRecord,
            mode: .measurement,
            options: [.duckOthers, .defaultToSpeaker, .allowBluetooth]
        )
        try session.setActive(true, options: .notifyOthersOnDeactivation)
    }

    static func deactivate() {
        guard activeCount > 0 else { return }
        activeCount -= 1
        guard activeCount == 0 else { return }

        let session = AVAudioSession.sharedInstance()
        do {
            try session.setActive(false, options: .notifyOthersOnDeactivation)
        } catch {
            // Best effort deactivation; do not throw to callers cleaning up.
        }
    }
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
            throw SpeechTranscriptionError.permissionDenied
        }
        guard await requestSpeechRecognition() else {
            throw SpeechTranscriptionError.permissionDenied
        }
    }

    static func requestRecognition() async throws {
        guard await requestSpeechRecognition() else {
            throw SpeechTranscriptionError.permissionDenied
        }
    }
}

final class SpeechBufferConverter {
    enum ConversionError: LocalizedError {
        case failedToCreateConverter
        case failedToCreateBuffer
        case conversionFailed(Error?)

        var errorDescription: String? {
            switch self {
            case .failedToCreateConverter:
                return "Unable to create an audio converter for SpeechAnalyzer."
            case .failedToCreateBuffer:
                return "Unable to allocate a destination audio buffer for SpeechAnalyzer."
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

/// Real-time audio conditioner specifically engineered for smart glasses microphones.
///
/// Smart glasses microphones are mounted on frames/temples near the ear, which introduces:
/// 1. Low-frequency wind buffeting, clothing rustle, and body vibration (< 100 Hz).
/// 2. Low speech energy (-35 to -48 dBFS) compared to close-proximity phone microphones.
/// 3. Cloudy ambient acoustic noise (HVAC, street noise, room reverberation).
///
/// This conditioner applies:
/// - 2nd-order Butterworth high-pass rumble filter (cutoff: 100 Hz) to clear sub-audible frame/wind thumps.
/// - Downward noise expander: smoothly suppresses stationary background noise when the user is silent.
/// - Adaptive Speech AGC & Soft Limiter: gently elevates quiet glasses speech into Apple's optimal
///   neural dynamic range (-20 dBFS RMS / -6 dBFS peak) without digital clipping.
@MainActor
final class GlassesAudioConditioner {
    private var sampleRate: Double = 16_000
    private var b0: Float = 1.0
    private var b1: Float = 0.0
    private var b2: Float = 0.0
    private var a1: Float = 0.0
    private var a2: Float = 0.0

    // Filter state (Direct Form I)
    private var x1: Float = 0.0
    private var x2: Float = 0.0
    private var y1: Float = 0.0
    private var y2: Float = 0.0

    // Downward expander envelope tracker
    private var envelopePower: Float = 0.0
    private let expanderThresholdDB: Float = -46.0 // dBFS
    private let attackCoeff: Float = 0.85
    private let releaseCoeff: Float = 0.97

    // Adaptive AGC
    private var speechGain: Float = 1.0
    private let targetSpeechRMS: Float = 0.09 // ~ -21 dBFS
    private let maxGainBoost: Float = 4.0 // max ~ +12 dB boost
    private let minGain: Float = 1.0

    init() {
        configureFilter(sampleRate: 16_000)
    }

    private func configureFilter(sampleRate: Double) {
        self.sampleRate = sampleRate
        let cutoff = 100.0 // Hz
        let q: Double = 0.70710678 // Butterworth Q
        let omega = 2.0 * .pi * cutoff / sampleRate
        let alpha = sin(omega) / (2.0 * q)
        let cosOmega = cos(omega)

        let a0 = 1.0 + alpha
        self.b0 = Float(((1.0 + cosOmega) / 2.0) / a0)
        self.b1 = Float((-(1.0 + cosOmega)) / a0)
        self.b2 = Float(((1.0 + cosOmega) / 2.0) / a0)
        self.a1 = Float((-2.0 * cosOmega) / a0)
        self.a2 = Float((1.0 - alpha) / a0)
    }

    func reset() {
        x1 = 0.0
        x2 = 0.0
        y1 = 0.0
        y2 = 0.0
        envelopePower = 0.0
        speechGain = 1.0
    }

    func process(_ buffer: AVAudioPCMBuffer) -> AVAudioPCMBuffer {
        guard let channelData = buffer.floatChannelData, buffer.frameLength > 0 else {
            return buffer
        }

        let frameCount = Int(buffer.frameLength)
        let samples = channelData[0]

        for i in 0..<frameCount {
            let x = samples[i]

            // 1. 100 Hz High-Pass Filter (Butterworth 2nd-order)
            let hp = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x
            y2 = y1
            y1 = hp

            // 2. Power envelope follower
            let power = hp * hp
            if power > envelopePower {
                envelopePower = envelopePower * attackCoeff + power * (1.0 - attackCoeff)
            } else {
                envelopePower = envelopePower * releaseCoeff + power * (1.0 - releaseCoeff)
            }

            let currentRMS = sqrt(max(1e-9, envelopePower))
            let currentDB = 20.0 * log10(currentRMS)

            // 3. Downward expander for stationary noise gating
            let expansionGain: Float
            if currentDB < expanderThresholdDB {
                let delta = expanderThresholdDB - currentDB
                let attenuationDB = min(12.0, delta * 0.5) // gentle 1:1.5 expansion ratio up to 12dB reduction
                expansionGain = pow(10.0, -attenuationDB / 20.0)
            } else {
                expansionGain = 1.0
            }

            // 4. Adaptive speech AGC
            if currentDB > expanderThresholdDB {
                let desiredGain = min(maxGainBoost, max(minGain, targetSpeechRMS / max(0.01, currentRMS)))
                speechGain = speechGain * 0.98 + desiredGain * 0.02
            } else {
                // Smoothly decay back towards unity gain during pauses
                speechGain = speechGain * 0.995 + minGain * 0.005
            }

            var out = hp * expansionGain * speechGain

            // 5. Soft-knee peak limiter to prevent saturation above -1.0 dBFS (0.89)
            let threshold: Float = 0.85
            if out > threshold {
                out = threshold + (1.0 - threshold) * tanh((out - threshold) / (1.0 - threshold))
            } else if out < -threshold {
                out = -threshold - (1.0 - threshold) * tanh((-out - threshold) / (1.0 - threshold))
            }

            samples[i] = out
        }

        return buffer
    }
}

/// Voice Activity Detector (VAD) tailored for smart glasses Bluetooth PCM audio.
///
/// Because smart glasses hardware stays streaming indefinitely once voice wake triggers:
/// 1. Tracks RMS energy of incoming 16-kHz Float32 PCM buffers.
/// 2. Requires at least 2 consecutive speech frames (~40ms) above threshold to latch `didDetectSpeech = true`.
/// 3. Arms a trailing silence timer (default 1.8s) once speech is followed by silence. If speech resumes,
///    the timer is cancelled.
/// 4. Fires `onNoSpeechTimeout` if the session is opened but no speech is detected within `noSpeechTimeout` (default 6.0s).
@MainActor
final class GlassesAudioVAD {
    private(set) var didDetectSpeech = false
    private var silenceTask: Task<Void, Never>?
    private var noSpeechTimeoutTask: Task<Void, Never>?
    private let silenceDuration: Duration
    private let noSpeechTimeout: Duration
    private let speechThresholdRMS: Float
    private var consecutiveSpeechFrames = 0
    private let requiredSpeechFrames = 2 // ~40ms to filter spurious clicks

    var onSilenceDetected: (() -> Void)?
    var onNoSpeechTimeout: (() -> Void)?

    init(
        silenceDuration: Duration = .milliseconds(1_800),
        noSpeechTimeout: Duration = .seconds(6),
        speechThresholdRMS: Float = 0.009 // ~ -41 dBFS (captures quiet/normal speech over temple mic)
    ) {
        self.silenceDuration = silenceDuration
        self.noSpeechTimeout = noSpeechTimeout
        self.speechThresholdRMS = speechThresholdRMS
    }

    func start() {
        reset()
        armNoSpeechTimeout()
    }

    func reset() {
        didDetectSpeech = false
        consecutiveSpeechFrames = 0
        silenceTask?.cancel()
        silenceTask = nil
        noSpeechTimeoutTask?.cancel()
        noSpeechTimeoutTask = nil
    }

    func processBuffer(_ buffer: AVAudioPCMBuffer) {
        guard let channelData = buffer.floatChannelData, buffer.frameLength > 0 else { return }
        let frameCount = Int(buffer.frameLength)
        let samples = channelData[0]

        var sumSquares: Float = 0.0
        for i in 0..<frameCount {
            let s = samples[i]
            sumSquares += s * s
        }
        let rms = sqrt(sumSquares / Float(frameCount))

        if rms >= speechThresholdRMS {
            consecutiveSpeechFrames += 1
            if consecutiveSpeechFrames >= requiredSpeechFrames {
                if !didDetectSpeech {
                    didDetectSpeech = true
                    noSpeechTimeoutTask?.cancel()
                    noSpeechTimeoutTask = nil
                }
                silenceTask?.cancel()
                silenceTask = nil
            }
        } else {
            consecutiveSpeechFrames = 0
            if didDetectSpeech {
                armSilenceTimer()
            }
        }
    }

    private func armSilenceTimer() {
        guard silenceTask == nil else { return }
        silenceTask = Task { @MainActor [weak self] in
            do {
                try await Task.sleep(for: self?.silenceDuration ?? .milliseconds(1_800))
            } catch {
                return
            }
            guard let self, self.didDetectSpeech else { return }
            self.silenceTask = nil
            self.onSilenceDetected?()
        }
    }

    private func armNoSpeechTimeout() {
        noSpeechTimeoutTask?.cancel()
        noSpeechTimeoutTask = Task { @MainActor [weak self] in
            do {
                try await Task.sleep(for: self?.noSpeechTimeout ?? .seconds(6))
            } catch {
                return
            }
            guard let self, !self.didDetectSpeech else { return }
            self.noSpeechTimeoutTask = nil
            self.onNoSpeechTimeout?()
        }
    }
}
