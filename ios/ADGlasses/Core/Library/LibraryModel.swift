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

    @discardableResult
    func delete(_ item: LibraryItem) async -> Bool {
        do {
            items = try await store.delete(itemID: item.id)
            errorMessage = nil
            return true
        } catch {
            errorMessage = "Could not delete \(item.title): \(error.localizedDescription)"
            return false
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
        guard var output = CIImage(
            contentsOf: fileURL,
            options: [.applyOrientationProperty: true]
        ) else {
            throw PhotoEnhancementError.unreadableImage
        }

        let sourceExtent = output.extent.integral
        guard !sourceExtent.isEmpty, !sourceExtent.isInfinite else {
            throw PhotoEnhancementError.unreadableImage
        }

        let filters = output.autoAdjustmentFilters(
            options: [
                .enhance: true,
                .redEye: true
            ]
        )
        for filter in filters {
            filter.setValue(output, forKey: kCIInputImageKey)
            if let next = filter.outputImage {
                output = next
            }
        }

        guard let rendered = context.createCGImage(
            output,
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
}
