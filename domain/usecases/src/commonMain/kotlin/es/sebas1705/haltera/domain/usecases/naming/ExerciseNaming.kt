package es.sebas1705.haltera.domain.usecases.naming

import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.ExerciseVariant
import es.sebas1705.haltera.data.models.LocalizedText

/**
 * The part of a name that tells a variant apart from its siblings: the variant's own name ("Wide grip")
 * when it has one, else its equipment ("Barbell"), which each feature localises with its own resources.
 */
sealed interface VariantLabel {
    data class Named(val name: LocalizedText) : VariantLabel

    data class OfEquipment(val equipment: Equipment) : VariantLabel

    /** The label in [language]; [equipmentText] localises the equipment. */
    fun text(language: String, equipmentText: (Equipment) -> String): String = when (this) {
        is Named -> name.get(language)
        is OfEquipment -> equipmentText(equipment)
    }
}

/**
 * How exercises and variants are named everywhere: the **title** is the exercise name ("Lat Pulldown"), the
 * **subtitle** is the [VariantLabel] ("Wide grip", or "Cable" for a variant without a name), and variant
 * pickers list the variant labels. A variant name never replaces the exercise name.
 */
object ExerciseNaming {

    fun variantLabel(name: LocalizedText?, equipment: Equipment): VariantLabel =
        name?.let { VariantLabel.Named(it) } ?: VariantLabel.OfEquipment(equipment)

    fun variantLabel(variant: ExerciseVariant): VariantLabel = variantLabel(variant.name, variant.equipment)

    /**
     * The title in [language]: the exercise name. Only when the exercise is unknown (a broken reference) does
     * it fall back to the variant name, so the row is never blank; null when neither is known.
     */
    fun title(exercise: LocalizedText?, variantName: LocalizedText?, language: String): String? =
        (exercise ?: variantName)?.get(language)
}
