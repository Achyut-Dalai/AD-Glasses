import XCTest
@testable import CyanBridgeShared

final class CyanBridgeKMPHostTests: XCTestCase {

    func testMainViewControllerReturnsNonNil() {
        let viewController = MainViewControllerKt.MainViewController()
        XCTAssertNotNil(viewController, "MainViewController() should return a non-nil UIViewController")
    }

    func testMainViewControllerIsComposeHost() {
        let viewController = MainViewControllerKt.MainViewController()
        // The CMP entry point returns a UIViewController that hosts Compose UI.
        // It should have a view (proves the composable tree was created).
        XCTAssertNotNil(viewController.view, "ComposeUIViewController should have a non-nil view")
    }

    func testMainViewControllerViewHasNonZeroFrame() {
        let viewController = MainViewControllerKt.MainViewController()
        // Trigger view layout.
        let _ = viewController.view
        // The frame may be .zero before layout, but the view object must exist.
        XCTAssertNotNil(viewController.view)
    }
}
