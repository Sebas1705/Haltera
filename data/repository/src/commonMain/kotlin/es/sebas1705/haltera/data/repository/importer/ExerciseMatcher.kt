package es.sebas1705.haltera.data.repository.importer

import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.Exercise
import es.sebas1705.haltera.data.models.ExerciseSource
import es.sebas1705.haltera.data.models.ExerciseVariant

/** The variant of an existing exercise that a name from a file stands for. */
data class ExerciseMatch(val exerciseId: String, val variantId: String)

/**
 * Finds the exercise a name from a file refers to, among the catalogue and the user's own exercises.
 *
 * Names are compared accent-insensitively, in lower case and with punctuation ignored, against both languages,
 * then against the curated [ImportAliases] (default names of Hevy, Strong and FitNotes that are not catalogue names).
 * Third-party apps append the equipment (`Bench Press (Barbell)`, `Barbell Bench Press`, `Remo con barra`); it is
 * split off and picks the variant of the exercise. A name that cannot be matched with confidence is left unmatched:
 * the user decides, nothing is guessed (the preview may only suggest, see [ExerciseSuggester]). The user's own
 * exercises win over the catalogue and the aliases when they have the name.
 */
class ExerciseMatcher(exercises: List<Exercise>) {
    private val byName = HashMap<String, Exercise>()
    private val byId: Map<String, Exercise> = exercises.filter { !it.deprecated }.associateBy { it.id }
    private val byVariantId: Map<String, Pair<Exercise, ExerciseVariant>> = byId.values
        .flatMap { e -> e.variants.filter { !it.deprecated }.map { v -> v.id to (e to v) } }
        .toMap()

    init {
        // Catalogue first, then the user's own exercises, which replace a catalogue exercise of the same name.
        val ordered = exercises.filter { !it.deprecated }.sortedBy { if (it.source == ExerciseSource.USER) 1 else 0 }
        for (exercise in ordered) {
            for (name in listOf(exercise.name.en, exercise.name.es)) {
                val key = ImportValues.matchKey(name)
                if (key.isNotEmpty()) byName[key] = exercise
            }
        }
    }

    fun match(name: String, equipment: Equipment? = null): ExerciseMatch? {
        val full = ImportValues.matchKey(name)
        if (full.isEmpty()) return null
        return byName[full]?.let { exercise -> pick(exercise, equipment) }
            ?: alias(full, equipment, requireHint = false)
            ?: splitEquipment(name).firstNotNullOfOrNull { (base, hint) ->
                matchBase(ImportValues.matchKey(base), hint ?: equipment, requireHint = hint != null)
            }
    }

    /** A name without its equipment: a catalogue or user name first, else an alias (never both). */
    private fun matchBase(key: String, hint: Equipment?, requireHint: Boolean): ExerciseMatch? {
        val exercise = byName[key] ?: return alias(key, hint, requireHint)
        return pick(exercise, hint, requireHint)
    }

    /**
     * The [ImportAliases] target of the normalised name [key]. A variant target is used as it is, unless [hint] names
     * other equipment (then nothing: not guessed); an exercise target picks its variant like a catalogue name.
     * Targets missing on this device (an older catalogue) or deprecated give nothing.
     */
    private fun alias(key: String, hint: Equipment?, requireHint: Boolean): ExerciseMatch? {
        val target = ImportAliases.byKey[key] ?: return null
        if ('.' !in target) return byId[target]?.let { pick(it, hint, requireHint) }
        val (exercise, variant) = byVariantId[target] ?: return null
        return ExerciseMatch(
            exercise.id,
            variant.id
        ).takeIf { !requireHint || hint == null || variant.equipment == hint }
    }

    /**
     * Variants of [exercise] usable now. With a [hint] the variant with that equipment; without one the first.
     * A [requireHint] that no variant satisfies yields null (the name names equipment this exercise lacks).
     */
    private fun pick(exercise: Exercise, hint: Equipment?, requireHint: Boolean = false): ExerciseMatch? {
        val variants = exercise.variants.filter { !it.deprecated }.sortedBy { it.position }
        val chosen: ExerciseVariant? = when {
            hint != null -> variants.firstOrNull { it.equipment == hint }
                ?: if (requireHint) null else variants.firstOrNull()
            else -> variants.firstOrNull()
        }
        return chosen?.let { ExerciseMatch(exercise.id, it.id) }
    }

    companion object {
        private const val MAX_EQUIPMENT_WORDS = 2

        /** Words that join a name and its equipment: `Remo con barra`, `Curl with dumbbells`, `Press en multipower`. */
        private val JOINERS = setOf("con", "with", "en", "on", "using")
        private val ARTICLES = setOf("la", "el", "las", "los", "una", "un", "the", "a")

        /** The equipment a name from a file mentions (`Squat (Barbell)`, `Dumbbell Curl`), or null. */
        fun equipmentIn(name: String): Equipment? = splitEquipment(name).firstNotNullOfOrNull { it.second }

        /** The variant of [exercise] for [hint]: the one with that equipment, else the first. Null without variants. */
        fun variantFor(exercise: Exercise, hint: Equipment?): ExerciseVariant? {
            val variants = exercise.variants.filter { !it.deprecated }.sortedBy { it.position }
            return variants.firstOrNull { hint != null && it.equipment == hint } ?: variants.firstOrNull()
        }

        /**
         * Candidate (name without equipment, equipment) pairs: a trailing `(Equipment)`, a leading equipment word, or
         * a trailing `con barra` / `with dumbbells`.
         */
        private fun splitEquipment(name: String): List<Pair<String, Equipment?>> {
            val out = ArrayList<Pair<String, Equipment?>>(2)
            val trimmed = name.trim()
            val open = trimmed.lastIndexOf('(')
            if (open > 0 && trimmed.endsWith(")")) {
                val hint = ImportValues.equipmentOf(trimmed.substring(open + 1, trimmed.length - 1))
                if (hint != null) out += trimmed.substring(0, open).trim() to hint
            }
            val words = ImportValues.matchKey(trimmed).split(' ')
            val leading = (MAX_EQUIPMENT_WORDS downTo 1).firstNotNullOfOrNull { take ->
                val hint = if (words.size > take) ImportValues.equipmentOf(words.take(take).joinToString(" ")) else null
                hint?.let { words.drop(take).joinToString(" ") to it }
            }
            if (leading != null) out += leading
            trailingJoined(words)?.let { out += it }
            return out
        }

        /** `<name> con|with|en <equipment>` (an article may follow the joiner): the name and the equipment. */
        private fun trailingJoined(words: List<String>): Pair<String, Equipment>? =
            (MAX_EQUIPMENT_WORDS downTo 1).firstNotNullOfOrNull { take ->
                if (words.size < take + 2) return@firstNotNullOfOrNull null
                val hint = ImportValues.equipmentOf(words.takeLast(take).joinToString(" ")) ?: return@firstNotNullOfOrNull null
                var joiner = words.size - take - 1
                if (words[joiner] in ARTICLES && joiner > 1) joiner--
                if (words[joiner] !in JOINERS) return@firstNotNullOfOrNull null
                words.take(joiner).joinToString(" ").takeIf { it.isNotEmpty() }?.let { it to hint }
            }
    }
}
