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
        val relayBase = getRelayBaseUrl()
        val apiToken = ProSubscriptionServerPrefs.getApiToken(context)

        if (apiToken.isBlank()) {
            return FirmwareResult.Error("No API token available. Please sign in to your CyanBridge account.")
        }

        // Step 1: Call the firmware download endpoint
        val apiUrl = "$relayBase/api/firmware/download" +
            "?wifiHardwareVersion=$wifiHardwareVersion" +
            "&wifiFirmwareVersion=$wifiFirmwareVersion"

        Log.i(TAG, "Requesting firmware: $apiUrl")

        val response = try {
            httpGet(apiUrl, apiToken)
        } catch (e: Exception) {
            Log.e(TAG, "Firmware API request failed: ${e.message}", e)
            return FirmwareResult.Error("Server request failed: ${e.message}")
        }

        if (response == null) {
            return FirmwareResult.Error("Empty response from server")
        }

        // Step 2: Parse response
        val statusCode = response.statusCode
        val body = response.body

        Log.i(TAG, "Firmware API response: HTTP $statusCode")

        return when (statusCode) {
            200 -> {
                // Success — download the file
                val json = JSONObject(body)
                val downloadUrl = json.getString("download_url")
                val filename = json.getString("filename")

                Log.i(TAG, "Firmware available: $filename, downloading from signed URL...")
                val file = downloadSwu(downloadUrl, filename, outputDir)

                if (file != null) {
                    FirmwareResult.Ready(file, filename)
                } else {
                    FirmwareResult.Error("Failed to download firmware file")
                }
            }

            403 -> {
                // Subscription required or expired
                val json = JSONObject(body)
                val error = json.optString("error", "")
                val message = json.optString("message", "Access denied")

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
                        FirmwareResult.SubscriptionRequired(message, currentPlan, requiredPlans)
                    }

                    "subscription_expired" -> {
                        FirmwareResult.SubscriptionRequired(message, "expired", listOf("standard", "max"))
                    }

                    else -> FirmwareResult.Error(message)
                }
            }

            404 -> {
                // Firmware not available for this model
                val json = JSONObject(body)
                val message = json.optString("message", "Firmware not available for this glasses model")
                FirmwareResult.NotAvailable(message, wifiHardwareVersion)
            }

            401 -> {
                val json = JSONObject(body)
                val message = json.optString("message", "Authentication failed")
                FirmwareResult.Error("Auth failed: $message")
            }

            else -> {
                Log.e(TAG, "Unexpected firmware API response: $statusCode — $body")
                FirmwareResult.Error("Server error ($statusCode)")
            }
        }
    }

    private fun downloadSwu(downloadUrl: String, filename: String, outputDir: File): File? {
        return try {
            if (!outputDir.exists()) outputDir.mkdirs()
            val outFile = File(outputDir, filename)

            val url = URL(downloadUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 30_000
            conn.readTimeout = 300_000 // 5 min for large files
            conn.instanceFollowRedirects = true

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "SWU download failed: HTTP ${conn.responseCode}")
                conn.disconnect()
                return null
            }

            val contentLength = conn.contentLengthLong
            Log.i(TAG, "Downloading SWU: $filename ($contentLength bytes)")

            conn.inputStream.use { input ->
                FileOutputStream(outFile).use { output ->
                    val buf = ByteArray(8192)
                    var totalRead = 0L
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        output.write(buf, 0, read)
                        totalRead += read
                    }
                    output.flush()
                    Log.i(TAG, "SWU download complete: $totalRead bytes written to ${outFile.absolutePath}")
                }
            }
            conn.disconnect()
            outFile
        } catch (e: Exception) {
            Log.e(TAG, "SWU download exception: ${e.message}", e)
            null
        }
    }

    private fun getRelayBaseUrl(): String {
        // Match the pattern used by ProSubscriptionRelayClient
        val prefs = context.getSharedPreferences("relay_server", Context.MODE_PRIVATE)
        return prefs.getString("base_url", null)
            ?: "https://cyanbridge.vercel.app"
    }

    private data class HttpResponse(val statusCode: Int, val body: String)

    private fun httpGet(url: String, apiToken: String): HttpResponse? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $apiToken")
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000

            val statusCode = conn.responseCode
            val stream = if (statusCode in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            HttpResponse(statusCode, body)
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TAG = "FirmwareClient"
    }
}
