import Combine
import Foundation

@MainActor
final class LibraryModel: ObservableObject {
    @Published private(set) var items = [LibraryItem]()
    @Published private(set) var isLoaded = false
    @Published var errorMessage: String?

    private let store: LibraryStore
    private var loadTask: Task<Void, Never>?

    init(store: LibraryStore = LibraryStore()) {
        self.store = store
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
