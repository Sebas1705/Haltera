import { test } from 'node:test';
import assert from 'node:assert/strict';
import { validateFoods } from '../lib/validate.mjs';

const food = (patch = {}) => ({
  id: 'usda-1',
  name: { en: 'Egg', es: 'Huevo' },
  unit: 'G',
  kcal: 143,
  protein: 12.6,
  carbs: 0.7,
  fat: 9.5,
  fiber: 0,
  sugar: null,
  salt: 0.36,
  serving: { name: { en: 'unit', es: 'unidad' }, grams: 50 },
  ...patch,
});
const data = (foods, deprecatedIds = []) => ({ foodsVersion: 1, contentHash: 'h', deprecatedIds, foods });

test('a valid file has no errors', () => {
  assert.deepEqual(validateFoods(data([food()])), []);
});

test('missing names, bad ids, units and nutrients are reported', () => {
  const errors = validateFoods(data([food({ id: 'egg', name: { en: 'Egg', es: '' }, unit: 'KG', kcal: -1, salt: 'x' })]));
  assert.ok(errors.some((e) => e.includes('id must look like')));
  assert.ok(errors.some((e) => e.includes('missing Spanish name')));
  assert.ok(errors.some((e) => e.includes('unit must be')));
  assert.ok(errors.some((e) => e.includes('kcal must be')));
  assert.ok(errors.some((e) => e.includes('salt must be null or')));
});

test('impossible energy or macros per 100 g are reported', () => {
  assert.ok(validateFoods(data([food({ kcal: 950 })])).some((e) => e.includes('impossible')));
  assert.ok(validateFoods(data([food({ protein: 60, carbs: 30, fat: 20 })])).some((e) => e.includes('protein + carbs + fat')));
});

test('duplicates, live deprecated ids and bad servings are reported', () => {
  const errors = validateFoods(data([food(), food({ serving: { name: { en: 'unit', es: 'unidad' }, grams: 0 } })], ['usda-1']));
  assert.ok(errors.some((e) => e.includes('duplicate id')));
  assert.ok(errors.some((e) => e.includes('both live and deprecated')));
  assert.ok(errors.some((e) => e.includes('serving grams')));
});

test('an empty list is invalid', () => {
  assert.deepEqual(validateFoods(data([])), ['foods must be a non-empty list']);
});
