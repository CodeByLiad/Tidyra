package digital.jinatra.tidyra.core.cache

/** Cache permissions differ from uninstall permissions: ordinary system apps may have cache cleared. */
object CachePolicy {
    fun blockedReason(packageName: String, uid: Int, persistent: Boolean, sharedUid: Boolean): String? = when {
        packageName.startsWith("digital.jinatra.tidyra") || packageName == "moe.shizuku.privileged.api" -> "Tidyra or its permission service"
        uid < 0 || uid % 100_000 < 10_000 || persistent -> "Core Android component"
        sharedUid -> "Shares storage identity with another app"
        else -> null
    }
}
