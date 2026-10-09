# Validation and known limitations

Device: physical Infinix X670 / Note 12 G96, Android 13 (API 33), ARM64. Validation dates: 2026-10-09 and 2026-10-10, Asia/Manila. The original React/FastAPI project was preserved. The first native Date-of-Birth slice was built and tested before the expanded workflow.

## Offline procedure

The phone was connected by authorized USB debugging, unlocked by the user and kept awake while USB-powered. Airplane mode was enabled and `cmd wifi status` confirmed Wi-Fi disabled. Android's `wifi_on` saved setting can show 2 in airplane mode; it is not used alone as proof of connection status. No server, laptop inference or model download is used by the installed app. USB provides installation/test control only. Release manifests are inspected separately for absence of INTERNET and ACCESS_NETWORK_STATE permissions.

Tests use actual CameraX frames, bundled ML Kit, real Room and real ONNX inference. The bundled blank PSA image and the user's identified blank cropped parent section are processed as images, without substituted OCR or hardcoded recognition results. Camera testing checks capture/preview/OCR/recovery behavior; it does not claim a scene-specific accuracy score. No personal camera-test photograph or OCR text is distributed.

## Build and workflow evidence

- `evidence/slice-device-tests.txt` and `slice-integration-tests.txt`: first native slice, actual bundled OCR and source-backed Date-of-Birth guidance.
- `evidence/bge-probe-build.txt`: expanded Compose/domain/shared-ONNX compilation and unit checks for all three flavors.
- `evidence/redesign-semantic-device-tests.txt`: initial redesigned device run. The two workflow tests, camera test and full PSA slice passed. This run also exposed a test-class selector typo, an overly optimistic MiniLM suggestion fixture and the real mother-section boundary bug; it is **not** an all-passing final run.
- `evidence/parent-section-public-golden.json`: actual ML Kit text/coordinates from the user's blank photo. These coordinates drive regression tests; production OCR does not save text to disk.
- `evidence/final-unit-lint.txt`: all 148 meaningful JVM checks passed (46 matching/layout cases in each flavor, plus five tokenizer cases in each encoder flavor). Android lint passed for all three variants. Template 2+2/package-name tests were removed.
- `evidence/enhanced-device-tests.txt`: an intermediate run passed 11 non-UI tests; three UI tests were blocked by a locked screen. `enhanced-ui-device-tests.txt` records their unlocked rerun. Wi-Fi was active during these intermediate runs, so they establish functionality rather than strict offline operation.
- **`evidence/final-airplane-device-tests.txt`: final strict offline run, all 14 tests passed in 45.811 seconds.** Airplane mode was 1 and actual Wi-Fi status was disabled both before and after. This covers real camera capture/preview/recovery, guide navigation/language/activity recreation, the user's cropped image, full-source OCR, image handling, Room updates and actual BGE production inference.
- `evidence/minilm-device-tests.txt`: four actual MiniLM production-engine checks passed. The final enhanced run uses BGE, not a substituted compact model.
- `evidence/design-lint.txt`: official @google/design.md 0.4.0 validator, zero errors/warnings. Its JavaScript entry point is invoked directly to avoid Windows interpreting `design.md` as a document filename.
- `evidence/premium-audit.json`: deterministic design/interaction ownership audit, zero findings. This check does not substitute for visual or accessibility testing.

A test's presence in source is not evidence that it passed. Intermediate failures are retained to show what was corrected; the strict final result above is authoritative for offline device coverage.

## Observed performance

These are individual physical-device observations, not a controlled benchmark or a guarantee for every image, device temperature, language or form.

| Workload | Observed result |
|---|---|
| Full bundled blank PSA image, 1584 × 1224, final strict offline run | Preparation 92 ms; OCR 1004 ms; initial Room/matching 209 ms; 94 OCR lines and 20 matched fields; source PDF SHA-256 valid. |
| Previous pre-optimization full-image run | Room/matching 3414 ms. Catalogue caching and prepared label/alias parsing reduced work; the runs were not thermally controlled. |
| MiniLM int8 actual model | First encoding/session 424 ms; warm encoding 7 ms; loaded PSS 237,220 KiB. |
| BGE large FP32 compatibility probe | Hash verification 1611 ms; fresh session + first encoding 6359 ms; warm encoding 104 ms. Baseline PSS 167,479 KiB; loaded 1,507,638 KiB; sampled peak 1,525,994 KiB; after closing 204,106 KiB. |
| User's actual blank parent crop, 697 × 429, final strict offline run | OCR 363 ms; initial Room/matching 241 ms; 21 lines, seven exact general matches, six name fields correctly split three Father / three Mother. No full-form identity or source-specific field was claimed. |
| Final enhanced production engine, strict offline run | Fresh session/first encoding 6607 ms; warm 104 ms; loaded PSS 1,515,021 KiB. Real confirmation-required Home → Residential-address suggestion cosine 0.8777038; PSS after suggestion/session release 206,281 KiB. |

PSS is process proportional set size, including app/runtime/test overhead, sampled during a compatibility probe; it is not just model tensor allocation and may miss brief peaks. BGE's public-reference vector cache and session-release policy are verified through the final production-engine tests, separately from this probe. The enhanced model costs approximately 1.34 GB in the APK plus another model copy in private no-backup storage after first use. The lightweight flavor retains the same OCR, layout rules and guide without encoder overhead.

## Actual models versus rules

Exact aliases, conservative fuzzy spelling, independent form anchors and explicit person-section boundaries are deterministic rules. ML Kit text extraction and ONNX vector inference are machine learning. All explanations/examples are authored catalogue content, never generated by an LLM. Semantic suggestions require user confirmation and cannot establish government identity, requirements, eligibility, fees or acceptance.

The published BGE FP16 export was checksum-verified but failed a real ONNX device load with an internal tensor type mismatch. It is excluded from the deliverable; its failure is retained in `evidence/bge-fp16-probe.txt`. The pinned FP32 export passed actual CPU inference; see `evidence/bge-probe.txt` and [ENHANCED_MODEL.md](ENHANCED_MODEL.md).

The FP32 probe produced 1024 finite normalized values and repeatable vectors. Related labels ranked above unrelated labels: postal/ZIP 0.7847 versus postal/birth-date 0.5914; home/residential-address 0.8777 versus home/birth-date 0.6627. These limited checks demonstrate working inference and sensible example ordering. They do **not** establish broad field-recognition accuracy. Some related text remains below the deliberately conservative suggestion gate; e.g. mobile/phone 0.7296 is declined at the BGE 0.80 gate.

## Incomplete or unverified

- Filipino/Taglish and English wording still lack independent human linguistic review. Source-backed PSA meaning does not imply verified translation.
- Cebuano, Ilocano, Hiligaynon and Bikol explanations are unavailable until reviewed content exists. Disabled language choices state this honestly.
- Arbitrary government/private form coverage, handwriting, severely blurred/glared photos and filled-answer accuracy have not been validated. The app explains labels and does not check answers or completeness.
- No broad labelled evaluation set exists for fuzzy/semantic precision or recall. Thresholds are uncalibrated suggestion gates, not accuracy percentages.
- TalkBack user testing and a broad device/Android-version matrix remain incomplete. Native semantics and large targets are implemented; visual/font-scale checks are recorded separately when performed.
- Distribution uses the Android development signing key. Public/store release requires the owner's production signing key, release governance and content/language review.

## Reproduce

Build with the checked-in Gradle wrapper, JDK 21 and Android SDK 36.1. Install the chosen APK. Unlock the physical phone normally, enable airplane mode, disable Wi-Fi and verify actual Wi-Fi status. Run the documented instrumentation tasks or manually take a clear photo, inspect the preview, run OCR, select a supported language and compare each original label/photo with its explanation. Confirm or skip uncertain suggestions. Change language and recreate the activity while partway through the guide; the current item and manual comparison must remain. Finish must say only that the guide ended. Enable optional English label recognition in About to exercise the installed encoder.

Radio/power settings before the 2026-10-10 run were airplane mode off, Wi-Fi on and stay-awake 0. The temporary font-scale check uses 1.3 instead of the original 1.0; the original screen timeout is 600000 ms. Restoration is recorded after the final release smoke test. Final file sizes and SHA-256 checksums are supplied in `deliverables/manifest.json` and `SHA256SUMS.txt`.
