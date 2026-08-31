import Combine
import CoreImage
import Foundation
import ImageIO
import Metal
import UniformTypeIdentifiers

@MainActor
final class LibraryModel: ObservableObject {
    @Published private(set) var items = [LibraryItem]()
    @Published private(set) var isLoaded = false
    @Published var errorMessage: String?

    private let store: LibraryStore
    private let photoEnhancer: PhotoEnhancementEngine
    private var loadTask: Task<Void, Never>?

    init(
        store: LibraryStore = LibraryStore(),
        photoEnhancer: PhotoEnhancementEngine = PhotoEnhancementEngine()
    ) {
        self.store = store
        self.photoEnhancer = photoEnhancer
        loadTask = Task { [weak self] in
            await self?.load()
        }
    }

    deinit { loadTask?.cancel() }

    func saveSoundbite(title: String, transcript: String) async -> Bool {
        do {
            let item = try await store.saveTranscript(title: title, text: transcript)
            items.removeAll { $0.id == item.id }
            items.insert(item, at: 0)
            errorMessage = nil
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }

    func ingest(
        fileURL: URL,
        title: String,
        kind: LibraryItemKind,
        sourceProviderID: String?,
        sourceReference: String? = nil
    ) async throws -> LibraryItem {
        let item = try await store.importFile(
            from: fileURL,
            title: title,
            kind: kind,
            sourceProviderID: sourceProviderID,
            sourceReference: sourceReference
        )
        items.removeAll { $0.id == item.id }
        items.insert(item, at: 0)
        return item
    }

    func contains(sourceProviderID: String, sourceReference: String) -> Bool {
        items.contains {
            $0.sourceProviderID == sourceProviderID && $0.sourceReference == sourceReference
        }
    }

    func toggleFavorite(_ item: LibraryItem) async {
        do {
            items = try await store.setFavorite(!item.isFavorite, itemID: item.id)
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func fileURL(for item: LibraryItem) -> URL {
        store.fileURL(for: item)
    }

    func enhancedPhotoURL(for item: LibraryItem) async -> URL? {
        await store.existingEnhancedPhotoURL(for: item)
    }

    func enhancePhoto(_ item: LibraryItem) async throws -> URL {
        guard item.kind == .photo else {
            throw PhotoEnhancementError.notAPhoto
        }
        if let existing = await store.existingEnhancedPhotoURL(for: item) {
            return existing
        }

        let sourceURL = store.fileURL(for: item)
        let data = try await photoEnhancer.enhance(fileURL: sourceURL)
        return try await store.saveEnhancedPhoto(data, for: item)
    }

    private func load() async {
        do {
            items = try await store.load()
        } catch {
            errorMessage = "Could not load the local Library: \(error.localizedDescription)"
        }
        isLoaded = true
        loadTask = nil
    }
}

enum PhotoEnhancementError: LocalizedError, Sendable {
    case notAPhoto
    case unreadableImage
    case renderFailed
    case encodingFailed

    var errorDescription: String? {
        switch self {
        case .notAPhoto:
            return "Only still photos can use Auto Enhance."
        case .unreadableImage:
            return "The original photo could not be opened for enhancement."
        case .renderFailed:
            return "Core Image could not render the enhanced photo."
        case .encodingFailed:
            return "The enhanced photo could not be saved as JPEG."
        }
    }
}

/// Produces a conservative, deterministic AD Glasses derivative with Apple's Core Image/Metal
/// stack. The original Library asset is never changed. This profile improves the common small-
/// sensor look without inventing detail: modest exposure recovery, protected highlights, restrained
/// color, denoise, then mild luminance sharpening.
actor PhotoEnhancementEngine {
    private let context: CIContext
    private let outputColorSpace: CGColorSpace

    init() {
        outputColorSpace = CGColorSpace(name: CGColorSpace.sRGB)!
        if let device = MTLCreateSystemDefaultDevice() {
            context = CIContext(mtlDevice: device)
        } else {
            context = CIContext()
        }
    }

    func enhance(fileURL: URL) throws -> Data {
        guard var image = CIImage(
            contentsOf: fileURL,
            options: [.applyOrientationProperty: true]
        ) else {
            throw PhotoEnhancementError.unreadableImage
        }

        let sourceExtent = image.extent.integral
        guard !sourceExtent.isEmpty, !sourceExtent.isInfinite else {
            throw PhotoEnhancementError.unreadableImage
        }

        let exposure = exposureCorrection(for: image)
        if abs(exposure) > 0.01 {
            image = try applying(
                "CIExposureAdjust",
                to: image,
                parameters: ["inputEV": exposure]
            )
        }

        image = try applying(
            "CIHighlightShadowAdjust",
            to: image,
            parameters: [
                "inputShadowAmount": 0.15,
                "inputHighlightAmount": 0.90
            ]
        )

        image = try applying(
            "CIColorControls",
            to: image,
            parameters: [
                "inputSaturation": 1.03,
                "inputBrightness": 0.0,
                "inputContrast": 1.06
            ]
        )

        image = try applying(
            "CIVibrance",
            to: image,
            parameters: ["inputAmount": 0.10]
        )

        image = try applying(
            "CINoiseReduction",
            to: image,
            parameters: [
                "inputNoiseLevel": 0.012,
                "inputSharpness": 0.35
            ]
        )

        image = try applying(
            "CISharpenLuminance",
            to: image,
            parameters: ["inputSharpness": 0.20]
        )
        .cropped(to: sourceExtent)

        // Keep v1 white balance neutral. Temperature/tint correction should be tuned from a real
        // set of AD Glasses daylight, indoor, face, text, and low-light captures rather than guessed.
        guard let rendered = context.createCGImage(
            image,
            from: sourceExtent,
            format: .RGBA8,
            colorSpace: outputColorSpace
        ) else {
            throw PhotoEnhancementError.renderFailed
        }

        let data = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(
            data,
            UTType.jpeg.identifier as CFString,
            1,
            nil
        ) else {
            throw PhotoEnhancementError.encodingFailed
        }
        CGImageDestinationAddImage(
            destination,
            rendered,
            [kCGImageDestinationLossyCompressionQuality: 0.95] as CFDictionary
        )
        guard CGImageDestinationFinalize(destination) else {
            throw PhotoEnhancementError.encodingFailed
        }
        return data as Data
    }

    private func exposureCorrection(for image: CIImage) -> Double {
        guard let luminance = meanLuminance(of: image) else { return 0 }
        let safeLuminance = max(0.05, luminance)
        let targetLuminance = 0.46
        let measuredEV = log2(targetLuminance / safeLuminance) * 0.30
        return min(0.40, max(-0.25, measuredEV))
    }

    private func meanLuminance(of image: CIImage) -> Double? {
        guard let averageFilter = CIFilter(name: "CIAreaAverage") else { return nil }
        averageFilter.setValue(image, forKey: kCIInputImageKey)
        averageFilter.setValue(CIVector(cgRect: image.extent), forKey: kCIInputExtentKey)
        guard let averageImage = averageFilter.outputImage else { return nil }

        var pixel = [UInt8](repeating: 0, count: 4)
        pixel.withUnsafeMutableBytes { bytes in
            guard let address = bytes.baseAddress else { return }
            context.render(
                averageImage,
                toBitmap: address,
                rowBytes: 4,
                bounds: CGRect(x: 0, y: 0, width: 1, height: 1),
                format: .RGBA8,
                colorSpace: outputColorSpace
            )
        }

        let red = Double(pixel[0]) / 255.0
        let green = Double(pixel[1]) / 255.0
        let blue = Double(pixel[2]) / 255.0
        return (0.2126 * red) + (0.7152 * green) + (0.0722 * blue)
    }

    private func applying(
        _ filterName: String,
        to image: CIImage,
        parameters: [String: Any]
    ) throws -> CIImage {
        guard let filter = CIFilter(name: filterName) else {
            throw PhotoEnhancementError.renderFailed
        }
        filter.setValue(image, forKey: kCIInputImageKey)
        for (key, value) in parameters {
            filter.setValue(value, forKey: key)
        }
        guard let output = filter.outputImage else {
            throw PhotoEnhancementError.renderFailed
        }
        return output
    }
}
