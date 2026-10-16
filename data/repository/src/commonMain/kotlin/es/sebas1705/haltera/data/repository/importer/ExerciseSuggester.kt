package es.sebas1705.haltera.data.repository.importer

import es.sebas1705.haltera.core.search.Antonyms
import es.sebas1705.haltera.core.search.EditDistance
import es.sebas1705.haltera.core.search.GymSynonyms
import es.sebas1705.haltera.core.search.TextNormalizer
import es.sebas1705.haltera.data.models.Exercise
import es.sebas1705.haltera.data.models.ExerciseFilter
import es.sebas1705.haltera.data.models.LocalizedText
import es.sebas1705.haltera.data.repository.sql.ExerciseSearch

/** How sure a suggestion for an unmatched name is (docs/IMPORT-FORMATS.md, "Suggestions"). */
enum class SuggestionConfidence {
    /** Same words as the exercise (order, plurals, a typo or a synonym aside): pre-selected in the preview. */
    HIGH,

    /** Most words agree: shown, never selected for the user. */
    MEDIUM,
}

/** An existing exercise the preview proposes for a name of the file; the user confirms or changes it. */
data class ExerciseSuggestion(val exerciseId: String, val name: LocalizedText, val confidence: SuggestionConfidence)

/**
 * Proposes an existing exercise for a name the [ExerciseMatcher] could not match, with the fuzzy engine of core:search
 * ([ExerciseSearch], the same ranking as every search of the app) and a conservative confidence on top of it:
 *
 * 1. The best search hit for the name is the candidate (a tie for first place between two exercises gives nothing).
 * 2. Its names (both languages) are compared word by word with the name, equipment words aside: a word agrees when it
 *    is the same after stemming, a synonym (GymSynonyms), or within the search's typo allowance and not its opposite
 *    (Antonyms: incline/decline, pulldown/pushdown...).
 * 3. A word of a "kind" group (incline/decline/flat, seated/standing/lying, front/rear/lateral, push/pull...) on one
 *    side that the other contradicts rules the candidate out.
 * 4. HIGH when every word agrees both ways (and the equipment named, if any, is a variant of it); MEDIUM when at least
 *    two thirds agree both ways; nothing otherwise.
 *
 * A suggestion is never applied silently: HIGH is pre-selected but shown with its confidence; MEDIUM waits for a tap.
 */
internal class ExerciseSuggester(exercises: List<Exercise>) {
    private val active = exercises.filter { !it.deprecated }
    private val search = ExerciseSearch(active, "en")

    fun suggest(name: String): ExerciseSuggestion? {
        val query = significant(name)
        val candidate = candidate(name)
        if (query.isEmpty() || candidate == null) return null
        val names = listOf(candidate.name.en, candidate.name.es).map(::significant)
        // A contradiction with any of its names rules it out ("Front Delt Fly" is not "Rear Delt Fly" / "Pájaros").
        if (names.any { conflicts(query, it) }) return null
        val best = names.mapNotNull { confidence(query, it) }.minByOrNull { it.ordinal } ?: return null
        val hint = ExerciseMatcher.equipmentIn(name)
        // Named equipment the exercise lacks: the import would use another variant, so never pre-selected.
        val lacksEquipment = hint != null && hint !in candidate.equipment()
        val confidence = if (best == SuggestionConfidence.HIGH && lacksEquipment) SuggestionConfidence.MEDIUM else best
        return ExerciseSuggestion(candidate.id, candidate.name, confidence)
    }

    /** The best search hit for [name], or null when there is none or two exercises tie for first place. */
    private fun candidate(name: String): Exercise? {
        val hits = search.hits(ExerciseFilter(query = name), limit = 2, usage = emptyMap(), now = 0L)
        val top = hits.firstOrNull() ?: return null
        val tie = hits.size > 1 && hits[1].second >= top.second && hits[1].first.id != top.first.id
        return top.first.takeUnless { tie }
    }

    private fun Exercise.equipment() = variants.filter { !it.deprecated }.map { it.equipment }.toSet()

    private fun confidence(query: List<Word>, name: List<Word>): SuggestionConfidence? {
        if (name.isEmpty() || conflicts(query, name)) return null
        // "Benchpress" and "Bench Press": the same letters.
        if (query.joinToString("") { it.raw } == name.joinToString("") { it.raw }) return SuggestionConfidence.HIGH
        val queryShare = query.count { q -> name.any { agree(q, it) } }.toDouble() / query.size
        val nameShare = name.count { n -> query.any { agree(n, it) } }.toDouble() / name.size
        return when {
            queryShare >= 1.0 && nameShare >= 1.0 -> SuggestionConfidence.HIGH
            queryShare >= MEDIUM_SHARE && nameShare >= MEDIUM_SHARE -> SuggestionConfidence.MEDIUM
            else -> null
        }
    }

    private fun agree(a: Word, b: Word): Boolean {
        if (a.stem == b.stem) return true
        if (Antonyms.opposite(a.raw, b.raw) || Antonyms.opposite(a.stem, b.stem)) return false
        if (b.stem in synonymsOf(a.stem) || a.stem in synonymsOf(b.stem)) return true
        // Typos are measured on the words as written ("pres" is one letter from "press", its stem is not).
        val typos = minOf(EditDistance.allowedTypos(a.raw.length), EditDistance.allowedTypos(b.raw.length))
        return typos > 0 && EditDistance.distance(a.raw, b.raw, typos) <= typos
    }

    private val synonymCache = HashMap<String, Set<String>>()

    private fun synonymsOf(term: String): Set<String> =
        synonymCache.getOrPut(term) { GymSynonyms.table.expand(listOf(term)).toSet() }

    /** A word of a kind group on each side, from different members: "incline" against "decline", "seated" against "lying". */
    private fun conflicts(query: List<Word>, name: List<Word>): Boolean = KIND_GROUPS.any { group ->
        val q = query.mapNotNull { group[it.stem] }.toSet()
        val n = name.mapNotNull { group[it.stem] }.toSet()
        q.isNotEmpty() && n.isNotEmpty() && q != n
    } || query.any { q -> name.any { Antonyms.opposite(q.raw, it.raw) } }

    /** A word as written (folded) and its stem. */
    data class Word(val raw: String, val stem: String)

    companion object {
        private const val MEDIUM_SHARE = 2.0 / 3.0

        /** Equipment words: they pick the variant, not the exercise. */
        private val EQUIPMENT_WORDS = setOf(
            "barbell", "bb", "barra", "dumbbell", "db", "mancuerna", "kettlebell", "kb", "smith", "multipower",
            "machine", "maquina", "cable", "pulley", "polea", "band", "goma", "banda", "bodyweight", "ez", "bar",
            "plate", "loaded", "lever", "leverage", "weighted", "assisted",
        ).map(TextNormalizer::stem).toSet()

        /**
         * Words that tell exercises of the same movement apart; each group maps a word form to its member. Members of
         * one group on the two sides that differ are a contradiction.
         */
        private val KIND_GROUPS: List<Map<String, String>> = listOf(
            mapOf(
                "incline" to "incline", "inclined" to "incline", "inclinado" to "incline", "inclinada" to "incline",
                "decline" to "decline", "declined" to "decline", "declinado" to "decline", "declinada" to "decline",
                "flat" to "flat", "plano" to "flat",
            ),
            mapOf(
                "seated" to "seated",
                "sentado" to "seated",
                "sitting" to "seated",
                "standing" to "standing",
                "pie" to "standing",
                "lying" to "lying",
                "tumbado" to "lying",
                "prone" to "lying",
            ),
            mapOf(
                "front" to "front",
                "frontal" to "front",
                "rear" to "rear",
                "posterior" to "rear",
                "reverse" to "rear",
                "lateral" to "lateral",
                "side" to "lateral",
            ),
            mapOf(
                "pulldown" to "pull",
                "pull" to "pull",
                "pullup" to "pull",
                "jalon" to "pull",
                "pushdown" to "push",
                "push" to "push",
                "pushup" to "push",
                "pressdown" to "push",
            ),
            mapOf("close" to "close", "cerrado" to "close", "narrow" to "close", "wide" to "wide", "abierto" to "wide"),
            mapOf("curl" to "curl", "extension" to "extension", "extensión" to "extension", "press" to "press"),
            mapOf(
                "row" to "row",
                "remo" to "row",
                "fly" to "fly",
                "apertura" to "fly",
                "raise" to "raise",
                "elevacion" to "raise"
            ),
        ).map { group -> group.entries.associate { (word, member) -> TextNormalizer.stem(TextNormalizer.fold(word)) to member } }

        /** The words that name the movement, with their stems: stopwords and equipment words removed. */
        fun significant(text: String): List<Word> =
            TextNormalizer.words(text).map { Word(it, TextNormalizer.stem(it)) }
                .filter { it.stem !in EQUIPMENT_WORDS }
                .distinctBy { it.stem }
    }
}
