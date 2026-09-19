# Reading Room — Android app for the reading list

Native Kotlin/Compose app that syncs the cross-project reading queue
(`~/vibes/projects/bin/reading-list`) to the phone, renders each report and web
clip for reading offline, and sends done / drop / comment back through the CLI.
Design: `../../docs/plans/2026-09-17-reader-app-design.md`.

The Mac side is the `reader` module (`../../lib/reader_server.py`) of the shared
server at `~/vibes/server/` (launchd job `com.mangesh.claude.server`, port 8642).
The app never writes the log; the module shells out to `reading-list`.

## How it behaves

- **Sync on open and on pull-to-refresh only.** No background work. A sync flushes
  the outbox first, then pulls the queue and fetches reports whose file changed.
- **The Mac being unreachable is normal.** Everything reads from cache; the three
  verbs work offline and wait in the outbox; the status line under the header says
  what happened. One bounded attempt per sync, never a retry loop.
- **Rendering is deterministic offline.** The WebView loads only what the phone has
  (report HTML, clip images, the bundled theme fonts); every other request is
  blocked. External links open in a Chrome custom tab.
- **Theme.** Playfair Display headings (400, never bold) over Work Sans, terracotta accent; light,
  sepia and dark, following the system until the Theme control overrides it. Tokens live in
  `ui/Palettes.kt` (colour) and `ui/TypeScale.kt` (five text sizes × three line spacings) and reach
  the page as CSS variables, so `reader.css` holds no colours of its own. **Aa** in the reader opens
  the settings sheet; every change restyles the page in place (`applyStyle` in `reader.js`), no reload,
  place kept. The accent is a stroke, an underline or a small figure, never a fill; no shadows, no
  cards. The fonts are in `res/font/` — one copy serves both Compose and the WebView. Source:
  `../../design_handoff_reader_theme/README.md`. `python3 scripts/palettes.py` previews the colours.
- **Math.** Two kinds, both decided by the Mac's renderer (`lib/mdrender.py`), so the phone and
  `reading.html` agree. *Math written as code* — the reports' habit: `` `x_1` ``, `` `z^{-1}` ``,
  `` `Σ_{x ∈ X} z^x` ``, and display formulae in a bare fence — gets real sub/superscripts and keeps its
  code look; a span that reads as code (`TURN_LEFT`, `snake_case`, a path, a call) is left exactly as
  typed, and so is a fence laid out in columns. *TeX* — `$…$` and `$$…$$` — is passed through as
  `class="tex"` and typeset on the page by KaTeX, bundled under `assets/katex/` (0.18.7, MIT; script,
  stylesheet, 20 woff2 fonts, ~600 KB) and loaded only on pages that have some, before `reader.js`
  measures anything. Money is never math: a dollar sign must pass pandoc's rule, and plain `$n$` only
  counts in a document that uses TeX somewhere. A renderer change reaches reports the phone already
  holds through `RENDER_VERSION`, which the server folds into the hash it reports.
- **Diagrams.** A ```` ```mermaid ```` fence is marked `<pre class="mermaid">` by the Mac's
  renderer; the app bundles mermaid (`assets/mermaid.min.js`, loaded only on pages that
  have one) and renders it to SVG in the page's theme.
- **Pages.** Paged mode (default) lays the article out as viewport-wide CSS columns, one per
  page, so lines are never cut. A vertical drag folds the near half over the midline,
  Flipboard-style, and completes past the edge; taps low/high turn pages, a middle tap
  toggles the bars. The fold uses two hidden clones of the current page prepared in idle
  time. Scroll mode is a toggle in Settings and the reader menu.
- **Quick scroll.** The footer's progress line is the page edge you thumb. Drag along it to riffle:
  pages swap at once with no fold, a tick marks where each section starts and the drag snaps softly
  to it, and a label over the finger names the section and the page ("Scorecard · 17 / 22"). Letting
  go lands with one real fold. **↩ 3** at the left of the line is the way back to the page you were
  reading; it survives a look around (three page turns, or about two screens of scrolling) and then
  goes. The line stays at the screen edge when the bars hide, so it is always there. Scroll mode uses
  the same line, mapped to scroll position. Page side: `scrubStart` / `scrubTo` / `scrubEnd` and
  `onSections` in `reader.js`; app side: `ui/Scrubber.kt`.
- **Comments** anchor to the report's line via the Mac's `data-line` and the file's
  hash at render time. A comment against a changed file is refused by the Mac and
  kept under Settings → Needs attention; tap it in the report and long-press a
  paragraph to place it again.

## Building

Toolchain (Homebrew): `openjdk@21`, `android-commandlinetools`, `gradle`. SDK path in
`local.properties`.

```sh
./gradlew test            # JVM tests: sync logic, model JSON, the theme's tokens
./gradlew testDebugUnitTest -Pscreenshots   # render the Compose screens to app/build/screenshots/ (Robolectric; no phone needed)
./scripts/release.sh      # bump versionCode, build signed APK, stage in dist/, adb-install if a phone is attached
```

`keys/reader.keystore` signs every build. **Never delete or regenerate it** — Android
only installs updates signed with the same key. Back it up.

## Working in git

The repo is `github.com/mangeshgupte/reading-room`, and it is **public**: no token, keystore
or password goes into a commit. `main` tracks `origin/main` and is what the phone runs or is
about to run: it builds, and `./gradlew test` passes on it.

- **A feature is a branch.** `git switch -c feature/<slug>` off `main`, committed in small
  steps whose messages say what changed and why. A one-line fix can go straight on `main`.
- **Trying it on the phone takes a release**, so release from the branch.
  `scripts/release.sh` commits the version bump as `Release build N` and tags it `build-N`.
  A build made over uncommitted work gets the commit but no tag, so a tag always names an
  APK's exact source: `git checkout build-N` is the code behind that APK.
- **Done** means tests green and, where it shows, seen on the phone. Then
  `git switch main && git merge --no-ff feature/<slug>`, delete the branch, and
  `git push --follow-tags` (the `build-N` tags go with it), so
  `git log --first-parent main` reads one line per feature.
- **Not in the repo:** `keys/` (git is no backup for the keystore), `local.properties`,
  `dist/`, build output.

## Installing / updating on the phone

1. Token: on the Mac, `python3 ~/vibes/server/serve.py --token reader`.
2. First install: on the phone, open `http://starlight.local:8642/reader/version.json` in
   Chrome to read the APK name, then `http://starlight.local:8642/reader/reader-v<N>.apk`
   (these two paths need no token), download, install (allow "install unknown apps" for
   Chrome when prompted). Or pair wireless adb once (Developer Options → Wireless
   debugging → Pair device; `adb pair`, `adb connect`) and run `scripts/release.sh`.
3. In the app: Settings → paste the token → Sync now. The default server is
   `http://starlight.local:8642/reader`; a `.local` name means the app finds the Mac over
   Bonjour (`_vibes._tcp`, advertised by the server), so neither the phone's `.local`
   resolution nor the Mac's DHCP address matters. Typing an IP instead skips discovery.
4. Updates: Settings → Check for updates (pull), or `scripts/release.sh` over adb (push).
