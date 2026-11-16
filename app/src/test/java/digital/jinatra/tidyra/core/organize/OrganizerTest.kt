package digital.jinatra.tidyra.core.organize

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Organising moves somebody's files. It is the one operation in Tidyra that is not a
 * deletion and still changes where things live, so the rules about what it leaves alone
 * matter as much as the rules about what it never deletes.
 */
class OrganizerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val organizer = Organizer()

    @Test
    fun `undo leaves a filed copy that changed after organising alone`() = kotlinx.coroutines.runBlocking {
        val source = file("invoice.pdf")
        val result = organizer.apply(organizer.plan(folder.root))
        result.moved.single().to.writeText("a new invoice that someone edited")
        assertEquals(0, organizer.undo(result))
        assertFalse(source.exists())
        assertEquals("a new invoice that someone edited", result.moved.single().to.readText())
    }

    private fun file(name: String, bytes: Int = 16): File =
        folder.newFile(name).apply { writeBytes(ByteArray(bytes)) }

    @Test
    fun `loose files are filed by type`() {
        file("invoice.pdf")
        file("holiday.jpg")

        val plan = organizer.plan(folder.root)

        assertEquals(
            setOf("Documents/invoice.pdf", "Images/holiday.jpg"),
            plan.moves.map { "${it.to.parentFile?.name}/${it.to.name}" }.toSet(),
        )
    }

    @Test
    fun `a backup is left exactly where it is`() {
        file("msgstore.db.crypt14")
        file("rgb-wallet-backup.bin")
        file("invoice.pdf")

        val plan = organizer.plan(folder.root)

        assertEquals(listOf("invoice.pdf"), plan.moves.map { it.from.name })
        assertEquals(
            setOf("msgstore.db.crypt14", "rgb-wallet-backup.bin"),
            plan.skipped.map { it.first.name }.toSet(),
        )
        assertTrue(plan.skipped.all { it.second.startsWith("Left where it is:") })
    }

    @Test
    fun `nothing is overwritten`() {
        file("invoice.pdf")
        File(folder.root, "Documents").mkdirs()
        File(folder.root, "Documents/invoice.pdf").writeBytes(ByteArray(4))

        val plan = organizer.plan(folder.root)

        assertTrue(plan.moves.isEmpty())
        assertEquals(1, plan.skipped.size)
    }

    @Test
    fun `hidden files are left alone`() {
        file(".nomedia")

        assertTrue(organizer.plan(folder.root).moves.isEmpty())
    }

    @Test
    fun `applying a plan is undone in one step`() = kotlinx.coroutines.runBlocking {
        val source = file("holiday.jpg")

        val plan = organizer.plan(folder.root)
        val result = organizer.apply(plan)
        assertEquals(1, result.moved.size)
        assertTrue(File(folder.root, "Images/holiday.jpg").exists())

        assertEquals(1, organizer.undo(result))
        assertTrue(source.exists())
    }

    @Test
    fun `destination created after preview is preserved`() = kotlinx.coroutines.runBlocking {
        val source = file("invoice.pdf")
        source.writeText("original invoice")
        val plan = organizer.plan(folder.root)
        val target = plan.moves.single().to
        target.parentFile!!.mkdirs()
        target.writeText("new invoice")
        val result = organizer.apply(plan)
        assertTrue(result.moved.isEmpty())
        assertEquals(1, result.failed.size)
        assertEquals("original invoice", source.readText())
        assertEquals("new invoice", target.readText())
    }

    @Test
    fun `undo preserves a replacement at the original path`() = kotlinx.coroutines.runBlocking {
        val source = file("holiday.jpg").apply { writeText("old photo") }
        val result = organizer.apply(organizer.plan(folder.root))
        source.writeText("new photo")
        File(folder.root, "holiday-restored.jpg").writeText("another photo")
        assertEquals(1, organizer.undo(result))
        assertEquals("new photo", source.readText())
        assertEquals("another photo", File(folder.root, "holiday-restored.jpg").readText())
        assertEquals("old photo", File(folder.root, "holiday-restored-2.jpg").readText())
    }
}
