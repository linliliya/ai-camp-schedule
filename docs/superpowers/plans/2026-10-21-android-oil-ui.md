# Android Oil UI Implementation Plan

**Goal:** Deliver a native Android agenda with clearer task hierarchy and fewer repeated containers, isolated from website code.

**Architecture:** Keep the existing Compose state, repository and reminders. Adapt shared row presentation and consolidate the Today planning header. Use inline execution; Oil UI independently reviews screenshots after the representative page is stable.

**Tech stack:** Kotlin, Jetpack Compose, existing Calendar 2.6.1, Haze 1.1.1, MiSans and Lora.

## Task 1: Isolate and establish evidence

- [x] Check clean Git state, branch history, remote heads and changes since Android started.
- [x] Create `codex/android-oil-ui` from `7526681`; retain previous branch and 1.4.0 APK.
- [x] Install and read upstream Oil UI using Skill Installer; retain baseline screenshot and design constraints.

## Task 2: Refine native presentation

Files: `TodayScreen.kt`, `TodaySummary.kt`, `UiComponents.kt`, `WeekDateStrip.kt`, `CampsScreen.kt` under `android/app/src/main/java/cn/aurange/schedule/`.

- [x] Allow `TodaySummary` to render without an outer border when embedded in a parent planning surface. Preserve its default standalone behavior and semantics for existing progress tests.
- [x] Introduce group-edge parameters on `TaskCard`, defaulting to a standalone row for existing consumers. Set only first/last corners, render one inset separator between rows, and order title/description before metadata. Keep completion and card detail callbacks distinct.
- [x] Use `itemsIndexed` in Today and Camp day groups, with unchanged task keys. Set group edges by index and zero spacing inside groups, using explicit spacing before group headings and planning controls.
- [x] Put Today summary and Calendar inside one white surface. Keep the week swipe and date-picker contracts unchanged.
- [x] Extract a task-filter control with one sliding plate, equal-width options, accessible Tab roles and content-driven height for large fonts; use existing Compose animation APIs only.
- [x] Build debug app/test APK: set existing JDK/SDK/Gradle paths, run `android/gradlew.bat -p android assembleDebug assembleDebugAndroidTest --console plain`; expect BUILD SUCCESSFUL.

## Task 3: Review actual app screens

- [x] Launch only `CampSchedule_API35`, install debug APK, clear crash log, and run `scripts/verify-android-ui.ps1`. Capture screenshots only after foreground/package checks.
- [x] Review Today, overdue/completed groups, Camp and Settings at normal text and 1.3x text. Use `adb shell wm size 900x2000` for a narrow native layout, then restore `wm size reset` and font scale 1.0.
- [x] Dispatch an isolated image-capable reviewer per `oil-ui/references/visual-review.md`; include screenshots, product task, fixed palette/typography and constraints only. Fix actionable deviations and record any retained choices.

## Task 4: Verify and deliver APK

Files: `android/app/build.gradle`, `android/README.md`, `docs/audits/2026-10-21-android-oil-ui.md`.

- [x] Increment versionCode to 7/versionName to 1.5.0.
- [x] Run existing completion/control, camp creation, latency and cloud fixture device tests. Repeat existing UI walkthrough three times because it exercises asynchronous transitions. Use no real account or production mutations.
- [x] Run `android/build.ps1` with the existing environment; expect website tests, native unit tests, lint and signed release build to pass. Verify signing certificate matches the previous APK.
- [x] Install the signed release over 1.4.0 on the dedicated emulator and confirm upgrade, Today navigation, glyph rendering and no app crash.
- [x] Check `git diff --check`, Android/web fixture parity, and `git diff --name-only 7526681` for scope. Write evidence and commit the verified Android work locally without push/deploy.
