package com.sovereign.commandcenter.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class ExecutiveAudioCadenceEngine(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isReady: Boolean = false
    var isMuted: Boolean = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setPitch(1.0f)
            tts?.setSpeechRate(1.05f)
            isReady = true
        }
    }

    fun speakExecutiveSummary(summaryText: String) {
        if (isMuted || !isReady || summaryText.isBlank()) return
        val cleanText = sanitizeForExecutiveVoice(summaryText)
        if (cleanText.isNotBlank()) {
            tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "EXECUTIVE_CADENCE_${System.currentTimeMillis()}")
        }
    }

    fun toggleMute(): Boolean {
        isMuted = !isMuted
        if (isMuted) {
            tts?.stop()
        }
        return isMuted
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isReady = false
        } catch (_: Exception) {}
    }

    companion object {
        fun sanitizeForExecutiveVoice(rawText: String): String {
            return rawText
                .replace(Regex("```[\\s\\S]*?```"), "")
                .replace(Regex("`[^`]*`"), "")
                .replace(Regex("@@[^@]+@@"), "")
                .replace(Regex("^[+\\-].*$", RegexOption.MULTILINE), "")
                .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
                .replace(Regex("[#*_`~<>]"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }
}
