package com.achyut.adglasses.ai.transcription

class TranscriptionHttpException(
    val code: Int,
    val body: String?
) : Exception("HTTP $code")
