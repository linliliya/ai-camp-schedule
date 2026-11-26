# Android Oil UI polish

## Task and boundary

Optimize only the native Android app using Oil UI's upstream, host-neutral skill. Continue the user's approved high-contrast workbench direction: MiSans Chinese, sparse Lora English, white surfaces on gray, blue actions, semantic overdue/completed colors, circular completion, and the existing floating glass navigation. The first screen's purpose is to find and complete the next task.

The supplied `oil-oil/dsh-oil-ui` is a DeepSeek Harness plugin, not a Compose component library. Its upstream `oil-oil/oil-ui` skill is installed locally and applied directly. Keep Compose, Calendar, Haze, and Lucide; no runtime dependency on an agent plugin, remote fonts, or WebView.

## Existing evidence

Baseline: `7526681`, branch previously `codex/sop-source-review`; this work uses `codex/android-oil-ui`. `git ls-remote --heads origin` on 2026-10-21 returned only `main` at `2988761`. Earlier web fixes are local commits on the old branch. Read-only retrieval of the live website's `index.html`, `data.js`, `config.js`, and `cloud.js` confirmed byte-for-byte equality with `origin/main`, and differences from the workspace's earlier web changes. Since Android development began after `d8984d9`, root web front-end files have no further changes. Android and web deliberately share production schedule records.

Baseline screenshot: `artifacts/android/oil-ui/today-before.png` (1.4.0, demo data, 1080x2400). It has repeated bordered summary/calendar/task cards and metadata above every task title. These create equal visual weight and delay the task list below several unrelated frames.

## Direction: a readable daily agenda

North star: open the app and immediately read what needs doing, with the quiet precision of a well-kept agenda.

- Combine progress and the week strip in one white planning surface; one separator, no nested border. Preserve summary state/accessibility and centered 100% at enlarged fonts.
- Keep the existing page title/date and calendar action. Avoid reintroducing the removed product/camp branding header.
- After independent review, place Today's status actions alongside its title instead of reserving a separate top row; place the date, English accent and calendar action in the second row. Status inside the captured scrolling source uses a plain white surface to avoid sampling itself; navigation and the sibling status overlays on the other pages keep Haze.
- Keep each summary count and its unit together in a FlowRow; the enlarged narrow layout must not orphan “项”.
- Group task rows into continuous white lists, separated by inset rules instead of individual red outlines. Preserve stable lazy-list task keys, individual completion hit targets, and item placement animations.
- Put task titles first, descriptions second, camp/phase and deadline last. Overdue text and an explicit group heading communicate urgency without making every row a warning panel.
- Animate one sliding selection plate for the task filter, matching the existing navigation motion; keep updates immediate and completion regrouping at 320ms. No decorative entrance delays or network-dependent motion.
- Keep colors and fonts, and use the same row grammar in the camp schedule. The tool's main visual is its real agenda; no illustration or stock image is warranted.

## Alternatives and constraints

Large editorial dashboard tiles would reduce visible tasks; a full-edge plain list would discard the user's chosen white/gray workbench. Continue the approved workbench and consolidate its surfaces instead. No new three-direction exploration is required for this continuation of a confirmed direction (Oil UI scope table).

Use content-driven heights, 48dp completion targets, readable text, and native Calendar gestures. Verify normal and 1.3x text, narrow layouts, overdue rows, completed and empty states, date/filter persistence, camp creation, and task detail access. Preserve notification and cloud behavior.

## Delivery and validation

Produce signed APK 1.5.0 using the existing signing key, native screenshots, one independent visual review per Oil UI's existing-interface polish protocol, and an audit. Use demo/fake-network fixtures only. Build and run relevant existing checks because delivery is an installable APK. Verify `git diff 7526681 --` contains only Android, Android docs and review artifacts, with zero website/database changes. No push or deployment is part of this task.
