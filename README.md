<div align="center">

# Tidyra

**Understand your Android storage. Decide what goes. Nothing happens without your approval.**

A storage cleaner and organiser with on-device AI, built by [Jinatra](https://bonline.digital).
Free, open source, no ads, no trackers, no upsell.

</div>

---

## Why another cleaner

The category is full of ad-funded apps that exaggerate findings, demand every permission, and hide a single **CLEAN** button over work you never see. Tidyra is built on the opposite premise: it shows you exactly what is taking space, proposes specific actions with the exact space each would free, and does nothing until you approve those specific items.

Three things it does that a typical cleaner does not:

**On-device AI.** A Gemma model you download in-app explains findings, groups files and drafts the cleanup plan. Your file names, sizes and folders never leave the phone — the model download is the only network request the app ever makes.

**Shizuku power mode, optional.** With Shizuku paired, Tidyra gets ADB-level rights and can genuinely clear other apps' caches, read `Android/data`, and uninstall without a dialog per app. Without it, every screen still works and the app tells you plainly what Android is blocking instead of pretending.

**A real undo.** Android has no recycle bin, so Tidyra keeps one. Approved deletions move to an app-owned trash with a manifest of where each file came from, and are purged on a schedule you control. Trashed and freed are reported as two different numbers, because a file in the trash still takes up space.

## What it finds

- Folder sizes across shared storage, and inside `Android/media` where chat apps keep their media
- Duplicate files, proved byte-for-byte: grouped by exact size, then an 8 KB edge hash, then a full SHA-256 — same name and same size is never treated as proof
- Per-app storage (app / data / cache) and time since you last opened each app
- Stale installers, empty folders, loose files with no folder of their own, and files over 100 MB

## What it will not do

| Blocked by Android | What Tidyra does instead |
|---|---|
| Clear another app's cache | One tap with Shizuku; otherwise deep-links you to that app's storage screen |
| Read `Android/data` or `Android/obb` (Android 11+) | Reads it with Shizuku; otherwise says so and skips it |
| Uninstall silently | Sends each app through Android's own uninstall dialog, one at a time |

It also never proposes removing system packages, never pre-selects anything personal, and never rounds a size into vagueness — 756 MB, not "about 0.7 GB".

## Permissions, and why

| Permission | Why it is needed |
|---|---|
| `MANAGE_EXTERNAL_STORAGE` | Reads folder sizes, hashes files to prove duplicates, and files loose files. The scoped MediaStore APIs cannot see folder structure or non-media files, so they cannot do this job. |
| `PACKAGE_USAGE_STATS` | Per-app storage sizes and time since last use. Without it the app shows nothing there rather than guessing. |
| `REQUEST_DELETE_PACKAGES` | Opens the system uninstall dialog for an app you selected. |
| `INTERNET` | The model download, and nothing else. |

All are requested in context, with the reason shown first — never on cold start.

## Build

Requires JDK 17 and the Android SDK (compileSdk 36).

```bash
./gradlew assembleDebug
```

Version pins that matter: AGP 8.13.2, Kotlin 2.4.21, Compose 1.11.4, material3 1.4.0, okhttp 4.12.0. Compose 1.12+ and okhttp 5.x require AGP 9.1 and compileSdk 37 — moving there is a deliberate toolchain upgrade, not a version bump.

## Status

Early but working, and exercised end to end on an emulator seeded with ~260 MB of
realistic mess (duplicates spread across three locations, stale installers, a 120 MB
video, protected backups, empty folders, loose files):

| | |
|---|---|
| Scan of shared storage | works |
| Duplicate detection, hash-verified | works — and keeps the camera original, not merely the oldest copy |
| Protected files excluded | works — chat databases, backups and key material are never offered |
| Per-item approval, trash, restore | works |
| Folder organiser, plan then apply | works |
| On-device model download, load, explain | works |
| Shizuku cache clearing and silent uninstall | written, not yet verified on hardware |

Unit tests cover the safety rules and the prompt framing: `./gradlew testDebugUnitTest`.

See [RESUME.md](RESUME.md) for exactly where it stands and [PRD.md](PRD.md) for what it is
meant to become. Screenshots live in `fastlane/metadata/android/en-US/images/`.

A note on Gemma: Google serves it behind a licence gate, so downloading it needs a Hugging
Face token. SmolLM 135M is also in the catalogue — open, tokenless, and small — so the
on-device path can be tried without an account. Its answers are correspondingly rough.

## Licence

GPL-3.0-or-later. The bundled Playfair Display is under the SIL Open Font License (see `licenses/OFL-PlayfairDisplay.txt`). Gemma models are downloaded by you, under Google's Gemma Terms of Use, and are not redistributed here.
