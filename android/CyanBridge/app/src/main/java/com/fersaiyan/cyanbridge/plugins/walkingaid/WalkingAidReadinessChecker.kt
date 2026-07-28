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
                    File(context.filesDir, "yolo_world_v2_s.tflite"),
                    File(context.filesDir, "yoloworld.tflite"),
                    File(context.getExternalFilesDir(null), "yolo_world_v2_s.tflite"),
                    File(context.getExternalFilesDir(null), "yoloworld.tflite"),
                    File(context.filesDir, "yolo11n_float16.tflite")
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
                val expected = if (yoloType == WalkingAidPreferences.MODEL_TYPE_YOLO_WORLD) "yolo_world_v2_s.tflite" else "yolo11n_float16.tflite"
                val corrupted = candidates.any { it.exists() }
                if (corrupted) {
                    details.add("❌ Local YOLO model file is corrupted or incomplete ($expected)")
                } else {
                    details.add("❌ Local YOLO model file missing ($expected)")
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
            val hasDepth = isValidModelFile(file) || isValidModelFile(extFile)
            if (!hasDepth) {
                if (file.exists() || extFile.exists()) {
                    details.add("❌ Local Depth Anything model file is corrupted or incomplete (depth_anything_v2_small.tflite)")
                } else {
                    details.add("❌ Local Depth Anything model file missing (depth_anything_v2_small.tflite)")
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
            if (!hasLocalLlm) {
                details.add("❌ No local LLM downloaded or configured in Local Models Settings")
            }
            hasLocalLlm
        }

        val allReady = yoloReady && depthReady && llmReady
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
