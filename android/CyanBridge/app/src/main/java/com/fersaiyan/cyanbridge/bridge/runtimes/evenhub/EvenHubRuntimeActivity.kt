package com.achyut.adglasses.bridge.runtimes.evenhub

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.achyut.adglasses.bridge.core.DisplayCommand
import com.achyut.adglasses.bridge.core.GlassesBridge
import com.achyut.adglasses.ui.appearance.AppearancePreferences
import com.achyut.adglasses.ui.appearance.rememberAppearanceSettings
import com.achyut.adglasses.ui.theme.CyanBridgeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

/** Compose shell around the Android-only WebView and EvenHub JavaScript bridge. */
class EvenHubRuntimeActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "EvenHubRuntime"
        private const val DEFAULT_URL = "http://10.0.2.2:5173"
        private const val SHIM_ASSET_PATH = "evenhub-compat-shim.js"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var webView: WebView
    private var isLoaded = false
    private var currentUrl = DEFAULT_URL
    private var urlInput by mutableStateOf(DEFAULT_URL)
    private var runtimeLog by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appearancePreferences = AppearancePreferences(this)
        setContent {
            val appearance by rememberAppearanceSettings(appearancePreferences)
            CyanBridgeTheme(appearance) {
                EvenHubRuntimeScreen(
                    url = urlInput,
                    logs = runtimeLog,
                    onUrlChange = { urlInput = it },
                    onLoad = { loadUrl(urlInput) },
                    onStop = ::shutdown,
                    onWebViewCreated = ::configureWebView,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) webView.onResume()
    }

    override fun onPause() {
        if (::webView.isInitialized) webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && ::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(createdWebView: WebView) {
        webView = createdWebView
        val jsBridge = createJsBridge()
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
                injectShim(view)
            }
        }
    }

    private fun createJsBridge(): EvenHubJsBridge = EvenHubJsBridge(
        onDisplayText = { text ->
            log("Display text: ${text.take(80)}")
            scope.launch { GlassesBridge.showText(DisplayCommand.Text(text = text)) }
        },
        onDisplayLines = { lines, page, totalPages ->
            log("Display lines: ${lines.size} items")
            scope.launch { GlassesBridge.showLines(DisplayCommand.Lines(lines, page, totalPages)) }
        },
        onDisplayCard = { title, body ->
            log("Display card: $title")
            scope.launch { GlassesBridge.showCard(DisplayCommand.Card(title, body)) }
        },
        onClearDisplay = {
            log("Clear display")
            scope.launch { GlassesBridge.clearDisplay() }
        },
        onExit = {
            log("Exit requested")
            finish()
        },
        onLog = ::log,
    )

    private fun loadUrl(url: String) {
        if (!::webView.isInitialized) return
        val fullUrl = if (url.startsWith("http://") || url.startsWith("https://")) url else "http://$url"
        currentUrl = fullUrl
        isLoaded = true
        webView.loadUrl(fullUrl)
    }

    private fun shutdown() {
        if (::webView.isInitialized && isLoaded) {
            webView.evaluateJavascript(
                "if (window._evenAppBridgeInstance) { window._evenAppBridgeInstance.shutDownPageContainer(0); }",
                null,
            )
        }
        finish()
    }

    private fun injectShim(view: WebView?) {
        val target = view ?: return
        runCatching { target.evaluateJavascript(readAsset(SHIM_ASSET_PATH), null) }
            .onSuccess { Log.d(TAG, "Shim injected") }
            .onFailure { Log.e(TAG, "Failed to inject shim", it) }
    }

    private fun log(message: String) {
        runOnUiThread {
            runtimeLog = buildString {
                if (runtimeLog.isNotBlank()) append(runtimeLog).append('\n')
                append("[$TAG] ").append(message)
            }.takeLast(16_000)
        }
    }

    private fun readAsset(path: String): String = assets.open(path).use { inputStream ->
        BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
    }
}
