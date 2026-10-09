# Optional local semantic suggestions

The `base` APK contains bundled ML Kit OCR and deterministic field matching. It does not depend on ONNX Runtime or include the sentence embedding model. The `semantic` APK additionally includes an English sentence encoder and ONNX Runtime. Its semantic setting starts disabled. Both APKs work without a server or runtime model download. The separate `enhanced` flavor bundles a larger BGE encoder to meet the user's requested APK size; its independent model contract and limitations are documented in [ENHANCED_MODEL.md](ENHANCED_MODEL.md).

The field pipeline runs exact and fuzzy rules first. Only unmatched OCR labels may reach the optional encoder. Label-like text filters are rules, not AI. Sentence embedding inference is the AI component. It creates vectors, compares known English field labels and returns suggestions; it generates no explanations, translations, requirements, or government policy. Every semantic suggestion requires explicit confirmation.

## Bundled model and provenance

| Item | Value |
| --- | --- |
| Publisher | Sentence Transformers |
| Model | `sentence-transformers/all-MiniLM-L6-v2` |
| Upstream file | `onnx/model_qint8_arm64.onnx` |
| Revision | `1110a243fdf4706b3f48f1d95db1a4f5529b4d41` |
| Model bytes | 23,026,053 |
| SHA-256 | `4278337fd0ff3c68bfb6291042cad8ab363e1d9fbc43dcb499fe91c871902474` |
| Vocabulary bytes | 231,508 |
| Vocabulary SHA-256 | `07eced375cec144d27c900241f3e339478dec958f92fddbc551f295c992038a3` |
| Model license | Apache-2.0, as declared by the publisher |
| Runtime | `com.microsoft.onnxruntime:onnxruntime-android:1.24.3` |
| Runtime license | MIT |
| Model changes | None |
| Output | 384-dimensional normalized float vector |

[Publisher model card](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2), [pinned model download](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/1110a243fdf4706b3f48f1d95db1a4f5529b4d41/onnx/model_qint8_arm64.onnx), [runtime artifact](https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/1.24.3/).

The original vocabulary, tokenizer configuration, special-token map, model card, machine-readable provenance, Apache license, runtime MIT license and runtime third-party notices are in `app/src/semantic/assets`. They are packaged in the optional APK. No application code downloads a model.

## Runtime behavior

The native model session loads lazily on a worker thread. The model is streamed from APK assets to the app's no-backup directory using a 64 KiB buffer. Size and SHA-256 are checked before use. The file-backed session avoids a 23 MB Java byte array. The copied model occupies an additional 23,026,053 bytes of app-private storage.

Inference uses the CPU with two intra-operation threads and one inter-operation thread. NNAPI and GPU delegates are not used. CPU memory arena and memory-pattern optimizations are disabled to bound retained scratch memory. The process-shared ONNX environment remains available; the engine owns and closes its session. Input tensors, result tensors and session options are closed after use. Closing the engine rejects new requests immediately and releases an in-flight session when its current native call returns. Normalized reference vectors for authored public catalogue labels may be cached in no-backup storage, keyed by model SHA and field ID/label hash. OCR text and user-query embeddings are never cached there. BGE additionally releases its large native session after every suggestion operation; MiniLM retains its compact session until engine closure.

Kotlin implements BERT uncased basic tokenization and greedy WordPiece using the publisher's unmodified vocabulary. Single-label input uses `[CLS]`, at most 62 label word pieces, and `[SEP]`; labels exceeding this limit are rejected instead of silently truncated. Attention-mask mean pooling includes all non-padding tokens, followed by L2 normalization. Every output must contain 384 finite values and have a valid nonzero magnitude.

The implementation independently limits input to the first 160 OCR lines, 12 label-like lines, 32 generic candidate fields and five suggestions. It excludes source-specific government fields. It rejects digits, likely entered values, headings, instructions, low OCR confidence and several directive patterns. These filters reduce risk but are not a proof that text is a field label.

A suggestion needs cosine similarity of at least 0.62 and a margin of at least 0.08 over the next candidate. These are conservative, uncalibrated application gates. Cosine similarity is not a probability, accuracy rate or confidence guarantee. Ambiguous or low-scoring text remains unmatched. Original OCR labels remain visible and users must confirm a suggestion before its explanation is treated as selected.

## Limits and verification

The publisher describes this model as English. Semantic accuracy in Filipino, Cebuano, Ilocano, Hiligaynon and Bikol has not been reviewed or validated. Explanations remain authored catalogue content with their own source and translation-review status; embeddings cannot make them verified. The model cannot establish form identity, required documents, fees, legal eligibility or the role of a person in a government form.

`testSemanticDebugUnitTest` checks tokenizer output against fixed IDs from the original vocabulary, Unicode normalization, continuation pieces and length rejection. `OfflineSemanticTest` under the shared `androidTestOnnx` source folder runs the actual flavor-bundled model and checks output shape, finite normalized values, repeated inference, related-versus-unrelated label ordering, mandatory confirmation, rejected instructions/values and closure. Instrumentation emits the first call for a fresh model session, a warm call, cosine values and loaded PSS. The first call can use an existing model file cache and is not a guaranteed first-install measurement. Device execution results and APK sizes belong in the project's validation report; the presence of these tests alone does not establish that they passed.
