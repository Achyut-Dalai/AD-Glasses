package com.fersaiyan.cyanbridge.ui.adglasses

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.fersaiyan.cyanbridge.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

enum class ADExternalDestination {
    CONVERSATIONS,
    SETTINGS,
    AI,
    LIBRARY_CAPTURES,
    LIBRARY_RECORDINGS,
    LIBRARY_NOTES,
}

data class ADNavigationRequest(
    val id: Long,
    val destination: ADExternalDestination,
    val prefill: String? = null,
    val threadId: String? = null,
    val webSearchRequested: Boolean = false,
)

/** Compatibility store for native/runtime callers that post Compose navigation requests. */
object ADNavigationRequestStore {
    private const val PREFS = "ad_navigation_requests"
    private const val KEY_ID = "id"
    private const val KEY_DESTINATION = "destination"
    private const val KEY_PREFILL = "prefill"
    private const val KEY_THREAD_ID = "thread_id"
    private const val KEY_WEB_SEARCH = "web_search"

    private val ids = AtomicLong(System.currentTimeMillis())
    private val mutableRequest = MutableStateFlow<ADNavigationRequest?>(null)
    private var loaded = false

    @Synchronized
    fun observe(context: Context): StateFlow<ADNavigationRequest?> {
        if (!loaded) {
            mutableRequest.value = readPersisted(context.applicationContext)
            loaded = true
        }
        return mutableRequest.asStateFlow()
    }

    @Synchronized
    fun post(
        context: Context,
        destination: ADExternalDestination,
        prefill: String? = null,
        threadId: String? = null,
        webSearchRequested: Boolean = false,
    ): ADNavigationRequest {
        val request = ADNavigationRequest(
            id = ids.updateAndGet { previous -> maxOf(previous + 1L, System.currentTimeMillis()) },
            destination = destination,
            prefill = prefill?.trim()?.takeIf { it.isNotEmpty() },
            threadId = threadId?.trim()?.takeIf { it.isNotEmpty() },
            webSearchRequested = webSearchRequested,
        )
        persist(context.applicationContext, request)
        loaded = true
        mutableRequest.value = request
        return request
    }

    @Synchronized
    fun consume(context: Context, requestId: Long) {
        val current = mutableRequest.value ?: readPersisted(context.applicationContext)
        if (current?.id != requestId) return
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        mutableRequest.value = null
        loaded = true
    }

    private fun persist(context: Context, request: ADNavigationRequest) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_ID, request.id)
            .putString(KEY_DESTINATION, request.destination.name)
            .putBoolean(KEY_WEB_SEARCH, request.webSearchRequested)
            .apply {
                if (request.prefill == null) remove(KEY_PREFILL) else putString(KEY_PREFILL, request.prefill)
                if (request.threadId == null) remove(KEY_THREAD_ID) else putString(KEY_THREAD_ID, request.threadId)
            }
            .commit()
    }

    private fun readPersisted(context: Context): ADNavigationRequest? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = prefs.getLong(KEY_ID, 0L)
        val destination = prefs.getString(KEY_DESTINATION, null)?.let { raw ->
            when (raw) {
                // Migrate an in-flight request written by older AD builds.
                "MODES" -> ADExternalDestination.AI
                else -> runCatching { ADExternalDestination.valueOf(raw) }.getOrNull()
            }
        }
        if (id <= 0L || destination == null) return null
        return ADNavigationRequest(
            id = id,
            destination = destination,
            prefill = prefs.getString(KEY_PREFILL, null),
            threadId = prefs.getString(KEY_THREAD_ID, null),
            webSearchRequested = prefs.getBoolean(KEY_WEB_SEARCH, false),
        )
    }
}

/** Invisible compatibility target; inherited native intents always land in the Compose shell. */
abstract class ADLegacyRouteRedirectActivity : Activity() {
    abstract val destination: ADExternalDestination

    protected open fun requestPrefill(intent: Intent): String? = null
    protected open fun requestThreadId(intent: Intent): String? = null
    protected open fun requestWebSearch(intent: Intent): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ADNavigationRequestStore.post(
            context = this,
            destination = destination,
            prefill = requestPrefill(intent),
            threadId = requestThreadId(intent),
            webSearchRequested = requestWebSearch(intent),
        )
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
        )
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}

class ADConversationsRedirectActivity : ADLegacyRouteRedirectActivity() {
    override val destination = ADExternalDestination.CONVERSATIONS
    override fun requestPrefill(intent: Intent): String? = intent.getStringExtra("prefill_message")
    override fun requestThreadId(intent: Intent): String? = intent.getStringExtra("chat_id")
    override fun requestWebSearch(intent: Intent): Boolean = intent.getBooleanExtra("web_search_requested", false)
}

class ADSettingsRedirectActivity : ADLegacyRouteRedirectActivity() {
    override val destination = ADExternalDestination.SETTINGS
}

class ADAiRedirectActivity : ADLegacyRouteRedirectActivity() {
    override val destination = ADExternalDestination.AI
}

class ADCapturesRedirectActivity : ADLegacyRouteRedirectActivity() {
    override val destination = ADExternalDestination.LIBRARY_CAPTURES
}

class ADRecordingsRedirectActivity : ADLegacyRouteRedirectActivity() {
    override val destination = ADExternalDestination.LIBRARY_RECORDINGS
}

class ADNotesRedirectActivity : ADLegacyRouteRedirectActivity() {
    override val destination = ADExternalDestination.LIBRARY_NOTES
}
