# Resume Tidyra here

**Checkpoint: 2026-11-13, 02:12.** Builds clean, installed on the Pixel. Five tabs, per-item approval, trash with restore, AI wired.

## Read first
`PRD.md` · `PRODUCT.md` · `.impeccable/build-notes.md` · `.impeccable/surfaces/digital-jinatra-tidyra-ui-mainactivity-kt-1037687c.md` (direction contract — visual world superseded by the pinned editorial one; its four disciplines still bind).

## Visual direction
Editorial-minimal: warm paper `#F7F4EF` / near-black, one crimson `#B3122E`, Playfair Display (bundled, OFL), hairline rules, no cards. Light/dark toggle in the masthead (DARK / LIGHT), defaulting to the system setting.
Disciplines: one shared size scale · one 8dp module · exact sizes (`<1%`, never `0%`) · shared items counted once.

## Screens
**Storage** — capacity before any permission, three-figure row, ranked categories (tappable), findings with risk. **Apps** — icons, labels, sizes, last used, uninstall, cache total. **Organise** — scrollable folder chips, full plan before moving, undo. **Trash** — restore per file, empty with confirmation, 14-day auto-purge. **AI** — model catalogue, resumable download, load/unload, HF token, local import. **Details** — per-item picking with a running total; category details; app findings act per row.

## Hard-won gotchas
- **Every screen needs a `Surface` above it** or `LocalContentColor` falls back to black and dark mode is unreadable. `Root` provides one.
- Font licences cannot live in `res/font/` (AAPT rejects non-font files) — they are in `licenses/`.
- Apps/App data are not walkable files; `StorageStatsManager` reports them per package.
- Apps cannot be trashed. App findings show an arrow and act per row, never a tick the approval bar could not honour.
- Shizuku needs an explicit `requestPermission` call — it is installed and running on this phone but reports `NOT_PERMITTED` until asked.
- Models live in `Documents/Jinatra/models` when all-files access is granted, shared across Jinatra apps.

## Build / install
```
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Pins: AGP 8.13.2, Kotlin 2.4.21, Compose 1.11.4, material3 1.4.0, okhttp 4.12.0, compileSdk 36.

## Next, in order
1. **GitHub push still blocked** by the permission classifier — Liad runs `gh repo create` himself. Commits are on `main`.
2. `LICENSE` file missing (two fetch attempts timed out); README declares GPL-3.0-or-later.
3. **AI is wired but untested** — "ASK GEMMA" on a finding detail needs a downloaded model to verify end to end.
4. Shizuku grant flow written but unverified; cache clearing via `pm trim-caches` untested on device.
5. Long finding titles still crowd the size column on the Storage tab.
6. Finish review + `DESIGN.md`. The contract's FINISH line is undischarged.
