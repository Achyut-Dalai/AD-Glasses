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
            return "Lens could not find readable text or codes in this image."
        }
    }
}

actor LensImageProcessor {
    private static let maximumInputBytes = 30 * 1_024 * 1_024
    private static let maximumPixelDimension = 2_048
    private static let maximumRecognizedCharacters = 20_000
    private static let maximumBarcodeValues = 16

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

    /// Performs the local, deterministic part of Lens before a request ever needs cloud vision.
    /// Accurate OCR and barcode/QR recognition run in the same Vision pass on the prepared image.
    /// This works on the iPhone 13 and gives later multimodal models cleaner, smaller context.
    func recognizeText(in image: LensPreparedImage) throws -> String {
        let textRequest = VNRecognizeTextRequest()
        textRequest.recognitionLevel = .accurate
        textRequest.usesLanguageCorrection = true
        textRequest.automaticallyDetectsLanguage = true

        let barcodeRequest = VNDetectBarcodesRequest()
        let handler = VNImageRequestHandler(data: image.jpegData, options: [:])
        try handler.perform([textRequest, barcodeRequest])

        let text = (textRequest.results ?? [])
            .compactMap { $0.topCandidates(1).first?.string }
            .joined(separator: "\n")
            .trimmingCharacters(in: .whitespacesAndNewlines)

        let barcodeValues = Array(
            (barcodeRequest.results ?? [])
                .compactMap(\.payloadStringValue)
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
                .uniquedPreservingOrder()
                .prefix(Self.maximumBarcodeValues)
        )

        var sections = [String]()
        if !text.isEmpty {
            sections.append(String(text.prefix(Self.maximumRecognizedCharacters)))
        }
        if !barcodeValues.isEmpty {
            let label = barcodeValues.count == 1 ? "Detected code" : "Detected codes"
            sections.append(label + ":\n" + barcodeValues.joined(separator: "\n"))
        }

        guard !sections.isEmpty else { throw LensImageError.noTextFound }
        return sections.joined(separator: "\n\n")
    }
}

private extension Sequence where Element == String {
    func uniquedPreservingOrder() -> [String] {
        var seen = Set<String>()
        var result = [String]()
        for value in self where seen.insert(value).inserted {
            result.append(value)
        }
        return result
    }
}
