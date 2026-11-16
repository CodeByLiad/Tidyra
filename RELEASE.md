# Tidyra 0.1.1

Signed direct-APK update for Android 11 and newer. Package: `digital.jinatra.tidyra`, version code 2. Updates 0.1.0 using the same signing key.

## Running apps and services

- Apps now has Installed, Running, and Services views, search, and All / Downloaded / System filters.
- Installed apps are listed without usage access or a storage scan. Storage sizes still require usage access and a scan.
- Shizuku provides timestamped snapshots of app processes and services with live processes. Refresh to check again; cached or foreground processes are not automatically called wasteful background activity.
- Stop app asks for confirmation, stops the selected app and all its services, and refreshes the result. Service rows explicitly stop the owning app: Android restricts stopping private services individually.
- System packages, Tidyra, Shizuku, shared identities, default apps, enabled keyboards, accessibility services, administrators, VPN providers, and notification-listener providers are protected from direct stop. Android Settings remains available.
- Stopping can interrupt unsaved work, notifications, alarms, downloads and syncing until the app is reopened. It does not delete app data, free storage, or promise battery improvements. No bulk task killer is included.

## Validation and limits

57 JVM tests pass, including process parsing, current-profile isolation, active-service detection, and UID/name matching. Android lint reports no errors. Runtime controls are verified using the optimized isolated build and a disposable app on both the Pixel 10 Pro / Android 17 and an Android 16 / API 36 emulator with official Shizuku. Checks cover protected targets, component ownership, service-owner stopping, app stopping, expired service rows, and preserved data. No personal app is stopped during testing.

The Pixel UI also passed Cancel/confirm checks with internal and external data preserved. Light/dark themes, large text, protected system controls, and tab preservation across font-size changes were checked on the emulator.

Without Shizuku, Android restricts cross-app running information and direct stop actions. Installed apps and app settings remain useful. Snapshot visibility varies across Android builds. Cache-only clearing still uses Storage Settings on the Pixel build where Android's command hangs. F-Droid acceptance remains pending because MediaPipe bundles prebuilt native libraries.

## Install

Install over the signed 0.1.0 APK to preserve its app data and trash. Do not uninstall an old Tidyra build to bypass a signature mismatch: restore any needed files from its private trash first. Development `.debug` and `.usbtest` packages remain separate.

Certificate SHA-256: `570253ffec64c5f15ee193b42e9d64b86c2ec37945f23c0c5b50200524da5311`.

The source archive excludes signing keys, credentials, phone backups, and generated device data.
