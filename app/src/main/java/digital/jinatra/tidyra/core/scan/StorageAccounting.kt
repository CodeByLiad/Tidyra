package digital.jinatra.tidyra.core.scan

import digital.jinatra.tidyra.core.model.Bytes
import digital.jinatra.tidyra.core.model.StorageCategory
import digital.jinatra.tidyra.core.model.StorageSnapshot

/** Shared media belongs to its file type; the same bytes are subtracted from app data. */
object StorageAccounting {
    data class Group(val packageName: String, val label: String, val appBytes: Bytes, val dataBytes: Bytes, val cacheBytes: Bytes)

    fun groups(apps: List<AppInventory.AppEntry>, snapshot: StorageSnapshot?): List<Group> =
        apps.groupBy { if (it.uid >= 0) "uid:${it.uid}" else "package:${it.packageName}" }.values.map { members ->
            val first = members.first()
            val media = members.sumOf { snapshot?.sharedMediaByPackage?.get(it.packageName)?.value ?: 0 }
            Group(first.packageName,
                if (members.size == 1) first.label else "${first.label} + ${members.size - 1} (shared storage)",
                Bytes(members.maxOf { it.groupAppBytes.value }),
                Bytes((members.maxOf { it.groupDataBytes.value } - media).coerceAtLeast(0)),
                Bytes(members.maxOf { it.cacheBytes.value }))
        }

    fun categories(apps: List<AppInventory.AppEntry>, snapshot: StorageSnapshot): Map<StorageCategory, Bytes> {
        val groups = groups(apps, snapshot)
        return snapshot.byCategory.toMutableMap().apply {
            this[StorageCategory.APPS] = Bytes(groups.sumOf { it.appBytes.value } + (snapshot.byCategory[StorageCategory.APPS]?.value ?: 0))
            this[StorageCategory.APP_DATA] = Bytes(groups.sumOf { it.dataBytes.value })
        }
    }
}
