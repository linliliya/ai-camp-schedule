# Android Morandi UI and Camps Implementation Plan

**Goal:** Ship a signed APK with a refined Morandi UI, Chinese typography, dynamic glass navigation, and native shared camp creation.

**Architecture:** Keep the existing Compose app, repository, cloud RPCs and schedule engine. Share a Haze backdrop across floating chrome; keep camp draft/validation separate from confirmed state.

**Tech Stack:** Kotlin 2.0.21, Compose 1.7.6, Material3, Haze 1.1.1, existing Supabase RPCs.

- [x] Add camp draft validation and repository createCamp using existing RPC; cover valid/duplicate/denied/offline flows with fixture transport. Cache the confirmed returned camp directly; subsequent manual/periodic refresh handles shared changes.
- [x] Add view model createCamp status, native form and camp selection; derive tasks/reminders with existing engine.
- [x] Bundle licensed Chinese font and source/license documentation; apply all typography weights consistently, replace saturated hardcoded UI colors.
- [x] Add shared glass component, backdrop source, floating nav selection motion, secondary sync capsule; remove top-left branding and inset scrolling content correctly.
- [x] Run unit and device tests; inspect native screenshots and changing backdrop; fix any regression. Build signed version 1.3.0, verify upgrade, signature and APK metadata; document evidence.
