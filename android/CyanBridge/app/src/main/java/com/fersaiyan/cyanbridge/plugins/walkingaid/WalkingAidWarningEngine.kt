package com.fersaiyan.cyanbridge.plugins.walkingaid

import com.fersaiyan.cyanbridge.plugins.walkingaid.vision.DepthResult
import com.fersaiyan.cyanbridge.plugins.walkingaid.vision.DetectionResult
import org.json.JSONArray
import org.json.JSONObject

data class WarningDecision(
    val shouldWarn: Boolean,
    val message: String,
    val sceneLogJson: String,
    val frameTimestampMs: Long,
    val frameAgeMs: Long,
    val isStale: Boolean,
    val trackId: Long? = null,
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
        frameTimestampMs: Long = System.currentTimeMillis(),
        nowMs: Long = System.currentTimeMillis(),
    ): WarningDecision {
        val frameAgeMs = (nowMs - frameTimestampMs).coerceAtLeast(0L)
        val isStale = frameAgeMs > MAX_WARNING_FRAME_AGE_MS
        val customWatchLabels = WalkingAidFocusMapper.matchDetectedLabels(
            text = focusDescription,
            detectedLabels = detection.objects.map { it.label },
        ).map { it.lowercase() }.toSet()

        var candidateAlert: String? = null
        var candidateKey = ""
        var warningTrackId: Long? = null

        // 1. Check approaching center hazards (Priority 1: Immediate collision risk)
        for (obj in detection.objects.takeUnless { isStale }.orEmpty()) {
            val isCustomWatch = obj.label.lowercase() in customWatchLabels
            val isCritical = CRITICAL_NAVIGATION_CLASSES.contains(obj.label) || isCustomWatch

            if (isCritical && obj.position == "center" && obj.approaching) {
                candidateKey = alertKey("approaching", obj)
                if (canTrigger(candidateKey, nowMs, URGENT_COOLDOWN_MS)) {
                    candidateAlert = "Warning: approaching ${obj.label} straight ahead!"
                    warningTrackId = obj.trackId
                    recordAlert(candidateKey, nowMs)
                    break
                }
            }
        }

        // 2. Check prominent immediate obstacles in direct path (Priority 2)
        if (candidateAlert == null) {
            for (obj in detection.objects.takeUnless { isStale }.orEmpty()) {
                // An approaching track uses the shorter urgent cooldown and must not fall through
                // to a second obstacle alert while that urgent alert is cooling down.
                if (obj.approaching) continue
                val isCustomWatch = obj.label.lowercase() in customWatchLabels
                val isCritical = CRITICAL_NAVIGATION_CLASSES.contains(obj.label) || isCustomWatch

                val boxHeight = obj.boundingBox.bottom - obj.boundingBox.top
                val boxWidth = obj.boundingBox.right - obj.boundingBox.left
                val isLargeInPath = obj.position == "center" && (boxHeight > 0.35f || boxWidth > 0.35f)

                if (isCritical && (isLargeInPath || isCustomWatch)) {
                    candidateKey = alertKey("obstacle", obj)
                    if (canTrigger(candidateKey, nowMs, DEFAULT_COOLDOWN_MS)) {
                        val posText = when (obj.position) {
                            "left" -> "to your left"
                            "right" -> "to your right"
                            else -> "directly ahead"
                        }
                        candidateAlert = "${obj.label.replaceFirstChar { it.titlecase() }} $posText."
                        warningTrackId = obj.trackId
                        recordAlert(candidateKey, nowMs)
                        break
                    }
                }
            }
        }

        // 3. Check Ground Discontinuities / Relative Depth (Priority 3)
        if (!isStale && candidateAlert == null && depth != null && depth.groundDiscontinuityDetected) {
            candidateKey = "ground_discontinuity"
            if (canTrigger(candidateKey, nowMs, DEFAULT_COOLDOWN_MS)) {
                candidateAlert = "Caution: ground level change or curb ahead."
                recordAlert(candidateKey, nowMs)
            }
        }

        // Build Structured Temporal Scene JSON Log for LLM context
        val logObj = buildSceneLogJson(
            detection = detection,
            depth = depth,
            warningIssued = candidateAlert,
            frameTimestampMs = frameTimestampMs,
            frameAgeMs = frameAgeMs,
            isStale = isStale,
        )
        if (temporalSceneBuffer.size >= 15) {
            temporalSceneBuffer.removeFirst()
        }
        temporalSceneBuffer.addLast(logObj)

        return WarningDecision(
            shouldWarn = candidateAlert != null,
            message = candidateAlert ?: "",
            sceneLogJson = logObj.toString(2),
            frameTimestampMs = frameTimestampMs,
            frameAgeMs = frameAgeMs,
            isStale = isStale,
            trackId = warningTrackId,
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

    @Synchronized
    fun clearTrackCooldowns(trackIds: Collection<Long>) {
        if (trackIds.isEmpty()) return
        val markers = trackIds.map { "_track_$it" }
        lastAlertTimeMap.keys.removeAll { key -> markers.any(key::endsWith) }
    }

    private fun canTrigger(key: String, nowMs: Long, cooldownMs: Long): Boolean {
        val last = lastAlertTimeMap[key] ?: 0L
        return (nowMs - last) >= cooldownMs
    }

    private fun recordAlert(key: String, nowMs: Long) {
        lastAlertTimeMap[key] = nowMs
    }

    private fun alertKey(prefix: String, detection: com.fersaiyan.cyanbridge.plugins.walkingaid.vision.DetectedObject): String =
        detection.trackId?.let { "${prefix}_track_$it" }
            ?: "${prefix}_${detection.label}_${detection.position}"

    private fun buildSceneLogJson(
        detection: DetectionResult,
        depth: DepthResult?,
        warningIssued: String?,
        frameTimestampMs: Long,
        frameAgeMs: Long,
        isStale: Boolean,
    ): JSONObject {
        val obj = JSONObject()
        obj.put("timestampMs", System.currentTimeMillis())
        obj.put("frameTimestampMs", frameTimestampMs)
        obj.put("frameAgeMs", frameAgeMs)
        obj.put("stale", isStale)

        val objArr = JSONArray()
        for (d in detection.objects) {
            val item = JSONObject()
                .put("label", d.label)
                .put("confidence", (d.confidence * 100).toInt() / 100.0)
                .put("position", d.position)
                .put("approaching", d.approaching)
                .put("motionState", d.motionState.name.lowercase())
            if (d.trackId != null) item.put("trackId", d.trackId)
            if (d.timeToCollisionSeconds != null) {
                item.put("timeToCollisionSeconds", (d.timeToCollisionSeconds * 10).toInt() / 10.0)
            }
            if (d.relativeDepth != null) item.put("relativeDepth", d.relativeDepth)
            objArr.put(item)
        }
        obj.put("objects", objArr)
        if (detection.clearedTrackIds.isNotEmpty()) {
            val cleared = JSONArray()
            detection.clearedTrackIds.forEach(cleared::put)
            obj.put("clearedTrackIds", cleared)
        }

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

    const val MAX_WARNING_FRAME_AGE_MS = 6_000L
}
