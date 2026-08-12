package com.achyut.adglasses.localagent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalAgentActionParserTest {

    @Test
    fun `parses expanded action types`() {
        val json = """
            [
              {"type":"wait","ms":400},
              {"type":"click_coord","x":120,"y":640},
              {"type":"type_text","text":"hello","hint":"search"},
              {"type":"press_enter"},
              {"type":"scroll","direction":"down"},
              {"type":"open_app","app_name":"Settings"},
              {"type":"finish","message":"done"}
            ]
        """.trimIndent()

        val parsed = LocalAgentActionParser.parseList(json)

        assertEquals(7, parsed.size)
        assertTrue(parsed[0] is LocalAgentAction.Wait)
        assertTrue(parsed[1] is LocalAgentAction.ClickCoord)
        assertTrue(parsed[2] is LocalAgentAction.TypeText)
        assertTrue(parsed[3] is LocalAgentAction.PressEnter)
        assertTrue(parsed[4] is LocalAgentAction.Scroll)
        assertTrue(parsed[5] is LocalAgentAction.OpenApp)
        assertTrue(parsed[6] is LocalAgentAction.Finish)
        assertEquals("search", (parsed[2] as LocalAgentAction.TypeText).hint)
        assertEquals(LocalAgentAction.Direction.DOWN, (parsed[4] as LocalAgentAction.Scroll).direction)
    }

    @Test
    fun `rejects non numeric click coordinates`() {
        val json = """
            [{"type":"click_coord","x":"left","y":640}]
        """.trimIndent()

        val parsed = LocalAgentActionParser.parseList(json)

        assertTrue(parsed.isEmpty())
    }

    @Test
    fun `parses swipe action`() {
        val json = """{"type":"swipe","start_x":100,"start_y":500,"end_x":100,"end_y":200,"duration_ms":250}"""
        val parsed = LocalAgentActionParser.parseList(json)

        assertEquals(1, parsed.size)
        val swipe = parsed[0] as LocalAgentAction.Swipe
        assertEquals(100, swipe.startX)
        assertEquals(500, swipe.startY)
        assertEquals(100, swipe.endX)
        assertEquals(200, swipe.endY)
        assertEquals(250L, swipe.durationMs)
    }

    @Test
    fun `parses long press action`() {
        val json = """{"type":"long_press","x":540,"y":960,"duration_ms":800}"""
        val parsed = LocalAgentActionParser.parseList(json)

        assertEquals(1, parsed.size)
        val lp = parsed[0] as LocalAgentAction.LongPress
        assertEquals(540, lp.x)
        assertEquals(960, lp.y)
        assertEquals(800L, lp.durationMs)
    }

    @Test
    fun `parses open notifications and recents`() {
        val json = """[{"type":"open_notifications"},{"type":"open_recents"}]"""
        val parsed = LocalAgentActionParser.parseList(json)

        assertEquals(2, parsed.size)
        assertTrue(parsed[0] is LocalAgentAction.OpenNotifications)
        assertTrue(parsed[1] is LocalAgentAction.OpenRecents)
    }

    @Test
    fun `parses make call action`() {
        val json = """{"type":"make_call","number":"+15551234567"}"""
        val parsed = LocalAgentActionParser.parseList(json)

        assertEquals(1, parsed.size)
        assertEquals("+15551234567", (parsed[0] as LocalAgentAction.MakeCall).number)
    }

    @Test
    fun `parses send sms action`() {
        val json = """{"type":"send_sms","number":"+15551234567","message":"Hello!"}"""
        val parsed = LocalAgentActionParser.parseList(json)

        assertEquals(1, parsed.size)
        val sms = parsed[0] as LocalAgentAction.SendSms
        assertEquals("+15551234567", sms.number)
        assertEquals("Hello!", sms.message)
    }

    @Test
    fun `parses set alarm action`() {
        val json = """{"type":"set_alarm","hour":7,"minute":30,"label":"Wake up"}"""
        val parsed = LocalAgentActionParser.parseList(json)

        assertEquals(1, parsed.size)
        val alarm = parsed[0] as LocalAgentAction.SetAlarm
        assertEquals(7, alarm.hour)
        assertEquals(30, alarm.minute)
        assertEquals("Wake up", alarm.label)
    }

    @Test
    fun `rejects set alarm with invalid hour`() {
        val json = """{"type":"set_alarm","hour":25,"minute":0}"""
        val parsed = LocalAgentActionParser.parseList(json)
        assertTrue(parsed.isEmpty())
    }

    @Test
    fun `parses open contacts and toggle actions`() {
        val json = """[{"type":"open_contacts"},{"type":"toggle_wifi"},{"type":"toggle_bluetooth"},{"type":"toggle_flashlight"}]"""
        val parsed = LocalAgentActionParser.parseList(json)

        assertEquals(4, parsed.size)
        assertTrue(parsed[0] is LocalAgentAction.OpenContacts)
        assertTrue(parsed[1] is LocalAgentAction.ToggleWifi)
        assertTrue(parsed[2] is LocalAgentAction.ToggleBluetooth)
        assertTrue(parsed[3] is LocalAgentAction.ToggleFlashlight)
    }

    @Test
    fun `parses swipe with missing coordinates`() {
        val json = """{"type":"swipe","start_x":100,"start_y":500}"""
        val parsed = LocalAgentActionParser.parseList(json)
        assertTrue(parsed.isEmpty())
    }

    @Test
    fun `parses sms with blank number`() {
        val json = """{"type":"send_sms","number":"","message":"Hello"}"""
        val parsed = LocalAgentActionParser.parseList(json)
        assertTrue(parsed.isEmpty())
    }

    @Test
    fun `parses email and read screen actions`() {
        val parsed = LocalAgentActionParser.parseList(
            """[{"type":"send_email","to":"person@example.com","subject":"Hi","body":"Hello"},{"type":"read_screen_aloud"}]""",
        )

        assertEquals(2, parsed.size)
        assertEquals("person@example.com", (parsed[0] as LocalAgentAction.SendEmail).to)
        assertTrue(parsed[1] is LocalAgentAction.ReadScreenAloud)
    }
}
