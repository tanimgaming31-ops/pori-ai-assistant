package com.pori.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/** Wrapper around Android TextToSpeech. */
class SpeechOutput(context: Context) {

    private val ready = CompletableDeferred<Boolean>()
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready.complete(status == TextToSpeech.SUCCESS)
    }

    /** Returns false if the phone has no voice for this language. */
    suspend fun speak(text: String, languageTag: String): Boolean {
        if (text.isBlank() || !ready.await()) return false
        val r = tts.setLanguage(Locale.forLanguageTag(languageTag))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) return false
        suspendCancellableCoroutine<Unit> { cont ->
            val id = UUID.randomUUID().toString()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { if (utteranceId == id && cont.isActive) cont.resume(Unit) }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { if (utteranceId == id && cont.isActive) cont.resume(Unit) }
            })
            cont.invokeOnCancellation { tts.stop() }
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        }
        return true
    }

    fun stop() { tts.stop() }
    fun shutdown() { tts.shutdown() }
}
