package digital.jinatra.tidyra.core.model

/** Observed work only. Unknown totals stay indeterminate instead of inventing a percentage. */
data class OperationProgress(val label: String, val completed: Int = 0, val total: Int? = null,
    val current: String? = null, val bytesDone: Long? = null, val bytesTotal: Long? = null) {
    val fraction: Float? get() {
        val bytes = if (bytesTotal != null && bytesTotal > 0 && bytesDone != null)
            (bytesDone.toDouble() / bytesTotal).coerceIn(0.0, 1.0) else null
        return when {
            total != null && total > 0 && bytes != null -> ((completed + bytes) / total).toFloat().coerceIn(0f, 1f)
            total != null && total > 0 -> (completed.toFloat() / total).coerceIn(0f, 1f)
            total == null && bytes != null -> bytes.toFloat()
            else -> null
        }
    }
}
