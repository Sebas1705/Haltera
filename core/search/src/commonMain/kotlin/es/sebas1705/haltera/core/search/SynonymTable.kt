package es.sebas1705.haltera.core.search

/**
 * Groups of equivalent phrases ("peso muerto" = "deadlift"). When a name contains one phrase of a group,
 * the index also files the name under the terms of every other phrase of the group, so a query in either
 * language or with either word finds it. Phrases go through [TextNormalizer.terms], so accents, case,
 * plurals and stopwords do not matter ("press de banca" is "press banca").
 */
class SynonymTable(groups: List<List<String>>) {

    private class Phrase(val terms: List<String>, val group: Int)

    private val groups: List<List<List<String>>> = groups.map { group ->
        group.map(TextNormalizer::terms).filter { it.isNotEmpty() }.distinct()
    }

    /** Phrases by their first term, longest first. */
    private val byFirstTerm: Map<String, List<Phrase>> = this.groups
        .flatMapIndexed { index, group -> group.map { Phrase(it, index) } }
        .groupBy { it.terms.first() }
        .mapValues { (_, phrases) -> phrases.sortedByDescending { it.terms.size } }

    val size: Int get() = groups.size

    /**
     * Terms to add to a name made of [terms] (already normalised): for every phrase of the table found in
     * [terms] as consecutive terms, the terms of the other phrases of its group. Terms already in [terms]
     * are not repeated.
     */
    fun expand(terms: List<String>): List<String> {
        if (terms.isEmpty() || byFirstTerm.isEmpty()) return emptyList()
        val found = terms.indices.flatMap { start ->
            byFirstTerm[terms[start]].orEmpty().filter { terms.matchesAt(start, it.terms) }
        }
        val own = terms.toSet()
        return found
            .flatMap { phrase -> groups[phrase.group].filter { it != phrase.terms }.flatten() }
            .filter { it !in own }
            .distinct()
    }

    /** Every term group as normalised phrases (for documentation and tests). */
    fun groups(): List<List<String>> = groups.map { group -> group.map { it.joinToString(" ") } }

    private fun List<String>.matchesAt(start: Int, phrase: List<String>): Boolean {
        if (start + phrase.size > size) return false
        for (i in phrase.indices) if (this[start + i] != phrase[i]) return false
        return true
    }

    companion object {
        val EMPTY = SynonymTable(emptyList())
    }
}
