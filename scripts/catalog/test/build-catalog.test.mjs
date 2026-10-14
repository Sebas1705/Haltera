import { test } from 'node:test';
import assert from 'node:assert/strict';
import { buildCatalog } from '../lib/build-catalog.mjs';

const source = (id, over = {}) => ({
  id, name: id.replace(/_/g, ' '), force: 'push', level: 'beginner', mechanic: 'compound', equipment: 'barbell',
  primaryMuscles: ['chest'], secondaryMuscles: ['triceps'], instructions: ['Step one.', 'Step two.'],
  category: 'strength', images: [`${id}/0.jpg`, `${id}/1.jpg`], ...over,
});
const es = (ids) => Object.fromEntries(ids.map((id) => [id, { name: `ES ${id}`, instructions: ['Paso uno.', 'Paso dos.'] }]));
const base = { catalogVersion: 1, groups: [], patches: {}, exclude: [] };

test('ungrouped source becomes one exercise with one variant', () => {
  const { catalog, missingEs } = buildCatalog({
    sources: [source('Barbell_Curl')], wger: [], overrides: base, custom: { exercises: [] }, es: es(['Barbell_Curl']), lockedIds: [],
  });
  assert.deepEqual(missingEs, []);
  const [exercise] = catalog.exercises;
  assert.equal(exercise.id, 'barbell-curl');
  assert.equal(exercise.trackingType, 'WEIGHT_REPS');
  assert.deepEqual(exercise.variants.map((v) => v.id), ['barbell-curl.barbell']);
  assert.deepEqual(exercise.variants[0].frames, ['Barbell_Curl/0.jpg', 'Barbell_Curl/1.jpg']);
});

test('groups merge sources into variants and remove them from the ungrouped list', () => {
  const overrides = {
    ...base,
    groups: [{
      id: 'bench-press', name: { en: 'Bench Press', es: 'Press de banca' },
      variants: [{ source: 'Barbell_Bench' }, { source: 'Dumbbell_Bench' }, { suffix: 'band-only', equipment: 'BAND' }],
    }],
  };
  const sources = [source('Barbell_Bench'), source('Dumbbell_Bench', { equipment: 'dumbbell' })];
  const { catalog } = buildCatalog({ sources, wger: [], overrides, custom: { exercises: [] }, es: es(['Barbell_Bench', 'Dumbbell_Bench']), lockedIds: [] });
  assert.equal(catalog.exercises.length, 1);
  const [exercise] = catalog.exercises;
  assert.deepEqual(exercise.name, { en: 'Bench Press', es: 'Press de banca' });
  assert.deepEqual(exercise.variants.map((v) => [v.id, v.loadType]), [
    ['bench-press.barbell', 'BARBELL'], ['bench-press.dumbbell', 'DUMBBELL'], ['bench-press.band-only', 'BAND'],
  ]);
  assert.deepEqual(exercise.variants[2].frames, []);
});

test('wger provides the Spanish name when es.yaml has none', () => {
  const wger = [{ wgerId: 1, en: 'Barbell Curl', enAliases: [], es: 'Curl con barra', license: 'CC-BY-SA 4', author: 'someone' }];
  const { catalog, usedWger } = buildCatalog({
    sources: [source('Barbell_Curl')], wger, overrides: base, custom: { exercises: [] },
    es: { Barbell_Curl: { instructions: ['Paso uno.', 'Paso dos.'] } }, lockedIds: [],
  });
  assert.equal(catalog.exercises[0].name.es, 'Curl con barra');
  assert.deepEqual(usedWger.map((w) => w.wgerId), [1]);
});

test('missing Spanish is reported per source id', () => {
  const { missingEs } = buildCatalog({ sources: [source('Barbell_Curl')], wger: [], overrides: base, custom: { exercises: [] }, es: {}, lockedIds: [] });
  assert.deepEqual(missingEs, ['Barbell_Curl']);
});

test('patches and excludes apply before grouping', () => {
  const overrides = { ...base, patches: { Plank: { trackingType: 'TIME' } }, exclude: ['Car_Deadlift'] };
  const sources = [source('Plank', { equipment: 'body only', force: 'push' }), source('Car_Deadlift', { equipment: 'other' })];
  const { catalog } = buildCatalog({ sources, wger: [], overrides, custom: { exercises: [] }, es: es(['Plank']), lockedIds: [] });
  assert.deepEqual(catalog.exercises.map((e) => [e.id, e.trackingType]), [['plank', 'TIME']]);
});

test('ids that disappear are listed as deprecated and the hash is stable', () => {
  const input = { sources: [source('Barbell_Curl')], wger: [], overrides: base, custom: { exercises: [] }, es: es(['Barbell_Curl']), lockedIds: ['old-one', 'old-one.barbell', 'barbell-curl'] };
  const first = buildCatalog(input);
  assert.deepEqual(first.catalog.deprecatedIds, ['old-one', 'old-one.barbell']);
  assert.deepEqual(first.lockedIds, ['barbell-curl', 'barbell-curl.barbell', 'old-one', 'old-one.barbell']);
  assert.equal(buildCatalog(input).catalog.contentHash, first.catalog.contentHash);
});

test('custom exercises are appended', () => {
  const custom = { exercises: [{
    id: 'padel', name: { en: 'Padel', es: 'Pádel' }, category: 'SPORT', trackingType: 'TIME', level: 'BEGINNER',
    primaryMuscles: ['FULL_BODY'], secondaryMuscles: [], instructions: { en: ['Play.'], es: ['Juega.'] },
    variants: [{ suffix: 'match', equipment: 'NONE' }],
  }] };
  const { catalog } = buildCatalog({ sources: [], wger: [], overrides: base, custom, es: {}, lockedIds: [] });
  assert.deepEqual(catalog.exercises[0].variants, [{ id: 'padel.match', equipment: 'NONE', loadType: 'NONE', name: null, instructions: null, frames: [] }]);
});

const run = (over) => buildCatalog({ sources: [], wger: [], overrides: base, custom: { exercises: [] }, es: {}, lockedIds: [], ...over });

test('output order and hash do not depend on input order', () => {
  const ids = ['Zottman_Curl', 'Ab_Roller', 'abs_lower', 'Barbell_Curl', 'Ölympic_Lift', 'cable-row', 'Cable_Row_2'];
  const sources = ids.map((id) => source(id));
  const first = run({ sources, es: es(ids) });
  const shuffled = run({ sources: [3, 6, 0, 5, 1, 4, 2].map((i) => sources[i]), es: es(ids) });
  assert.deepEqual(shuffled.catalog, first.catalog);
  const exerciseIds = first.catalog.exercises.map((e) => e.id);
  assert.deepEqual(exerciseIds, [...exerciseIds].sort((a, b) => (a < b ? -1 : a > b ? 1 : 0)));
});

test('patches and excludes naming unknown sources are errors', () => {
  assert.throws(() => run({ sources: [source('Plank')], es: es(['Plank']), overrides: { ...base, patches: { Nope: { trackingType: 'TIME' } } } }), /patches: unknown source "Nope"/);
  assert.throws(() => run({ sources: [source('Plank')], es: es(['Plank']), overrides: { ...base, exclude: ['Nope'] } }), /exclude: unknown source "Nope"/);
});

test('a source used twice in groups is an error', () => {
  const group = (id, sources) => ({ id, name: { en: id, es: id }, variants: sources.map((s) => ({ source: s, suffix: `${s.toLowerCase()}-${id}` })) });
  const sources = [source('Barbell_Bench')];
  assert.throws(() => run({ sources, es: es(['Barbell_Bench']), overrides: { ...base, groups: [group('a', ['Barbell_Bench', 'Barbell_Bench'])] } }), /source "Barbell_Bench" is already grouped/);
  assert.throws(() => run({ sources, es: es(['Barbell_Bench']), overrides: { ...base, groups: [group('a', ['Barbell_Bench']), group('b', ['Barbell_Bench'])] } }), /source "Barbell_Bench" is already grouped/);
});

test('a source both excluded and grouped is an error', () => {
  const overrides = { ...base, exclude: ['Barbell_Bench'], groups: [{ id: 'bench', name: { en: 'B', es: 'B' }, variants: [{ source: 'Barbell_Bench' }] }] };
  assert.throws(() => run({ sources: [source('Barbell_Bench')], overrides }), /source "Barbell_Bench" is excluded/);
});

test('a patched category drives the null-equipment default', () => {
  const sources = [source('Hip_Stretch', { equipment: null, category: 'stretching', force: 'pull' })];
  const { catalog } = run({ sources, es: es(['Hip_Stretch']), overrides: { ...base, patches: { Hip_Stretch: { category: 'STRENGTH' } } } });
  assert.equal(catalog.exercises[0].variants[0].equipment, 'BODYWEIGHT');
});
