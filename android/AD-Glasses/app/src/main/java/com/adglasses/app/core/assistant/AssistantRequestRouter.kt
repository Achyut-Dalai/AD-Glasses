package com.adglasses.app.core.assistant

sealed interface AssistantRoute {
    data object Conversation : AssistantRoute
    data object Notifications : AssistantRoute
    data object CapturePhoto : AssistantRoute
    data object StartVideo : AssistantRoute
    data object StopVideo : AssistantRoute
    data object StartAudio : AssistantRoute
    data object StopAudio : AssistantRoute
    data class PhoneCall(val query: String) : AssistantRoute
    data class SendSms(val recipient: String, val body: String) : AssistantRoute
    data class ReplyNotification(val target: String?, val body: String) : AssistantRoute
}

/**
 * Deterministic local-action boundary ported from the iOS Assistant router and extended with
 * Android-only notification interaction. It runs only on the user's own utterance; model output is
 * never interpreted as a device action.
 */
object AssistantRequestRouter {
    fun route(raw: String): AssistantRoute {
        val text = raw.trim()
        if (text.isBlank()) return AssistantRoute.Conversation
        val normalized = normalize(text)
        val words = normalized.split(' ').filter { it.isNotBlank() }.toSet()
        if (isMetaQuestion(normalized) || words.any { it in setOf("not", "dont", "never") }) {
            return AssistantRoute.Conversation
        }

        notificationReply(text)?.let { return it }
        sms(text)?.let { return it }
        phoneCall(text)?.let { return AssistantRoute.PhoneCall(it) }

        if (containsAny(normalized, listOf(
                "what notifications", "my notifications", "recent notifications", "latest notifications",
                "what did i miss", "anything i missed", "notifications did i get", "read my notifications",
                "show notifications", "notification summary"
            ))) return AssistantRoute.Notifications

        val captureVerb = words.any { it in setOf("take", "capture", "click", "snap", "shoot") }
        val photoSubject = words.any { it in setOf("photo", "picture", "photograph") }
        if (captureVerb && photoSubject) return AssistantRoute.CapturePhoto

        if (containsAny(normalized, listOf("stop video", "stop recording video", "end video", "finish video", "end video recording"))) {
            return AssistantRoute.StopVideo
        }
        if (containsAny(normalized, listOf("start video", "record video", "record a video", "start recording video", "begin video", "begin video recording"))) {
            return AssistantRoute.StartVideo
        }
        if (containsAny(normalized, listOf("stop audio", "stop audio recording", "stop recording audio", "end audio recording", "finish audio recording"))) {
            return AssistantRoute.StopAudio
        }
        if (containsAny(normalized, listOf("start audio", "record audio", "record some audio", "start audio recording", "start recording audio", "begin audio recording"))) {
            return AssistantRoute.StartAudio
        }

        return AssistantRoute.Conversation
    }

    private fun notificationReply(text: String): AssistantRoute.ReplyNotification? {
        val trimmed = text.trim()
        val targeted = listOf(
            Regex("^(?:please\\s+)?reply\\s+to\\s+(.+?)\\s+(?:saying|that|with)\\s+(.+)$", RegexOption.IGNORE_CASE),
            Regex("^(?:please\\s+)?reply\\s+to\\s+([^:,-]+?)\\s*[:,-]\\s*(.+)$", RegexOption.IGNORE_CASE),
        )
        targeted.forEach { pattern ->
            val match = pattern.matchEntire(trimmed) ?: return@forEach
            val target = match.groupValues[1].trim()
            val body = match.groupValues[2].trim()
            if (target.isNotBlank() && body.isNotBlank()) {
                return AssistantRoute.ReplyNotification(target, body)
            }
        }

        val latest = listOf(
            Regex("^(?:please\\s+)?reply\\s+(?:saying|that|with)\\s+(.+)$", RegexOption.IGNORE_CASE),
            Regex("^(?:please\\s+)?reply\\s*[:,-]\\s*(.+)$", RegexOption.IGNORE_CASE),
            Regex("^(?:please\\s+)?reply\\s+(?!to\\b)(.+)$", RegexOption.IGNORE_CASE),
        )
        latest.forEach { pattern ->
            val match = pattern.matchEntire(trimmed) ?: return@forEach
            val body = match.groupValues[1].trim()
            if (body.isNotBlank()) return AssistantRoute.ReplyNotification(null, body)
        }
        return null
    }

    private fun phoneCall(text: String): String? {
        val match = Regex(
            "^(?:please\\s+)?(?:call|phone|dial|make\\s+a\\s+call\\s+to|place\\s+a\\s+call\\s+to|ring)\\s+(?:up\\s+)?(.+)$",
            RegexOption.IGNORE_CASE,
        ).matchEntire(text.trim()) ?: return null
        val candidate = match.groupValues[1].trim().trimEnd('.', '?', '!', ',')
        val nonTargets = setOf("it a day", "it quits", "of duty", "me back", "you later", "off", "back")
        return candidate.takeIf { it.isNotBlank() && it.lowercase() !in nonTargets }
    }

    private fun sms(text: String): AssistantRoute.SendSms? {
        val trimmed = text.trim()
        val explicit = listOf(
            Regex("^(?:please\\s+)?send\\s+(?:a\\s+)?(?:text|message)\\s+to\\s+(.+?)\\s+(?:saying|that)\\s+(.+)$", RegexOption.IGNORE_CASE),
            Regex("^(?:please\\s+)?(?:text|message)\\s+(.+?)\\s+(?:saying|that)\\s+(.+)$", RegexOption.IGNORE_CASE),
            Regex("^(?:please\\s+)?send\\s+(?:a\\s+)?(?:text|message)\\s+to\\s+([^:,-]+?)\\s*[:,-]\\s*(.+)$", RegexOption.IGNORE_CASE),
            Regex("^(?:please\\s+)?(?:text|message)\\s+([^:,-]+?)\\s*[:,-]\\s*(.+)$", RegexOption.IGNORE_CASE),
        )
        explicit.forEach { pattern ->
            val match = pattern.matchEntire(trimmed) ?: return@forEach
            val recipient = match.groupValues[1].trim()
            val body = match.groupValues[2].trim()
            if (recipient.isNotBlank() && body.isNotBlank()) return AssistantRoute.SendSms(recipient, body)
        }

        val direct = Regex("^(?:please\\s+)?(?:text|message)\\s+(\\+?[0-9][0-9 ()-]{4,})\\s+(.+)$", RegexOption.IGNORE_CASE)
            .matchEntire(trimmed)
        if (direct != null) {
            val recipient = direct.groupValues[1].trim()
            val body = direct.groupValues[2].trim()
            if (body.isNotBlank()) return AssistantRoute.SendSms(recipient, body)
        }
        return null
    }

    private fun normalize(value: String): String = value
        .lowercase()
        .replace('’', '\'')
        .replace("'", "")
        .replace(Regex("[^a-z0-9+]+"), " ")
        .trim()

    private fun isMetaQuestion(text: String): Boolean =
        containsAny(text, listOf("how do i", "how to", "can you explain", "what happens if", "how does"))

    private fun containsAny(text: String, phrases: List<String>): Boolean = phrases.any(text::contains)
}
