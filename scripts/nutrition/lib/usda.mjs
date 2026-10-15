// Reduces USDA FoodData Central foods (Foundation, SR Legacy JSON) to what the pipeline needs:
// id, description, category and the nutrients per 100 g Ironlog keeps (nutrition design §2).

// Nutrient numbers (USDA `nutrient.number`), in order of preference for each value.
const NUTRIENTS = {
  kcal: ['208', '958', '957'], // Energy in kcal; Foundation foods often carry only the Atwater energies
  protein: ['203'],
  fat: ['204', '298'], // Total lipid (fat); Total fat (NLEA)
  carbs: ['205', '205.2'], // Carbohydrate by difference; by summation
  fiber: ['291'],
  sugar: ['269', '269.3'], // Sugars, total (incl. NLEA)
  sodium: ['307'], // mg
};

const ENERGY = new Set(['208', '957', '958']);

const round = (value, digits) => {
  const f = 10 ** digits;
  return Math.round(value * f) / f;
};

/** Amount of the first nutrient number of [numbers] present in [food], or null. Energy only from kcal rows. */
function pick(food, numbers) {
  for (const number of numbers) {
    const row = (food.foodNutrients ?? []).find(
      (n) =>
        n?.nutrient?.number === number &&
        typeof n.amount === 'number' &&
        (!ENERGY.has(number) || n.nutrient.unitName === 'kcal'),
    );
    if (row) return row.amount;
  }
  return null;
}

const DIGITS = { kcal: 0, protein: 2, fat: 2, carbs: 2, fiber: 2, sugar: 2, sodium: 1 };

/** One USDA food, reduced. Values are per 100 g; sodium in mg; a missing value is null. */
export function reduceFood(food, dataType) {
  const reduced = {
    fdcId: food.fdcId,
    dataType,
    description: food.description,
    category: food.foodCategory?.description ?? null,
  };
  for (const [key, numbers] of Object.entries(NUTRIENTS)) {
    const value = pick(food, numbers);
    reduced[key] = value == null ? null : round(value, DIGITS[key]);
  }
  return reduced;
}

/** Every food of a downloaded dataset JSON (`{ FoundationFoods: [...] }` or `{ SRLegacyFoods: [...] }`). */
export function reduceDataset(json) {
  const out = [];
  for (const [key, dataType] of [['FoundationFoods', 'foundation'], ['SRLegacyFoods', 'sr_legacy']]) {
    // The downloads contain a few null entries.
    for (const food of json[key] ?? []) if (food?.fdcId != null) out.push(reduceFood(food, dataType));
  }
  if (out.length === 0) throw new Error('dataset has no FoundationFoods or SRLegacyFoods');
  return out;
}
