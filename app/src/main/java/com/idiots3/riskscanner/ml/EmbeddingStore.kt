package com.idiots3.riskscanner.ml

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * Small disk cache for text embeddings. Keeps the AI pass fast on re-scan and
 * avoids re-running the model for unchanged apps.
 *
 * Only numeric vectors are stored — never any user content.
 */
class EmbeddingStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, "embedding_cache.json")
    private val vectors = ConcurrentHashMap<String, FloatArray>()

    @Volatile
    private var loaded = false

    private val loadLock = Any()

    fun get(key: String): FloatArray? {
        ensureLoaded()
        return vectors[key]
    }

    fun put(key: String, vector: FloatArray) {
        vectors[key] = vector
    }

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(loadLock) {
            if (loaded) return
            try {
                if (file.exists()) {
                    val root = JSONObject(file.readText())
                    val keys = root.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        decode(root.optString(key))?.let { vectors[key] = it }
                    }
                }
            } catch (_: Throwable) {
            }
            loaded = true
        }
    }

    suspend fun persist() = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject()
            vectors.forEach { (key, vector) -> encode(vector)?.let { root.put(key, it) } }
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(root.toString())
                tmp.delete()
            }
        } catch (_: Throwable) {
        }
    }

    companion object {
        private const val CACHE_VERSION = "v1"

        fun archetypeKey(id: String) = "$CACHE_VERSION:a:$id"

        fun justificationKey(category: String, index: Int) = "$CACHE_VERSION:j:$category:$index"

        fun appKey(packageName: String, versionCode: Long, textHash: Int) =
            "$CACHE_VERSION:p:$packageName:$versionCode:$textHash"

        private fun encode(vector: FloatArray): String? = try {
            val buffer = ByteBuffer.allocate(vector.size * 4)
            buffer.asFloatBuffer().put(vector)
            Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
        } catch (_: Throwable) {
            null
        }

        private fun decode(encoded: String?): FloatArray? = try {
            if (encoded.isNullOrEmpty()) {
                null
            } else {
                val bytes = Base64.decode(encoded, Base64.NO_WRAP)
                val floats = FloatArray(bytes.size / 4)
                ByteBuffer.wrap(bytes).asFloatBuffer().get(floats)
                floats
            }
        } catch (_: Throwable) {
            null
        }
    }
}
