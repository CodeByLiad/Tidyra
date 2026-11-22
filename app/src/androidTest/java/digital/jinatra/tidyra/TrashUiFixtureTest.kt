package digital.jinatra.tidyra

import android.os.Environment
import androidx.test.platform.app.InstrumentationRegistry
import digital.jinatra.tidyra.core.trash.TrashStore
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID

/** Explicit UI-fixture preparation, never a cleanup of existing app Trash. */
class TrashUiFixtureTest {
    @Test fun prepareOwnedSamplesOnlyWhenTrashIsEmpty() = deviceTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageName.endsWith(".usbtest"))
        assumeTrue(InstrumentationRegistry.getArguments().getString("tidyra.trashUiFixture") == "true")
        val store = TrashStore(context)
        check(store.contents().isEmpty()) { "Existing QA Trash must be preserved" }
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Tidyra-Pixel-Trash-QA-${UUID.randomUUID()}")
        check(folder.mkdir())
        File(folder, ".tidyra-qa-marker").writeText("TIDYRA_OWNED_TRASH_UI_FIXTURE")
        val restore = File(folder, "QA restore.txt").apply { writeText("TIDYRA_PHONE_UI_RESTORE_ONLY") }
        val delete = File(folder, "QA delete.txt").apply { writeText("TIDYRA_PHONE_UI_DELETE_ONLY") }
        store.trash(listOf(restore.path, delete.path))
        assertEquals(2, store.contents().size)
    }
}
