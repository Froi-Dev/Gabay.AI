package com.example.gabayai.semantic

internal enum class Pooling { MEAN, CLS }

/** Immutable contracts for publisher files. The APK flavor determines the one available encoder. */
internal data class SemanticModel(
    val displayName: String,
    val assetDirectory: String,
    val assetFileName: String,
    val cacheFileName: String,
    val bytes: Long,
    val sha256: String,
    val dimensions: Int,
    val pooling: Pooling,
    val minimumCosine: Float,
    val minimumMargin: Float,
    val releaseSessionAfterSuggestion: Boolean = false,
) {
    companion object {
        val MINILM = SemanticModel(
            "MiniLM L6 int8", "models/minilm", "model_qint8_arm64.onnx", "minilm-arm64-int8.onnx",
            23_026_053L, "4278337fd0ff3c68bfb6291042cad8ab363e1d9fbc43dcb499fe91c871902474",
            384, Pooling.MEAN, 0.62f, 0.08f,
        )
        val BGE_LARGE = SemanticModel(
            "BGE large v1.5 FP32", "models/bge", "model.onnx", "bge-large-fp32.onnx",
            1_336_749_542L, "deb82329ff0e9725a0d8407e434be40a7793725ce4e3032d8091f615130d0105",
            1024, Pooling.CLS, 0.80f, 0.08f,
            releaseSessionAfterSuggestion = true,
        )
    }
}
