package com.pori.assistant.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.pori.assistant.brain.Agent
import com.pori.assistant.brain.LlmClient
import com.pori.assistant.brain.tools.BasicTools
import com.pori.assistant.brain.tools.SetLanguageTool
import com.pori.assistant.brain.tools.ToolRegistry
import com.pori.assistant.data.Languages
import com.pori.assistant.data.Settings
import com.pori.assistant.voice.OpenWakeWordDetector
import com.pori.assistant.voice.SpeechInput
import com.pori.assistant.voice.SpeechOutput
import com.pori.assistant.voice.WakeWordDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

enum class PoriState { IDLE, WAITING_WAKE, LISTENING, THINKING, SPEAKING }

data class ChatLine(val who: String, val text: String)

/** Pori's main loop: wake word -> listen -> think (AI) -> speak. */
class PoriController(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = Settings(context)

    /** Language of the last reply; used as the listening language in auto mode. */
    @Volatile private var lastLang = "en-US"

    val registry = ToolRegistry().apply {
        registerAll(BasicTools.all(context))
        register(SetLanguageTool { tag ->
            settings.setLanguage(tag)
            if (tag != Languages.AUTO) lastLang = tag
        })
    }

    private val speechIn = SpeechInput(context)
    private val speechOut = SpeechOutput(context)
    private val agent = Agent(
        llm = LlmClient(settings),
        registry = registry,
        system = SYSTEM_PROMPT,
        confirm = ::confirm,
        onToolCall = { log("tool", it) }
    )

    private val _state = MutableStateFlow(PoriState.IDLE)
    val state: StateFlow<PoriState> = _state
    private val _lines = MutableStateFlow<List<ChatLine>>(emptyList())
    val lines: StateFlow<List<ChatLine>> = _lines

    private var wakeJob: Job? = null
    private val manualTrigger = AtomicBoolean(false)
    private val busy = AtomicBoolean(false)

    fun isWakeLoopRunning() = wakeJob?.isActive == true

    /** Starts the always-listening wake-word loop (called from the foreground service). */
    fun startWakeLoop() {
        if (isWakeLoopRunning()) return
        wakeJob = scope.launch(Dispatchers.IO) {
            if (!hasMic()) { log("system", "Microphone permission is missing."); return@launch }
            if (!OpenWakeWordDetector.modelsPresent(context)) {
                log("system", "No wake-word model installed. Tap the orb to talk.")
                _state.value = PoriState.IDLE
                return@launch
            }
            OpenWakeWordDetector(context).use { detector ->
                while (isActive) {
                    _state.value = PoriState.WAITING_WAKE
                    if (waitForWake(detector)) {
                        busy.set(true)
                        try { converse() } finally { busy.set(false) }
                    }
                }
            }
        }
    }

    fun stopAll() {
        wakeJob?.cancel(); wakeJob = null
        speechOut.stop()
        _state.value = PoriState.IDLE
    }

    /** Orb tap: start listening right away, no wake word needed. */
    fun talkNow() {
        if (isWakeLoopRunning()) { manualTrigger.set(true); return }
        if (busy.compareAndSet(false, true)) {
            scope.launch { try { converse() } finally { busy.set(false); _state.value = PoriState.IDLE } }
        }
    }

    // ---- internals ----

    private suspend fun waitForWake(detector: WakeWordDetector): Boolean {
        val rate = 16000
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        @Suppress("MissingPermission")
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(min, WakeWordDetector.CHUNK * 4)
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); kotlinx.coroutines.delay(1000); return false }
        val chunk = ShortArray(WakeWordDetector.CHUNK)
        try {
            rec.startRecording()
            while (scope.isActive) {
                var got = 0
                while (got < chunk.size) {
                    val n = rec.read(chunk, got, chunk.size - got)
                    if (n < 0) return false
                    got += n
                }
                if (manualTrigger.getAndSet(false) || detector.process(chunk)) return true
            }
            return false
        } finally {
            runCatching { rec.stop() }
            rec.release()
        }
    }

    private suspend fun converse() {
        val pref = settings.languageNow()
        val auto = pref == Languages.AUTO
        val listenTag = if (auto) lastLang else pref

        _state.value = PoriState.LISTENING
        val heard = speechIn.listen(listenTag, if (auto) Languages.detectable() else emptyList())
        if (heard.isNullOrBlank()) return
        log("you", heard)

        _state.value = PoriState.THINKING
        var speakTag: String?
        val text: String
        try {
            val (tag, body) = splitLang(agent.ask(heard))
            speakTag = tag
            text = body
            if (tag != null && auto) lastLang = tag
        } catch (e: Exception) {
            speakTag = "en-US"
            text = friendlyError(e)
        }
        log("pori", text)

        _state.value = PoriState.SPEAKING
        val tag = speakTag ?: listenTag
        if (!speechOut.speak(text, tag)) {
            log("system", "This phone has no voice for \"$tag\". Install it in Settings > Text-to-speech.")
        }
    }

    /** Asks out loud and listens for yes/no before sensitive actions. */
    private suspend fun confirm(prompt: String): Boolean {
        _state.value = PoriState.SPEAKING
        speechOut.speak(prompt, "en-US")
        _state.value = PoriState.LISTENING
        val pref = settings.languageNow()
        val auto = pref == Languages.AUTO
        val ans = speechIn.listen(if (auto) lastLang else pref, if (auto) Languages.detectable() else emptyList())
            ?.lowercase() ?: return false
        log("you", ans)
        if (NO_WORDS.any { ans.contains(it) }) return false
        return YES_WORDS.any { ans.contains(it) }
    }

    private fun hasMic() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun log(who: String, text: String) {
        val last = _lines.value.lastOrNull()
        if (who == "system" && last?.who == who && last.text == text) return
        _lines.value = (_lines.value + ChatLine(who, text)).takeLast(200)
    }

    /** Replies start with "[lang:xx-XX]"; split it off so it is neither shown nor spoken. */
    private fun splitLang(reply: String): Pair<String?, String> {
        val m = LANG_TAG.find(reply) ?: return null to reply.trim()
        return m.groupValues[1] to reply.removeRange(m.range).trim()
    }

    private fun friendlyError(e: Exception): String {
        val m = e.message.orEmpty()
        return when {
            "API key" in m || "401" in m -> "The API key is not valid. Please check it in settings."
            "credit balance" in m || "billing" in m -> "The API account has no credit. Please add credit."
            "429" in m -> "Too many requests. Please try again in a moment."
            "Unable to resolve" in m || "timeout" in m.lowercase() -> "I can't reach the internet right now."
            else -> "Something went wrong."
        }
    }

    fun shutdown() { stopAll(); speechOut.shutdown(); scope.cancel() }

    companion object {
        private val LANG_TAG = Regex("^\\s*\\[lang:([A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*)\\]\\s*")

        private val YES_WORDS = listOf(
            "yes", "yeah", "ok", "okay", "sure", "yep", "haan", "han", "ji", "ya", "iya", "oo", "oui", "si", "sí",
            "হ্যাঁ", "হাঁ", "हाँ", "हां", "ठीक", "ہاں", "جی", "نعم", "ایوا"
        )
        private val NO_WORDS = listOf(
            "no", "nope", "nahi", "nahin", "cancel", "stop", "tidak", "hindi", "non",
            "না", "नहीं", "नही", "نہیں", "نہ", "لا", "বাতিল"
        )

        const val SYSTEM_PROMPT = """You are Pori, a voice assistant living on the user's phone.

LANGUAGE (very important):
- Always reply in the same language the user is speaking (Hindi, Urdu, Arabic, Indonesian, Tagalog, Bengali, English, or any other language). If they switch language, switch with them.
- Begin EVERY reply with a language tag in square brackets using a BCP-47 code for the language of your reply, for example [lang:hi-IN], [lang:ur-PK], [lang:ar-SA], [lang:id-ID], [lang:fil-PH], [lang:en-US]. The tag is removed before the user sees it. Never mention the tag.
- If the user's speech looks like it was transcribed in the wrong language, or they ask you to speak a certain language, call the set_speech_language tool.

STYLE:
- Keep replies short and natural for speaking aloud. No markdown, lists, or emoji.
- To act on the phone (time, battery, ...) use the matching tool; never guess or invent results.
- If a tool fails or the user declines, say so honestly."""
    }
}
