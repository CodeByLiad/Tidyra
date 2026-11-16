package digital.jinatra.tidyra.core.organize

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CustomDestinationTest {
    @get:Rule val temp = TemporaryFolder()
    private fun source() = File(temp.root, "Pictures/photo.png").apply { parentFile!!.mkdirs(); writeText("unique image bytes") }

    @Test fun `custom nested album is preview only until apply and survives restart undo`() = runBlocking {
        val file = source()
        val history = File(temp.root, "private/history.json")
        val organizer = Organizer(history)
        val original = organizer.plan(file.parentFile!!).moves.single()
        val move = Organizer.retarget(original, File(temp.root, "Albums/Family/Weekend"), temp.root)
        assertTrue(file.exists())
        assertFalse(move.to.parentFile!!.exists())
        val result = organizer.apply(Organizer.Plan(listOf(move), emptyList()))
        assertEquals(1, result.moved.size)
        assertFalse(file.exists())
        assertEquals("unique image bytes", move.to.readText())
        file.writeText("new original at old path")
        val restarted = Organizer(history)
        assertEquals(1, restarted.undo(restarted.pending()!!))
        assertEquals("new original at old path", file.readText())
        assertEquals("unique image bytes", File(file.parentFile, "photo-restored.png").readText())
        assertTrue(move.to.parentFile!!.isDirectory)
    }

    @Test fun `outside root and same folder destinations are rejected without writing`() {
        val file = source()
        val move = Organizer().plan(file.parentFile!!).moves.single()
        assertTrue(runCatching { Organizer.retarget(move, File(temp.root, "../outside"), temp.root) }.isFailure)
        assertTrue(runCatching { Organizer.retarget(move, file.parentFile!!, temp.root) }.isFailure)
        assertTrue(file.exists())
    }

    @Test fun `hidden protected and android managed folders cannot be picked`() {
        listOf(".secret", "Albums/Backups", "Android/data/com.example.app", "Albums/wallet").forEach {
            assertFalse(it, Organizer.destinationAllowed(temp.root, File(temp.root, it)))
        }
        assertTrue(Organizer.destinationAllowed(temp.root, File(temp.root, "Albums/Family")))
    }

    @Test fun `late destination collision never overwrites either copy`() = runBlocking {
        val file = source()
        val organizer = Organizer()
        val move = Organizer.retarget(organizer.plan(file.parentFile!!).moves.single(), File(temp.root, "Albums/Family"), temp.root)
        move.to.parentFile!!.mkdirs(); move.to.writeText("existing album photo")
        val result = organizer.apply(Organizer.Plan(listOf(move), emptyList()))
        assertTrue(result.moved.isEmpty())
        assertEquals("existing album photo", move.to.readText())
        assertEquals("unique image bytes", file.readText())
    }

    @Test fun `collision can remain in review proposals so another destination can be chosen`() {
        val file = source()
        File(file.parentFile, "Images").mkdirs()
        File(file.parentFile, "Images/photo.png").writeText("existing")
        val organizer = Organizer()
        assertTrue(organizer.plan(file.parentFile!!).moves.isEmpty())
        assertEquals(1, organizer.plan(file.parentFile!!, includeConflicts = true).moves.size)
    }

    @Test fun `a destination changed to a protected location after preview is refused`() = runBlocking {
        val file = source()
        val move = Organizer.Move(file, File(temp.root, "Backups/photo.png"), approvedRoot = temp.root)
        val result = Organizer().apply(Organizer.Plan(listOf(move), emptyList()))
        assertEquals(1, result.failed.size)
        assertTrue(file.exists())
        assertFalse(move.to.exists())
    }
}
