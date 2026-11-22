# Tidyra 0.1.6

Signed update for Android 11 and newer. Package: `digital.jinatra.tidyra`, version code 7. Uses the original production signing key for in-place updates.

## Cleaner cache results

- Completed batches show one compact outcome summary, with individual app results available under View app results.
- Done replaces the old clear button after completion. Review remaining apps opens the refreshed inventory with nothing selected. Android Back also dismisses completed results.
- Repeated command messages no longer fill the screen. Failed or unconfirmed apps retain their explanation in the expanded list.
- Settings requests remain distinct from cache clearing confirmed by Android. No estimated bytes are presented as space freed.
- Running progress, Cancel, manual Settings review, and the existing cache-only safety protections remain available.

## Validation

87 unit tests pass, and Android lint reports no errors. Android 17 emulator tests cover a 17-app results display, expanded/collapsed details, completion actions, mixed outcomes, dark mode, larger text, and cancellation controls. A real two-fixture batch verified automatic clearing, protected targets, duplicate selection, no action without confirmation, cancellation, and preserved internal/external saved data.

The normal emulator UI selected only the two disposable fixture apps, cleared their caches, showed the compact summary, and returned to the refreshed inventory with zero selected. Personal app caches were not used for testing.

This release retains 0.1.5's Pixel-tested automatic Shizuku Settings route and progress safe-area correction. Settings layouts vary by device: unrecognized controls remain untouched. Keep the phone unlocked during automatic clearing. An operation already accepted by Android may finish after cancellation.

Install over the earlier signed release to preserve app data and Trash. Development `.debug` and `.usbtest` packages remain separate. Do not uninstall a differently signed build to bypass a signature mismatch if it contains saved Trash.

Certificate SHA-256: `570253ffec64c5f15ee193b42e9d64b86c2ec37945f23c0c5b50200524da5311`.

Release assets include the signed APK, source snapshot, and SHA-256 checksums. Source archives exclude signing material, credentials, phone backups, and generated test files.
