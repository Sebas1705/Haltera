import { test } from 'node:test';
import assert from 'node:assert/strict';
import { buildFoods, saltFromSodium } from '../lib/build-foods.mjs';

const usda = [
  { fdcId: 2, description: 'Milk, whole', kcal: 61, protein: 3.15, carbs: 4.8, fat: 3.25, fiber: 0, sugar: 5.05, sodium: 43 },
  { fdcId: 1, description: 'Chicken, breast', kcal: 120, protein: 22.5, carbs: -0.4, fat: 2.62, fiber: null, sugar: null, sodium: 45 },
  { fdcId: 3, description: 'Not selected', kcal: 1, protein: 0, carbs: 0, fat: 0, fiber: 0, sugar: 0, sodium: 0 },
];
const selection = {
  foodsVersion: 3,
  servingKinds: { glass: { en: 'glass', es: 'vaso' } },
  foods: [
    { fdc: 2, en: 'Whole milk', unit: 'ML', serving: ['glass', 250] },
    { fdc: 1, en: 'Chicken breast, raw' },
  ],
};
const es = { 1: 'Pechuga de pollo, cruda', 2: 'Leche entera' };

test('keeps only the selected foods, sorted by id, with names, unit and serving', () => {
  const { foods, missingEs } = buildFoods({ usda, selection, es, lockedIds: [] });
  assert.deepEqual(missingEs, []);
  assert.equal(foods.foodsVersion, 3);
  assert.deepEqual(foods.foods.map((f) => f.id), ['usda-1', 'usda-2']);
  const milk = foods.foods[1];
  assert.deepEqual(milk.name, { en: 'Whole milk', es: 'Leche entera' });
  assert.equal(milk.unit, 'ML');
  assert.deepEqual(milk.serving, { name: { en: 'glass', es: 'vaso' }, grams: 250 });
  assert.equal(foods.foods[0].unit, 'G');
  assert.equal(foods.foods[0].serving, null);
});

test('negative values become 0, missing optional nutrients stay null and salt is sodium x 2.5', () => {
  const chicken = buildFoods({ usda, selection, es, lockedIds: [] }).foods.foods[0];
  assert.equal(chicken.carbs, 0);
  assert.equal(chicken.fiber, null);
  assert.equal(chicken.sugar, null);
  assert.equal(chicken.salt, 0.11);
  assert.equal(saltFromSodium(1000), 2.5);
  assert.equal(saltFromSodium(null), null);
});

test('ids that were emitted before and are gone become deprecated, and the lock keeps every id', () => {
  const { foods, lockedIds } = buildFoods({ usda, selection, es, lockedIds: ['usda-9'] });
  assert.deepEqual(foods.deprecatedIds, ['usda-9']);
  assert.deepEqual(lockedIds, ['usda-1', 'usda-2', 'usda-9']);
});

test('the content hash changes with the content and not with the input order', () => {
  const a = buildFoods({ usda, selection, es, lockedIds: [] }).foods.contentHash;
  const reversed = { ...selection, foods: [...selection.foods].reverse() };
  assert.equal(buildFoods({ usda, selection: reversed, es, lockedIds: [] }).foods.contentHash, a);
  const renamed = buildFoods({ usda, selection, es: { ...es, 2: 'Leche' }, lockedIds: [] }).foods.contentHash;
  assert.notEqual(renamed, a);
});

test('missing Spanish names are reported instead of invented', () => {
  const { foods, missingEs } = buildFoods({ usda, selection, es: { 1: 'Pechuga' }, lockedIds: [] });
  assert.deepEqual(missingEs, [2]);
  assert.equal(foods.foods[1].name.es, '');
});

test('unknown ids, duplicates and serving kinds fail with a readable message', () => {
  assert.throws(() => buildFoods({ usda, selection: { ...selection, foods: [{ fdc: 99, en: 'x' }] }, es, lockedIds: [] }), /fdc 99.*not in the USDA snapshot/);
  assert.throws(() => buildFoods({ usda, selection: { ...selection, foods: [{ fdc: 1, en: 'x' }, { fdc: 1, en: 'y' }] }, es, lockedIds: [] }), /listed twice/);
  assert.throws(() => buildFoods({ usda, selection: { ...selection, foods: [{ fdc: 1, en: 'x', serving: ['cup', 1] }] }, es, lockedIds: [] }), /unknown serving kind "cup"/);
  assert.throws(() => buildFoods({ usda, selection: { ...selection, foods: [{ fdc: 1, en: 'x', unit: 'L' }] }, es, lockedIds: [] }), /unit must be/);
});
