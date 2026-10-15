package es.sebas1705.ironlog.feature.progress.ui.bodyweight

import es.sebas1705.ironlog.core.common.mvi.MVIBaseIntent
import es.sebas1705.ironlog.core.common.mvi.MVIBaseState
import es.sebas1705.ironlog.core.common.mvi.MVIBaseViewModel
import es.sebas1705.ironlog.core.datastore.SettingsRepository
import es.sebas1705.ironlog.core.units.WeightUnit
import es.sebas1705.ironlog.core.units.Weights
import es.sebas1705.ironlog.data.models.BodyWeightEntry
import es.sebas1705.ironlog.data.repository.BodyWeightRepository
import es.sebas1705.ironlog.feature.progress.model.BodyWeightChart
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
) : MVIBaseState

internal sealed interface BodyWeightIntent : MVIBaseIntent {
    data object OpenAdd : BodyWeightIntent

    data class OpenEdit(val entry: BodyWeightEntry) : BodyWeightIntent

    data class InputChanged(val text: String) : BodyWeightIntent

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
                input = Weights.format(intent.entry.weightGrams, currentState.unit),
            )
            is BodyWeightIntent.InputChanged -> updateState { it.copy(input = intent.text, inputError = false) }
            BodyWeightIntent.Dismiss -> showDialog(null, input = "")
            BodyWeightIntent.Save -> save()
            is BodyWeightIntent.Delete -> {
                bodyWeight.delete(intent.id)
                showDialog(null, input = "")
            }
        }
    }

    private fun showDialog(dialog: WeightDialog?, input: String) =
        updateState { it.copy(dialog = dialog, input = input, inputError = false) }

    private suspend fun save() {
        val state = currentState
        val grams = parseWeight(state.input, state.unit)
        if (grams == null) {
            updateState { it.copy(inputError = true) }
            return
        }
        when (val dialog = state.dialog) {
            WeightDialog.Add -> bodyWeight.add(grams)
            is WeightDialog.Edit -> bodyWeight.update(dialog.entry.copy(weightGrams = grams))
            null -> return
        }
        showDialog(null, input = "")
    }

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
            val value = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
            if (value <= 0.0 || value >= MAX_WEIGHT || value.isNaN()) return null
            return Weights.toGrams(value, unit)
        }
    }
}
