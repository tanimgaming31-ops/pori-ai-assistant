package com.pori.assistant.voice

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/** Consumes 16 kHz mono PCM16 audio and returns true when the wake word is heard. */
interface WakeWordDetector : AutoCloseable {
    /** Pass exactly [CHUNK] samples (80 ms). */
    fun process(pcm: ShortArray): Boolean

    companion object { const val CHUNK = 1280 }
}

/**
 * openWakeWord approach: melspectrogram -> embedding -> wake-word classifier (all .tflite).
 * Needs in assets/wakeword/: melspectrogram.tflite, embedding_model.tflite, hey_pori.tflite
 * (see README: the first two come from openWakeWord, the third must be trained for "Pori".)
 */
class OpenWakeWordDetector(
    context: Context,
    wakeModel: String = "hey_pori.tflite",
    private val threshold: Float = 0.5f,
    private val cooldownMs: Long = 2000
) : WakeWordDetector {

    private val mel = Interpreter(load(context, "melspectrogram.tflite"))
    private val emb = Interpreter(load(context, "embedding_model.tflite"))
    private val clf = Interpreter(load(context, wakeModel))

    private val audioBuf = ArrayDeque<Float>()               // recent raw audio
    private val melFrames = ArrayDeque<FloatArray>()         // each frame = 32 values
    private val features = ArrayDeque<FloatArray>()          // each = 96-value embedding
    private var lastHit = 0L

    override fun process(pcm: ShortArray): Boolean {
        require(pcm.size == WakeWordDetector.CHUNK) { "chunk must be ${WakeWordDetector.CHUNK}" }
        for (s in pcm) audioBuf.addLast(s.toFloat())
        while (audioBuf.size > CHUNK_WITH_CONTEXT) audioBuf.removeFirst()
        if (audioBuf.size < CHUNK_WITH_CONTEXT) return false

        // 1) melspectrogram (append the new frames; value = x/10 + 2)
        for (f in runMel(audioBuf.toFloatArray())) melFrames.addLast(f)
        while (melFrames.size > MEL_WINDOW + 16) melFrames.removeFirst()
        if (melFrames.size < MEL_WINDOW) return false

        // 2) embedding: last 76 frames -> 96 numbers
        features.addLast(runEmbedding(melFrames.toList().takeLast(MEL_WINDOW)))
        while (features.size > FEATURE_WINDOW) features.removeFirst()
        if (features.size < FEATURE_WINDOW) return false

        // 3) wake-word classifier: last 16 embeddings -> score
        val score = runClassifier(features.toList())
        val now = System.currentTimeMillis()
        if (score >= threshold && now - lastHit > cooldownMs) {
            lastHit = now
            features.clear()
            return true
        }
        return false
    }

    private fun runMel(samples: FloatArray): List<FloatArray> {
        mel.resizeInput(0, intArrayOf(1, samples.size))
        mel.allocateTensors()
        val input = floatBuffer(samples)
        val out = ByteBuffer.allocateDirect(mel.getOutputTensor(0).numBytes()).order(ByteOrder.nativeOrder())
        mel.run(input, out)
        out.rewind()
        val fb = out.asFloatBuffer()
        val frames = fb.remaining() / MEL_BINS
        return List(frames) { FloatArray(MEL_BINS) { fb.get() / 10f + 2f } }
    }

    private fun runEmbedding(window: List<FloatArray>): FloatArray {
        val flat = FloatArray(MEL_WINDOW * MEL_BINS)
        window.forEachIndexed { i, f -> f.copyInto(flat, i * MEL_BINS) }
        val out = ByteBuffer.allocateDirect(emb.getOutputTensor(0).numBytes()).order(ByteOrder.nativeOrder())
        emb.run(floatBuffer(flat), out)
        out.rewind()
        val fb = out.asFloatBuffer()
        return FloatArray(fb.remaining()) { fb.get() }
    }

    private fun runClassifier(feats: List<FloatArray>): Float {
        val flat = FloatArray(FEATURE_WINDOW * EMB_SIZE)
        feats.forEachIndexed { i, f -> f.copyInto(flat, i * EMB_SIZE, 0, EMB_SIZE) }
        val out = ByteBuffer.allocateDirect(clf.getOutputTensor(0).numBytes()).order(ByteOrder.nativeOrder())
        clf.run(floatBuffer(flat), out)
        out.rewind()
        return out.asFloatBuffer().get(0)
    }

    private fun floatBuffer(a: FloatArray): ByteBuffer {
        val b = ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder())
        b.asFloatBuffer().put(a)
        b.rewind()
        return b
    }

    override fun close() { mel.close(); emb.close(); clf.close() }

    companion object {
        private const val MEL_BINS = 32
        private const val MEL_WINDOW = 76
        private const val EMB_SIZE = 96
        private const val FEATURE_WINDOW = 16
        private const val CHUNK_WITH_CONTEXT = WakeWordDetector.CHUNK + 160 * 3

        /** Whether the model files exist; if not, the app runs in tap-to-talk mode. */
        fun modelsPresent(context: Context, wakeModel: String = "hey_pori.tflite"): Boolean =
            listOf("melspectrogram.tflite", "embedding_model.tflite", wakeModel).all {
                runCatching { context.assets.open("wakeword/$it").close() }.isSuccess
            }

        private fun load(context: Context, name: String): MappedByteBuffer {
            val fd = try { context.assets.openFd("wakeword/$name") }
            catch (e: Exception) { throw FileNotFoundException("assets/wakeword/$name is missing") }
            return fd.use { f ->
                java.io.FileInputStream(f.fileDescriptor).use { s ->
                    s.channel.map(FileChannel.MapMode.READ_ONLY, f.startOffset, f.declaredLength)
                }
            }
        }
    }
}
