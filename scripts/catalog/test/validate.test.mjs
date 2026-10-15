import { test } from 'node:test';
import assert from 'node:assert/strict';
import { reviewVariantLabels, validateCatalog } from '../lib/validate.mjs';

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

test('malformed entries are reported instead of crashing', () => {
  const errors = validateCatalog(catalog([exercise({ primaryMuscles: undefined, secondaryMuscles: 'CHEST', variants: null, instructions: null })]));
  assert.ok(errors.some((e) => e.includes('primaryMuscles must be an array')));
  assert.ok(errors.some((e) => e.includes('secondaryMuscles must be an array')));
  assert.ok(errors.some((e) => e.includes('variants must be an array')));
  assert.ok(errors.some((e) => e.includes('instructions must be')));
  const partial = validateCatalog(catalog([exercise({ instructions: { en: ['a'], es: 'b' } })]));
  assert.ok(partial.some((e) => e.includes('instructions must be')));
});

test('Spanish steps without English steps are reported', () => {
  const errors = validateCatalog(catalog([exercise({ instructions: { en: [], es: ['b'] } })]));
  assert.ok(errors.some((e) => e.includes('instructions.es has 1 steps, instructions.en has 0')));
});

test('ids must be kebab-case', () => {
  const errors = validateCatalog(catalog([exercise({ id: 'Bench_Press', variants: [variant({ id: 'Bench_Press.Barbell' })] })]));
  assert.ok(errors.some((e) => e.includes('Bench_Press: exercise id is not kebab-case')));
  assert.ok(errors.some((e) => e.includes('Bench_Press.Barbell: variant id')));
});

test('BODYWEIGHT_REPS needs a bodyweight, band or machine load', () => {
  const ok = exercise({ trackingType: 'BODYWEIGHT_REPS', variants: [variant({ id: 'bench-press.band', equipment: 'BAND', loadType: 'BAND' })] });
  assert.deepEqual(validateCatalog(catalog([ok])), []);
  const errors = validateCatalog(catalog([exercise({ trackingType: 'BODYWEIGHT_REPS' })]));
  assert.ok(errors.some((e) => e.includes('BODYWEIGHT_REPS exercise with loadType BARBELL')));
});

test('catalogVersion must be a positive integer and primary muscles non-empty', () => {
  assert.ok(validateCatalog({ ...catalog([exercise()]), catalogVersion: 0 }).includes('catalogVersion must be a positive integer'));
  assert.ok(validateCatalog({ ...catalog([exercise()]), catalogVersion: '1' }).includes('catalogVersion must be a positive integer'));
  assert.ok(validateCatalog(catalog([exercise({ primaryMuscles: [] })])).includes('bench-press: no primary muscles'));
});

test('null entries and non-string names are reported instead of crashing', () => {
  const errors = validateCatalog(catalog([null, exercise({ name: { en: 42, es: null }, variants: [null] })]));
  assert.ok(errors.some((e) => e.includes('exercise entry must be an object')));
  assert.ok(errors.some((e) => e.includes('bench-press: missing name.en')));
  assert.ok(errors.some((e) => e.includes('bench-press: missing name.es')));
  assert.ok(errors.some((e) => e.includes('bench-press: variant entry must be an object')));
});

test('variants sharing equipment need distinct names', () => {
  const twoCables = (a, b) => exercise({ variants: [
    variant({ id: 'bench-press.wide', equipment: 'CABLE', loadType: 'MACHINE', name: a }),
    variant({ id: 'bench-press.close', equipment: 'CABLE', loadType: 'MACHINE', name: b }),
  ] });
  const unnamed = validateCatalog(catalog([twoCables(null, { en: 'Close', es: 'Cerrado' })]));
  assert.ok(unnamed.some((e) => e.includes('bench-press.wide: variants sharing CABLE need a name')));
  const same = validateCatalog(catalog([twoCables({ en: 'Grip', es: 'Agarre' }, { en: 'Grip', es: 'Otro' })]));
  assert.ok(same.some((e) => e.includes('bench-press: duplicate variant name.en "Grip"')));
  assert.deepEqual(validateCatalog(catalog([twoCables({ en: 'Wide', es: 'Ancho' }, { en: 'Close', es: 'Cerrado' })])), []);
});

test('a variant name must have en and es', () => {
  const errors = validateCatalog(catalog([exercise({ variants: [variant({ name: { en: 'Wide' } })] })]));
  assert.ok(errors.some((e) => e.includes('bench-press.barbell: variant name needs en and es')));
});

test('a variant name repeating the exercise name is reported', () => {
  const errors = validateCatalog(catalog([exercise({ variants: [variant({ name: { en: 'bench press', es: 'Con barra' } })] })]));
  assert.ok(errors.some((e) => e.includes('repeats the exercise name')));
});

test('the label review flags generic and unnamed band labels', () => {
  const { rows, warnings } = reviewVariantLabels(catalog([
    exercise({
      id: 'pull-up', name: { en: 'Pull-Up', es: 'Dominada' }, trackingType: 'BODYWEIGHT_REPS', variants: [
        variant({ id: 'pull-up.bodyweight', equipment: 'BODYWEIGHT', loadType: 'BODYWEIGHT' }),
        variant({ id: 'pull-up.band-assisted', equipment: 'BAND', loadType: 'BAND' }),
        variant({ id: 'pull-up.none', equipment: 'NONE', loadType: 'NONE', name: { en: 'Named', es: 'Con nombre' } }),
      ],
    }),
    exercise(),
  ]));
  assert.equal(rows.length, 3, 'single-variant exercises are skipped');
  assert.deepEqual(warnings, ['pull-up.band-assisted: band variant of a bodyweight movement needs a name']);
});
