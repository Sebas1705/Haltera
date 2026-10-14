package es.sebas1705.ironlog.data.repository

import app.cash.turbine.test
import es.sebas1705.ironlog.data.models.DayKind
import es.sebas1705.ironlog.data.models.LoadIncrement
import es.sebas1705.ironlog.data.models.Prescription
import es.sebas1705.ironlog.data.models.ProgressionRule
import es.sebas1705.ironlog.data.models.SlotSpec
import es.sebas1705.ironlog.data.models.SlotTarget
import es.sebas1705.ironlog.data.repository.sql.SqlRoutineRepository
import es.sebas1705.ironlog.data.routines.FamousRoutines
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutineRepositoryTest {
    private val db = inMemoryDatabase().withTestCatalog()
    private val clock = TestClock()

    private fun TestScope.repository() =
        SqlRoutineRepository(db, FamousRoutines.all, UnconfinedTestDispatcher(testScheduler), clock)

    private val squat = SlotSpec(
        target = SlotTarget.Fixed("squat.barbell"),
        sets = 5,
        prescription = Prescription.Reps(5),
        progression = ProgressionRule.Linear(LoadIncrement.Weight(2_500)),
    )

    @Test
    fun `create a routine with days and slots`() = runTest {
        val repo = repository()
        val id = repo.createRoutine("PPL", "Push pull legs")
        val push = repo.addDay(id, "Push")
        repo.addDay(id, "Rest", DayKind.REST)
        val slot = repo.addSlot(push, squat)
        repo.addSlot(push, squat.copy(target = SlotTarget.Flexible("bench-press")))

        val routine = repo.getRoutine(id)!!
        assertEquals("PPL", routine.name)
        assertEquals(listOf("Push", "Rest"), routine.days.map { it.name })
        assertEquals(listOf(DayKind.TRAINING, DayKind.REST), routine.days.map { it.kind })
        assertEquals(listOf(0, 1), routine.days.first().slots.map { it.position })
        assertEquals(squat, routine.days.first().slots.first().toSpec())
        assertEquals(slot, routine.days.first().slots.first().id)
    }

    @Test
    fun `update, move and delete keep positions contiguous`() = runTest {
        val repo = repository()
        val id = repo.createRoutine("R")
        val a = repo.addDay(id, "A")
        val b = repo.addDay(id, "B")
        val c = repo.addDay(id, "C")
        repo.moveDay(c, 0)
        assertEquals(listOf("C", "A", "B"), repo.getRoutine(id)!!.days.map { it.name })

        repo.updateDay(a, "A2", DayKind.REST)
        repo.deleteDay(c)
        val days = repo.getRoutine(id)!!.days
        assertEquals(listOf("A2", "B"), days.map { it.name })
        assertEquals(listOf(0, 1), days.map { it.position })
        assertEquals(DayKind.REST, days.first().kind)

        val s1 = repo.addSlot(b, squat)
        val s2 = repo.addSlot(b, squat.copy(sets = 3))
        repo.moveSlot(s2, 0)
        repo.updateSlot(s1, squat.copy(notes = "belt"))
        assertEquals(listOf(s2, s1), repo.getRoutine(id)!!.days[1].slots.map { it.id })
        assertEquals("belt", repo.getRoutine(id)!!.days[1].slots[1].notes)
        repo.deleteSlot(s2)
        assertEquals(listOf(0), repo.getRoutine(id)!!.days[1].slots.map { it.position })

        repo.updateRoutine(id, "Renamed", "d")
        assertEquals("Renamed", repo.getRoutine(id)!!.name)
        repo.deleteRoutine(id)
        assertNull(repo.getRoutine(id))
    }

    @Test
    fun `only one routine is active`() = runTest {
        val repo = repository()
        val first = repo.createRoutine("First")
        val second = repo.createRoutine("Second")

        repo.observeActive().test {
            assertNull(awaitItem())
            repo.setActive(first)
            assertEquals(first, awaitItem()?.id)
            repo.setActive(second)
            assertEquals(second, expectMostRecentItem()?.id)
            repo.setActive(null)
            assertNull(expectMostRecentItem())
        }
        assertFalse(repo.observeRoutines().first().any { it.isActive })
    }

    @Test
    fun `observers see slot changes`() = runTest {
        val repo = repository()
        val id = repo.createRoutine("R")
        val day = repo.addDay(id, "A")

        repo.observeRoutine(id).test {
            assertEquals(0, awaitItem()!!.days.single().slots.size)
            clock.millis = 5_000
            repo.addSlot(day, squat)
            val updated = expectMostRecentItem()!!
            assertEquals(1, updated.days.single().slots.size)
            assertEquals(5_000L, updated.updatedAt)
        }
    }

    @Test
    fun `cycle pointer is clamped`() = runTest {
        val repo = repository()
        val id = repo.createRoutine("R")
        repo.addDay(id, "A")
        val b = repo.addDay(id, "B")
        assertNull(repo.getRoutine(id)!!.nextDayIndexSetAt)
        clock.millis = 7_000
        repo.setNextDayIndex(id, 7)
        assertEquals(1, repo.getRoutine(id)!!.nextDayIndex)
        assertEquals(7_000L, repo.getRoutine(id)!!.nextDayIndexSetAt)
        clock.millis = 8_000
        repo.deleteDay(b)
        assertEquals(0, repo.getRoutine(id)!!.nextDayIndex)
        // Clamping after an edit is not a new choice: the override keeps its time.
        assertEquals(7_000L, repo.getRoutine(id)!!.nextDayIndexSetAt)
    }

    @Test
    fun `copying a famous routine creates an editable routine with its origin`() = runTest {
        val repo = repository()
        assertTrue(repo.templates().size >= 11)
        val template = FamousRoutines.byId("linear-5x5")!!

        val id = repo.copyTemplate("linear-5x5", "es")

        val routine = repo.getRoutine(id)!!
        assertEquals(template.name.es, routine.name)
        assertEquals("linear-5x5", routine.origin?.templateId)
        assertEquals(template.days.size, routine.days.size)
        assertEquals(template.days.map { it.name.es }, routine.days.map { it.name })
        assertEquals(template.days.first().slots, routine.days.first().slots.map { it.toSpec() })
    }
}
