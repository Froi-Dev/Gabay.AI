package com.example.gabayai.semantic

import android.content.Context
import com.example.gabayai.domain.FieldDefinition
import com.example.gabayai.domain.FieldMatch
import com.example.gabayai.domain.OcrLine

object SemanticEngineFactory {
    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context): SemanticEngine = object : SemanticEngine {
        override val available = false
        override suspend fun suggest(lines: List<OcrLine>, fields: List<FieldDefinition>, onProgress: (SemanticProgress) -> Unit): List<FieldMatch> = emptyList()
        override fun close() = Unit
    }
}
