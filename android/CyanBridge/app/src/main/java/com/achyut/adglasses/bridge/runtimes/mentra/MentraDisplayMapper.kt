package com.achyut.adglasses.bridge.runtimes.mentra

import android.util.Log
import com.achyut.adglasses.shared.bridge.core.DisplayCommand
import org.json.JSONObject

/**
 * Maps MentraOS layout JSON objects into [DisplayCommand] instances
 * that can be routed through [GlassesBridge] to the active device adapter.
 *
 * Supported layout types:
 * - `text_wall`          → [DisplayCommand.Text]
 * - `double_text_wall`   → [DisplayCommand.Lines]
 * - `reference_card`     → [DisplayCommand.Card]
 * - `dashboard_card`     → [DisplayCommand.Text]
 */
object MentraDisplayMapper {

    private const val TAG = "MentraDisplayMapper"

    /**
     * Convert a MentraOS layout JSON object into a [DisplayCommand].
     *
     * @param layoutJson The `layout` object from a MentraOS display message.
     * @return A [DisplayCommand] ready for the bridge.
     */
    fun mapToDisplayCommand(layoutJson: JSONObject): DisplayCommand {
        val layoutType = layoutJson.optString("layoutType", "")

        Log.d(TAG, "Mapping layout type: $layoutType")

        return when (layoutType) {
            "text_wall" -> {
                val text = layoutJson.optString("text", "")
                DisplayCommand.Text(text = text)
            }

            "double_text_wall" -> {
                val top = layoutJson.optString("topText", "")
                val bottom = layoutJson.optString("bottomText", "")
                DisplayCommand.Lines(lines = listOf(top, bottom))
            }

            "reference_card" -> {
                val title = layoutJson.optString("title", "")
                val text = layoutJson.optString("text", "")
                DisplayCommand.Card(title = title, body = text)
            }

            "dashboard_card" -> {
                val left = layoutJson.optString("leftText", "")
                val right = layoutJson.optString("rightText", "")
                DisplayCommand.Text(text = "$left: $right")
            }

            else -> {
                Log.w(TAG, "Unknown layout type: $layoutType")
                DisplayCommand.Text(text = "Unknown layout: $layoutType")
            }
        }
    }
}
