package digital.jinatra.tidyra.core.cache

/** Only the native Settings cache button is eligible. Never infer intent from button position. */
object CacheUiPolicy {
    const val SETTINGS = "com.android.settings"
    const val TITLE = "$SETTINGS:id/entity_header_title"
    const val BUTTON = "$SETTINGS:id/button2"
    const val ACTION = "$SETTINGS:id/action2"
    const val ACTION_TEXT = "$SETTINGS:id/text2"
    fun label(text: String?, description: String?): String = text.orEmpty().trim().ifBlank { description.orEmpty().trim() }
    data class Control(val id: String?, val text: String, val className: String?,
        val visible: Boolean, val clickable: Boolean, val enabled: Boolean)
    fun cacheControl(packageName: String?, titles: List<String>, expectedLabel: String,
        expectedCacheText: String, controls: List<Control>): Control? {
        if (packageName != SETTINGS || expectedLabel.isBlank() || expectedCacheText.isBlank() ||
            titles.size != 1 || titles.single() != expectedLabel) return null
        return controls.singleOrNull()?.takeIf { it.text == expectedCacheText &&
            ((it.id == BUTTON && it.className == "android.widget.Button") ||
                (it.id == ACTION && it.className == "android.widget.LinearLayout")) &&
            it.visible && (it.clickable || !it.enabled) }
    }
}
