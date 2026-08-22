package com.ad_glasses.ai.transcription

class TranscriptionHttpException(
    val code: Int,
    val body: String?
) : Exception("HTTP $code")
