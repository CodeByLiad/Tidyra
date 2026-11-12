# Resume Tidyra here

**Checkpoint: 2026-11-13, 02:00.** Builds clean, installed on the Pixel, four tabs working against real data.

## Read first
`PRD.md` (what it should become) · `PRODUCT.md` (product truth, platform limits) · `.impeccable/build-notes.md` (toolchain, version pins) · `.impeccable/surfaces/digital-jinatra-tidyra-ui-mainactivity-kt-1037687c.md` (direction contract — world superseded, four disciplines still bind).

## Visual direction
**Editorial-minimal**, pinned by Liad from a reference. Warm paper `#F7F4EF` / near-black, one crimson `#B3122E`, Playfair Display (bundled, OFL, licence in `licenses/`), hairline rules, no cards.
Disciplines: one shared size scale · one 8dp module · exact sizes ("756 MB", `<1%` never `0%`) · shared items counted once.

## Working on device
Four tabs (Storage / Apps / Organise / AI). Scan finds real data: 62 unused apps 10.5 GB, cache in 67 apps 769 MB, duplicates, empty folders. Per-item approval with detail screens, app icons and labels everywhere, trash with undo.

## Hard-won gotchas
- **Every screen needs a `Surface` above it.** Without one `LocalContentColor` falls back to black and dark mode shows invisible text. `Root` wraps everything in one.
- Font licence files **cannot** live in `res/font/` — AAPT rejects non-font files. They are in `licenses/`.
- Apps / App data are not walkable files; `StorageStatsManager` reports them per package, so their category detail is built from the app list.
- Models live in **shared** storage (`Documents/Jinatra/models`) when all-files access is granted, so other Jinatra apps can reuse one download. Falls back to app-private.

## Build / install
```
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Pins: AGP 8.13.2, Kotlin 2.4.21, Compose 1.11.4, material3 1.4.0, okhttp 4.12.0, compileSdk 36.

## Next, in order
1. **GitHub push still blocked** by the permission classifier. Liad runs `gh repo create` himself; commits are on `main`.
2. `LICENSE` file missing — two fetch attempts timed out. README declares GPL-3.0-or-later.
3. Restyle `ModelScreen` properly (it was machine-retinted by sed, only approximately editorial).
4. Long finding titles crowd the size column; "SHARED ITEMS COUNTED ONCE" wraps badly.
5. Wire the AI: nothing calls `LlmEngine` yet to explain findings.
6. Finish review + `DESIGN.md`. The contract's FINISH line is undischarged.
