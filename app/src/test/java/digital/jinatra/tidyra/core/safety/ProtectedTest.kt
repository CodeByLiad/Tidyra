package digital.jinatra.tidyra.core.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * These are the rules that stand between a storage cleaner and somebody's chat history.
 * They are worth testing precisely because they are boring.
 */
class ProtectedTest {

    private val root = "/storage/emulated/0"

    @Test
    fun `chat databases are protected`() {
        assertTrue(Protected.isProtected("$root/Android/media/com.whatsapp/WhatsApp/Databases/msgstore.db.crypt14"))
        assertTrue(Protected.isProtected("$root/Android/media/org.telegram.messenger/Databases/cache4.db"))
    }

    @Test
    fun `backups and key material are protected wherever they sit`() {
        assertTrue(Protected.isProtected("$root/Download/rgb-wallet-backup.bin"))
        assertTrue(Protected.isProtected("$root/Download/vault.kdbx"))
        assertTrue(Protected.isProtected("$root/Documents/seed-phrase.txt"))
        assertTrue(Protected.isProtected("$root/Download/keystore.jks"))
    }

    @Test
    fun `game data is protected because it is often not re-downloadable`() {
        assertTrue(Protected.isProtected("$root/Android/obb/com.some.game/main.1.obb"))
    }

    @Test
    fun `ordinary media is not protected`() {
        assertFalse(Protected.isProtected("$root/DCIM/Camera/IMG_20260914_101500.jpg"))
        assertFalse(Protected.isProtected("$root/Download/holiday.mp4"))
        assertFalse(Protected.isProtected("$root/Download/some-game-v2.1-mod.apk"))
    }

    @Test
    fun `every protection states a reason`() {
        assertNotNull(Protected.reason("$root/Download/rgb-wallet-backup.bin"))
        assertNull(Protected.reason("$root/DCIM/Camera/IMG_1.jpg"))
    }

    /**
     * The regression that motivated the rule: a camera photo and the chat attachment made
     * from it are byte-identical, and the chat copy is usually older. Keeping "the oldest"
     * would delete the camera roll.
     */
    @Test
    fun `the camera original outranks every copy of it`() {
        val camera = File("$root/DCIM/Camera/IMG_20260914_101500.jpg")
        val chat = File("$root/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/IMG-20260914-WA0007.jpg")
        val download = File("$root/Download/photo-from-chat.jpg")
        val cache = File("$root/Android/data/com.example/cache/thumb.jpg")

        val ranked = listOf(chat, download, cache, camera).sortedBy { Protected.keepRank(it) }

        assertEquals(camera, ranked.first())
        assertTrue(Protected.keepRank(camera) < Protected.keepRank(chat))
        assertTrue(Protected.keepRank(download) < Protected.keepRank(cache))
    }

    @Test
    fun `tidyra never offers to uninstall itself, the system, or shizuku`() {
        assertTrue(Protected.isProtectedPackage("digital.jinatra.tidyra", isSystem = false))
        assertTrue(Protected.isProtectedPackage("digital.jinatra.tidyra.debug", isSystem = false))
        assertTrue(Protected.isProtectedPackage("moe.shizuku.privileged.api", isSystem = false))
        assertTrue(Protected.isProtectedPackage("com.google.android.gms", isSystem = false))
        assertTrue(Protected.isProtectedPackage("com.anything.at.all", isSystem = true))
        assertFalse(Protected.isProtectedPackage("com.playdigious.pol", isSystem = false))
    }
}
