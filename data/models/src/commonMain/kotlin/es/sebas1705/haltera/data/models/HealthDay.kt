package es.sebas1705.haltera.data.models

import kotlinx.datetime.LocalDate

/**
 * One local day of totals read from the phone's health platform (Health Connect). Every value is null when the
 * platform had no data for that day or the type is not read.
 *
 * @property activeKcal Calories burned by activity.
 * @property totalKcal Calories burned in total (activity plus resting metabolism), as the platform computes it.
 * @property restingHeartRate Average resting heart rate of the day, beats per minute.
 * @property sleepMinutes Sleep of the night that ended on [date] (18:00 the day before to 18:00 that day).
 */
data class HealthDay(
    val date: LocalDate,
    val steps: Long? = null,
    val activeKcal: Double? = null,
    val totalKcal: Double? = null,
    val restingHeartRate: Long? = null,
    val sleepMinutes: Long? = null,
)
