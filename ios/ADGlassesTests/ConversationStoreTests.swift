import XCTest
@testable import ADGlasses

final class ConversationStoreTests: XCTestCase {
    func testConversationRoundTripsWithoutLosingTimestampPrecision() async throws {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("ADGlasses-ConversationTests-\(UUID().uuidString)", isDirectory: true)
        let fileURL = root.appendingPathComponent("conversations.json")
        addTeardownBlock { try? FileManager.default.removeItem(at: root) }
        let store = ConversationStore(fileURL: fileURL)
        let message = ConversationMessage(role: .user, text: "Remember this")
        let thread = ConversationThread(
            title: "Remember this",
            messages: [message],
            createdAt: message.createdAt,
            updatedAt: Date()
        )

        try await store.save([thread])

        let loaded = try await store.load()
        XCTAssertEqual(loaded, [thread])
    }

    func testImageAttachmentRoundTripsAndDeletes() async throws {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("ADGlasses-ConversationTests-\(UUID().uuidString)", isDirectory: true)
        let fileURL = root.appendingPathComponent("conversations.json")
        addTeardownBlock { try? FileManager.default.removeItem(at: root) }
        let store = ConversationStore(fileURL: fileURL)
        let jpegData = Data([0xFF, 0xD8, 0xFF, 0xD9])

        let attachment = try await store.saveImageAttachment(
            jpegData,
            pixelWidth: 640,
            pixelHeight: 480
        )
        let loadedData = try await store.loadImageAttachment(attachment)

        XCTAssertEqual(attachment.pixelWidth, 640)
        XCTAssertEqual(attachment.pixelHeight, 480)
        XCTAssertEqual(loadedData, jpegData)

        try await store.deleteImageAttachments([attachment])
        do {
            _ = try await store.loadImageAttachment(attachment)
            XCTFail("Deleted conversation image should no longer be readable")
        } catch {
            // Expected: the attachment file was deleted.
        }
    }

    func testDeleteAllRemovesSavedConversations() async throws {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("ADGlasses-ConversationTests-\(UUID().uuidString)", isDirectory: true)
        let fileURL = root.appendingPathComponent("conversations.json")
        addTeardownBlock { try? FileManager.default.removeItem(at: root) }
        let store = ConversationStore(fileURL: fileURL)

        try await store.save([ConversationThread(title: "One")])
        try await store.deleteAll()

        let loaded = try await store.load()
        XCTAssertEqual(loaded, [])
    }
}
