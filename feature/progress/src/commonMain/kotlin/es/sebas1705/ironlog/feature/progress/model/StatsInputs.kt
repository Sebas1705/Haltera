@file:Suppress("MatchingDeclarationName") // The period selection and the small stats input helpers live together.

package es.sebas1705.ironlog.feature.progress.model

import es.sebas1705.ironlog.data.models.Routine
import es.sebas1705.ironlog.data.models.WorkoutSession
import es.sebas1705.ironlog.domain.usecases.stats.DatedSession
import es.sebas1705.ironlog.domain.usecases.stats.StatsPeriod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** The period chosen on Overview and Muscles, shared so both tabs show the same range (one per app). */
internal class StatsPeriodSelection(initial: StatsPeriod = StatsPeriod.FOUR_WEEKS) {
    private val state = MutableStateFlow(initial)
    val period: StateFlow<StatsPeriod> = state.asStateFlow()

    fun select(period: StatsPeriod) {
        state.value = period
    }
}

/** Local epoch day of a timestamp (see `EpochDays` in domain stats). */
internal fun Long.toEpochDay(timeZone: TimeZone): Int = toLocalDate(timeZone).toEpochDays().toInt()

internal fun LocalDate.toEpochDay(): Int = toEpochDays().toInt()

/** Sessions placed on their local start day. */
internal fun List<WorkoutSession>.dated(timeZone: TimeZone): List<DatedSession> =
    map { DatedSession(it.startedAt.toEpochDay(timeZone), it) }

/** Slots whose dumbbell volume counts both hands, from every routine. */
internal fun List<Routine>.bilateralSlots(): Set<String> =
    flatMap { routine -> routine.days.flatMap { day -> day.slots.filter { it.bilateral }.map { it.id } } }.toSet()
