import Foundation

enum HeyCyanMediaKind: String, Sendable {
    case photo
    case video
    case audio
}

struct HeyCyanMediaItem: Identifiable, Equatable, Sendable {
    var id: String { fileName }

    let fileName: String
    let kind: HeyCyanMediaKind
}

enum HeyCyanMediaError: LocalizedError, Sendable {
    case invalidResponse
    case httpStatus(Int)
    case manifestTooLarge
    case unsafeFileName

    var errorDescription: String? {
        switch self {
        case .invalidResponse:
            return "The glasses returned an invalid media response."
        case .httpStatus(let status):
            return "The glasses media server returned HTTP \(status)."
        case .manifestTooLarge:
            return "The glasses media list exceeded the safety limit."
        case .unsafeFileName:
            return "The glasses returned an unsafe media filename."
        }
    }
}

/// Current official-app `/files/media.config` transport variant.
///
/// The older `/api/get_media_list` + IPFS contract is intentionally not mixed into this client.
/// It should become a separate endpoint strategy only after the connected firmware identifies
/// which contract it supports.
actor HeyCyanMediaClient {
    private static let maximumManifestBytes = 1_048_576
    // Captured official HeyCyan traffic sends this User-Agent. The glasses HTTP server is known to
    // reject otherwise-correct `/files/media.config` requests when the header is absent.
    private static let vendorCompatibleUserAgent = "okhttp/4.9.2"

    private let session: URLSession
    private let redirectDelegate: HeyCyanNoRedirectDelegate?

    init(session: URLSession? = nil) {
        if let session {
            self.session = session
            redirectDelegate = nil
        } else {
            let configuration = URLSessionConfiguration.ephemeral
            configuration.timeoutIntervalForRequest = 12
            configuration.timeoutIntervalForResource = 180
            configuration.allowsCellularAccess = false
            configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
            configuration.httpCookieStorage = nil
            configuration.httpShouldSetCookies = false
            configuration.urlCredentialStorage = nil
            let redirectDelegate = HeyCyanNoRedirectDelegate()
            self.redirectDelegate = redirectDelegate
            self.session = URLSession(
                configuration: configuration,
                delegate: redirectDelegate,
                delegateQueue: nil
            )
        }
    }

    func mediaList(on accessPoint: HeyCyanAccessPoint) async throws -> [HeyCyanMediaItem] {
        let url = try endpoint(
            deviceIPv4Address: accessPoint.deviceIPv4Address,
            pathComponents: ["files", "media.config"]
        )
        let (data, response) = try await session.data(for: request(for: url))
        try validate(response, expectedHost: accessPoint.deviceIPv4Address)
        guard data.count <= Self.maximumManifestBytes else {
            throw HeyCyanMediaError.manifestTooLarge
        }
        guard let content = String(data: data, encoding: .utf8) else {
            throw HeyCyanMediaError.invalidResponse
        }

        var items = [HeyCyanMediaItem]()
        for line in content.split(whereSeparator: \.isNewline) {
            guard let fileName = safeFileName(String(line)) else {
                throw HeyCyanMediaError.unsafeFileName
            }
            if let item = mediaItem(for: fileName) {
                items.append(item)
            }
        }
        return items
    }

    func download(
        _ item: HeyCyanMediaItem,
        from accessPoint: HeyCyanAccessPoint,
        to destinationURL: URL
    ) async throws {
        guard safeFileName(item.fileName) == item.fileName else {
            throw HeyCyanMediaError.unsafeFileName
        }
        let sourceURL = try endpoint(
            deviceIPv4Address: accessPoint.deviceIPv4Address,
            pathComponents: ["files", item.fileName]
        )
        let (temporaryURL, response) = try await session.download(for: request(for: sourceURL))
        try validate(response, expectedHost: accessPoint.deviceIPv4Address)
        try FileManager.default.moveItem(at: temporaryURL, to: destinationURL)
    }

    private func request(for url: URL) -> URLRequest {
        var request = URLRequest(url: url)
        request.setValue(Self.vendorCompatibleUserAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("keep-alive", forHTTPHeaderField: "Connection")
        return request
    }

    private func validate(_ response: URLResponse, expectedHost: String) throws {
        guard let httpResponse = response as? HTTPURLResponse else {
            throw HeyCyanMediaError.invalidResponse
        }
        guard httpResponse.url?.host == expectedHost else {
            throw HeyCyanMediaError.invalidResponse
        }
        guard (200 ..< 300).contains(httpResponse.statusCode) else {
            throw HeyCyanMediaError.httpStatus(httpResponse.statusCode)
        }
    }

    private func endpoint(
        deviceIPv4Address: String,
        pathComponents: [String]
    ) throws -> URL {
        var components = URLComponents()
        components.scheme = "http"
        components.host = deviceIPv4Address
        guard var url = components.url else {
            throw HeyCyanMediaError.invalidResponse
        }
        for component in pathComponents {
            url.appendPathComponent(component)
        }
        return url
    }

    private func safeFileName(_ rawValue: String) -> String? {
        let value = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty,
              value.count <= 180,
              value != ".",
              value != "..",
              !value.contains(".."),
              !value.contains("/"),
              !value.contains("\\"),
              !value.contains("?"),
              !value.contains("#"),
              !value.contains("%"),
              !value.contains(":"),
              !value.unicodeScalars.contains(where: {
                  CharacterSet.controlCharacters.contains($0)
              }) else {
            return nil
        }
        return value
    }

    private func mediaItem(for fileName: String) -> HeyCyanMediaItem? {
        switch URL(fileURLWithPath: fileName).pathExtension.lowercased() {
        case "jpg", "jpeg":
            return HeyCyanMediaItem(fileName: fileName, kind: .photo)
        case "mp4":
            return HeyCyanMediaItem(fileName: fileName, kind: .video)
        case "opus":
            return HeyCyanMediaItem(fileName: fileName, kind: .audio)
        default:
            return nil
        }
    }
}

private final class HeyCyanNoRedirectDelegate: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest,
        completionHandler: @escaping (URLRequest?) -> Void
    ) {
        completionHandler(nil)
    }
}
