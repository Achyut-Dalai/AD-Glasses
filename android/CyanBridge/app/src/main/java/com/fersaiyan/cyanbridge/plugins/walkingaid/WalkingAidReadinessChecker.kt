package com.fersaiyan.cyanbridge.plugins.walkingaid

import android.content.Context
import com.fersaiyan.cyanbridge.agent.ProSubscriptionPrefs
import com.fersaiyan.cyanbridge.localmodels.storage.LocalModelStorageRepository
import java.io.File

data class WalkingAidReadinessResult(
    val isReady: Boolean,
    val yoloReady: Boolean,
    val depthReady: Boolean,
    val llmReady: Boolean,
    val requiresProForCloud: Boolean,
    val missingDetails: List<String>,
)

object WalkingAidReadinessChecker {

    fun isValidModelFile(file: File?): Boolean {
        return file != null && file.exists() && file.length() >= 1024L
    }

    fun checkReadiness(context: Context): WalkingAidReadinessResult {
        val details = mutableListOf<String>()
        val isProActive = ProSubscriptionPrefs.isActiveLocally(context)
        var requiresPro = false

        // 1. Object Detection Readiness Check
        val yoloSource = WalkingAidPreferences.getImageDescriptionSource(context)
        val yoloReady = if (yoloSource == "cloud") {
            if (!isProActive) {
                requiresPro = true
                details.add("🔒 Cloud Object Detection requires active Pro Subscription")
                false
            } else {
                true
            }
        } else {
            val yoloType = WalkingAidPreferences.getYoloModelType(context)
            val candidates = if (yoloType == WalkingAidPreferences.MODEL_TYPE_YOLO_WORLD) {
                listOf(
                    File(context.filesDir, "yolo_world.tflite"),
                    File(context.filesDir, "yolo_world_v2_s.tflite"),
                    File(context.filesDir, "yoloworld.tflite"),
                    File(context.getExternalFilesDir(null), "yolo_world.tflite"),
                    File(context.getExternalFilesDir(null), "yolo_world_v2_s.tflite"),
                    File(context.getExternalFilesDir(null), "yoloworld.tflite")
                )
            } else {
                listOf(
                    File(context.filesDir, "yolo11n_float16.tflite"),
                    File(context.filesDir, "yolov11n.tflite"),
                    File(context.getExternalFilesDir(null), "yolo11n_float16.tflite"),
                    File(context.getExternalFilesDir(null), "yolov11n.tflite")
                )
            }
            val validYoloFile = candidates.firstOrNull { isValidModelFile(it) }
            val yoloReady = validYoloFile != null
            if (!yoloReady) {
                val expected = if (yoloType == WalkingAidPreferences.MODEL_TYPE_YOLO_WORLD) "yolo_world.tflite" else "yolo11n_float16.tflite"
                val corrupted = candidates.any { it.exists() }
                if (corrupted) {
                    details.add("❌ Walking Aid YOLO model is corrupted or incomplete ($expected). Download it in this screen.")
                } else {
                    details.add("❌ Walking Aid YOLO model is missing ($expected). Download it in this screen.")
                }
            }
            yoloReady
        }

        // 2. Relative Depth Readiness Check
        val depthEnabled = WalkingAidPreferences.isDepthEnabled(context)
        val depthSource = WalkingAidPreferences.getDepthSource(context)
        val depthReady = if (!depthEnabled) {
            true
        } else if (depthSource == "cloud") {
            if (!isProActive) {
                requiresPro = true
                details.add("🔒 Cloud Relative Depth requires active Pro Subscription")
                false
            } else {
                true
            }
        } else {
            val file = File(context.filesDir, "depth_anything_v2_small.tflite")
            val extFile = File(context.getExternalFilesDir(null), "depth_anything_v2_small.tflite")
            val depthAnything3 = File(context.filesDir, "depth_anything_3_small.tflite")
            val externalDepthAnything3 = File(context.getExternalFilesDir(null), "depth_anything_3_small.tflite")
            val hasDepth = isValidModelFile(file) || isValidModelFile(extFile) ||
                isValidModelFile(depthAnything3) || isValidModelFile(externalDepthAnything3)
            if (!hasDepth) {
                if (file.exists() || extFile.exists() || depthAnything3.exists() || externalDepthAnything3.exists()) {
                    details.add("❌ Walking Aid depth model is corrupted or incomplete (depth_anything_3_small.tflite). Download it in this screen.")
                } else {
                    details.add("❌ Walking Aid depth model is missing (depth_anything_3_small.tflite). Download it in this screen.")
                }
            }
            hasDepth
        }

        // 3. Scene Reasoning LLM Readiness Check
        val stateModelSource = WalkingAidPreferences.getStateModelSource(context)
        val llmReady = if (stateModelSource == "cloud") {
            if (!isProActive) {
                requiresPro = true
                details.add("🔒 Cloud Scene LLM requires active Pro Subscription")
                false
            } else {
                true
            }
        } else {
            val installedLocalModels = LocalModelStorageRepository.listInstalled(context)
            val hasLocalLlm = installedLocalModels.isNotEmpty()
            hasLocalLlm
        }

        // Scene LLM rewriting is optional. Deterministic YOLO/depth warnings remain usable
        // when no local LLM is installed or the configured cloud LLM is unavailable.
        val allReady = yoloReady && depthReady
        return WalkingAidReadinessResult(
            isReady = allReady,
            yoloReady = yoloReady,
            depthReady = depthReady,
            llmReady = llmReady,
            requiresProForCloud = requiresPro,
            missingDetails = details,
        )
    }
}
