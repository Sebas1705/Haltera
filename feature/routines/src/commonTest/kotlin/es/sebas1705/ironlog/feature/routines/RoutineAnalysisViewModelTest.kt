package es.sebas1705.ironlog.feature.routines

import es.sebas1705.ironlog.core.testing.CoroutineTestRule
import es.sebas1705.ironlog.data.models.DayKind
import es.sebas1705.ironlog.data.models.ExerciseHistoryEntry
import es.sebas1705.ironlog.data.models.Level
import es.sebas1705.ironlog.data.models.LocalizedText
import es.sebas1705.ironlog.data.models.RoutineTemplate
import es.sebas1705.ironlog.data.models.SessionExercise
import es.sebas1705.ironlog.data.models.SetInput
import es.sebas1705.ironlog.data.models.SetLog
import es.sebas1705.ironlog.data.models.SlotSpec
import es.sebas1705.ironlog.data.models.SlotTarget
import es.sebas1705.ironlog.data.models.TemplateDay
import es.sebas1705.ironlog.data.models.WorkoutSession
import es.sebas1705.ironlog.data.repository.SessionRepository
import es.sebas1705.ironlog.domain.usecases.routinescore.Advice
import es.sebas1705.ironlog.domain.usecases.routinescore.CriterionId
import es.sebas1705.ironlog.domain.usecases.routinescore.MuscleGroup
import es.sebas1705.ironlog.feature.routines.ui.analysis.RoutineAnalysisViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutineAnalysisViewModelTest {
    private val rule = CoroutineTestRule()
    private val template = RoutineTemplate(
        id = "push-only",
        version = 1,
        name = LocalizedText("Push only", "Solo empuje"),
        description = LocalizedText("", ""),
        credit = "Test",
        level = Level.BEGINNER,
        days = listOf(
            TemplateDay(
                LocalizedText("Push", "Empuje"),
                slots = listOf(SlotSpec(SlotTarget.Fixed(TestCatalog.BENCH_BARBELL), sets = 5)),
            ),
            TemplateDay(LocalizedText("Rest", "Descanso"), DayKind.REST),
        ),
    )
    private val routines = FakeRoutineRepository(templates = listOf(template))
    private val exercises = FakeExerciseRepository(TestCatalog.all)
    private val sessions = FakeSessions()
    private val now = 100L * DAY

    @BeforeTest
    fun setUp() = rule.setup()

    @AfterTest
    fun tearDown() = rule.tearDown()

    private fun viewModel(routineId: String? = null, templateId: String? = null, language: String = "en") =
        RoutineAnalysisViewModel(routineId, templateId, routines, exercises, sessions, { language }, { now })

    @Test
    fun `a template is scored from its plan, with advice exercises named`() = runTest {
        val state = viewModel(templateId = template.id, language = "es").uiState.value
        assertFalse(state.loading)
        assertEquals("Solo empuje", state.name)
        val score = assertNotNull(state.score)
        assertTrue(score.scored)
        assertEquals(17.5, score.weeklySets[MuscleGroup.CHEST]) // 5 sets, 3.5 times a week.
        assertNull(score.criteria.firstOrNull { it.id == CriterionId.ADHERENCE })
        // Back is not trained: the advice names catalogue exercises; the ones loaded get their names.
        val cover = score.criteria.flatMap { it.advice }.filterIsInstance<Advice.Cover>()
        assertTrue(cover.any { it.group == MuscleGroup.BACK })
        assertEquals("Press de banca", state.exerciseName("bench-press"))
        assertEquals("unknown-id", state.exerciseName("unknown-id"))
    }

    @Test
    fun `a saved routine is scored with its recent sessions and follows edits`() = runTest {
        val routineId = routines.copyTemplate(template.id, "en")
        val dayId = routines.routine(routineId).days.first().id
        sessions.history.value = (1..6).map { session(routineId, daysAgo = it * 4) } +
            session("other-routine", daysAgo = 2)
        val vm = viewModel(routineId = routineId)

        val score = assertNotNull(vm.uiState.value.score)
        val adherence = assertNotNull(score.criteria.firstOrNull { it.id == CriterionId.ADHERENCE })
        assertTrue(adherence.score in 1..100)
        assertNotNull(score.criteria.firstOrNull { it.id == CriterionId.LOAD_PROGRESS })

        routines.addSlot(dayId, SlotSpec(SlotTarget.Fixed("pull-up.bodyweight"), sets = 5))
        val updated = assertNotNull(vm.uiState.value.score)
        assertEquals(17.5, updated.weeklySets[MuscleGroup.BACK])
    }

    @Test
    fun `a missing routine or template shows as missing`() = runTest {
        assertTrue(viewModel(routineId = "nope").uiState.value.missing)
        assertTrue(viewModel(templateId = "nope").uiState.value.missing)
    }

    private fun session(routineId: String, daysAgo: Int) = WorkoutSession(
        id = "s-$routineId-$daysAgo",
        startedAt = now - daysAgo * DAY,
        endedAt = now - daysAgo * DAY + HOUR,
        routineId = routineId,
        routineDayIndex = 0,
        exercises = listOf(
            SessionExercise(
                id = "e$daysAgo",
                sessionId = "s$daysAgo",
                position = 0,
                variantId = TestCatalog.BENCH_BARBELL,
                sets = List(5) { i ->
                    SetLog(
                        id = "set$daysAgo-$i",
                        sessionExerciseId = "e$daysAgo",
                        position = i,
                        reps = 5,
                        loadGrams = (100_000L - daysAgo * 500L),
                        completed = true,
                    )
                },
            ),
        ),
    )

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
    }
}

/** [SessionRepository] serving only the finished-session history the analysis reads. */
@Suppress("TooManyFunctions")
private class FakeSessions : SessionRepository {
    val history = MutableStateFlow<List<WorkoutSession>>(emptyList())

    override fun observeHistory(limit: Int): Flow<List<WorkoutSession>> = history

    override fun observeInProgress(): Flow<WorkoutSession?> = flowOf(null)
    override suspend fun getInProgress(): WorkoutSession? = null
    override fun observeSession(id: String): Flow<WorkoutSession?> = flowOf(null)
    override suspend fun getSession(id: String): WorkoutSession? = null
    override fun observeLatestForRoutine(routineId: String): Flow<WorkoutSession?> = flowOf(null)
    override suspend fun startFromRoutineDay(
        routineId: String,
        dayIndex: Int,
        startedAt: Long?
    ): String = error("Not used")
    override suspend fun startFree(startedAt: Long?): String = error("Not used")
    override suspend fun addExercise(sessionId: String, variantId: String, slotId: String?): String = error("Not used")
    override suspend fun removeExercise(sessionExerciseId: String) = error("Not used")
    override suspend fun substituteExercise(sessionExerciseId: String, variantId: String) = error("Not used")
    override suspend fun moveExercise(sessionExerciseId: String, toPosition: Int) = error("Not used")
    override suspend fun updateExerciseNotes(sessionExerciseId: String, notes: String) = error("Not used")
    override suspend fun logSet(sessionExerciseId: String, input: SetInput): String = error("Not used")
    override suspend fun updateSet(setId: String, input: SetInput) = error("Not used")
    override suspend fun deleteSet(setId: String) = error("Not used")
    override suspend fun updateSessionNotes(sessionId: String, notes: String) = error("Not used")
    override suspend fun finish(sessionId: String, endedAt: Long?) = error("Not used")
    override suspend fun updateTimes(sessionId: String, startedAt: Long, endedAt: Long?) = error("Not used")
    override suspend fun deleteSession(sessionId: String) = error("Not used")
    override fun observeSessionsBetween(
        fromMillis: Long,
        toMillis: Long
    ): Flow<List<WorkoutSession>> = flowOf(emptyList())
    override suspend fun lastForSlotAndVariant(
        slotId: String,
        variantId: String,
        limit: Int,
        before: Long?,
    ): List<ExerciseHistoryEntry> = emptyList()

    override suspend fun lastForVariant(variantId: String, limit: Int, before: Long?): List<ExerciseHistoryEntry> = emptyList()
}
