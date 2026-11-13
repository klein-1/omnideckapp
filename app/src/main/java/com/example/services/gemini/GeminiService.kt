package com.example.services.gemini

import androidx.compose.runtime.Immutable

/**
 * Keep every model name in one file (§2.1):
 * newest Flash-class model for text, newest Gemini image model for generation/editing,
 * Gemini TTS for speech, and Live API for voice.
 */
object DotkitAiModels {
    const val TEXT_FLASH = "gemini-2.5-flash"
    const val IMAGE_GEN = "gemini-2.5-flash-image"
    const val SPEECH_TTS = "gemini-2.5-flash-preview-tts"
    const val VOICE_LIVE = "gemini-2.0-flash-exp"
}

@Immutable
data class AiStatusSnapshot(
    val textModel: String = DotkitAiModels.TEXT_FLASH,
    val imageModel: String = DotkitAiModels.IMAGE_GEN,
    val ttsModel: String = DotkitAiModels.SPEECH_TTS,
    val liveModel: String = DotkitAiModels.VOICE_LIVE,
    val hasCustomKey: Boolean = false,
    val offlineOnlyKillSwitch: Boolean = false,
    val userConsentedOffDevice: Boolean = false,
    val sessionTokensUsed: Int = 0,
    val sessionCallsCount: Int = 0
)
