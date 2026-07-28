import XCTest

final class CyanBridgeKMPHostTests: XCTestCase {

    func testAppLaunches() {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.state == .runningForeground, "App should launch to foreground")
    }
}
