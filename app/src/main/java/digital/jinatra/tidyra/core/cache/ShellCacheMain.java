package digital.jinatra.tidyra.core.cache;

import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONObject;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/** Runs only in the user-authorized Shizuku shell process, never in the app UID. */
public final class ShellCacheMain {
    private static final String SETTINGS = "com.android.settings";
    private static final String PREFIX = "TIDYRA_CACHE:";
    private static final AtomicBoolean cancelled = new AtomicBoolean(false);

    public static void main(String[] args) {
        int uid = Process.myUid();
        if (uid != 2000 && uid != 0) { emit("error", "", "Shizuku shell access required", 0, 0); return; }
        HandlerThread thread = new HandlerThread("tidyra-cache-ui");
        UiAutomation ui = null;
        try {
            Looper.prepareMainLooper();
            thread.start();
            Class<?> connectionType = Class.forName("android.app.IUiAutomationConnection");
            Object connection = Class.forName("android.app.UiAutomationConnection").getDeclaredConstructor().newInstance();
            Constructor<UiAutomation> constructor = UiAutomation.class.getDeclaredConstructor(Looper.class, connectionType);
            constructor.setAccessible(true);
            ui = constructor.newInstance(thread.getLooper(), connection);
            UiAutomation.class.getMethod("connect", int.class).invoke(ui, UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            if (args.length == 1 && args[0].equals("--probe")) {
                emit("ready", "", "Privileged Settings automation available", 0, 0);
                return;
            }
            if (args.length < 3 || !args[0].equals("--caller") || !args[1].equals(digital.jinatra.tidyra.BuildConfig.APPLICATION_ID))
                throw new IllegalArgumentException("Start the confirmed selection from Tidyra");
            String caller = args[1];
            CountDownLatch started = new CountDownLatch(1);
            Thread control = new Thread(() -> {
                try {
                    BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
                    if (!"START".equals(input.readLine())) { cancelled.set(true); started.countDown(); return; }
                    started.countDown();
                    input.read(); // Any further input or parent EOF cancels remaining requests.
                } catch (Exception ignored) { }
                cancelled.set(true);
            }, "tidyra-parent-control");
            control.setDaemon(true); control.start();
            if (!started.await(5, TimeUnit.SECONDS) || cancelled.get()) throw new IllegalStateException("The confirming app disconnected");
            Object activityThread = Class.forName("android.app.ActivityThread").getMethod("systemMain").invoke(null);
            Context system = (Context) activityThread.getClass().getMethod("getSystemContext").invoke(activityThread);
            Context context = system.createPackageContext("com.android.shell", Context.CONTEXT_IGNORE_SECURITY);
            LinkedHashSet<String> approved = new LinkedHashSet<>();
            for (String value : java.util.Arrays.copyOfRange(args, 2, args.length)) {
                if (!value.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")) throw new IllegalArgumentException("Invalid app identity");
                approved.add(value);
            }
            if (approved.isEmpty() || approved.size() > 500) throw new IllegalArgumentException("Choose between 1 and 500 apps");
            int index = 0;
            boolean returnToCaller = true;
            for (String pkg : approved) {
                if (cancelled.get()) break;
                index++;
                emit("progress", pkg, "Opening cache settings", index, approved.size());
                String label = verifiedLabel(context, pkg);
                if (label == null) { emit("skipped", pkg, "App is protected or its identity is ambiguous", index, approved.size()); continue; }
                if (cancelled.get()) break;
                Intent intent = new Intent("com.android.settings.APP_STORAGE_SETTINGS", Uri.parse("package:" + pkg))
                        .setPackage(SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                if (intent.resolveActivity(context.getPackageManager()) == null) {
                    emit("skipped", pkg, "Native cache settings unavailable", index, approved.size()); continue;
                }
                String component = intent.resolveActivity(context.getPackageManager()).flattenToString();
                java.lang.Process launch = new ProcessBuilder("/system/bin/am", "start", "-n", component,
                        "-a", intent.getAction(), "-d", intent.getDataString(), "-f", "0x14000000")
                        .redirectErrorStream(true).start();
                try {
                    if (!launch.waitFor(3, java.util.concurrent.TimeUnit.SECONDS) || launch.exitValue() != 0)
                        throw new IllegalStateException("Android did not open the selected app's cache settings");
                } finally { launch.destroy(); launch.getInputStream().close(); }
                String result = clear(ui, context, label);
                emit(result.startsWith("REQUESTED:") || result.startsWith("STOP_REQUESTED:") ? "requested" : "skipped", pkg,
                        result.substring(result.indexOf(':') + 1), index, approved.size());
                if (result.contains("Left Settings")) returnToCaller = false;
                if (result.startsWith("STOP")) break;
            }
            emit(cancelled.get() ? "cancelled" : "done", "", "Cache review finished", index, approved.size());
            if (returnToCaller && !cancelled.get()) {
                java.lang.Process back = new ProcessBuilder("/system/bin/am", "start", "-n", caller + "/digital.jinatra.tidyra.ui.MainActivity",
                        "-f", "0x34000000").redirectErrorStream(true).start();
                try { back.waitFor(2, TimeUnit.SECONDS); } finally { back.destroy(); back.getInputStream().close(); }
            }
        } catch (Throwable error) {
            if (digital.jinatra.tidyra.BuildConfig.DEBUG) error.printStackTrace(System.err);
            Throwable cause = error.getCause() == null ? error : error.getCause();
            emit("error", "", cause.getClass().getSimpleName() + ": " + String.valueOf(cause.getMessage()), 0, 0);
        } finally {
            if (ui != null) try { UiAutomation.class.getMethod("disconnect").invoke(ui); } catch (Throwable ignored) { }
            thread.quitSafely();
            System.out.flush();
            System.exit(0);
        }
    }

    private static String verifiedLabel(Context context, String pkg) throws Exception {
        PackageManager pm = context.getPackageManager();
        ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
        String[] owners = pm.getPackagesForUid(info.uid);
        if (owners == null || CachePolicy.INSTANCE.blockedReason(pkg, info.uid,
                (info.flags & ApplicationInfo.FLAG_PERSISTENT) != 0, owners.length != 1) != null) return null;
        String label = pm.getApplicationLabel(info).toString().trim();
        if (label.isEmpty()) return null;
        int count = 0;
        for (ApplicationInfo candidate : pm.getInstalledApplications(0))
            if (pm.getApplicationLabel(candidate).toString().trim().equals(label)) count++;
        return count == 1 ? label : null;
    }

    private static String clear(UiAutomation ui, Context context, String label) throws Exception {
        Context settings = context.createPackageContext(SETTINGS, 0);
        int textId = settings.getResources().getIdentifier("clear_cache_btn_text", "string", SETTINGS);
        if (textId == 0) return "STOP:Cache action could not be identified in this language";
        String cacheText = settings.getString(textId).trim();
        long started = SystemClock.elapsedRealtime();
        boolean clicked = false;
        while (SystemClock.elapsedRealtime() - started < 12000) {
            if (cancelled.get()) return "STOP:Cancelled; remaining requests stopped";
            if (isLocked()) return "STOP:Phone locked; remaining requests stopped";
            Thread.sleep(150);
            AccessibilityNodeInfo root = ui.getRootInActiveWindow();
            if (root == null) continue;
            if (!root.refresh()) continue;
            if (!SETTINGS.contentEquals(root.getPackageName() == null ? "" : root.getPackageName())) {
                if (!clicked && SystemClock.elapsedRealtime() - started < 2000) continue;
                return "STOP:Left Settings; remaining requests stopped";
            }
            List<String> titles = new ArrayList<>();
            for (AccessibilityNodeInfo title : root.findAccessibilityNodeInfosByViewId(CacheUiPolicy.TITLE))
                if (title.isVisibleToUser()) titles.add(String.valueOf(title.getText()).trim());
            List<AccessibilityNodeInfo> controls = root.findAccessibilityNodeInfosByViewId(CacheUiPolicy.ACTION);
            if (controls.isEmpty()) controls = root.findAccessibilityNodeInfosByViewId(CacheUiPolicy.BUTTON);
            List<CacheUiPolicy.Control> candidates = new ArrayList<>();
            for (AccessibilityNodeInfo node : controls) {
                String text = CacheUiPolicy.INSTANCE.label(node.getText() == null ? null : node.getText().toString(),
                        node.getContentDescription() == null ? null : node.getContentDescription().toString());
                if (text.isEmpty() && CacheUiPolicy.ACTION.equals(node.getViewIdResourceName())) {
                    List<AccessibilityNodeInfo> labels = node.findAccessibilityNodeInfosByViewId(CacheUiPolicy.ACTION_TEXT);
                    if (labels.size() == 1 && labels.get(0).isVisibleToUser()) text = String.valueOf(labels.get(0).getText()).trim();
                }
                candidates.add(new CacheUiPolicy.Control(node.getViewIdResourceName(), text,
                        String.valueOf(node.getClassName()), node.isVisibleToUser(), node.isClickable(), node.isEnabled()));
            }
            CacheUiPolicy.Control control = CacheUiPolicy.INSTANCE.cacheControl(SETTINGS, titles, label, cacheText, candidates);
            if (control == null) continue;
            if (clicked && !control.getEnabled()) return "REQUESTED:Clear cache requested; Settings updated";
            if (!clicked && control.getEnabled() && !cancelled.get()) clicked = controls.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
            else if (!clicked && SystemClock.elapsedRealtime() - started > 2500) return "SKIPPED:Clear cache is disabled in Settings";
        }
        return clicked ? "STOP_REQUESTED:Clear cache was requested but completion was not verified" : "STOP:Native cache control could not be verified";
    }

    private static boolean isLocked() throws Exception {
        // Shell app_process is not bound as an application. Android 17's Context KeyguardManager
        // requires application shared memory; query the same system service directly instead.
        Object binder = Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "window");
        Class<?> window = Class.forName("android.view.IWindowManager");
        Object service = Class.forName("android.view.IWindowManager$Stub").getMethod("asInterface", android.os.IBinder.class).invoke(null, binder);
        return (Boolean) window.getMethod("isKeyguardLocked").invoke(service);
    }

    private static void emit(String event, String pkg, String detail, int current, int total) {
        try {
            System.out.println(PREFIX + new JSONObject().put("event", event).put("package", pkg)
                    .put("detail", detail).put("current", current).put("total", total));
            System.out.flush();
        } catch (Exception ignored) { }
    }
}
