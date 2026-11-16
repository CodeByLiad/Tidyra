package digital.jinatra.tidyra.core.scan

import digital.jinatra.tidyra.core.model.*
import org.junit.Assert.*
import org.junit.Test

class StorageAccountingTest {
    private fun app(pkg: String, uid: Int, code: Long, data: Long) = AppInventory.AppEntry(
        pkg, pkg, false, Bytes(code), Bytes(data), Bytes.ZERO, 0, uid, Bytes(code), Bytes(data))
    private fun snapshot(media: Map<String, Bytes>) = StorageSnapshot(Bytes(10000), Bytes(5000),
        mapOf(StorageCategory.PHOTOS to Bytes(300), StorageCategory.APPS to Bytes(20)), 0, media)

    @Test fun `shared media is counted by type and removed from its owning app data`() {
        val result = StorageAccounting.categories(listOf(app("com.chat", 1, 100, 1000)), snapshot(mapOf("com.chat" to Bytes(300))))
        assertEquals(Bytes(700), result[StorageCategory.APP_DATA])
        assertEquals(Bytes(300), result[StorageCategory.PHOTOS])
        assertEquals(Bytes(120), result[StorageCategory.APPS])
    }

    @Test fun `shared UID storage is counted once including multiple media owners`() {
        val result = StorageAccounting.categories(listOf(app("com.one", 1, 100, 1000), app("com.two", 1, 100, 1000)),
            snapshot(mapOf("com.one" to Bytes(100), "com.two" to Bytes(200))))
        assertEquals(Bytes(120), result[StorageCategory.APPS])
        assertEquals(Bytes(700), result[StorageCategory.APP_DATA])
    }

    @Test fun `uninstalled media stays in shared categories and does not reduce another app`() {
        val result = StorageAccounting.categories(listOf(app("com.chat", 1, 100, 1000)), snapshot(mapOf("com.gone" to Bytes(300))))
        assertEquals(Bytes(1000), result[StorageCategory.APP_DATA])
    }

    @Test fun `statistics lag cannot create negative app data`() {
        val result = StorageAccounting.categories(listOf(app("com.chat", 1, 100, 10)), snapshot(mapOf("com.chat" to Bytes(300))))
        assertEquals(Bytes.ZERO, result[StorageCategory.APP_DATA])
    }
}
