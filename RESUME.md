# Resume Tidyra here

**Checkpoint: 2026-11-13, 02:45.** Builds clean, installed on the Pixel. Five tabs, per-item approval, trash with bulk select, on-device model loads and runs.

## Read first
`PRD.md` · `PRODUCT.md` · `.impeccable/build-notes.md` · `.impeccable/surfaces/digital-jinatra-tidyra-ui-mainactivity-kt-1037687c.md` (direction contract — visual world superseded by the pinned editorial one; its four disciplines still bind).

## Visual direction
Editorial-minimal. Nude/beige paper `#F3EDE3`, near-black ink, one vivid crimson `#D6203F` (brightened so it also pops on the dark ground `#131110`), Playfair Display (bundled, OFL), hairline rules, no cards. Light/dark toggle in the masthead.
Disciplines: one shared size scale · one 8dp module · exact sizes (`<1%`, never `0%`) · shared items counted once.

## Verified on device
- Scan of real storage; findings with risk levels; per-item picking with running totals
- Trash: restore one, restore all, bulk select by long-press, empty with confirm
- **Model download**: SmolLM 135M q8 pulled to `Documents/Jinatra/models`, 166,754,726 bytes — exact
- **Model load**: MediaPipe reports RUNNING / UNLOAD on the AI tab
- Tabs measured stable across switches: `Storage [63..218]`, `AI [975..1017]` identical before and after, 63px margins both sides

## Not yet verified
**ASK GEMMA end to end.** The button was hidden because `aiReady` was only computed on activity resume — which happens before any model is loaded. Now read live from `EngineHolder.isReady`, but the full chain (load → scan → open finding → ask → read answer) has not been completed once. Driving it by UI automation kept drifting; tap it by hand.

Also unverified: Shizuku cache clearing (`pm trim-caches`), uninstall flow, organise apply/undo on a folder that actually has loose files.

## Hard-won gotchas
- Every screen needs a `Surface` above it or `LocalContentColor` falls back to black.
- Detail screens replace the masthead, so they carry their own `statusBarsPadding`; sections under the masthead must not, or a dead band appears under the tabs.
- Tab labels must not change weight with state — bold is wider and shifts the whole row. Colour plus an always-present rule (alpha animated) instead, each tab `width(IntrinsicSize.Min)`.
- Font licences cannot live in `res/font/` (AAPT rejects non-font files) — they are in `licenses/`.
- Apps/App data are not walkable files; apps cannot be trashed, only uninstalled per row.
- Shizuku needs an explicit `requestPermission` call.
- Gemma on Hugging Face is **gated (401)** — needs a token. SmolLM 135M is open and is in the catalogue for exactly this reason.

## Build / install
```
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Pins: AGP 8.13.2, Kotlin 2.4.21, Compose 1.11.4, material3 1.4.0, okhttp 4.12.0, compileSdk 36.

## Next, in order
1. **GitHub push still blocked** by the permission classifier — run `gh repo create` yourself; commits are on `main`.
2. `LICENSE` file missing; README declares GPL-3.0-or-later.
3. Finish the ASK GEMMA verification.
4. Finish review + `DESIGN.md`. The contract's FINISH line is undischarged.
