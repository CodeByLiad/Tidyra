package digital.jinatra.tidyra.ui.format

import digital.jinatra.tidyra.core.model.Bytes
import java.util.Locale

/**
 * One way to say a size, everywhere in the app.
 *
 * Exactness is a product principle, not a detail: a cleaner that says "about 0.7 GB" where
 * it means 756 MB is rounding in its own favour. Sizes keep one decimal at GB scale and
 * none below it, and the exact byte count is always available underneath.
 */
object Format {

    private const val KB = 1024.0
    private const val MB = KB * 1024
    private const val GB = MB * 1024

    fun size(bytes: Bytes): String = size(bytes.value)

    fun size(value: Long): String {
        val v = value.toDouble()
        return when {
            value < 0 -> "—"
            v < KB -> "$value B"
            v < MB -> String.format(Locale.US, "%.0f KB", v / KB)
            v < GB -> String.format(Locale.US, "%.0f MB", v / MB)
            else -> String.format(Locale.US, "%.1f GB", v / GB)
        }
    }

    /** Spoken form for TalkBack: "756 megabytes", not "756 MB" read as letters. */
    fun spokenSize(bytes: Bytes): String {
        val v = bytes.value.toDouble()
        return when {
            bytes.value < 0 -> "unknown size"
            v < KB -> "${bytes.value} bytes"
            v < MB -> String.format(Locale.US, "%.0f kilobytes", v / KB)
            v < GB -> String.format(Locale.US, "%.0f megabytes", v / MB)
            else -> String.format(Locale.US, "%.1f gigabytes", v / GB)
        }
    }

    /**
     * A share of the device. Anything real but under 1% reads "<1%" rather than "0%",
     * which looks like a bug and tells the reader nothing.
     */
    fun percent(fraction: Float): String = when {
        fraction <= 0f -> "0%"
        fraction < 0.01f -> "<1%"
        else -> String.format(Locale.US, "%.0f%%", fraction * 100)
    }

    fun exact(bytes: Bytes): String =
        String.format(Locale.US, "%,d bytes", bytes.value)

    fun count(n: Int, singular: String, plural: String = singular + "s"): String =
        String.format(Locale.US, "%,d %s", n, if (n == 1) singular else plural)

    fun days(days: Long): String = when {
        days == Long.MAX_VALUE -> "never opened"
        days < 1 -> "used today"
        days == 1L -> "1 day ago"
        days < 30 -> "$days days ago"
        days < 365 -> "${days / 30} months ago"
        else -> "over a year ago"
    }
}

/**
 * The shared size scale.
 *
 * Every bar in Tidyra — a container on the station, a row in a findings list, the span a
 * plan would free — is drawn against the same denominator: the device's total capacity.
 * A megabyte is therefore the same visual length everywhere, and a small finding can
 * never be made to look worth acting on by being given its own axis.
 */
class SizeScale(private val totalBytes: Long) {

    /** Fraction of the whole device, clamped. Never re-normalised per screen. */
    fun fraction(bytes: Bytes): Float {
        if (totalBytes <= 0) return 0f
        return (bytes.value.toFloat() / totalBytes).coerceIn(0f, 1f)
    }

    /**
     * What a bar actually draws. A real but tiny quantity still gets a visible sliver, so
     * "present but small" never renders as "absent" — capped so the floor can never make
     * something look larger than the next real value up.
     */
    fun drawnFraction(bytes: Bytes): Float {
        val raw = fraction(bytes)
        return if (bytes.value > 0 && raw < MIN_VISIBLE) MIN_VISIBLE else raw
    }

    private companion object {
        const val MIN_VISIBLE = 0.004f
    }
}
