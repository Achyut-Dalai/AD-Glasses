package com.fersaiyan.cyanbridge.localagent

import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

object LocalAgentUiControlProtocol {
    const val CURRENT_VERSION: Int = 1

    val RESPONSE_JSON_SCHEMA: String = """
        {
          "title": "LocalAgentUiControlDecision",
          "type": "object",
          "required": ["version", "action", "is_complete"],
          "properties": {
            "version": {"type": "integer", "enum": [1]},
            "reasoning": {"type": "string"},
            "is_complete": {"type": "boolean"},
            "action": {
              "type": "object",
              "required": ["type"],
              "properties": {
                "type": {
                  "type": "string",
                  "enum": [
                    "noop",
                    "wait",
                    "click_text",
                    "click_coord",
                    "type_text",
                    "press_enter",
                    "scroll",
                    "swipe",
                    "long_press",
                    "press_back",
                    "press_home",
                    "open_notifications",
                    "open_recents",
                    "open_app",
                    "make_call",
                    "send_sms",
                    "send_email",
                    "set_alarm",
                    "open_contacts",
                    "toggle_wifi",
                    "toggle_bluetooth",
                    "toggle_flashlight",
                    "read_screen_aloud",
                    "finish"
                  ]
                },
                "text": {"type": "string"},
                "hint": {"type": "string"},
                "direction": {"type": "string", "enum": ["up", "down"]},
                "app_name": {"type": "string"},
                "message": {"type": "string"},
                "ms": {"type": "integer", "minimum": 0},
                "x": {"type": "number"},
                "y": {"type": "number"},
                "start_x": {"type": "number"},
                "start_y": {"type": "number"},
                "end_x": {"type": "number"},
                "end_y": {"type": "number"},
                "duration_ms": {"type": "integer", "minimum": 0},
                "number": {"type": "string"},
                "to": {"type": "string"},
                "subject": {"type": "string"},
                "body": {"type": "string"},
                "hour": {"type": "integer", "minimum": 0, "maximum": 23},
                "minute": {"type": "integer", "minimum": 0, "maximum": 59},
                "label": {"type": "string"}
              }
            }
          }
        }
    """.trimIndent()

    data class Prompt(
        val system: String,
        val user: String,
    )

    data class StepContext(
        val goal: String,
        val observation: LocalAgentObservation,
        val stepIndex: Int,
        val maxSteps: Int,
        val previousActionResult: String? = null,
        val consecutiveFailures: Int = 0,
    )

    sealed interface Action {
        val type: String
    }

    data object NoOp : Action {
        override val type: String = "noop"
    }

    data class Wait(
        val ms: Long,
    ) : Action {
        override val type: String = "wait"
    }

    data class ClickText(
        val text: String,
    ) : Action {
        override val type: String = "click_text"
    }

    data class ClickCoord(
        val x: Int,
        val y: Int,
    ) : Action {
        override val type: String = "click_coord"
    }

    data class TypeText(
        val text: String,
        val hint: String? = null,
    ) : Action {
        override val type: String = "type_text"
    }

    data object PressEnter : Action {
        override val type: String = "press_enter"
    }

    data class Scroll(
        val direction: Direction,
    ) : Action {
        override val type: String = "scroll"
    }

    data object PressBack : Action {
        override val type: String = "press_back"
    }

    data object PressHome : Action {
        override val type: String = "press_home"
    }

    data class OpenApp(
        val appName: String,
    ) : Action {
        override val type: String = "open_app"
    }

    data class Swipe(
        val startX: Int,
        val startY: Int,
        val endX: Int,
        val endY: Int,
        val durationMs: Long = 300L,
    ) : Action {
        override val type: String = "swipe"
    }

    data class LongPress(
        val x: Int,
        val y: Int,
        val durationMs: Long = 1000L,
    ) : Action {
        override val type: String = "long_press"
    }

    data object OpenNotifications : Action {
        override val type: String = "open_notifications"
    }

    data object OpenRecents : Action {
        override val type: String = "open_recents"
    }

    data class MakeCall(
        val number: String,
    ) : Action {
        override val type: String = "make_call"
    }

    data class SendSms(
        val number: String,
        val message: String,
    ) : Action {
        override val type: String = "send_sms"
    }

    data class SendEmail(
        val to: String,
        val subject: String,
        val body: String,
    ) : Action {
        override val type: String = "send_email"
    }

    data class SetAlarm(
        val hour: Int,
        val minute: Int,
        val label: String? = null,
    ) : Action {
        override val type: String = "set_alarm"
    }

    data object OpenContacts : Action {
        override val type: String = "open_contacts"
    }

    data object ToggleWifi : Action {
        override val type: String = "toggle_wifi"
    }

    data object ToggleBluetooth : Action {
        override val type: String = "toggle_bluetooth"
    }

    data object ToggleFlashlight : Action {
        override val type: String = "toggle_flashlight"
    }

    data object ReadScreenAloud : Action {
        override val type: String = "read_screen_aloud"
    }

    data class Finish(
        val message: String? = null,
    ) : Action {
        override val type: String = "finish"
    }

    enum class Direction {
        up,
        down,
    }

    data class Decision(
        val version: Int,
        val reasoning: String?,
        val action: Action,
        val isComplete: Boolean,
    )

    open class ProtocolException(message: String, cause: Throwable? = null) : Exception(message, cause)

    class JsonExtractionException(message: String) : ProtocolException(message)

    class JsonParseException(message: String, cause: Throwable? = null) : ProtocolException(message, cause)

    class SchemaViolationException(message: String) : ProtocolException(message)

    fun buildPrompt(context: StepContext): Prompt {
        val system = buildString {
            appendLine("You are the CyanBridge LocalAgent UI-control planner.")
            appendLine("You MUST respond with exactly one JSON object and nothing else.")
            appendLine("Your response MUST follow this schema:")
            appendLine(RESPONSE_JSON_SCHEMA)
            appendLine()
            appendLine("Rules:")
            appendLine("- Choose exactly one next action.")
            appendLine("- Prefer click_text when a visible label exists.")
            appendLine("- Use click_coord only when text-based targeting is unreliable.")
            appendLine("- Use type_text only after the target field is already focused or obvious.")
            appendLine("- Use press_enter only to submit the currently focused editor; it requires approval because it can send a form or message.")
            appendLine("- Use swipe for drag gestures, dismissing cards, or horizontal page swipes.")
            appendLine("- Use long_press for context menus or drag-to-select.")
            appendLine("- Use open_notifications to pull down the notification shade.")
            appendLine("- Use open_recents to switch between recent apps.")
            appendLine("- Use open_contacts to open the contacts list.")
            appendLine("- Use make_call to open the dialer with a phone number. It does not place the call automatically.")
            appendLine("- Use send_sms to open a prefilled SMS composer. It does not send automatically.")
            appendLine("- Use send_email to open a prefilled email composer. It does not send automatically.")
            appendLine("- Use set_alarm to create an alarm.")
            appendLine("- Use toggle_wifi, toggle_bluetooth, toggle_flashlight for system controls.")
            appendLine("- Use read_screen_aloud only when the user explicitly asks to hear the current visible screen. It is privacy-sensitive and requires approval.")
            appendLine("- Use finish when the user goal is complete or clearly blocked.")
            appendLine("- Keep reasoning to one short sentence.")
            appendLine("- Never invent UI elements that are not present in the observation.")
        }.trim()

        val user = buildString {
            appendLine("Goal:")
            appendLine(context.goal.trim())
            appendLine()
            appendLine("Step: ${context.stepIndex}/${context.maxSteps}")
            context.previousActionResult?.trim()?.takeIf { it.isNotEmpty() }?.let {
                appendLine("Previous action result:")
                appendLine(it)
                appendLine()
            }
            if (context.consecutiveFailures > 0) {
                appendLine("Recovery guidance:")
                appendLine("The previous approach has failed ${context.consecutiveFailures} time(s). Use a different visible control or navigation path; do not repeat the same action.")
                appendLine()
            }
            appendLine("Observation:")
            appendLine(
                context.observation.screenSnapshot?.toPromptText()
                    ?: context.observation.screenText.orEmpty().ifBlank { "(screen unreadable)" }
            )
        }.trim()

        return Prompt(system = system, user = user)
    }

    fun parseDecision(raw: String): Decision {
        val jsonText = extractJsonObjectText(raw)
        val obj = try {
            JSONObject(JSONTokener(jsonText))
        } catch (e: JSONException) {
            throw JsonParseException(
                message = "UI-control response is not valid JSON: ${e.message}. Extracted=${jsonText.preview()}",
                cause = e,
            )
        }

        val version = obj.optInt("version", -1)
        if (version != CURRENT_VERSION) {
            throw SchemaViolationException("Version mismatch. Expected=$CURRENT_VERSION got=$version")
        }

        val actionObj = obj.optJSONObject("action")
            ?: throw SchemaViolationException("Missing required object: action")
        val action = parseAction(actionObj)

        if (!obj.has("is_complete")) {
            throw SchemaViolationException("Missing required field: is_complete")
        }

        return Decision(
            version = version,
            reasoning = obj.optNullableString("reasoning")?.trim()?.takeIf { it.isNotBlank() },
            action = action,
            isComplete = obj.optBoolean("is_complete", false),
        )
    }

    private fun parseAction(obj: JSONObject): Action {
        val type = obj.optNullableString("type")?.trim()?.lowercase()
            ?: throw SchemaViolationException("action.type is required")

        return when (type) {
            "noop" -> NoOp
            "wait" -> Wait(ms = obj.optLong("ms", 500L).coerceAtLeast(0L))
            "click_text" -> {
                val text = obj.optString("text", "").trim()
                if (text.isBlank()) throw SchemaViolationException("action.text is required for click_text")
                ClickText(text)
            }
            "click_coord" -> {
                if (!obj.has("x") || !obj.has("y")) {
                    throw SchemaViolationException("action.x and action.y are required for click_coord")
                }
                val x = obj.optDouble("x", Double.NaN)
                val y = obj.optDouble("y", Double.NaN)
                if (!x.isFinite() || !y.isFinite()) {
                    throw SchemaViolationException("action.x and action.y must be finite numbers for click_coord")
                }
                ClickCoord(
                    x = x.toInt(),
                    y = y.toInt(),
                )
            }
            "type_text" -> {
                val text = obj.optString("text", "").trim()
                if (text.isBlank()) throw SchemaViolationException("action.text is required for type_text")
                TypeText(text = text, hint = obj.optNullableString("hint")?.trim()?.takeIf { it.isNotBlank() })
            }
            "press_enter" -> PressEnter
            "scroll" -> {
                val direction = obj.optString("direction", "").trim()
                val parsed = runCatching { Direction.valueOf(direction) }.getOrNull()
                    ?: throw SchemaViolationException("action.direction must be 'up' or 'down' for scroll")
                Scroll(parsed)
            }
            "press_back" -> PressBack
            "press_home" -> PressHome
            "open_notifications" -> OpenNotifications
            "open_recents" -> OpenRecents
            "open_app" -> {
                val appName = obj.optString("app_name", "").trim()
                if (appName.isBlank()) throw SchemaViolationException("action.app_name is required for open_app")
                OpenApp(appName)
            }
            "swipe" -> {
                val sx = obj.optDouble("start_x", Double.NaN)
                val sy = obj.optDouble("start_y", Double.NaN)
                val ex = obj.optDouble("end_x", Double.NaN)
                val ey = obj.optDouble("end_y", Double.NaN)
                if (!sx.isFinite() || !sy.isFinite() || !ex.isFinite() || !ey.isFinite()) {
                    throw SchemaViolationException("start_x, start_y, end_x, end_y must be finite numbers for swipe")
                }
                Swipe(
                    startX = sx.toInt(),
                    startY = sy.toInt(),
                    endX = ex.toInt(),
                    endY = ey.toInt(),
                    durationMs = obj.optLong("duration_ms", 300L).coerceIn(50L, 5000L),
                )
            }
            "long_press" -> {
                if (!obj.has("x") || !obj.has("y")) {
                    throw SchemaViolationException("x and y are required for long_press")
                }
                val x = obj.optDouble("x", Double.NaN)
                val y = obj.optDouble("y", Double.NaN)
                if (!x.isFinite() || !y.isFinite()) {
                    throw SchemaViolationException("x and y must be finite numbers for long_press")
                }
                LongPress(
                    x = x.toInt(),
                    y = y.toInt(),
                    durationMs = obj.optLong("duration_ms", 1000L).coerceIn(300L, 5000L),
                )
            }
            "make_call" -> {
                val number = obj.optString("number", "").trim()
                if (number.isBlank()) throw SchemaViolationException("number is required for make_call")
                MakeCall(number)
            }
            "send_sms" -> {
                val number = obj.optString("number", "").trim()
                val message = obj.optString("message", "").trim()
                if (number.isBlank()) throw SchemaViolationException("number is required for send_sms")
                if (message.isBlank()) throw SchemaViolationException("message is required for send_sms")
                SendSms(number, message)
            }
            "send_email" -> {
                val to = obj.optString("to", "").trim()
                if (to.isBlank()) throw SchemaViolationException("to is required for send_email")
                SendEmail(
                    to = to,
                    subject = obj.optNullableString("subject").orEmpty(),
                    body = obj.optNullableString("body").orEmpty(),
                )
            }
            "set_alarm" -> {
                val hour = obj.optInt("hour", -1)
                val minute = obj.optInt("minute", -1)
                if (hour !in 0..23 || minute !in 0..59) {
                    throw SchemaViolationException("hour (0-23) and minute (0-59) are required for set_alarm")
                }
                SetAlarm(
                    hour = hour,
                    minute = minute,
                    label = obj.optNullableString("label")?.trim()?.takeIf { it.isNotBlank() },
                )
            }
            "open_contacts" -> OpenContacts
            "toggle_wifi" -> ToggleWifi
            "toggle_bluetooth" -> ToggleBluetooth
            "toggle_flashlight" -> ToggleFlashlight
            "read_screen_aloud" -> ReadScreenAloud
            "finish" -> Finish(message = obj.optNullableString("message")?.trim()?.takeIf { it.isNotBlank() })
            else -> throw SchemaViolationException("Unsupported action type: $type")
        }
    }

    private fun extractJsonObjectText(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) throw JsonExtractionException("UI-control response was empty")

        val fenced = FENCED_JSON_REGEX.find(trimmed)
        if (fenced != null) {
            val inner = fenced.groupValues.getOrNull(1)?.trim().orEmpty()
            if (inner.startsWith("{")) return inner
        }

        val firstBrace = trimmed.indexOf('{')
        if (firstBrace < 0) {
            throw JsonExtractionException("UI-control response did not contain a JSON object. Raw=${trimmed.preview()}")
        }

        return extractBalancedBraces(trimmed.substring(firstBrace))
            ?: throw JsonExtractionException(
                "UI-control response contained '{' but matching braces were not balanced. Raw=${trimmed.preview()}"
            )
    }

    private fun extractBalancedBraces(textStartingWithBrace: String): String? {
        var depth = 0
        var inString = false
        var escape = false

        for (i in textStartingWithBrace.indices) {
            val c = textStartingWithBrace[i]

            if (inString) {
                if (escape) {
                    escape = false
                } else {
                    when (c) {
                        '\\' -> escape = true
                        '"' -> inString = false
                    }
                }
                continue
            }

            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return textStartingWithBrace.substring(0, i + 1)
                }
            }
        }

        return null
    }

    private fun JSONObject.optNullableString(key: String): String? {
        val v = opt(key)
        if (v == null || v == JSONObject.NULL) return null
        return when (v) {
            is String -> v
            else -> v.toString()
        }
    }

    private fun String.preview(maxChars: Int = 400): String {
        val singleLine = replace("\n", "\\n")
        return if (singleLine.length <= maxChars) singleLine else singleLine.take(maxChars) + "…"
    }

    private val FENCED_JSON_REGEX =
        Regex("""```(?:json)?\s*([\s\S]*?)\s*```""", RegexOption.IGNORE_CASE)
}
