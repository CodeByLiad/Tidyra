<div align="center">

# Tidyra

**Understand your Android storage. Decide what goes. Nothing happens without your approval.**

A storage cleaner and organiser with on-device AI, built by [Jinatra](https://bonline.digital).
Free, open source, no ads, no trackers, no upsell.

[Download the signed APK](https://github.com/CodeByLiad/Tidyra/releases/latest) · Android 11+

</div>

---

## Why another cleaner

The category is full of ad-funded apps that exaggerate findings, demand every permission, and hide a single **CLEAN** button over work you never see. Tidyra is built on the opposite premise: it shows you exactly what is taking space, proposes specific actions with the exact space each would free, and does nothing until you approve those specific items.

Three things it does that a typical cleaner does not:

**On-device AI.** An optional model you download or import explains findings locally. Deterministic rules produce the cleanup plan; the model cannot choose or delete files. Your file names, sizes and folders never leave the phone — model downloads are the app's only network requests.

**Shizuku power mode, optional.** With Shizuku paired, Tidyra measures restricted folders and uninstalls individually confirmed apps. Cache-only clearing depends on Android support: a timeout or unsupported command switches the device to Storage Settings until its system build changes. Every screen remains useful without Shizuku.

**A real undo.** Android has no recycle bin, so Tidyra keeps one. Approved deletions move to an app-owned trash with a manifest of where each file came from, and are purged on a schedule you control. Trashed and freed are reported as two different numbers, because a file in the trash still takes up space.

## What it finds

- Folder sizes across shared storage, and inside `Android/media` where chat apps keep their media
- Duplicate files, proved byte-for-byte: grouped by exact size, then an 8 KB edge hash, then a full SHA-256 — same name and same size is never treated as proof
- Per-app storage (app / data / cache) and time since you last opened each app
- Stale installers, empty folders, loose files with no folder of their own, and files over 100 MB

## What it will not do

| Blocked by Android | What Tidyra does instead |
|---|---|
| Clear another app's cache | Uses cache-only clearing where Shizuku and Android support it; otherwise opens that app's storage screen |
| Read `Android/data` or `Android/obb` (Android 11+) | Measures per-folder allocated sizes with Shizuku; restricted files are not offered for deletion |
| Uninstall silently | Sends each app through Android's own uninstall dialog, one at a time |

It also never proposes removing system packages, never pre-selects anything personal, and never rounds a size into vagueness — 756 MB, not "about 0.7 GB".

## Permissions, and why

| Permission | Why it is needed |
|---|---|
| `MANAGE_EXTERNAL_STORAGE` | Reads folder sizes, hashes files to prove duplicates, and files loose files. The scoped MediaStore APIs cannot see folder structure or non-media files, so they cannot do this job. |
| `PACKAGE_USAGE_STATS` | Per-app storage sizes and time since last use. Without it the app shows nothing there rather than guessing. |
| `QUERY_ALL_PACKAGES` | Lists installed apps for storage accounting and the app review screen. |
| `REQUEST_DELETE_PACKAGES` | Opens the system uninstall dialog for an app you selected. |
| `INTERNET` | The model download, and nothing else. |

All are requested in context, with the reason shown first — never on cold start.

## Build

Requires JDK 17 or newer and the Android SDK (compileSdk 36). Runs on Android 11 or newer.

```bash
./gradlew assembleDebug
```

Version pins that matter: AGP 8.13.2, Kotlin 2.4.21, Compose 1.11.4, material3 1.4.0, okhttp 4.12.0. Compose 1.12+ and okhttp 5.x require AGP 9.1 and compileSdk 37 — moving there is a deliberate toolchain upgrade, not a version bump.

For a signed direct-APK release, configure the ignored `.signing/keystore.properties`
with `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`, or point
`TIDYRA_SIGNING_PROPERTIES` to an equivalent local properties file. Keep the key and
credentials out of Git and back them up securely: future updates need the same key.
Run `powershell -File tools/verify-release.ps1` to check tests, Android lint, the
optimized release build, and its signature. A clean clone without credentials builds
an unsigned release for an independent distributor to sign.

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
| Folder organiser, plan then apply | persistent undo; collision-safe even after restarting |
| Interrupted trash operations | reconciled on next access; uncertain recovered files require manual review |
| Shared-media storage totals | UID-grouped app totals with scanned shared media counted once |
| On-device model download, load, explain | works |
| Shizuku uninstall and restricted-folder sizes | verified on a Pixel 10 Pro with Android 17; per-app uninstall confirmation |
| Shizuku cache clearing | times out on that phone; reports failure, preserves app data, and offers Settings |

Unit tests cover the safety rules and the prompt framing: `./gradlew testDebugUnitTest`.

The 16 November release pass includes 53 JVM tests, Android lint, a signed minified
release, and opt-in optimized-device checks. The release shrinker is pinned separately
to R8 9.1.57 for Kotlin 2.4 metadata compatibility. Trash retention can be changed in
the Trash tab; new settings apply on the next launch. See `RESUME.md` for exact
verification and distribution limits. F-Droid acceptance remains pending because the
current AI runtime contains prebuilt native libraries.

See [RESUME.md](RESUME.md) for exactly where it stands and [PRD.md](PRD.md) for what it is
meant to become. Screenshots live in `fastlane/metadata/android/en-US/images/`.

A note on Gemma: Google serves it behind a licence gate, so downloading it needs a Hugging
Face token. SmolLM 135M is also in the catalogue — open, tokenless, and small — so the
on-device path can be tried without an account. Its answers are correspondingly rough.

## Licence

GPL-3.0-or-later. The bundled Playfair Display is under the SIL Open Font License (see `licenses/OFL-PlayfairDisplay.txt`). Gemma models are downloaded by you, under Google's Gemma Terms of Use, and are not redistributed here.
