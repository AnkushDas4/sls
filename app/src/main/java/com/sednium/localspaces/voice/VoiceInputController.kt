package com.sednium.localspaces.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Lightweight handle returned by [rememberVoiceInputController]. */
class VoiceInputController internal constructor(
    private val onToggleStart: () -> Unit,
    private val onToggleStop: () -> Unit,
    private val listeningState: State<Boolean>
) {
    val isListening: Boolean get() = listeningState.value
    fun toggle() {
        if (isListening) onToggleStop() else onToggleStart()
    }
}

/**
 * Wraps Android's built-in, on-device SpeechRecognizer for press-to-talk
 * dictation straight into the message composer. Deliberately not using any
 * provider's audio API: this is free, works offline on most devices, and
 * needs zero new Gradle dependencies — SpeechRecognizer ships with the
 * platform. RECORD_AUDIO is requested the first time the mic button is
 * tapped, not on app launch.
 */
@Composable
fun rememberVoiceInputController(
    onPartialResult: (String) -> Unit = {},
    onFinalResult: (String) -> Unit,
    onError: (String) -> Unit = {}
): VoiceInputController {
    val context = LocalContext.current
    val isListeningState = remember { mutableStateOf(false) }
    var isListening by isListeningState

    val currentOnPartial by rememberUpdatedState(onPartialResult)
    val currentOnFinal by rememberUpdatedState(onFinalResult)
    val currentOnError by rememberUpdatedState(onError)

    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    }

    DisposableEffect(Unit) {
        onDispose { recognizer?.destroy() }
    }

    fun actuallyStart() {
        val r = recognizer
        if (r == null) {
            currentOnError("Speech recognition isn't available on this device.")
            return
        }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                isListening = false
                val message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that — try again."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Network error during voice recognition."
                    else -> "Voice input error ($error)."
                }
                currentOnError(message)
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) currentOnFinal(text)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) currentOnPartial(text)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        r.startListening(intent)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) actuallyStart() else currentOnError("Microphone permission was denied.")
    }

    fun start() {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) actuallyStart() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun stop() {
        recognizer?.stopListening()
        isListening = false
    }

    return VoiceInputController(
        onToggleStart = { start() },
        onToggleStop = { stop() },
        listeningState = isListeningState
    )
}
