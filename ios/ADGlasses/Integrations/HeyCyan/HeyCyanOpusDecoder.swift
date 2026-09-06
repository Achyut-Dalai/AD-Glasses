@preconcurrency import AVFoundation
import AudioToolbox
import Foundation

enum HeyCyanOpusDecodingError: LocalizedError, Equatable {
    case invalidPacketSize(expected: Int, actual: Int)
    case formatUnavailable
    case converterUnavailable
    case bufferUnavailable
    case conversionFailed(String)
    case noDecodedFrames

    var errorDescription: String? {
        switch self {
        case .invalidPacketSize(let expected, let actual):
            return "Expected a \(expected)-byte glasses audio packet, received \(actual) bytes."
        case .formatUnavailable:
            return "The native Opus audio format is unavailable on this device."
        case .converterUnavailable:
            return "The native Opus decoder could not be created."
        case .bufferUnavailable:
            return "The native Opus decoder could not allocate an audio buffer."
        case .conversionFailed(let reason):
            return "The native Opus decoder failed: \(reason)"
        case .noDecodedFrames:
            return "The native Opus decoder returned no audio frames."
        }
    }
}

/// Decodes the complete Opus packets carried by HeyCyan family `0x59` into native PCM.
///
/// Physical capture and the official Jieli decoder agree on 16-kHz mono input and fixed 40-byte
/// packet containers. The Opus TOC in the captured packets describes one 20-ms wideband frame,
/// which is 320 samples at 16 kHz. The packet's own Opus padding remains intact.
@MainActor
final class HeyCyanOpusDecoder {
    static let sampleRate: Double = 16_000
    static let channelCount: AVAudioChannelCount = 1
    static let packetSize = 40
    static let framesPerPacket: AVAudioFrameCount = 320

    let outputFormat: AVAudioFormat

    private let inputFormat: AVAudioFormat
    private let converter: AVAudioConverter

    init() throws {
        var description = AudioStreamBasicDescription(
            mSampleRate: Self.sampleRate,
            mFormatID: kAudioFormatOpus,
            mFormatFlags: 0,
            mBytesPerPacket: 0,
            mFramesPerPacket: Self.framesPerPacket,
            mBytesPerFrame: 0,
            mChannelsPerFrame: Self.channelCount,
            mBitsPerChannel: 0,
            mReserved: 0
        )
        guard let inputFormat = AVAudioFormat(streamDescription: &description),
              let outputFormat = AVAudioFormat(
                commonFormat: .pcmFormatFloat32,
                sampleRate: Self.sampleRate,
                channels: Self.channelCount,
                interleaved: false
              ) else {
            throw HeyCyanOpusDecodingError.formatUnavailable
        }
        guard let converter = AVAudioConverter(from: inputFormat, to: outputFormat) else {
            throw HeyCyanOpusDecodingError.converterUnavailable
        }
        converter.primeMethod = .none
        self.inputFormat = inputFormat
        self.outputFormat = outputFormat
        self.converter = converter
    }

    func decode(_ packet: Data) throws -> AVAudioPCMBuffer {
        guard packet.count == Self.packetSize else {
            throw HeyCyanOpusDecodingError.invalidPacketSize(
                expected: Self.packetSize,
                actual: packet.count
            )
        }

        let compressed = AVAudioCompressedBuffer(
            format: inputFormat,
            packetCapacity: 1,
            maximumPacketSize: Self.packetSize
        )
        packet.copyBytes(to: compressed.data.assumingMemoryBound(to: UInt8.self), count: packet.count)
        compressed.byteLength = UInt32(packet.count)
        compressed.packetCount = 1
        compressed.packetDescriptions?[0] = AudioStreamPacketDescription(
            mStartOffset: 0,
            mVariableFramesInPacket: Self.framesPerPacket,
            mDataByteSize: UInt32(packet.count)
        )

        guard let pcm = AVAudioPCMBuffer(
            pcmFormat: outputFormat,
            frameCapacity: Self.framesPerPacket
        ) else {
            throw HeyCyanOpusDecodingError.bufferUnavailable
        }

        var conversionError: NSError?
        var suppliedPacket = false
        let status = converter.convert(to: pcm, error: &conversionError) { _, inputStatus in
            if suppliedPacket {
                inputStatus.pointee = .noDataNow
                return nil
            }
            suppliedPacket = true
            inputStatus.pointee = .haveData
            return compressed
        }

        guard status != .error else {
            throw HeyCyanOpusDecodingError.conversionFailed(
                conversionError?.localizedDescription ?? "unknown converter error"
            )
        }
        guard pcm.frameLength > 0 else {
            throw HeyCyanOpusDecodingError.noDecodedFrames
        }
        return pcm
    }

    func reset() {
        converter.reset()
    }
}
