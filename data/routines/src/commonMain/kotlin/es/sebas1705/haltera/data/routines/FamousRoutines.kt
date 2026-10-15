@file:Suppress("MagicNumber")

package es.sebas1705.ironlog.data.routines

import es.sebas1705.ironlog.data.models.DayKind
import es.sebas1705.ironlog.data.models.Level
import es.sebas1705.ironlog.data.models.LoadIncrement
import es.sebas1705.ironlog.data.models.LoadType
import es.sebas1705.ironlog.data.models.LocalizedText
import es.sebas1705.ironlog.data.models.Prescription
import es.sebas1705.ironlog.data.models.ProgressionRule
import es.sebas1705.ironlog.data.models.RoutineTemplate
import es.sebas1705.ironlog.data.models.SlotSpec
import es.sebas1705.ironlog.data.models.SlotTarget
import es.sebas1705.ironlog.data.models.TemplateDay

/**
 * The bundled famous-routine templates (spec §6.3). Names describe the programme and credit the
 * well-known name it follows; the descriptions are our own. Every exercise reference is checked
 * against the bundled catalogue by `FamousRoutinesTest`.
 */
object FamousRoutines {
    val all: List<RoutineTemplate> by lazy {
        listOf(
            beginnerFullBody3,
            linear5x5,
            startingStrength,
            gzclp,
            pplReddit,
            upperLower4,
            phul,
            arnoldSplit,
            broSplit5,
            recommendedRoutine,
            couchTo5k,
            hiitTabata,
        )
    }

    fun byId(id: String): RoutineTemplate? = all.firstOrNull { it.id == id }
}

// region DSL

private const val TEMPLATE_VERSION = 1
private const val MAIN_REST_S = 180
private const val SECONDARY_REST_S = 120
private const val ACCESSORY_REST_S = 90
private const val SHORT_REST_S = 60
private const val MAIN_WARMUPS = 2
private const val BARBELL_STEP_G = 2_500L
private const val LOWER_BODY_STEP_G = 5_000L

private fun text(en: String, es: String) = LocalizedText(en = en, es = es)

private fun fixed(variantId: String): SlotTarget = SlotTarget.Fixed(variantId)

private fun flexible(exerciseId: String, vararg allowedVariantIds: String): SlotTarget =
    SlotTarget.Flexible(exerciseId, allowedVariantIds.toList())

/** Lateral raises with dumbbells or cable, chosen during the session. */
private val lateralRaise = flexible("lateral-raise", "lateral-raise.dumbbell", "lateral-raise.cable")

private val restDay = TemplateDay(name = text("Rest", "Descanso"), kind = DayKind.REST)

private fun day(en: String, es: String, slots: () -> List<SlotSpec>) = TemplateDay(
    text(en, es),
    DayKind.TRAINING,
    slots()
)

private fun template(
    id: String,
    name: LocalizedText,
    description: LocalizedText,
    credit: String,
    level: Level,
    days: List<TemplateDay>,
) = RoutineTemplate(id, TEMPLATE_VERSION, name, description, credit, level, days)

/** A barbell lift with a fixed rep target and linear progression. */
private fun linear(
    variantId: String,
    sets: Int,
    reps: Int,
    incrementGrams: Long = BARBELL_STEP_G,
    restS: Int = MAIN_REST_S,
    warmupSets: Int = MAIN_WARMUPS,
) = SlotSpec(
    target = fixed(variantId),
    sets = sets,
    warmupSets = warmupSets,
    prescription = Prescription.Reps(reps),
    restBetweenSetsS = restS,
    restAfterS = restS,
    progression = ProgressionRule.Linear(LoadIncrement.Weight(incrementGrams)),
)

/** A rep-range slot with double progression using the default increment for [load]. Dumbbell slots count both hands. */
private fun ranged(
    target: SlotTarget,
    load: LoadType,
    sets: Int,
    min: Int,
    max: Int,
    restS: Int = ACCESSORY_REST_S,
    warmupSets: Int = 0,
) = SlotSpec(
    target = target,
    sets = sets,
    warmupSets = warmupSets,
    prescription = Prescription.RepRange(min, max),
    restBetweenSetsS = restS,
    restAfterS = restS,
    bilateral = load == LoadType.DUMBBELL,
    progression = ProgressionRule.DoubleProgression(sessionsAtTop = 1, increment = LoadIncrement.defaultFor(load)),
)

/** A timed slot without progression; [restAfterS] is the recovery before the next slot. */
private fun timed(variantId: String, sets: Int, seconds: Int, restS: Int, restAfterS: Int = restS) = SlotSpec(
    target = fixed(variantId),
    sets = sets,
    prescription = Prescription.Duration(seconds),
    restBetweenSetsS = restS,
    restAfterS = restAfterS,
    progression = ProgressionRule.None,
)

private fun SlotSpec.superset(group: Int) = copy(supersetGroup = group)

private fun SlotSpec.unilateral() = copy(bilateral = false)

// endregion

// region Beginner full body

private val beginnerFullBody3 = template(
    id = "beginner-full-body-3",
    name = text("Beginner full body (3 days)", "Cuerpo completo para principiantes (3 días)"),
    description = text(
        "Three whole-body sessions a week on alternate days. Each session covers legs, a push, a pull and some " +
            "accessory work; add weight once every set reaches the top of its rep range.",
        "Tres sesiones de cuerpo completo a la semana en días alternos. Cada sesión trabaja pierna, un empuje, un " +
            "tirón y algo de accesorios; sube el peso cuando todas las series lleguen al tope del rango.",
    ),
    credit = "Ironlog",
    level = Level.BEGINNER,
    days = listOf(
        day("Full body A", "Cuerpo completo A") {
            listOf(
                ranged(fixed("squat.barbell"), LoadType.BARBELL, 3, 8, 10, SECONDARY_REST_S, MAIN_WARMUPS),
                ranged(fixed("bench-press.barbell"), LoadType.BARBELL, 3, 8, 10, SECONDARY_REST_S, MAIN_WARMUPS),
                ranged(fixed("bent-over-row.barbell"), LoadType.BARBELL, 3, 8, 10, SECONDARY_REST_S),
                ranged(lateralRaise, LoadType.DUMBBELL, 2, 12, 15),
                timed("plank.bodyweight", 3, 30, SHORT_REST_S),
            )
        },
        restDay,
        day("Full body B", "Cuerpo completo B") {
            listOf(
                ranged(fixed("romanian-deadlift.barbell"), LoadType.BARBELL, 3, 8, 10, SECONDARY_REST_S, MAIN_WARMUPS),
                ranged(fixed("overhead-press.barbell"), LoadType.BARBELL, 3, 8, 10, SECONDARY_REST_S, MAIN_WARMUPS),
                ranged(flexible("lat-pulldown"), LoadType.MACHINE, 3, 10, 12),
                ranged(fixed("lunge.dumbbell"), LoadType.DUMBBELL, 2, 10, 12),
                ranged(fixed("crunches.bodyweight"), LoadType.BODYWEIGHT, 2, 12, 15, SHORT_REST_S),
            )
        },
        restDay,
        day("Full body C", "Cuerpo completo C") {
            listOf(
                ranged(fixed("leg-press.machine"), LoadType.MACHINE, 3, 10, 12, SECONDARY_REST_S, 1),
                ranged(fixed("incline-bench-press.dumbbell"), LoadType.DUMBBELL, 3, 8, 12),
                ranged(fixed("seated-row.cable"), LoadType.MACHINE, 3, 10, 12),
                ranged(flexible("biceps-curl"), LoadType.DUMBBELL, 2, 10, 12, SHORT_REST_S),
                ranged(flexible("triceps-pushdown"), LoadType.MACHINE, 2, 10, 12, SHORT_REST_S),
            )
        },
        restDay,
        restDay,
    ),
)

// endregion

// region Linear barbell programmes

private val linear5x5 = template(
    id = "linear-5x5",
    name = text("Linear 5×5 A/B (StrongLifts-style)", "5×5 lineal A/B (estilo StrongLifts)"),
    description = text(
        "Two alternating workouts built on five sets of five on the big barbell lifts. Train three non-consecutive " +
            "days a week and add a little weight every session while all sets are completed.",
        "Dos entrenamientos alternos basados en cinco series de cinco en los grandes básicos con barra. Entrena tres " +
            "días no consecutivos a la semana y sube un poco el peso cada sesión mientras completes todas las series.",
    ),
    credit = "Mehdi Hadim (StrongLifts)",
    level = Level.BEGINNER,
    days = listOf(
        day("Workout A", "Entrenamiento A") {
            listOf(
                linear("squat.barbell", 5, 5),
                linear("bench-press.barbell", 5, 5),
                linear("bent-over-row.barbell", 5, 5),
            )
        },
        day("Workout B", "Entrenamiento B") {
            listOf(
                linear("squat.barbell", 5, 5),
                linear("overhead-press.barbell", 5, 5),
                linear("deadlift.barbell", 1, 5, LOWER_BODY_STEP_G),
            )
        },
    ),
)

private val startingStrength = template(
    id = "starting-strength",
    name = text("Novice barbell A/B (Starting Strength-style)", "Barra para novatos A/B (estilo Starting Strength)"),
    description = text(
        "A minimal novice programme: three sets of five on the squat and presses, one heavy set of deadlifts and " +
            "power cleans for explosiveness. Alternate the two workouts and add weight every session.",
        "Un programa mínimo para novatos: tres series de cinco en sentadilla y presses, una serie pesada de peso " +
            "muerto y cargadas de potencia para la explosividad. Alterna los dos entrenamientos y sube peso cada sesión.",
    ),
    credit = "Mark Rippetoe",
    level = Level.BEGINNER,
    days = listOf(
        day("Workout A", "Entrenamiento A") {
            listOf(
                linear("squat.barbell", 3, 5),
                linear("bench-press.barbell", 3, 5),
                linear("deadlift.barbell", 1, 5, LOWER_BODY_STEP_G),
            )
        },
        day("Workout B", "Entrenamiento B") {
            listOf(
                linear("squat.barbell", 3, 5),
                linear("overhead-press.barbell", 3, 5),
                linear("power-clean.barbell", 5, 3, restS = SECONDARY_REST_S),
            )
        },
    ),
)

/** GZCLP tier 1: heavy 5×3. */
private fun tier1(variantId: String, incrementGrams: Long) = linear(variantId, 5, 3, incrementGrams)

/** GZCLP tier 2: volume 3×10. */
private fun tier2(
    variantId: String,
    incrementGrams: Long
) = linear(variantId, 3, 10, incrementGrams, SECONDARY_REST_S, 1)

/** GZCLP tier 3: high-rep back work with double progression. */
private fun tier3(target: SlotTarget) = ranged(target, LoadType.MACHINE, 3, 15, 20, SHORT_REST_S)

private val gzclp = template(
    id = "gzclp",
    name = text(
        "Tiered linear progression (GZCLP-style, simplified)",
        "Progresión lineal por niveles (estilo GZCLP, simplificado)"
    ),
    description = text(
        "Four days that pair a heavy main lift (tier 1, 5×3) with a lighter volume lift (tier 2, 3×10) and a " +
            "high-rep back exercise (tier 3). Tiers 1 and 2 progress linearly; tier 3 by reps first.",
        "Cuatro días que combinan un básico pesado (nivel 1, 5×3) con un básico ligero de volumen (nivel 2, 3×10) " +
            "y un ejercicio de espalda a altas repeticiones (nivel 3). Los niveles 1 y 2 progresan de forma lineal; " +
            "el 3, primero por repeticiones.",
    ),
    credit = "Cody LeFever",
    level = Level.BEGINNER,
    days = listOf(
        day("Day 1", "Día 1") {
            listOf(
                tier1("squat.barbell", LOWER_BODY_STEP_G),
                tier2("bench-press.barbell", BARBELL_STEP_G),
                tier3(flexible("lat-pulldown")),
            )
        },
        day("Day 2", "Día 2") {
            listOf(
                tier1("overhead-press.barbell", BARBELL_STEP_G),
                tier2("deadlift.barbell", LOWER_BODY_STEP_G),
                tier3(fixed("one-arm-row.dumbbell")).unilateral(),
            )
        },
        day("Day 3", "Día 3") {
            listOf(
                tier1("bench-press.barbell", BARBELL_STEP_G),
                tier2("squat.barbell", LOWER_BODY_STEP_G),
                tier3(flexible("lat-pulldown")),
            )
        },
        day("Day 4", "Día 4") {
            listOf(
                tier1("deadlift.barbell", LOWER_BODY_STEP_G),
                tier2("overhead-press.barbell", BARBELL_STEP_G),
                tier3(fixed("one-arm-row.dumbbell")).unilateral(),
            )
        },
    ),
)

// endregion

// region Push/pull/legs

private fun pplPull(en: String, es: String, main: SlotSpec) = day(en, es) {
    listOf(
        main,
        ranged(flexible("lat-pulldown"), LoadType.MACHINE, 3, 8, 12),
        ranged(fixed("seated-row.cable"), LoadType.MACHINE, 3, 8, 12),
        ranged(fixed("face-pull.cable"), LoadType.MACHINE, 5, 15, 20, SHORT_REST_S),
        ranged(fixed("hammer-curl.dumbbell"), LoadType.DUMBBELL, 4, 8, 12, SHORT_REST_S),
        ranged(fixed("biceps-curl.dumbbell"), LoadType.DUMBBELL, 4, 8, 12, SHORT_REST_S),
    )
}

private fun pplPush(en: String, es: String, main: SlotSpec, secondary: String) = day(en, es) {
    listOf(
        main,
        ranged(fixed(secondary), LoadType.BARBELL, 3, 8, 12, SECONDARY_REST_S, 1),
        ranged(fixed("incline-bench-press.dumbbell"), LoadType.DUMBBELL, 3, 8, 12),
        ranged(fixed("triceps-pushdown.rope"), LoadType.MACHINE, 3, 8, 12, SHORT_REST_S).superset(1),
        ranged(flexible("lateral-raise"), LoadType.DUMBBELL, 3, 15, 20, SHORT_REST_S).superset(1),
        ranged(fixed("overhead-triceps-extension.cable"), LoadType.MACHINE, 3, 8, 12, SHORT_REST_S).superset(2),
        ranged(flexible("lateral-raise"), LoadType.DUMBBELL, 3, 15, 20, SHORT_REST_S).superset(2),
    )
}

private val pplLegs = day("Legs", "Pierna") {
    listOf(
        linear("squat.barbell", 3, 5),
        ranged(fixed("romanian-deadlift.barbell"), LoadType.BARBELL, 3, 8, 12, SECONDARY_REST_S, 1),
        ranged(fixed("leg-press.machine"), LoadType.MACHINE, 3, 8, 12),
        ranged(fixed("leg-curl.lying"), LoadType.MACHINE, 3, 8, 12),
        ranged(fixed("standing-calf-raise.machine"), LoadType.MACHINE, 5, 8, 12, SHORT_REST_S),
    )
}

private val pplReddit = template(
    id = "ppl-reddit",
    name = text("Push/Pull/Legs 6 days (Reddit PPL-style)", "Empuje/Tirón/Pierna 6 días (estilo PPL de Reddit)"),
    description = text(
        "Six sessions a week rotating pull, push and legs. Each day opens with a main barbell lift that progresses " +
            "linearly, followed by accessories in rep ranges with double progression.",
        "Seis sesiones a la semana rotando tirón, empuje y pierna. Cada día empieza con un básico con barra que " +
            "progresa de forma lineal, seguido de accesorios en rangos de repeticiones con doble progresión.",
    ),
    credit = "u/Metallicadpa",
    level = Level.BEGINNER,
    days = listOf(
        pplPull("Pull A", "Tirón A", linear("deadlift.barbell", 1, 5, LOWER_BODY_STEP_G)),
        pplPush("Push A", "Empuje A", linear("bench-press.barbell", 5, 5), "overhead-press.barbell"),
        pplLegs,
        pplPull("Pull B", "Tirón B", linear("bent-over-row.barbell", 5, 5)),
        pplPush("Push B", "Empuje B", linear("overhead-press.barbell", 5, 5), "bench-press.barbell"),
        pplLegs,
        restDay,
    ),
)

// endregion

// region Upper/lower

private val upperLower4 = template(
    id = "upper-lower-4",
    name = text("Upper/Lower 4 days", "Torso/Pierna 4 días"),
    description = text(
        "Four sessions a week alternating upper- and lower-body days, each muscle trained twice a week. Heavy " +
            "compound lifts first, then hypertrophy accessories; all slots use double progression.",
        "Cuatro sesiones a la semana alternando días de torso y de pierna, cada músculo dos veces por semana. " +
            "Primero básicos pesados y después accesorios de hipertrofia; todo con doble progresión.",
    ),
    credit = "Ironlog",
    level = Level.INTERMEDIATE,
    days = listOf(
        day("Upper A", "Torso A") {
            listOf(
                ranged(fixed("bench-press.barbell"), LoadType.BARBELL, 4, 6, 8, MAIN_REST_S, MAIN_WARMUPS),
                ranged(fixed("bent-over-row.barbell"), LoadType.BARBELL, 4, 6, 8, MAIN_REST_S, 1),
                ranged(fixed("overhead-press.dumbbell"), LoadType.DUMBBELL, 3, 8, 10, SECONDARY_REST_S),
                ranged(flexible("lat-pulldown"), LoadType.MACHINE, 3, 10, 12),
                ranged(flexible("biceps-curl"), LoadType.DUMBBELL, 2, 10, 12, SHORT_REST_S),
                ranged(flexible("triceps-pushdown"), LoadType.MACHINE, 2, 10, 12, SHORT_REST_S),
            )
        },
        day("Lower A", "Pierna A") {
            listOf(
                ranged(fixed("squat.barbell"), LoadType.BARBELL, 4, 6, 8, MAIN_REST_S, MAIN_WARMUPS),
                ranged(fixed("romanian-deadlift.barbell"), LoadType.BARBELL, 3, 8, 10, SECONDARY_REST_S, 1),
                ranged(fixed("leg-press.machine"), LoadType.MACHINE, 3, 10, 12),
                ranged(fixed("leg-curl.lying"), LoadType.MACHINE, 3, 10, 12),
                ranged(fixed("standing-calf-raise.machine"), LoadType.MACHINE, 4, 10, 15, SHORT_REST_S),
            )
        },
        restDay,
        day("Upper B", "Torso B") {
            listOf(
                ranged(fixed("overhead-press.barbell"), LoadType.BARBELL, 4, 6, 8, MAIN_REST_S, MAIN_WARMUPS),
                ranged(fixed("pull-up.bodyweight"), LoadType.BODYWEIGHT, 4, 6, 10, SECONDARY_REST_S),
                ranged(fixed("incline-bench-press.dumbbell"), LoadType.DUMBBELL, 3, 8, 10, SECONDARY_REST_S),
                ranged(fixed("seated-row.cable"), LoadType.MACHINE, 3, 10, 12),
                ranged(flexible("lateral-raise"), LoadType.DUMBBELL, 3, 12, 15, SHORT_REST_S),
                ranged(fixed("hammer-curl.dumbbell"), LoadType.DUMBBELL, 2, 10, 12, SHORT_REST_S),
            )
        },
        day("Lower B", "Pierna B") {
            listOf(
                ranged(fixed("deadlift.barbell"), LoadType.BARBELL, 3, 5, 6, MAIN_REST_S, MAIN_WARMUPS),
                ranged(fixed("lunge.dumbbell"), LoadType.DUMBBELL, 3, 10, 12),
                ranged(fixed("leg-extensions.machine"), LoadType.MACHINE, 3, 12, 15),
                ranged(fixed("leg-curl.seated"), LoadType.MACHINE, 3, 10, 12),
                ranged(fixed("seated-calf-raise.machine"), LoadType.MACHINE, 4, 12, 15, SHORT_REST_S),
                ranged(fixed("hanging-leg-raise.bodyweight"), LoadType.BODYWEIGHT, 3, 10, 15, SHORT_REST_S),
            )
        },
        restDay,
        restDay,
    ),
)

private val phul = template(
    id = "phul",
    name = text("Power-hypertrophy upper/lower (PHUL-style)", "Fuerza-hipertrofia torso/pierna (estilo PHUL)"),
    description = text(
        "Four days a week: one heavy upper and lower day for strength, then one upper and lower day with more " +
            "volume for muscle growth. Double progression throughout.",
        "Cuatro días a la semana: un día de torso y otro de pierna pesados para la fuerza, y otro par con más " +
            "volumen para ganar músculo. Doble progresión en todo el programa.",
    ),
    credit = "Brandon Campbell",
    level = Level.INTERMEDIATE,
    days = listOf(
        day("Upper power", "Torso fuerza") {
            listOf(
                ranged(fixed("bench-press.barbell"), LoadType.BARBELL, 4, 3, 5, MAIN_REST_S, MAIN_WARMUPS),
                ranged(fixed("incline-bench-press.dumbbell"), LoadType.DUMBBELL, 4, 6, 10, SECONDARY_REST_S),
                ranged(fixed("bent-over-row.barbell"), LoadType.BARBELL, 4, 3, 5, MAIN_REST_S, 1),
                ranged(flexible("lat-pulldown"), LoadType.MACHINE, 4, 6, 10, SECONDARY_REST_S),
                ranged(fixed("overhead-press.dumbbell"), LoadType.DUMBBELL, 3, 5, 8, SECONDARY_REST_S),
                ranged(fixed("biceps-curl.barbell"), LoadType.BARBELL, 3, 6, 10),
                ranged(fixed("skull-crusher.ez-bar"), LoadType.BARBELL, 3, 6, 10),
            )
        },
        day("Lower power", "Pierna fuerza") {
            listOf(
                ranged(fixed("squat.barbell"), LoadType.BARBELL, 4, 3, 5, MAIN_REST_S, MAIN_WARMUPS),
                ranged(fixed("deadlift.barbell"), LoadType.BARBELL, 4, 3, 5, MAIN_REST_S, 1),
                ranged(fixed("leg-press.machine"), LoadType.MACHINE, 4, 10, 15, SECONDARY_REST_S),
                ranged(fixed("leg-curl.lying"), LoadType.MACHINE, 4, 6, 10),
                ranged(fixed("standing-calf-raise.machine"), LoadType.MACHINE, 4, 6, 10, SHORT_REST_S),
            )
        },
        restDay,
        day("Upper hypertrophy", "Torso hipertrofia") {
            listOf(
                ranged(fixed("incline-bench-press.barbell"), LoadType.BARBELL, 4, 8, 12, SECONDARY_REST_S, 1),
                ranged(fixed("chest-fly.dumbbell"), LoadType.DUMBBELL, 4, 8, 12),
                ranged(fixed("seated-row.cable"), LoadType.MACHINE, 4, 8, 12),
                ranged(fixed("one-arm-row.dumbbell"), LoadType.DUMBBELL, 4, 8, 12).unilateral(),
                ranged(flexible("lateral-raise"), LoadType.DUMBBELL, 4, 8, 12, SHORT_REST_S),
                ranged(flexible("biceps-curl"), LoadType.DUMBBELL, 4, 8, 12, SHORT_REST_S),
                ranged(fixed("overhead-triceps-extension.cable"), LoadType.MACHINE, 4, 8, 12, SHORT_REST_S),
            )
        },
        day("Lower hypertrophy", "Pierna hipertrofia") {
            listOf(
                ranged(fixed("front-squat.barbell"), LoadType.BARBELL, 4, 8, 12, SECONDARY_REST_S, MAIN_WARMUPS),
                ranged(fixed("lunge.dumbbell"), LoadType.DUMBBELL, 4, 8, 12),
                ranged(fixed("leg-extensions.machine"), LoadType.MACHINE, 4, 10, 15),
                ranged(fixed("leg-curl.seated"), LoadType.MACHINE, 4, 10, 15),
                ranged(fixed("seated-calf-raise.machine"), LoadType.MACHINE, 4, 8, 12, SHORT_REST_S),
            )
        },
        restDay,
        restDay,
    ),
)

// endregion

// region Body-part splits

private val arnoldChestBack = day("Chest and back", "Pecho y espalda") {
    listOf(
        ranged(fixed("bench-press.barbell"), LoadType.BARBELL, 4, 8, 12, SECONDARY_REST_S, MAIN_WARMUPS).superset(1),
        ranged(fixed("pull-up.bodyweight"), LoadType.BODYWEIGHT, 4, 6, 10, SECONDARY_REST_S).superset(1),
        ranged(fixed("incline-bench-press.barbell"), LoadType.BARBELL, 4, 8, 12, SECONDARY_REST_S).superset(2),
        ranged(fixed("bent-over-row.barbell"), LoadType.BARBELL, 4, 8, 12, SECONDARY_REST_S).superset(2),
        ranged(fixed("chest-fly.dumbbell"), LoadType.DUMBBELL, 3, 10, 12),
        ranged(fixed("parallel-bar-dip.bodyweight"), LoadType.BODYWEIGHT, 3, 8, 12),
    )
}

private val arnoldShouldersArms = day("Shoulders and arms", "Hombro y brazo") {
    listOf(
        ranged(fixed("overhead-press.barbell"), LoadType.BARBELL, 4, 8, 12, SECONDARY_REST_S, MAIN_WARMUPS),
        ranged(fixed("arnold-dumbbell-press.dumbbell"), LoadType.DUMBBELL, 3, 8, 12),
        ranged(flexible("lateral-raise"), LoadType.DUMBBELL, 4, 10, 12, SHORT_REST_S),
        ranged(fixed("biceps-curl.barbell"), LoadType.BARBELL, 4, 8, 12).superset(1),
        ranged(fixed("skull-crusher.ez-bar"), LoadType.BARBELL, 4, 8, 12).superset(1),
        ranged(fixed("biceps-curl.dumbbell"), LoadType.DUMBBELL, 3, 10, 12).superset(2),
        ranged(fixed("triceps-pushdown.rope"), LoadType.MACHINE, 3, 10, 12).superset(2),
    )
}

private val arnoldLegs = day("Legs", "Pierna") {
    listOf(
        ranged(fixed("squat.barbell"), LoadType.BARBELL, 5, 8, 12, MAIN_REST_S, MAIN_WARMUPS),
        ranged(fixed("leg-press.machine"), LoadType.MACHINE, 4, 10, 12, SECONDARY_REST_S),
        ranged(fixed("romanian-deadlift.barbell"), LoadType.BARBELL, 3, 8, 10, SECONDARY_REST_S),
        ranged(fixed("leg-extensions.machine"), LoadType.MACHINE, 3, 12, 15),
        ranged(fixed("leg-curl.lying"), LoadType.MACHINE, 3, 10, 12),
        ranged(fixed("standing-calf-raise.machine"), LoadType.MACHINE, 5, 10, 15, SHORT_REST_S),
    )
}

private val arnoldSplit = template(
    id = "arnold-split",
    name = text(
        "Chest-back / shoulders-arms / legs (Arnold split)",
        "Pecho-espalda / hombro-brazo / pierna (rutina Arnold)"
    ),
    description = text(
        "A high-volume six-day split that trains every muscle twice a week, pairing opposing muscles in supersets. " +
            "Best suited to experienced lifters with time to recover.",
        "Una división de seis días con mucho volumen que entrena cada músculo dos veces por semana, emparejando " +
            "músculos opuestos en superseries. Pensada para gente con experiencia y tiempo para recuperarse.",
    ),
    credit = "Arnold Schwarzenegger",
    level = Level.EXPERT,
    days = listOf(
        arnoldChestBack,
        arnoldShouldersArms,
        arnoldLegs,
        arnoldChestBack,
        arnoldShouldersArms,
        arnoldLegs,
        restDay
    ),
)

private val broSplit5 = template(
    id = "bro-split-5",
    name = text("Classic 5-day body-part split", "Rutina clásica por grupos musculares de 5 días"),
    description = text(
        "One muscle group per day, five days a week: chest, back, shoulders, legs and arms. Plenty of volume per " +
            "session and a full week of recovery for each group.",
        "Un grupo muscular por día, cinco días a la semana: pecho, espalda, hombro, pierna y brazo. Mucho volumen " +
            "por sesión y una semana entera de recuperación para cada grupo.",
    ),
    credit = "Bodybuilding tradition",
    level = Level.INTERMEDIATE,
    days = listOf(
        day("Chest", "Pecho") {
            listOf(
                ranged(fixed("bench-press.barbell"), LoadType.BARBELL, 4, 6, 10, MAIN_REST_S, MAIN_WARMUPS),
                ranged(fixed("incline-bench-press.dumbbell"), LoadType.DUMBBELL, 3, 8, 12, SECONDARY_REST_S),
                ranged(fixed("chest-fly.cable"), LoadType.MACHINE, 3, 10, 15),
                ranged(fixed("parallel-bar-dip.bodyweight"), LoadType.BODYWEIGHT, 3, 8, 12),
            )
        },
        day("Back", "Espalda") {
            listOf(
                ranged(fixed("deadlift.barbell"), LoadType.BARBELL, 3, 5, 8, MAIN_REST_S, MAIN_WARMUPS),
                ranged(fixed("pull-up.bodyweight"), LoadType.BODYWEIGHT, 3, 6, 10, SECONDARY_REST_S),
                ranged(fixed("bent-over-row.barbell"), LoadType.BARBELL, 3, 8, 10, SECONDARY_REST_S),
                ranged(flexible("lat-pulldown"), LoadType.MACHINE, 3, 10, 12),
                ranged(fixed("seated-row.cable"), LoadType.MACHINE, 3, 10, 12),
            )
        },
        day("Shoulders", "Hombro") {
            listOf(
                ranged(fixed("overhead-press.barbell"), LoadType.BARBELL, 4, 6, 10, MAIN_REST_S, MAIN_WARMUPS),
                ranged(fixed("arnold-dumbbell-press.dumbbell"), LoadType.DUMBBELL, 3, 8, 12),
                ranged(lateralRaise, LoadType.DUMBBELL, 4, 12, 15, SHORT_REST_S),
                ranged(fixed("face-pull.cable"), LoadType.MACHINE, 3, 12, 15, SHORT_REST_S),
                ranged(fixed("shrug.barbell"), LoadType.BARBELL, 3, 10, 12),
            )
        },
        day("Legs", "Pierna") {
            listOf(
                ranged(fixed("squat.barbell"), LoadType.BARBELL, 4, 6, 10, MAIN_REST_S, MAIN_WARMUPS),
                ranged(fixed("leg-press.machine"), LoadType.MACHINE, 3, 10, 12, SECONDARY_REST_S),
                ranged(fixed("romanian-deadlift.barbell"), LoadType.BARBELL, 3, 8, 10, SECONDARY_REST_S),
                ranged(fixed("leg-extensions.machine"), LoadType.MACHINE, 3, 12, 15),
                ranged(fixed("leg-curl.lying"), LoadType.MACHINE, 3, 10, 12),
                ranged(fixed("standing-calf-raise.machine"), LoadType.MACHINE, 4, 10, 15, SHORT_REST_S),
            )
        },
        day("Arms", "Brazo") {
            listOf(
                ranged(fixed("biceps-curl.barbell"), LoadType.BARBELL, 3, 8, 12),
                ranged(fixed("skull-crusher.ez-bar"), LoadType.BARBELL, 3, 8, 12),
                ranged(fixed("hammer-curl.dumbbell"), LoadType.DUMBBELL, 3, 10, 12, SHORT_REST_S),
                ranged(fixed("triceps-pushdown.rope"), LoadType.MACHINE, 3, 10, 12, SHORT_REST_S),
                ranged(flexible("preacher-curl"), LoadType.MACHINE, 3, 10, 12, SHORT_REST_S),
                ranged(flexible("overhead-triceps-extension"), LoadType.MACHINE, 3, 10, 12, SHORT_REST_S),
            )
        },
        restDay,
        restDay,
    ),
)

// endregion

// region Bodyweight

private val recommendedRoutineDay = day("Bodyweight session", "Sesión de peso corporal") {
    listOf(
        ranged(fixed("pull-up.bodyweight"), LoadType.BODYWEIGHT, 3, 5, 8).superset(1),
        ranged(fixed("parallel-bar-dip.bodyweight"), LoadType.BODYWEIGHT, 3, 5, 8).superset(1),
        ranged(fixed("bodyweight-squat.bodyweight"), LoadType.BODYWEIGHT, 3, 5, 8).superset(2),
        ranged(fixed("single-leg-glute-bridge.bodyweight"), LoadType.BODYWEIGHT, 3, 5, 8).superset(2),
        ranged(fixed("inverted-row.bodyweight"), LoadType.BODYWEIGHT, 3, 5, 8).superset(3),
        ranged(fixed("pushups.bodyweight"), LoadType.BODYWEIGHT, 3, 5, 8).superset(3),
        ranged(fixed("hanging-leg-raise.bodyweight"), LoadType.BODYWEIGHT, 3, 8, 12, SHORT_REST_S).superset(4),
        timed("plank.bodyweight", 3, 30, SHORT_REST_S).superset(4),
    )
}

private val recommendedRoutine = template(
    id = "recommended-routine",
    name = text(
        "Bodyweight recommended routine (r/bodyweightfitness-style)",
        "Rutina recomendada de peso corporal (estilo r/bodyweightfitness)",
    ),
    description = text(
        "Three full-body calisthenics sessions a week built from paired pull/push, squat/hinge and core exercises. " +
            "When every set reaches eight reps, move to a harder variation or add a little weight.",
        "Tres sesiones de calistenia de cuerpo completo a la semana con parejas de tirón/empuje, sentadilla/bisagra " +
            "y core. Cuando todas las series lleguen a ocho repeticiones, pasa a una variante más difícil o añade algo de peso.",
    ),
    credit = "r/bodyweightfitness",
    level = Level.BEGINNER,
    days = listOf(
        recommendedRoutineDay,
        restDay,
        recommendedRoutineDay,
        restDay,
        recommendedRoutineDay,
        restDay,
        restDay
    ),
)

// endregion

// region Cardio

private val warmUpWalk = timed("walking.outdoor", 1, 300, 0, 0)
private val coolDownWalk = timed("walking.outdoor", 1, 300, 0, 0)

private fun runWeek(week: Int, vararg intervals: SlotSpec): List<TemplateDay> {
    val slots = listOf(warmUpWalk) + intervals + coolDownWalk
    val run = TemplateDay(text("Week $week · Run", "Semana $week · Carrera"), DayKind.TRAINING, slots)
    return listOf(run, restDay, run, restDay, run, restDay, restDay)
}

private val couchTo5k = template(
    id = "couch-to-5k",
    name = text("Couch to 5K: weeks 1–3", "Del sofá a 5 km: semanas 1–3"),
    description = text(
        "The first three weeks of a gradual walk-to-run plan: three runs a week, each one a five-minute warm-up " +
            "walk, short running intervals with walking recovery, and a five-minute cool-down walk.",
        "Las tres primeras semanas de un plan progresivo de caminar a correr: tres salidas a la semana, cada una con " +
            "cinco minutos de calentamiento caminando, intervalos cortos de carrera con recuperación caminando y " +
            "cinco minutos de vuelta a la calma.",
    ),
    credit = "Josh Clark (Cool Running / NHS)",
    level = Level.BEGINNER,
    days = runWeek(1, timed("running.outdoor", 8, 60, 90)) +
        runWeek(2, timed("running.outdoor", 6, 90, 120)) +
        runWeek(
            3,
            timed("running.outdoor", 1, 90, 0, 90),
            timed("running.outdoor", 1, 180, 0, 180),
            timed("running.outdoor", 1, 90, 0, 90),
            timed("running.outdoor", 1, 180, 0, 180),
        ),
)

private val hiitTabata = template(
    id = "hiit-tabata",
    name = text("Tabata HIIT (20 s on / 10 s off)", "HIIT Tabata (20 s de trabajo / 10 s de descanso)"),
    description = text(
        "Four four-minute blocks of eight all-out 20-second efforts with 10 seconds of rest, and a minute of easy " +
            "recovery between blocks. Opens with the stationary bike used in the original protocol.",
        "Cuatro bloques de cuatro minutos con ocho esfuerzos máximos de 20 segundos y 10 segundos de descanso, y un " +
            "minuto de recuperación suave entre bloques. Empieza con la bicicleta estática del protocolo original.",
    ),
    credit = "Izumi Tabata (protocol)",
    level = Level.INTERMEDIATE,
    days = listOf(
        day("Tabata", "Tabata") {
            listOf(
                timed("walking.treadmill", 1, 300, 0, SHORT_REST_S),
                timed("bicycling-stationary.cardio-machine", 8, 20, 10, SHORT_REST_S),
                timed("rope-jumping.other", 8, 20, 10, SHORT_REST_S),
                timed("rowing-stationary.cardio-machine", 8, 20, 10, SHORT_REST_S),
                timed("running.treadmill", 8, 20, 10, SHORT_REST_S),
            )
        },
    ),
)

// endregion
