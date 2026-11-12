# Resume Tidyra here

**Checkpoint: 2026-11-13, 01:40.** Builds clean, installed and running on the Pixel (`digital.jinatra.tidyra.debug`), scanning real data.

## Read these first
- `PRD.md` — what it is meant to become.
- `PRODUCT.md` — product truth and platform limits.
- `.impeccable/surfaces/digital-jinatra-tidyra-ui-mainactivity-kt-1037687c.md` — direction contract. **Superseded on its visual world** (see below) but its four disciplines still bind.
- `.impeccable/build-notes.md` — toolchain facts and version pins.

## Visual direction (current)
**Editorial-minimal**, pinned by Liad from a reference screenshot, replacing the earlier "Sorting Station" world.
Warm paper `#F7F4EF` / near-black ink, one crimson accent `#B3122E`, Playfair Display (bundled, OFL) for every headline and figure, hairline rules instead of cards, generous whitespace.

Four disciplines carried over from the direction round and still binding:
1. One shared size scale — a megabyte is the same length on every screen.
2. One 8dp module, nothing off-grid.
3. Exact sizes — "756 MB", never "about 0.7 GB". Shares under 1% read "<1%", never "0%".
4. Interchange marks — a file in two findings is counted once.

## Verified working on device
Scan of real storage: Apps 52.9 GB, App data 58.9 GB, Media 1.6 GB, ranked category bars with percentages, permission notices that disappear as access is granted.

## Build / install
```
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Pins that matter: AGP 8.13.2, Kotlin 2.4.21, Compose 1.11.4, material3 1.4.0, okhttp 4.12.0, compileSdk 36. Compose 1.12+/okhttp 5.x need AGP 9.1 + compileSdk 37.
Font licence lives at `licenses/OFL-PlayfairDisplay.txt` — it **cannot** sit in `res/font/`, AAPT rejects non-font files there.

## Next, in order
1. **GitHub push is blocked** — the permission classifier refuses `gh repo create`. Liad runs it himself (command is in the session). Local commits are done and on `main`.
2. `LICENSE` file is missing — both fetch attempts timed out. README declares GPL-3.0-or-later.
3. Restyle `ModelScreen` properly; it was machine-retinted by sed and is only approximately editorial.
4. Wire navigation (tabs or bottom nav: Storage / Apps / Files / AI). `ModelScreen` exists but nothing routes to it.
5. Write `AppsScreen` and `OrganizeScreen` (plan preview before moving).
6. Finish review + `DESIGN.md`. The direction contract's FINISH line is undischarged.

## Known defects
- Checked in this build but unverified by screenshot: scan progress now sits below the hero figure, and `<1%` replaces `0%`.
- Row 7 "Everything else" bar rendered dark in the light capture with nothing selected — investigate `proposedWidth` in `RankedBar`.
