# Tidyra 0.1.7

Signed update for Android 11 and newer. Package `digital.jinatra.tidyra`, version code 8. Installs over earlier signed releases using the original production key.

## Clear storage breakdown

The category bar now uses distinct colours with matching numbered rows. System and unmeasured storage and free space have their own labels. Small segments retain their true size; the rows identify segments too small to hold a number. Light and dark themes use corresponding palettes.

## Cache results shown immediately

Every processed app appears after a batch, with its icon and a concise outcome. No View app results button is required. Settings requests, confirmed clearing, failures and unconfirmed operations stay distinct. Done and Review remaining apps remain available; reviewing resets the selection.

## Compare duplicates before removal

Matching files appear together, with previews, names, locations, sizes and a clearly marked copy to keep. Tap a file for a larger image and its full location. Select only the extra copies you want to move to Trash; the selected count and size update before approval. Large groups load their preview pairs as you scroll.

Kept-copy protection also applies when another cleanup category selects the same file. Duplicate verification metadata survives overlapping findings, and the Trash layer independently refuses to remove a marked keeper. Removed copies remain recoverable in Trash until permanently deleted.

## Verification

89 unit tests pass, including new overlap/keeper retention regressions. Android checks report no lint errors. Physical Pixel Android 17 tests passed for category labels/navigation, a complete 17-app result list, mixed outcomes, completion/cancellation controls, previews, duplicate selection, dark mode and larger text. Generated duplicate images were moved into an isolated private test ledger and restored with identical SHA-256 bytes; the kept image stayed unchanged. Personal files, app caches and production Trash were not used for mutation testing.

The single persistent progress bar, gesture-area spacing, automatic cache route and manual fallback from earlier releases are retained. Automatic Settings clearing still depends on recognized native controls and an unlocked phone; unsupported screens remain untouched.

Install in place to preserve data and Trash. Development test packages remain separate. Certificate SHA-256: `570253ffec64c5f15ee193b42e9d64b86c2ec37945f23c0c5b50200524da5311`.

Assets include the signed APK, source snapshot and SHA-256 checksums. Signing material, credentials, phone backups and generated test images are excluded.
