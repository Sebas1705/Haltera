package es.sebas1705.haltera.core.search

import kotlin.test.Test
import kotlin.test.assertEquals

class EditDistanceTest {

    @Test
    fun `distance counts insertions, deletions, substitutions and adjacent transpositions`() {
        data class Case(val a: String, val b: String, val expected: Int)
        val cases = listOf(
            Case("deadlift", "deadlift", 0),
            Case("deadlfit", "deadlift", 1), // transposition
            Case("sentadila", "sentadilla", 1), // deletion
            Case("sentadiya", "sentadilla", 2), // substitution + deletion
            Case("dominadass", "dominada", 2),
            Case("sqaut", "squat", 1),
            Case("chiken", "chicken", 1),
            Case("brest", "breast", 1),
            Case("olvia", "oliva", 1),
            Case("press", "prensa", 2),
            Case("", "abc", 3),
            Case("abc", "", 3),
        )
        cases.forEach { (a, b, expected) -> assertEquals(expected, EditDistance.distance(a, b, max = 3), "$a → $b") }
    }

    @Test
    fun `distance stops early above the bound`() {
        assertEquals(2, EditDistance.distance("press", "squat", max = 1))
        assertEquals(2, EditDistance.distance("a", "abcdef", max = 1))
        assertEquals(1, EditDistance.distance("remo", "remos", max = 1))
        assertEquals(1, EditDistance.distance("", "", max = 0) + 1)
    }

    @Test
    fun `typo allowance grows with the term length`() {
        assertEquals(listOf(0, 0, 0, 1, 1, 1, 1, 2, 2), (1..9).map(EditDistance::allowedTypos))
    }
}
