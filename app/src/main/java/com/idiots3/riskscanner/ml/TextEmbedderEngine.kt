package com.idiots3.riskscanner.ml

import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder

/**
 * Thin wrapper around MediaPipe's on-device TextEmbedder running the bundled
 * Universal Sentence Encoder model (~6 MB). Everything happens on-device;
 * the model never fetches anything at runtime.
 *
 * The native handle is intentionally closeable: the ViewModel closes it once the
 * AI pass finishes so the native runtime memory is released while the app keeps running.
 */
class TextEmbedderEngine(private val context: Context) {

    private var embedder: TextEmbedder? = null
    private var initializationFailed = false
    private val lock = Any()

    val failed: Boolean
        get() = initializationFailed

    fun ensureInitialized(): Boolean = synchronized(lock) {
        if (embedder != null) true
        else if (initializationFailed) false
        else {
            try {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath(MODEL_ASSET)
                    .build()
                val options = TextEmbedder.TextEmbedderOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setL2Normalize(true)
                    .build()
                embedder = TextEmbedder.createFromOptions(context, options)
            } catch (t: Throwable) {
                initializationFailed = true
                embedder = null
            }
            embedder != null
        }
    }

    /** Returns a 512-dimension normalized embedding, or null when inference fails. */
    fun embed(text: String): FloatArray? {
        val instance = synchronized(lock) { embedder } ?: return null
        return try {
            instance.embed(text)
                .embeddingResult()
                .embeddings()
                .firstOrNull()
                ?.floatEmbedding()
        } catch (t: Throwable) {
            null
        }
    }

    fun close() {
        synchronized(lock) {
            try {
                embedder?.close()
            } catch (_: Throwable) {
            }
            embedder = null
        }
    }

    companion object {
        const val MODEL_ASSET = "universal_sentence_encoder.tflite"
        const val DIMENSIONS = 512
    }
}
