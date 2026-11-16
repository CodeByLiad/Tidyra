# Tidyra 0.1.0

Signed direct-APK release for Android 11 and newer. Package: `digital.jinatra.tidyra`, version code 1.

## Changes

- Per-item review, protected-file checks, stale-scan checks, and duplicate-counterpart revalidation.
- Collision-safe trash restore, persisted retention, clear permanent-delete confirmation, and interrupted-copy recovery with manual review for uncertain files.
- Organiser undo survives closing or restarting, with existing filenames preserved.
- UID-grouped storage accounting counts scanned shared app media once.
- Shizuku restricted-folder measurements and individually confirmed uninstall. Unsupported or timed-out cache clearing switches to Storage Settings for that Android build.
- Cancellable, range-validated model downloads, visible imported models, serialized native inference, and the optimized AI runtime fix.
- Signed optimized APK, unused permissions removed, backups disabled, Android 11 minimum.

## Validation

53 JVM tests pass; full Android lint reports no errors. The optimized isolated build was tested on a Pixel 10 Pro / Android 17 for real native SmolLM inference, process-restart recovery, and Shizuku fixture safety. The normal system uninstall Cancel/Uninstall flow was also exercised. This does not guarantee every Android device or every imported model works.

Cache-only clearing fails on the tested Pixel system build; Settings is the verified fallback. No app-data clearing fallback exists. F-Droid acceptance remains separate work because MediaPipe bundles prebuilt native libraries.

## Install and preserve data

Install the APK normally. Keep existing installations and their trash if Android reports a signature mismatch. Do not uninstall an old Tidyra build until you have restored anything you need from its private trash. The production package is separate from development `.debug` and `.usbtest` packages.

Certificate SHA-256: `570253ffec64c5f15ee193b42e9d64b86c2ec37945f23c0c5b50200524da5311`.

The maintainer's local `.signing/` directory contains the private release key and credentials. Future updates must use the same key. It is excluded from Git and release archives. The source archive contains the release source snapshot; it contains no private signing material.
