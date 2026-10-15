// foods.json validation. Returns human-readable errors; empty means valid. Malformed entries are reported,
// never thrown, so one bad curation line does not hide the others.
import { UNITS } from './build-foods.mjs';

const ID = /^usda-\d+$/;
const MAX_KCAL = 902; // pure fat
const isText = (value) => typeof value === 'string' && value.trim() !== '';
const isAmount = (value) => typeof value === 'number' && Number.isFinite(value) && value >= 0;

export function validateFoods(data) {
  const errors = [];
  if (!Number.isInteger(data?.foodsVersion) || data.foodsVersion < 1) errors.push('foodsVersion must be a positive integer');
  if (!Array.isArray(data?.foods) || data.foods.length === 0) {
    errors.push('foods must be a non-empty list');
    return errors;
  }
  const ids = new Set();
  for (const food of data.foods) {
    const where = food?.id ?? '(no id)';
    if (!ID.test(food?.id ?? '')) errors.push(`${where}: id must look like usda-<fdcId>`);
    if (ids.has(food?.id)) errors.push(`${where}: duplicate id`);
    ids.add(food?.id);
    if (!isText(food?.name?.en)) errors.push(`${where}: missing English name`);
    if (!isText(food?.name?.es)) errors.push(`${where}: missing Spanish name (nutrition/i18n/es.yaml)`);
    if (!UNITS.includes(food?.unit)) errors.push(`${where}: unit must be one of ${UNITS.join(', ')}`);
    for (const key of ['kcal', 'protein', 'carbs', 'fat']) {
      if (!isAmount(food?.[key])) errors.push(`${where}: ${key} must be a number >= 0`);
    }
    for (const key of ['fiber', 'sugar', 'salt']) {
      if (food?.[key] != null && !isAmount(food[key])) errors.push(`${where}: ${key} must be null or a number >= 0`);
    }
    if (isAmount(food?.kcal) && food.kcal > MAX_KCAL) errors.push(`${where}: ${food.kcal} kcal per 100 g is impossible`);
    const macros = (food?.protein ?? 0) + (food?.carbs ?? 0) + (food?.fat ?? 0);
    if (macros > 101) errors.push(`${where}: protein + carbs + fat = ${macros} g per 100 g`);
    if (food?.serving != null) {
      if (!isText(food.serving?.name?.en) || !isText(food.serving?.name?.es)) errors.push(`${where}: serving needs en and es names`);
      if (!(typeof food.serving?.grams === 'number' && food.serving.grams > 0)) errors.push(`${where}: serving grams must be > 0`);
    }
  }
  for (const id of data.deprecatedIds ?? []) {
    if (ids.has(id)) errors.push(`${id}: both live and deprecated`);
  }
  return errors;
}
