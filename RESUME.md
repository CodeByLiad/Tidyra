# Resume Tidyra here

**Checkpoint: 2026-11-13, 04:00.** Builds clean, 13 unit tests pass, verified end to end on an emulator. Finish review applied.

## Read first
`PRD.md` (what it should become) · `PRODUCT.md` (product truth, platform limits) · `DESIGN.md` (visual system, written at finish) · `.impeccable/build-notes.md` (toolchain, version pins) · `.impeccable/surfaces/digital-jinatra-tidyra-ui-mainactivity-kt-1037687c.md` (direction contract — its OWN-WORLD block is superseded by the pinned editorial world; its four raises still bind).

## Visual direction
Editorial-minimal, pinned by Liad from a reference. Nude paper `#F3EDE3` / ink `#15110E`, dark ground `#131110`, one crimson — `#C41733` in light (5.1:1 on paper), `#FF5A72` in dark, always read from `MaterialTheme.colorScheme.primary` so the theme supplies it. Playfair Display (bundled, OFL) for every headline and figure. Hairline rules, no cards, no elevation-as-decoration.

**The four disciplines:** one shared size scale (bars are a true fraction of the device, never re-normalised) · one 8dp module · exact sizes (`756 MB`, `<1%`, never `0%`) · shared items counted once.

## Verified on the emulator (`cueplay_pixel`, API 35, x86_64)
Seeded with ~260 MB of realistic mess, then exercised:
- scan, findings, per-item approval, trash, restore, organise plan + apply
- **duplicates keep the original**: the camera copy survives even though it is the newest file; the old "keep the oldest" rule would have deleted the camera roll and kept the chat attachment
- **protected files are excluded**: the `msgstore.db.crypt14` duplicate is never offered
- **on-device AI**: SmolLM downloads (exactly 166,754,726 bytes), loads, and answers; Gemma 3 1B is refused on 2.5 GB RAM with a stated reason

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

## Build / test / install
```
./gradlew assembleDebug
./gradlew testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Pins: AGP 8.13.2, Kotlin 2.4.21, Compose 1.11.4, material3 1.4.0, okhttp 4.12.0, compileSdk 36.
Emulator permissions without tapping: `adb shell appops set <pkg> MANAGE_EXTERNAL_STORAGE allow` and `... GET_USAGE_STATS allow`.

## Next
1. **GitHub push is still blocked** by this session's permission classifier. Everything is committed on `main`; run it yourself:
   `gh repo create CodeByLiad/Tidyra --public --source=. --remote=origin --push`
2. Verify Shizuku on hardware.
3. The signature interaction from the contract — the proposed span as a hatch, retracting on confirmation — is a flat alpha overlay with no authored motion.
4. Off-grid paddings flagged by the review (`Module.u + 2.dp` and friends) are still there, deliberately, pending a decision.
