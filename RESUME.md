# Resume Tidyra here

Checkpoint: 2026-11-16. Release 0.1.1 adds running-app and service management; minimum Android 11, compile/target SDK 36. The previous 0.1.0 release is published and remains intact.

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
