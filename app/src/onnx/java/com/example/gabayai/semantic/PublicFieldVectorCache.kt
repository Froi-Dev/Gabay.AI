package com.example.gabayai.semantic

import android.content.Context
import com.example.gabayai.domain.FieldDefinition
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs

/** Caches only authored field-label reference vectors. OCR text and user-image vectors never enter it. */
internal class PublicFieldVectorCache(context: Context, private val model: SemanticModel) {
    private val directory = File(context.noBackupFilesDir, "public-field-vectors-v1/${model.sha256}")

    fun read(field: FieldDefinition): FloatArray? {
        require(field.formId == null)
        val file = file(field)
        if (!file.isFile || file.length() != 8L + model.dimensions * 4L) return null
        return runCatching {
            DataInputStream(file.inputStream().buffered()).use { input ->
                require(input.readInt() == MAGIC && input.readInt() == model.dimensions)
                FloatArray(model.dimensions) { input.readFloat() }.also(::validate)
            }
        }.getOrNull()
    }

    /** Best-effort optimization. Storage failure must never discard a valid local inference result. */
    fun write(field: FieldDefinition, vector: FloatArray) {
        require(field.formId == null)
        validate(vector)
        runCatching {
            if (!directory.isDirectory && !directory.mkdirs()) return@runCatching
            val file = file(field)
            val pending = File(directory, "${file.name}.pending")
            try {
                DataOutputStream(pending.outputStream().buffered()).use { output ->
                    output.writeInt(MAGIC)
                    output.writeInt(model.dimensions)
                    vector.forEach(output::writeFloat)
                }
                if (!file.exists() || file.delete()) pending.renameTo(file)
            } finally { pending.delete() }
        }
    }

    private fun file(field: FieldDefinition): File {
        val key = "${field.id}\u0000${field.label}".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(key)
        return File(directory, digest.joinToString("") { "%02x".format(it) } + ".vec")
    }

    private fun validate(vector: FloatArray) {
        require(vector.size == model.dimensions && vector.all(Float::isFinite))
        val squaredMagnitude = vector.sumOf { it.toDouble() * it }
        require(abs(squaredMagnitude - 1.0) < 0.001)
    }

    companion object { private const val MAGIC = 0x47425631 }
}
