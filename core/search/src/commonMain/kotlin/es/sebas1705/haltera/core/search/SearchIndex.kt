package es.sebas1705.haltera.core.search

import kotlin.math.max

/** How a field takes part in matching (docs/designs/2026-10-16-fuzzy-search-design.md). */
enum class FieldKind {
    /** A display name: counts for exact-name, name-prefix and name-coverage bonuses; expanded with synonyms. */
    NAME,

    /** Another name (a variant label, an alias): expanded with synonyms, no name bonuses. */
    ALIAS,

    /** Plain keywords (muscle, equipment, category words): matched as they are. */
    KEYWORD,
}

/** One searchable text of a document with its relative [weight] (1.0 = the name in the app language). */
class SearchField(val text: String, val weight: Double, val kind: FieldKind = FieldKind.KEYWORD)

/** Something that can be found: its [key] and its searchable [fields]. */
class SearchDocument<K>(val key: K, val fields: List<SearchField>)

/** A ranked result. */
data class SearchHit<K>(val key: K, val score: Double)

/**
 * In-memory fuzzy search over a fixed set of documents, built once ([build]) and queried on every keystroke.
 *
 * Matching is per query term and order-independent: each term is compared with every term of the
 * vocabulary once (exact, prefix, Damerau–Levenshtein within [EditDistance.allowedTypos]), then each
 * document takes, per query term, its best `quality × field weight`. The score of a document is
 *
 * ```
 * average term score (0..1)
 * + ALL_TERMS when every query term matched
 * + EXACT_NAME × name weight when the query is a name (EXACT_NAME_ANY_ORDER when only the words agree)
 *   or NAME_PREFIX × name weight when a name starts with the query
 * + NAME_COVERAGE × name weight × share of the name's words the query matched
 * + boost(key) (usage, custom…; the caller keeps it small)
 * ```
 *
 * Documents need every query term matched (one may be missing from three terms up) and a score of at
 * least [SearchWeights.MIN_SCORE]. Ties keep the order of the documents given to [build], so results
 * are stable.
 */
class SearchIndex<K> private constructor(
    private val keys: List<K>,
    private val vocabulary: List<String>,
    private val docTerms: Array<IntArray>,
    private val docWeights: Array<DoubleArray>,
    private val docNames: Array<List<NameInfo>>,
) {
    private class NameInfo(
        val terms: IntArray,
        /** Per name word, the unstemmed form (same id when stemming changed nothing), so typos count for coverage. */
        val rawTerms: IntArray,
        /** The stemmed words without stopwords, space separated. */
        val key: String,
        /** The folded text with its stopwords, for prefix matches while typing ("press d" → "Press de banca"). */
        val folded: String,
        val weight: Double,
    ) {
        val compact = key.replace(" ", "")
        val sorted = key.split(' ').sorted().joinToString(" ")
        val stopwords = folded.split(' ').filter { it in TextNormalizer.STOPWORDS }
    }

    val size: Int get() = keys.size

    /** Number of distinct indexed terms (for diagnostics and tests). */
    val vocabularySize: Int get() = vocabulary.size

    /**
     * Documents matching [query], best first, at most [limit]. [accept] filters documents out before they
     * are scored; [boost] adds a small per-document bonus. A blank query matches nothing.
     */
    fun search(
        query: String,
        limit: Int = Int.MAX_VALUE,
        accept: (K) -> Boolean = { true },
        boost: (K) -> Double = { 0.0 },
    ): List<SearchHit<K>> {
        val terms = TextNormalizer.terms(query)
        if (terms.isEmpty() || limit <= 0) return emptyList()
        val typing = !query.last().isWhitespace()
        val match = QueryMatch(
            qualities = terms.mapIndexed { i, term ->
                qualities(
                    term,
                    prefix = term.length >= 2 || (typing && i == terms.lastIndex)
                )
            },
            joined = (0 until terms.size - 1).map { i ->
                qualities(terms[i] + terms[i + 1], prefix = typing && i + 1 == terms.lastIndex)
            },
            keys = QueryKeys(terms, TextNormalizer.fold(query), typing),
        )
        return keys.indices.asSequence()
            .filter { accept(keys[it]) }
            .mapNotNull { doc -> score(doc, match)?.let { Scored(doc, it + boost(keys[doc])) } }
            .filter { it.score >= SearchWeights.MIN_SCORE }
            .sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.doc })
            .take(limit)
            .map { SearchHit(keys[it.doc], it.score) }
            .toList()
    }

    private class Scored(val doc: Int, val score: Double)

    /** A query prepared once per search: term qualities against the vocabulary and its normalised forms. */
    private class QueryMatch(val qualities: List<DoubleArray>, val joined: List<DoubleArray>, val keys: QueryKeys) {
        val size: Int get() = qualities.size

        /** Best quality of any query term (or joined pair) against vocabulary term [t]. */
        fun best(t: Int): Double {
            var q = 0.0
            for (quality in qualities) q = max(q, quality[t])
            for (quality in joined) q = max(q, quality[t])
            return q
        }
    }

    private class QueryKeys(terms: List<String>, val folded: String, val typing: Boolean) {
        val key = terms.joinToString(" ")
        val compact = terms.joinToString("")
        val sorted = terms.sorted().joinToString(" ")

        /** The last word while it is being typed and could still become a stopword ("d" of "de"). */
        val partialStopword: String? = terms.last().takeIf { last ->
            typing && terms.size > 1 && TextNormalizer.STOPWORDS.any { it.length > last.length && it.startsWith(last) }
        }
    }

    /** Match quality (0..1) of [term] against every vocabulary term; 0 = no match. */
    private fun qualities(term: String, prefix: Boolean): DoubleArray {
        val out = DoubleArray(vocabulary.size)
        val typos = EditDistance.allowedTypos(term.length)
        for (i in vocabulary.indices) out[i] = SearchWeights.quality(term, vocabulary[i], prefix, typos)
        return out
    }

    /** Score of [doc] before the boost, or null when it misses too many query terms. */
    private fun score(doc: Int, match: QueryMatch): Double? {
        val best = termScores(doc, match)
        val n = match.size
        val matched = best.count { it > 0.0 }
        val required = if (n <= 2) n else n - 1
        if (matched < required) return null
        val allTerms = if (matched == n) SearchWeights.ALL_TERMS else 0.0
        val names = docNames[doc]
        val nameBonus = names.maxOfOrNull { nameBonus(it, match.keys) } ?: 0.0
        val coverageBonus = names.maxOfOrNull { coverageBonus(it, match) } ?: 0.0
        return best.sum() / n + allTerms + nameBonus + coverageBonus
    }

    /** Per query term, the document's best quality × field weight. */
    private fun termScores(doc: Int, match: QueryMatch): DoubleArray {
        val terms = docTerms[doc]
        val weights = docWeights[doc]
        val n = match.size
        val best = DoubleArray(n) { i -> bestOf(match.qualities[i], terms, weights) }
        // "pull down" meets "pulldown": a joined pair counts for both words when it matches better.
        for (i in match.joined.indices) {
            val pair = bestOf(match.joined[i], terms, weights)
            if (pair > (best[i] + best[i + 1]) / 2) {
                best[i] = max(best[i], pair)
                best[i + 1] = max(best[i + 1], pair)
            }
        }
        val partial = match.keys.partialStopword ?: return best
        val stopword = docNames[doc]
            .filter { name -> name.stopwords.any { it.startsWith(partial) } }
            .maxOfOrNull { SearchWeights.STOPWORD_PREFIX * it.weight }
        if (stopword != null) best[n - 1] = max(best[n - 1], stopword)
        return best
    }

    private fun nameBonus(name: NameInfo, query: QueryKeys): Double {
        val bonus = when {
            name.key == query.key || name.compact == query.compact -> SearchWeights.EXACT_NAME
            name.sorted == query.sorted -> SearchWeights.EXACT_NAME_ANY_ORDER
            name.key.startsWith(query.key) || (query.typing && name.folded.startsWith(query.folded)) -> SearchWeights.NAME_PREFIX
            else -> 0.0
        }
        return bonus * name.weight
    }

    /** Each name word counts with its best match quality, so a typo covers less than the exact word. */
    private fun coverageBonus(name: NameInfo, match: QueryMatch): Double {
        if (name.terms.isEmpty()) return 0.0
        val covered = name.terms.indices.sumOf { max(match.best(name.terms[it]), match.best(name.rawTerms[it])) }
        return SearchWeights.NAME_COVERAGE * name.weight * covered / name.terms.size
    }

    private fun bestOf(quality: DoubleArray, terms: IntArray, weights: DoubleArray): Double {
        var best = 0.0
        for (k in terms.indices) {
            val q = quality[terms[k]]
            if (q > 0.0) best = max(best, q * weights[k])
        }
        return best
    }

    /** Collects the vocabulary and the per-document terms while building. */
    private class Builder(private val synonyms: SynonymTable) {
        private val ids = HashMap<String, Int>()
        val vocabulary = ArrayList<String>()

        fun id(term: String): Int = ids.getOrPut(term) {
            vocabulary.add(term)
            vocabulary.size - 1
        }

        /** Terms of one document with their best weight, and its names. */
        inner class Document {
            val weights = LinkedHashMap<Int, Double>()
            val names = ArrayList<NameInfo>()

            private fun add(term: String, weight: Double) {
                val termId = id(term)
                weights[termId] = max(weights[termId] ?: 0.0, weight)
            }

            fun add(field: SearchField) {
                val terms = TextNormalizer.terms(field.text)
                terms.forEach { add(it, field.weight) }
                // The unstemmed words too, so a typo is measured against the real word ("aple" → "apple").
                TextNormalizer.words(field.text).forEach { add(it, field.weight) }
                if (field.kind == FieldKind.KEYWORD || terms.isEmpty()) return
                for (i in 0 until terms.size - 1) add(terms[i] + terms[i + 1], field.weight * SearchWeights.JOINED)
                synonyms.expand(terms).forEach { add(it, field.weight * SearchWeights.SYNONYM) }
                if (field.kind == FieldKind.NAME) names += name(field, terms)
            }

            private fun name(field: SearchField, terms: List<String>): NameInfo {
                val pairs = TextNormalizer.words(
                    field.text
                ).map { id(TextNormalizer.stem(it)) to id(it) }.distinctBy { it.first }
                return NameInfo(
                    terms = pairs.map { it.first }.toIntArray(),
                    rawTerms = pairs.map { it.second }.toIntArray(),
                    key = terms.joinToString(" "),
                    folded = TextNormalizer.fold(field.text),
                    weight = field.weight,
                )
            }
        }
    }

    companion object {
        /**
         * Builds the index of [documents]; their order is the tie-break order of results. Names and aliases
         * are expanded with [synonyms] (weight × [SearchWeights.SYNONYM]) and with their joined word pairs
         * ("pull up" → "pullup", weight × [SearchWeights.JOINED]).
         */
        fun <K> build(documents: List<SearchDocument<K>>, synonyms: SynonymTable = SynonymTable.EMPTY): SearchIndex<K> {
            val builder = Builder(synonyms)
            val built = documents.map { document -> builder.Document().apply { document.fields.forEach { add(it) } } }
            return SearchIndex(
                keys = documents.map { it.key },
                vocabulary = builder.vocabulary,
                docTerms = Array(built.size) { built[it].weights.keys.toIntArray() },
                docWeights = Array(built.size) { built[it].weights.values.toDoubleArray() },
                docNames = Array(built.size) { built[it].names },
            )
        }
    }
}

/** The constants of the scoring (docs/designs/2026-10-16-fuzzy-search-design.md). */
object SearchWeights {
    /** Query is a name, same words in the same order (stopwords and punctuation ignored). */
    const val EXACT_NAME = 2.0

    /** Query has the words of a name in another order. */
    const val EXACT_NAME_ANY_ORDER = 1.7

    /** Every query term matched something. */
    const val ALL_TERMS = 0.5

    /** A name starts with the query (typing). */
    const val NAME_PREFIX = 0.25

    /** Times the share of a name's words the query matched: "Press inclinado" beats "Press inclinado con agarre martillo". */
    const val NAME_COVERAGE = 0.4

    /** Weight factor of terms added by the synonym table. */
    const val SYNONYM = 0.9

    /** Weight factor of joined word pairs ("pullup" from "pull up"). */
    const val JOINED = 0.95

    /** Quality of a word being typed that is the start of a stopword in the name ("press d" → "Press de banca"). */
    const val STOPWORD_PREFIX = 0.6

    /** Results below this score are junk. */
    const val MIN_SCORE = 0.25

    /** Same term. */
    const val EXACT = 1.0

    /** Term is a prefix of the word: PREFIX_BASE + PREFIX_SPAN × typed share of the word (always below EXACT). */
    const val PREFIX_BASE = 0.55
    const val PREFIX_SPAN = 0.35

    /** Typo: FUZZY × (1 − distance / longer length). */
    const val FUZZY = 0.8

    /** Typo in a word being typed: the query term is within one edit of the start of the word. */
    const val FUZZY_PREFIX = 0.45

    /** Shortest term that is matched with a typo inside a prefix. */
    private const val FUZZY_PREFIX_MIN = 4

    /** Match quality of query [term] against an indexed [word]: 0 (none) to [EXACT]. */
    fun quality(term: String, word: String, prefix: Boolean, typos: Int): Double {
        if (term == word) return EXACT
        if (prefix && word.length > term.length && word.startsWith(term)) {
            return PREFIX_BASE + PREFIX_SPAN * term.length / word.length
        }
        if (typos == 0) return 0.0
        val distance = EditDistance.distance(term, word, typos)
        val fuzzy = if (distance <= typos) FUZZY * (1.0 - distance.toDouble() / max(term.length, word.length)) else 0.0
        return if (prefix && typoInPrefix(term, word)) max(fuzzy, FUZZY_PREFIX) else fuzzy
    }

    /** [term] is within one edit of the start of the longer [word], with the same first letter. */
    private fun typoInPrefix(term: String, word: String): Boolean {
        if (term.length < FUZZY_PREFIX_MIN || word.length <= term.length || word[0] != term[0]) return false
        return EditDistance.distance(term, word.substring(0, term.length), 1) <= 1
    }
}
