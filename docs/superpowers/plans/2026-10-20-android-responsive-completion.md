# Responsive Completion Implementation Plan

**Goal:** Remove network waiting from checkbox feedback and add restrained completion / list animations.

**Architecture:** Keep confirmed Snapshot separate from temporary per-task display overrides. Serialize persistence with existing repository mutex, merge confirmed versions, and invalidate callbacks on account changes. Execute inline under the user's request.

**Tech Stack:** Existing Kotlin, Compose animation / haptics / lazy-list animation, coroutines and Android instrumentation; no new dependency.

- [x] Add injectable ViewModel test seam and gate-backed `CompletionLatencyTest`; reproduce unchecked UI while a save is blocked.
- [x] Replace completion's global busy transaction with immediate per-task display overrides; handle rollback, conflict, revoked access, concurrency and logout.
- [x] Remove redundant successful mutation refresh while retaining permission checks and server versions.
- [x] Render display overrides in today, camps and detail; disable only pending tasks, expose saving state, add ripple / spring / haptic feedback and keyed row animations.
- [x] Verify latency, failure, conflict, account races and existing engine / notification / UI tests; build signed 1.2.0 and check upgrade, animation and actual screenshots.
- [x] Update README / audit, review diff, commit source and deliver APK.
