package com.fersaiyan.cyanbridge.bridge.runtimes.evenhub

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.KeyEvent
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fersaiyan.cyanbridge.R
import com.fersaiyan.cyanbridge.bridge.core.DisplayCommand
import com.fersaiyan.cyanbridge.bridge.core.GlassesBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Activity that loads an EvenHub app (TypeScript/Vite web app) in a WebView
 * and routes display commands to [GlassesBridge] via [EvenHubJsBridge].
 *
 * ## Usage
 * 1. Enter the EvenHub app URL (default: `http://10.0.2.2:5173` for emulator).
 * 2. Tap **Load** to load the URL in the WebView.
 * 3. The shim JS intercepts EvenAppBridge SDK calls and forwards them to Android.
 * 4. Tap **Stop** to shut down the EvenHub app and finish the activity.
 *
 * ## Layout
 * - [R.id.evenhub_url_input] — EditText for the URL
 * - [R.id.evenhub_load_button] — Button to load the URL
 * - [R.id.evenhub_stop_button] — Button to stop and finish
 * - [R.id.evenhub_webview] — WebView that renders the EvenHub app
 * - [R.id.evenhub_log] — TextView showing bridge log messages
 */
class EvenHubRuntimeActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "EvenHubRuntime"
        private const val DEFAULT_URL = "http://10.0.2.2:5173"
        private const val SHIM_ASSET_PATH = "evenhub-compat-shim.js"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // UI
    private lateinit var urlInput: EditText
    private lateinit var loadButton: Button
    private lateinit var stopButton: Button
    private lateinit var webView: WebView
    private lateinit var logView: TextView

    // State
    private var isLoaded = false
    private var currentUrl: String = DEFAULT_URL

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_evenhub_runtime)

        // Bind UI
        urlInput = findViewById(R.id.evenhub_url_input)
        loadButton = findViewById(R.id.evenhub_load_button)
        stopButton = findViewById(R.id.evenhub_stop_button)
        webView = findViewById(R.id.evenhub_webview)
        logView = findViewById(R.id.evenhub_log)

        logView.movementMethod = ScrollingMovementMethod()

        // Set default URL
        urlInput.setText(DEFAULT_URL)

        // Create the JS bridge
        val jsBridge = createJsBridge()

        // Configure WebView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        // Register the JS bridge
        webView.addJavascriptInterface(jsBridge, "CyanBridgeEvenHubBridge")

        webView.webChromeClient = WebChromeClient()

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                currentUrl = url ?: currentUrl
                log("Loading: $url")
                injectShim(view)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                log("Page loaded: $url")
                // Re-inject shim on page finish to catch any late-loading SDK
                view?.let { injectShim(it) }
            }
        }

        // Load button
        loadButton.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isNotEmpty()) {
                currentUrl = url
                loadUrl(url)
            }
        }

        // Stop button
        stopButton.setOnClickListener {
            shutdown()
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Handle back navigation in the WebView history
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // ------------------------------------------------------------------
    // Shim injection
    // ------------------------------------------------------------------

    /**
     * Inject the EvenHub compatibility shim into the WebView.
     * This must run before the SDK tries to register its own bridge,
     * and again after page load to ensure it's active.
     */
    private fun injectShim(view: WebView?) {
        val webView = view ?: return
        try {
            val shimJs = readAsset(SHIM_ASSET_PATH)
            webView.evaluateJavascript(shimJs, null)
            Log.d(TAG, "Shim injected")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject shim", e)
        }
    }

    // ------------------------------------------------------------------
    // JS bridge factory
    // ------------------------------------------------------------------

    private fun createJsBridge(): EvenHubJsBridge {
        return EvenHubJsBridge(
            onDisplayText = { text ->
                log("Display text: ${text.take(80)}")
                scope.launch {
                    GlassesBridge.showText(DisplayCommand.Text(text = text))
                }
            },
            onDisplayLines = { lines, page, totalPages ->
                log("Display lines: ${lines.size} items")
                scope.launch {
                    GlassesBridge.showLines(
                        DisplayCommand.Lines(lines = lines, page = page, totalPages = totalPages)
                    )
                }
            },
            onDisplayCard = { title, body ->
                log("Display card: $title")
                scope.launch {
                    GlassesBridge.showCard(DisplayCommand.Card(title = title, body = body))
                }
            },
            onClearDisplay = {
                log("Clear display")
                scope.launch {
                    GlassesBridge.clearDisplay()
                }
            },
            onExit = {
                log("Exit requested")
                finish()
            },
            onLog = { msg ->
                log(msg)
            }
        )
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun loadUrl(url: String) {
        val fullUrl = if (url.startsWith("http://") || url.startsWith("https://")) url else "http://$url"
        isLoaded = true
        webView.loadUrl(fullUrl)
    }

    private fun shutdown() {
        // Evaluate shutDown in JS if the page is loaded
        if (isLoaded) {
            webView.evaluateJavascript(
                "if (window._evenAppBridgeInstance) { window._evenAppBridgeInstance.shutDownPageContainer(0); }",
                null
            )
        }
        finish()
    }

    private fun log(message: String) {
        runOnUiThread {
            logView.append("[$TAG] $message\n")
            // Auto-scroll to bottom
            val layout = logView.layout ?: return@runOnUiThread
            val scrollAmount = layout.getLineTop(logView.lineCount) - logView.height
            if (scrollAmount > 0) {
                logView.scrollTo(0, scrollAmount)
            }
        }
    }

    private fun readAsset(path: String): String {
        return assets.open(path).use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readText()
            }
        }
    }
}
