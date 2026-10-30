package net.nekomo.nex.util

object Versions {
    /** Compares dotted version strings numerically ("1.4.10" > "1.4.9"); non-numeric suffixes are ignored. */
    fun compare(a: String, b: String): Int {
        val pa = parts(a)
        val pb = parts(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val c = (pa.getOrElse(i) { 0 }).compareTo(pb.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    private fun parts(v: String): List<Long> =
        v.trim().removePrefix("v").split('.', '-', '+', '_')
            .map { seg -> seg.takeWhile { it.isDigit() } }
            .takeWhile { it.isNotEmpty() }
            .map { it.toLongOrNull() ?: 0L }

    /** Extracts "major.minor" as a double, e.g. "1.4.15" -> 1.4, "14.12" -> 14.0 is not intended; use [libMajorMinor]. */
    fun libMajorMinor(versionName: String): Pair<Int, Int>? {
        val p = parts(versionName)
        return if (p.size >= 2) p[0].toInt() to p[1].toInt() else null
    }
}
