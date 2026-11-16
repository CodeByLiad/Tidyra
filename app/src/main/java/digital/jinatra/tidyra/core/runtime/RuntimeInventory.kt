package digital.jinatra.tidyra.core.runtime

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.content.pm.ApplicationInfo
import android.view.inputmethod.InputMethodManager
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import digital.jinatra.tidyra.core.safety.Protected
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(val packageName: String, val label: String, val uid: Int,
    val processName: String, val isSystem: Boolean, val stopBlockedReason: String?) {
    fun processes(snapshot: RunningSnapshot) = snapshot.processes.filter { it.uid == uid &&
        (it.name == processName || it.name == packageName || it.name.startsWith("$packageName:")) }
}

class RuntimeInventory(private val context: Context) {
    suspend fun installed(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(0)
        // Fail closed if a protected-role lookup fails: direct stop must not risk the
        // active keyboard, accessibility service, administrator, or default phone UI.
        val protected = runCatching {
            buildSet {
                listOf(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:")), Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
                    Intent(Intent.ACTION_ASSIST)).forEach { intent ->
                    pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName?.let(::add)
                }
                listOf("android.net.VpnService", "android.service.notification.NotificationListenerService").forEach { action ->
                    pm.queryIntentServices(Intent(action), 0).forEach { add(it.serviceInfo.packageName) }
                }
                context.getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.forEach { add(it.packageName) }
                context.getSystemService(DevicePolicyManager::class.java)?.activeAdmins?.forEach { add(it.packageName) }
                context.getSystemService(AccessibilityManager::class.java)
                    ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                    ?.forEach { add(it.resolveInfo.serviceInfo.packageName) }
            }
        }.getOrNull()
        apps.map { info ->
            val system = info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val reason = when {
                protected == null -> "Protected-app checks unavailable; use Android Settings"
                Protected.isProtectedPackage(info.packageName, system) || info.uid % 100_000 < 10_000 ||
                    info.flags and ApplicationInfo.FLAG_PERSISTENT != 0 -> "System or protected app"
                info.packageName in protected -> "Active default app, keyboard, accessibility service, or administrator"
                apps.count { it.uid == info.uid } > 1 -> "Shares a process identity with other apps"
                else -> null
            }
            InstalledApp(info.packageName, pm.getApplicationLabel(info).toString(), info.uid,
                info.processName ?: info.packageName, system, reason)
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }
}
