---
stage: Building
intent: Android app that syncs the reading list to the phone for offline reading, with done / drop / comment flowing back through the CLI. Shipped = installed on Mangesh's phone, one report read end to end and marked done from the phone, one comment landing in the .md.
started: 2026-09-17
last_review: 2026-09-17
next_action: Walk the remaining flows on the phone — mark a report done from the end-of-report row, long-press a paragraph and comment, sync, see both land on the Mac; then the airplane-mode walk.
blocker:
---

# Notes

## 2026-09-17
- Designed with Mangesh (docs/plans/2026-09-17-reader-app-design.md): LAN pull-and-cache, sync on open + pull-to-refresh only, adds only on the Mac, web pages clipped to markdown on the Mac, render on the Mac, comments in v1, one shared Mac server.
- Mac side built and live: shared server host (`~/vibes/server/`, launchd), renderer split into `lib/mdrender.py` with images, `bin/clip` + `reading-list add <url>`, `lib/reader_server.py`, 65 + 8 tests. First clip: the American Scholar essay, #64.
- App built: Kotlin/Compose, files-as-store, WebView reader with intercepted requests, 7 JVM tests. Release v2 staged in `dist/` and served at `/reader/version.json`. Not yet on the phone (no adb device attached).
- Installed on the phone (v2 → v4 the same evening). Fixed on the way: `.local` not resolvable by the app (Bonjour discovery), alternate fetches dying on a stale pooled socket (`Connection: close` + HTTP/1.1), reader page broken under `loadDataWithBaseURL` (page now served through the interceptor). Rendering confirmed working on v4.

## 2026-09-18
- Reader theme implemented from `design_handoff_reader_theme/` (direction 1c, screens 2a–2e): Playfair Display + Work Sans, terracotta accent as stroke only, hairline rows instead of cards, no shadows. Queue = the notes list (2d) with search added; reader = nav row with **Aa**, accent-ruled section headings, hairline progress footer; **Aa** opens the settings sheet (2e), which restyles the page live. Settings and both sheets restyled to match.
- Replaced the eight palettes with the handoff's light / dark plus sepia (light values on a warmer ground — not designed yet). Text size is now five steps (2 = standard), line spacing three; an old pixel size maps to the nearest step. Justify now defaults off, per the handoff.
- Calls the handoff left open, made here: line spacing carries leading + paragraph gap + screen padding, text size carries body/title/section sizes (steps 0 and 4 continue the designed intervals: 15 and 19px body); dark secondary text `#C5BFB6`; light/sepia sheet surface `#FAF8F3` / `#F6EFE0`; M's margin notes take the highlight tint, prism's replies a hairline aside (the old yellow/blue are gone).
- Verified: 12 JVM tests green; the reading page checked in Chrome at 360×740 against the mocks (three scales, three themes, paged + scroll, live restyle keeps the place, real reports #66 and #64). **Not yet seen on a phone** — the Compose screens (queue, sheet, settings, tabs) compile but are unverified visually. Not released: `scripts/release.sh` when ready.
- Released as build 15 the same day (staged in `dist/`, served at `/reader/version.json`; no adb device, so the phone pulls it). The on-phone walk is still owed.
- Build 16: the tab bar's icons are back (list, book, gear) over the meta labels; the current tab takes the accent as ink, still no pill or fill. Mangesh missed them in build 15.
- Build 17: quick scroll. The footer's progress line is now a scrubber (drag to riffle, section ticks with soft snap and a haptic tick, section + page label over the finger, one landing fold on release, `↩ N` way back that survives three page turns). Verified: page side in real-time headless Chrome (lands on the exact page, no stray fold layers, fresh spares, reports as not-a-user-turn, clean cancel when grabbed mid-landing); app side by Robolectric tests that drag the real reader screen's line. Not yet felt on a phone: drag feel, the landing fold's cost on long reports (it clones the page twice, as any unprepared flip does), haptics, and the back-swipe exclusion at the line's left end. Also: a `---` directly above a section heading is hidden, matching the desktop.
- Build 18: math. (1) Mac-side, no build needed: `lib/mdrender.py` turns math written as inline code into real sub/superscripts (368 of 1,345 code spans, every one in the four turnpike reports, none elsewhere; 11 of 17 fenced blocks as display formulae; the aligned derivation and the ASCII tree are left as typed), with a small table for the 11 TeX commands that occur inside those spans. Code spans are now out of reach of emphasis, which also fixes `*` inside code. `RENDER_VERSION` (now 3) is folded into the hash the server reports, so phones fetch again; a comment's hash has it stripped on the way back. (2) KaTeX 0.18.7 bundled for `$…$` / `$$…$$`: 101 formulae across three documents, 0 parse errors; money stays money, including inside the math documents. Verified in real-time headless Chrome at phone size (scroll and paged) and on `reading.html` over file://. Not yet seen on the phone.
- Now in git (local repo, no remote): the app as of build 18 is the first commit, tagged `build-18`. From here a feature is a `feature/<slug>` branch merged to `main` with `--no-ff`; `scripts/release.sh` commits each version bump and tags it `build-N` (no tag when the build includes uncommitted work). Workflow in the README under "Working in git"; `CLAUDE.md` points sessions at it. The keystore stays outside the repo, so git is not its backup.

## 2026-09-19
- Upstream set: `origin` = `git@github.com:mangeshgupte/reading-room.git` (public, MIT). GitHub's initial commit held only `LICENSE`, so the local history was replayed on top of it rather than force-pushed; `build-18` moved to the replayed import commit. Tracked files scanned for credentials before the first push: none (the token is typed in at runtime, signing passwords come from the ignored `keys/`).

## 2026-10-02
- Added Settings → Playground: sample reader, five tunable swipe parameters,
  per-gesture intent labels and notes, local retention of 300 traces, and JSON export.
  The reader and playground share swipe.js. Cancelled touches now roll back; flick
  speed uses a recent window including release, preventing stale velocity after a pause.
- Added dependency-free trace replay/ranking script and ten passing detector and reader touch-handler tests.
  Changed Kotlin screens compile directly against cached dependencies.
  Android Gradle verification blocked by sandbox socket restrictions; escalation
  disabled. Not released or checked on a phone; feature remains on its branch.
- Page turns on the Schmidhuber paper (#88, 81 pages) were sluggish: a Perfetto trace of build 18 on the Pixel 9 Pro XL shows the WebView's renderer main thread busy for ~2.1 s after each tap. Reproduced in headless Chrome at phone size (a *fresh* profile is needed; a reused one composites differently and hides it): a tap flip cost 1.3 s of main thread, a finger drag 740 ms per touchmove, nearly all of it Chrome's layer assignment (`Layerize`), not layout. Cause: every scroll container inside the column flow (`.tex.display`, `pre`, `.tablewrap`, `.diagram`) is composited, and a composited box inside the flow gets a layer as wide as the whole 33,000 px flow — nine of them, times three copies of the article. Fix on `feature/fold-perf`: in paged mode a box is a scroll container only when its content overflows (`.scrolls`, marked after each layout) with `contain: paint`, the fold's copies never scroll, and the two copies are kept across turns instead of rebuilt (two full relayouts) after each. Measured: tap flip 1,320 → 210 ms, worst frame 734 → 34 ms; drag 12.1 s → 0.4 s, no frame over 34 ms; #28 (8 tables) and #84 (tables, a code block) improve too. Pagination identical, pages pixel-identical, wide boxes still scroll, 38 JVM tests green. **Not yet on the phone**: the release was not cut; the 2.1 s number is the one to re-measure after install (`scratchpad/perfetto_flip.cfg` + `q2.py` from this session, or any Perfetto trace with `sched_switch`).
- Merged `feature/fold-perf` into `feature/swipe-playground` for the combined release.
  Rechecked the ten gesture tests; Gradle and ADB both cannot start their local
  sockets in this session, so no new APK was built or installed and build 18 remains current.

- Swipe study now opens in an immersive full-screen window. Tune and Feedback
  overlay the sample reader instead of reserving screen space; app tabs are hidden.
  Changed Kotlin screens compile against cached dependencies. Not yet released
  or visually verified on the phone.

- `feature/open-fullscreen` (on top of fold-perf): an article now opens full screen — `open()` and the Reading tab hide the bars instead of showing them; a middle tap brings them back, and in scroll mode so does scrolling up or reaching the end. The gate's "always shown near the top" rule now fires only on an upward scroll, otherwise scroll mode would flash the bars on every open. Screenshot test sets the bars on explicitly. 31 JVM tests green. Not yet on the phone (same release as fold-perf).
- Measured on the phone, build 20 (same Perfetto method as the 2.1 s baseline): renderer main thread 379 / 405 ms of CPU after a tap on #88, down from 2,130 / 2,136 ms; longest busy stretch 234 / 225 ms (the fold's own 240 ms animation), down from 1.4–2.1 s; longest single run 50–60 ms, down from 262–274 ms. Merged `feature/open-fullscreen` to main (8c9dd0c), 38 tests green; main is unpushed.
