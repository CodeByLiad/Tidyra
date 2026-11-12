package digital.jinatra.tidyra.core.organize

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
}
