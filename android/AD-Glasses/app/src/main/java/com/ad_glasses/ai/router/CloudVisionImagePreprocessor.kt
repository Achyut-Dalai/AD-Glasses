package com.ad_glasses.ai.router

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Log
import com.ad_glasses.shared.ai.AiVisionDetail
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Metadata for a cloud-ready image. Temporary files are owned by the caller and must be cleaned. */
internal data class PreparedCloudImage(
    val path: String,
    val originalWidth: Int,
    val originalHeight: Int,
    val outputWidth: Int,
    val outputHeight: Int,
    val inputBytes: Long,
    val outputBytes: Long,
    val temporary: Boolean,
) {
    fun cleanup() {
        if (temporary) runCatching { File(path).delete() }
    }
}

/**
 * Android-only codec boundary for cloud vision.
 *
 * The source file is measured without allocating its pixels, then decoded with BitmapFactory
 * subsampling before any exact resize. This prevents a 12MP camera frame from becoming a full-size
 * ARGB bitmap just to create a much smaller API upload. The transport still receives a normal file
 * path, keeping provider JSON/base64 code independent from Android bitmap handling.
 */
internal object CloudVisionImagePreprocessor {
    private const val TAG = "AssistantTiming"
    private const val STANDARD_MAX_DIMENSION = 1_024
    private const val TEXT_DETAIL_MAX_DIMENSION = 1_600
    private const val STANDARD_JPEG_QUALITY = 82
    private const val TEXT_DETAIL_JPEG_QUALITY = 88

    suspend fun prepare(
        context: Context,
        sourcePath: String,
        detail: AiVisionDetail,
    ): PreparedCloudImage = withContext(Dispatchers.Default) {
        val source = File(sourcePath)
        require(source.isFile) { "Image file not found" }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) {
            // Some provider-supported formats may not be decodable by BitmapFactory on every API
            // level. Preserve compatibility by uploading the original rather than failing Lens.
            return@withContext original(source)
        }

        val maxDimension = when (detail) {
            AiVisionDetail.STANDARD -> STANDARD_MAX_DIMENSION
            AiVisionDetail.TEXT_DETAIL -> TEXT_DETAIL_MAX_DIMENSION
        }
        if (maxOf(width, height) <= maxDimension) {
            return@withContext original(source, width, height)
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(width, height, maxDimension)
            inJustDecodeBounds = false
        }
        var decoded = BitmapFactory.decodeFile(source.absolutePath, options)
            ?: return@withContext original(source, width, height)
        var oriented: Bitmap? = null
        var scaled: Bitmap? = null
        try {
            oriented = applyExifOrientation(decoded, source.absolutePath)
            val orientedBitmap = oriented ?: decoded
            scaled = scaleToFit(orientedBitmap, maxDimension)
            val outputBitmap = scaled ?: orientedBitmap

            val cacheDir = File(context.cacheDir, "cloud-vision").apply { mkdirs() }
            val output = File(cacheDir, "vision-${UUID.randomUUID()}.jpg")
            val quality = when (detail) {
                AiVisionDetail.STANDARD -> STANDARD_JPEG_QUALITY
                AiVisionDetail.TEXT_DETAIL -> TEXT_DETAIL_JPEG_QUALITY
            }
            FileOutputStream(output).use { stream ->
                check(outputBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)) {
                    "Unable to encode cloud vision image"
                }
            }

            Log.i(
                TAG,
                "stage=vision_preprocess detail=$detail input=${width}x$height " +
                    "output=${outputBitmap.width}x${outputBitmap.height} " +
                    "inputBytes=${source.length()} outputBytes=${output.length()}",
            )
            PreparedCloudImage(
                path = output.absolutePath,
                originalWidth = width,
                originalHeight = height,
                outputWidth = outputBitmap.width,
                outputHeight = outputBitmap.height,
                inputBytes = source.length(),
                outputBytes = output.length(),
                temporary = true,
            )
        } finally {
            // These bitmaps are private to this one-shot preprocessing operation. Releasing them
            // promptly reduces peak memory during repeated Lens captures; correctness does not rely
            // on recycle() or on a particular garbage-collector schedule.
            val unique = linkedSetOf<Bitmap>()
            scaled?.let(unique::add)
            oriented?.let(unique::add)
            unique += decoded
            unique.forEach { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
        }
    }

    internal fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        if (width <= 0 || height <= 0 || maxDimension <= 0) return 1
        var sample = 1
        // BitmapFactory rounds sampling to powers of two. Keep the decoded result at least as large
        // as the requested target, then do one exact scale so we never decode the full 12MP frame.
        while (maxOf(width / (sample * 2), height / (sample * 2)) >= maxDimension) {
            sample *= 2
        }
        return sample
    }

    private fun scaleToFit(bitmap: Bitmap, maxDimension: Int): Bitmap? {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDimension) return null
        val scale = maxDimension.toFloat() / longest.toFloat()
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun applyExifOrientation(bitmap: Bitmap, path: String): Bitmap? {
        val orientation = runCatching {
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            else -> return null
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun original(file: File, width: Int = 0, height: Int = 0): PreparedCloudImage =
        PreparedCloudImage(
            path = file.absolutePath,
            originalWidth = width,
            originalHeight = height,
            outputWidth = width,
            outputHeight = height,
            inputBytes = file.length(),
            outputBytes = file.length(),
            temporary = false,
        )
}
