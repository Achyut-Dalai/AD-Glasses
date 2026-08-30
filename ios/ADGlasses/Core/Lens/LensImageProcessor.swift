import Foundation
import ImageIO
import UniformTypeIdentifiers
import Vision

struct LensPreparedImage: Equatable, Sendable {
    let jpegData: Data
    let pixelWidth: Int
    let pixelHeight: Int
    let preparedAt: Date
}

enum LensImageError: LocalizedError, Sendable {
    case inputTooLarge
    case invalidImage
    case encodingFailed
    case noTextFound

    var errorDescription: String? {
        switch self {
        case .inputTooLarge:
            return "That image is too large to prepare safely."
        case .invalidImage:
            return "That file is not a readable still image."
        case .encodingFailed:
            return "The image could not be prepared for Lens."
        case .noTextFound:
            return "Lens could not find readable text in this image."
        }
    }
}

actor LensImageProcessor {
    private static let maximumInputBytes = 30 * 1_024 * 1_024
    private static let maximumPixelDimension = 2_048
    private static let maximumRecognizedCharacters = 20_000

    func prepare(_ sourceData: Data) throws -> LensPreparedImage {
        guard !sourceData.isEmpty else { throw LensImageError.invalidImage }
        guard sourceData.count <= Self.maximumInputBytes else {
            throw LensImageError.inputTooLarge
        }
        guard let source = CGImageSourceCreateWithData(
            sourceData as CFData,
            [kCGImageSourceShouldCache: false] as CFDictionary
        ) else {
            throw LensImageError.invalidImage
        }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: Self.maximumPixelDimension,
            kCGImageSourceShouldCacheImmediately: false
        ]
        guard let image = CGImageSourceCreateThumbnailAtIndex(
            source,
            0,
            options as CFDictionary
        ) else {
            throw LensImageError.invalidImage
        }

        let output = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(
            output,
            UTType.jpeg.identifier as CFString,
            1,
            nil
        ) else {
            throw LensImageError.encodingFailed
        }
        CGImageDestinationAddImage(
            destination,
            image,
            [kCGImageDestinationLossyCompressionQuality: 0.86] as CFDictionary
        )
        guard CGImageDestinationFinalize(destination) else {
            throw LensImageError.encodingFailed
        }

        return LensPreparedImage(
            jpegData: output as Data,
            pixelWidth: image.width,
            pixelHeight: image.height,
            preparedAt: Date()
        )
    }

    func recognizeText(in image: LensPreparedImage) throws -> String {
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.usesLanguageCorrection = true
        request.automaticallyDetectsLanguage = true

        let handler = VNImageRequestHandler(data: image.jpegData, options: [:])
        try handler.perform([request])
        let text = (request.results ?? [])
            .compactMap { $0.topCandidates(1).first?.string }
            .joined(separator: "\n")
            .trimmingCharacters(in: .whitespacesAndNewlines)

        guard !text.isEmpty else { throw LensImageError.noTextFound }
        return String(text.prefix(Self.maximumRecognizedCharacters))
    }
}
