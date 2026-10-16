package es.sebas1705.haltera.core.search

import kotlin.test.Test
import kotlin.test.assertEquals

class TextNormalizerTest {

    @Test
    fun `fold lowercases, removes accents and turns punctuation into single spaces`() {
        val cases = mapOf(
            "Press de Banca Inclinado" to "press de banca inclinado",
            "PRÉSS  de   banca" to "press de banca",
            "Jalón al pecho" to "jalon al pecho",
            "Bíceps, Tríceps & Cuádriceps" to "biceps triceps cuadriceps",
            "Pull-Up" to "pull up",
            "Abdominal 3/4" to "abdominal 3 4",
            "Rocky Pull-Ups/Pulldowns" to "rocky pull ups pulldowns",
            "Muñeca" to "muneca",
            "Crème brûlée" to "creme brulee",
            "  (Clean Grip)  " to "clean grip",
            "" to "",
            "%_-" to "",
        )
        cases.forEach { (input, expected) -> assertEquals(expected, TextNormalizer.fold(input), input) }
    }

    @Test
    fun `terms drop stopwords in both languages and stem plurals`() {
        val cases = mapOf(
            "Press de banca con mancuernas" to listOf("press", "banca", "mancuerna"),
            "Curl de bíceps" to listOf("curl", "bicep"),
            "Biceps Curls" to listOf("bicep", "curl"),
            "Apertura con mancuerna a una mano en banco plano" to listOf("apertura", "mancuerna", "mano", "banco", "plano"),
            "Bench Press - With Bands" to listOf("bench", "press", "band"),
            "Hiperextensiones sin banco" to listOf("hiperextension", "banco"),
            "Elevaciones laterales" to listOf("elevacion", "lateral"),
            // Only stopwords: kept so the user still sees something while typing.
            "de" to listOf("de"),
            "the" to listOf("the"),
            "" to emptyList(),
        )
        cases.forEach { (input, expected) -> assertEquals(expected, TextNormalizer.terms(input), input) }
    }

    @Test
    fun `plural and singular forms meet on the same stem`() {
        val pairs = listOf(
            "curls" to "curl",
            "mancuernas" to "mancuerna",
            "dominadas" to "dominada",
            "presses" to "press",
            "flyes" to "fly",
            "flies" to "fly",
            "flye" to "fly",
            "crunches" to "crunch",
            "elevaciones" to "elevacion",
            "dorsales" to "dorsal",
            "abductores" to "abductor",
            "cables" to "cable",
            "machines" to "machine",
            "lunges" to "lunge",
            "squats" to "squat",
            "glutes" to "glute",
            "gluteos" to "gluteo",
            "biceps" to "bicep",
            "triceps" to "tricep",
            "dumbbells" to "dumbbell",
            "abs" to "ab",
            "ups" to "up",
            "aperturas" to "apertura",
            "isquiotibiales" to "isquiotibial",
        )
        pairs.forEach { (plural, singular) ->
            assertEquals(TextNormalizer.stem(singular), TextNormalizer.stem(plural), "$plural / $singular")
        }
    }

    @Test
    fun `stemming leaves short words, double s and numbers alone`() {
        listOf("press", "row", "ab", "bus", "pelvis", "gluteus", "core", "bike", "rope", "3", "45s", "de").forEach { word ->
            assertEquals(word, TextNormalizer.stem(word), word)
        }
    }
}
