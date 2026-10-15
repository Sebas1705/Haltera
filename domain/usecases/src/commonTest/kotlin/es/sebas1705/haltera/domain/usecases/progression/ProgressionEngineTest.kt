package es.sebas1705.ironlog.domain.usecases.progression

import es.sebas1705.ironlog.data.models.LoadIncrement
import es.sebas1705.ironlog.data.models.Prescription
import es.sebas1705.ironlog.data.models.ProgressionRule
import es.sebas1705.ironlog.data.models.RoutineSlot
import es.sebas1705.ironlog.data.models.SessionExercise
import es.sebas1705.ironlog.data.models.SetKind
import es.sebas1705.ironlog.data.models.SetLog
import es.sebas1705.ironlog.data.models.SlotTarget
import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressionEngineTest {
    private val barbell = "bench-press.barbell"
    private val dumbbell = "bench-press.dumbbell"
    private val plus2500 = LoadIncrement.Weight(2_500)

    private fun slot(
        rule: ProgressionRule,
        prescription: Prescription = Prescription.RepRange(8, 12),
        sets: Int = 3,
    ) = RoutineSlot(
        id = "slot",
        dayId = "day",
        position = 0,
        target = SlotTarget.Flexible("bench-press"),
        sets = sets,
        prescription = prescription,
        progression = rule,
    )

    private var counter = 0

    private fun set(reps: Int?, load: Long?, kind: SetKind = SetKind.WORKING, completed: Boolean = true, duration: Int? = null) =
        SetLog(
            id = "set${counter++}",
            sessionExerciseId = "e",
            position = 0,
            kind = kind,
            reps = reps,
            loadGrams = load,
            durationS = duration,
            completed = completed
        )

    private fun session(vararg sets: SetLog, variant: String = barbell, slotId: String? = "slot") =
        SessionExercise(
            id = "e${counter++}",
            sessionId = "s",
            position = 0,
            variantId = variant,
            slotId = slotId,
            sets = sets.toList()
        )

    private fun atTop(load: Long, variant: String = barbell) = session(
        set(12, load),
        set(12, load),
        set(12, load),
        variant = variant
    )

    private fun belowTop(load: Long) = session(set(12, load), set(11, load), set(10, load))

    private val double1 = ProgressionRule.DoubleProgression(sessionsAtTop = 1, increment = plus2500)
    private val double2 = ProgressionRule.DoubleProgression(sessionsAtTop = 2, increment = plus2500)

    @Test
    fun `no history is first time`() {
        assertEquals(Suggestion.FirstTime, ProgressionEngine.suggest(slot(double1), barbell, emptyList()))
    }

    @Test
    fun `double progression increases when every working set reached the top`() {
        val suggestion = ProgressionEngine.suggest(slot(double1), barbell, listOf(atTop(60_000)))
        assertEquals(Suggestion.Increase(60_000, 62_500, IncreaseReason.TOP_OF_RANGE), suggestion)
    }

    @Test
    fun `double progression keeps the load when a set missed the top`() {
        assertEquals(
            Suggestion.Keep(60_000),
            ProgressionEngine.suggest(slot(double1), barbell, listOf(belowTop(60_000)))
        )
    }

    @Test
    fun `double progression needs X sessions in a row at the same load`() {
        val rule = slot(double2)
        assertEquals(Suggestion.Keep(60_000), ProgressionEngine.suggest(rule, barbell, listOf(atTop(60_000))))
        assertEquals(
            Suggestion.Keep(60_000),
            ProgressionEngine.suggest(rule, barbell, listOf(atTop(60_000), belowTop(60_000)))
        )
        assertEquals(
            Suggestion.Keep(60_000),
            ProgressionEngine.suggest(rule, barbell, listOf(atTop(60_000), atTop(57_500)))
        )
        assertEquals(
            Suggestion.Increase(60_000, 62_500, IncreaseReason.TOP_OF_RANGE),
            ProgressionEngine.suggest(rule, barbell, listOf(atTop(60_000), atTop(60_000), belowTop(60_000))),
        )
    }

    @Test
    fun `fewer working sets than prescribed do not progress`() {
        val twoSets = session(set(12, 60_000), set(12, 60_000))
        assertEquals(Suggestion.Keep(60_000), ProgressionEngine.suggest(slot(double1), barbell, listOf(twoSets)))
    }

    @Test
    fun `warm-up, drop and uncompleted sets are ignored`() {
        val withExtras = session(
            set(5, 20_000, SetKind.WARMUP),
            set(12, 60_000),
            set(12, 60_000),
            set(12, 60_000),
            set(6, 40_000, SetKind.DROP),
            set(3, 60_000, completed = false),
        )
        assertEquals(
            Suggestion.Increase(60_000, 62_500, IncreaseReason.TOP_OF_RANGE),
            ProgressionEngine.suggest(slot(double1), barbell, listOf(withExtras)),
        )
    }

    @Test
    fun `a session with only warm-ups does not count`() {
        val warmupsOnly = session(set(5, 20_000, SetKind.WARMUP))
        assertEquals(
            Suggestion.Increase(60_000, 62_500, IncreaseReason.TOP_OF_RANGE),
            ProgressionEngine.suggest(slot(double1), barbell, listOf(warmupsOnly, atTop(60_000))),
        )
    }

    @Test
    fun `variants are isolated in a flexible slot`() {
        val history = listOf(atTop(24_000, dumbbell), atTop(60_000), atTop(24_000, dumbbell), atTop(60_000))
        assertEquals(
            Suggestion.Increase(60_000, 62_500, IncreaseReason.TOP_OF_RANGE),
            ProgressionEngine.suggest(slot(double2), barbell, history),
        )
        assertEquals(
            Suggestion.Increase(24_000, 26_500, IncreaseReason.TOP_OF_RANGE),
            ProgressionEngine.suggest(slot(double2), dumbbell, history),
        )
    }

    @Test
    fun `a dumbbell session does not reset the barbell streak`() {
        val history = listOf(belowTop(24_000).copy(variantId = dumbbell), atTop(60_000))
        assertEquals(
            Suggestion.Increase(60_000, 62_500, IncreaseReason.TOP_OF_RANGE),
            ProgressionEngine.suggest(slot(double1), barbell, history),
        )
    }

    @Test
    fun `history of other slots is ignored`() {
        val other = atTop(60_000).copy(slotId = "other")
        assertEquals(Suggestion.FirstTime, ProgressionEngine.suggest(slot(double1), barbell, listOf(other)))
    }

    @Test
    fun `linear increases when the target is hit`() {
        val fiveByFive = slot(ProgressionRule.Linear(plus2500, everyNSessions = 1), Prescription.Reps(5), sets = 5)
        val done = session(*Array(5) { set(5, 100_000) })
        val missed = session(*Array(4) { set(5, 100_000) }, set(4, 100_000))
        assertEquals(
            Suggestion.Increase(100_000, 102_500, IncreaseReason.TARGET_HIT),
            ProgressionEngine.suggest(fiveByFive, "squat.barbell", listOf(done.copy(variantId = "squat.barbell"))),
        )
        assertEquals(
            Suggestion.Keep(100_000),
            ProgressionEngine.suggest(fiveByFive, "squat.barbell", listOf(missed.copy(variantId = "squat.barbell"))),
        )
    }

    @Test
    fun `linear every N sessions and rep range uses the minimum`() {
        val rule =
            slot(ProgressionRule.Linear(LoadIncrement.Weight(5_000), everyNSessions = 2), Prescription.RepRange(8, 12))
        val atMin = session(set(8, 80_000), set(8, 80_000), set(9, 80_000))
        assertEquals(Suggestion.Keep(80_000), ProgressionEngine.suggest(rule, barbell, listOf(atMin)))
        assertEquals(
            Suggestion.Increase(80_000, 85_000, IncreaseReason.TARGET_HIT),
            ProgressionEngine.suggest(rule, barbell, listOf(atMin, atMin)),
        )
    }

    @Test
    fun `duration prescriptions progress on reaching the time`() {
        val rule = slot(double1, Prescription.Duration(60), sets = 2)
        val held = session(set(null, 10_000, duration = 60), set(null, 10_000, duration = 65))
        assertEquals(
            Suggestion.Increase(10_000, 12_500, IncreaseReason.TOP_OF_RANGE),
            ProgressionEngine.suggest(rule, barbell, listOf(held)),
        )
    }

    @Test
    fun `step increments and no rule`() {
        val bands = slot(ProgressionRule.DoubleProgression(1, LoadIncrement.Steps(1)))
        assertEquals(
            Suggestion.Increase(3, 4, IncreaseReason.TOP_OF_RANGE),
            ProgressionEngine.suggest(bands, barbell, listOf(atTop(3)))
        )
        assertEquals(
            Suggestion.Keep(60_000),
            ProgressionEngine.suggest(slot(ProgressionRule.None), barbell, listOf(atTop(60_000)))
        )
    }

    @Test
    fun `sets without load keep a null load`() {
        val bodyweight = session(set(12, null), set(12, null), set(12, null))
        assertEquals(Suggestion.Keep(null), ProgressionEngine.suggest(slot(double1), barbell, listOf(bodyweight)))
    }

    @Test
    fun `mixed loads in a session use the lowest`() {
        assertEquals(57_500L, ProgressionEngine.workingLoad(session(set(12, 60_000), set(12, 57_500))))
    }
}
