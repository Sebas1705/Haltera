package es.sebas1705.ironlog.domain.usecases.stats

/**
 * Catalogue of every statistic the app shows. Each id is explained in the UI by one info dialog
 * (title, summary, formula, what counts and limitations, all localised in feature:progress) and computed
 * by the function named in its KDoc, so the explanation and the maths have one place each.
 */
enum class Metric {
    /** Epley estimated one-rep max of the best set of a session: [TrainingMath.setOneRm]. */
    ESTIMATED_ONE_RM,

    /** Heaviest load of a session's counted sets: [ExerciseProgression.table] `topLoad`. */
    TOP_SET,

    /** Σ load × reps of counted sets: [TrainingMath.setVolume]. */
    VOLUME,

    /** Completed non-warm-up sets with reps, time or distance: [TrainingMath.isEffective]. */
    EFFECTIVE_SETS,

    /** Personal records of one variant: [PrDetector.events]. */
    PERSONAL_RECORDS,

    /** Records set inside the period: [Overview.build] `newPrs`. */
    NEW_PRS,

    /** Finished sessions started inside the period. */
    SESSIONS,

    /** Start to finish of one session: [TrainingMath.durationMillis]. */
    SESSION_DURATION,

    /** Sum of session durations in the period. */
    TRAINING_TIME,

    /** Sessions per 7 days: [StatsBucket.perWeek]. */
    FREQUENCY,

    /** Trailing average of the latest entries (body weight). */
    MOVING_AVERAGE,

    /** This period against the previous equal period: [Periods], [Comparison.percent]. */
    PERCENT_CHANGE,

    /** Sessions logged against the active routine's plan: [Overview.plannedPerWeek]. */
    ADHERENCE,

    /** Training days calendar: [Heatmaps.build]. */
    TRAINING_HEATMAP,

    /** Least-squares slope of the estimated 1RM: [ExerciseProgression.oneRmTrend]. */
    ONE_RM_TREND,

    /** Effective sets by repetitions: [ExerciseProgression.repRanges]. */
    REP_RANGES,

    /** One row per session of an exercise: [ExerciseProgression.table]. */
    PROGRESSION_TABLE,

    /** Effective sets per week by primary muscle: [MuscleStats.compare]. */
    MUSCLE_SETS,

    /** Volume per week by primary muscle: [MuscleStats.compare]. */
    MUSCLE_VOLUME,

    /** Push/pull, upper/lower and quads/hamstrings set ratios: [MuscleStats.balance]. */
    MUSCLE_BALANCE,

    /** Average body weight per calendar week: [BodyWeightStats.weeklyAverages]. */
    BODY_WEIGHT_WEEKLY,

    /** Latest body weight minus the oldest entry of the 30 days before it (feature ProgressMath.change). */
    BODY_WEIGHT_CHANGE,

    /** Body weight change per week over the last 28 days: [BodyWeightStats.ratePerWeek]. */
    BODY_WEIGHT_RATE,

    /** Two variants or exercises on one chart. */
    COMPARE,
}
