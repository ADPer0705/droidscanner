---
type: architecture
title: On-Device ML Purpose Inference
description: >-
  Documents DroidScanner's offline MediaPipe Universal Sentence Encoder pipeline,
  archetype and permission-justification matching, bounded scoring guardrails, and
  atomic EmbeddingStore vector caching.
tags: [ml, mediapipe, embeddings, purpose-inference, caching]
verified:
  - by: openwiki/0.9.0
    at: 2026-11-23T19:21:54.091Z
sources:
  - id: openwiki-source-6081837d7dbf0dfab71b4fdd
    resource: repo://app/src/main/java/com/idiots3/riskscanner/engine/RiskScorer.kt
  - id: openwiki-source-f3e048ec11ca6c72cd45dc1d
    resource: repo://app/src/main/java/com/idiots3/riskscanner/engine/ScoringConfig.kt
  - id: openwiki-source-e9b5ae0ab3c35045d5ea576e
    resource: repo://app/src/main/java/com/idiots3/riskscanner/ml/EmbeddingStore.kt
  - id: openwiki-source-37f838ca8050d54ee2a33882
    resource: repo://app/src/main/java/com/idiots3/riskscanner/ml/PurposeInferenceEngine.kt
generated: { by: "antigravity", at: "2026-11-23T19:21:54.091Z" }
---

# On-Device ML Purpose Inference

DroidScanner includes a secondary, strictly bounded on-device ML signal that asks whether an app's own metadata plausibly explains its granted sensitive permissions. It runs 100% offline using MediaPipe Tasks Text and a bundled Universal Sentence Encoder (`universal_sentence_encoder.tflite`, ~6 MB, configured with `noCompress += "tflite"` in `app/build.gradle.kts` so it can be memory-mapped).

## Components & Data Flow

1. **`TextEmbedderEngine`** (`app/src/main/java/com/idiots3/riskscanner/ml/TextEmbedderEngine.kt`):
   - Lazily initializes MediaPipe `TextEmbedder` with `setL2Normalize(true)` to produce 512-dimension float embeddings.
   - Thread-safe `ensureInitialized()` and `close()` allow `MainViewModel` to release the native TFLite runtime after each scan and re-open it cleanly on subsequent scans.
2. **`PurposeInferenceEngine`** (`app/src/main/java/com/idiots3/riskscanner/ml/PurposeInferenceEngine.kt`):
   - Loads `app/src/main/assets/purpose_archetypes.json` containing 40 app-function archetypes (each with `id`, `label`, `text`, and `plausible` `PermissionCategory` set) and per-category permission justification sentences.
   - `prepare()` loads the knowledge base once, calls `embedder.ensureInitialized()`, and warms archetype/justification vectors into `EmbeddingStore` once (`vectorsWarmed`). Even after `releaseModel()` closes the native handle, subsequent `prepare()` or `appVector()` calls re-initialize the embedder safely.
   - `buildAppText(app)` constructs the input strictly from public app metadata: `appName`, filtered package-name tokens (excluding generic noise like `com`, `android`, `app`), and `androidCategory`. Never inspects file contents, messages, or accounts.
3. **`EmbeddingStore`** (`app/src/main/java/com/idiots3/riskscanner/ml/EmbeddingStore.kt`):
   - Persists base64-encoded float vectors in `filesDir/embedding_cache.json` under versioned keys (`v1:a:<id>`, `v1:j:<category>:<index>`, `v1:p:<pkg>:<versionCode>:<textHash>`).
   - `persist()` writes atomically via a `.tmp` sibling file followed by `renameTo()`. Excluded from Android cloud backup and device transfer.

## Purpose-Fit Classification & Bounded Scoring

For each sensitive `PermissionCategory` present on an app (excluding `OTHER`), `PurposeInferenceEngine.assess(app)` computes cosine similarity against:
- All 40 archetypes (selecting the highest-similarity `best` archetype).
- All justification descriptions for that permission category (`bestSimilarity`).

Classification rules per category:
- **`FITS`**: `(best.second >= 0.40f && best.first.plausible.contains(category) && bestSimilarity >= 0.15f)` OR `bestSimilarity >= 0.45f` (`ScoringConfig.AI_FIT_THRESHOLD`).
- **`UNCLEAR`**: `bestSimilarity >= 0.30f` (`ScoringConfig.AI_UNCLEAR_THRESHOLD`).
- **`UNEXPLAINED`**: Below `0.30f`.

### Conservative Guardrails

- **Granted-only penalty:** Only `UNEXPLAINED` categories with at least one **granted** sensitive permission are included in `unexplainedCategories`; declared-only permissions receive UI fit chips without inflating the score.
- **Bounded delta:** `RiskScorer` adds `0` AI points if `rulesScore < 26` (`AI_MIN_RULE_SCORE`). Above that threshold, `aiDeltaFor()` adds `+2` (or `+3` for SMS / Contacts & Call Logs) for 1 unexplained category, scaling up to a hard cap of `+8` (`AI_MAX_DELTA`).
- **Never alone Critical:** If `rulesScore < 76` (`AI_CRITICAL_FLOOR`), `RiskScorer` caps the final score at `75`, ensuring the AI signal can never push an app into `Critical` by itself.
- **Non-probabilistic framing:** Similarity values are cosine similarities in embedding space, never malware probabilities. Background WorkManager runs intentionally skip this pass to preserve battery and RAM on consumer devices.

## Related Pages

- [Architecture, Trust & Background Guard](../architecture/overview.md)
- [Risk Scoring v3, Impersonation & Recommendations](../scoring/risk-and-recommendations.md)
