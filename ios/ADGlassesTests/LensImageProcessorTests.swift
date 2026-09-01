import Foundation
import UIKit
import XCTest
@testable import ADGlasses

final class LensImageProcessorTests: XCTestCase {
    func testPreparationDownsamplesAndEncodesJPEG() async throws {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 2_600, height: 1_300))
        let png = renderer.pngData { context in
            UIColor.systemIndigo.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 2_600, height: 1_300))
        }

        let prepared = try await LensImageProcessor().prepare(png)

        XCTAssertEqual(max(prepared.pixelWidth, prepared.pixelHeight), 2_048)
        XCTAssertNotNil(UIImage(data: prepared.jpegData))
        XCTAssertEqual(prepared.jpegData.prefix(2), Data([0xFF, 0xD8]))
    }

    func testPreparationRejectsInvalidBytes() async {
        do {
            _ = try await LensImageProcessor().prepare(Data("not an image".utf8))
            XCTFail("Expected invalid image data to fail")
        } catch let error as LensImageError {
            guard case .invalidImage = error else {
                return XCTFail("Unexpected Lens error: \(error)")
            }
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
    }

    func testOpenAIVisualRequestUsesResponsesInputImageDataURL() async throws {
        let session = makeStubSession(
            statusCode: 200,
            responseJSONObject: ["output_text": "A red mug is on a table."]
        )
        let profile = AIProfile(
            id: UUID(),
            name: "OpenAI",
            provider: .openAI,
            baseURL: "https://api.openai.com/v1",
            model: "gpt-5"
        )

        let answer = try await JarvisVisualAIClient(session: session).answer(
            question: "What am I looking at?",
            imageJPEGData: Data([0xFF, 0xD8, 0xFF, 0xD9]),
            profile: profile,
            credential: "test-key"
        )

        XCTAssertEqual(answer, "A red mug is on a table.")
        let request = try XCTUnwrap(VisualRequestRecorder.shared.lastRequest())
        XCTAssertEqual(request.url?.absoluteString, "https://api.openai.com/v1/responses")
        XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer test-key")

        let root = try requestJSONObject(request)
        let input = try XCTUnwrap(root["input"] as? [[String: Any]])
        let content = try XCTUnwrap(input.first?["content"] as? [[String: Any]])
        XCTAssertEqual(content.first?["type"] as? String, "input_text")
        let image = try XCTUnwrap(content.first(where: { ($0["type"] as? String) == "input_image" }))
        let imageURL = try XCTUnwrap(image["image_url"] as? String)
        XCTAssertTrue(imageURL.hasPrefix("data:image/jpeg;base64,"))
    }

    func testGeminiVisualRequestUsesInlineJPEGData() async throws {
        let response: [String: Any] = [
            "candidates": [[
                "content": [
                    "parts": [["text": "A bicycle is leaning against a wall."]]
                ]
            ]]
        ]
        let session = makeStubSession(statusCode: 200, responseJSONObject: response)
        let profile = AIProfile(
            id: UUID(),
            name: "Gemini",
            provider: .google,
            baseURL: "https://generativelanguage.googleapis.com/v1beta",
            model: "models/gemini-3.7-flash:generateContent"
        )

        let answer = try await JarvisVisualAIClient(session: session).answer(
            question: "Describe the scene",
            imageJPEGData: Data([0xFF, 0xD8, 0xFF, 0xD9]),
            profile: profile,
            credential: "gemini-test-key"
        )

        XCTAssertEqual(answer, "A bicycle is leaning against a wall.")
        let request = try XCTUnwrap(VisualRequestRecorder.shared.lastRequest())
        XCTAssertEqual(
            request.url?.absoluteString,
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.7-flash:generateContent"
        )
        XCTAssertEqual(request.value(forHTTPHeaderField: "x-goog-api-key"), "gemini-test-key")

        let root = try requestJSONObject(request)
        let contents = try XCTUnwrap(root["contents"] as? [[String: Any]])
        let parts = try XCTUnwrap(contents.first?["parts"] as? [[String: Any]])
        let inline = try XCTUnwrap(parts.compactMap { $0["inline_data"] as? [String: Any] }.first)
        XCTAssertEqual(inline["mime_type"] as? String, "image/jpeg")
        XCTAssertFalse((inline["data"] as? String)?.isEmpty ?? true)
    }

    override func tearDown() {
        VisualRequestRecorder.shared.reset()
        super.tearDown()
    }

    private func makeStubSession(
        statusCode: Int,
        responseJSONObject: [String: Any]
    ) -> URLSession {
        VisualRequestRecorder.shared.configure(statusCode: statusCode, responseJSONObject: responseJSONObject)
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [VisualStubURLProtocol.self]
        return URLSession(configuration: configuration)
    }

    private func requestJSONObject(_ request: URLRequest) throws -> [String: Any] {
        let data = try XCTUnwrap(request.httpBody)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }
}

private final class VisualRequestRecorder: @unchecked Sendable {
    static let shared = VisualRequestRecorder()

    private let lock = NSLock()
    private var request: URLRequest?
    private var statusCode = 200
    private var responseData = Data("{}".utf8)

    func configure(statusCode: Int, responseJSONObject: [String: Any]) {
        lock.lock()
        defer { lock.unlock() }
        self.statusCode = statusCode
        responseData = (try? JSONSerialization.data(withJSONObject: responseJSONObject)) ?? Data("{}".utf8)
        request = nil
    }

    func response(for request: URLRequest) -> (HTTPURLResponse, Data) {
        lock.lock()
        defer { lock.unlock() }
        self.request = request
        let response = HTTPURLResponse(
            url: request.url!,
            statusCode: statusCode,
            httpVersion: "HTTP/1.1",
            headerFields: ["Content-Type": "application/json"]
        )!
        return (response, responseData)
    }

    func lastRequest() -> URLRequest? {
        lock.lock()
        defer { lock.unlock() }
        return request
    }

    func reset() {
        lock.lock()
        defer { lock.unlock() }
        request = nil
        statusCode = 200
        responseData = Data("{}".utf8)
    }
}

private final class VisualStubURLProtocol: URLProtocol, @unchecked Sendable {
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        let (response, data) = VisualRequestRecorder.shared.response(for: request)
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: data)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}
