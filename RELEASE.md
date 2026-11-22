# Tidyra 0.1.5

Signed update for Android 11 and newer. Package: `digital.jinatra.tidyra`, version code 6. Uses the original production signing key for in-place updates.

## Pixel verification and progress-bar correction

Physical Pixel testing verified automatic two-app cache clearing, protected-target rejection, cancellation, preserved saved data, service/app stops, live uninstall updates, image previews, custom destinations and undo, recovery after process restart, and native SmolLM loading/generation/unloading. Mutations used disposable fixtures only.

The phone pass found that the shared progress footer overlapped the gesture area. It now respects the bottom system inset and uses the warm theme's neutral progress track in both light and dark modes.

The corrected signed build was verified on the Pixel in light and dark themes. Its normal UI cleared exactly two selected disposable app caches automatically, showed foreground progress, and preserved their saved data. The phone UI also passed custom-album creation, reviewed image moves, Undo after restart, selected Trash restore, cancelled deletion and confirmed Empty trash, using generated samples only.

## Automatic selected-app cache clearing

- Stock Shizuku shell setups can now step through Android Settings automatically for the apps you select and confirm. No manual visit to each app is needed on recognized layouts. Direct cache-only commands remain available where Android actually permits them.
- A finite foreground service shows notification progress and Cancel while Settings is open. Keep the phone unlocked. Cancellation, parent disconnection, locked phones and unrecognized controls stop further requests; an operation already accepted by Android may finish.
- Each action verifies the selected app's unique identity and the exact native, localized Clear cache control. Core/persistent apps, shared identities, Tidyra and Shizuku remain protected. Clear storage and global cache trimming are never fallbacks.
- Settings requests remain distinct from confirmed direct commands. Selected app measurements and actual free capacity refresh afterward; estimates are not reported as bytes freed. Manual Settings review remains available.
- The existing optional Accessibility service remains accurately declared as automation. The new Shizuku route uses Android's privileged UiAutomation tooling and does not require that separate Accessibility grant.

## Progress and clearer controls

- File cleanup, restore, permanent deletion, organising, app operations, scans, model loading/import and downloads have visible progress. Known item and byte totals are measured; unknown totals use an indeterminate bar.
- Trash keeps Select, Restore and Empty trash visible in a fixed bottom action area. Permanent deletion still requires confirmation. Zero-byte entries accurately show when there is no storage to reclaim.
- Uninstall events remove the app from lists and findings immediately and update free capacity. The old instruction to rescan after uninstall is gone.
- Running-app reads and restricted-folder measurement have shorter, bounded waits. A stalled remote cleanup cannot extend the visible command deadline.
- Gemma downloads explain the licence/token requirement before attempting a tokenless download, with links to the official licence page and read-token settings. SmolLM remains available without an account.

## Validation and limits

87 JVM tests pass, and Android lint reports no errors. Optimized Android 17 emulator tests verified two selected disposable caches were removed automatically, duplicate selection, preserved internal/external saved-data markers, protected-target rejection, no action without the parent confirmation signal, cancellation, visible uninstall updates without a rescan, and isolated Trash progress/restore. Six Shizuku regression checks passed; the optional system-uninstall-dialog check was skipped. The signed production APK also completed the normal select/confirm/automatic Settings/return flow for both disposable apps, preserving their saved data and leaving unselected rows unchanged.

Verified on a Pixel 10 Pro with Android 17. This release does not claim compatibility with every OEM Settings layout. Unrecognized screens remain untouched. Personal app caches were not used for testing.

Install over the earlier signed release to preserve app data and Trash. Development `.debug` and `.usbtest` packages remain separate. Do not uninstall an older differently signed build merely to bypass a signature mismatch if it contains saved Trash.

Certificate SHA-256: `570253ffec64c5f15ee193b42e9d64b86c2ec37945f23c0c5b50200524da5311`.

Source archives exclude signing material, credentials, phone backups and generated fixtures. Trash-flow references reviewed through Mobbin: [Speechify deleted files](https://mobbin.com/flows/55e880b9-88dd-41de-80dd-544ea74ad707) and [Google Drive Trash](https://mobbin.com/flows/3da87867-3ef8-4b47-a6ff-759c892570f2), adapted to native Android and Tidyra's existing visual design.
