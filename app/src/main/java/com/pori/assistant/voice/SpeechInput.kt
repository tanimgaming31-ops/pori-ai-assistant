package com.pori.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Listens once with Android's SpeechRecognizer and returns the text. */
class SpeechInput(private val context: Context) {

    fun isAvailable() = SpeechRecognizer.isRecognitionAvailable(context)

    /** Returns null if nothing was recognized or an error occurred. */
    suspend fun listen(languageTag: String, detectAmong: List<String> = emptyList()): String? =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val rec = SpeechRecognizer.createSpeechRecognizer(context)
                fun finish(v: String?) {
                    if (cont.isActive) cont.resume(v)
                    rec.destroy()
                }
                rec.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(results: Bundle?) {
                        finish(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())
                    }
                    override fun onError(error: Int) = finish(null)
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    // Android 13+: ask the recognizer to detect the language among these (best effort)
                    if (detectAmong.isNotEmpty() && Build.VERSION.SDK_INT >= 33) {
                        putExtra("android.speech.extra.ENABLE_LANGUAGE_DETECTION", true)
                        putStringArrayListExtra(
                            "android.speech.extra.LANGUAGE_DETECTION_ALLOWED_LANGUAGES",
                            ArrayList(detectAmong)
                        )
                    }
                }
                cont.invokeOnCancellation { rec.destroy() }
                rec.startListening(intent)
            }
        }
}
