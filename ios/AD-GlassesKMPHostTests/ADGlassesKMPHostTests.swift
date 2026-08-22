import XCTest

final class AD GlassesKMPHostTests: XCTestCase {

    func testAppLaunches() {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.state == .runningForeground, "App should launch to foreground")
    }
}
