package es.sebas1705.ironlog.domain.usecases.routinescore

import es.sebas1705.ironlog.data.models.DayKind
import es.sebas1705.ironlog.data.models.Equipment
import es.sebas1705.ironlog.data.models.Exercise
import es.sebas1705.ironlog.data.models.ExerciseCategory
import es.sebas1705.ironlog.data.models.ExerciseSource
import es.sebas1705.ironlog.data.models.ExerciseVariant
import es.sebas1705.ironlog.data.models.Level
import es.sebas1705.ironlog.data.models.LoadIncrement
import es.sebas1705.ironlog.data.models.LoadType
import es.sebas1705.ironlog.data.models.LocalizedSteps
import es.sebas1705.ironlog.data.models.LocalizedText
import es.sebas1705.ironlog.data.models.Muscle
import es.sebas1705.ironlog.data.models.Prescription
import es.sebas1705.ironlog.data.models.ProgressionRule
import es.sebas1705.ironlog.data.models.Routine
import es.sebas1705.ironlog.data.models.RoutineDay
import es.sebas1705.ironlog.data.models.RoutineSlot
import es.sebas1705.ironlog.data.models.RoutineTemplate
import es.sebas1705.ironlog.data.models.SessionExercise
import es.sebas1705.ironlog.data.models.SetLog
import es.sebas1705.ironlog.data.models.SlotSpec
import es.sebas1705.ironlog.data.models.SlotTarget
import es.sebas1705.ironlog.data.models.TemplateDay
import es.sebas1705.ironlog.data.models.TrackingType
import es.sebas1705.ironlog.data.models.WorkoutSession
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutineScorerTest {

    // region Synthetic catalogue and routines

    private fun exercise(
        id: String,
        primary: List<Muscle>,
        secondary: List<Muscle> = emptyList(),
        category: ExerciseCategory = ExerciseCategory.STRENGTH,
    ) = Exercise(
        id = id,
        name = LocalizedText.same(id),
        category = category,
        level = Level.BEGINNER,
        primaryMuscles = primary,
        secondaryMuscles = secondary,
        instructions = LocalizedSteps.EMPTY,
        trackingType = TrackingType.WEIGHT_REPS,
        source = ExerciseSource.CATALOG,
        variants = listOf(ExerciseVariant("$id.barbell", id, Equipment.BARBELL, LoadType.BARBELL)),
    )

    private val bench = exercise("bench-press", listOf(Muscle.CHEST), listOf(Muscle.SHOULDERS, Muscle.TRICEPS))
    private val row = exercise("bent-over-row", listOf(Muscle.MIDDLE_BACK), listOf(Muscle.LATS, Muscle.BICEPS))
    private val press = exercise("overhead-press", listOf(Muscle.SHOULDERS), listOf(Muscle.TRICEPS))
    private val squat = exercise("squat", listOf(Muscle.QUADRICEPS), listOf(Muscle.GLUTES, Muscle.HAMSTRINGS))
    private val rdl = exercise("romanian-deadlift", listOf(Muscle.HAMSTRINGS), listOf(Muscle.GLUTES, Muscle.LOWER_BACK))
    private val curl = exercise("biceps-curl", listOf(Muscle.BICEPS))
    private val pushdown = exercise("triceps-pushdown", listOf(Muscle.TRICEPS))
    private val calf = exercise("standing-calf-raise", listOf(Muscle.CALVES))
    private val crunch = exercise("crunches", listOf(Muscle.ABDOMINALS))
    private val thrust = exercise("hip-thrust", listOf(Muscle.GLUTES), listOf(Muscle.HAMSTRINGS))
    private val run = exercise("running", listOf(Muscle.QUADRICEPS), category = ExerciseCategory.CARDIO)

    private val catalogue =
        ScoringCatalogue(listOf(bench, row, press, squat, rdl, curl, pushdown, calf, crunch, thrust, run))

    private fun spec(
        exercise: Exercise,
        sets: Int = 3,
        min: Int = 8,
        max: Int = 12,
        rest: Int = 90,
        progression: ProgressionRule = ProgressionRule.DoubleProgression(1, LoadIncrement.Weight(2_500)),
    ) = SlotSpec(
        target = SlotTarget.Fixed("${exercise.id}.barbell"),
        sets = sets,
        prescription = Prescription.RepRange(min, max),
        restBetweenSetsS = rest,
        restAfterS = rest,
        progression = progression,
    )

    private class Day(val name: String, val kind: DayKind, val slots: List<SlotSpec>)

    private fun train(name: String, vararg slots: SlotSpec) = Day(name, DayKind.TRAINING, slots.toList())

    private val rest = Day("Rest", DayKind.REST, emptyList())

    private fun routine(vararg days: Day) = Routine(
        id = "r",
        name = "Test",
        days = days.mapIndexed { d, day ->
            RoutineDay(
                id = "d$d",
                routineId = "r",
                position = d,
                name = day.name,
                kind = day.kind,
                slots = day.slots.mapIndexed { s, spec ->
                    RoutineSlot(
                        id = "s$d-$s",
                        dayId = "d$d",
                        position = s,
                        target = spec.target,
                        sets = spec.sets,
                        prescription = spec.prescription,
                        restBetweenSetsS = spec.restBetweenSetsS,
                        restAfterS = spec.restAfterS,
                        progression = spec.progression,
                    )
                },
            )
        },
    )

    /** A sound upper/lower routine: major groups near 10–20 sets, twice a week, balanced, with rest days. */
    private val upper = train(
        "Upper",
        spec(bench, 4, 5, 8),
        spec(row, 4, 6, 10),
        spec(press, 3),
        spec(curl, 3),
        spec(pushdown, 3),
    )
    private val lower = train(
        "Lower",
        spec(squat, 4, 5, 8),
        spec(rdl, 4),
        spec(thrust, 3),
        spec(calf, 4, 10, 15),
        spec(crunch, 3, 12, 15),
    )
    private val upperLower = routine(upper, lower, rest, upper, lower, rest, rest)

    private fun score(routine: Routine, sessions: List<WorkoutSession> = emptyList(), now: Long = 0L) =
        RoutineScorer.score(routine, catalogue, sessions, now)

    private fun RoutineScore.criterion(id: CriterionId) = criteria.first { it.id == id }

    private fun RoutineScore.criterionOrNull(id: CriterionId) = criteria.firstOrNull { it.id == id }

    // endregion

    @Test
    fun `an empty routine is not scored`() {
        val score = score(routine(rest))
        assertEquals(RoutineFocus.EMPTY, score.focus)
        assertEquals(Verdict.NOT_SCORED, score.verdict)
        assertTrue(score.criteria.isEmpty())
    }

    @Test
    fun `a cardio routine is not scored`() {
        val running = SlotSpec(SlotTarget.Fixed("running.barbell"), sets = 6, prescription = Prescription.Duration(60))
        val score = score(routine(train("Run", running), rest))
        assertEquals(RoutineFocus.CONDITIONING, score.focus)
        assertTrue(!score.scored)
    }

    @Test
    fun `primary muscles count full sets, secondary muscles half, normalised to a week`() {
        // One training day in a 7-day cycle: once a week.
        val weekly = score(routine(train("A", spec(bench, 4)), rest, rest, rest, rest, rest, rest)).weeklySets
        assertEquals(4.0, weekly[MuscleGroup.CHEST])
        assertEquals(2.0, weekly[MuscleGroup.SHOULDERS])
        assertEquals(2.0, weekly[MuscleGroup.TRICEPS])
        // Train, rest: the day comes 3.5 times a week.
        assertEquals(14.0, score(routine(train("A", spec(bench, 4)), rest)).weeklySets[MuscleGroup.CHEST])
    }

    @Test
    fun `pulling movements give no shoulder credit and the split is reported`() {
        val pulldown = exercise("lat-pulldown", listOf(Muscle.LATS), listOf(Muscle.SHOULDERS, Muscle.BICEPS))
        val withPull = ScoringCatalogue(listOf(bench, pulldown, press))
        val routine = routine(
            train("A", spec(bench, 4), spec(pulldown, 4), spec(press, 3)),
            rest,
            rest,
            rest,
            rest,
            rest,
            rest,
        )
        val score = RoutineScorer.score(routine, withPull)
        // 3 direct (press) + 2 from the bench press; the pulldown adds nothing.
        assertEquals(5.0, score.weeklySets[MuscleGroup.SHOULDERS])
        assertEquals(
            GroupSets(MuscleGroup.SHOULDERS, 5.0, direct = 3.0, secondary = 2.0),
            score.weeklySplit[MuscleGroup.SHOULDERS]
        )
        // Biceps still get half a set per pulldown set.
        assertEquals(
            GroupSets(MuscleGroup.BICEPS, 2.0, direct = 0.0, secondary = 2.0),
            score.weeklySplit[MuscleGroup.BICEPS]
        )
    }

    @Test
    fun `too much shoulder work points at the overlap, never at trimming direct work`() {
        val raise = exercise("lateral-raise", listOf(Muscle.SHOULDERS))
        val withRaise = ScoringCatalogue(listOf(bench, press, raise))
        val day = train("Push", spec(bench, 4), spec(press, 4), spec(raise, 6))
        val score = RoutineScorer.score(routine(day, rest), withRaise)
        // (4 + 6) direct + 2 secondary, 3.5 times a week.
        val shoulders = GroupSets(MuscleGroup.SHOULDERS, 42.0, direct = 35.0, secondary = 7.0)
        val volume = score.criterion(CriterionId.VOLUME)
        assertTrue(volume.advice.filterIsInstance<Advice.RemoveSets>().none { it.group == MuscleGroup.SHOULDERS })
        assertEquals(
            Advice.ShoulderOverlap(shoulders),
            volume.advice.filterIsInstance<Advice.ShoulderOverlap>().single()
        )
        assertTrue(shoulders in assertIs<Finding.Volume>(volume.finding).high)
    }

    @Test
    fun `advice numbers are rounded like the chart`() {
        // Hamstrings only from squats: 3 sets × 0.5 × 3.5 = 5.25, shown as 5.3 on the chart and in the advice.
        val score = score(routine(train("Legs", spec(squat, 3)), rest))
        val advice = score.criterion(CriterionId.VOLUME).advice.filterIsInstance<Advice.AddSets>()
        assertTrue(advice.isNotEmpty())
        advice.forEach {
            assertEquals(score.weeklySets[it.group] ?: 0.0, it.weeklySets, "${it.group}")
        }
    }

    @Test
    fun `a cycle without rest days assumes three sessions a week`() {
        val ab = routine(train("A", spec(bench, 4), spec(squat, 4)), train("B", spec(row, 4), spec(squat, 4)))
        val score = score(ab)
        assertEquals(3.0, score.sessionsPerWeek)
        // Squat on both days: 4 sets × 3 sessions.
        assertEquals(12.0, score.weeklySets[MuscleGroup.QUADS])
        assertEquals(6.0, score.weeklySets[MuscleGroup.CHEST])
        // Six or more days without rest: one cycle a week.
        val six = routine(*Array(6) { train("D$it", spec(bench, 2)) })
        assertEquals(6.0, score(six).sessionsPerWeek)
    }

    @Test
    fun `a sound upper lower routine scores high`() {
        val score = score(upperLower)
        assertEquals(RoutineFocus.HYPERTROPHY, score.focus)
        assertTrue(score.total >= RoutineScorer.EXCELLENT_FROM, "total ${score.total}: ${score.criteria}")
        assertEquals(100, score.criterion(CriterionId.FREQUENCY).score)
        assertEquals(100, score.criterion(CriterionId.COVERAGE).score)
        assertEquals(100, score.criterion(CriterionId.RECOVERY).score)
        assertEquals(100, score.criteria.sumOf { it.weight })
    }

    @Test
    fun `low volume gives concrete advice with exercises and a day`() {
        // Hamstrings only from squats (half sets): 2 days × 3 × 0.5 = 3 sets a week.
        val routine = routine(
            train("Upper", spec(bench, 4), spec(row, 4)),
            train("Legs", spec(squat, 3), spec(calf, 3)),
            rest,
            train("Upper 2", spec(bench, 4), spec(row, 4)),
            train("Legs 2", spec(squat, 3), spec(calf, 3)),
            rest,
            rest,
        )
        val volume = score(routine).criterion(CriterionId.VOLUME)
        val finding = assertIs<Finding.Volume>(volume.finding)
        assertTrue(finding.low.any { it.group == MuscleGroup.HAMSTRINGS && it.sets == 3.0 }, "$finding")
        val advice = volume.advice.filterIsInstance<Advice.AddSets>().first { it.group == MuscleGroup.HAMSTRINGS }
        assertEquals(3.0, advice.weeklySets)
        assertEquals(listOf("romanian-deadlift", "leg-curl"), advice.exerciseIds)
        assertTrue(advice.day == "Legs" || advice.day == "Legs 2", "lower-body day, got ${advice.day}")
        assertTrue(advice.addMin in 2..advice.addMax)
    }

    @Test
    fun `very high volume is flagged with a suggestion to trim`() {
        val routine = routine(
            train("Chest", spec(bench, 15)),
            rest,
            train("Chest 2", spec(bench, 15)),
            rest,
            rest,
            rest,
            rest,
        )
        val volume = score(routine).criterion(CriterionId.VOLUME)
        val finding = assertIs<Finding.Volume>(volume.finding)
        assertEquals(listOf(GroupSets(MuscleGroup.CHEST, 30.0)), finding.high)
        val trim = volume.advice.filterIsInstance<Advice.RemoveSets>().single()
        assertEquals(5, trim.removeMin)
        assertEquals(10, trim.removeMax)
    }

    @Test
    fun `a body-part split trains each muscle once a week and is told to spread it`() {
        val split = routine(
            train("Chest", spec(bench, 12)),
            train("Back", spec(row, 12)),
            train("Legs", spec(squat, 12), spec(rdl, 10)),
            rest,
            rest,
            rest,
            rest,
        )
        val frequency = score(split).criterion(CriterionId.FREQUENCY)
        assertTrue(frequency.score <= 50, "score ${frequency.score}")
        val finding = assertIs<Finding.Frequency>(frequency.finding)
        assertTrue(finding.low.any { it.group == MuscleGroup.CHEST && it.perWeek == 1.0 })
        assertNotNull(frequency.advice.filterIsInstance<Advice.SpreadAcrossDays>().first().day)
    }

    @Test
    fun `missing groups are listed, large ones first`() {
        val coverage =
            score(routine(train("Push", spec(bench, 10), spec(press, 6)), rest)).criterion(CriterionId.COVERAGE)
        val missing = assertIs<Finding.Coverage>(coverage.finding).missing
        assertTrue(MuscleGroup.BACK in missing && MuscleGroup.CALVES in missing && MuscleGroup.CHEST !in missing)
        assertTrue(coverage.score < 50)
        val first = assertIs<Advice.Cover>(coverage.advice.first())
        assertTrue(first.group.large)
    }

    @Test
    fun `push heavy routines are told to add pull work`() {
        val routine =
            routine(train("A", spec(bench, 6), spec(press, 6), spec(row, 3), spec(squat, 4), spec(rdl, 4)), rest)
        val balance = score(routine).criterion(CriterionId.BALANCE)
        val finding = assertIs<Finding.Balance>(balance.finding)
        assertEquals(4.0, finding.pushPull?.ratio)
        assertEquals(1.0, finding.quadsPosterior?.ratio)
        assertEquals(BalanceSide.PULL, assertIs<Advice.Rebalance>(balance.advice.single()).weaker)
        assertTrue(balance.score < 70, "score ${balance.score}")
    }

    @Test
    fun `the same muscle trained hard on consecutive days is a recovery conflict`() {
        val routine = routine(
            train("Legs", spec(squat, 5)),
            train("Legs again", spec(squat, 5)),
            rest,
            train("Upper", spec(bench, 5)),
            rest,
        )
        val recovery = score(routine).criterion(CriterionId.RECOVERY)
        val conflicts = assertIs<Finding.Recovery>(recovery.finding).conflicts
        assertEquals(listOf(RecoveryConflict(MuscleGroup.QUADS, "Legs", "Legs again")), conflicts)
        assertEquals(80, recovery.score)
        assertIs<Advice.SeparateDays>(recovery.advice.single())
    }

    @Test
    fun `an A B cycle at three sessions a week has no back to back days`() {
        val ab = routine(train("A", spec(squat, 5)), train("B", spec(squat, 5)))
        assertTrue(assertIs<Finding.Recovery>(score(ab).criterion(CriterionId.RECOVERY).finding).conflicts.isEmpty())
    }

    @Test
    fun `long sessions are flagged`() {
        val marathon = train("Marathon", *Array(8) { spec(bench, 5, rest = 180) })
        val length = score(routine(marathon, rest)).criterion(CriterionId.SESSION_LENGTH)
        val finding = assertIs<Finding.SessionLength>(length.finding)
        assertTrue(finding.longestMinutes > 90, "minutes ${finding.longestMinutes}")
        assertEquals(Advice.ShortenSession("Marathon", finding.longestMinutes), length.advice.single())
        assertTrue(length.score < 80)
    }

    @Test
    fun `session time counts sets, rest and supersets once per round`() {
        val slot = RoutineSlot(
            id = "s",
            dayId = "d",
            position = 0,
            target = SlotTarget.Fixed("x"),
            sets = 3,
            prescription = Prescription.Reps(10),
            restBetweenSetsS = 60,
            restAfterS = 60,
        )
        // 3 × (10 × 4 + 10) s work + 2 × 60 s rest + 60 s after + 45 s change = 375 s, about 6 min.
        assertEquals(6, SessionTime.minutes(listOf(slot)))
        val superset = listOf(slot.copy(supersetGroup = 1), slot.copy(id = "t", supersetGroup = 1))
        // 2 × 150 s work + 2 × 60 s rest per round + 60 s after + 2 × 45 s change = 570 s, about 10 min.
        assertEquals(10, SessionTime.minutes(superset))
    }

    @Test
    fun `progression share counts slots with a rule`() {
        val none = ProgressionRule.None
        val routine = routine(
            train("A", spec(bench), spec(row, progression = none), spec(squat, progression = none), spec(rdl)),
            rest,
        )
        val progression = score(routine).criterion(CriterionId.PROGRESSION)
        assertEquals(Finding.Progression(withRule = 2, total = 4), progression.finding)
        assertEquals(63, progression.score) // 50 % of the 80 % that earns full marks.
        assertEquals(Advice.AddProgressionRules(2, 4), progression.advice.single())
    }

    @Test
    fun `everything in one rep zone is noted and routines without heavy compounds are told so`() {
        val heavy = routine(
            train("A", spec(squat, 5, 5, 5), spec(bench, 5, 5, 5), spec(row, 5, 5, 5)),
            train("B", spec(squat, 5, 5, 5)),
        )
        val score = score(heavy)
        assertEquals(RoutineFocus.STRENGTH, score.focus)
        val reps = score.criterion(CriterionId.REP_RANGES)
        assertEquals(true, assertIs<Finding.RepRanges>(reps.finding).heavyCompound)
        assertEquals(Advice.AddRepZone(RepZone.HEAVY, RepZone.MODERATE), reps.advice.single())

        val pump = score(routine(train("A", spec(curl, 4, 15, 20), spec(pushdown, 4, 15, 20)), rest))
        assertTrue(pump.criterion(CriterionId.REP_RANGES).advice.any { it is Advice.AddHeavyCompound })
    }

    @Test
    fun `the total is the weighted mean of the criteria and sets the verdict`() {
        val score = score(upperLower)
        val expected = score.criteria.sumOf { it.score * it.weight }.toDouble() / score.criteria.sumOf { it.weight }
        assertEquals(expected.roundToInt(), score.total)
        assertEquals(Verdict.of(score.total), score.verdict)
        assertEquals(Verdict.EXCELLENT, Verdict.of(90))
        assertEquals(Verdict.GOOD, Verdict.of(75))
        assertEquals(Verdict.FAIR, Verdict.of(60))
        assertEquals(Verdict.NEEDS_WORK, Verdict.of(59))
    }

    // region Sessions

    private val dayMs = 86_400_000L
    private val now = 100 * dayMs

    private fun session(
        daysAgo: Int,
        dayIndex: Int = 0,
        routineId: String = "r",
        loadKg: Long = 100,
        sets: Int = 4,
        variant: String = "bench-press.barbell",
    ) = WorkoutSession(
        id = "w$daysAgo-$routineId-$variant",
        startedAt = now - daysAgo * dayMs,
        endedAt = now - daysAgo * dayMs + 3_600_000,
        routineId = routineId,
        routineDayIndex = dayIndex,
        exercises = listOf(
            SessionExercise(
                id = "e$daysAgo",
                sessionId = "w$daysAgo",
                position = 0,
                variantId = variant,
                sets = List(sets) { i ->
                    SetLog(
                        id = "set$daysAgo-$i",
                        sessionExerciseId = "e$daysAgo",
                        position = i,
                        reps = 8,
                        loadGrams = loadKg * 1_000,
                        completed = true,
                    )
                },
            ),
        ),
    )

    @Test
    fun `without recent sessions the session criteria are left out`() {
        val old = listOf(session(daysAgo = 60), session(daysAgo = 50))
        val score = score(upperLower, old, now)
        assertNull(score.criterionOrNull(CriterionId.ADHERENCE))
        assertNull(score.criterionOrNull(CriterionId.LOAD_PROGRESS))
        // Another routine's sessions never count.
        val other = score(upperLower, listOf(session(2, routineId = "other")), now)
        assertNull(other.criterionOrNull(CriterionId.ADHERENCE))
    }

    @Test
    fun `adherence compares planned and done sessions and sets`() {
        // The routine plans 4 sessions a week: 16 in four weeks. 8 were done, each with all 18 planned sets.
        val done = (1..8).map { session(daysAgo = it * 3, dayIndex = 0, sets = 18) } + session(daysAgo = 40)
        val score = score(upperLower, done, now)
        val adherence = score.criterion(CriterionId.ADHERENCE)
        assertEquals(Finding.Adherence(done = 8, planned = 16, setCompletion = 1.0, weeks = 4), adherence.finding)
        assertEquals(70, adherence.score) // 0.6 × 0.5 + 0.4 × 1.0
        assertEquals(Advice.FitTheSchedule(8, 16), adherence.advice.single())
        assertEquals(120, score.criteria.sumOf { it.weight })
    }

    @Test
    fun `load progress tells rising lifts from stalled ones`() {
        val rising = (0 until 6).map { i -> session(daysAgo = 40 - i * 7, loadKg = 100L + i * 5) }
        val flat = (0 until 6).map { i -> session(daysAgo = 41 - i * 7, loadKg = 80, variant = "squat.barbell") }
        val progress = score(upperLower, rising + flat, now).criterion(CriterionId.LOAD_PROGRESS)
        assertEquals(Finding.LoadProgress(improved = 1, tracked = 2, weeks = 6), progress.finding)
        assertEquals(71, progress.score) // 50 % of the 70 % that earns full marks.
        assertEquals(Advice.BreakPlateau(listOf("squat")), progress.advice.single())
    }

    // endregion

    @Test
    fun `a template converts to a routine with names in the language`() {
        val template = RoutineTemplate(
            id = "t",
            version = 1,
            name = LocalizedText("Full body", "Cuerpo completo"),
            description = LocalizedText("", ""),
            credit = "Test",
            level = Level.BEGINNER,
            days = listOf(
                TemplateDay(LocalizedText("Day", "Día"), slots = listOf(spec(bench))),
                TemplateDay(LocalizedText("Rest", "Descanso"), DayKind.REST),
            ),
        )
        val routine = template.toScoringRoutine("es")
        assertEquals("Cuerpo completo", routine.name)
        assertEquals(listOf("Día", "Descanso"), routine.days.map { it.name })
        assertEquals(spec(bench), routine.days.first().slots.single().toSpec())
        // Train, rest: 3 sets 3.5 times a week.
        assertEquals(10.5, score(routine).weeklySets[MuscleGroup.CHEST])
    }
}
