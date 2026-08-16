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
    MODES,
    LIBRARY_CAPTURES,
    LIBRARY_RECORDINGS,
    LIBRARY_NOTES,
}

data class ADNavigationRequest(
    val id: Long,
    val destination: ADExternalDestination,
    val prefill: String? = null,
    val threadId: String? = null,
)

/**
 * Small bridge between inherited explicit Activity intents and the native AD navigation
 * shell. Requests are persisted before MainActivity is opened so process-start redirects
 * cannot be lost between the compatibility alias and Compose initialization.
 */
object ADNavigationRequestStore {
    private const val PREFS = "ad_navigation_requests"
    private const val KEY_ID = "id"
    private const val KEY_DESTINATION = "destination"
    private const val KEY_PREFILL = "prefill"
    private const val KEY_THREAD_ID = "thread_id"

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
    ): ADNavigationRequest {
        val request = ADNavigationRequest(
            id = ids.updateAndGet { previous ->
                maxOf(previous + 1L, System.currentTimeMillis())
            },
            destination = destination,
            prefill = prefill?.trim()?.takeIf { it.isNotEmpty() },
            threadId = threadId?.trim()?.takeIf { it.isNotEmpty() },
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
            .apply {
                if (request.prefill == null) remove(KEY_PREFILL) else putString(KEY_PREFILL, request.prefill)
                if (request.threadId == null) remove(KEY_THREAD_ID) else putString(KEY_THREAD_ID, request.threadId)
            }
            // Synchronous on purpose: MainActivity may start immediately after this write.
            .commit()
    }

    private fun readPersisted(context: Context): ADNavigationRequest? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = prefs.getLong(KEY_ID, 0L)
        val destination = prefs.getString(KEY_DESTINATION, null)
            ?.let { raw -> runCatching { ADExternalDestination.valueOf(raw) }.getOrNull() }
        if (id <= 0L || destination == null) return null
        return ADNavigationRequest(
            id = id,
            destination = destination,
            prefill = prefs.getString(KEY_PREFILL, null),
            threadId = prefs.getString(KEY_THREAD_ID, null),
        )
    }
}

/** Invisible compatibility target; it never renders a legacy product page. */
abstract class ADLegacyRouteRedirectActivity : Activity() {
    abstract val destination: ADExternalDestination

    protected open fun requestPrefill(intent: Intent): String? = null
    protected open fun requestThreadId(intent: Intent): String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ADNavigationRequestStore.post(
            context = this,
            destination = destination,
            prefill = requestPrefill(intent),
            threadId = requestThreadId(intent),
        )
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
        )
        finish()
        overridePendingTransition(0, 0)
    }
}

class ADConversationsRedirectActivity : ADLegacyRouteRedirectActivity() {
    override val destination = ADExternalDestination.CONVERSATIONS
    override fun requestPrefill(intent: Intent): String? = intent.getStringExtra("prefill_message")
    override fun requestThreadId(intent: Intent): String? = intent.getStringExtra("chat_id")
}

class ADSettingsRedirectActivity : ADLegacyRouteRedirectActivity() {
    override val destination = ADExternalDestination.SETTINGS
}

class ADModesRedirectActivity : ADLegacyRouteRedirectActivity() {
    override val destination = ADExternalDestination.MODES
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
