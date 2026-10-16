package es.sebas1705.haltera.data.repository.sql

import es.sebas1705.haltera.core.search.FieldKind
import es.sebas1705.haltera.core.search.GymSynonyms
import es.sebas1705.haltera.core.search.SearchDocument
import es.sebas1705.haltera.core.search.SearchField
import es.sebas1705.haltera.core.search.SearchIndex
import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.Exercise
import es.sebas1705.haltera.data.models.ExerciseCategory
import es.sebas1705.haltera.data.models.ExerciseFilter
import es.sebas1705.haltera.data.models.ExerciseSource
import es.sebas1705.haltera.data.models.LocalizedText
import es.sebas1705.haltera.data.models.Muscle
import kotlin.math.ln
import kotlin.math.min

/** How often and how recently the user logged an exercise (sessions with a completed set). */
internal data class ExerciseUsage(val sessions: Long, val lastStartedAt: Long)

/**
 * Fuzzy exercise search over every active exercise (docs/designs/2026-10-16-fuzzy-search-design.md):
 * the [SearchIndex] of one app language plus the filters of [ExerciseFilter] applied in memory.
 */
internal class ExerciseSearch(exercises: List<Exercise>, language: String) {
    private val exercises: List<Exercise> = exercises.sortedWith(compareBy({ it.name.of(language) }, { it.id }))
    private val equipment: List<Set<Equipment>> = this.exercises.map { e -> e.variants.map { it.equipment }.toSet() }
    private val index: SearchIndex<Int> = SearchIndex.build(
        this.exercises.mapIndexed { i, e -> ExerciseSearchDocuments.document(i, e, language) },
        GymSynonyms.table,
    )

    /** Exercises matching [filter] (non-blank query), best first; [usage] by exercise id, [now] in epoch millis. */
    fun search(filter: ExerciseFilter, limit: Int, usage: Map<String, ExerciseUsage>, now: Long): List<Exercise> =
        hits(filter, limit, usage, now).map { it.first }

    /** [search] with the scores (tests and tuning). */
    fun hits(
        filter: ExerciseFilter,
        limit: Int,
        usage: Map<String, ExerciseUsage>,
        now: Long
    ): List<Pair<Exercise, Double>> =
        index.search(
            query = filter.query,
            limit = limit,
            accept = { i -> matches(i, filter) },
            boost = { i -> boost(exercises[i], usage[exercises[i].id], now) },
        ).map { exercises[it.key] to it.score }

    private fun matches(i: Int, filter: ExerciseFilter): Boolean {
        val e = exercises[i]
        return (filter.category == null || e.category == filter.category) &&
            (filter.muscle == null || filter.muscle in e.primaryMuscles) &&
            (filter.equipment == null || filter.equipment in equipment[i])
    }

    companion object {
        /** Most a usage history adds to a score: below a typo's penalty, above a tie. */
        const val USAGE_BOOST = 0.12
        const val RECENT_BOOST = 0.05
        const val CUSTOM_BOOST = 0.05
        private const val USAGE_SATURATION = 20.0
        private const val RECENT_DAYS = 14L
        private const val DAY_MILLIS = 86_400_000L

        /** Small bonus for exercises logged often or in the last two weeks, and for the user's own ones. */
        fun boost(exercise: Exercise, usage: ExerciseUsage?, now: Long): Double {
            var boost = if (exercise.source == ExerciseSource.USER) CUSTOM_BOOST else 0.0
            if (usage != null && usage.sessions > 0) {
                boost += USAGE_BOOST * min(1.0, ln(1.0 + usage.sessions) / ln(1.0 + USAGE_SATURATION))
                if (now - usage.lastStartedAt <= RECENT_DAYS * DAY_MILLIS) boost += RECENT_BOOST
            }
            return boost
        }
    }
}

private fun LocalizedText.of(language: String): String =
    if (language == "es") es else en

/** The searchable fields of an exercise and their weights (design note, "Fields and weights"). */
internal object ExerciseSearchDocuments {
    const val NAME = 1.0
    const val OTHER_NAME = 0.9
    const val VARIANT_NAME = 0.8
    const val PRIMARY_MUSCLE = 0.7
    const val EQUIPMENT = 0.65
    const val SECONDARY_MUSCLE = 0.5
    const val CATEGORY = 0.45

    fun <K> document(key: K, e: Exercise, language: String): SearchDocument<K> {
        val other = if (language == "es") "en" else "es"
        val fields = buildList {
            add(SearchField(e.name.of(language), NAME, FieldKind.NAME))
            add(SearchField(e.name.of(other), OTHER_NAME, FieldKind.NAME))
            e.variants.forEach { v ->
                v.name?.let { name ->
                    add(SearchField(name.of(language), VARIANT_NAME, FieldKind.ALIAS))
                    add(SearchField(name.of(other), VARIANT_NAME, FieldKind.ALIAS))
                }
            }
            e.primaryMuscles.forEach { add(SearchField(MUSCLE_TERMS.getValue(it), PRIMARY_MUSCLE)) }
            e.secondaryMuscles.forEach { add(SearchField(MUSCLE_TERMS.getValue(it), SECONDARY_MUSCLE)) }
            e.variants.map { it.equipment }.distinct().forEach {
                add(
                    SearchField(EQUIPMENT_TERMS.getValue(it), EQUIPMENT)
                )
            }
            add(SearchField(CATEGORY_TERMS.getValue(e.category), CATEGORY))
        }
        return SearchDocument(key, fields)
    }

    /** Muscle words in English and Spanish (UI labels plus common gym words). */
    val MUSCLE_TERMS: Map<Muscle, String> = mapOf(
        Muscle.ABDOMINALS to "abdominals abs core abdominales abdomen",
        Muscle.ABDUCTORS to "abductors abductores",
        Muscle.ADDUCTORS to "adductors aductores",
        Muscle.BICEPS to "biceps",
        Muscle.CALVES to "calves calf gemelos pantorrillas",
        Muscle.CHEST to "chest pecs pectorals pecho pectorales",
        Muscle.FOREARMS to "forearms antebrazos",
        Muscle.GLUTES to "glutes gluteos",
        Muscle.HAMSTRINGS to "hamstrings isquiotibiales isquios femorales",
        Muscle.LATS to "lats dorsales espalda back",
        Muscle.LOWER_BACK to "lower back lumbar lumbares espalda",
        Muscle.MIDDLE_BACK to "middle back upper back espalda media",
        Muscle.NECK to "neck cuello",
        Muscle.QUADRICEPS to "quadriceps quads cuadriceps",
        Muscle.SHOULDERS to "shoulders delts deltoids hombros deltoides",
        Muscle.TRAPS to "traps trapezius trapecios",
        Muscle.TRICEPS to "triceps",
        Muscle.FULL_BODY to "full body cuerpo completo",
    )

    /** Equipment words in English and Spanish. */
    val EQUIPMENT_TERMS: Map<Equipment, String> = mapOf(
        Equipment.BARBELL to "barbell barra",
        Equipment.EZ_BAR to "ez bar barra z",
        Equipment.TRAP_BAR to "trap bar hex bar barra hexagonal",
        Equipment.DUMBBELL to "dumbbell mancuerna",
        Equipment.KETTLEBELL to "kettlebell pesa rusa",
        Equipment.SMITH to "smith multipower",
        Equipment.MACHINE to "machine maquina",
        Equipment.CABLE to "cable pulley polea",
        Equipment.BAND to "band goma elastica",
        Equipment.BODYWEIGHT to "bodyweight peso corporal",
        Equipment.MEDICINE_BALL to "medicine ball balon medicinal",
        Equipment.SWISS_BALL to "swiss ball fitball",
        Equipment.FOAM_ROLLER to "foam roller rodillo espuma",
        Equipment.CARDIO_MACHINE to "cardio machine maquina cardio",
        Equipment.OTHER to "",
        Equipment.NONE to "",
    )

    /** Category words in English and Spanish. */
    val CATEGORY_TERMS: Map<ExerciseCategory, String> = mapOf(
        ExerciseCategory.STRENGTH to "strength fuerza",
        ExerciseCategory.CARDIO to "cardio",
        ExerciseCategory.MOBILITY to "mobility stretching movilidad estiramientos",
        ExerciseCategory.PLYOMETRICS to "plyometrics pliometria",
        ExerciseCategory.CALISTHENICS to "calisthenics calistenia",
        ExerciseCategory.SPORT to "sport deporte",
    )
}
