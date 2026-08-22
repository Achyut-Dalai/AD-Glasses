package com.ad_glasses.ui.localagent

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ad_glasses.localagent.dailysummary.DailySummaryGenerator
import com.ad_glasses.localagent.dailysummary.DailySummaryPrefs
import com.ad_glasses.localagent.dailysummary.DailySummaryRegenerateWorker
import com.ad_glasses.localagent.dailysummary.DailySummaryRunHistory
import com.ad_glasses.localagent.memory.LocalAgentMemoryStore
import com.ad_glasses.shared.ui.localagent.DailySummaryScreen
import com.ad_glasses.ui.appearance.AppearancePreferences
import com.ad_glasses.ui.appearance.rememberAppearanceSettings
import com.ad_glasses.ui.theme.ADGlassesTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DailySummaryActivity : AppCompatActivity() {

    private val workManager by lazy { WorkManager.getInstance(this) }
    private var summaryText by mutableStateOf("")
    private var statusText by mutableStateOf("")
    private var isRegenerating by mutableStateOf(false)
    private var progressValue by mutableStateOf(0)
    private var progressTitle by mutableStateOf("Regenerating daily summary")
    private var progressDetail by mutableStateOf("Estimating remaining time...")

    private val date: String by lazy {
        intent.getStringExtra(EXTRA_DATE)?.trim().orEmpty().ifBlank {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(System.currentTimeMillis()))
        }
    }

    private val file by lazy { LocalAgentMemoryStore.dailySummaryFileForDate(this, date) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocalAgentMemoryStore.ensureSeedFiles(this)
        val appearancePreferences = AppearancePreferences(this)
        setContent {
            val appearance by rememberAppearanceSettings(appearancePreferences)
            ADGlassesTheme(appearance) {
                DailySummaryScreen(
                    title = "Daily summary ($date)",
                    path = file.absolutePath,
                    status = statusText,
                    summary = summaryText,
                    isBusy = isRegenerating,
                    progress = progressValue,
                    progressTitle = progressTitle,
                    progressDetail = progressDetail,
                    onRefresh = ::refreshFromDisk,
                    onRegenerate = ::regenerate,
                    onShare = ::shareCurrent,
                    onBack = ::finish,
                )
            }
        }

        refreshFromDisk()
        observeRegenerationWork()
    }

    private fun refreshFromDisk() {
        val loaded = LocalAgentMemoryStore.readText(file).trimEnd()
        val text = if (loaded.isNotBlank()) loaded else "(No daily summary generated yet. Tap Regenerate.)"
        summaryText = text

        val last = DailySummaryPrefs.getLastGeneratedAtMs(this, date)
        statusText = if (last > 0L) {
            val t = SimpleDateFormat("HH:mm", Locale.US).format(Date(last))
            "Last generated: $t"
        } else {
            "Not generated yet"
        }
    }

    private fun setBusy(busy: Boolean) {
        isRegenerating = busy
    }

    private fun regenerate() {
        val cooldown = DailySummaryPrefs.remainingCooldownMs(this, date)
        if (cooldown > 0L) {
            val seconds = (cooldown / 1000L).coerceAtLeast(1L)
            Toast.makeText(this, "Please wait ${seconds}s before regenerating.", Toast.LENGTH_SHORT).show()
            return
        }

        setBusy(true)
        val inputTokens = DailySummaryGenerator.estimateInputTokensForDate(this, date)
        val estimate = DailySummaryRunHistory.estimate(
            context = this,
            providerHint = DailySummaryGenerator.providerHint(this),
            inputTokens = inputTokens,
        )

        progressValue = 1
        progressTitle = "Regenerating daily summary (${estimate.provider})"
        progressDetail = formatEtaText(
            etaMs = estimate.expectedTotalMs,
            sampleCount = estimate.sampleCount,
            stage = "Queued",
        )
        statusText = "Generating…"

        val request = DailySummaryRegenerateWorker.buildRequest(date)
        workManager.enqueueUniqueWork(
            DailySummaryRegenerateWorker.uniqueWorkName(date),
            ExistingWorkPolicy.REPLACE,
            request,
        )

        Toast.makeText(this, "Generating daily summary in background…", Toast.LENGTH_SHORT).show()
    }

    private fun observeRegenerationWork() {
        workManager.getWorkInfosForUniqueWorkLiveData(
            DailySummaryRegenerateWorker.uniqueWorkName(date),
        ).observe(this) { infos ->
            val info = infos.firstOrNull() ?: run {
                setBusy(false)
                return@observe
            }
            renderWorkInfo(info)
        }
    }

    private fun renderWorkInfo(info: WorkInfo) {
        when (info.state) {
            WorkInfo.State.ENQUEUED,
            WorkInfo.State.BLOCKED,
            WorkInfo.State.RUNNING -> {
                setBusy(true)
                val percent = info.progress.getInt(DailySummaryRegenerateWorker.KEY_PROGRESS_PERCENT, 0)
                    .coerceIn(0, 100)
                val etaMs = info.progress.getLong(DailySummaryRegenerateWorker.KEY_ETA_MS, 0L)
                val stage = info.progress.getString(DailySummaryRegenerateWorker.KEY_STAGE)
                    ?.trim()
                    .orEmpty()
                    .ifBlank { "Generating summary" }
                val provider = info.progress.getString(DailySummaryRegenerateWorker.KEY_PROVIDER)
                    ?.trim()
                    .orEmpty()
                    .ifBlank { DailySummaryGenerator.providerHint(this) }
                val sampleCount = info.progress.getInt(DailySummaryRegenerateWorker.KEY_SAMPLE_COUNT, 0)
                val bulletDone = info.progress.getInt(DailySummaryRegenerateWorker.KEY_BULLET_DONE, 0)
                val bulletTotal = info.progress.getInt(DailySummaryRegenerateWorker.KEY_BULLET_TOTAL, 0)

                progressValue = percent
                progressTitle = "Regenerating daily summary (${provider})"
                progressDetail = formatEtaText(
                    etaMs = etaMs,
                    sampleCount = sampleCount,
                    stage = stage,
                    bulletDone = bulletDone,
                    bulletTotal = bulletTotal,
                )
                statusText = if (percent > 0) {
                    "Generating… $percent%"
                } else {
                    "Generating…"
                }
            }

            WorkInfo.State.SUCCEEDED -> {
                setBusy(false)
                progressValue = 100
                statusText = "Generation complete"
                refreshFromDisk()
                Toast.makeText(this, "Daily summary saved", Toast.LENGTH_SHORT).show()
            }

            WorkInfo.State.FAILED,
            WorkInfo.State.CANCELLED -> {
                setBusy(false)
                statusText = "Generation failed"
                val error = info.outputData.getString(DailySummaryRegenerateWorker.KEY_ERROR)
                    ?.trim()
                    .orEmpty()
                    .ifBlank { "Failed to regenerate summary" }
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun formatEtaText(
        etaMs: Long,
        sampleCount: Int,
        stage: String,
        bulletDone: Int = 0,
        bulletTotal: Int = 0,
    ): String {
        val safeEta = etaMs.coerceAtLeast(0L)
        val totalSeconds = (safeEta / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        val etaLabel = if (minutes > 0L) {
            String.format(Locale.US, "%dm %02ds", minutes, seconds)
        } else {
            String.format(Locale.US, "%ds", seconds)
        }
        val sampleLabel = if (sampleCount > 0) {
            "avg of last ${sampleCount.coerceAtMost(3)} runs"
        } else {
            "cold-start estimate"
        }
        val bulletLabel = if (bulletTotal > 0) {
            " · bullets $bulletDone/$bulletTotal"
        } else {
            ""
        }
        return "$stage$bulletLabel · ETA ~$etaLabel ($sampleLabel)"
    }

    private fun shareCurrent() {
        val content = summaryText.trim()
        if (content.isBlank()) {
            Toast.makeText(this, "Nothing to share", Toast.LENGTH_SHORT).show()
            return
        }

        val payload = buildString {
            append("Daily summary ($date)\n")
            append("File: ${file.absolutePath}\n\n")
            append(content)
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Daily summary ($date)")
            putExtra(Intent.EXTRA_TEXT, payload)
        }

        startActivity(Intent.createChooser(intent, "Share daily summary"))
    }

    companion object {
        const val EXTRA_DATE = "extra_date"
    }
}
