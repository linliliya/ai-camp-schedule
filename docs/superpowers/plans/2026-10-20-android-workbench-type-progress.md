# Android Workbench Typography and Progress Implementation Plan

**Goal:** Deliver the approved readable workbench UI, better Chinese fonts and an aligned compact completion summary in APK 1.4.0.

**Architecture:** Retain the current native Compose app, repositories, cloud RPCs and reminder engine. Centralize progress presentation in a small summary component; replace typography resources without changing scheduling behavior.

**Tech Stack:** Kotlin 2.0.21, Compose 1.7.6, Material3, existing Haze.

- [x] Download official MiSans and HarmonyOS Sans, inspect licenses and regular/medium faces, compare Chinese and numerical rendering, and record source hashes. Bundle chosen unmodified fonts and attribution. Add Lora after the user's English-font follow-up.
- [x] Add a focused native regression for 100%/empty/partial and large-font summary layout, including centered percentage bounds. Reproduce the old constrained progress layout first.
- [x] Implement TodaySummary and semantic statistics; replace old ring/hero. Use empty and all-done states and preserve immediate UI feedback.
- [x] Apply the approved contrast, typography, card density and readable glass adjustments across screens. Validate functional color pair contrast.
- [x] Diagnose repeated slow-save regression failure: prove shared AtomicFile reader/writer cache loss, add a failing two-store regression, then serialize snapshot reads/writes/deletes process-wide. Native progress, camp, completion, cloud and cache checks pass (20 cases including fixture isolation).
- [x] Run appropriate unit/device/UI checks, inspect native screenshots, build a signed 1.4.0 APK, check 1.3.0 upgrade and final package, and document evidence. Final smoke and large-font checks pass.
