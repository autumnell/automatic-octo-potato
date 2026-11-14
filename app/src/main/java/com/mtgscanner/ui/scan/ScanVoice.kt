package com.mtgscanner.ui.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.mtgscanner.scan.VoiceCommands

/**
 * Voice commands while scanning: listens over and over while [active] (the scan screen is up and not paused), and
 * hands each command heard to [onCommand]. Asks for the microphone the first time; without it, does nothing.
 */
@Composable
fun ScanVoiceListener(active: Boolean, onCommand: (com.mtgscanner.scan.VoiceCommand) -> Unit, onUnavailable: () -> Unit) {
    val ctx = LocalContext.current
    var allowed by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        allowed = ok
        if (!ok) onUnavailable()
    }
    LaunchedEffect(Unit) { if (!allowed) ask.launch(Manifest.permission.RECORD_AUDIO) }
    if (!allowed) return
    if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
        LaunchedEffect(Unit) { onUnavailable() }
        return
    }
    val latest by androidx.compose.runtime.rememberUpdatedState(onCommand)
    // Only while the app is in front: in the background Android gives no microphone, and the retries would run on.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val shown by lifecycle.currentStateFlow.collectAsState()
    val listening = active && shown.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    DisposableEffect(listening) {
        if (!listening) return@DisposableEffect onDispose { }
        val main = Handler(Looper.getMainLooper())
        val recognizer = SpeechRecognizer.createSpeechRecognizer(ctx)
        var stopped = false
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        fun listen() {
            if (stopped) return
            runCatching { recognizer.startListening(intent) }
        }
        // Each listen ends after a phrase or a quiet spell; the next starts a moment later. A busy or failing
        // recognizer waits longer before trying again.
        fun again(ms: Long) { main.postDelayed({ listen() }, ms) }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                VoiceCommands.first(heard)?.let { latest(it) }
                again(150)
            }
            override fun onError(error: Int) {
                again(
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 150L
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 800L
                        else -> 2500L
                    },
                )
            }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        listen()
        onDispose {
            stopped = true
            main.removeCallbacksAndMessages(null)
            runCatching { recognizer.cancel() }
            runCatching { recognizer.destroy() }
        }
    }
}
