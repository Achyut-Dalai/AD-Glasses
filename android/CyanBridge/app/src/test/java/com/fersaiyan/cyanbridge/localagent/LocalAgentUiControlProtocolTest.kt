package com.fersaiyan.cyanbridge.localagent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalAgentUiControlProtocolTest {

    @Test
    fun `parses click text decision`() {
        val raw = """
            {
              "version": 1,
              "reasoning": "Tap the search field first.",
              "action": {"type": "click_text", "text": "Search"},
              "is_complete": false
            }
        """.trimIndent()

        val parsed = LocalAgentUiControlProtocol.parseDecision(raw)

        assertEquals(1, parsed.version)
        assertEquals("Tap the search field first.", parsed.reasoning)
        assertTrue(parsed.action is LocalAgentUiControlProtocol.ClickText)
        assertEquals("Search", (parsed.action as LocalAgentUiControlProtocol.ClickText).text)
        assertEquals(false, parsed.isComplete)
    }

    @Test
    fun `extracts fenced json and parses click coord`() {
        val raw = """
            Here's the next action.

            ```json
            {"version":1,"reasoning":"The icon has no label.","action":{"type":"click_coord","x":540,"y":960},"is_complete":false}
            ```
        """.trimIndent()

        val parsed = LocalAgentUiControlProtocol.parseDecision(raw)
        val action = parsed.action as LocalAgentUiControlProtocol.ClickCoord

        assertEquals(540, action.x)
        assertEquals(960, action.y)
    }

    @Test
    fun `build prompt includes structured observation`() {
        val observation = LocalAgentObservation(
            createdAtMs = 123L,
            packageName = "com.android.settings",
            screenText = "Settings\nBluetooth",
            screenSnapshot = LocalAgentScreenSnapshot(
                packageName = "com.android.settings",
                textSummary = "Settings\nBluetooth",
                nodes = listOf(
                    LocalAgentScreenNode(
                        index = 0,
                        depth = 0,
                        text = "Bluetooth",
                        contentDescription = "",
                        className = "Switch",
                        viewId = "android:id/switch_widget",
                        isClickable = true,
                        isEditable = false,
                        isScrollable = false,
                        bounds = LocalAgentNodeBounds(10, 20, 110, 70),
                    )
                ),
            ),
        )

        val prompt = LocalAgentUiControlProtocol.buildPrompt(
            LocalAgentUiControlProtocol.StepContext(
                goal = "Turn on Bluetooth",
                observation = observation,
                stepIndex = 1,
                maxSteps = 8,
                previousActionResult = "Opened Settings",
                consecutiveFailures = 1,
            )
        )

        assertTrue(prompt.user.contains("Goal:"))
        assertTrue(prompt.user.contains("Turn on Bluetooth"))
        assertTrue(prompt.user.contains("Current app: com.android.settings"))
        assertTrue(prompt.user.contains("Bluetooth"))
        assertTrue(prompt.user.contains("center:(60,45)"))
        assertTrue(prompt.user.contains("Recovery guidance:"))
    }

    @Test(expected = LocalAgentUiControlProtocol.SchemaViolationException::class)
    fun `rejects unsupported action type`() {
        LocalAgentUiControlProtocol.parseDecision(
            """{"version":1,"action":{"type":"delete_everything"},"is_complete":false}"""
        )
    }

    @Test(expected = LocalAgentUiControlProtocol.SchemaViolationException::class)
    fun `rejects non numeric click coord`() {
        LocalAgentUiControlProtocol.parseDecision(
            """{"version":1,"action":{"type":"click_coord","x":"left","y":200},"is_complete":false}"""
        )
    }

    @Test
    fun `parses swipe decision`() {
        val raw = """{"version":1,"reasoning":"Swipe up to scroll.","action":{"type":"swipe","start_x":540,"start_y":1500,"end_x":540,"end_y":500,"duration_ms":300},"is_complete":false}"""
        val parsed = LocalAgentUiControlProtocol.parseDecision(raw)
        val action = parsed.action as LocalAgentUiControlProtocol.Swipe

        assertEquals(540, action.startX)
        assertEquals(1500, action.startY)
        assertEquals(540, action.endX)
        assertEquals(500, action.endY)
        assertEquals(300L, action.durationMs)
    }

    @Test
    fun `parses long press decision`() {
        val raw = """{"version":1,"reasoning":"Long press for context menu.","action":{"type":"long_press","x":300,"y":600,"duration_ms":1200},"is_complete":false}"""
        val parsed = LocalAgentUiControlProtocol.parseDecision(raw)
        val action = parsed.action as LocalAgentUiControlProtocol.LongPress

        assertEquals(300, action.x)
        assertEquals(600, action.y)
        assertEquals(1200L, action.durationMs)
    }

    @Test
    fun `parses open notifications decision`() {
        val raw = """{"version":1,"reasoning":"Check notifications.","action":{"type":"open_notifications"},"is_complete":false}"""
        val parsed = LocalAgentUiControlProtocol.parseDecision(raw)
        assertTrue(parsed.action is LocalAgentUiControlProtocol.OpenNotifications)
    }

    @Test
    fun `parses open recents decision`() {
        val raw = """{"version":1,"reasoning":"Switch apps.","action":{"type":"open_recents"},"is_complete":false}"""
        val parsed = LocalAgentUiControlProtocol.parseDecision(raw)
        assertTrue(parsed.action is LocalAgentUiControlProtocol.OpenRecents)
    }

    @Test
    fun `parses make call decision`() {
        val raw = """{"version":1,"reasoning":"Call the contact.","action":{"type":"make_call","number":"+15551234567"},"is_complete":false}"""
        val parsed = LocalAgentUiControlProtocol.parseDecision(raw)
        val action = parsed.action as LocalAgentUiControlProtocol.MakeCall
        assertEquals("+15551234567", action.number)
    }

    @Test
    fun `parses send sms decision`() {
        val raw = """{"version":1,"reasoning":"Send text.","action":{"type":"send_sms","number":"+15551234567","message":"On my way"},"is_complete":false}"""
        val parsed = LocalAgentUiControlProtocol.parseDecision(raw)
        val action = parsed.action as LocalAgentUiControlProtocol.SendSms
        assertEquals("+15551234567", action.number)
        assertEquals("On my way", action.message)
    }

    @Test
    fun `parses keyboard submit email and read aloud decisions`() {
        val enter = LocalAgentUiControlProtocol.parseDecision(
            """{"version":1,"action":{"type":"press_enter"},"is_complete":false}""",
        )
        val email = LocalAgentUiControlProtocol.parseDecision(
            """{"version":1,"action":{"type":"send_email","to":"person@example.com","subject":"Hi","body":"Hello"},"is_complete":false}""",
        )
        val read = LocalAgentUiControlProtocol.parseDecision(
            """{"version":1,"action":{"type":"read_screen_aloud"},"is_complete":true}""",
        )

        assertTrue(enter.action is LocalAgentUiControlProtocol.PressEnter)
        assertEquals("person@example.com", (email.action as LocalAgentUiControlProtocol.SendEmail).to)
        assertTrue(read.action is LocalAgentUiControlProtocol.ReadScreenAloud)
        assertTrue(read.isComplete)
    }

    @Test
    fun `parses set alarm decision`() {
        val raw = """{"version":1,"reasoning":"Set morning alarm.","action":{"type":"set_alarm","hour":7,"minute":30,"label":"Wake up"},"is_complete":false}"""
        val parsed = LocalAgentUiControlProtocol.parseDecision(raw)
        val action = parsed.action as LocalAgentUiControlProtocol.SetAlarm
        assertEquals(7, action.hour)
        assertEquals(30, action.minute)
        assertEquals("Wake up", action.label)
    }

    @Test(expected = LocalAgentUiControlProtocol.SchemaViolationException::class)
    fun `rejects set alarm with invalid hour`() {
        LocalAgentUiControlProtocol.parseDecision(
            """{"version":1,"action":{"type":"set_alarm","hour":25,"minute":0},"is_complete":false}"""
        )
    }

    @Test
    fun `parses open contacts decision`() {
        val raw = """{"version":1,"reasoning":"Open contacts.","action":{"type":"open_contacts"},"is_complete":false}"""
        val parsed = LocalAgentUiControlProtocol.parseDecision(raw)
        assertTrue(parsed.action is LocalAgentUiControlProtocol.OpenContacts)
    }

    @Test
    fun `parses toggle wifi decision`() {
        val raw = """{"version":1,"reasoning":"Toggle wifi.","action":{"type":"toggle_wifi"},"is_complete":false}"""
        val parsed = LocalAgentUiControlProtocol.parseDecision(raw)
        assertTrue(parsed.action is LocalAgentUiControlProtocol.ToggleWifi)
    }

    @Test
    fun `parses toggle bluetooth decision`() {
        val raw = """{"version":1,"reasoning":"Toggle bluetooth.","action":{"type":"toggle_bluetooth"},"is_complete":false}"""
        val parsed = LocalAgentUiControlProtocol.parseDecision(raw)
        assertTrue(parsed.action is LocalAgentUiControlProtocol.ToggleBluetooth)
    }

    @Test
    fun `parses toggle flashlight decision`() {
        val raw = """{"version":1,"reasoning":"Toggle flashlight.","action":{"type":"toggle_flashlight"},"is_complete":false}"""
        val parsed = LocalAgentUiControlProtocol.parseDecision(raw)
        assertTrue(parsed.action is LocalAgentUiControlProtocol.ToggleFlashlight)
    }

    @Test(expected = LocalAgentUiControlProtocol.SchemaViolationException::class)
    fun `rejects swipe with missing coordinates`() {
        LocalAgentUiControlProtocol.parseDecision(
            """{"version":1,"action":{"type":"swipe","start_x":100,"start_y":500},"is_complete":false}"""
        )
    }

    @Test(expected = LocalAgentUiControlProtocol.SchemaViolationException::class)
    fun `rejects sms with blank number`() {
        LocalAgentUiControlProtocol.parseDecision(
            """{"version":1,"action":{"type":"send_sms","number":"","message":"Hello"},"is_complete":false}"""
        )
    }
}
