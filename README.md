# Gabay.AI — native offline Android

Separate Kotlin/Compose migration of the reference at `C:/Users/dever/OneDrive/Desktop/AppBuilders`. The reference application is preserved. Target: Infinix Note 12 / connected Infinix X670, Android 13, ARM64. Minimum Android 7.0 (API 24); this distribution packages ARM64 only.

## Install

Install `deliverables/GabayAI-enhanced.apk` for the requested build exceeding 500 MB, with a real bundled BGE large English encoder. Its size comes from usable model weights, not padding. `deliverables/GabayAI-light.apk` retains the same OCR and guide with deterministic matching; `deliverables/GabayAI-semantic.apk` adds compact MiniLM embeddings. They have separate application IDs and can coexist. All include their inference assets; none needs internet, a laptop, FastAPI, Ollama or a cloud API after installation. The larger encoder does not improve ML Kit's text extraction or verify government policy. Consult the measured device cost in `docs/VALIDATION.md` before enabling optional inference.

APKs are optimized and signed with the Android development key for installation/testing. Use your own release signing key for public/store distribution. Complete buildable source is in this directory and `deliverables/GabayAI-source.zip`.

## Use

Take Photo → confirm preview → actual bundled offline OCR → choose explanation language → one original field label and explanation at a time. Choose Photo is secondary. The blank PSA sample is an actual bundled image processed by the same OCR path, not a fake result. Retake, retry, uncertainty confirmation, original-photo highlights/zoom, manual comparison and finish/restart are implemented.

The catalogue contains six source-backed PSA birth-application meanings and 24 common field kinds (112 original aliases, 60 English/Filipino explanation records). Repeated parent/child/requester labels use conservative spatial checks. Readable printed parent headings identify whose name is being discussed; uncertain or distant headings retain neutral wording. A cropped form can receive general guidance without a known identity. Unknown text remains unknown. No answers, required status, completeness, eligibility, fees or official acceptance are inferred.

Filipino/Taglish and English wording are available. **Human language review is pending.** Cebuano, Ilocano, Hiligaynon and Bikol are visibly unavailable because the reference contains no reviewed translations. The application never claims that these translations exist or are verified. A source-backed meaning is distinct from a reviewed translation.

Exact, fuzzy and section-layout matching are **rules**, not AI. Bundled ML Kit OCR is machine learning. Both encoder builds contain real ONNX inference, default off and supply only confirmation-required suggestions after rules are insufficient. Enable it under About Offline Guidance; the installed model's name is shown there. These encoders are English and matching accuracy across arbitrary forms is uncalibrated.

## Build and test

Use Android Studio with JDK 21, Android SDK 36.1 and the checked-in Gradle wrapper. Initial dependency resolution needs build-machine internet; APK runtime does not. On this Windows machine:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleBaseRelease :app:assembleSemanticRelease :app:assembleEnhancedRelease --max-workers=2
.\gradlew.bat :app:testBaseDebugUnitTest :app:testSemanticDebugUnitTest :app:testEnhancedDebugUnitTest
.\gradlew.bat :app:connectedBaseDebugAndroidTest :app:connectedSemanticDebugAndroidTest :app:connectedEnhancedDebugAndroidTest
```

Use `base` for OCR/rules only, `semantic` for MiniLM and `enhanced` for BGE large. Model/tokenizer assets are already in the respective flavor asset directories; no download script is required to build the supplied source. Shared inference code is in `app/src/onnx/java`. `local.properties` is machine-specific and excluded from the source archive; set the SDK location in Android Studio on another computer.

Physical tests require a connected, unlocked Android device. CameraCaptureTest uses its real rear camera and does not assume the photographed scene contains a particular field. Public-image tests check actual OCR/matching correctness independently. Device tests do not toggle radios automatically; use the documented airplane-mode procedure in `docs/VALIDATION.md`.

## Architecture and privacy

Compose + Material 3 and Navigation Compose → ScanViewModel/StateFlow → repositories for CameraX photo preparation, ML Kit, Room definitions, DataStore preferences and optional ONNX embeddings. Image decoding/inference/matching run off main. Camera is lifecycle-bound and released on navigation/errors. Cancellation drains native work before replacing session state. Displayed bitmaps are released through reference removal/GC, avoiding recycle races with Compose.

Photos are app-private temporary cache files. Original orientation is applied once, long edge is bounded to 2600px and location metadata is stripped. Inputs are bounded to 30MiB/60MP; normal camera capture requests approximately 2560×1920. The same upright image drives preview, OCR and highlights. Public reference definitions and language/settings are persistent; personal images, OCR and answers never enter Room/DataStore/logs. No document history or background service exists. Backup/transfer are excluded and the merged manifest removes INTERNET and ACCESS_NETWORK_STATE.

## Documentation

- [Migration map](docs/MIGRATION_MAP.md): inspected functionality and replacements, written before implementation.
- [Offline dependencies](docs/OFFLINE_DEPENDENCIES.md): bundled OCR, runtime, model and library inventory.
- [Semantic model](docs/SEMANTIC_MODEL.md): weights/tokenizer hashes, licenses, pooling, limits and inference behavior.
- [Validation](docs/VALIDATION.md): exact physical-device results, performance observations and remaining gaps.
- [Design](DESIGN.md) and [interaction contract](UX-CONTRACT.md).

This is an installable migration pilot. Regional translations, independent language review, broad form accuracy/latency coverage and TalkBack usability review remain release work. General glossary content is not institution-verified policy.
