package digital.jinatra.tidyra.core.runtime

/** Point-in-time evidence, never inferred from recent usage or used to promise storage savings. */
data class RunningSnapshot(
    val processes: List<RunningProcess> = emptyList(),
    val services: List<RunningService> = emptyList(),
    val processesAvailable: Boolean = false,
    val servicesAvailable: Boolean = false,
    val note: String? = null,
)

data class RunningProcess(val uid: Int, val pid: Int, val name: String)
data class RunningService(val packageName: String, val component: String, val pid: Int, val foreground: Boolean)

object RunningParser {
    fun processes(output: String, user: Int): List<RunningProcess> = output.lineSequence().mapNotNull { line ->
        val fields = line.trim().split(Regex("\\s+"), limit = 3)
        val uid = fields.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
        val pid = fields.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
        val name = fields.getOrNull(2)?.trim().orEmpty()
        if (uid / 100_000 != user || pid <= 0 || name.isEmpty()) null else RunningProcess(uid, pid, name)
    }.distinctBy { it.pid }.toList()

    fun services(output: String, user: Int): List<RunningService> {
        // Only top-level active records with a live process count. Pending/restarting
        // records and nested connection references do not establish a running service.
        val record = Regex("(?m)^\\s*\\* ServiceRecord\\{[^\\n]+")
        val matches = record.findAll(output).toList()
        return matches.mapIndexedNotNull { index, match ->
            val header = Regex("\\bu(\\d+) ([A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+)").find(match.value)
                ?: return@mapIndexedNotNull null
            if (header.groupValues[1].toIntOrNull() != user) return@mapIndexedNotNull null
            val end = matches.getOrNull(index + 1)?.range?.first ?: output.length
            val block = output.substring(match.range.last + 1, end)
            val pid = Regex("(?m)^\\s+app=ProcessRecord\\{\\S+ (\\d+):").find(block)
                ?.groupValues?.get(1)?.toIntOrNull() ?: return@mapIndexedNotNull null
            if (pid <= 0) return@mapIndexedNotNull null
            val component = header.groupValues[2]
            RunningService(component.substringBefore('/'), component, pid,
                Regex("\\bisForeground=true\\b").containsMatchIn(block))
        }.distinctBy { it.component }
    }
}
