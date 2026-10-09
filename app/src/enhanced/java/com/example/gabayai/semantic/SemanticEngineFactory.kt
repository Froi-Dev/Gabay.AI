package com.example.gabayai.semantic

import android.content.Context

object SemanticEngineFactory {
    fun create(context: Context): SemanticEngine = OnnxSemanticEngine(context.applicationContext, SemanticModel.BGE_LARGE)
}
