# Completion Control Refinement Implementation Plan

**Goal:** Deliver the user's chosen smaller circular completion control, gentle animation and readable completed cards in APK 1.1.1.

**Architecture:** Extract the drawing and interaction into one Compose control used by the shared TaskCard. Keep state and server confirmation in existing callers. Execute inline under the user's approved option.

**Tech Stack:** Existing Kotlin / Compose Canvas, animation and interaction APIs; existing Android instrumentation runner. No added dependencies.

- [x] Add `TaskCompletionControl.kt`: 18dp visual circle, 1.2dp outline, 48dp touch target, checkbox semantics, enabled gating, 180ms fill / 200ms check drawing and subtle press scale.
- [x] Update `UiComponents.kt`: use the new control, replace grey completion background with nearly white lavender, remove strikethrough, keep title readable and card opening independent.
- [x] Update `TodayScreen.kt`: after confirmed completion, hold presentation grouping/removal for 240ms while status/counts update immediately, so the check draws before the task moves.
- [x] Add `CompletionControlTest.kt`: exercise actual accessibility click, checked state, restore, disabled editing and independent card click on Android.
- [x] Increment `android/app/build.gradle` to versionCode 3 / 1.1.1; run unit tests, instrumentation build, full `android/build.ps1` checks and focused device tests.
- [x] Verify same-signature upgrade from 1.1.0, inspect real demo cards / completed filter at normal and enlarged font, check no crash, record audit and update README, commit source and deliver APK / screenshot.
