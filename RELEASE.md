# Tidyra 0.1.3

Signed update for Android 11 and newer. Package: `digital.jinatra.tidyra`, version code 4. Uses the original production signing key for in-place updates.

## Cache selection and reliable fallback

- App cache has individual checkboxes, Select all/Deselect all, a selected count and size, and one bottom action. Nothing is preselected. Cache approval never enters the file-trash plan.
- Supported Shizuku backends can clear only the approved apps as a batch. Progress identifies the current app; Cancel stops further requests. Android-confirmed, failed and unattempted outcomes remain visible. Estimated cache totals are not reported as freed space.
- Shizuku being connected is no longer treated as proof that cache clearing is allowed. Tidyra checks `INTERNAL_DELETE_CACHE_FILES` before sending a command. Stock ADB setups missing it go directly to Settings instead of waiting for a command that Android silently ignores.
- On unsupported setups, Open selected settings starts a guided queue. Review Storage & cache, choose Clear cache, then return for Next app. Reopen and End review remain available. Visiting Settings is never reported as confirmed clearing.
- Optional Automate Settings uses a separately enabled Accessibility service after a plain-language disclosure and a confirmed app selection. A visible Cancel control stays on screen. The service checks the selected app's unique identity and the native Settings Clear cache button; it never taps Clear storage, accepts unfamiliar dialogs or uses coordinates. Unrecognized layouts remain manual. Screen content is neither stored nor sent.
- Confirmation closes before work begins. Busy actions cannot open an unusable confirmation dialog. Cache commands have bounded waits and cancellation, including when a descendant holds an output pipe open. A failed backend is remembered by Android build and server UID so switching to a supported backend can work.
- Ordinary system apps such as Chrome can be reviewed. Core/persistent components, shared identities, Tidyra and Shizuku are protected from automated cache clearing; Android Settings remains available. No app-data-clearing or global cache-trim fallback is used.
- Finishing refreshes only the selected apps' storage measurements, avoiding another whole-storage scan.
- Detail navigation stays open through theme and text-size changes, and the optional automation switch has a visible off state.

## Validation

82 JVM tests pass, and Android lint reports no errors. The optimized emulator build passed actual removal of two explicitly selected fixture caches, duplicate-selection handling, preserved internal/external keep markers, protected targets and immediate cancellation. The optimized Pixel build passed its separate hidden-controls/manual-fallback test, including an immediate repeat fallback and unchanged fixture cache/data. Each accessibility suite executes three tests and skips the one intended for the other platform outcome.

The Pixel also passed six Shizuku command/safety checks, with one optional system-uninstall-dialog test skipped. These cover bounded remote timeouts, immediate cache-permission denial and preserved app data. The signed production APK installed in place on the Pixel and cold-launched as 0.1.3. Personal app caches were not cleared by testing.

## Install and limits

Install over the earlier signed release to preserve its data and trash. Do not uninstall an old build to bypass a signature mismatch; restore anything you need from its private trash first. Development `.debug` and `.usbtest` packages remain separate.

Automatic per-app cache clearing is restricted by Android. This update does not enable it on every stock phone. A request already accepted by Android may finish after cancellation. Storage statistics can lag or caches can rebuild. Optional Accessibility automation supports recognized native Android Settings storage pages and refuses ambiguous app names or unsupported layouts. Settings button requests are reported separately from Android-confirmed Shizuku results. The manual queue remains available without special access.

The Pixel 10 Pro on Android 17 does not expose its native cache controls to this optional service. Tidyra detects and remembers that limitation, stops further automatic requests, and offers the manual queue. The service is accurately declared as automation rather than as a disability accessibility tool; it does not bypass Android's sensitive-view restrictions. Detection is retried after app/system/Settings/language changes or an explicit retry.

Certificate SHA-256: `570253ffec64c5f15ee193b42e9d64b86c2ec37945f23c0c5b50200524da5311`.

Source archives exclude signing material, credentials, phone backups, screenshots and generated device fixtures.

Implementation reference: [AOSP PackageManagerService cache-permission check](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/pm/PackageManagerService.java). Selection references reviewed through Mobbin: [Walmart removing items](https://mobbin.com/flows/b829abe5-f243-44db-99a4-25eaded4fa55) and [eBay removing items from a list](https://mobbin.com/flows/62e65a16-6414-42db-8d45-ed8998fabe29), adapted to native Android and Tidyra's existing design.
