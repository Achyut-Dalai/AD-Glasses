import Foundation

enum HeyCyanThumbnailTransferError: LocalizedError, Sendable {
    case operationInProgress
    case tooManyChunks(Int)
    case unexpectedChunk(expected: UInt16, actual: UInt16)
    case inconsistentChunkCount(expected: UInt16, actual: UInt16)
    case imageTooLarge(Int)

    var errorDescription: String? {
        switch self {
        case .operationInProgress:
            return "Another glasses thumbnail transfer is already running."
        case .tooManyChunks(let count):
            return "The glasses thumbnail contains too many chunks (\(count))."
        case .unexpectedChunk(let expected, let actual):
            return "Expected thumbnail chunk \(expected), received \(actual)."
        case .inconsistentChunkCount(let expected, let actual):
            return "The glasses changed the thumbnail chunk count from \(expected) to \(actual)."
        case .imageTooLarge(let bytes):
            return "The glasses thumbnail exceeded the \(bytes)-byte safety limit."
        }
    }
}

/// Sequential thumbnail retrieval translated from `LargeDataHandler.syncPictureThumbnails`.
/// This component performs no capture command; the provider starts it only after the glasses emit
/// the captured `0x73` visual-ready event verified in the physical Android/HCI trace.
@MainActor
final class HeyCyanThumbnailTransfer {
    private let session: HeyCyanSession
    private let decoder: HeyCyanResponseDecoder
    private var isFetching = false

    init(
        session: HeyCyanSession,
        decoder: HeyCyanResponseDecoder = HeyCyanResponseDecoder()
    ) {
        self.session = session
        self.decoder = decoder
    }

    func fetchLatestThumbnail(
        maximumChunks: Int = 1_024,
        maximumBytes: Int = 20 * 1_024 * 1_024
    ) async throws -> Data {
        guard !isFetching else { throw HeyCyanThumbnailTransferError.operationInProgress }
        isFetching = true
        defer { isFetching = false }

        var image = Data()
        var expectedTotal: UInt16?
        var index: UInt16 = 0

        while true {
            try Task.checkCancellation()
            let frame = try await session.send(.requestPictureThumbnail(index: index))
            let chunk = try decoder.decodeThumbnailChunk(frame)

            guard chunk.index == index else {
                throw HeyCyanThumbnailTransferError.unexpectedChunk(
                    expected: index,
                    actual: chunk.index
                )
            }
            if let expectedTotal, expectedTotal != chunk.totalChunks {
                throw HeyCyanThumbnailTransferError.inconsistentChunkCount(
                    expected: expectedTotal,
                    actual: chunk.totalChunks
                )
            }
            guard Int(chunk.totalChunks) <= maximumChunks else {
                throw HeyCyanThumbnailTransferError.tooManyChunks(Int(chunk.totalChunks))
            }
            expectedTotal = chunk.totalChunks

            guard chunk.imageData.count <= maximumBytes,
                  image.count <= maximumBytes - chunk.imageData.count else {
                throw HeyCyanThumbnailTransferError.imageTooLarge(maximumBytes)
            }
            image.append(chunk.imageData)

            if index + 1 == chunk.totalChunks {
                return image
            }
            index += 1
        }
    }
}
