package es.sebas1705.ironlog.data.models

/**
 * Enum values are stored by name in the database and in the bundled data. Every enum here has a
 * lenient `fromDb` that maps unknown or missing values to a safe default instead of throwing, so a
 * newer catalogue or an older app version never crashes on a value it does not know.
 */
internal inline fun <reified E : Enum<E>> lenientEnum(value: String?, default: E): E =
    value?.trim()?.uppercase()?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

/** Parses a comma-separated list of enum names, dropping unknown values. */
internal inline fun <reified E : Enum<E>> lenientEnumList(value: String?): List<E> =
    value.orEmpty().split(',').mapNotNull { raw ->
        val name = raw.trim().uppercase()
        enumValues<E>().firstOrNull { it.name == name }
    }

enum class ExerciseCategory {
    STRENGTH, CARDIO, MOBILITY, PLYOMETRICS, CALISTHENICS, SPORT;

    companion object {
        fun fromDb(value: String?): ExerciseCategory = lenientEnum(value, STRENGTH)
    }
}

/** What a set of the exercise records (spec §3.1). */
enum class TrackingType {
    WEIGHT_REPS, BODYWEIGHT_REPS, REPS, TIME, WEIGHT_TIME, DISTANCE_TIME;

    val tracksLoad: Boolean get() = this == WEIGHT_REPS || this == BODYWEIGHT_REPS || this == WEIGHT_TIME
    val tracksReps: Boolean get() = this == WEIGHT_REPS || this == BODYWEIGHT_REPS || this == REPS
    val tracksDuration: Boolean get() = this == TIME || this == WEIGHT_TIME || this == DISTANCE_TIME
    val tracksDistance: Boolean get() = this == DISTANCE_TIME

    companion object {
        fun fromDb(value: String?): TrackingType = lenientEnum(value, WEIGHT_REPS)
    }
}

enum class Level {
    BEGINNER, INTERMEDIATE, EXPERT;

    companion object {
        fun fromDb(value: String?): Level = lenientEnum(value, BEGINNER)
    }
}

enum class Muscle {
    ABDOMINALS, ABDUCTORS, ADDUCTORS, BICEPS, CALVES, CHEST, FOREARMS, GLUTES, HAMSTRINGS, LATS,
    LOWER_BACK, MIDDLE_BACK, NECK, QUADRICEPS, SHOULDERS, TRAPS, TRICEPS, FULL_BODY;

    companion object {
        /** Parses a comma-separated column; unknown muscle names are dropped. */
        fun listFromDb(value: String?): List<Muscle> = lenientEnumList(value)

        fun listToDb(muscles: List<Muscle>): String = muscles.joinToString(",") { it.name }
    }
}

enum class Equipment {
    BARBELL, EZ_BAR, TRAP_BAR, DUMBBELL, KETTLEBELL, SMITH, MACHINE, CABLE, BAND, BODYWEIGHT,
    MEDICINE_BALL, SWISS_BALL, FOAM_ROLLER, CARDIO_MACHINE, OTHER, NONE;

    companion object {
        fun fromDb(value: String?): Equipment = lenientEnum(value, OTHER)
    }
}

/** How load is entered and stored for a variant (spec §3.1). */
enum class LoadType {
    BARBELL, DUMBBELL, MACHINE, BODYWEIGHT, BAND, NONE;

    companion object {
        fun fromDb(value: String?): LoadType = lenientEnum(value, NONE)
    }
}

enum class ExerciseSource {
    CATALOG, USER;

    companion object {
        fun fromDb(value: String?): ExerciseSource = lenientEnum(value, CATALOG)
    }
}

/** A routine day is either trained or a planned rest (spec §3.2). */
enum class DayKind {
    TRAINING, REST;

    companion object {
        fun fromDb(value: String?): DayKind = lenientEnum(value, TRAINING)
    }
}

enum class SetKind {
    WARMUP, WORKING, DROP, FAILURE;

    companion object {
        fun fromDb(value: String?): SetKind = lenientEnum(value, WORKING)
    }
}
