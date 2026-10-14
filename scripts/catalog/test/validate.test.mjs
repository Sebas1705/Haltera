import { test } from 'node:test';
import assert from 'node:assert/strict';
import { validateCatalog } from '../lib/validate.mjs';

const variant = (over = {}) => ({ id: 'bench-press.barbell', equipment: 'BARBELL', loadType: 'BARBELL', name: null, instructions: null, frames: [], ...over });
const exercise = (over = {}) => ({
  id: 'bench-press', name: { en: 'Bench Press', es: 'Press de banca' }, category: 'STRENGTH', trackingType: 'WEIGHT_REPS',
  level: 'BEGINNER', primaryMuscles: ['CHEST'], secondaryMuscles: [], instructions: { en: ['a'], es: ['b'] },
  variants: [variant()], ...over,
});
const catalog = (exercises) => ({ catalogVersion: 1, contentHash: 'x', exercises, deprecatedIds: [] });

test('a valid catalogue has no errors', () => {
  assert.deepEqual(validateCatalog(catalog([exercise()])), []);
});

test('duplicate ids are reported', () => {
  const errors = validateCatalog(catalog([exercise(), exercise()]));
  assert.ok(errors.some((e) => e.includes('duplicate id bench-press')));
});

test('missing Spanish name and mismatched instructions are reported', () => {
  const errors = validateCatalog(catalog([exercise({ name: { en: 'Bench Press', es: '' }, instructions: { en: ['a', 'b'], es: ['c'] } })]));
  assert.ok(errors.some((e) => e.includes('name.es')));
  assert.ok(errors.some((e) => e.includes('instructions.es')));
});

test('load type must fit the equipment', () => {
  const errors = validateCatalog(catalog([exercise({ variants: [variant({ loadType: 'DUMBBELL' })] })]));
  assert.ok(errors.some((e) => e.includes('loadType DUMBBELL not allowed for BARBELL')));
});

test('weighted tracking needs a weighted load', () => {
  const errors = validateCatalog(catalog([exercise({ variants: [variant({ id: 'bench-press.bodyweight', equipment: 'BODYWEIGHT', loadType: 'BODYWEIGHT' })] })]));
  assert.ok(errors.some((e) => e.includes('WEIGHT_REPS')));
});

test('variant ids must start with the exercise id', () => {
  const errors = validateCatalog(catalog([exercise({ variants: [variant({ id: 'other.barbell' })] })]));
  assert.ok(errors.some((e) => e.includes('other.barbell')));
});
