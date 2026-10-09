# Gabay.AI native interaction contract

Sources: user migration brief; `docs/MIGRATION_MAP.md`; AppBuilders PROJECT.md, DESIGN.md and UX-CONTRACT.md read during audit. Public definitions may persist; personal documents are temporary and never backed up or logged. No network requests, submission, accounts, payment or required-field inference.

## Canonical UI Map
| Capability | Canonical owner | Source of truth | Allowed variants | Verification |
|---|---|---|---|---|
| Form | CameraScreen / ScanViewModel / ImageRepository | Migration brief and map | CameraX / Android photo picker | device and instrumentation tests |
| Select/Listbox | Language screen Material 3 radio rows | DESIGN.md and content review status | available / unavailable | Compose semantics tests |
| Toast | Shared inline Notice | DESIGN.md | status / warning / error | Compose semantics tests |
| Scrollbar | Native Android/Compose scrolling | Native platform accessibility | screen / original image | physical inspection |

## Workflow
Camera is primary, picker secondary. Preview and OCR use the same bounded, oriented image. Retry keeps the photo. New photo clears previous matches. Actual OCR and matching run off main. Duplicate starts are blocked; cancellation prevents stale completion. Camera releases when its screen leaves and follows lifecycle. Temporary pictures are deleted on restart and cold start. Process death returns to capture instead of saving document contents.

Only actual recognized labels receive guidance. Exact aliases use rules; fuzzy/semantic suggestions require user comparison before an explanation is shown and never imply verified identity. Independent PSA title/agency/section evidence gates template meanings. Literal father/mother headings and adjacent coordinates can supply a parent role independently of form identity; ambiguous or remote headings keep neutral wording. Unknown forms retain common vocabulary guidance with institution-specific uncertainty. Unknown labels remain visible without invented meanings. Required/filled status is not inferred. Manual comparison is separate from completeness; finish says only the guide ended.

Language preference persists via DataStore; image/OCR does not. Changing language after OCR switches local wording without repeated inference. Filipino is draft wording without recorded human review. Regional languages with no reviewed content are unavailable with a visible explanation. Source-backed meaning never implies reviewed translation. Original printed labels stay visible. Back returns to the previous stage; restart clears document state.

Shared controls grow with text, have 48/56/64dp minimum targets, native semantics and accessible names. Headings and progress/error expose semantics. A selected label's actual highlighted photo detail is immediately visible. Previous/next remain reachable while the explanation scrolls. User instructions/accessibility labels follow Filipino/English preference; original source labels are unchanged.
