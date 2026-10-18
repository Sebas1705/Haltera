package es.sebas1705.haltera.feature.progress.ui.measurements

import es.sebas1705.haltera.core.common.mvi.MVIBaseIntent
import es.sebas1705.haltera.core.common.mvi.MVIBaseState
import es.sebas1705.haltera.core.common.mvi.MVIBaseViewModel
import es.sebas1705.haltera.core.datastore.SettingsRepository
import es.sebas1705.haltera.core.units.LengthUnit
import es.sebas1705.haltera.data.models.BodyMeasurement
import es.sebas1705.haltera.data.models.BodyMeasurements
import es.sebas1705.haltera.data.models.MeasurementType
import es.sebas1705.haltera.data.repository.BodyMeasurementRepository
import es.sebas1705.haltera.feature.progress.model.MeasurementMath
import es.sebas1705.haltera.feature.progress.model.MeasurementSeries
import es.sebas1705.haltera.feature.progress.model.PastSessionTime
import es.sebas1705.haltera.feature.progress.model.ProgressClock
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine

/** The open add/edit dialog. */
internal sealed interface MeasurementDialog {
    data object Add : MeasurementDialog

    data class Edit(val measurement: BodyMeasurement) : MeasurementDialog
}

/** The fields of the add/edit dialog. */
internal data class MeasurementForm(
    val type: MeasurementType = MeasurementType.WAIST,
    val customName: String = "",
    /** The value in the shown unit (cm, in or percent), as typed. */
    val input: String = "",
    val notes: String = "",
    /** The date as UTC midnight (what the date picker uses); [maxDateUtcMillis] is today. */
    val dateUtcMillis: Long = 0,
    val maxDateUtcMillis: Long = 0,
    val valueError: Boolean = false,
    val nameError: Boolean = false,
)

internal data class MeasurementsState(
    val loading: Boolean = true,
    val unit: LengthUnit = LengthUnit.CM,
    /** One chart per type or custom name with entries. */
    val series: List<MeasurementSeries> = emptyList(),
    /** The key of the series on screen; null when there is none. */
    val selectedKey: String? = null,
    /** Custom names already used, offered in the dialog. */
    val customNames: List<String> = emptyList(),
    val dialog: MeasurementDialog? = null,
    val form: MeasurementForm = MeasurementForm(),
) : MVIBaseState {
    val selected: MeasurementSeries? get() = series.firstOrNull { it.key == selectedKey }
}

internal sealed interface MeasurementsIntent : MVIBaseIntent {
    data class SelectSeries(val key: String) : MeasurementsIntent

    data object OpenAdd : MeasurementsIntent

    data class OpenEdit(val measurement: BodyMeasurement) : MeasurementsIntent

    data class TypeChanged(val type: MeasurementType) : MeasurementsIntent

    data class CustomNameChanged(val text: String) : MeasurementsIntent

    data class InputChanged(val text: String) : MeasurementsIntent

    data class NotesChanged(val text: String) : MeasurementsIntent

    /** A date picked in the dialog (UTC midnight); future dates are clamped to today. */
    data class DateChanged(val dateUtcMillis: Long) : MeasurementsIntent

    data object Dismiss : MeasurementsIntent

    data object Save : MeasurementsIntent

    data class Delete(val id: String) : MeasurementsIntent
}

/**
 * Body measurements (roadmap P6): one chart per type or custom name, in centimetres or inches as the weight unit
 * says (kg -> cm, lb -> in) and body fat in percent. Like body weight, any past date can be logged, nothing in the
 * future, and every entry can be edited or deleted.
 */
internal class MeasurementsViewModel(
    private val measurements: BodyMeasurementRepository,
    private val settings: SettingsRepository,
    private val clock: ProgressClock = ProgressClock(),
) : MVIBaseViewModel<MeasurementsState, MeasurementsIntent>(MeasurementsState()) {

    init {
        execute { observe() }
    }

    override fun intentHandler(intent: MeasurementsIntent): Job = execute {
        when (intent) {
            is MeasurementsIntent.SelectSeries -> updateState { it.copy(selectedKey = intent.key) }
            MeasurementsIntent.OpenAdd -> openAdd()
            is MeasurementsIntent.OpenEdit -> openEdit(intent.measurement)
            is MeasurementsIntent.TypeChanged -> updateForm {
                // The typed number keeps its meaning only between lengths; switching to or from body fat clears it.
                val keep = it.type.isLength == intent.type.isLength
                it.copy(type = intent.type, input = if (keep) it.input else "", valueError = false, nameError = false)
            }
            is MeasurementsIntent.CustomNameChanged -> updateForm {
                it.copy(customName = intent.text.take(BodyMeasurements.MAX_NAME_LENGTH), nameError = false)
            }
            is MeasurementsIntent.InputChanged -> updateForm { it.copy(input = intent.text, valueError = false) }
            is MeasurementsIntent.NotesChanged -> updateForm { it.copy(notes = intent.text.take(MAX_NOTES)) }
            is MeasurementsIntent.DateChanged -> updateForm {
                it.copy(dateUtcMillis = intent.dateUtcMillis.coerceAtMost(it.maxDateUtcMillis))
            }
            MeasurementsIntent.Dismiss -> updateState { it.copy(dialog = null) }
            MeasurementsIntent.Save -> save()
            is MeasurementsIntent.Delete -> {
                measurements.delete(intent.id)
                updateState { it.copy(dialog = null) }
            }
        }
    }

    private fun updateForm(change: (MeasurementForm) -> MeasurementForm) {
        updateState { it.copy(form = change(it.form)) }
    }

    /** A new measurement is dated today, of the type on screen (custom: with its name). */
    private fun openAdd() {
        val today = today()
        val selected = currentState.selected
        val form = MeasurementForm(
            type = selected?.type ?: MeasurementType.WAIST,
            customName = selected?.customName.orEmpty(),
            dateUtcMillis = today,
            maxDateUtcMillis = today,
        )
        updateState { it.copy(dialog = MeasurementDialog.Add, form = form) }
    }

    private fun openEdit(measurement: BodyMeasurement) {
        val today = today()
        val date = PastSessionTime.utcMillis(PastSessionTime.localDate(measurement.measuredAt, clock.timeZone))
        val form = MeasurementForm(
            type = measurement.type,
            customName = measurement.customName,
            input = MeasurementMath.inputText(measurement.value, measurement.type, currentState.unit),
            notes = measurement.notes,
            dateUtcMillis = date.coerceAtMost(today),
            maxDateUtcMillis = today,
        )
        updateState { it.copy(dialog = MeasurementDialog.Edit(measurement), form = form) }
    }

    private suspend fun save() {
        val state = currentState
        val form = state.form
        val value = MeasurementMath.parseValue(form.input, form.type, state.unit)
        val name = BodyMeasurements.normalizeName(form.customName)
        val nameMissing = form.type == MeasurementType.CUSTOM && name.isEmpty()
        if (value == null || nameMissing) {
            updateForm { it.copy(valueError = value == null, nameError = nameMissing) }
            return
        }
        val savedKey = when (val dialog = state.dialog) {
            MeasurementDialog.Add -> {
                measurements.add(form.type, value, measuredAt(form.dateUtcMillis), name, form.notes)
                BodyMeasurement("", 0, form.type, value, name).seriesKey
            }
            is MeasurementDialog.Edit -> {
                val old = dialog.measurement
                val sameDay = PastSessionTime.localDate(old.measuredAt, clock.timeZone) ==
                    PastSessionTime.dateOf(form.dateUtcMillis)
                val updated = old.copy(
                    type = form.type,
                    value = value,
                    customName = name,
                    notes = form.notes,
                    measuredAt = if (sameDay) old.measuredAt else measuredAt(form.dateUtcMillis),
                )
                measurements.update(updated)
                updated.seriesKey
            }
            null -> return
        }
        // The chart of what was just saved comes on screen.
        updateState { it.copy(dialog = null, selectedKey = savedKey) }
    }

    /** Now for today, noon for an earlier day (as body weight). */
    private fun measuredAt(dateUtcMillis: Long): Long =
        PastSessionTime.measuredAt(dateUtcMillis, clock.now(), clock.timeZone)

    private fun today(): Long = PastSessionTime.todayUtcMillis(clock.now(), clock.timeZone)

    private suspend fun observe() {
        combine(measurements.observeAll(), settings.settings) { all, userSettings ->
            all to LengthUnit.forWeightUnit(userSettings.unit)
        }.collect { (all, unit) ->
            val series = MeasurementMath.series(all)
            val names = MeasurementMath.customNames(all)
            updateState { state ->
                val keep = state.selectedKey?.takeIf { key -> series.any { it.key == key } }
                // Without a choice (or when its series is gone) the series of the latest measurement is shown.
                val latestKey = all.maxByOrNull { it.measuredAt }?.seriesKey
                state.copy(
                    loading = false,
                    unit = unit,
                    series = series,
                    selectedKey = keep ?: latestKey,
                    customNames = names,
                )
            }
        }
    }

    companion object {
        const val MAX_NOTES = 200
    }
}
