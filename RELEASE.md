# Tidyra 0.1.8

Signed update for Android 11 and newer. Package `digital.jinatra.tidyra`, version code 9. Uses the original production signing key for in-place updates.

## A clear space-saved result

Automatic cache completion shows a large space-saved figure above the complete app list. The amount comes from fresh cache measurements taken before and after the operation for the apps whose requests were sent or confirmed. It is not the selected cache estimate or a change in unrelated device free space.

Failed and unattempted apps do not contribute. Duplicate package results count once. Cache growth offsets reductions. If Android cannot provide complete measurements, the screen says the amount is unavailable; an unchanged measurement shows zero with an explanation that Android may update sizes later. Cache may rebuild as apps are used.

The large figure adapts to the available width and text size. Measuring space saved remains visible in progress until the final readings arrive. The full app-by-app outcomes, Done, and Review remaining apps are retained.

## Validation status

95 unit tests pass, including missing measurements, duplicate outcomes, failures, cancellation, unchanged cache and net growth. New Pixel fixture and display tests are prepared; their final run is pending phone unlock and Shizuku authorization for the separate test package. This candidate has not yet been published as a release.

The category legend, duplicate comparisons, kept-copy protections, single progress indicator and cache-only safety checks from 0.1.7 are retained. Install in place to preserve app data and Trash. Development test packages remain separate.

Certificate SHA-256: `570253ffec64c5f15ee193b42e9d64b86c2ec37945f23c0c5b50200524da5311`.
