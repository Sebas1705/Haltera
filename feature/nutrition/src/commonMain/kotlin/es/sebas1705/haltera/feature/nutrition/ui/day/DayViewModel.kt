package es.sebas1705.ironlog.feature.nutrition.ui.day

import es.sebas1705.ironlog.core.common.mvi.MVIBaseIntent
import es.sebas1705.ironlog.core.common.mvi.MVIBaseState
import es.sebas1705.ironlog.core.common.mvi.MVIBaseViewModel
import es.sebas1705.ironlog.data.models.FoodDay
import es.sebas1705.ironlog.data.models.Meal
import es.sebas1705.ironlog.data.models.NutritionGoals
import es.sebas1705.ironlog.data.repository.NutritionRepository
import es.sebas1705.ironlog.feature.nutrition.model.Today
import es.sebas1705.ironlog.feature.nutrition.model.attempt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** What "Copy from another day" copies: one meal into the same meal, or the whole day. */
internal sealed interface CopyTarget {
    data class OneMeal(val meal: Meal) : CopyTarget

    data object WholeDay : CopyTarget
}

internal data class DayState(
    val loading: Boolean = true,
    val date: LocalDate,
    val today: LocalDate,
    val day: FoodDay,
    val goals: NutritionGoals = NutritionGoals(),
    /** Open "copy from" date picker. */
    val copying: CopyTarget? = null,
    /** Entries copied by the last copy (shown once), or null. */
    val copiedCount: Int? = null,
    val error: Boolean = false,
) : MVIBaseState

internal sealed interface DayIntent : MVIBaseIntent {
    data object PreviousDay : DayIntent

    data object NextDay : DayIntent

    data object GoToToday : DayIntent

    data class SetDate(val date: LocalDate) : DayIntent

    data class DeleteEntry(val entryId: String) : DayIntent

    data class StartCopy(val target: CopyTarget) : DayIntent

    data object CancelCopy : DayIntent

    /** Copies from [from] into the shown date. */
    data class CopyFrom(val from: LocalDate) : DayIntent

    data object CopiedShown : DayIntent

    data object ErrorShown : DayIntent
}

/** Nutrition tab root: one day of the food log with its totals against the goals (design §4). */
@OptIn(ExperimentalCoroutinesApi::class)
internal class DayViewModel(
    private val repository: NutritionRepository,
    today: Today,
) : MVIBaseViewModel<DayState, DayIntent>(
    today.date().let { DayState(date = it, today = it, day = FoodDay(it, emptyList())) },
) {
    private val date = MutableStateFlow(currentState.date)

    init {
        execute {
            combine(
                date.flatMapLatest { repository.observeDay(it) },
                repository.observeGoals()
            ) { day, goals -> day to goals }
                .collect { (day, goals) -> updateState { it.copy(loading = false, day = day, goals = goals) } }
        }
    }

    override fun intentHandler(intent: DayIntent): Job = execute {
        when (intent) {
            DayIntent.PreviousDay -> show(currentState.date.minus(DatePeriod(days = 1)))
            DayIntent.NextDay -> show(currentState.date.plus(DatePeriod(days = 1)))
            DayIntent.GoToToday -> show(currentState.today)
            is DayIntent.SetDate -> show(intent.date)
            is DayIntent.DeleteEntry -> report(attempt { repository.deleteEntry(intent.entryId) })
            is DayIntent.StartCopy -> updateState { it.copy(copying = intent.target) }
            DayIntent.CancelCopy -> updateState { it.copy(copying = null) }
            is DayIntent.CopyFrom -> copy(intent.from)
            DayIntent.CopiedShown -> updateState { it.copy(copiedCount = null) }
            DayIntent.ErrorShown -> updateState { it.copy(error = false) }
        }
    }

    private fun show(newDate: LocalDate) {
        updateState { it.copy(date = newDate, day = FoodDay(newDate, emptyList()), loading = true) }
        date.value = newDate
    }

    private suspend fun copy(from: LocalDate) {
        val target = currentState.copying ?: return
        val to = currentState.date
        updateState { it.copy(copying = null) }
        attempt {
            when (target) {
                is CopyTarget.OneMeal -> repository.copyMeal(from, target.meal, to, target.meal)
                CopyTarget.WholeDay -> repository.copyDay(from, to)
            }
        }.onSuccess { n -> updateState { it.copy(copiedCount = n) } }
            .onFailure { updateState { it.copy(error = true) } }
    }

    private fun report(result: Result<Unit>) {
        if (result.isFailure) updateState { it.copy(error = true) }
    }
}
