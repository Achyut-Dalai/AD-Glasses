package com.ad_glasses.localagent.dailysummary

import android.content.Context
import com.ad_glasses.agent.LocalAgentPrefs as AutomationPrefs
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.ad_glasses.ai.router.ApiTokenClient
import com.ad_glasses.ai.router.AiProviderPrefs
import com.ad_glasses.localagent.memory.LocalAgentMemoryStore
import com.ad_glasses.localagent.dailyfacts.DailyBulletsSettings

object DailySummaryGenerator {
    private const val MAX_LOCAL_EVENT_BULLETS_RENDERED = 220
    private const val MAX_LOCAL_EVENT_BULLETS_CHARS = 52_000
    private const val MAX_INCREMENTAL_APPEND_BULLETS = 20
    private const val DEDUPE_EVENT_WINDOW_MS = 8 * 60 * 1000L

    private data class ProviderResponse(
        val text: String,
        val metrics: DailySummaryRunHistory.RunMetrics,
    )

    private data class ScreenCaptureEvent(
        val tsMs: Long,
        val packageName: String,
        val text: String,
    )

    private data class EventBullet(
        val tsMs: Long,
        val packageName: String,
        val bullet: String,
    )

    data class BulletProgress(
        val done: Int,
        val total: Int,
    )

    private data class Input(
        val date: String,
        val confirmedFacts: String,
        val previousSummary: String?,
        val newScreenSnippets: String,
        val screenEvents: List<ScreenCaptureEvent>,
        val processedCaptureMaxTsMs: Long,
        val isIncremental: Boolean,
        val outputFile: File,
    )

    private fun todayString(nowMs: Long = System.currentTimeMillis()): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMs))
    }

    fun providerHint(context: Context): String {
        val profile = AiProviderPrefs.getActiveProfile(context) ?: return "cloud_unconfigured"
        return "cloud:${profile.provider.wire}:${profile.model}"
    }

    fun estimateInputTokensForDate(
        context: Context,
        date: String = todayString(),
    ): Int {
        val input = buildInputForDate(context = context, date = date)
        val prompt = buildPrompt(input)
        return DailySummaryRunHistory.estimateTokenCount(prompt)
    }

    fun estimateBulletEventsForDate(
        context: Context,
        date: String = todayString(),
    ): Int = 0

    private fun buildInputForDate(
        context: Context,
        date: String = todayString(),
        maxCaptureLines: Int = 220,
        maxCharsPerCapture: Int = 1_200,
        maxTotalChars: Int = 48_000,
        forceFullRebuild: Boolean = false,
    ): Input {
        LocalAgentMemoryStore.ensureSeedFiles(context)

        val confirmedFile = LocalAgentMemoryStore.confirmedDailyFactsFileForDate(context, date)
        if (!confirmedFile.exists()) {
            LocalAgentMemoryStore.writeText(
                confirmedFile,
                "# Confirmed daily facts ($date)\n\n- \n",
            )
        }
        val confirmedFacts = LocalAgentMemoryStore.readText(confirmedFile).trim()

        val lastProcessedAtMs = DailySummaryPrefs.getLastCaptureProcessedAtMs(context, date)
        val hasExistingSummary = DailySummaryPrefs.getLastGeneratedAtMs(context, date) > 0L
        
        val previousSummary = if (hasExistingSummary && !forceFullRebuild) {
            val summaryFile = LocalAgentMemoryStore.dailySummaryFileForDate(context, date)
            val existing = LocalAgentMemoryStore.readText(summaryFile).trim()
            if (existing.isNotBlank() && !existing.contains("(Generate from Settings")) existing else null
        } else {
            null
        }

        val allCaptureLines = LocalAgentMemoryStore.readScreenCaptureLines(context, date, maxCaptureLines * 3)
        val allEvents = parseScreenCaptureEvents(
            lines = allCaptureLines,
            maxCharsPerCapture = maxCharsPerCapture,
        )

        val newEvents = if (lastProcessedAtMs > 0L && allEvents.isNotEmpty() && previousSummary != null && !forceFullRebuild) {
            allEvents
                .filter { it.tsMs > lastProcessedAtMs }
                .takeLast(maxCaptureLines)
        } else {
            allEvents.takeLast(maxCaptureLines)
        }

        val snippets = if (newEvents.isNotEmpty()) {
            formatTailScreenCaptures(
                events = newEvents,
                maxTotalChars = maxTotalChars,
            )
        } else {
            "(no new screen captures since last summary)"
        }

        val isIncremental = lastProcessedAtMs > 0L && previousSummary != null && !forceFullRebuild

        val out = LocalAgentMemoryStore.dailySummaryFileForDate(context, date)

        return Input(
            date = date,
            confirmedFacts = confirmedFacts,
            previousSummary = previousSummary,
            newScreenSnippets = snippets,
            screenEvents = newEvents,
            processedCaptureMaxTsMs = newEvents.maxOfOrNull { it.tsMs } ?: 0L,
            isIncremental = isIncremental,
            outputFile = out,
        )
    }

    private fun parseScreenCaptureEvents(
        lines: List<String>,
        maxCharsPerCapture: Int,
    ): List<ScreenCaptureEvent> {
        if (lines.isEmpty()) return emptyList()

        val lastSeenTsByKey = HashMap<String, Long>()
        val out = ArrayList<ScreenCaptureEvent>(lines.size)

        for (line in lines) {
            val obj = runCatching { JSONObject(line) }.getOrNull() ?: continue
            val ts = obj.optLong("ts_ms", 0L)
            val pkg = obj.optString("package", "?").ifBlank { "?" }
            val rawText = obj.optString("text", "").trim()
            if (rawText.isBlank()) continue

            val text = rawText
                .replace(Regex("\\s+"), " ")
                .replace(Regex("[\\u0000-\\u001F]"), " ")
                .trim()
                .take(maxCharsPerCapture)
            if (!looksLikeUsefulCapture(text)) continue

            val dedupeKey = "${pkg.lowercase(Locale.US)}|" + text
                .lowercase(Locale.US)
                .replace(Regex("\\s+"), " ")
                .take(500)
            val previousTs = lastSeenTsByKey[dedupeKey]
            if (previousTs != null && ts > 0L && previousTs > 0L && (ts - previousTs) in 0 until DEDUPE_EVENT_WINDOW_MS) {
                continue
            }
            lastSeenTsByKey[dedupeKey] = ts

            out += ScreenCaptureEvent(
                tsMs = ts,
                packageName = pkg,
                text = text,
            )
        }

        return out
    }

    private fun formatTailScreenCaptures(
        events: List<ScreenCaptureEvent>,
        maxTotalChars: Int,
    ): String {
        if (events.isEmpty()) return "(screen captures file is empty)"

        val timeFmt = SimpleDateFormat("HH:mm", Locale.US)

        val out = StringBuilder()
        var remaining = maxTotalChars

        for (event in events) {
            if (remaining <= 0) break
            val time = if (event.tsMs > 0L) timeFmt.format(Date(event.tsMs)) else "??:??"
            val row = "- [$time] ${event.packageName}: ${event.text}\n"
            if (row.length > remaining) {
                val clipped = row.take(remaining.coerceAtLeast(0))
                out.append(clipped)
                break
            }
            out.append(row)
            remaining -= row.length
        }

        val rendered = out.toString().trimEnd()
        return if (rendered.isBlank()) {
            "(screen captures existed but were mostly noisy/duplicated UI text)"
        } else {
            rendered
        }
    }

    private suspend fun prepareInputForGeneration(
        context: Context,
        input: Input,
        onBulletProgress: ((BulletProgress) -> Unit)? = null,
    ): Input = input

    private fun buildFullPrompt(input: Input): String {
        return """
You are my personal AI assistant. Create a daily summary from the information below.

FACTS I CONFIRMED TODAY:
${input.confirmedFacts.ifBlank { "(none)" }}

SCREEN ACTIVITY (OCR from my phone - may have UI noise but meaningful content is real):
${input.newScreenSnippets.ifBlank { "(no screen captures)" }}

OUTPUT FORMAT:
# Daily Summary (${input.date})

[Short first-person narrative - what you did today]

## Highlights
- [bullet 1]
- [bullet 2]
- [bullet 3]
- [bullet 4]
- [bullet 5]

## Open questions
[Only include if real uncertainties exist]

IMPORTANT:
- Do not invent facts
- Do not refuse - always produce a summary even if input is messy
- Do not apologize or say you can't help
- If uncertain, note it in "Open questions"
""".trim()
    }

    private fun buildIncrementalPrompt(input: Input): String {
        return """
You are my personal AI assistant. Your task is to UPDATE a daily summary.

CURRENT SUMMARY (keep this, just add to it):
${input.previousSummary}

NEW SCREEN ACTIVITY (add relevant items to the summary above):
${input.newScreenSnippets.ifBlank { "(no new captures since last summary)" }}

OUTPUT INSTRUCTIONS:
- Output Markdown
- Keep the "# Daily Summary (YYYY-MM-DD)" header  
- Keep existing narrative and bullets
- ADD new events from the new captures above
- If new captures contain nothing useful, just return the original summary unchanged
- NEVER refuse, NEVER say you can't, NEVER ask for more details
- If uncertain, note it briefly in "## Open questions"

Remember: You MUST output a valid summary. Do not refuse.
""".trim()
    }

    suspend fun generateAndStore(
        context: Context,
        date: String = todayString(),
        onBulletProgress: ((BulletProgress) -> Unit)? = null,
    ): Result<File> {
        val forceFullRebuild = false

        return runCatching {
            val input = buildInputForDate(context, date, forceFullRebuild = forceFullRebuild)
            val preparedInput = prepareInputForGeneration(context, input, onBulletProgress = onBulletProgress)
            val (usedInput, providerResult) = try {
                preparedInput to generateSummary(context, buildPrompt(preparedInput))
            } catch (_: Throwable) {
                val fallbackInput = buildInputForDate(
                    context = context,
                    date = date,
                    maxCaptureLines = 50,
                    maxCharsPerCapture = 400,
                    maxTotalChars = 8_000,
                    forceFullRebuild = true,
                )
                val preparedFallbackInput = prepareInputForGeneration(
                    context,
                    fallbackInput,
                    onBulletProgress = onBulletProgress,
                )
                preparedFallbackInput to generateSummary(context, buildFullPrompt(preparedFallbackInput))
            }

            val summary = providerResult.text.trim()

            require(summary.isNotBlank()) { "Empty summary returned" }

            LocalAgentMemoryStore.writeText(usedInput.outputFile, summary + "\n")
            val generatedAtMs = System.currentTimeMillis()
            DailySummaryPrefs.setLastGeneratedAtMs(context, date, generatedAtMs)
            val processedAtMs = usedInput.processedCaptureMaxTsMs
                .takeIf { it > 0L }
                ?: generatedAtMs
            DailySummaryPrefs.setLastCaptureProcessedAtMs(context, date, processedAtMs)
            DailySummaryRunHistory.record(context, providerResult.metrics)

            usedInput.outputFile
        }
    }

    private suspend fun generateSummary(context: Context, prompt: String): ProviderResponse =
        runCloudApi(context, prompt)

    private suspend fun runCloudApi(context: Context, prompt: String): ProviderResponse {
        val inputTokens = DailySummaryRunHistory.estimateTokenCount(prompt)
        val started = System.currentTimeMillis()
        val reply = ApiTokenClient.chat(
            context = context,
            messages = listOf(mapOf("role" to "user", "content" to prompt)),
            maxTokens = 1200,
        ).getOrElse { err ->
            throw IllegalStateException("Cloud AI unavailable (${err.message}).", err)
        }.trim()
        val totalMs = (System.currentTimeMillis() - started).coerceAtLeast(1L)

        if (!isUsableSummaryReply(reply)) {
            throw IllegalStateException("Unable to generate daily summary from active cloud provider.")
        }

        val outputTokens = DailySummaryRunHistory.estimateTokenCount(reply)
        val promptMs = (totalMs * 0.35).toLong().coerceAtLeast(1L)
        val generationMs = (totalMs - promptMs).coerceAtLeast(1L)
        return ProviderResponse(
            text = reply,
            metrics = DailySummaryRunHistory.RunMetrics(
                provider = "cloud_api",
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                promptTokensPerSec = inputTokens / (promptMs / 1000.0),
                generationTokensPerSec = outputTokens / (generationMs / 1000.0),
                totalMs = totalMs,
            ),
        )
    }

    private fun isUsableSummaryReply(text: String): Boolean {
        val s = text.trim()
        val lower = s.lowercase(Locale.US)
        if (s.isBlank()) return false
        if (s.startsWith("Demo mode reply:", ignoreCase = true)) return false
        if (s.startsWith("Cloud AI unavailable (", ignoreCase = true)) return false
        if (s.startsWith("Company backend is not configured", ignoreCase = true)) return false
        if (s.startsWith("I couldn't generate a reply yet.", ignoreCase = true)) return false
        if (lower.startsWith("i apologize")) return false
        if (lower.startsWith("i'm sorry")) return false
        if (lower.startsWith("sorry")) return false
        if (lower.contains("i don't have any specific context")) return false
        if (lower.contains("i don't have enough information")) return false
        if (lower.contains("random lines of text")) return false
        if (lower.contains("cannot generate a summary")) return false
        if (lower.contains("please provide more details")) return false
        if (lower.contains("can't provide")) return false
        if (lower.contains("unable to provide")) return false
        if (lower.contains("don't have enough context")) return false
        if (lower.contains("not enough information")) return false
        if (lower.contains("provide more details")) return false
        if (lower.contains("what specific content")) return false
        if (lower.contains("what you're looking for")) return false
        if (lower.contains("clarify what you need")) return false
        if (lower.contains("mix of different apps")) return false
        return true
    }

    private fun shouldRetryWithCompactPrompt(error: Throwable): Boolean {
        val msg = error.message?.lowercase().orEmpty()
        return msg.contains("timed out") ||
            msg.contains("timeout") ||
            msg.contains("cloud ai unavailable") ||
            msg.contains("http 5") ||
            msg.contains("unusable response") ||
            msg.contains("couldn't generate")
    }
}
