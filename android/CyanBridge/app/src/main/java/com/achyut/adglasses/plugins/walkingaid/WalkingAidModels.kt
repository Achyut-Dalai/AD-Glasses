package com.achyut.adglasses.plugins.walkingaid

data class SceneRecord(
    val timestampMs: Long,
    val imagePath: String,
    val description: String,
    val depthDescription: String?,
    val stateDecision: StateDecision,
)

enum class StateDecision {
    WARN,
    DESCRIBE,
    SKIP,
}

data class StateModelOutput(
    val decision: StateDecision,
    val message: String,
)

data class WalkingAidImageEntry(
    val timestampMs: Long,
    val imagePath: String,
    val description: String,
)
