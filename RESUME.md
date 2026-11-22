# Resume Tidyra here

Checkpoint: 2026-11-22. Version 0.1.4 adds Shizuku-powered native Settings batches, shared operation progress, live uninstall updates and clearer Trash actions. Minimum Android 11, compile/target SDK 36.

## 0.1.4 automatic Settings batches and operation feedback

Checkpoint: 2026-11-22. Version code 5. The user requested emulator checks first;
physical Pixel verification of the new shell Settings backend is still pending.

Stock ADB Shizuku lacks INTERNAL_DELETE_CACHE_FILES. Direct-command preflight stays
in place. A confirmed selected batch can now use a shell-UID app_process helper
with platform UiAutomation, hosted by a finite special-use foreground service.
The app Accessibility service remains isAccessibilityTool=false. No disability
declaration or app-data/global-trim fallback was added. Native app identity,
unique label, localized cache action, protection policy and lock checks gate taps.
An explicit START signal is required. Cancellation sends a stop byte before pipe
closure (Shizuku may retain a duplicate descriptor); remote cleanup is asynchronous
and bounded. Shizuku also links remote-process lifetime to client binder death.
Unknown screens stay untouched. The manual queue remains explicitly selectable.

Android 17 emulator verification removed both disposable fixture caches while
preserving internal/external keep markers, rejected protected targets, deduplicated
selection, refused action without START, and preserved unrequested cache on cancel.
LiveUpdatesTest verified the visible uninstall row disappears without a scan and
checked measured Trash progress/restore using a fresh isolated cache-directory ledger.
It traverses Compose accessibility nodes rather than using framework text queries,
which do not resolve Compose virtual nodes. Existing QA Trash is never emptied by
this repeatable test. Earlier disposable preview samples remain in QA Trash unless
restored through the UI; never uninstall that QA package merely to repeat a test.
The existing deviceTest continuation harness is used by optimized tests, avoiding
test-only coroutine facade retention in the production APK. Optimized verification
passed, including automatic clearing/cancellation, live uninstall and Trash progress.
Six Shizuku regressions passed; the optional OS uninstall-dialog check was skipped.
The signed production APK completed the normal two-app batch flow with both caches
removed, both internal/external markers intact and unselected cache rows unchanged.

The new API 37 emulator requires current SDK tools (22.0 was installed). Old tools
19.0 silently created target=android-0, causing a graphics crash loop. The working
isolated AVD is Tidyra_API37_Current on emulator-5556, created with target=android-37.0
in both the AVD index and config. Do not change unrelated AVDs. Android 17's
Context KeyguardManager also fails in standalone app_process due to uninitialized
ApplicationSharedMemory; ShellCacheMain queries IWindowManager lock state directly.

Shared operation feedback covers Trash copy/restore/purge, organising/undo, scans,
app operations, model loading/import and downloads. Counts/bytes are measured and
unknown totals remain indeterminate. Package events and resume reconciliation remove
uninstalled rows/findings immediately; free capacity refreshes after uninstall and
cache batches. Scans filter a fresh installed-package set before publication.
Running reads are independent with 4s/7s limits; restricted measurement has a 12s
budget and partial-result notice. Installed-list refresh never runs dumpsys.

Trash has a fixed bottom action area, stronger outlines, warm tonal Restore buttons,
and the existing permanent-deletion confirmation. Zero-byte entries do not imply
reclaimable space. Mobbin references: Speechify Deleted files and Google Drive Trash;
the native cream/ink/crimson design remains authoritative. Gemma now explains missing
token/licence access before download and links to official licence/token pages.

87 JVM tests pass; Android lint reports no errors. Download progress uses actual
server-reported totals, with an indeterminate bar until a total is known.
Logs are under ignored build/nov22-*. Signing material remains in ignored .signing;
the original production certificate is unchanged. Do not expose or commit it.

## 0.1.3 cache review and optional automation

Checkpoint: 2026-11-17. Version code 4. Adds independent cache selection, explicit
batch confirmation, current-app progress, cancellation, persistent per-app outcomes,
and selected-package-only measurement refresh. No cache request enters file trash;
no app-data clearing or global trim fallback exists. Cache and uninstall policies
are separate, allowing ordinary system apps such as Chrome to be reviewed while
protecting core/persistent/shared-UID packages and Tidyra/Shizuku.

Root cause: AOSP PackageManagerService silently ignores callers that hold legacy
DELETE_CACHE_FILES but lack INTERNAL_DELETE_CACHE_FILES. PackageManagerShellCommand
then waits for an observer that is never notified. Shizuku now checks the remote
permission before issuing a cache command. Build fingerprint plus backend UID scopes
learned failures, permitting a switch from shell to a genuinely supported backend.
Cache help is validated before --cache-only can run. Cache calls use bounded 5-second
help/8-second action waits; the shared runner drains capped output on daemon readers
and never awaits an inherited pipe indefinitely. Cancellation stops new requests;
an operation already accepted by Android may still finish.

Manual fallback is a selected-app Settings queue with Reopen, Next/Finish and End
review. It uses Android's exported APP_STORAGE_SETTINGS route when available, with
app details fallback. Visiting Settings never means a cache was cleared. Optional
Automate Settings has a disclosure, separately enabled Accessibility permission and
batch confirmation. Its service filters events to com.android.settings, remains
idle outside an explicitly started in-memory session, shows a Cancel overlay,
requires the native entity_header_title matching a unique installed-app label and
the exact localized button2/action2 Clear cache control, and never clicks coordinates,
Clear storage, generic dialog buttons or unrecognized controls. Unsupported layouts,
languages lacking the cache label, duplicate app labels and unknown identities fail
to manual review. Screen contents are not stored or sent. Accessibility button
requests use REQUESTED outcomes, separately from Android-confirmed Shizuku results.

82 JVM tests and Android lint pass without errors. Final optimized accessibility
checks passed on both API 36 emulator and API 37 Pixel (three executed, one
platform-specific case skipped per device). The emulator actually removed both
selected fixture caches while preserving internal/external keep markers. The Pixel
hides native cache actions from this non-disability automation service: its separate
test verifies no cache/data mutation, learned unavailability and immediate repeat
fallback. This is a verified manual fallback, not successful automatic Pixel clearing.
Detection is scoped to OS fingerprint, app version, Settings version and language;
explicit consent can retry it. Never misdeclare isAccessibilityTool=true to bypass
sensitive-view restrictions. Evidence: build/cache-verify-usbRelease-emulator-5554.txt
and build/cache-verify-usbRelease-5A241FDCH0001C.txt.

Final optimized Pixel Shizuku checks passed (six executed, one optional system
uninstall-dialog case skipped), including immediate permission denial, a bounded
remote timeout, protected targets, preserved keep markers, restricted measurement
and fixture uninstall. Evidence: build/cache-release-shizuku-pixel.txt. The signed
production APK installed in place and cold-launched as 0.1.3, preserving production
data. No pre-existing .debug app or its saved trash was modified or uninstalled. Certificate remains
570253ffec64c5f15ee193b42e9d64b86c2ec37945f23c0c5b50200524da5311.

Cache selection, manual queue, consent, dark theme and larger text were checked on
the emulator. Detail navigation survives configuration changes; switch contrast was
confirmed in light and dark themes. All testing cache mutations target disposable digital.jinatra.usbfixture
and .peer only. QA accessibility grants are removed after each native test while
preserving other enabled services. The Pixel's verified generated Organise images
were removed after comparing canonical paths, markers and SHA-256. Its modified
56-byte .nomedia was preserved with the identifying marker under
Pictures/Tidyra-Organise-QA-20261116; no personal pictures were removed.

Release target: https://github.com/CodeByLiad/Tidyra/releases/tag/v0.1.3.

## 0.1.2 image and destination review

Organise now shows thumbnails with larger local previews, explicit selection (none by default), and a separate final review before Move. The final layout is deliberately minimal: a compact source-folder selector, image rows with tappable To paths, and one Review action after selecting files. Repeated headings, chips and instruction paragraphs were removed. Full From/To paths remain in final review and the larger preview. Individual or selected-group destinations can be changed with a hierarchical folder picker, including new-folder creation. The same picker can browse source albums beyond the five common top-level folders. Destination choice stays within primary shared internal storage, outside hidden, protected and Android app-managed trees. Destination conflicts stay visible as proposals and block review until resolved; they are not silently discarded or overwritten.

Custom Move records include an optional approved root. Apply and recovery validate canonical boundaries and reject symlink destinations and protected paths; old four-field moves and journals remain readable. Selected plans are captured for final review, and only that snapshot can be applied. Source metadata and destination availability are rechecked at execution. Undo preserves name collisions, survives restart, and keeps user-created folders.

LocalImageDecoder uses Android ImageDecoder off the main thread, limits image dimensions and concurrent decodes, and keeps a bounded 16 MiB memory cache. No preview network requests or disk thumbnail cache. Unsupported/corrupt files show an explicit unavailable state. File selection has accessible filename labels. Protected path checks now normalize Windows separators as well as Android separators.

64 JVM tests and full Android lint pass without errors. The optimized OrganizePreviewTest passed on the Pixel, including actual red/blue pixel checks, bounded image size, corrupt-file fallback, custom destination apply, and persisted undo. The Pixel UI pass used only the marked generated fixture under Pictures/Tidyra-Organise-QA: thumbnail and larger preview, default destination collision, new-album creation/choice, final From/To review without mutation, Back to edit, confirmed move, process restart, and undo all passed. Existing destination bytes survived. Personal pictures were not moved. Dark theme and font scale 1.3 were checked on the emulator and its font scale restored. Evidence stays local under `build/organise-*`; screenshots and device fixtures are excluded from source archives.

Reference: [Obsidian moving-a-file flow](https://mobbin.com/flows/e3934fdb-68c4-436f-9a05-97f16899771c), reviewed actual previews through Mobbin for destination navigation; implemented with native Android components and the pinned Tidyra design. [Android ImageDecoder](https://developer.android.com/reference/android/graphics/ImageDecoder) supplies local bounded decoding. Release destination: [GitHub v0.1.2](https://github.com/CodeByLiad/Tidyra/releases/tag/v0.1.2).

The final presentation removes the duplicate Organise heading, common-folder chips, explanatory paragraphs, default disabled footer, and repeated per-file action labels. Folder selection is one compact dropdown; thumbnails open the larger preview; the inline To path opens destination choice. Review appears only after selection. Refresh also works for browsed source folders, and source choice falls back to internal storage if the previous source was deleted. The signed minimal build was verified on the emulator. Pixel USB became unstable during transfer of this final presentation, so its install and removal of the marked public Pixel QA folder remain pending a stable connection. The earlier generated-image Pixel move/undo test passed and restored the fixture. Do not remove personal files to resolve USB problems.

## 0.1.1 runtime management

Apps has Installed / Running / Services views, search, and All / Downloaded / System filters. Installed metadata comes directly from PackageManager, independent of usage access and storage scans. Storage statistics remain separate. Running snapshots use Shizuku `ps -A -o UID,PID,NAME` and `dumpsys activity services`; numeric UID/user matching prevents other-profile processes being assigned to the current profile. Only service records with live ProcessRecords count; pending/restarting records do not. Foreground and cached processes are described honestly, without storage or battery-saving claims. Snapshots have timestamps and explicit refresh, including refresh after returning from Android Settings.

Stop app confirms a force-stop of the owner and all its services, then obtains a new snapshot and reports verified absence, continued activity/restart, or an unverifiable result. An actual emulator test showed `stop-service` cannot stop another app's private service even with shell access. Service rows therefore explicitly offer Stop app, rather than an unreliable individual-service control. No silent fallback broadens an approved action.

The privileged boundary rechecks installed-package policy, component ownership and current-profile scope. A service finishing during confirmation does not block the explicitly approved owner-app stop. System/persistent packages, core UIDs, Tidyra/Shizuku, shared UIDs, active default apps, enabled keyboards/accessibility services/admins, VPN providers and notification listeners are protected. Missing protection information fails closed. System apps/services can be inspected and opened in Android Settings.

57 JVM tests pass; full Android lint reports no errors. New runtime validation uses the optimized isolated APK on both the Pixel 10 Pro / API 37 and the API 36 emulator with official Shizuku and `digital.jinatra.usbfixture` only. Both device tests passed for protected-package/component refusal, service-owner stop, app stop, and preserved data; the Pixel also passed the expired-service-row regression. Evidence: `build/runtime-control-phone.txt`, `build/runtime-control-emulator.txt`. Repeat instructions and fixture source are under `tools/usb-fixture/`. Version code is now 2, signed with the original production key for in-place updates. Release packaging writes to `build/release/<version>/` to preserve older local artifacts.

References: [Android running-service visibility and process restrictions](https://developer.android.com/reference/android/app/ActivityManager), [ActivityManager shell commands](https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/am/ActivityManagerShellCommand.java).

The 0.1.1 Pixel UI check passed: Cancel left the fixture running; confirmed Stop app removed its process and preserved both internal and external keep markers. Emulator light/dark and font scale 1.3 were checked; system-service controls were effectively disabled, and the selected Services view survived restoring font scale to 1.0. The selected-control colors use Tidyra's accent with sufficient text contrast rather than the library's default purple. Captures remain local under `build/runtime-*`.

Release destination: [GitHub v0.1.1](https://github.com/CodeByLiad/Tidyra/releases/tag/v0.1.1).

Signed 0.1.1 installed successfully over production 0.1.0 on the Pixel; version code 2 and all new view/filter controls were observed. Shizuku access is enabled for production. The pre-existing differently signed `.debug` app and its saved trash remain untouched. No phone font, lock, or stay-awake settings were changed.

The sections below document the 0.1.0 foundation and its verification.

## Product and design

Read `PRD.md`, `PRODUCT.md`, `DESIGN.md`, and `.impeccable/build-notes.md`. Preserve Liad's pinned editorial direction: cream #F3EDE3, ink #15110E, dark #131110, crimson #C41733 (light) / #FF5A72 (dark), Playfair Display headlines and figures, 8dp spacing, hairline rules, no decorative cards. Use MaterialTheme colors, never a hardcoded accent. One whole-device capacity rule carries actual shares; ranked rows carry exact values. System/unmeasured storage has its own span. Proposal motion respects Compose's system animation scale. Design sidecar metadata is older than the pinned direction; refresh it separately before a future redesign.

## Safety and recovery

- Every file action requires per-item approval. Loose files route to Organise, never deletion; app actions cannot enter file trash. Protected backups, databases, and key material are excluded at scan and execution boundaries.
- Approved copies are written exclusively, synced, and committed to an atomic trash ledger before originals are removed. Store instances share a per-root lock. Damaged ledgers block mutation instead of being silently emptied. Insufficient temporary space preserves originals.
- Size/mtime and duplicate counterpart hashes are rechecked at approval. Restore and organiser undo use collision-safe numbered names; existing files are never replaced. Each saved trash version has its own ID. Failed purges remain visible.
- Copy intents precede payload writes. Restart reconciliation removes only redundant private copies whose originals still verify; missing/changed originals preserve even incomplete payloads for review. Legacy unindexed payloads become visible recovery entries. Unknown originals restore under Download/Tidyra Recovered. Recovery warnings exclude entries from automatic expiry.
- Retention is persisted: 7/14/30 days or manual-only; it runs on launch. The Trash screen explains changes before saving, shows selection totals, and separately confirms permanent deletion. Files in app-owned trash still occupy space. Uninstalling Tidyra removes that private trash, so restore it first.
- Organiser journals each move before same-volume atomic rename. The latest batch remains undoable after process death, with earlier batches retained. Undo verifies content and does not delete bucket folders. Interrupted metadata updates reconcile without deleting user files.

## Storage, AI, and permissions

App statistics are grouped by UID. Scanned Android/media ownership is subtracted from app data before file-type categories are added, preventing shared-media overlap; cache is already part of app data. Standalone installers remain in Apps. Restricted-folder allocated sizes are informational and are not added again. Inaccessible roots and partial scans remain explicit; an empty result does not claim the entire phone is clean.

AI only explains findings; deterministic rules generate plans. Model downloads register cancellation before blocking work, validate resume ranges and final length, sync partials, and never promote a known incomplete download. Imported task files use unique names and appear in the catalogue. The unsupported photo-analysis model listing was removed. Native load/generate/unload is serialized. Release shrinking retains protobuf-lite fields used reflectively by MediaPipe; otherwise the real model fails at load despite successful compilation. SmolLM is open/tokenless; Gemma needs accepted licence terms and a Hugging Face token. Model output quality is not guaranteed.

Unused media-read, notification, network-state and foreground-service permissions were removed. Usage access is checked through AppOps. App data backups are disabled. No file information is sent to a server; network access is for user-started model downloads.

## Physical-device verification

Pixel 10 Pro, Android 17/API 37. The pre-existing `digital.jinatra.tidyra.debug` has a different certificate and saved trash: it was preserved. Never uninstall it to bypass a signature mismatch. Isolated QA uses `digital.jinatra.tidyra.usbtest`; production is `digital.jinatra.tidyra`.

Shizuku verification uses only disposable `digital.jinatra.usbfixture`. Checks cover protected-package refusal, remote output/exit status after actual completion, restricted-folder sizes, cache-operation safety, and fixture uninstall. The default suite reports six tests including one skipped system-dialog case (five executed). The separate normal Android uninstall-dialog flow was previously tested with both Cancel and Uninstall. Sources and repeat instructions: `tools/usb-fixture/`.

Two hardware bugs were fixed: use ShizukuRemoteProcess.waitForTimeout rather than inherited Process.waitFor(timeout); query known user-package restricted paths as well as parent enumeration because Android can hide a newly installed child from enumeration.

Cache-only clearing does not complete on this Pixel's system build, even via direct ADB. This is a failed operation with safe fallback, not successful cache clearing. The app remembers unsupported/timeout capability against Build.FINGERPRINT and subsequently opens Storage Settings without repeating the blocking command. It never substitutes app-data clearing. Fixture internal/external keep markers survived; the repeat fallback completed within five seconds. Evidence: `build/release-shizuku-phone.txt`, including cacheClearOutcome=FAILED_SAFELY. Direct cmd package avoids leaving a pm wrapper child after timeout.

The optimized restart tests passed in two separate instrumentation sessions with force-stop between: persistent organiser undo on external storage, a new original preserved during undo, interrupted partial cleanup, legacy orphan visibility/restore, and recovery exemption from expiry. Evidence: `build/release-prepare-restart.txt`, `build/release-recover-restart.txt`.

The real SmolLM task model loaded, returned nonblank text, unloaded, and rejected a subsequent unloaded request on the optimized Pixel build. This exposed and verified the fix for the protobuf reflection failure. Evidence: `build/release-ai-phone.txt` (one passed test; 21 seconds). Its temporary model file was removed after success.

The signed production APK installed as `digital.jinatra.tidyra`, cold-launched successfully, and completed a read-only scan on the Pixel. Storage, Apps, Organise, Trash, and AI navigation, capacity before grants, permission refresh after resume, and light/dark switching were checked. No personal file action was performed. The old `.debug` package and its saved trash remain untouched.

After checking the isolated QA app's Trash screen was empty, its target and runner were uninstalled. The disposable fixture is absent. The production app remains installed; temporary model/UI-dump files were removed. This pass did not change the phone's stay-awake setting.

## Build and signing

53 JVM tests pass, zero failures/errors. Full lintDebug passes without errors; dependency/style warnings remain. R8 is pinned to 9.1.57 for Kotlin 2.4 metadata compatibility while retaining AGP 8.13.2. Other pins: Kotlin 2.4.21, Compose 1.11.4, Material3 1.4.0, OkHttp 4.12.0. JDK 17+; tested with Microsoft JDK 21.

`powershell -File tools/verify-release.ps1` runs tests, lint, optimized release build, and APK signature verification. Local production key: `.signing/tidyra-release.p12`, credentials `.signing/keystore.properties`, both ignored and excluded from source packages. RSA 4096, certificate SHA-256 `570253ffec64c5f15ee193b42e9d64b86c2ec37945f23c0c5b50200524da5311`. Securely back up this directory outside the repo before publishing: future updates need the same key. A fresh clone without credentials intentionally builds an unsigned release. TIDYRA_SIGNING_PROPERTIES can point to an alternate local properties file. Do not expose tokens/passwords or include keys in an archive.

The optimized isolated build uses `'-Ptidyra.usbReleaseTests=true' assembleUsbRelease assembleUsbReleaseAndroidTest`; release public entry points are retained across the separate test APK. All mutations require fixture opt-ins. Device UI inspection must verify the foreground is Tidyra or the expected system permission dialog before reading it. Do not inspect private apps or bypass the lock screen.

## Distribution limits and future work

Release destination: [GitHub v0.1.0](https://github.com/CodeByLiad/Tidyra/releases/tag/v0.1.0), with signed APK, source snapshot, and checksums. F-Droid acceptance is pending: MediaPipe bundles prebuilt native libraries, and a from-source backend or approved distribution arrangement remains separate work. Gemma answer quality has not been tested with the owner's gated model. Broad device compatibility, Play approval, and absolute data-loss guarantees are not claimed. The app cannot fix Android's hanging package-manager cache command; Storage Settings is the supported fallback.

Mobbin references reviewed via the connected tool: [Speechify permanent deletion](https://mobbin.com/flows/1cbfae86-f49b-4a78-ba56-d4110851ede2) and [Dropbox recovery](https://mobbin.com/flows/3041b0da-bac7-42c4-8870-3449e661f244). Applied visible selection, clear locations, separate restore/delete actions, and native irreversible-action confirmation while preserving the pinned design.

Technical references: [Shizuku process wait](https://github.com/RikkaApps/Shizuku-API/blob/master/api/src/main/java/rikka/shizuku/ShizukuRemoteProcess.java), [Android storage-stat semantics](https://developer.android.com/reference/android/app/usage/StorageStats#getDataBytes()), [Kotlin/R8 compatibility](https://developer.android.com/build/kotlin-support), [MediaPipe release shrinking issue](https://github.com/google-ai-edge/mediapipe/issues/6138).
