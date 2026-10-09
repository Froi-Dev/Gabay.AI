package com.example.gabayai.semantic

import com.example.gabayai.domain.FieldDefinition
import com.example.gabayai.domain.FieldMatch
import com.example.gabayai.domain.OcrLine

enum class SemanticProgress { PREPARING_MODEL, MATCHING }
enum class SemanticResourceProblem { STORAGE, MEMORY }
class SemanticResourceException(val problem: SemanticResourceProblem) : IllegalStateException("Optional model resources: $problem")

/** Optional local embeddings. Implementations never confirm a field or generate an explanation. */
interface SemanticEngine : AutoCloseable {
    val available: Boolean
    val modelName: String? get() = null
    val modelStorageBytes: Long get() = 0L

    /** Only send rule-unmatched labels. Returned suggestions always require explicit confirmation. */
    suspend fun suggest(lines: List<OcrLine>, fields: List<FieldDefinition>, onProgress: (SemanticProgress) -> Unit = {}): List<FieldMatch>

    /** Rejects further work immediately; an in-flight native call releases resources on return. */
    override fun close()
}
