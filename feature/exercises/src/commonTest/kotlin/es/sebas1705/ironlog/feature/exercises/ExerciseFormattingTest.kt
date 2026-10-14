package es.sebas1705.ironlog.feature.exercises

import es.sebas1705.ironlog.data.models.Equipment
import es.sebas1705.ironlog.data.models.LoadType
import es.sebas1705.ironlog.feature.exercises.model.defaultLoadType
import es.sebas1705.ironlog.feature.exercises.model.formatDate
import es.sebas1705.ironlog.feature.exercises.model.formatDuration
import es.sebas1705.ironlog.feature.exercises.model.frameUrl
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class ExerciseFormattingTest {
    @Test
    fun `every equipment has the catalogue's default load type`() {
        val expected = mapOf(
            Equipment.BARBELL to LoadType.BARBELL,
            Equipment.EZ_BAR to LoadType.BARBELL,
            Equipment.TRAP_BAR to LoadType.BARBELL,
            Equipment.SMITH to LoadType.BARBELL,
            Equipment.DUMBBELL to LoadType.DUMBBELL,
            Equipment.KETTLEBELL to LoadType.DUMBBELL,
            Equipment.MEDICINE_BALL to LoadType.DUMBBELL,
            Equipment.MACHINE to LoadType.MACHINE,
            Equipment.CABLE to LoadType.MACHINE,
            Equipment.BAND to LoadType.BAND,
            Equipment.BODYWEIGHT to LoadType.BODYWEIGHT,
            Equipment.SWISS_BALL to LoadType.NONE,
            Equipment.FOAM_ROLLER to LoadType.NONE,
            Equipment.CARDIO_MACHINE to LoadType.NONE,
            Equipment.OTHER to LoadType.NONE,
            Equipment.NONE to LoadType.NONE,
        )
        assertEquals(expected, Equipment.entries.associateWith { it.defaultLoadType() })
    }

    @Test
    fun `frame urls point at the pinned free-exercise-db commit`() {
        assertEquals(
            "https://raw.githubusercontent.com/yuhonas/free-exercise-db/" +
                "f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5/exercises/Barbell_Squat/0.jpg",
            frameUrl("Barbell_Squat/0.jpg"),
        )
    }

    @Test
    fun `durations and dates`() {
        assertEquals("0:05", formatDuration(5))
        assertEquals("1:30", formatDuration(90))
        assertEquals("1:01:01", formatDuration(3_661))
        assertEquals("0:00", formatDuration(-3))
        assertEquals("2026-10-14", formatDate(1_791_936_000_000, TimeZone.UTC))
    }
}
