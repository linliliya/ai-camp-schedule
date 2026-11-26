# Android UI Redesign Implementation Plan

**Goal:** Deliver the approved warm-white / indigo native UI as a signed 1.1.0 APK that upgrades 1.0.0.

**Architecture:** Preserve the existing model, repository and reminder engine. Add shared design primitives, a library-backed week calendar, and pure presentation grouping; update all Compose screens around these primitives.

**Tech Stack:** Kotlin 2.0.21 / Compose 1.7.6 / Material 3, Kizitonwose Calendar 2.6.1, official Lucide vectors converted to Android drawables, Compose animation and Canvas.

User approved implementation in this chat; execute inline without another confirmation or delegation.

## Tasks

- [x] Pin compatible calendar and Lucide dependencies, add bundled third-party notices, increment app to versionCode 2 / 1.1.0 and derive output APK name from actual release metadata.
- [x] Introduce warm palette, typography, shared Lucide icon wrappers, cards, completion ring, task rows, labels and presentation grouping in focused files.
- [x] Rebuild app shell and login with compact header, explicit demo/offline state, navigation and properly inset content.
- [x] Rebuild today with indigo summary, swipeable week calendar, date picker, filters and grouped task cards. Verify the displayed date and selected calendar date stay consistent.
- [x] Rebuild camp overview / phase navigation and reuse task cards; redesign settings and task detail with consistent icons and actions.
- [x] Add presentation-state tests covering complete/unfinished partitions, single group membership and follow/overdue precedence; preserve all existing date, notification and cloud tests.
- [x] Run `npm test`, `node scripts/export-android-data.js --check`, `android/gradlew.bat -p android testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease`.
- [x] Install on Android 15 emulator; inspect screenshots and test navigation, date swipes, date picker, filters, detail links, settings, 360dp width and enlarged font. Run the existing emulator integration/background checks.
- [x] Verify release certificate equals 1.0.0, install with `adb install -r`, inspect final UI, export screenshots and final SHA-256, update Android README and audit, commit source changes.
