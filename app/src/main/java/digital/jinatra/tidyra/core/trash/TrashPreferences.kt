package digital.jinatra.tidyra.core.trash

import android.content.Context

/** Zero means keep until the user empties trash. Applied on the next app launch. */
class TrashPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("trash_settings", Context.MODE_PRIVATE)
    var retentionDays: Int
        get() = preferences.getInt("retention_days", 14)
        set(value) {
            require(value in OPTIONS)
            check(preferences.edit().putInt("retention_days", value).commit()) { "Could not save trash setting" }
        }

    companion object { val OPTIONS = listOf(0, 7, 14, 30) }
}
