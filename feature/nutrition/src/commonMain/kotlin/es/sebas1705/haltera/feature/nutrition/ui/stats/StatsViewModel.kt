package es.sebas1705.haltera.feature.nutrition.ui.stats

import es.sebas1705.haltera.core.common.mvi.MVIBaseIntent
import es.sebas1705.haltera.core.common.mvi.MVIBaseState
import es.sebas1705.haltera.core.common.mvi.MVIBaseViewModel
import es.sebas1705.haltera.data.models.Nutrients
import es.sebas1705.haltera.data.models.NutritionGoals
import es.sebas1705.haltera.data.repository.BodyWeightRepository
import es.sebas1705.haltera.data.repository.NutritionRepository
import es.sebas1705.haltera.domain.usecases.nutrition.Adherence
import es.sebas1705.haltera.domain.usecases.nutrition.DailyIntake
import es.sebas1705.haltera.domain.usecases.nutrition.IntakeWeightTrend
import es.sebas1705.haltera.domain.usecases.nutrition.MacroSplit
import es.sebas1705.haltera.domain.usecases.nutrition.NutritionStats
import es.sebas1705.haltera.domain.usecases.nutrition.WeeklyAverage
import es.sebas1705.haltera.feature.nutrition.model.Today
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus

internal enum class StatsRange(val days: Int) { WEEK(7), MONTH(30), QUARTER(90) }

internal data class StatsState(
    val loading: Boolean = true,
    val range: StatsRange = StatsRange.MONTH,
    val from: LocalDate,
    val to: LocalDate,
    val goals: NutritionGoals = NutritionGoals(),
    /** Logged days only, oldest first. */
    val daily: List<DailyIntake> = emptyList(),
    val average: Nutrients? = null,
    val split: MacroSplit? = null,
    val weekly: List<WeeklyAverage> = emptyList(),
    val adherence: Adherence? = null,
    val latestWeightGrams: Long? = null,
    val proteinPerKg: Double? = null,
    val trend: IntakeWeightTrend = IntakeWeightTrend(emptyList(), emptyList()),
) : MVIBaseState

internal sealed interface StatsIntent : MVIBaseIntent {
    data class SetRange(val range: StatsRange) : StatsIntent
}

/** Nutrition statistics over the last 7, 30 or 90 days (design §1), computed by [NutritionStats]. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class StatsViewModel(
    private val repository: NutritionRepository,
    private val bodyWeight: BodyWeightRepository,
    private val today: Today,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : MVIBaseViewModel<StatsState, StatsIntent>(
    today.date().let { StatsState(from = it.minus(DatePeriod(days = StatsRange.MONTH.days - 1)), to = it) },
) {
    private val range = MutableStateFlow(StatsRange.MONTH)

    init {
        execute {
            val days = range.flatMapLatest { r ->
                val to = today.date()
                repository.observeDays(to.minus(DatePeriod(days = r.days - 1)), to)
            }
            combine(days, repository.observeGoals(), bodyWeight.observeAll(), range) { d, goals, weights, r ->
                val to = today.date()
                val daily = NutritionStats.daily(d)
                val average = NutritionStats.averagePerDay(daily)
                val latest = weights.maxByOrNull { it.measuredAt }?.weightGrams
                StatsState(
                    loading = false,
                    range = r,
                    from = to.minus(DatePeriod(days = r.days - 1)),
                    to = to,
                    goals = goals,
                    daily = daily,
                    average = average,
                    split = average?.let(NutritionStats::macroSplit),
                    weekly = NutritionStats.weeklyAverages(daily),
                    adherence = NutritionStats.adherence(daily, goals.kcal),
                    latestWeightGrams = latest,
                    proteinPerKg = average?.let { NutritionStats.proteinPerKg(it.protein, latest) },
                    trend = NutritionStats.intakeVsWeight(daily, weights, timeZone),
                )
            }.collect { computed -> updateState { computed } }
        }
    }

    override fun intentHandler(intent: StatsIntent): Job = execute {
        when (intent) {
            is StatsIntent.SetRange -> {
                updateState { it.copy(range = intent.range) }
                range.value = intent.range
            }
        }
    }
}
