# BGE enhanced offline build

The `enhanced` flavor is the requested APK above 500 MB. It contains one genuine BGE large sentence embedding model, rather than padding or duplicate models. It retains the same bundled ML Kit OCR, Room catalogue, deterministic matching and reviewed-source metadata as the smaller flavors. Optional semantic matching starts disabled and runs only after exact and fuzzy matching leave eligible labels unmatched.

This encoder does not make OCR sharper or translate explanations. The crop supplied by the user needs correct parent-section attribution and presentation of the original labels; those changes belong to the layout rules and guide interface. A larger encoder can suggest a known English label when wording differs. Its accuracy on this project's full set of forms has not been established.

| Property | Value |
| --- | --- |
| Upstream model | `BAAI/bge-large-en-v1.5` |
| ONNX publisher | `onnx-community/bge-large-en-v1.5-ONNX` |
| Pinned ONNX revision | `2fb1aa0d84fb0a59a4273cce47d90c7d2f7264bb` |
| Publisher file | `onnx/model.onnx` |
| Actual model size | 1,336,749,542 bytes |
| SHA-256 | `deb82329ff0e9725a0d8407e434be40a7793725ce4e3032d8091f615130d0105` |
| Weight changes | None; publisher's FP32 ONNX file |
| Architecture | BERT encoder, 24 layers, hidden size 1024, 16 attention heads |
| Vocabulary | 30,522 BERT uncased WordPiece tokens |
| Vocabulary SHA-256 | `07eced375cec144d27c900241f3e339478dec958f92fddbc551f295c992038a3` |
| Language | English |
| Output | 1024 finite floats, CLS pooling, L2 normalization |
| Application input bound | 64 tokens including CLS and SEP; no silent truncation |
| License | MIT, declared by both publisher model cards |
| Native runtime | ONNX Runtime Android 1.24.3, ARM64 CPU |

[BAAI model card and pooling instructions](https://huggingface.co/BAAI/bge-large-en-v1.5), [pinned ONNX repository](https://huggingface.co/onnx-community/bge-large-en-v1.5-ONNX/tree/2fb1aa0d84fb0a59a4273cce47d90c7d2f7264bb), [pinned weight file](https://huggingface.co/onnx-community/bge-large-en-v1.5-ONNX/resolve/2fb1aa0d84fb0a59a4273cce47d90c7d2f7264bb/onnx/model.onnx).

The original card, configuration, tokenizer files, vocabulary and machine-readable provenance ship in `app/src/enhanced/assets/models/bge`. The MIT text from the upstream FlagEmbedding project, ONNX Runtime's MIT license and third-party notices ship in `app/src/enhanced/assets/licenses`. The model card declares the weights' license; the bundled upstream license text preserves its original copyright notice.

The enhanced APK does not include MiniLM weights. `src/onnx/java` contains the shared tokenizer and bounded inference implementation; factories choose the model contract for each flavor. BGE uses the upstream CLS pooling instructions. It does not use MiniLM's mean pooling or copy its cosine gate: the BGE suggestion gate is cosine >= 0.80 with a margin >= 0.08. These values are uncalibrated filters, not probabilities. Every suggestion requires confirmation and source-specific government roles are excluded from model candidates.

Model extraction streams through a 64 KiB buffer into app-private no-backup storage and checks size and SHA-256. It requires another 1,336,749,542 bytes of storage after first use. The file is not loaded into a Java byte array. CPU inference uses two intra-operation threads, one inter-operation thread, no NNAPI or GPU, no CPU arena, and no memory pattern optimization. BGE releases its model session after each suggestion operation, including cancellation and failure, to avoid retaining the large model while users read the guide or open the camera. A native inference already in progress must finish before resources can be released. The shared process-wide ONNX environment is not closed by the engine.

Only authored public catalogue label vectors are cached, keyed by model SHA-256 and the field ID/label hash. Changing the model or label invalidates that reference. The cache validates dimensions, finite values and normalization before use. It stores no OCR text, image content or user-query embeddings. This avoids repeatedly encoding the same catalogue while allowing the large native session to close.

The publisher's 668,718,129-byte FP16 graph was tested first and failed actual ONNX Runtime CPU loading because an intermediate Cast had conflicting float16/float type annotations. Its failure log is retained as `docs/evidence/bge-fp16-probe.txt`; those failed weights are excluded from the app and source package. The full-precision file above was then checksum-verified and passed the physical Infinix X670 CPU probe in airplane mode with Wi-Fi disabled. The fresh session plus first encoding took 6,359 ms; a warm encoding took 104 ms. Sampled peak PSS was 1,525,994 KiB, falling to 204,106 KiB after closure. Model hashing took a separate 1,611 ms. These are one recorded research run, not guaranteed timings or whole-workflow accuracy measurements. The probe log is `docs/evidence/bge-probe.txt`; final production-engine and APK results belong in `docs/VALIDATION.md`.

`BgeModelProbeTest` is a compatibility research test in the semantic test APK. It reads the checksum-verified model transferred by ADB through `run-as` to that test target's private app files, then measures a fresh session, a warm encoding, sampled PSS and related-label ordering. It is skipped when that manual research fixture is absent. `OfflineSemanticTest`, shared by the semantic and enhanced instrumentation source sets, exercises the actual flavor factory and bundled production engine, checks normalized dimensions and related-label ordering, runs a real suggestion, verifies mandatory confirmation and rejected directives/values, and verifies closure. Airplane mode with Wi-Fi disabled is required for the recorded device runs.
