---
version: alpha
name: Gabay.AI Android
description: Patient native form guidance, one original label and explanation at a time.
colors:
  primary: "#0038A8"
  brand-red: "#CE1126"
  sun: "#FCD116"
  background: "#F8FAFC"
  surface: "#FFFFFF"
  ink: "#0F172A"
  muted: "#475569"
  border: "#D7DFEA"
  tint: "#EFF4FC"
typography:
  display:
    fontFamily: "Roboto, system-ui, sans-serif"
    fontSize: "30px"
    lineHeight: "1.27"
  body:
    fontFamily: "Roboto, system-ui, sans-serif"
    fontSize: "18px"
    lineHeight: "1.56"
rounded:
  DEFAULT: "8px"
  surface: "12px"
spacing:
  gutter: "24px"
  action-height: "56px"
components:
  button:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.surface}"
  guidance:
    backgroundColor: "{colors.tint}"
    textColor: "{colors.ink}"
  wordmark-accent:
    textColor: "{colors.brand-red}"
  step-progress:
    backgroundColor: "{colors.sun}"
    textColor: "{colors.ink}"
  supporting-text:
    textColor: "{colors.muted}"
  divider:
    backgroundColor: "{colors.border}"
---

# Gabay.AI Android design system

## Overview
Product register for Filipino residents and community helpers reading printed government and private forms on the Infinix Note 12. Reference identity and workflow were audited in AppBuilders before implementation. North Star: patient, example-led assistance while holding a phone and paper. Signature: the original printed label and its actual highlighted photo detail stay above one plain explanation and a clearly fictional example. Literal parent headings appear beside repeated name fields when conservative layout evidence supports them. No dashboard, government seal, gradients, glass, decorative animations or fabricated AI status. Existing supplied logo artwork is retained as a compact native asset.

## Colors
Runtime ownership: `ui/GabayTheme.kt` implements the exact accepted palette documented above and maps it to Material 3; shared controls consume MaterialTheme. No dynamic system palette changes the identity. Yellow is a restrained step accent with dark text; red is wordmark/error only. Light theme is intentional for paper comparison; Android font scaling remains enabled.

## Typography
Bundled Android sans: 30sp heading, 22sp item heading, 18sp explanation with 28sp line height, 16sp supporting copy, 17sp buttons. System fallback supports requested Latin scripts. Body and button height grow with font scaling. Long original labels wrap.

## Layout
Single column, 24dp gutters, 56dp minimum main controls, camera action 64dp, 48dp minimum selections. Home → camera → photo preview → OCR → language → guide. Compact brand header; camera/picker actions stay at the bottom of home and preview. The guide has a scrolling body and fixed previous/next controls; at font scales of 1.2 or greater, navigation stacks at full width so labels remain readable. Screen owns vertical scrolling with native system safe insets. Camera displays the full frame and accepts tap-to-focus. One guide item at a time; original label is always visible. Source, matching method, unknown text and language-review detail share one disclosure rather than repeated warning cards.

## Elevation & Depth
White work surface and thin borders. Avoid a stack of cards, shadows, blur and gradients. Existing logo art is unchanged apart from deterministic asset sizing/cropping in `scripts/package_brand.py`.

## Shapes
8dp controls, 12dp surfaces. Real document remains rectangular.

## Components
Material 3 controls own native semantics, focus, pressed/disabled states. Inline status/error copy is persistent and announced. Photo selection and permission dialogs belong to Android. Language selection uses explicit radio rows, including unavailable languages with an explanation. Home's compact language switch flips Filipino/English. Uncertain suggestions show the original crop and confirmation controls before any explanation. A full-photo view preserves context. No custom date input, table or destructive action is needed.

## Motion
No decorative transitions or fabricated progress. Progress changes only when actual local work changes stage.

## Content
Filipino/Taglish default; English available. Source-backed meaning and language review are separate statuses. Cebuano, Ilocano, Hiligaynon and Bikol remain unavailable until actual reviewed content exists. Rules are identified as rules; ML Kit OCR is the bundled machine-learning feature. Unknown and uncertain matches require visible recovery/confirmation.
