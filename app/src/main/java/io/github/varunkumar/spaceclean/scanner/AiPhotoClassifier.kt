package io.github.varunkumar.spaceclean.scanner

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/**
 * On-device image understanding, powered by a bundled MobileNet model (TensorFlow Lite).
 *
 * Everything runs locally on the phone — the model ships inside the app's assets, inference
 * uses only the CPU, and there is **no network access of any kind** (the app declares no
 * INTERNET permission). This is the genuine AI behind "AI Photo Cleanup": the model looks at
 * each photo and returns (a) what it thinks the photo contains and (b) a feature vector used
 * to group visually-similar shots far more accurately than a hash can.
 *
 * The [Interpreter] is not thread-safe, so [classify] is synchronized; call it from a single
 * background dispatcher.
 */
class AiPhotoClassifier(context: Context) : Closeable {

    data class Result(
        /** Top predicted labels with confidence 0..1, highest first. */
        val labels:    List<Pair<String, Float>>,
        /** L2-normalized feature vector for cosine-similarity grouping. */
        val embedding: FloatArray,
    )

    private val labels: List<String> = runCatching {
        context.assets.open(LABELS_ASSET).bufferedReader().useLines { it.toList() }
    }.getOrDefault(emptyList())

    private val interpreter: Interpreter? = runCatching {
        Interpreter(loadModel(context), Interpreter.Options().apply { numThreads = 2 })
    }.getOrNull()

    /** False when the model failed to load — callers fall back to the non-AI scanners. */
    val isAvailable: Boolean get() = interpreter != null && labels.isNotEmpty()

    @Synchronized
    fun classify(bitmap: Bitmap): Result? {
        val tflite = interpreter ?: return null
        return runCatching {
            val input  = toInputBuffer(bitmap)
            val output = Array(1) { ByteArray(labels.size) }
            tflite.run(input, output)

            // Dequantize uint8 → probability (mobilenet_v1 quant: scale ≈ 1/256, zeroPoint 0).
            val probs = FloatArray(labels.size) { (output[0][it].toInt() and 0xFF) / 256f }

            val top = probs.indices
                .sortedByDescending { probs[it] }
                .take(TOP_K)
                .filter { probs[it] >= MIN_CONFIDENCE }
                .map { labels[it] to probs[it] }

            Result(labels = top, embedding = l2Normalize(probs))
        }.getOrNull()
    }

    override fun close() {
        runCatching { interpreter?.close() }
    }

    // ── Internals ───────────────────────────────────────────────────────────────

    private fun loadModel(context: Context): MappedByteBuffer {
        context.assets.openFd(MODEL_ASSET).use { fd ->
            java.io.FileInputStream(fd.fileDescriptor).use { input ->
                return input.channel.map(
                    FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength,
                )
            }
        }
    }

    /** Scales the bitmap to 224×224 and packs raw RGB uint8 bytes (what the quant model expects). */
    private fun toInputBuffer(bitmap: Bitmap): ByteBuffer {
        val scaled = if (bitmap.width == INPUT_SIZE && bitmap.height == INPUT_SIZE) bitmap
                     else Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val buffer = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3).order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (pixel in pixels) {
            buffer.put((pixel shr 16 and 0xFF).toByte())   // R
            buffer.put((pixel shr 8 and 0xFF).toByte())    // G
            buffer.put((pixel and 0xFF).toByte())          // B
        }
        if (scaled !== bitmap) scaled.recycle()
        buffer.rewind()
        return buffer
    }

    private fun l2Normalize(v: FloatArray): FloatArray {
        var sum = 0f
        for (x in v) sum += x * x
        val norm = sqrt(sum).coerceAtLeast(1e-6f)
        return FloatArray(v.size) { v[it] / norm }
    }

    companion object {
        private const val MODEL_ASSET  = "mobilenet_v1_224_quant.tflite"
        private const val LABELS_ASSET = "mobilenet_labels.txt"
        private const val INPUT_SIZE   = 224
        private const val TOP_K        = 5
        private const val MIN_CONFIDENCE = 0.10f

        /** Cosine similarity of two L2-normalized embeddings (0..1 for these non-negative vectors). */
        fun cosine(a: FloatArray, b: FloatArray): Float {
            if (a.size != b.size) return 0f
            var dot = 0f
            for (i in a.indices) dot += a[i] * b[i]
            return dot
        }
    }
}
