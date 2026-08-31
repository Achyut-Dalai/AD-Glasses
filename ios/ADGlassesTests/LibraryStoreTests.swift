import XCTest
@testable import ADGlasses

final class LibraryStoreTests: XCTestCase {
    func testTranscriptPersistsAndCanBeFavorited() async throws {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("ADGlasses-LibraryTests-\(UUID().uuidString)", isDirectory: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: root) }
        let store = LibraryStore(rootURL: root)

        let saved = try await store.saveTranscript(title: "Walk notes", text: "Turn left at the park.")
        var loaded = try await store.load()

        XCTAssertEqual(loaded, [saved])
        XCTAssertEqual(
            try String(contentsOf: store.fileURL(for: saved), encoding: .utf8),
            "Turn left at the park."
        )

        loaded = try await store.setFavorite(true, itemID: saved.id)
        XCTAssertTrue(loaded.first?.isFavorite == true)
    }

    func testImportRejectsUnexpectedExtension() async throws {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("ADGlasses-LibraryTests-\(UUID().uuidString)", isDirectory: true)
        let source = root.appendingPathComponent("source.bin")
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        try Data([0x00]).write(to: source)
        addTeardownBlock { try? FileManager.default.removeItem(at: root) }
        let store = LibraryStore(rootURL: root.appendingPathComponent("Library", isDirectory: true))

        do {
            _ = try await store.importFile(
                from: source,
                title: "Unexpected",
                kind: .photo,
                sourceProviderID: "test"
            )
            XCTFail("Expected an unsafe extension to be rejected")
        } catch let error as LibraryStoreError {
            guard case .invalidFileName = error else {
                return XCTFail("Unexpected Library error: \(error)")
            }
        }
    }

    func testImportedMediaPreservesItsRemoteIdentityForDeduplication() async throws {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("ADGlasses-LibraryTests-\(UUID().uuidString)", isDirectory: true)
        let source = root.appendingPathComponent("capture.jpg")
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        try Data([0xFF, 0xD8, 0xFF, 0xD9]).write(to: source)
        addTeardownBlock { try? FileManager.default.removeItem(at: root) }
        let store = LibraryStore(rootURL: root.appendingPathComponent("Library", isDirectory: true))

        let imported = try await store.importFile(
            from: source,
            title: "Capture",
            kind: .photo,
            sourceProviderID: "provider",
            sourceReference: "DCIM_0001.jpg"
        )
        let loaded = try await store.load()

        XCTAssertEqual(imported.sourceReference, "DCIM_0001.jpg")
        XCTAssertEqual(loaded.first?.sourceReference, "DCIM_0001.jpg")
    }
}
