# Bundled inference and offline dependency inventory

No application HTTP client, server, Python runtime, LLM, Ollama, Play-services model-download OCR variant, online fonts or cloud inference is used. The merged manifest explicitly removes internet/network-state permissions inherited from libraries. Build-time Maven resolution is independent of phone runtime.

| Component | Pinned direct dependency / asset | Purpose and offline behavior |
|---|---|---|
| OCR model | `com.google.mlkit:text-recognition:16.0.1` | Bundled Latin Text Recognition v2; its native OCR pipeline/model is packaged in the APK and immediately available. No dynamically downloaded `play-services-mlkit-text-recognition` dependency. ML-powered; no handwriting guarantee. |
| Optional compact semantic model | MiniLM L6 v2 ARM64 int8, 23,026,053 bytes | Semantic APK. English sentence encoder with real inference; weights and vocabulary/license/provenance bundled. See SEMANTIC_MODEL.md for exact SHA256 and pinned upstream revision. |
| Optional large semantic model | BGE large v1.5 FP32 ONNX, 1,336,749,542 bytes | Enhanced APK. English encoder, 1024-dimensional CLS pooling. This real model supplies the requested >500 MB payload. No additional filler or duplicate MiniLM weights are included. Larger weights do not alter OCR or verify authored content. See ENHANCED_MODEL.md for provenance and CPU limitations. The published FP16 export failed its device graph-load test and is excluded. |
| Optional inference runtime | `com.microsoft.onnxruntime:onnxruntime-android:1.24.3` | Android JNI/ARM64 CPU runtime, MIT; semantic/enhanced APKs. Full Android runtime rather than a custom operator-pruned runtime, so this adds substantial size. |
| UI | Compose BOM `2026.02.01`, Material 3, material-icons-extended, activity-compose `1.8.0` | Native UI, fonts through Android, no web view. Only used icon code is retained by optimized release shrinking. Resolved transitive versions are in dependency reports. |
| Lifecycle/MVVM | lifecycle-runtime-compose and lifecycle-viewmodel-compose `2.9.4` | StateFlow collection and lifecycle-aware native state. |
| Navigation | navigation-compose `2.9.8` | Local workflow navigation; no network routes. |
| Camera | CameraX camera-camera2/lifecycle/view `1.5.3` | Lifecycle-bound rear-camera preview and JPEG capture. |
| Content | Room runtime/ktx/compiler `2.8.4` | SQLite public form/field/alias/explanation data, transactional seed; compiler is build-only. Uses Android system SQLite. |
| Preferences | DataStore preferences `1.1.7` | Language and semantic opt-in only. |
| Image orientation | ExifInterface `1.4.1` | Worker-thread orientation/metadata handling. |
| Concurrency | coroutines-android/play-services `1.10.2` | Worker dispatch, Flow and ML Kit Task completion; no remote call implied by Task adapter. |
| Build-only | AGP `9.3.3`, Gradle `9.5.0`, Kotlin/Compose plugin `2.2.10`, KSP `2.2.10-2.0.2`, JDK 21 | Not packaged as a development runtime on the phone. KSP compatibility uses documented `android.disallowKotlinSourceSets=false`. |

The exact complete Maven dependency trees, including bundled transitive Android/Google dependencies, are saved as `docs/evidence/dependencies-base.txt`, `dependencies-semantic.txt` and `dependencies-enhanced.txt`. APK inspections in VALIDATION.md verify the base has no encoder/ONNX payload and all variants lack internet permission. Tests, JUnit, Compose tooling and instrumentation libraries are debug/test dependencies, not optimized release runtime features.

## Public content provenance

`app/src/main/assets/source/psa-birth.pdf` is the audited reference PDF, SHA256 `c777a6389c78cd6fa1fd96281b8094e1a0dba7abe41d0672749892dd70a24060`. Reference source URL: [PSA birth application](https://crs-appointment.psahelpline.ph/forms/birth-new.pdf). The local copy supplies field-label evidence; its presence does not prove that requirements remain current or the catalogue is complete. Metadata date 2026-10-09 is project source review, not independent linguistic signoff.

The blank PSA PNG and supplied logo derivative are public reference assets. The logo is deterministically cropped/resized from the supplied brand presentation by `scripts/package_brand.py`; no replacement branding is invented. User-supplied blank parent-section image is a **test-only fixture**, not bundled as a supposed verified form. Personal camera-test photographs are not distributed.

The 24 general vocabulary entries and examples come from audited `backend/general_forms.py`. The six PSA entries come from `backend/templates.py`; fictional examples come from `frontend/src/field-guidance.ts`. The unsafe general Filipino middle-name assertion about a mother's premarital surname was replaced with neutral wording equivalent to the existing English. Original labels/qualifiers remain visible. No regional translations were invented; every current explanation is marked NOT_LINGUISTICALLY_REVIEWED.

Google's [bundled OCR documentation](https://developers.google.com/ml-kit/vision/text-recognition/v2/android), [CameraX releases](https://developer.android.com/jetpack/androidx/releases/camera), [Room releases](https://developer.android.com/jetpack/androidx/releases/room) and [ONNX mobile deployment documentation](https://onnxruntime.ai/docs/tutorials/mobile/) were consulted for integration. Model/runtime licenses and third-party notices are packaged in both encoder flavor asset directories.
