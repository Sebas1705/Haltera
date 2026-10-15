package es.sebas1705.ironlog.feature.progress.ui.bodyweight

import es.sebas1705.ironlog.core.common.mvi.MVIBaseIntent
import es.sebas1705.ironlog.core.common.mvi.MVIBaseState
import es.sebas1705.ironlog.core.common.mvi.MVIBaseViewModel
import es.sebas1705.ironlog.core.datastore.SettingsRepository
import es.sebas1705.ironlog.core.units.Numbers
import es.sebas1705.ironlog.core.units.WeightUnit
import es.sebas1705.ironlog.core.units.Weights
import es.sebas1705.ironlog.data.models.BodyWeightEntry
import es.sebas1705.ironlog.data.repository.BodyWeightRepository
import es.sebas1705.ironlog.feature.progress.model.BodyWeightChart
import es.sebas1705.ironlog.feature.progress.model.PastSessionTime
import es.sebas1705.ironlog.feature.progress.model.ProgressClock
import es.sebas1705.ironlog.feature.progress.model.ProgressMath
import es.sebas1705.ironlog.feature.progress.model.toEpochDay
import es.sebas1705.ironlog.domain.usecases.stats.BodyWeightStats
import es.sebas1705.ironlog.domain.usecases.stats.DatedWeight
import es.sebas1705.ironlog.domain.usecases.stats.WeeklyWeight
import es.sebas1705.ironlog.domain.usecases.stats.WeightRate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine

/** The open add/edit dialog. */
internal sealed interface WeightDialog {
    data object Add : WeightDialog

    data class Edit(val entry: BodyWeightEntry) : WeightDialog
}

internal data class BodyWeightState(
    val loading: Boolean = true,
    val unit: WeightUnit = WeightUnit.KG,
    /** Latest first. */
    val entries: List<BodyWeightEntry> = emptyList(),
    val chart: BodyWeightChart = BodyWeightChart(),
    /** Change over the last 30 days in grams, or null without enough entries. */
    val change30Days: Long? = null,
    /** Weekly averages, latest week first. */
    val weekly: List<WeeklyWeight> = emptyList(),
    /** Change per week over the last 28 days, or null without enough entries. */
    val rate: WeightRate? = null,
    val dialog: WeightDialog? = null,
    /** Text of the dialog's weight field, in [unit]. */
    val input: String = "",
    val inputError: Boolean = false,
    /** The dialog's date as UTC midnight (what the date picker uses); [maxDateUtcMillis] is today. */
    val dateUtcMillis: Long = 0,
    val maxDateUtcMillis: Long = 0,
) : MVIBaseState

internal sealed interface BodyWeightIntent : MVIBaseIntent {
    data object OpenAdd : BodyWeightIntent

    data class OpenEdit(val entry: BodyWeightEntry) : BodyWeightIntent

    data class InputChanged(val text: String) : BodyWeightIntent

    /** A date picked in the dialog (UTC midnight); future dates are clamped to today. */
    data class DateChanged(val dateUtcMillis: Long) : BodyWeightIntent

    data object Dismiss : BodyWeightIntent

    data object Save : BodyWeightIntent

    data class Delete(val id: String) : BodyWeightIntent
}

/** Body weight log (spec §10 F6): entries in the user's unit, a 7-entry moving average and the 30-day trend. */
internal class BodyWeightViewModel(
    private val bodyWeight: BodyWeightRepository,
    private val settings: SettingsRepository,
    private val clock: ProgressClock = ProgressClock(),
) : MVIBaseViewModel<BodyWeightState, BodyWeightIntent>(BodyWeightState()) {

    init {
        execute { observe() }
    }

    override fun intentHandler(intent: BodyWeightIntent): Job = execute {
        when (intent) {
            BodyWeightIntent.OpenAdd -> showDialog(WeightDialog.Add, input = "")
            is BodyWeightIntent.OpenEdit -> showDialog(
                WeightDialog.Edit(intent.entry),
                input = Weights.format(intent.entry.weightGrams, currentState.unit, grouping = false),
            )
            is BodyWeightIntent.InputChanged -> updateState { it.copy(input = intent.text, inputError = false) }
            is BodyWeightIntent.DateChanged -> updateState {
                it.copy(dateUtcMillis = intent.dateUtcMillis.coerceAtMost(it.maxDateUtcMillis))
            }
            BodyWeightIntent.Dismiss -> showDialog(null, input = "")
            BodyWeightIntent.Save -> save()
            is BodyWeightIntent.Delete -> {
                bodyWeight.delete(intent.id)
                showDialog(null, input = "")
            }
        }
    }

    /** Opens (or closes, with null) the dialog; it is dated today for a new entry, the entry's day when editing. */
    private fun showDialog(dialog: WeightDialog?, input: String) {
        val today = PastSessionTime.todayUtcMillis(clock.now(), clock.timeZone)
        val date = (dialog as? WeightDialog.Edit)?.let {
            PastSessionTime.utcMillis(PastSessionTime.localDate(it.entry.measuredAt, clock.timeZone))
        } ?: today
        updateState {
            it.copy(
                dialog = dialog,
                input = input,
                inputError = false,
                dateUtcMillis = date.coerceAtMost(today),
                maxDateUtcMillis = today,
            )
        }
    }

    private suspend fun save() {
        val state = currentState
        val grams = parseWeight(state.input, state.unit)
        if (grams == null) {
            updateState { it.copy(inputError = true) }
            return
        }
        when (val dialog = state.dialog) {
            WeightDialog.Add -> bodyWeight.add(grams, measuredAt(state.dateUtcMillis))
            is WeightDialog.Edit -> {
                val entry = dialog.entry
                val sameDay = PastSessionTime.localDate(entry.measuredAt, clock.timeZone) ==
                    PastSessionTime.dateOf(state.dateUtcMillis)
                val measuredAt = if (sameDay) entry.measuredAt else measuredAt(state.dateUtcMillis)
                bodyWeight.update(entry.copy(weightGrams = grams, measuredAt = measuredAt))
            }
            null -> return
        }
        showDialog(null, input = "")
    }

    /** Now for today, noon for an earlier day (an edit on the same day keeps its time). */
    private fun measuredAt(dateUtcMillis: Long): Long =
        PastSessionTime.measuredAt(dateUtcMillis, clock.now(), clock.timeZone)

    private suspend fun observe() {
        combine(bodyWeight.observeAll(), settings.settings) { entries, userSettings -> entries to userSettings.unit }
            .collect { (entries, unit) ->
                val latestFirst = entries.sortedByDescending { it.measuredAt }
                val chart = ProgressMath.bodyWeightChart(entries)
                val change = ProgressMath.change(entries)
                val dated = entries.map {
                    DatedWeight(
                        it.measuredAt.toEpochDay(clock.timeZone),
                        it.measuredAt,
                        it.weightGrams
                    )
                }
                val weekly = BodyWeightStats.weeklyAverages(dated)
                val rate = BodyWeightStats.ratePerWeek(dated, clock.today().toEpochDay())
                updateState {
                    it.copy(
                        loading = false,
                        unit = unit,
                        entries = latestFirst,
                        chart = chart,
                        change30Days = change,
                        weekly = weekly,
                        rate = rate,
                    )
                }
            }
    }

    companion object {
        private const val MAX_WEIGHT = 1_000.0

        /** Parses a weight typed in [unit] ("72.5" or "72,5") to grams; null if not a plausible weight. */
        fun parseWeight(text: String, unit: WeightUnit): Long? {
            val value = Numbers.parse(text) ?: return null
            if (value <= 0.0 || value >= MAX_WEIGHT || value.isNaN()) return null
            return Weights.toGrams(value, unit)
        }
    }
}
