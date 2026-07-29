package com.fersaiyan.cyanbridge.plugins.walkingaid

data class WalkingAidModelCatalogEntry(
    val id: String,
    val displayName: String,
    val description: String,
    val sourceUrl: String,
    val sourcePageUrl: String,
    val expectedFilename: String,
    val sizeBytes: Long,
    val licenseNote: String,
)

/** Public LiteRT/TFLite artifacts installed directly into Walking Aid's model directory. */
object WalkingAidModelCatalog {
    val entries = listOf(
        WalkingAidModelCatalogEntry(
            id = "yolo11n-tflite",
            displayName = "YOLO11n (LiteRT/TFLite)",
            description = "Fast COCO object detection for local obstacle awareness.",
            sourceUrl = "https://huggingface.co/mehmetkeremturkcan/RubikPi.YOLO11x.Detection/resolve/main/yolo11n.tflite",
            sourcePageUrl = "https://huggingface.co/mehmetkeremturkcan/RubikPi.YOLO11x.Detection",
            expectedFilename = "yolo11n_float16.tflite",
            sizeBytes = 2_971_416L,
            licenseNote = "Ultralytics YOLO11 is distributed under AGPL-3.0 terms.",
        ),
        WalkingAidModelCatalogEntry(
            id = "depth-anything-3-small-litert",
            displayName = "Depth Anything 3 Small (LiteRT)",
            description = "On-device monocular relative-depth estimation for walking hazards.",
            sourceUrl = "https://huggingface.co/litert-community/Depth-Anything-3-Small/resolve/main/da3_small_gpu_fp16.tflite",
            sourcePageUrl = "https://huggingface.co/litert-community/Depth-Anything-3-Small",
            expectedFilename = "depth_anything_3_small.tflite",
            sizeBytes = 55_035_456L,
            licenseNote = "Depth Anything 3 Small and this LiteRT conversion are Apache-2.0.",
        ),
        WalkingAidModelCatalogEntry(
            id = "yolo-world-tflite",
            displayName = "YOLO-World (LiteRT/TFLite)",
            description = "Open-vocabulary detector with a quantized TFLite export.",
            sourceUrl = "https://huggingface.co/wondervictor/YOLO-World/resolve/main/yolo_world_x_coco_zeroshot_rep_integer_quant.tflite",
            sourcePageUrl = "https://huggingface.co/wondervictor/YOLO-World",
            expectedFilename = "yolo_world.tflite",
            sizeBytes = 73_490_408L,
            licenseNote = "This YOLO-World export is distributed under GPL-3.0 terms.",
        ),
    )

    fun findById(id: String): WalkingAidModelCatalogEntry? = entries.firstOrNull { it.id == id }

    fun detectorFor(type: String): WalkingAidModelCatalogEntry =
        if (type == WalkingAidPreferences.MODEL_TYPE_YOLO_WORLD) entries[2] else entries[0]

    val depth: WalkingAidModelCatalogEntry
        get() = entries[1]
}
