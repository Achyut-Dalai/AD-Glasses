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
