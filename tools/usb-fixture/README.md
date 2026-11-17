# Disposable USB fixture

This standalone Android app is only for testing Tidyra on a device. It creates a
16 MiB cache file plus internal and external keep markers. Its package is
`digital.jinatra.usbfixture`; it contains no user data.

From the repository root, with the Android SDK and JDK configured:

```powershell
.\gradlew.bat -p tools/usb-fixture assembleDebug
.\gradlew.bat '-Ptidyra.usbTests=true' assembleUsbTest assembleUsbTestAndroidTest
# Optimized production-equivalent code, same isolated package:
.\gradlew.bat '-Ptidyra.usbReleaseTests=true' assembleUsbRelease assembleUsbReleaseAndroidTest
```

Choose one ADB serial explicitly. Install and launch the fixture, install
`app/build/outputs/apk/usbTest/app-usbTest.apk`, and grant Shizuku access to
**Tidyra USB Test**. This variant uses a separate package so the original Tidyra
install and its trash remain intact even when another PC signed that install.

Install `app/build/outputs/apk/androidTest/usbTest/app-usbTest-androidTest.apk`, then:

```text
adb -s SERIAL shell am instrument -w -r -e tidyra.usbFixture true -e class digital.jinatra.tidyra.UsbShizukuTest digital.jinatra.tidyra.usbtest.test/androidx.test.runner.AndroidJUnitRunner
```

The suite requires the isolated variant and an explicit opt-in. It checks remote
command completion, protected-package refusal, restricted-folder measurements,
cache clearing with keep-marker preservation, and fixture-only uninstall.
**A passing safety check does not imply cache clearing succeeded.** Read
`cacheClearOutcome`: a device timeout must be reported as a failed operation,
with Settings offered as the fallback. App data must remain intact either way.

The final system-dialog test is skipped by default. Reinstall and launch the
fixture, then run only `UsbShizukuTest#z_requestSystemDialog` with both
`tidyra.usbFixture=true` and `tidyra.systemDialog=true`; cancel/confirm the Android
dialog interactively. The default suite uninstalls the disposable fixture.

Do not uninstall an existing Tidyra package to resolve a signing-key mismatch:
its app-owned trash may contain files that need restoring.

## Running apps and services (0.1.1)

The fixture now starts a private, non-sticky `FixtureService` while its Activity is
foreground. Install and launch it immediately before running `RuntimeControlTest`
with `-e tidyra.runtimeFixture true` against the isolated optimized target. The test
checks process/service discovery, protected-package refusal, cross-package component
refusal, stopping the service's owner, relaunching, force-stopping the app, and keep-marker
preservation. Service rows intentionally stop the owning app and all its services:
shell access cannot reliably stop another app's private service individually.
No personal app is targeted. The fixture can be uninstalled after testing.

## Restart recovery

With the optimized target and test APK installed, run `ReleaseRecoveryTest#prepareForRestart`
with `-e tidyra.recoveryFixture true`, force-stop `digital.jinatra.tidyra.usbtest`,
then run `ReleaseRecoveryTest#recoverAfterRestart` with the same argument. These are
two separate instrumentation sessions. The fixture refuses to overwrite a prior QA
directory. It checks persistent undo on emulated external storage, name collisions,
partial-copy reconciliation, and legacy orphan recovery. All files belong to the
isolated app's own private or external directories.

## Optional native AI smoke test

Push the open SmolLM 135M task file (166,754,726 bytes) to
`/sdcard/Android/data/digital.jinatra.tidyra.usbtest/files/release-ai-fixture.task`.
Run `ReleaseAiTest` with `-e tidyra.aiFixture true`. It checks the real optimized
MediaPipe runtime loads, produces nonblank text, unloads, and rejects generation
without a loaded model. It removes its model fixture after success. This is a runtime
smoke test, not a claim about answer quality or every supported model/device.

The release tests use a small continuation/latch bridge so they do not depend on
coroutine facade classes removed from the target by optimization. Public tested API
boundaries and the Kotlin runtime are retained across the separate test APK.

## Optional Settings automation (0.1.3)

`CacheAutomationTest` requires `tidyra.accessibilityFixture=true`, the isolated
`.usbtest` package, and both disposable fixture packages. Build the second fixture
with `./gradlew -p tools/usb-fixture assembleCachePeer`; its package is
`digital.jinatra.usbfixture.peer` and label is `Tidyra Cache Peer`. Launch both
fixtures before testing so each creates cache plus internal/external keep markers.

The test uses UI automation with `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES`,
rebinds only the isolated Tidyra cache service using adopted shell permission, and
removes that test grant after each method. Existing enabled services are preserved.
It checks protected targets, a two-app batch with duplicate selection, preserved
markers after actual cache removal, and immediate cancellation before any click.
Never grant special access to a production package or target personal apps in this
suite. Do not run `uiautomator dump` during an active batch: its default automation
connection suppresses accessibility services and would interrupt the test.

On the tested Pixel Android 17 build, native cache actions are hidden from this
non-disability automation service. Pass `-e tidyra.expectCacheAutomation manual`:
the clearing test is skipped and a dedicated test requires detected unavailability,
an immediate repeat fallback, and unchanged cache plus keep markers. On supported
devices omit that argument: actual two-fixture cache removal must pass. These are
distinct outcomes; a successful manual-fallback test is not automatic cache clearing.
