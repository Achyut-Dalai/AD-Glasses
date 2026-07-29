package com.fersaiyan.cyanbridge.plugins.walkingaid

import com.fersaiyan.cyanbridge.plugins.walkingaid.vision.DepthResult
import com.fersaiyan.cyanbridge.plugins.walkingaid.vision.DetectionResult
import org.json.JSONArray
import org.json.JSONObject

data class WarningDecision(
    val shouldWarn: Boolean,
    val message: String,
    val sceneLogJson: String,
)

object WalkingAidWarningEngine {
    private val lastAlertTimeMap = mutableMapOf<String, Long>()
    private const val DEFAULT_COOLDOWN_MS = 6000L
    private const val URGENT_COOLDOWN_MS = 2500L

    private val CRITICAL_NAVIGATION_CLASSES = setOf(
        "bicycle", "motorcycle", "car", "bus", "truck", "dog", "bench",
        "person", "traffic light", "fire hydrant", "stop sign", "chair"
    )

    private val temporalSceneBuffer = ArrayDeque<JSONObject>(15)

    @Synchronized
    fun evaluate(
        detection: DetectionResult,
        depth: DepthResult?,
        focusDescription: String,
    ): WarningDecision {
        val now = System.currentTimeMillis()
        val userKeywords = WalkingAidFocusMapper.resolve(focusDescription)

        var candidateAlert: String? = null
        var isUrgent = false
        var alertKey = ""

        // 1. Check approaching center hazards (Priority 1: Immediate collision risk)
        for (obj in detection.objects) {
            val isCustomWatch = userKeywords.any { keyword -> matchesClass(obj.label, keyword) }
            val isCritical = CRITICAL_NAVIGATION_CLASSES.contains(obj.label) || isCustomWatch

            if (isCritical && obj.position == "center" && obj.approaching) {
                alertKey = "approaching_${obj.label}"
                if (canTrigger(alertKey, now, URGENT_COOLDOWN_MS)) {
                    candidateAlert = "Warning: approaching ${obj.label} straight ahead!"
                    isUrgent = true
                    recordAlert(alertKey, now)
                    break
                }
            }
        }

        // 2. Check prominent immediate obstacles in direct path (Priority 2)
        if (candidateAlert == null) {
            for (obj in detection.objects) {
                val isCustomWatch = userKeywords.any { keyword -> matchesClass(obj.label, keyword) }
                val isCritical = CRITICAL_NAVIGATION_CLASSES.contains(obj.label) || isCustomWatch

                val boxHeight = obj.boundingBox.bottom - obj.boundingBox.top
                val boxWidth = obj.boundingBox.right - obj.boundingBox.left
                val isLargeInPath = obj.position == "center" && (boxHeight > 0.35f || boxWidth > 0.35f)

                if (isCritical && (isLargeInPath || isCustomWatch)) {
                    alertKey = "obstacle_${obj.label}_${obj.position}"
                    if (canTrigger(alertKey, now, DEFAULT_COOLDOWN_MS)) {
                        val posText = when (obj.position) {
                            "left" -> "to your left"
                            "right" -> "to your right"
                            else -> "directly ahead"
                        }
                        candidateAlert = "${obj.label.replaceFirstChar { it.titlecase() }} $posText."
                        recordAlert(alertKey, now)
                        break
                    }
                }
            }
        }

        // 3. Check Ground Discontinuities / Relative Depth (Priority 3)
        if (candidateAlert == null && depth != null && depth.groundDiscontinuityDetected) {
            alertKey = "ground_discontinuity"
            if (canTrigger(alertKey, now, DEFAULT_COOLDOWN_MS)) {
                candidateAlert = "Caution: ground level change or curb ahead."
                recordAlert(alertKey, now)
            }
        }

        // Build Structured Temporal Scene JSON Log for LLM context
        val logObj = buildSceneLogJson(detection, depth, candidateAlert)
        if (temporalSceneBuffer.size >= 15) {
            temporalSceneBuffer.removeFirst()
        }
        temporalSceneBuffer.addLast(logObj)

        return WarningDecision(
            shouldWarn = candidateAlert != null,
            message = candidateAlert ?: "",
            sceneLogJson = logObj.toString(2),
        )
    }

    @Synchronized
    fun getRecentSceneTelemetryJson(): String {
        val arr = JSONArray()
        temporalSceneBuffer.forEach { arr.put(it) }
        return arr.toString(2)
    }

    @Synchronized
    fun reset() {
        lastAlertTimeMap.clear()
        temporalSceneBuffer.clear()
    }

    private fun canTrigger(key: String, nowMs: Long, cooldownMs: Long): Boolean {
        val last = lastAlertTimeMap[key] ?: 0L
        return (nowMs - last) >= cooldownMs
    }

    private fun recordAlert(key: String, nowMs: Long) {
        lastAlertTimeMap[key] = nowMs
    }

    private fun matchesClass(label: String, keyword: String): Boolean =
        label.equals(keyword, ignoreCase = true) ||
            "${label}s".equals(keyword, ignoreCase = true)

    private fun buildSceneLogJson(
        detection: DetectionResult,
        depth: DepthResult?,
        warningIssued: String?
    ): JSONObject {
        val obj = JSONObject()
        obj.put("timestampMs", System.currentTimeMillis())

        val objArr = JSONArray()
        for (d in detection.objects) {
            val item = JSONObject()
                .put("label", d.label)
                .put("confidence", (d.confidence * 100).toInt() / 100.0)
                .put("position", d.position)
                .put("approaching", d.approaching)
            if (d.relativeDepth != null) item.put("relativeDepth", d.relativeDepth)
            objArr.put(item)
        }
        obj.put("objects", objArr)

        val hasCenterObstacle = detection.objects.any { it.position == "center" && (it.boundingBox.bottom - it.boundingBox.top > 0.25f) }
        val freePath = when {
            !hasCenterObstacle -> "center"
            !detection.objects.any { it.position == "right" } -> "right"
            !detection.objects.any { it.position == "left" } -> "left"
            else -> "congested"
        }
        obj.put("freePath", freePath)

        if (depth != null) {
            obj.put("depthStatus", depth.relativeDepthSummary)
            obj.put("groundDiscontinuity", depth.groundDiscontinuityDetected)
        }
        if (warningIssued != null) {
            obj.put("warningIssued", warningIssued)
        }
        return obj
    }
}
