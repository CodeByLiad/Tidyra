package digital.jinatra.tidyra.core.organize

import digital.jinatra.tidyra.core.safety.AtomicJson
import digital.jinatra.tidyra.core.safety.SafeFiles
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Each planned mutation is recorded before rename; recovery only reconciles metadata. */
internal class OrganizeHistory(private val file: File?) {
    data class Record(
        val batch: String,
        val move: Organizer.Move,
        val digest: String,
        var phase: String = "prepared",
        var restorePath: String? = null,
    )

    private var memory = mutableListOf<Record>()

    fun save(records: List<Record>) {
        if (file != null) {
            val array = JSONArray()
            records.forEach { r ->
                array.put(JSONObject().put("batch", r.batch).put("from", r.move.from.path)
                    .put("to", r.move.to.path).put("size", r.move.expectedSize)
                    .put("modified", r.move.expectedModified).put("digest", r.digest)
                    .put("phase", r.phase).put("restore", r.restorePath ?: JSONObject.NULL))
            }
            AtomicJson.write(file, array.toString())
        }
        memory = records.toMutableList()
    }

    fun recover(): MutableList<Record> {
        val records = if (file != null && file.exists()) {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                val move = Organizer.Move(File(item.getString("from")), File(item.getString("to")),
                    item.getLong("size"), item.getLong("modified"))
                check(move.from.isAbsolute && move.to.isAbsolute &&
                    move.from.canonicalFile.parentFile == move.to.canonicalFile.parentFile?.parentFile) {
                    "Invalid organiser recovery path"
                }
                Record(item.getString("batch"), move, item.getString("digest"),
                    item.getString("phase"), if (item.isNull("restore")) null else item.getString("restore"))
            }.toMutableList()
        } else memory.toMutableList()
        var changed = false
        records.toList().forEach { record ->
            when (record.phase) {
                "prepared" -> {
                    if (matches(record.move.to, record)) record.phase = "moved"
                    else if (record.move.from.exists()) records.remove(record)
                    else record.phase = "moved"
                    changed = true
                }
                "restoring" -> {
                    val restored = record.restorePath?.let(::File)
                    check(restored == null || restored.canonicalFile.parentFile == record.move.from.canonicalFile.parentFile) {
                        "Invalid restore recovery path"
                    }
                    if (!record.move.to.exists() && restored != null && matches(restored, record)) records.remove(record)
                    else { record.phase = "moved"; record.restorePath = null }
                    changed = true
                }
                "moved" -> Unit
                else -> error("Unrecognised organiser recovery state")
            }
        }
        if (changed) save(records)
        return records
    }

    fun latest(records: List<Record>): Organizer.Result? {
        val batch = records.lastOrNull()?.batch ?: return null
        return Organizer.Result(records.filter { it.batch == batch }.map { it.move }, emptyList())
    }

    fun matches(file: File, record: Record): Boolean =
        file.isFile && file.length() == record.move.expectedSize &&
            SafeFiles.digest(file) == record.digest
}
