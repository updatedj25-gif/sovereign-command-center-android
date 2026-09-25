package com.sovereign.commandcenter.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed class VoiceInputState {
    object Idle : VoiceInputState()
    object Listening : VoiceInputState()
    object Processing : VoiceInputState()
    data class ReviewTranscript(val recognizedText: String) : VoiceInputState()
    data class Error(val message: String) : VoiceInputState()
}

class VoiceManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false

    private val _inputState = MutableStateFlow<VoiceInputState>(VoiceInputState.Idle)
    val inputState: StateFlow<VoiceInputState> = _inputState.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    init {
        try {
            textToSpeech = TextToSpeech(context.applicationContext, this)
        } catch (_: Exception) {
            ttsReady = false
        }
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _inputState.value = VoiceInputState.Error("Speech recognition is unavailable on this device.")
            return
        }

        stopListening()
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _inputState.value = VoiceInputState.Listening
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        _inputState.value = VoiceInputState.Processing
                    }
                    override fun onError(error: Int) {
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized."
                            SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                            else -> "Recognition error code: $error"
                        }
                        _inputState.value = VoiceInputState.Error(msg)
                    }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val bestResult = matches?.firstOrNull() ?: ""
                        // Require review: Set into ReviewTranscript mode so CEO reviews before sending
                        _inputState.value = VoiceInputState.ReviewTranscript(bestResult)
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            _inputState.value = VoiceInputState.Error("Failed to initiate voice capture: ${e.message}")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        if (_inputState.value is VoiceInputState.Listening) {
            _inputState.value = VoiceInputState.Idle
        }
    }

    fun resetInputState() {
        _inputState.value = VoiceInputState.Idle
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.US
            ttsReady = true
        } else {
            ttsReady = false
        }
    }

    fun speakResponse(text: String, isVoiceOutputEnabled: Boolean = true) {
        if (!ttsReady || !isVoiceOutputEnabled || text.isBlank()) return

        // Privacy and safety filter: redact code blocks, secrets, and raw tool output from speech
        val sanitized = text.replace(Regex("(?s)```.*?```"), "Code block omitted.")
            .replace(Regex("(?i)bearer\\s+[a-z0-9_\\-\\.]+"), "Token omitted.")
            .replace(Regex("(?i)ghp_[a-z0-9]+"), "Token omitted.")

        try {
            textToSpeech?.stop()
            _isSpeaking.value = true
            textToSpeech?.speak(sanitized, TextToSpeech.QUEUE_FLUSH, null, "SeniorVoiceId")
        } catch (_: Exception) {
            _isSpeaking.value = false
        }
    }

    fun stopSpeaking() {
        try {
            textToSpeech?.stop()
        } catch (_: Exception) {}
        _isSpeaking.value = false
    }

    fun shutdown() {
        stopListening()
        stopSpeaking()
        try {
            textToSpeech?.shutdown()
        } catch (_: Exception) {}
    }
}
