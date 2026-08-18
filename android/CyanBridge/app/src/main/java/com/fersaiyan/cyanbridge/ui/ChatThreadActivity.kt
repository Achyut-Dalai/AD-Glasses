package com.fersaiyan.cyanbridge.ui

/**
 * Compatibility component-name token for inherited conversation intents.
 *
 * The manifest redirects this component into the AD Glasses Compose navigation
 * store. Only the historical intent-extra contract is retained here.
 */
class ChatThreadActivity private constructor() {
    companion object {
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_CREATE_THREAD_TITLE = "create_thread_title"
        const val EXTRA_PREFILL_MESSAGE = "prefill_message"
        const val EXTRA_DAILY_FACTS_REVIEW = "daily_facts_review"
        const val EXTRA_DAILY_FACTS_DATE = "daily_facts_date"
        const val EXTRA_DAILY_FACTS_LOOKBACK_DAYS = "daily_facts_lookback_days"
    }
}
