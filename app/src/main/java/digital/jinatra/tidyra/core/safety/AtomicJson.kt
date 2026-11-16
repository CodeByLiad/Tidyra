package digital.jinatra.tidyra.core.safety

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Replace a complete recovery record, never a partly written one. */
object AtomicJson {
    fun write(file: File, text: String) {
        file.parentFile?.let { check(it.isDirectory || it.mkdirs()) { "Cannot create recovery folder" } }
        val pending = File(file.parentFile, file.name + ".pending")
        FileOutputStream(pending).use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        Files.move(pending.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}
