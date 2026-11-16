package digital.jinatra.tidyra.core.shizuku

import digital.jinatra.tidyra.core.model.Bytes

data class RestrictedFolder(val path: String, val size: Bytes) {
    companion object {
        /** du reports allocated KiB, not logical file bytes. Only direct children qualify. */
        fun parse(output: String, root: String): List<RestrictedFolder> = output.lineSequence().mapNotNull { line ->
            val fields = line.trim().split(Regex("\\s+"), limit = 2)
            if (fields.size != 2) return@mapNotNull null
            val kib = fields[0].toLongOrNull() ?: return@mapNotNull null
            val path = fields[1].trimEnd('/')
            val child = path.removePrefix("$root/")
            if (kib < 0 || kib > Long.MAX_VALUE / 1024 || !path.startsWith("$root/") ||
                child.isEmpty() || child.contains('/') || child == "." || child == "..") return@mapNotNull null
            RestrictedFolder(path, Bytes(kib * 1024))
        }.distinctBy { it.path }.sortedByDescending { it.size.value }.toList()
    }
}
