import AVFoundation
import Foundation
import os

private let groqSpeechLogger = Logger(subsystem: "com.achyutdalai.ADGlasses", category: "GroqWhisper")

/// Supported Groq Whisper speech transcription models.
enum GroqWhisperTranscriptionModel: String, CaseIterable, Identifiable, Sendable {
    case largeV3Turbo = "whisper-large-v3-turbo"
    case largeV3 = "whisper-large-v3"

    var id: String { rawValue }
    var displayName: String {
        switch self {
        case .largeV3Turbo: return "Whisper Large V3 Turbo"
        case .largeV3: return "Whisper Large V3"
        }
    }
}

/// Lossless, memory-efficient 16 kHz 16-bit Linear PCM Mono WAV encoder.
/// Converts an array of `AVAudioPCMBuffer`s directly into a standard RIFF WAV payload
/// ready for Groq Whisper transcription.
enum AudioWAVEncoder {
    static func encode(buffers: [AVAudioPCMBuffer], targetSampleRate: Double = 16_000) -> Data? {
        var samples = [Int16]()
        for buffer in buffers {
            let frameCount = Int(buffer.frameLength)
            guard frameCount > 0 else { continue }

            if let floatData = buffer.floatChannelData {
                let channel = floatData[0]
                samples.reserveCapacity(samples.count + frameCount)
                for i in 0..<frameCount {
                    let clamped = max(-1.0, min(1.0, channel[i]))
                    let sample = Int16(clamped * 32767.0)
                    samples.append(sample)
                }
            } else if let int16Data = buffer.int16ChannelData {
                let channel = int16Data[0]
                samples.reserveCapacity(samples.count + frameCount)
                for i in 0..<frameCount {
                    samples.append(channel[i])
                }
            }
        }

        guard !samples.isEmpty else { return nil }

        let sampleRate = UInt32(buffers.first?.format.sampleRate ?? targetSampleRate)
        let numChannels: UInt16 = 1
        let bitsPerSample: UInt16 = 16
        let byteRate: UInt32 = sampleRate * UInt32(numChannels) * UInt32(bitsPerSample / 8)
        let blockAlign: UInt16 = numChannels * (bitsPerSample / 8)
        let dataSize = UInt32(samples.count * 2)
        let chunkSize: UInt32 = 36 + dataSize

        var data = Data()
        data.reserveCapacity(44 + Int(dataSize))

        // RIFF header
        data.append(contentsOf: [UInt8]("RIFF".utf8))
        var cs = chunkSize.littleEndian
        withUnsafeBytes(of: &cs) { data.append(contentsOf: $0) }
        data.append(contentsOf: [UInt8]("WAVE".utf8))

        // fmt subchunk
        data.append(contentsOf: [UInt8]("fmt ".utf8))
        var sc1s = UInt32(16).littleEndian
        withUnsafeBytes(of: &sc1s) { data.append(contentsOf: $0) }
        var audioFormat = UInt16(1).littleEndian // 1 = PCM
        withUnsafeBytes(of: &audioFormat) { data.append(contentsOf: $0) }
        var nc = numChannels.littleEndian
        withUnsafeBytes(of: &nc) { data.append(contentsOf: $0) }
        var sr = sampleRate.littleEndian
        withUnsafeBytes(of: &sr) { data.append(contentsOf: $0) }
        var br = byteRate.littleEndian
        withUnsafeBytes(of: &br) { data.append(contentsOf: $0) }
        var ba = blockAlign.littleEndian
        withUnsafeBytes(of: &ba) { data.append(contentsOf: $0) }
        var bps = bitsPerSample.littleEndian
        withUnsafeBytes(of: &bps) { data.append(contentsOf: $0) }

        // data subchunk
        data.append(contentsOf: [UInt8]("data".utf8))
        var ds = dataSize.littleEndian
        withUnsafeBytes(of: &ds) { data.append(contentsOf: $0) }

        // samples payload
        samples.withUnsafeBufferPointer { bufferPtr in
            data.append(Data(buffer: bufferPtr))
        }

        return data
    }
}

/// Standalone, high-throughput Groq Whisper client for transcription.
final class GroqWhisperClient: Sendable {
    static let shared = GroqWhisperClient()

    private let session: URLSession

    init(session: URLSession = .shared) {
        self.session = session
    }

    func transcribe(
        wavData: Data,
        credential: String,
        model: GroqWhisperTranscriptionModel = .largeV3,
        language: String? = nil,
        prompt: String? = nil
    ) async throws -> String {
        let key = credential.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty else {
            throw GroqSpeechTranslationError.missingCredential
        }
        guard let url = URL(string: "https://api.groq.com/openai/v1/audio/transcriptions") else {
            throw GroqSpeechTranslationError.invalidResponse
        }

        let boundary = "ADGlasses-Groq-\(UUID().uuidString)"
        var body = Data()
        body.appendMultipartField(name: "model", value: model.rawValue, boundary: boundary)
        body.appendMultipartField(name: "response_format", value: "json", boundary: boundary)
        body.appendMultipartField(name: "temperature", value: "0", boundary: boundary)
        if let language, !language.isEmpty {
            body.appendMultipartField(name: "language", value: language, boundary: boundary)
        }
        if let prompt, !prompt.isEmpty {
            body.appendMultipartField(name: "prompt", value: prompt, boundary: boundary)
        }
        body.appendMultipartFile(
            name: "file",
            filename: "audio.wav",
            mimeType: "audio/wav",
            data: wavData,
            boundary: boundary
        )
        body.appendString("--\(boundary)--\r\n")

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 20
        request.httpBody = body
        request.setValue("Bearer \(key)", forHTTPHeaderField: "Authorization")
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        let startTime = CFAbsoluteTimeGetCurrent()
        let (data, response) = try await session.data(for: request)
        let elapsed = Int((CFAbsoluteTimeGetCurrent() - startTime) * 1000)

        guard let http = response as? HTTPURLResponse else {
            throw GroqSpeechTranslationError.invalidResponse
        }
        guard (200..<300).contains(http.statusCode) else {
            if let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let error = root["error"] as? [String: Any],
               let message = error["message"] as? String {
                groqSpeechLogger.error("Groq Whisper HTTP \(http.statusCode): \(message)")
                throw GroqSpeechTranslationError.requestFailed("Groq Whisper: \(message)")
            }
            throw GroqSpeechTranslationError.requestFailed("Groq Whisper HTTP \(http.statusCode)")
        }

        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let text = root["text"] as? String else {
            throw GroqSpeechTranslationError.invalidResponse
        }

        let cleaned = text.trimmingCharacters(in: .whitespacesAndNewlines)
        groqSpeechLogger.notice("Groq \(model.displayName) transcribed in \(elapsed)ms: \"\(cleaned)\"")
        return cleaned
    }
}
