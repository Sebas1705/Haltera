package es.sebas1705.haltera.data.repository.sql

import es.sebas1705.haltera.core.search.FieldKind
import es.sebas1705.haltera.core.search.SearchDocument
import es.sebas1705.haltera.core.search.SearchField
import es.sebas1705.haltera.core.search.SearchIndex
import es.sebas1705.haltera.data.models.Food
import es.sebas1705.haltera.data.models.FoodSource

/**
 * Fuzzy food search (docs/designs/2026-10-16-fuzzy-search-design.md) over every non-deprecated food of one
 * app language: names in both languages and the brand, favourites and the user's own foods boosted.
 * No synonym table: food names are already bilingual.
 */
internal class FoodSearch(foods: List<Food>, language: String) {
    private val foods: List<Food> = foods.sortedWith(
        compareBy({
            it.name(language).length
        }, { it.name(language) }, { it.id })
    )
    private val index: SearchIndex<Int> = SearchIndex.build(
        this.foods.mapIndexed { i, food ->
            val other = if (language == "es") "en" else "es"
            SearchDocument(
                i,
                listOfNotNull(
                    SearchField(food.name(language), NAME, FieldKind.NAME),
                    SearchField(food.name(other), OTHER_NAME, FieldKind.NAME),
                    food.brand?.let { SearchField(it, BRAND) },
                ),
            )
        },
    )

    fun search(query: String, limit: Int): List<Food> =
        index.search(query, limit, boost = { i -> boost(foods[i]) }).map { foods[it.key] }

    companion object {
        const val NAME = 1.0
        const val OTHER_NAME = 0.9
        const val BRAND = 0.6

        /** Favourites outrank a name-prefix match; own foods and recipes get a nudge. */
        const val FAVOURITE_BOOST = 0.3
        const val OWN_BOOST = 0.05

        fun boost(food: Food): Double =
            (if (food.favourite) FAVOURITE_BOOST else 0.0) +
                (if (food.source == FoodSource.USER || food.source == FoodSource.RECIPE) OWN_BOOST else 0.0)
    }
}
