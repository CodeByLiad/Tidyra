package digital.jinatra.tidyra.core.cache

import org.junit.Assert.*
import org.junit.Test

class CacheUiPolicyTest {
    private val cache = CacheUiPolicy.Control(CacheUiPolicy.BUTTON, "Clear cache", "android.widget.Button", true, true, true)
    private fun check(pkg: String? = CacheUiPolicy.SETTINGS, titles: List<String> = listOf("Chosen app"),
        controls: List<CacheUiPolicy.Control> = listOf(cache), text: String = "Clear cache") =
        CacheUiPolicy.cacheControl(pkg, titles, "Chosen app", text, controls)
    @Test fun permitsOnlyTheIdentifiedCacheControl() { assertEquals(cache, check()) }
    @Test fun iconButtonsUseTheirAccessibleLabelWithoutOverridingVisibleText() {
        assertEquals("Clear cache", CacheUiPolicy.label("", "Clear cache"))
        assertEquals("Clear storage", CacheUiPolicy.label("Clear storage", "Clear cache"))
        assertEquals("", CacheUiPolicy.label(null, null))
    }
    @Test fun expressiveSettingsUsesOnlyTheDedicatedCacheActionContainer() {
        val action = cache.copy(id = CacheUiPolicy.ACTION, className = "android.widget.LinearLayout")
        assertEquals(action, check(controls = listOf(action)))
        assertNull(check(controls = listOf(action.copy(id = "com.android.settings:id/action1"))))
        assertNull(check(controls = listOf(action.copy(text = "Clear storage"))))
        assertNull(check(controls = listOf(action, action)))
    }
    @Test fun cannotTapStorageOrGenericConfirmationEvenWithMatchingPosition() {
        assertNull(check(controls = listOf(cache.copy(id = "com.android.settings:id/button1", text = "Clear storage"))))
        assertNull(check(controls = listOf(cache.copy(text = "Clear storage"))))
        assertNull(check(controls = listOf(cache.copy(id = "android:id/button1", text = "OK"))))
    }
    @Test fun wrongOrAmbiguousAppIdentityCannotAct() {
        assertNull(check(titles = listOf("Other app")))
        assertNull(check(titles = emptyList()))
        assertNull(check(titles = listOf("Chosen app", "Chosen app")))
        assertNull(check(pkg = "private.app"))
    }
    @Test fun ambiguousHiddenOrNonButtonControlsCannotAct() {
        assertNull(check(controls = listOf(cache, cache)))
        assertNull(check(controls = listOf(cache.copy(visible = false))))
        assertNull(check(controls = listOf(cache.copy(clickable = false))))
        assertNull(check(controls = listOf(cache.copy(className = "android.widget.TextView"))))
    }
    @Test fun localizedExactLabelAndDisabledStateArePreserved() {
        assertNull(check(text = ""))
        val localized = cache.copy(text = "Vider le cache", enabled = false)
        assertEquals(localized, check(controls = listOf(localized), text = "Vider le cache"))
        assertNotNull(check(controls = listOf(cache.copy(enabled = false, clickable = false))))
    }
}
