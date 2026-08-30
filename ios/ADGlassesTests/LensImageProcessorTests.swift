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
}
