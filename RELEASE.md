# Tidyra 0.1.2

Signed direct-APK update for Android 11 and newer. Package: `digital.jinatra.tidyra`, version code 3. Uses the same release signing key as 0.1.0 and 0.1.1.

## Organise with image and destination review

- Image thumbnails identify files before selecting them. Tap Preview for a larger image with its name, size, source, and proposed destination. Decoding is local, bounded, and cached in memory; unsupported/corrupt images show an explicit unavailable state.
- A compact source-folder selector sits above the image list. Each file has a tappable To path for changing its destination. Final review shows From and To together. No repeated page headings or instruction blocks; Review appears after selection.
- No files are selected by default. Select individual files or explicitly select all when multiple files are shown.
- Change the destination for one file or the selected group. Browse existing albums or create a new folder within primary shared internal storage. Protected and app-managed folders are excluded.
- Browse source subfolders to organise an existing album, rather than only files directly in the five common top-level folders.
- Review opens a separate page showing exactly the selected files and final destinations. Back to edit changes nothing. Only the final Move button moves files.
- Existing destination names produce visible warnings and block review until resolved. Apply rechecks sources and destinations; existing files are not replaced.
- Custom destination moves remain undoable after closing or restarting. Old undo journals remain readable. Name collisions on restore receive a new name, and user-created album folders are preserved.

## Validation

64 JVM tests pass; Android lint reports no errors. The optimized build passed real Pixel image decoding (including corrupt-image handling) and custom-folder move/undo checks. Pixel UI testing verified thumbnails, larger previews, destination conflicts, new-album selection, final From/To review without mutation, Back to edit, confirmed movement into the chosen album, and undo after force-stopping and restarting the app. Only a marked generated-image fixture was moved; personal pictures were not moved.

The image/move/undo behavior was checked on the Pixel. The final minimal presentation, Review/Edit controls, dark theme and larger text were checked on the emulator. File previews use Android's ImageDecoder and no additional network requests.

## Install and limits

Install over the signed earlier release to preserve its data and trash. Do not uninstall an old build to bypass a signature mismatch; restore anything you need from its private trash first. Development `.debug` and `.usbtest` packages remain separate.

Destination selection covers primary shared internal storage, not cloud/document providers or other storage volumes. The optional Shizuku running-app controls from 0.1.1 remain available. Cache-only clearing still falls back to Storage Settings on the tested Pixel Android build. F-Droid acceptance remains separate work because MediaPipe bundles prebuilt native libraries.

Certificate SHA-256: `570253ffec64c5f15ee193b42e9d64b86c2ec37945f23c0c5b50200524da5311`.

Source archives exclude private signing material, credentials, phone backups, screenshots, and generated device fixtures.
