package com.fersaiyan.cyanbridge.ota

import android.content.Context
import android.util.Log
import com.fersaiyan.cyanbridge.agent.ProSubscriptionServerPrefs
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Result of a firmware catalog lookup + download.
 */
sealed class FirmwareResult {
    data class Ready(val file: File, val filename: String) : FirmwareResult()
    data class NotAvailable(val message: String, val wifiHwVersion: String) : FirmwareResult()
    data class SubscriptionRequired(
        val message: String,
        val currentPlan: String,
        val requiredPlans: List<String>,
    ) : FirmwareResult()

    data class Error(val message: String) : FirmwareResult()
}

/**
 * Client for the CyanBridge server's firmware download API.
 *
 * Calls GET /api/firmware/download with the paired glasses info,
 * checks subscription tier, and downloads the gated SWU file.
 */
class FirmwareClient(
    private val context: Context,
) {
    /**
     * Fetch firmware info from the server and download the SWU file.
     *
     * @param wifiHardwareVersion The glasses' Wi-Fi hardware version (e.g. "WIFIAM01G1_V9.2")
     * @param wifiFirmwareVersion The glasses' current Wi-Fi firmware version
     * @param outputDir Directory to save the downloaded file
     * @return FirmwareResult indicating success, failure, or subscription gate
     */
    fun fetchAndDownload(
        wifiHardwareVersion: String,
        wifiFirmwareVersion: String,
        outputDir: File,
    ): FirmwareResult {
        val startTime = System.currentTimeMillis()
        Log.i(TAG, "=== FIRMWARE FETCH START ===")
        Log.i(TAG, "  wifiHardwareVersion: $wifiHardwareVersion")
        Log.i(TAG, "  wifiFirmwareVersion: $wifiFirmwareVersion")
        Log.i(TAG, "  outputDir: ${outputDir.absolutePath}")

        // Step 1: Resolve relay base URL
        val relayBase = getRelayBaseUrl()
        Log.i(TAG, "[1/5] Relay base URL: $relayBase")

        // Step 2: Get API token
        val apiToken = ProSubscriptionServerPrefs.getApiToken(context)
        val accountEmail = ProSubscriptionServerPrefs.getAccountEmail(context)
        if (apiToken.isBlank()) {
            Log.e(TAG, "[2/5] FAIL: No API token available. User not signed in.")
            return FirmwareResult.Error("No API token available. Please sign in to your CyanBridge account.")
        }
        Log.i(TAG, "[2/5] API token present: ${apiToken.take(12)}... (email: ${accountEmail.ifBlank { "(none)" }})")

        // Step 3: Call the firmware download endpoint
        val apiUrl = "$relayBase/api/firmware/download" +
            "?wifiHardwareVersion=$wifiHardwareVersion" +
            "&wifiFirmwareVersion=$wifiFirmwareVersion"

        Log.i(TAG, "[3/5] Calling firmware API: $apiUrl")

        val response = try {
            httpGet(apiUrl, apiToken)
        } catch (e: Exception) {
            Log.e(TAG, "[3/5] FAIL: Firmware API request threw exception: ${e.javaClass.simpleName}: ${e.message}", e)
            return FirmwareResult.Error("Server request failed: ${e.message}")
        }

        if (response == null) {
            Log.e(TAG, "[3/5] FAIL: Firmware API returned null response")
            return FirmwareResult.Error("Empty response from server")
        }

        val statusCode = response.statusCode
        val body = response.body
        Log.i(TAG, "[3/5] Firmware API response: HTTP $statusCode (${body.length} bytes)")
        if (statusCode != 200) {
            Log.w(TAG, "[3/5] Non-200 response body: ${body.take(500)}")
        }

        // Step 4: Parse response based on status code
        return when (statusCode) {
            200 -> {
                val json = JSONObject(body)
                val downloadUrl = json.getString("download_url")
                val filename = json.getString("filename")
                val objectKey = json.optString("object_key", "")
                val expiresIn = json.optInt("expires_in_seconds", 0)

                Log.i(TAG, "[4/5] Firmware available!")
                Log.i(TAG, "  filename: $filename")
                Log.i(TAG, "  object_key: $objectKey")
                Log.i(TAG, "  signed_url_expires_in: ${expiresIn}s")
                Log.i(TAG, "  download_url: ${downloadUrl.take(80)}...")

                // Step 5: Download the SWU file
                Log.i(TAG, "[5/5] Starting SWU download...")
                val file = downloadSwu(downloadUrl, filename, outputDir)

                if (file != null) {
                    val elapsed = System.currentTimeMillis() - startTime
                    Log.i(TAG, "=== FIRMWARE FETCH SUCCESS === (${elapsed}ms)")
                    Log.i(TAG, "  file: ${file.absolutePath}")
                    Log.i(TAG, "  size: ${file.length()} bytes (${file.length() / 1024 / 1024} MB)")
                    FirmwareResult.Ready(file, filename)
                } else {
                    Log.e(TAG, "=== FIRMWARE FETCH FAIL === SWU download returned null")
                    FirmwareResult.Error("Failed to download firmware file from storage")
                }
            }

            403 -> {
                val json = JSONObject(body)
                val error = json.optString("error", "")
                val message = json.optString("message", "Access denied")

                Log.w(TAG, "[4/5] Subscription gate: error=$error, message=$message")

                when (error) {
                    "subscription_required" -> {
                        val currentPlan = json.optString("currentPlan", "unknown")
                        val requiredPlans = mutableListOf<String>()
                        val arr = json.optJSONArray("requiredPlans")
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                requiredPlans.add(arr.getString(i))
                            }
                        }
                        Log.w(TAG, "  currentPlan: $currentPlan, requiredPlans: $requiredPlans")
                        Log.i(TAG, "=== FIRMWARE FETCH BLOCKED (subscription) ===")
                        FirmwareResult.SubscriptionRequired(message, currentPlan, requiredPlans)
                    }

                    "subscription_expired" -> {
                        Log.w(TAG, "  Subscription expired")
                        Log.i(TAG, "=== FIRMWARE FETCH BLOCKED (expired) ===")
                        FirmwareResult.SubscriptionRequired(message, "expired", listOf("standard", "max"))
                    }

                    "rate_limited" -> {
                        val retryAfterMs = json.optLong("retryAfterMs", 0)
                        Log.w(TAG, "  Rate limited! Retry after ${retryAfterMs}ms")
                        Log.i(TAG, "=== FIRMWARE FETCH BLOCKED (rate limit) ===")
                        FirmwareResult.Error("Too many requests. Please try again in ${retryAfterMs / 1000 / 2} minutes.")
                    }

                    else -> {
                        Log.e(TAG, "=== FIRMWARE FETCH FAIL (403 unknown) ===")
                        FirmwareResult.Error(message)
                    }
                }
            }

            404 -> {
                val json = JSONObject(body)
                val message = json.optString("message", "Firmware not available for this glasses model")
                Log.w(TAG, "[4/5] No firmware for this model: $message")
                Log.i(TAG, "=== FIRMWARE FETCH NOT AVAILABLE ===")
                FirmwareResult.NotAvailable(message, wifiHardwareVersion)
            }

            401 -> {
                val json = JSONObject(body)
                val message = json.optString("message", "Authentication failed")
                Log.e(TAG, "[4/5] Auth failed: $message")
                Log.i(TAG, "=== FIRMWARE FETCH FAIL (auth) ===")
                FirmwareResult.Error("Auth failed: $message")
            }

            429 -> {
                val json = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
                val retryAfter = json.optLong("retryAfterMs", 60_000)
                Log.e(TAG, "[4/5] Rate limited by server. Retry after ${retryAfter}ms")
                Log.i(TAG, "=== FIRMWARE FETCH FAIL (rate limit) ===")
                FirmwareResult.Error("Too many requests. Please wait a moment and try again.")
            }

            else -> {
                Log.e(TAG, "[4/5] Unexpected response: $statusCode — ${body.take(500)}")
                Log.i(TAG, "=== FIRMWARE FETCH FAIL (HTTP $statusCode) ===")
                FirmwareResult.Error("Server error ($statusCode)")
            }
        }
    }

    private fun downloadSwu(downloadUrl: String, filename: String, outputDir: File): File? {
        val downloadStart = System.currentTimeMillis()
        return try {
            if (!outputDir.exists()) {
                outputDir.mkdirs()
                Log.d(TAG, "Created output directory: ${outputDir.absolutePath}")
            }
            val outFile = File(outputDir, filename)

            // Delete any existing partial file
            if (outFile.exists()) {
                Log.d(TAG, "Deleting existing file: ${outFile.absolutePath} (${outFile.length()} bytes)")
                outFile.delete()
            }

            val url = URL(downloadUrl)
            Log.d(TAG, "Opening HTTP connection to Supabase Storage...")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 30_000
            conn.readTimeout = 300_000 // 5 min for large files
            conn.instanceFollowRedirects = true

            Log.d(TAG, "Waiting for response...")
            val responseCode = conn.responseCode
            Log.d(TAG, "Response code: $responseCode")

            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "SWU download failed: HTTP $responseCode")
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "(no body)"
                Log.e(TAG, "Error body: ${errorBody.take(300)}")
                conn.disconnect()
                return null
            }

            val contentLength = conn.contentLengthLong
            val contentType = conn.contentType
            Log.i(TAG, "Content-Length: $contentLength bytes, Content-Type: $contentType")

            val buf = ByteArray(8192)
            var totalRead = 0L
            var lastLogAt = 0L

            conn.inputStream.use { input ->
                FileOutputStream(outFile).use { output ->
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        output.write(buf, 0, read)
                        totalRead += read

                        // Log progress every 2 seconds
                        val now = System.currentTimeMillis()
                        if (now - lastLogAt > 2000) {
                            val pct = if (contentLength > 0) (totalRead * 100 / contentLength) else -1
                            val elapsedSec = (now - downloadStart) / 1000.0
                            val speedKbps = if (elapsedSec > 0) (totalRead / 1024.0 / elapsedSec) else 0.0
                            Log.d(TAG, "Download progress: $totalRead / $contentLength bytes ($pct%) — ${speedKbps.toInt()} KB/s")
                            lastLogAt = now
                        }
                    }
                    output.flush()
                }
            }
            conn.disconnect()

            val elapsed = System.currentTimeMillis() - downloadStart
            val avgSpeed = if (elapsed > 0) (totalRead / 1024.0 / (elapsed / 1000.0)) else 0.0
            Log.i(TAG, "SWU download complete: $totalRead bytes in ${elapsed}ms (${avgSpeed.toInt()} KB/s avg)")
            Log.i(TAG, "Saved to: ${outFile.absolutePath}")

            // Verify file size matches Content-Length
            if (contentLength > 0 && outFile.length() != contentLength.toLong()) {
                Log.w(TAG, "File size mismatch! Expected $contentLength, got ${outFile.length()}")
            }

            outFile
        } catch (e: Exception) {
            Log.e(TAG, "SWU download exception: ${e.javaClass.simpleName}: ${e.message}", e)
            null
        }
    }

    private fun getRelayBaseUrl(): String {
        val prefs = context.getSharedPreferences("relay_server", Context.MODE_PRIVATE)
        val url = prefs.getString("base_url", null) ?: "https://cyanbridge.vercel.app"
        Log.d(TAG, "Relay base URL resolved: $url")
        return url
    }

    private data class HttpResponse(val statusCode: Int, val body: String)

    private fun httpGet(url: String, apiToken: String): HttpResponse? {
        Log.d(TAG, "HTTP GET: $url")
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer ${apiToken.take(12)}...")
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000

            val statusCode = conn.responseCode
            Log.d(TAG, "HTTP response: $statusCode")

            val stream = if (statusCode in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            Log.d(TAG, "Response body length: ${body.length} bytes")

            HttpResponse(statusCode, body)
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TAG = "FirmwareClient"
    }
}
