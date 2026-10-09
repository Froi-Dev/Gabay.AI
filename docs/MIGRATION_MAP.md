# Gabay.AI native migration map

Audit date: 2026-10-09. Reference: `C:/Users/dever/OneDrive/Desktop/AppBuilders` (read only). Native destination: `C:/Users/dever/AndroidStudioProjects/GabayAI`, an Android Studio starter separate from the reference. No reference files are modified.

## Audit before implementation

Read `PROJECT.md`, `DESIGN.md`, `UX-CONTRACT.md`, React capture/guide/brand code, FastAPI services, `backend/templates.py`, `backend/general_forms.py`, translations, SQLite definitions, local PSA PDF and assets. No AGENTS.md was found in either project. Native starter contains only a Compose greeting; there is no working Android feature to remove.

| Existing capability / evidence | Native replacement | Migration constraint |
|---|---|---|
| React/Vite camera-first workflow, photo preparation and retry | CameraX lifecycle-bound rear camera, preview, retake; Android image picker secondary | Confirm image before OCR; bound resolution and preserve EXIF orientation |
| FastAPI/PaddleOCR upload pipeline | Bundled ML Kit Text Recognition v2 Latin script | Actual OCR on phone, no model download or API server |
| `backend/templates.py`, SQLite: one PSA birth application, six fields | Room form, field, alias and explanation tables, bundled seed | Retain source excerpt and PDF provenance; source review is distinct from translation review |
| PSA Date of Birth source: “Date of Birth / Month / Day / Year” | First slice: exact Date of Birth match and source-backed explanation | Require independent form evidence before applying PSA-specific guidance |
| PSA surname/first-name context safeguards | Layout-aware template matching | Do not confuse certificate owner, parent and requester labels |
| `backend/general_forms.py`: 24 common vocabulary kinds | Offline deterministic glossary and conservative fuzzy suggestions | Preserve general government/private form support; unfamiliar labels stay uncertain |
| Qwen3/Ollama rewriting and general classification | Prepared explanations and exact/fuzzy matching; optional ONNX embedding candidate retrieval | Remove LLM from core; rules are never described as AI; semantic suggestions need confirmation |
| One-item guide, fictional examples, photo highlight, manual comparison ticks, finish/restart | Compose guide with original label, source photo, Back/Next, local comparison state | Comparison never means complete or approved; examples are explicitly illustrative |
| React `fil` / `en` copy | Filipino/Taglish default and English via DataStore preference | Existing Filipino phrasing has no recorded native-speaker review |
| No regional language content or review evidence | Visible unavailable language options with review status | Cebuano, Ilocano, Hiligaynon and Bikol must not be fabricated or called reviewed |
| Supplied logo and blue/red/yellow visual system | Bundled existing brand artwork, Material 3 tokens and native controls | No gradients, decorative AI UI or network fonts |
| In-memory request documents | Private temporary photo, transient OCR state; no logs/backups | Only public definitions/preferences persist; no document history by default |
| Laptop/LAN offline demonstration | Standalone installable APK without INTERNET permission | Real physical-device airplane-mode test required; report pending if phone unavailable |

## Ordered implementation gates

1. Build camera → preview → bundled OCR → exact known Date of Birth → source-backed explanation. Run build and meaningful matcher/database/OCR checks before expanding screens.
2. Extend to all six source-backed PSA fields and 24 common field types with explicit unknown/uncertain paths. Add language selection, source-photo comparison, completion and retry.
3. Integrate optional compact semantic model only if provenance, tokenizer, runtime compatibility, memory and matching-quality validation are available. Absence must leave rules working; never fake semantic output.
4. Produce APK, source, dependency/model manifest, build evidence and physical-device airplane-mode checklist. No physical accuracy/performance/translation-review claims without evidence.

## Content decision

The reference records PSA source review dated 2026-10-09 and includes the blank source PDF. This supports field meaning, not an independent native-speaker review or a statement of current government requirements. Preserve supplied source-backed wording, never infer required/optional status except literal source text, and never claim full form coverage. Regional translations and linguistic review are outstanding release work.

## Follow-up scope: cropped parent section and larger APK

The user supplied a blank cropped PSA parent-name section that does not contain the form title or birth-date field. Recognition must work on that section without inventing a full form identity or applying certificate-owner wording to a parent. The replacement joins explicit split parent headings, uses adjacent field positions and section boundaries, and keeps general vocabulary when evidence is uncertain. The actual supplied crop is an instrumentation fixture; OCR is never replaced by hardcoded results.

The user subsequently requested an APK of at least 500 MB and a redesigned UI. The enhanced flavor therefore bundles a genuine BGE large encoder separately from compact MiniLM. The published FP16 graph failed a real device load test; the CPU-compatible FP32 export is used instead. This is a size/latency/memory tradeoff requested after the original lightweight brief. Deterministic matching remains first, and optional inference never creates or verifies explanations. Device results must distinguish an encoder ranking check from measured field-recognition accuracy. The guide redesign replaces repeated warning cards with an actual highlighted label detail, readable explanation/example, one disclosure and fixed navigation. Source-photo comparison, zoom and manual comparison remain available.
