package es.sebas1705.haltera.core.search

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Typo distance between search terms. */
object EditDistance {

    /**
     * Damerau–Levenshtein distance (optimal string alignment: insertions, deletions, substitutions and
     * transpositions of two adjacent letters cost 1) between [a] and [b], or `max + 1` as soon as it is
     * known to exceed [max]. "deadlfit" → "deadlift" is 1, "sentadila" → "sentadilla" is 1.
     */
    @Suppress("ReturnCount")
    fun distance(a: String, b: String, max: Int): Int {
        if (a == b) return 0
        if (abs(a.length - b.length) > max) return max + 1
        if (a.isEmpty() || b.isEmpty()) return max(a.length, b.length).coerceAtMost(max + 1)
        val cols = b.length + 1
        var prev2 = IntArray(cols)
        var prev = IntArray(cols) { it }
        var current = IntArray(cols)
        for (i in 1..a.length) {
            current[0] = i
            var rowMin = current[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var value = min(min(prev[j] + 1, current[j - 1] + 1), prev[j - 1] + cost)
                if (swapped(a, b, i, j)) {
                    value = min(value, prev2[j - 2] + 1)
                }
                current[j] = value
                rowMin = min(rowMin, value)
            }
            if (rowMin > max) return max + 1
            val recycled = prev2
            prev2 = prev
            prev = current
            current = recycled
        }
        return prev[b.length].coerceAtMost(max + 1)
    }

    /** The two letters before [i] in [a] are the two before [j] in [b], swapped. */
    private fun swapped(a: String, b: String, i: Int, j: Int): Boolean =
        i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]

    /**
     * Typos tolerated in a query term of [length] letters: none up to 3 letters ("row", "abs"), one up to 7
     * ("remo", "press", "pullup"), two from 8 ("sentadilla", "dominadas").
     */
    fun allowedTypos(length: Int): Int = when {
        length <= SHORT -> 0
        length <= MEDIUM -> 1
        else -> 2
    }

    private const val SHORT = 3
    private const val MEDIUM = 7
}
