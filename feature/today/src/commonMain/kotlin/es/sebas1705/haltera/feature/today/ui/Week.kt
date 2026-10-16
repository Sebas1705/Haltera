package es.sebas1705.haltera.feature.today.ui

import es.sebas1705.haltera.data.models.WorkoutSession
import es.sebas1705.haltera.domain.usecases.stats.TrainingMath
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Clock and time zone of the Today tab, injectable for tests. */
internal class TodayClock(val now: () -> Long, val timeZone: TimeZone)

/** The current week, Monday to Sunday, in [timeZone]: [startMillis] inclusive, [endMillis] exclusive. */
internal data class WeekRange(
    val today: LocalDate,
    val days: List<LocalDate>,
    val startMillis: Long,
    val endMillis: Long,
    val timeZone: TimeZone,
)

internal object Week {
    private const val DAYS_IN_WEEK = 7

    fun rangeFor(nowMillis: Long, timeZone: TimeZone): WeekRange {
        val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(timeZone).date
        val monday = today.minus(today.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
        val days = List(DAYS_IN_WEEK) { monday.plus(it, DateTimeUnit.DAY) }
        return WeekRange(
            today = today,
            days = days,
            startMillis = monday.atStartOfDayIn(timeZone).toEpochMilliseconds(),
            endMillis = monday.plus(DAYS_IN_WEEK, DateTimeUnit.DAY).atStartOfDayIn(timeZone).toEpochMilliseconds(),
            timeZone = timeZone,
        )
    }

    /** Marks the days with a session and totals the week (sessions and counted sets: completed, not warm-up; volume lives in Progress). */
    fun summarize(range: WeekRange, sessions: List<WorkoutSession>): WeekUi {
        val trainedDates = sessions
            .map { Instant.fromEpochMilliseconds(it.startedAt).toLocalDateTime(range.timeZone).date }
            .toSet()
        return WeekUi(
            days = range.days.map { date ->
                WeekDayUi(
                    isoDayOfWeek = date.dayOfWeek.isoDayNumber,
                    dayOfMonth = date.day,
                    isToday = date == range.today,
                    trained = date in trainedDates,
                )
            },
            sessionCount = sessions.size,
            setCount = sessions.sumOf { session ->
                session.exercises.sumOf { exercise ->
                    exercise.sets.count(
                        TrainingMath::isCounted
                    )
                }
            },
        )
    }
}
