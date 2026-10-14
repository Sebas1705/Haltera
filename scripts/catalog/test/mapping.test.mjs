import { test } from 'node:test';
import assert from 'node:assert/strict';
import { equipmentOf, categoryOf, loadTypeOf, trackingOf, musclesOf, levelOf } from '../lib/mapping.mjs';
import { slug } from '../lib/slug.mjs';

const src = (over) => ({ name: 'X', equipment: 'barbell', category: 'strength', force: 'push', level: 'beginner', primaryMuscles: [], secondaryMuscles: [], ...over });

test('smith machine exercises are SMITH even though the source says machine', () => {
  assert.equal(equipmentOf(src({ name: 'Smith Machine Bench Press', equipment: 'machine' })), 'SMITH');
});

test('cardio machines are CARDIO_MACHINE', () => {
  assert.equal(equipmentOf(src({ name: 'Jogging, Treadmill', equipment: 'machine', category: 'cardio' })), 'CARDIO_MACHINE');
});

test('null equipment is BODYWEIGHT for strength and NONE otherwise', () => {
  assert.equal(equipmentOf(src({ equipment: null })), 'BODYWEIGHT');
  assert.equal(equipmentOf(src({ equipment: null, category: 'stretching' })), 'NONE');
});

test('source categories collapse into ours', () => {
  assert.equal(categoryOf(src({ category: 'powerlifting' })), 'STRENGTH');
  assert.equal(categoryOf(src({ category: 'stretching' })), 'MOBILITY');
  assert.equal(categoryOf(src({ category: 'cardio' })), 'CARDIO');
});

test('load type follows equipment', () => {
  assert.equal(loadTypeOf('EZ_BAR'), 'BARBELL');
  assert.equal(loadTypeOf('KETTLEBELL'), 'DUMBBELL');
  assert.equal(loadTypeOf('CABLE'), 'MACHINE');
  assert.equal(loadTypeOf('FOAM_ROLLER'), 'NONE');
});

test('tracking type defaults', () => {
  assert.equal(trackingOf('MOBILITY', 'NONE', 'static'), 'TIME');
  assert.equal(trackingOf('CARDIO', 'CARDIO_MACHINE', null), 'DISTANCE_TIME');
  assert.equal(trackingOf('STRENGTH', 'BODYWEIGHT', 'static'), 'TIME');
  assert.equal(trackingOf('STRENGTH', 'BODYWEIGHT', 'pull'), 'BODYWEIGHT_REPS');
  assert.equal(trackingOf('STRENGTH', 'OTHER', 'push'), 'REPS');
  assert.equal(trackingOf('STRENGTH', 'BARBELL', 'push'), 'WEIGHT_REPS');
  assert.equal(trackingOf('PLYOMETRICS', 'BODYWEIGHT', 'push'), 'REPS');
});

test('muscles and level map to enums', () => {
  assert.deepEqual(musclesOf(['lower back', 'chest']), ['LOWER_BACK', 'CHEST']);
  assert.equal(levelOf('expert'), 'EXPERT');
});

test('slug turns source ids into kebab case', () => {
  assert.equal(slug('Barbell_Bench_Press_-_Medium_Grip'), 'barbell-bench-press-medium-grip');
  assert.equal(slug('3_4_Sit-Up'), '3-4-sit-up');
});

test('null-equipment default follows the category passed in (patched category)', () => {
  assert.equal(equipmentOf(src({ equipment: null, category: 'stretching' }), 'STRENGTH'), 'BODYWEIGHT');
  assert.equal(equipmentOf(src({ equipment: null }), 'MOBILITY'), 'NONE');
});

test('slug strips diacritics', () => {
  assert.equal(slug('Pádel Ñandú'), 'padel-nandu');
});
