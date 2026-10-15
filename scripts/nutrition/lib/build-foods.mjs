// Pure foods builder: USDA snapshot + curation -> foods.json content (nutrition design §2).
import { createHash } from 'node:crypto';

export const UNITS = ['G', 'ML'];
const NUTRIENT_DIGITS = { protein: 2, carbs: 2, fat: 2, fiber: 2, sugar: 2 };

/** Locale-independent ordering by code unit, so the output and its hash are the same on every machine. */
const byId = (a, b) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0);

const round = (value, digits) => {
  const f = 10 ** digits;
  return Math.round(value * f) / f;
};

/** USDA values can be slightly negative (by-difference carbs); a food has no negative nutrients. */
const clamp = (value) => (value == null ? null : Math.max(0, value));

/** Salt in g per 100 g from sodium in mg per 100 g (salt = sodium x 2.5, EU labelling). */
export const saltFromSodium = (sodiumMg) => (sodiumMg == null ? null : round((Math.max(0, sodiumMg) * 2.5) / 1000, 2));

export const foodId = (fdc) => `usda-${fdc}`;

/**
 * Builds the bundled foods.
 * - `usda`: the reduced snapshot (sources/usda-foods.json), `selection`: foods.yaml, `es`: i18n/es.yaml,
 *   `lockedIds`: every id emitted before (ids.lock.json).
 * Returns `{ foods, missingEs, lockedIds }`; `foods` is the foods.json object. Throws on unknown ids or kinds.
 */
export function buildFoods({ usda, selection, es, lockedIds }) {
  const byFdc = new Map(usda.map((f) => [f.fdcId, f]));
  const kinds = selection.servingKinds ?? {};
  const missingEs = [];
  const seen = new Set();
  const foods = [];
  for (const [index, entry] of (selection.foods ?? []).entries()) {
    const where = `foods[${index}] (fdc ${entry?.fdc})`;
    const source = byFdc.get(entry?.fdc);
    if (!source) throw new Error(`${where}: not in the USDA snapshot`);
    if (seen.has(entry.fdc)) throw new Error(`${where}: listed twice`);
    seen.add(entry.fdc);
    const nameEs = es[entry.fdc];
    if (typeof nameEs !== 'string' || nameEs.trim() === '') missingEs.push(entry.fdc);
    const unit = entry.unit ?? 'G';
    if (!UNITS.includes(unit)) throw new Error(`${where}: unit must be one of ${UNITS.join(', ')}`);
    let serving = null;
    if (entry.serving != null) {
      const [kind, grams] = entry.serving;
      if (!kinds[kind]) throw new Error(`${where}: unknown serving kind "${kind}"`);
      serving = { name: { en: kinds[kind].en, es: kinds[kind].es }, grams };
    }
    const food = {
      id: foodId(entry.fdc),
      name: { en: entry.en, es: typeof nameEs === 'string' ? nameEs.trim() : '' },
      unit,
      kcal: source.kcal == null ? null : Math.round(clamp(source.kcal)),
    };
    for (const [key, digits] of Object.entries(NUTRIENT_DIGITS)) {
      const value = clamp(source[key]);
      food[key] = value == null ? null : round(value, digits);
    }
    food.salt = saltFromSodium(source.sodium);
    food.serving = serving;
    foods.push(food);
  }
  foods.sort(byId);

  const current = new Set(foods.map((f) => f.id));
  const allIds = [...new Set([...lockedIds, ...current])].sort();
  const deprecatedIds = allIds.filter((id) => !current.has(id));
  const contentHash = createHash('sha256').update(JSON.stringify({ foods, deprecatedIds })).digest('hex').slice(0, 16);
  return {
    foods: { foodsVersion: selection.foodsVersion, contentHash, deprecatedIds, foods },
    missingEs,
    lockedIds: allIds,
  };
}
