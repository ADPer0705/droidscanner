package com.idiots3.riskscanner.ml

import android.content.Context
import com.idiots3.riskscanner.engine.ScoringConfig
import com.idiots3.riskscanner.model.AiAssessment
import com.idiots3.riskscanner.model.AppInfo
import com.idiots3.riskscanner.model.FitStatus
import com.idiots3.riskscanner.model.PermissionCategory
import com.idiots3.riskscanner.model.PermissionFit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sqrt

/**
 * On-device purpose inference.
 *
 * Compares an app's *own metadata* (label, package-name words, Android category) with a
 * curated knowledge base of app functions and permission-use descriptions using the
 * Universal Sentence Encoder. It answers one question per sensitive permission:
 * "does this app look like something that would plausibly need this?"
 *
 * Design rules (intentionally conservative):
 *  - cosine similarity is a similarity, never a probability;
 *  - "unexplained" is never phrased as malicious;
 *  - the deterministic rule engine remains authoritative — this only adds a small,
 *    clearly labelled signal and human-readable tags.
 */
class PurposeInferenceEngine(
    private val context: Context,
    private val embedder: TextEmbedderEngine,
    private val store: EmbeddingStore
) {

    data class Archetype(
        val id: String,
        val label: String,
        val text: String,
        val plausible: Set<PermissionCategory>
    )

    private val prepareLock = Any()

    @Volatile
    private var ready = false

    @Volatile
    private var modelOk = false

    private var knowledgeBaseLoaded = false
    private var archetypes: List<Archetype> = emptyList()
    private var justifications: Map<PermissionCategory, List<String>> = emptyMap()

    val isModelAvailable: Boolean
        get() = modelOk

    val isReady: Boolean
        get() = ready

    /** Loads the knowledge base and embeds it (cached on disk across launches). */
    suspend fun prepare(): Boolean = withContext(Dispatchers.Default) {
        ensureReady()
        modelOk
    }

    private fun ensureReady() {
        if (ready) return
        synchronized(prepareLock) {
            if (ready) return
            loadKnowledgeBase()
            modelOk = archetypes.isNotEmpty() && embedder.ensureInitialized()
            if (modelOk) {
                archetypes.forEach { archetype ->
                    ensureVector(EmbeddingStore.archetypeKey(archetype.id), archetype.text)
                }
                justifications.forEach { (category, texts) ->
                    texts.forEachIndexed { index, text ->
                        ensureVector(EmbeddingStore.justificationKey(category.name, index), text)
                    }
                }
            }
            ready = true
        }
    }

    /** Called after a scan so cached vectors survive into the next launch. */
    suspend fun flush() {
        store.persist()
    }

    /** Releases the native model handle; it is lazily re-created on the next scan. */
    fun releaseModel() {
        embedder.close()
    }

    suspend fun assess(app: AppInfo): AiAssessment = withContext(Dispatchers.Default) {
        if (!ready || !modelOk) {
            return@withContext AiAssessment(
                purposeLabel = null,
                purposeSimilarity = 0f,
                permissionFit = emptyMap(),
                unexplainedCategories = emptyList(),
                modelUsed = false,
                note = "On-device model unavailable — deterministic rules only"
            )
        }

        val text = buildAppText(app)
        if (text.isBlank()) {
            return@withContext AiAssessment(null, 0f, emptyMap(), emptyList(), modelUsed = false, note = "Not enough app metadata")
        }

        val appVector = appVector(app, text)
            ?: return@withContext AiAssessment(null, 0f, emptyMap(), emptyList(), modelUsed = false, note = "Embedding failed for this app")

        val best = archetypes
            .mapNotNull { archetype ->
                store.get(EmbeddingStore.archetypeKey(archetype.id))?.let { archetype to cosine(appVector, it) }
            }
            .maxByOrNull { it.second }

        val sensitiveCategories = app.permissions
            .filter { it.isSensitive && (it.isGranted || it.isDeclared) }
            .map { it.category }
            .distinct()
            .filter { it != PermissionCategory.OTHER }

        val fits = LinkedHashMap<PermissionCategory, PermissionFit>()
        for (category in sensitiveCategories) {
            val texts = justifications[category].orEmpty()
            var bestSimilarity = 0f
            texts.forEachIndexed { index, _ ->
                val vector = store.get(EmbeddingStore.justificationKey(category.name, index))
                if (vector != null) {
                    val similarity = cosine(appVector, vector)
                    if (similarity > bestSimilarity) bestSimilarity = similarity
                }
            }

            val matchesArchetype = best != null &&
                best.second >= ScoringConfig.AI_ARCHETYPE_TRUST_THRESHOLD &&
                best.first.plausible.contains(category)

            val status = when {
                matchesArchetype && bestSimilarity >= 0.15f -> FitStatus.FITS
                bestSimilarity >= ScoringConfig.AI_FIT_THRESHOLD -> FitStatus.FITS
                bestSimilarity >= ScoringConfig.AI_UNCLEAR_THRESHOLD -> FitStatus.UNCLEAR
                else -> FitStatus.UNEXPLAINED
            }

            val evidence = if (matchesArchetype) {
                "matches the app's detected function"
            } else {
                "best match ${formatSimilarity(bestSimilarity)} against ${texts.size} use descriptions"
            }
            fits[category] = PermissionFit(status, bestSimilarity, evidence)
        }

        val unexplained = fits.filterValues { it.status == FitStatus.UNEXPLAINED }.keys.toList()

        AiAssessment(
            purposeLabel = best?.first?.label,
            purposeSimilarity = best?.second ?: 0f,
            permissionFit = fits,
            unexplainedCategories = unexplained,
            modelUsed = true
        )
    }

    // ------------------------------------------------------------------ internals

    private fun appVector(app: AppInfo, text: String): FloatArray? {
        val key = EmbeddingStore.appKey(app.packageName, app.versionCode, text.hashCode())
        store.get(key)?.let { return it }
        val vector = embedder.embed(text) ?: return null
        store.put(key, vector)
        return vector
    }

    private fun ensureVector(key: String, text: String): FloatArray? {
        store.get(key)?.let { return it }
        val vector = embedder.embed(text) ?: return null
        store.put(key, vector)
        return vector
    }

    private fun buildAppText(app: AppInfo): String {
        val tokens = app.packageName
            .split('.', '_', '-')
            .filter { it.length > 2 && !NOISE_TOKENS.contains(it.lowercase()) }
        val parts = mutableListOf(app.appName)
        if (tokens.isNotEmpty()) parts += tokens.joinToString(" ")
        app.androidCategory?.let { parts += "$it app" }
        return parts.joinToString(". ")
    }

    /** Sentence vectors are L2-normalised by MediaPipe; the guard keeps cosine correct regardless. */
    private fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || b.isEmpty() || a.size != b.size) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator <= 0f) 0f else dot / denominator
    }

    private fun loadKnowledgeBase() {
        if (knowledgeBaseLoaded) return
        knowledgeBaseLoaded = true
        try {
            val json = context.assets.open(KB_ASSET).bufferedReader().use { it.readText() }
            val root = JSONObject(json)

            val array: JSONArray = root.optJSONArray("archetypes") ?: JSONArray()
            archetypes = (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val plausibleArray = obj.optJSONArray("plausible") ?: JSONArray()
                val plausible = (0 until plausibleArray.length())
                    .mapNotNull { i -> categoryOf(plausibleArray.optString(i)) }
                    .toSet()
                Archetype(
                    id = obj.optString("id"),
                    label = obj.optString("label"),
                    text = obj.optString("text"),
                    plausible = plausible
                )
            }

            val justificationObject: JSONObject = root.optJSONObject("justifications") ?: JSONObject()
            val map = LinkedHashMap<PermissionCategory, List<String>>()
            val keys = justificationObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val category = categoryOf(key) ?: continue
                val texts = justificationObject.optJSONArray(key) ?: continue
                map[category] = (0 until texts.length()).map { texts.optString(it) }
            }
            justifications = map
        } catch (_: Throwable) {
            archetypes = emptyList()
            justifications = emptyMap()
        }
    }

    private fun categoryOf(name: String): PermissionCategory? =
        PermissionCategory.entries.firstOrNull { it.name == name }

    private fun formatSimilarity(value: Float): String = "%.2f".format(value)

    private companion object {
        const val KB_ASSET = "purpose_archetypes.json"

        val NOISE_TOKENS = setOf(
            "com", "org", "net", "android", "app", "apps", "application", "mobile",
            "example", "main", "client", "free", "pro", "lite", "the", "and", "inc"
        )
    }
}
