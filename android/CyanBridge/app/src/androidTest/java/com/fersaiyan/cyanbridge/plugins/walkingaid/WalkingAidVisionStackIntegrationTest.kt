package com.fersaiyan.cyanbridge.plugins.walkingaid

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.fersaiyan.cyanbridge.plugins.walkingaid.vision.LiteRtVisionBackend
import com.fersaiyan.cyanbridge.plugins.walkingaid.vision.VisionFrame
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

@LargeTest
@RunWith(AndroidJUnit4::class)
class WalkingAidVisionStackIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun realModels_detectFixture_matchUserFocus_andEstimateDepth() = runBlocking {
        val enabled = InstrumentationRegistry.getArguments()
            .getString(ARG_RUN_REAL_MODELS)
            ?.toBooleanStrictOrNull() == true
        assumeTrue(
            "Opt in with -Pandroid.testInstrumentationRunnerArguments.$ARG_RUN_REAL_MODELS=true",
            enabled,
        )

        WalkingAidPreferences.setYoloModelType(context, WalkingAidPreferences.MODEL_TYPE_YOLO11)
        ensureModel(WalkingAidModelCatalog.detectorFor(WalkingAidPreferences.MODEL_TYPE_YOLO11))
        ensureModel(WalkingAidModelCatalog.depth)
        val fixture = downloadFixture()
        val bitmap = checkNotNull(BitmapFactory.decodeFile(fixture.absolutePath)) {
            "Could not decode Walking Aid fixture ${fixture.absolutePath}"
        }
        val backend = LiteRtVisionBackend(context)
        try {
            assertTrue(
                "Detector failed to load: ${backend.detectorInitError}",
                backend.isDetectorModelLoaded,
            )
            assertTrue(
                "Depth Anything failed to load: ${backend.depthInitError}",
                backend.isDepthModelLoaded,
            )

            val now = System.currentTimeMillis()
            val frame = VisionFrame(bitmap = bitmap, timestampMs = now)
            val detection = backend.detect(frame)
            assertFalse("Detector error: ${detection.errorMessage}", detection.isError)
            assertTrue("Expected at least one object, got ${detection.objects}", detection.objects.isNotEmpty())

            val labels = detection.objects.map { it.label }
            assertTrue(
                "Expected the public bus fixture to contain people, got $labels",
                labels.any { it == "person" },
            )
            val focusMatches = WalkingAidFocusMapper.matchDetectedLabels(
                text = "Please warn me about peopel",
                detectedLabels = labels,
            )
            assertTrue(
                "Focus text did not match real detections. labels=$labels matches=$focusMatches",
                focusMatches.any { it == "person" },
            )

            val depth = backend.estimateDepth(frame)
            assertNotNull("Depth Anything returned no result", depth)
            assertTrue(depth!!.closestRegion in setOf("left", "center", "right"))
            assertTrue("Depth summary was empty", depth.relativeDepthSummary.isNotBlank())
            assertTrue("Depth inference time was invalid", depth.inferenceTimeMs >= 0L)
        } finally {
            backend.close()
            bitmap.recycle()
        }
    }

    private suspend fun ensureModel(entry: WalkingAidModelCatalogEntry) {
        if (WalkingAidModelInstaller.isInstalled(context, entry)) return
        WalkingAidModelInstaller.install(context, entry) { }
        check(WalkingAidModelInstaller.isInstalled(context, entry)) {
            "Model download did not install ${entry.displayName}"
        }
    }

    private fun downloadFixture(): File {
        val target = File(context.cacheDir, "walking_aid_bus_fixture.jpg")
        if (target.length() >= 1024L) return target
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        client.newCall(Request.Builder().url(FIXTURE_URL).build()).execute().use { response ->
            check(response.isSuccessful) { "Fixture download failed: HTTP ${response.code}" }
            val body = checkNotNull(response.body) { "Fixture download returned no body" }
            target.outputStream().use { output -> body.byteStream().use { it.copyTo(output) } }
        }
        check(target.length() >= 1024L) { "Fixture download was incomplete" }
        return target
    }

    companion object {
        private const val ARG_RUN_REAL_MODELS = "walkingAidRealModels"
        private const val FIXTURE_URL = "https://ultralytics.com/images/bus.jpg"
    }
}
