# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

delegated: Kotlin + Jetpack Compose with Material 3 Expressive (`androidx.compose.material3`), single-module Gradle app built against compileSdk 35. Chosen because Material 3 Expressive ships natively here, and because Shizuku, MediaPipe/Gemma, `StorageStatsManager` and `UsageStatsManager` are all first-class on native Android. The user delegated this choice ("your recommendations").

## Users

Primary: a person whose Android phone is steadily filling up and who wants to understand and tidy it rather than hand it to a one-tap "booster". They are doing periodic maintenance, not emergency triage — they have space left and want order, not panic. They are suspicious of cleaner apps for good reason: the category is full of ad-driven, permission-hungry software that exaggerates findings.

Secondary: power users who will pair Shizuku to unlock ADB-level capability, and who are comfortable seeing package names, real paths and exact byte counts.

Distribution audience: open-source users who find the app on F-Droid or through the Shizuku-compatible app listings.

## Product Purpose

Tidyra shows truthfully what is consuming storage on an Android phone, proposes specific actions with the space each would free, and carries them out only on explicit per-item approval. It also files loose files in shared storage into tidy, predictable folders. Success is a user who understands their storage, frees space they agree they did not need, and never loses something they wanted.

## Positioning

Three things a neighbouring cleaner cannot truthfully copy at once:

1. **On-device AI.** A Gemma model the user downloads in-app explains findings, categorises files and drafts the cleanup plan. Nothing about the user's files leaves the phone — the only network traffic in the app is the model download itself.
2. **Shizuku power mode.** Optional ADB-level access performs real cache clearing, reads `Android/data`, and uninstalls without a dialog per app — work that is impossible for a normal Android app — while the app stays fully useful without it.
3. **Approval as the core mechanic, not a confirmation dialog.** The scan produces a reviewable plan; nothing is acted on until the user approves specific items.

Free, open source, no ads, no trackers, no upsell.

## Operating Context

- Distributed as open source (F-Droid and direct APK), listed among Shizuku-compatible apps.
- Shizuku is paired by the user out-of-app via wireless debugging or a connected computer; Tidyra detects it and adapts.
- The Gemma model is a large download (≈555 MB for Gemma 3 1B int4); users will do it over Wi-Fi, once, and may delete it later.
- Typical session: open app → scan → read findings → approve a subset → confirm → see what was freed.

## Capabilities and Constraints

Can do:
- Scan shared storage for size by folder, large files, duplicates (hash-verified), empty folders, stale installers and archives.
- Report per-app app/data/cache sizes via `StorageStatsManager`, and time-since-last-use via `UsageStatsManager` / standby bucket, to surface unused apps.
- Read and organise `Android/media/*` (reachable with All-files access) — this is where WhatsApp-family media lives.
- Move loose files into type-based subfolders, previewing the full layout before anything moves.
- Request uninstall of user-selected apps through the system package installer.
- Download, store, select and delete on-device model files; run inference locally.

Cannot do without Shizuku (hard Android limits, to be stated plainly in the UI, never worked around):
- Clear another app's cache. Without Shizuku the app deep-links to the system storage screen for that app.
- Read or delete inside another app's `Android/data` or `Android/obb` (Android 11+).
- Uninstall silently.

Platform requirements: `MANAGE_EXTERNAL_STORAGE` (All files access), `PACKAGE_USAGE_STATS` (usage access), `REQUEST_DELETE_PACKAGES`, `POST_NOTIFICATIONS`. All are requested in context, with the reason shown first, never on cold start.

Product fact that follows from the platform: **deletions on Android have no recycle bin.** Tidyra therefore keeps its own trash — approved deletions move to an app-owned trash folder and are purged on a schedule the user controls — so "undo" is real rather than a promise.

Undecided: minimum supported Android version (to be set by what the Gemma runtime and Shizuku realistically need); whether the Gemma runtime ships as MediaPipe (prebuilt `.so`, may need an F-Droid exception) or a llama.cpp backend compiled from source. The AI layer is built behind an engine interface so this can change without touching the UI.

## Brand Commitments

- Name: **Tidyra**. Company: **Jinatra**. Package: `digital.jinatra.tidyra`.
- Design language: **Material 3 Expressive**, binding, as specified by the user.
- Open source, free, no advertising, no analytics, no telemetry.

## Evidence on Hand

Real scan data from the user's own Pixel 10 Pro (Android 17), gathered over ADB in the session that preceded this build: 228 GB device, 62 GB app code, 66 GB app data, 3.8 GB cache, 2,277 hash-verified duplicate files between two Telegram clients, a 756 MB stale APK in Downloads, 1.9 GB of sent WhatsApp Business media, 235 third-party apps in Android's "rare" or "restricted" standby buckets. This is the ground truth the findings UI must handle well — including the awkward cases, like a 13 GB system AI component that must never be offered for deletion.

No testimonials, user counts, press, benchmarks or store ratings exist. None may be invented.

## Product Principles

1. **Nothing happens without approval.** The scan proposes; the user disposes. Per-item, never a single blanket "Clean" button.
2. **Always show the cost and the gain.** Every proposed action states what it frees and what is lost; every completed action states what actually happened.
3. **Tell the truth about limits.** When Android blocks something, say so and hand off — never imply a capability the app does not have, and never inflate findings to look useful.
4. **The device is the boundary.** File content, names and scan results never leave the phone. The only network call is a model download the user starts.
5. **Useful at every level of permission.** No permission, All-files only, usage access, Shizuku, AI model present or absent — each state is a working app, not a nag screen.

## Accessibility & Inclusion

Material 3 Expressive accessibility baseline: every control reaches 48dp touch target, full TalkBack labelling including sizes read as words ("756 megabytes"), state never conveyed by colour alone (the storage ring and risk levels carry text and shape too), support for large font scales and display sizes without truncation, and reduced-motion honoured for the scan and success animations.
