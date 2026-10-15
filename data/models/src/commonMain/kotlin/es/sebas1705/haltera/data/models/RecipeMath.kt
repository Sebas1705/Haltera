package es.sebas1705.haltera.data.models

/** Per-100 g values of a recipe (nutrition design §2). */
object RecipeMath {
    /**
     * The recipe's nutrients per 100 g: the sum of the ingredients divided by the recipe weight, which is
     * [yieldGrams] (the cooked weight, when the user weighed it) or else the sum of the ingredient amounts.
     * Ingredients without a food (not on this device) count as weight without nutrients. Zero weight gives zero.
     */
    fun per100(items: List<Pair<Food?, Double>>, yieldGrams: Double?): Nutrients {
        val total = items.fold(
            Nutrients.ZERO
        ) { sum, (food, grams) -> sum + (food?.nutrientsFor(grams) ?: Nutrients.ZERO) }
        val weight = yieldGrams?.takeIf { it > 0 } ?: items.sumOf { it.second }
        return if (weight <= 0) Nutrients.ZERO else total * (Food.PER / weight)
    }
}
