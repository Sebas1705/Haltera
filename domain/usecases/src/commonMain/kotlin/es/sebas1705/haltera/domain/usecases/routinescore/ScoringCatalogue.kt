package es.sebas1705.haltera.domain.usecases.routinescore

import es.sebas1705.haltera.data.models.Exercise
import es.sebas1705.haltera.data.models.Routine
import es.sebas1705.haltera.data.models.RoutineDay
import es.sebas1705.haltera.data.models.RoutineSlot
import es.sebas1705.haltera.data.models.RoutineTemplate
import es.sebas1705.haltera.data.models.SlotTarget

/**
 * The catalogue information the scorer needs (category, primary and secondary muscles) for the exercises a
 * routine uses. Exercises should come with their variants so fixed targets resolve; a fixed target whose
 * variant is unknown falls back to the exercise id before the dot (catalogue ids are `<exercise>.<equipment>`).
 */
class ScoringCatalogue(exercises: Collection<Exercise>) {
    private val byId: Map<String, Exercise> = exercises.associateBy { it.id }
    private val byVariant: Map<String, Exercise> =
        exercises.flatMap { exercise -> exercise.variants.map { it.id to exercise } }.toMap()

    fun exerciseOf(target: SlotTarget): Exercise? = when (target) {
        is SlotTarget.Fixed -> exerciseOfVariant(target.variantId)
        is SlotTarget.Flexible -> byId[target.exerciseId]
    }

    fun exerciseOfVariant(variantId: String): Exercise? = byVariant[variantId] ?: byId[variantId.substringBefore('.')]

    companion object {
        val EMPTY = ScoringCatalogue(emptyList())
    }
}

/**
 * A famous-routine template as a [Routine] the scorer can read, with day names in [language]. Ids are synthetic
 * (`template-day-0`, `template-slot-0-1`…); nothing is stored.
 */
fun RoutineTemplate.toScoringRoutine(language: String): Routine {
    val routineId = "template-$id"
    return Routine(
        id = routineId,
        name = name.get(language),
        days = days.mapIndexed { dayIndex, day ->
            val dayId = "template-day-$dayIndex"
            RoutineDay(
                id = dayId,
                routineId = routineId,
                position = dayIndex,
                name = day.name.get(language),
                kind = day.kind,
                slots = day.slots.mapIndexed { slotIndex, spec ->
                    RoutineSlot(
                        id = "template-slot-$dayIndex-$slotIndex",
                        dayId = dayId,
                        position = slotIndex,
                        target = spec.target,
                        sets = spec.sets,
                        warmupSets = spec.warmupSets,
                        prescription = spec.prescription,
                        restBetweenSetsS = spec.restBetweenSetsS,
                        restAfterS = spec.restAfterS,
                        supersetGroup = spec.supersetGroup,
                        bilateral = spec.bilateral,
                        notes = spec.notes,
                        progression = spec.progression,
                    )
                },
            )
        },
    )
}
