# Resume Tidyra here

Status 2026-11-13: **builds clean, installed and running** on the Pixel (`digital.jinatra.tidyra.debug`).

## Read these first (they carry all the context)
- `PRODUCT.md` — product truth, platform limits, principles.
- `.impeccable/surfaces/digital-jinatra-tidyra-ui-mainactivity-kt-1037687c.md` — the **direction contract** (Sorting Station world, four named raises, signature interaction). Build against this.
- `.impeccable/build-notes.md` — toolchain facts.

## Working
`./gradlew assembleDebug` → `adb install -r app/build/outputs/apk/debug/app-debug.apk`

Engine: scanner, hash-verified duplicate finder, app inventory, Shizuku bridge, trash with undo, organiser, Gemma catalog/downloader/MediaPipe engine. UI: station screen (container row + collection slips + approval bar), model screen.

**Version pins that matter:** Compose 1.11.4, material3 1.4.0, okhttp 4.12.0, AGP 8.13.2, compileSdk 36. Compose 1.12+ and okhttp 5.x require AGP 9.1 + compileSdk 37 — moving there is a deliberate toolchain upgrade, not a bump.

## Next, in order
1. **Stray "SCAN" ghost** at top-right of the header, seen in `.impeccable/review/phone.png` — unexplained, investigate before anything else.
2. Wire navigation (NavigationBar: Station / Apps / Sort / Model); `ModelScreen` exists but nothing routes to it yet.
3. Write `AppsScreen` and `OrganizeScreen` (plan preview before moving).
4. Hook `onOpenFinding` to a per-finding detail screen with the item list.
5. Capture light + dark + font scale 1.3 into `.impeccable/review/`, then the Impeccable **finish review** and `DESIGN.md`. The contract's FINISH line is undischarged until that happens.

## Cheap way to resume
Fresh session, short context: "Read RESUME.md and the direction contract, then continue Tidyra."
