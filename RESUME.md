# Resume Tidyra here

**Checkpoint: 2026-11-13, pushed.** Builds clean, 18 unit tests pass, verified end to end on an emulator. Finish review applied, verdict applied, `DESIGN.md` written. This is the finished state.

## Read first
`PRD.md` (what it should become) · `PRODUCT.md` (product truth, platform limits) · `DESIGN.md` (visual system, written at finish) · `.impeccable/build-notes.md` (toolchain, version pins) · `.impeccable/surfaces/digital-jinatra-tidyra-ui-mainactivity-kt-1037687c.md` (direction contract — its OWN-WORLD block is superseded by the pinned editorial world; its four raises still bind).

## Visual direction
Editorial-minimal, pinned by Liad from a reference. Nude paper `#F3EDE3` / ink `#15110E`, dark ground `#131110`, one crimson — `#C41733` in light (5.18:1 on paper), `#FF5A72` in dark, always read from `MaterialTheme.colorScheme.primary` so the theme supplies it. Playfair Display (bundled, OFL) for every headline and figure, `StationType.amount` included — a figure must never arrive in Roboto. Hairline rules, no cards, no elevation-as-decoration.

**The four disciplines:** one shared size scale (drawn against the device, never re-normalised) · one 8dp module · exact sizes (`756 MB`, `<1%`, never `0%`) · shared items counted once.

## How storage is drawn
One stacked `CapacityRule` for the whole page, not a track per row. Seven per-row tracks against a whole-device denominator meant five of seven categories drew as empty track — an honest scale rendered dishonestly. The rule carries true shares and the free span; the rows carry rank, label, a dotted leader and the figure. The hairline between segments is cut out of the segment before it, never added, so the rule still measures the device exactly.

## Verified on the emulator (`cueplay_pixel`, API 35, x86_64)
Seeded with ~260 MB of realistic mess, then exercised:
- scan, findings, per-item approval, trash, restore, organise plan + apply
- **duplicates keep the original**: the camera copy survives even though it is the newest file; the old "keep the oldest" rule would have deleted the camera roll and kept the chat attachment
- **protected files are excluded**: the `msgstore.db.crypt14` duplicate is never offered
- **on-device AI**: SmolLM downloads (exactly 166,754,726 bytes), loads, and answers; Gemma 3 1B is refused on 2.5 GB RAM with a stated reason
- **organising refuses to relocate protected files**: `cueplay-backup.json`, `msgstore.db.crypt14` and `rgb-wallet-backup.bin` stay in Download, each with its reason on screen. Moving a backup breaks nothing structurally, but it can break the restore that goes looking for it by path.

## Not verified
- **Shizuku**: installed on the emulator but its ADB-start file never appears, so cache clearing (`pm trim-caches`) and silent uninstall are unexercised. Guards are in place and `isProtectedPackage` is unit-tested.
- Uninstall through the system dialog, on hardware.
- Answer quality from Gemma 3 1B (gated: needs a Hugging Face token).

## Hard-won gotchas
- Every screen needs a `Surface` above it or `LocalContentColor` falls back to black.
- Detail screens replace the masthead, so they carry their own `statusBarsPadding`; sections under the masthead must not.
- Never hardcode the accent: read `MaterialTheme.colorScheme.primary`, or dark mode silently runs the light-mode value.
- Tab labels must not change weight with state — bold is wider and shifts the row.
- Font licences cannot live in `res/font/` (AAPT rejects non-font files) — `licenses/`.
- Apps/App data are not walkable files; apps cannot be trashed, only uninstalled per row.
- `keepRank` is separator-agnostic so it is testable off-device.
- Gemma on Hugging Face is gated (401). SmolLM 135M is open and in the catalogue for that reason.
- A minimum-visible *fraction* is not a minimum visible *length*: 0.4% of a 115dp track is half a pixel. Floors for drawn marks belong in px, or the geometry eats them.
- The app owns its light/dark switch, so `cmd uimode night yes` does not change the theme — press the control.
- `uiautomator dump` only sees what a lazy list has composed; scroll before concluding a row is absent.

## Build / test / install
```
./gradlew assembleDebug
./gradlew testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Pins: AGP 8.13.2, Kotlin 2.4.21, Compose 1.11.4, material3 1.4.0, okhttp 4.12.0, compileSdk 36.
Emulator permissions without tapping: `adb shell appops set <pkg> MANAGE_EXTERNAL_STORAGE allow` and `... GET_USAGE_STATS allow`.
Scratchpad helpers: `seed.sh` (push to `/data/local/tmp`, run with `sh`), `capture.sh` (scroll-aware taps, verifies the foreground activity and an on-screen string after every screencap).

## Published
`https://github.com/CodeByLiad/Tidyra` — public, GPL-3.0, default branch `main`, `origin` wired up. 74 files; no keystore, `local.properties` or token strings in the tree (checked). Topics and description set for the F-Droid and Shizuku listings.

## Next
1. Verify Shizuku on hardware.
2. The signature interaction from the contract — the proposed span retracting on confirmation — is a flat alpha overlay with no authored motion. The stacked rule now carries the proposal in the accent; animating that retraction is the remaining half.
