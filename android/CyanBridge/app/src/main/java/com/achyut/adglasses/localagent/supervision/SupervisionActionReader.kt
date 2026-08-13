package com.achyut.adglasses.localagent.supervision

import org.json.JSONArray
import org.json.JSONObject

data class SupervisionAction(
    val actionId: String,
    val actionType: String,
    val tsMs: Long,
    val date: String?,
    val targetEventIds: List<String>,
    val targetFactId: String?,
)

object SupervisionActionReader {
    fun parseLine(line: String): SupervisionAction? {
        if (line.isBlank()) return null

        return runCatching {
            val obj = JSONObject(line)
            val actionId = obj.optString("action_id", "").trim()
            val actionType = obj.optString("action_type", "").trim()
            if (actionId.isBlank() || actionType.isBlank()) return null

            SupervisionAction(
                actionId = actionId,
                actionType = actionType,
                tsMs = obj.optLong("ts_ms", 0L),
                date = obj.optString("date", "").trim().ifBlank { null },
                targetEventIds = obj.optJSONArray("target_event_ids").toStringList(),
                targetFactId = obj.optString("target_fact_id", "").trim().ifBlank { null },
            )
        }.getOrNull()
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val out = ArrayList<String>(length())
        for (i in 0 until length()) {
            val value = optString(i, "").trim()
            if (value.isNotBlank()) out.add(value)
        }
        return out
    }
}
