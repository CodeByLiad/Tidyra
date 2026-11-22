# Tidyra — Product Requirements

**Owner:** Jinatra · **Status:** v0.1.2 direct-APK update · **Last updated:** 2026-11-16

---

## 1. Problem

Android phones fill up, and the tools for dealing with it are bad in two different ways.

Android's own storage screen is honest but shallow: it tells you that apps take 62 GB and stops there. It cannot tell you that 2,277 files in one app are byte-identical copies of files in another, that 235 apps have not been opened in three months, or that a 756 MB installer from August is still sitting in Downloads.

Third-party cleaners go the other way. They find more, but the category is funded by ads and retention, so it inflates findings, demands permissions it does not need, and hides the actual work behind one button. People who have been burned by these apps — the people with the most to clean — are exactly the people who will not install another one.

There is no tool that is both thorough and trustworthy.

## 2. Users

**Primary.** Someone whose phone is steadily filling and who wants to understand and tidy it rather than hand it to a one-tap booster. They are doing periodic maintenance, not emergency triage: there is space left, and the goal is order. They are suspicious of this category, with reason.

**Secondary.** Power users who will pair Shizuku for ADB-level capability, and who are comfortable seeing package names, real paths and exact byte counts.

**Distribution.** Open-source users who find the app on F-Droid or in the Shizuku-compatible app listings.

## 3. Principles

These are testable commitments, not values.

1. **Nothing happens without approval.** The scan proposes; the user disposes, per item. There is no blanket Clean button anywhere in the app.
2. **Always show the cost and the gain.** Every proposal states what it frees and what is lost. Every completed action states what actually happened, including failures.
3. **Tell the truth about limits.** Where Android blocks something, say so and hand off. Never imply a capability the app does not have; never inflate a finding to look useful.
4. **The device is the boundary.** File names, sizes, paths and scan results never leave the phone. The only network call is a model download the user starts.
5. **Useful at every permission level.** No permission, all-files only, usage access, Shizuku, model present or absent — each state is a working app, not a nag screen.

## 4. Scope — v1

### In

| Capability | Detail |
|---|---|
| Storage snapshot | Capacity and free space with no permission at all; category breakdown after a scan |
| Shared-storage scan | One cancellable walk producing categories, large files, empty folders, stale installers, loose files |
| Duplicate detection | Size → 8 KB edge hash → full SHA-256. Prefer the camera/original location, then the oldest copy within that rank. Unreadable files can never match |
| App inventory | Per-app app/data/cache size and time since last use; unused-app and cache findings |
| Running apps and services | Installed / Running / Services views, search, downloaded/system filters, timestamped manual refresh. Shizuku powers live cross-app snapshots and individually confirmed owner-app stops; system and active protected apps remain read-only |
| Approval gate | Per-finding selection, a plan total that counts overlapping items once, explicit confirmation |
| Trash | App-owned, with restore, timed purge, and interrupted-copy reconciliation. Uncertain recovery entries require manual review. Trashed and freed reported separately |
| Organiser | Image thumbnails and larger previews; explicit selection; suggested type folders with individual/bulk destination choice and new-folder creation; final From/To review before Move; persisted latest-batch undo. Never deletes or overwrites existing files |
| Shizuku mode | Permission-checked direct cache batches or privileged native Settings automation for a confirmed selection, restricted-directory reads, uninstall; manual review and separately enabled Accessibility fallback remain available |
| On-device AI | In-app model catalogue, resumable download, load/unload, explanation of findings |

### Out, for now

Cloud backup, scheduled background cleaning, a widget, root mode, photo-quality analysis (blurry/burst detection — needs the multimodal model), per-chat media management inside messaging apps, tablet and foldable layouts beyond what Material 3 gives for free.

### Non-goals, permanently

A one-tap clean. A score or rating out of 100. Notifications nagging about storage. Any analytics or telemetry. Any advertising. Anything that deletes without the user having seen the specific item.

## 5. Key flows

**First run.** Open → capacity and free space are already shown → a notice explains all-files access in plain language with the reason first → grant → **Scan** → findings appear with sizes.

**Clean.** Review findings → select specific ones → the plan total appears, counting shared items once → confirm → items move to trash → result states what moved, what failed, and that the space returns when the trash is emptied.

**Organise.** Pick or browse a source folder → identify files by thumbnail and larger preview → explicitly select files → inspect From/To paths → keep suggestions or choose/create another destination per file or for the selection → review the final paths and images → Move → undo remains available after closing or restarting. Destination conflicts block review until resolved. Existing files are never overwritten; restore-name collisions receive numbered names. Destination browsing is limited to primary shared internal storage outside protected/app-managed folders.

**AI.** Model screen → pick a model, see its size and whether this phone can run it → download (resumable) → load → findings gain plain-language explanations. Everything works without this step.

**Running apps.** Apps → Running or Services → filter/search → inspect the timestamped snapshot → choose Stop app → confirm that the entire app and all its services will stop → check the refreshed outcome. Individual private-service stopping is restricted by Android. Stopping does not free storage or promise battery improvements. Force stop may interrupt notifications, alarms, syncing and unsaved work. Without Shizuku, installed apps and Android Settings remain available.

## 6. Constraints that shape the product

| Platform fact | Consequence |
|---|---|
| No recycle bin on Android | The app keeps its own trash; "undo" has to be built, not promised |
| `Android/data` unreadable since Android 11 | Chat-app media in `Android/data` is invisible without Shizuku; `Android/media` is readable |
| Only privileged callers can clear another app's cache | Check INTERNAL_DELETE_CACHE_FILES before direct commands. Stock Shizuku shell instead uses platform UiAutomation to tap only verified native Clear cache controls in confirmed selected apps. Use a finite foreground service with progress, cancellation and parent-disconnect handling. Unknown screens and locked phones stop safely; manual Settings remains available. Never clear app data or globally trim unselected caches. |
| `MANAGE_EXTERNAL_STORAGE` is manually reviewed on Play | F-Droid and direct APK are the distribution path; Play is not a v1 target |
| MediaPipe ships prebuilt `.so` files | May conflict with F-Droid's build-from-source policy. `LlmEngine` is an interface so a llama.cpp-from-source backend can replace it without touching the UI |

## 7. Design

Editorial-minimal, pinned by the owner from a reference: warm cream ground, serif display at large sizes, a single crimson accent, hairline rules, generous whitespace, and a ranked list whose values are right-aligned and carried across by a dotted leader. Proportion is drawn once, on a single stacked rule measuring the whole device, rather than as a track per row.

Four disciplines carry over from the earlier direction round and are binding:

1. **One shared size scale** — a megabyte is the same visual length on every screen. Never re-normalised per screen to make a small finding look worth acting on.
2. **One 8dp module** — nothing sits off-grid.
3. **Tabular lining numerals, exact** — "756 MB", never "about 0.7 GB".
4. **Interchange marks** — a file claimed by two findings is drawn once and counted once.

Material 3 components and navigation throughout; 48dp touch targets; TalkBack labels that speak sizes as words; large font scales without truncation; reduced motion honoured.

## 8. Success

- A user can answer "what is taking my space?" within 10 seconds of opening the app, before granting anything.
- Every byte the app offers to free is one the user agreed to, item by item.
- No deletion is unrecoverable within the trash retention window.
- The app is useful with zero permissions granted, and says why it is limited rather than blocking.
- Zero network requests other than a user-initiated model download — verifiable by inspection, since the source is public.

## 9. Risks

| Risk | Mitigation |
|---|---|
| F-Droid rejects the MediaPipe prebuilt binaries | Engine interface allows a from-source llama.cpp backend; worst case the AI ships as a separate flavour |
| Gemma download is gated behind a Hugging Face licence | Token field plus local file import, both explained in the UI |
| Users expect one-tap cleaning and bounce | The approval gate is the product; the first screen must make the value obvious before the first tap |
| Hashing 20,000 files is slow | Three-pass detection means the full hash runs on almost nothing; the walk is cancellable |
| Trash doubles storage pressure temporarily | Trashed and freed shown as separate numbers; retention is user-controlled; purge on launch |

## 10. Roadmap after v1

Photo quality analysis with the multimodal model · scheduled scans with a notification only when something material is found · per-app media management for the chat apps that keep media in `Android/media` · tablet layout · a widget showing free space.
