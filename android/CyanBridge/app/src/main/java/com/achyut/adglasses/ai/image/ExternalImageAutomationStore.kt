package com.achyut.adglasses.ai.image

import android.content.Context
import java.util.UUID

data class ExternalImageAutomationSession(
    val sessionId: String,
    val callbackToken: String,
    val imagePath: String,
    val imageUri: String,
    val question: String,
    val source: ImageQuestionSource,
    val state: ExternalImageAutomationState,
    val followUpPromptShown: Boolean,
)

object ExternalImageAutomationIntents {
    const val TASKER_PACKAGE = "net.dinglisch.android.taskerm"
    const val AUTO_INPUT_PACKAGE = "com.joaomgcd.autoinput"
    const val GEMINI_PACKAGE = "com.google.android.googlequicksearchbox"
    const val GEMINI_ALTERNATE_PACKAGE = "com.google.android.apps.bard"
    const val CHATGPT_PACKAGE = "com.openai.chatgpt"

    const val EXTRA_STATUS = "status"
    const val EXTRA_ERROR = "error"
    const val EXTRA_PROFILE_TARGET = "profile_target"
    const val EXTRA_PROFILE_VERSION = "profile_version"
    const val EXTRA_PROFILE_TOKEN = "profile_token"

    fun statusAction(packageName: String): String = "$packageName.AI_IMAGE_STATUS"
    fun profileAction(packageName: String): String = "$packageName.AI_IMAGE_PROFILE"
    fun internalStatusAction(packageName: String): String = "$packageName.AI_IMAGE_STATUS_UPDATED"
}

/** Persists enough state to diagnose callbacks after Gemini has taken the foreground. */
object ExternalImageAutomationStore {
    private const val PREFS = "external_image_automation"
    private const val KEY_SESSION = "session"
    private const val KEY_TOKEN = "token"
    private const val KEY_PATH = "path"
    private const val KEY_URI = "uri"
    private const val KEY_QUESTION = "question"
    private const val KEY_SOURCE = "source"
    private const val KEY_STAGE = "stage"
    private const val KEY_ERROR = "error"
    private const val KEY_FOLLOW_UP_PROMPT_SHOWN = "follow_up_prompt_shown"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun begin(
        context: Context,
        imagePath: String,
        imageUri: String,
        question: String,
        source: ImageQuestionSource,
    ): ExternalImageAutomationSession {
        val session = ExternalImageAutomationSession(
            sessionId = UUID.randomUUID().toString(),
            callbackToken = UUID.randomUUID().toString(),
            imagePath = imagePath,
            imageUri = imageUri,
            question = question,
            source = source,
            state = ExternalImageAutomationState(ExternalImageAutomationStage.IMAGE_STARTED),
            followUpPromptShown = false,
        )
        write(context, session)
        return session
    }

    fun current(context: Context): ExternalImageAutomationSession? {
        val values = prefs(context)
        val sessionId = values.getString(KEY_SESSION, null) ?: return null
        val token = values.getString(KEY_TOKEN, null) ?: return null
        val path = values.getString(KEY_PATH, null) ?: return null
        val uri = values.getString(KEY_URI, null) ?: return null
        val source = ImageQuestionSource.entries.firstOrNull {
            it.wireName == values.getString(KEY_SOURCE, null)
        } ?: return null
        val stage = ExternalImageAutomationStage.fromWireName(values.getString(KEY_STAGE, null))
            ?: ExternalImageAutomationStage.IDLE
        return ExternalImageAutomationSession(
            sessionId = sessionId,
            callbackToken = token,
            imagePath = path,
            imageUri = uri,
            question = values.getString(KEY_QUESTION, "").orEmpty(),
            source = source,
            state = ExternalImageAutomationState(stage, values.getString(KEY_ERROR, null)),
            followUpPromptShown = values.getBoolean(KEY_FOLLOW_UP_PROMPT_SHOWN, false),
        )
    }

    fun acceptsCallback(context: Context, sessionId: String?, callbackToken: String?): Boolean {
        val current = current(context) ?: return false
        return current.sessionId == sessionId && current.callbackToken == callbackToken
    }

    fun recordCallback(
        context: Context,
        stage: ExternalImageAutomationStage,
        error: String?,
    ): ExternalImageAutomationSession? {
        val current = current(context) ?: return null
        val nextState = ExternalImageAutomationStateMachine.transition(current.state, stage, error)
        if (nextState == current.state) return current
        return current.copy(state = nextState).also { write(context, it) }
    }

    fun recordLocalStage(
        context: Context,
        stage: ExternalImageAutomationStage,
        error: String? = null,
    ): ExternalImageAutomationSession? = recordCallback(context, stage, error)

    fun markFollowUpPromptShown(context: Context) {
        val current = current(context) ?: return
        if (!current.followUpPromptShown) write(context, current.copy(followUpPromptShown = true))
    }

    private fun write(context: Context, session: ExternalImageAutomationSession) {
        prefs(context).edit()
            .putString(KEY_SESSION, session.sessionId)
            .putString(KEY_TOKEN, session.callbackToken)
            .putString(KEY_PATH, session.imagePath)
            .putString(KEY_URI, session.imageUri)
            .putString(KEY_QUESTION, session.question)
            .putString(KEY_SOURCE, session.source.wireName)
            .putString(KEY_STAGE, session.state.stage.wireName)
            .putString(KEY_ERROR, session.state.error)
            .putBoolean(KEY_FOLLOW_UP_PROMPT_SHOWN, session.followUpPromptShown)
            .apply()
    }
}
