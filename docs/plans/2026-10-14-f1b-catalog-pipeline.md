# F1b · Exercise catalogue pipeline — Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** A reproducible Node pipeline that turns pinned source data (free-exercise-db, wger Spanish names) plus our own curation into the bundled `catalog.json` and `ATTRIBUTION.md`, validated in CI.

**Architecture:** Two commands. `fetch` (network) refreshes committed snapshots of the sources under `catalog/sources/`. `build` (offline, deterministic) reads the snapshots and the curation files (`catalog/overrides.yaml`, `catalog/custom.yaml`, `catalog/i18n/es.yaml`), groups source exercises into base exercises with equipment variants, applies mappings and patches, validates, and writes `composeApp/src/commonMain/composeResources/files/catalog.json`, `catalog/ATTRIBUTION.md` and `catalog/ids.lock.json`. `build --check` (CI) fails if the committed outputs are stale. All logic lives in pure functions under `scripts/catalog/lib/`, tested with `node:test`.

**Tech Stack:** Node 22 (ES modules, `node:test`), `yaml` 2.8.3.

**Platform:** tooling (output consumed by KMP in F1c)

---

## Context for the executor

- Repo: `~/Documents/Repos/Ironlog`. Work on branch `f1b-catalog-pipeline` from `main`. F1a may be running in parallel: this plan only touches `scripts/catalog/`, `catalog/`, `composeApp/src/commonMain/composeResources/files/catalog.json` and adds one job to `.github/workflows/ci.yml` (if F1a has not merged yet, create the file with only this job; rebase resolves it).
- **Everything committed is in English** except the `es` fields and `catalog/i18n/es.yaml` values (Spanish UI data, allowed by AGENTS.md).
- Commits: configured identity, no co-author trailers.
- Sources and licences (spec §6.1): free-exercise-db is Unlicense (public domain); wger data is CC-BY-SA — every Spanish name taken from wger must be listed in `ATTRIBUTION.md` with its licence and author.
- free-exercise-db pinned commit: `f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5` (2026-09-27, 876 exercises).
- wger API: `https://wger.de/api/v2/exerciseinfo/?limit=100&offset=N`, no key. Language ids: English `2`, Spanish `4`. Send `User-Agent: Ironlog-catalog/1.0 (+https://github.com/Sebas1705/Ironlog)`.
- Spanish style: Spain gym vocabulary (see the glossary in Task 8).

## Output format (`catalog.json`)

```json
{
  "catalogVersion": 1,
  "contentHash": "3f2a9c0d1b7e4a55",
  "exercises": [
    {
      "id": "bench-press",
      "name": { "en": "Bench Press", "es": "Press de banca" },
      "category": "STRENGTH",
      "trackingType": "WEIGHT_REPS",
      "level": "BEGINNER",
      "primaryMuscles": ["CHEST"],
      "secondaryMuscles": ["SHOULDERS", "TRICEPS"],
      "instructions": { "en": ["..."], "es": ["..."] },
      "variants": [
        {
          "id": "bench-press.barbell",
          "equipment": "BARBELL",
          "loadType": "BARBELL",
          "name": null,
          "instructions": { "en": ["..."], "es": ["..."] },
          "frames": ["Barbell_Bench_Press_-_Medium_Grip/0.jpg", "Barbell_Bench_Press_-_Medium_Grip/1.jpg"]
        }
      ]
    }
  ],
  "deprecatedIds": []
}
```

- Exercise ids: kebab-case. Variant ids: `<exerciseId>.<suffix>`, suffix kebab-case (default: the equipment in lower kebab case).
- `name` on a variant is `null` unless curation sets it; the app shows "<exercise> (<equipment>)" only when an exercise has more than one variant.
- `frames` are paths relative to free-exercise-db's `exercises/` folder (downloaded by the app in F2).
- `deprecatedIds`: ids that were emitted before (`catalog/ids.lock.json`) and are no longer produced. The app marks them deprecated; they are never deleted.
- Enums:
  - `category`: `STRENGTH, CARDIO, MOBILITY, PLYOMETRICS, CALISTHENICS, SPORT`
  - `trackingType`: `WEIGHT_REPS, BODYWEIGHT_REPS, REPS, TIME, WEIGHT_TIME, DISTANCE_TIME`
  - `level`: `BEGINNER, INTERMEDIATE, EXPERT`
  - muscles: `ABDOMINALS, ABDUCTORS, ADDUCTORS, BICEPS, CALVES, CHEST, FOREARMS, GLUTES, HAMSTRINGS, LATS, LOWER_BACK, MIDDLE_BACK, NECK, QUADRICEPS, SHOULDERS, TRAPS, TRICEPS, FULL_BODY`
  - `equipment`: `BARBELL, EZ_BAR, TRAP_BAR, DUMBBELL, KETTLEBELL, SMITH, MACHINE, CABLE, BAND, BODYWEIGHT, MEDICINE_BALL, SWISS_BALL, FOAM_ROLLER, CARDIO_MACHINE, OTHER, NONE`
  - `loadType`: `BARBELL, DUMBBELL, MACHINE, BODYWEIGHT, BAND, NONE` (`DUMBBELL` means "weight of one implement": dumbbell, kettlebell, medicine ball)

The spec's equipment list (§3.1) gains `TRAP_BAR`, `MEDICINE_BALL`, `SWISS_BALL` and `FOAM_ROLLER` (needed to map the source faithfully); Task 10 updates the spec.

## File map

| Action | Path |
|---|---|
| Create | `scripts/catalog/package.json`, `scripts/catalog/package-lock.json` (by npm) |
| Create | `scripts/catalog/lib/mapping.mjs`, `lib/slug.mjs`, `lib/wger.mjs`, `lib/build-catalog.mjs`, `lib/validate.mjs`, `lib/attribution.mjs` |
| Create | `scripts/catalog/fetch.mjs`, `scripts/catalog/build.mjs` |
| Create | `scripts/catalog/test/mapping.test.mjs`, `test/build-catalog.test.mjs`, `test/validate.test.mjs` |
| Create | `catalog/sources/sources.json`, `catalog/sources/free-exercise-db.json`, `catalog/sources/wger-es.json` |
| Create | `catalog/overrides.yaml`, `catalog/custom.yaml`, `catalog/i18n/es.yaml`, `catalog/ids.lock.json`, `catalog/ATTRIBUTION.md`, `catalog/README.md` |
| Create | `composeApp/src/commonMain/composeResources/files/catalog.json` |
| Modify | `.github/workflows/ci.yml` (job `catalog`), `docs/designs/2026-10-14-ironlog-core-design.md` (§3.1 equipment list) |

---

### Task 1: Package and mappings

**Files:**
- Create: `scripts/catalog/package.json`, `scripts/catalog/lib/mapping.mjs`, `scripts/catalog/lib/slug.mjs`
- Test: `scripts/catalog/test/mapping.test.mjs`

- [ ] **Step 1: Branch and package**

```bash
cd ~/Documents/Repos/Ironlog
git switch -c f1b-catalog-pipeline
mkdir -p scripts/catalog/lib scripts/catalog/test catalog/sources catalog/i18n
```

`scripts/catalog/package.json`:
```json
{
  "name": "ironlog-catalog",
  "private": true,
  "type": "module",
  "engines": { "node": ">=22" },
  "scripts": {
    "fetch": "node fetch.mjs",
    "build": "node build.mjs",
    "check": "node build.mjs --check",
    "test": "node --test \"test/*.test.mjs\""
  },
  "dependencies": {
    "yaml": "2.8.3"
  }
}
```
```bash
npm install --prefix scripts/catalog
printf '\nscripts/catalog/node_modules/\n' >> .gitignore
```
Expected: `scripts/catalog/package-lock.json` created.

- [ ] **Step 2: Write the failing mapping tests**

`scripts/catalog/test/mapping.test.mjs`:
```js
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
```

- [ ] **Step 3: Run to see it fail**

```bash
npm test --prefix scripts/catalog
```
Expected: FAIL — `Cannot find module '../lib/mapping.mjs'`.

- [ ] **Step 4: Implement**

`scripts/catalog/lib/slug.mjs`:
```js
/** Kebab-case id from a source id or name: "Barbell_Bench_Press_-_Medium_Grip" -> "barbell-bench-press-medium-grip". */
export function slug(text) {
  return text
    .toLowerCase()
    .normalize('NFD').replace(/[̀-ͯ]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}
```

`scripts/catalog/lib/mapping.mjs`:
```js
// Mapping from free-exercise-db values to Ironlog enums (spec §3.1).

export const CATEGORIES = ['STRENGTH', 'CARDIO', 'MOBILITY', 'PLYOMETRICS', 'CALISTHENICS', 'SPORT'];
export const TRACKING_TYPES = ['WEIGHT_REPS', 'BODYWEIGHT_REPS', 'REPS', 'TIME', 'WEIGHT_TIME', 'DISTANCE_TIME'];
export const LEVELS = ['BEGINNER', 'INTERMEDIATE', 'EXPERT'];
export const MUSCLES = [
  'ABDOMINALS', 'ABDUCTORS', 'ADDUCTORS', 'BICEPS', 'CALVES', 'CHEST', 'FOREARMS', 'GLUTES', 'HAMSTRINGS',
  'LATS', 'LOWER_BACK', 'MIDDLE_BACK', 'NECK', 'QUADRICEPS', 'SHOULDERS', 'TRAPS', 'TRICEPS', 'FULL_BODY',
];
export const EQUIPMENT = [
  'BARBELL', 'EZ_BAR', 'TRAP_BAR', 'DUMBBELL', 'KETTLEBELL', 'SMITH', 'MACHINE', 'CABLE', 'BAND', 'BODYWEIGHT',
  'MEDICINE_BALL', 'SWISS_BALL', 'FOAM_ROLLER', 'CARDIO_MACHINE', 'OTHER', 'NONE',
];
export const LOAD_TYPES = ['BARBELL', 'DUMBBELL', 'MACHINE', 'BODYWEIGHT', 'BAND', 'NONE'];

/** Load types each equipment may use; the first one is the default. */
export const ALLOWED_LOAD_TYPES = {
  BARBELL: ['BARBELL'],
  EZ_BAR: ['BARBELL'],
  TRAP_BAR: ['BARBELL'],
  SMITH: ['BARBELL', 'MACHINE'],
  DUMBBELL: ['DUMBBELL'],
  KETTLEBELL: ['DUMBBELL'],
  MEDICINE_BALL: ['DUMBBELL', 'NONE'],
  MACHINE: ['MACHINE'],
  CABLE: ['MACHINE'],
  BAND: ['BAND'],
  BODYWEIGHT: ['BODYWEIGHT', 'NONE'],
  SWISS_BALL: ['NONE'],
  FOAM_ROLLER: ['NONE'],
  CARDIO_MACHINE: ['NONE'],
  OTHER: ['NONE', 'DUMBBELL', 'BARBELL', 'MACHINE', 'BODYWEIGHT', 'BAND'],
  NONE: ['NONE', 'BODYWEIGHT'],
};

const SOURCE_EQUIPMENT = {
  'barbell': 'BARBELL',
  'e-z curl bar': 'EZ_BAR',
  'dumbbell': 'DUMBBELL',
  'kettlebells': 'KETTLEBELL',
  'machine': 'MACHINE',
  'cable': 'CABLE',
  'bands': 'BAND',
  'body only': 'BODYWEIGHT',
  'medicine ball': 'MEDICINE_BALL',
  'exercise ball': 'SWISS_BALL',
  'foam roll': 'FOAM_ROLLER',
  'other': 'OTHER',
};

const SOURCE_CATEGORY = {
  'strength': 'STRENGTH',
  'powerlifting': 'STRENGTH',
  'olympic weightlifting': 'STRENGTH',
  'strongman': 'STRENGTH',
  'stretching': 'MOBILITY',
  'plyometrics': 'PLYOMETRICS',
  'cardio': 'CARDIO',
};

export function categoryOf(source) {
  const category = SOURCE_CATEGORY[source.category];
  if (!category) throw new Error(`Unknown source category "${source.category}" in ${source.name}`);
  return category;
}

export function equipmentOf(source) {
  if (/smith machine/i.test(source.name)) return 'SMITH';
  if (source.equipment === 'machine' && source.category === 'cardio') return 'CARDIO_MACHINE';
  if (source.equipment == null) return categoryOf(source) === 'STRENGTH' ? 'BODYWEIGHT' : 'NONE';
  const equipment = SOURCE_EQUIPMENT[source.equipment];
  if (!equipment) throw new Error(`Unknown source equipment "${source.equipment}" in ${source.name}`);
  return equipment;
}

export function loadTypeOf(equipment) {
  return ALLOWED_LOAD_TYPES[equipment][0];
}

export function trackingOf(category, equipment, force) {
  if (category === 'MOBILITY') return 'TIME';
  if (category === 'CARDIO') return 'DISTANCE_TIME';
  const loadType = loadTypeOf(equipment);
  if (category === 'PLYOMETRICS') return loadType === 'NONE' || loadType === 'BODYWEIGHT' ? 'REPS' : 'WEIGHT_REPS';
  if (equipment === 'BODYWEIGHT') return force === 'static' ? 'TIME' : 'BODYWEIGHT_REPS';
  if (loadType === 'NONE') return force === 'static' ? 'TIME' : 'REPS';
  return 'WEIGHT_REPS';
}

export function musclesOf(list) {
  return list.map((m) => {
    const value = m.toUpperCase().replace(/ /g, '_');
    if (!MUSCLES.includes(value)) throw new Error(`Unknown muscle "${m}"`);
    return value;
  });
}

export function levelOf(level) {
  return level ? level.toUpperCase() : 'BEGINNER';
}
```

- [ ] **Step 5: Run the tests**

```bash
npm test --prefix scripts/catalog
```
Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add .gitignore scripts/catalog
git commit -m "feat(catalog): package and source-to-enum mappings"
```

### Task 2: Source snapshots (`fetch`)

**Files:**
- Create: `scripts/catalog/lib/wger.mjs`, `scripts/catalog/fetch.mjs`, `catalog/sources/sources.json`, `catalog/sources/free-exercise-db.json`, `catalog/sources/wger-es.json`

- [ ] **Step 1: Pin the sources**

`catalog/sources/sources.json`:
```json
{
  "freeExerciseDb": {
    "repo": "yuhonas/free-exercise-db",
    "commit": "f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5",
    "license": "Unlicense"
  },
  "wger": {
    "api": "https://wger.de/api/v2/exerciseinfo/",
    "fetchedAt": null
  }
}
```

- [ ] **Step 2: wger reduction**

`scripts/catalog/lib/wger.mjs`:
```js
// Reduces wger exerciseinfo results to the English/Spanish name pairs we use, with their licence.

const ENGLISH = 2;
const SPANISH = 4;

/** Normalised key used to match names across sources. */
export function nameKey(name) {
  return name.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9]/g, '');
}

/** One entry per wger exercise that has both an English and a Spanish translation. */
export function reduceWger(results) {
  const entries = [];
  for (const exercise of results) {
    const en = exercise.translations.find((t) => t.language === ENGLISH);
    const es = exercise.translations.find((t) => t.language === SPANISH);
    if (!en || !es || !es.name.trim()) continue;
    entries.push({
      wgerId: exercise.id,
      en: en.name.trim(),
      enAliases: (en.aliases ?? []).map((a) => (typeof a === 'string' ? a : a.alias)).filter(Boolean),
      es: es.name.trim(),
      license: es.license_title || exercise.license?.short_name || 'CC-BY-SA',
      author: es.license_author || exercise.license_author || 'wger.de',
    });
  }
  return entries.sort((a, b) => a.wgerId - b.wgerId);
}

/** Map nameKey -> entry over English names and aliases; the lowest wger id wins on collisions. */
export function indexWger(entries) {
  const index = new Map();
  for (const entry of entries) {
    for (const name of [entry.en, ...entry.enAliases]) {
      const key = nameKey(name);
      if (key && !index.has(key)) index.set(key, entry);
    }
  }
  return index;
}
```

- [ ] **Step 3: Fetch script**

`scripts/catalog/fetch.mjs`:
```js
// Refreshes the committed source snapshots under catalog/sources/. Needs network; `build` does not.
import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { reduceWger } from './lib/wger.mjs';

const root = fileURLToPath(new URL('../../catalog/sources/', import.meta.url));
const userAgent = 'Ironlog-catalog/1.0 (+https://github.com/Sebas1705/Ironlog)';

async function getJson(url) {
  const response = await fetch(url, { headers: { 'User-Agent': userAgent, Accept: 'application/json' } });
  if (!response.ok) throw new Error(`${response.status} ${response.statusText} for ${url}`);
  return response.json();
}

const sources = JSON.parse(await readFile(root + 'sources.json', 'utf8'));

const { repo, commit } = sources.freeExerciseDb;
const exercises = await getJson(`https://raw.githubusercontent.com/${repo}/${commit}/dist/exercises.json`);
await writeFile(root + 'free-exercise-db.json', JSON.stringify(exercises, null, 1) + '\n');
console.log(`free-exercise-db@${commit.slice(0, 7)}: ${exercises.length} exercises`);

const results = [];
for (let url = `${sources.wger.api}?limit=100&offset=0`; url; ) {
  const page = await getJson(url);
  results.push(...page.results);
  url = page.next;
}
const wger = reduceWger(results);
await writeFile(root + 'wger-es.json', JSON.stringify(wger, null, 1) + '\n');
console.log(`wger: ${results.length} exercises, ${wger.length} with English and Spanish names`);

sources.wger.fetchedAt = new Date().toISOString().slice(0, 10);
await writeFile(root + 'sources.json', JSON.stringify(sources, null, 2) + '\n');
```

- [ ] **Step 4: Run it**

```bash
node scripts/catalog/fetch.mjs
```
Expected: `free-exercise-db@f00c92c: 876 exercises` and `wger: 9xx exercises, N with English and Spanish names` (N > 100).

- [ ] **Step 5: Commit**

```bash
git add scripts/catalog/lib/wger.mjs scripts/catalog/fetch.mjs catalog/sources
git commit -m "feat(catalog): pinned snapshots of free-exercise-db and wger Spanish names"
```

### Task 3: Catalogue builder (pure)

**Files:**
- Create: `scripts/catalog/lib/build-catalog.mjs`
- Test: `scripts/catalog/test/build-catalog.test.mjs`

Inputs of `buildCatalog`:

| Input | Shape |
|---|---|
| `sources` | free-exercise-db array |
| `wger` | `wger-es.json` array |
| `overrides` | parsed `overrides.yaml`: `{ catalogVersion, groups: [...], patches: { <sourceId>: {...} }, exclude: [<sourceId>] }` |
| `custom` | parsed `custom.yaml`: `{ exercises: [...] }` |
| `es` | parsed `i18n/es.yaml`: `{ <sourceId>: { name, instructions: [...] } }` |
| `lockedIds` | `ids.lock.json` array |

Group entry: `{ id, name: {en, es}, category?, trackingType?, primaryMuscles?, secondaryMuscles?, variants: [ { source, suffix?, equipment?, loadType?, name? } | { suffix, equipment, loadType? } ] }`. The first variant is the primary one: the exercise takes its category, muscles, level and instructions unless the group sets them.

Patch entry (by source id, applied before grouping): any of `equipment`, `loadType`, `category`, `trackingType`, `primaryMuscles`, `secondaryMuscles`.

Custom exercise: same shape as an output exercise but `instructions` and `name` inline, variants without frames: `{ id, name: {en, es}, category, trackingType, level, primaryMuscles, secondaryMuscles, instructions: {en, es}, variants: [ { suffix, equipment, loadType? } ] }`.

Spanish resolution for a source exercise: `es.yaml[sourceId].name` ?? wger match by `nameKey(source.name)`; instructions only from `es.yaml[sourceId].instructions`. Missing Spanish is reported, not invented.

- [ ] **Step 1: Write the failing tests**

`scripts/catalog/test/build-catalog.test.mjs`:
```js
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
```

- [ ] **Step 2: Run to see them fail**

```bash
npm test --prefix scripts/catalog
```
Expected: FAIL — `Cannot find module '../lib/build-catalog.mjs'`.

- [ ] **Step 3: Implement**

`scripts/catalog/lib/build-catalog.mjs`:
```js
// Pure catalogue builder: sources + curation -> catalog.json content (spec §6.2).
import { createHash } from 'node:crypto';
import { categoryOf, equipmentOf, levelOf, loadTypeOf, musclesOf, trackingOf } from './mapping.mjs';
import { slug } from './slug.mjs';
import { indexWger, nameKey } from './wger.mjs';

/** A source exercise after mappings and patches, before grouping. */
function prepare(source, patch = {}) {
  const category = patch.category ?? categoryOf(source);
  const equipment = patch.equipment ?? equipmentOf(source);
  return {
    sourceId: source.id,
    nameEn: source.name,
    category,
    equipment,
    loadType: patch.loadType ?? loadTypeOf(equipment),
    trackingType: patch.trackingType ?? trackingOf(category, equipment, source.force),
    level: levelOf(source.level),
    primaryMuscles: patch.primaryMuscles ?? musclesOf(source.primaryMuscles),
    secondaryMuscles: patch.secondaryMuscles ?? musclesOf(source.secondaryMuscles),
    instructionsEn: source.instructions,
    frames: source.images,
  };
}

export function buildCatalog({ sources, wger, overrides, custom, es, lockedIds }) {
  const wgerIndex = indexWger(wger);
  const usedWger = new Map();
  const missingEs = new Set();
  const excluded = new Set(overrides.exclude ?? []);
  const prepared = new Map(
    sources.filter((s) => !excluded.has(s.id)).map((s) => [s.id, prepare(s, overrides.patches?.[s.id])]),
  );

  const spanishName = (p) => {
    const own = es[p.sourceId]?.name;
    if (own) return own;
    const match = wgerIndex.get(nameKey(p.nameEn));
    if (match) {
      usedWger.set(match.wgerId, match);
      return match.es;
    }
    missingEs.add(p.sourceId);
    return '';
  };
  const spanishInstructions = (p) => {
    const own = es[p.sourceId]?.instructions;
    if (own?.length || p.instructionsEn.length === 0) return own ?? [];
    missingEs.add(p.sourceId);
    return [];
  };
  const instructionsOf = (p) => ({ en: p.instructionsEn, es: spanishInstructions(p) });

  const exercises = [];
  const grouped = new Set();

  for (const group of overrides.groups ?? []) {
    const variants = group.variants.map((v) => {
      if (!v.source) {
        return { id: `${group.id}.${v.suffix}`, equipment: v.equipment, loadType: v.loadType ?? loadTypeOf(v.equipment), name: v.name ?? null, instructions: null, frames: [] };
      }
      const p = prepared.get(v.source);
      if (!p) throw new Error(`Group ${group.id}: unknown or excluded source "${v.source}"`);
      grouped.add(v.source);
      const equipment = v.equipment ?? p.equipment;
      return {
        id: `${group.id}.${v.suffix ?? slug(equipment)}`,
        equipment,
        loadType: v.loadType ?? (v.equipment ? loadTypeOf(v.equipment) : p.loadType),
        name: v.name ?? null,
        instructions: instructionsOf(p),
        frames: p.frames,
      };
    });
    const primary = prepared.get(group.variants.find((v) => v.source)?.source);
    if (!primary) throw new Error(`Group ${group.id}: needs at least one source variant`);
    exercises.push({
      id: group.id,
      name: group.name,
      category: group.category ?? primary.category,
      trackingType: group.trackingType ?? primary.trackingType,
      level: group.level ?? primary.level,
      primaryMuscles: group.primaryMuscles ?? primary.primaryMuscles,
      secondaryMuscles: group.secondaryMuscles ?? primary.secondaryMuscles,
      instructions: instructionsOf(primary),
      variants,
    });
  }

  for (const p of prepared.values()) {
    if (grouped.has(p.sourceId)) continue;
    const id = slug(p.sourceId);
    exercises.push({
      id,
      name: { en: p.nameEn, es: spanishName(p) },
      category: p.category,
      trackingType: p.trackingType,
      level: p.level,
      primaryMuscles: p.primaryMuscles,
      secondaryMuscles: p.secondaryMuscles,
      instructions: instructionsOf(p),
      variants: [{ id: `${id}.${slug(p.equipment)}`, equipment: p.equipment, loadType: p.loadType, name: null, instructions: null, frames: p.frames }],
    });
  }

  for (const c of custom.exercises ?? []) {
    exercises.push({
      id: c.id,
      name: c.name,
      category: c.category,
      trackingType: c.trackingType,
      level: c.level,
      primaryMuscles: c.primaryMuscles,
      secondaryMuscles: c.secondaryMuscles,
      instructions: c.instructions,
      variants: c.variants.map((v) => ({
        id: `${c.id}.${v.suffix}`, equipment: v.equipment, loadType: v.loadType ?? loadTypeOf(v.equipment), name: v.name ?? null, instructions: null, frames: [],
      })),
    });
  }

  exercises.sort((a, b) => a.id.localeCompare(b.id));
  const liveIds = exercises.flatMap((e) => [e.id, ...e.variants.map((v) => v.id)]);
  const live = new Set(liveIds);
  const deprecatedIds = lockedIds.filter((id) => !live.has(id)).sort();
  const contentHash = createHash('sha256').update(JSON.stringify({ exercises, deprecatedIds })).digest('hex').slice(0, 16);

  return {
    catalog: { catalogVersion: overrides.catalogVersion, contentHash, exercises, deprecatedIds },
    lockedIds: [...new Set([...lockedIds, ...liveIds])].sort(),
    missingEs: [...missingEs].sort(),
    usedWger: [...usedWger.values()].sort((a, b) => a.wgerId - b.wgerId),
  };
}
```

- [ ] **Step 4: Run the tests**

```bash
npm test --prefix scripts/catalog
```
Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add scripts/catalog/lib/build-catalog.mjs scripts/catalog/test/build-catalog.test.mjs
git commit -m "feat(catalog): pure builder grouping sources into exercises and variants"
```

### Task 4: Validation

**Files:**
- Create: `scripts/catalog/lib/validate.mjs`
- Test: `scripts/catalog/test/validate.test.mjs`

Rules (spec §6.2, plus load/tracking coherence):
1. Exercise ids are kebab-case and unique; variant ids are `<exerciseId>.<kebab suffix>` and unique.
2. `name.en` and `name.es` are non-empty.
3. `instructions.en` non-empty ⇒ `instructions.es` has the same number of steps (exercise and variants).
4. Every enum value is valid; `primaryMuscles` is non-empty.
5. Every exercise has ≥ 1 variant; each variant's `loadType` is allowed for its `equipment` (`ALLOWED_LOAD_TYPES`).
6. `WEIGHT_REPS` / `WEIGHT_TIME` exercises: no variant with `loadType` `NONE` or `BODYWEIGHT`. `BODYWEIGHT_REPS`: every variant's `loadType` is `BODYWEIGHT`, `BAND` or `MACHINE` (assisted).
7. `catalogVersion` is a positive integer.

- [ ] **Step 1: Write the failing tests**

`scripts/catalog/test/validate.test.mjs`:
```js
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
```

- [ ] **Step 2: Run to see them fail**

```bash
npm test --prefix scripts/catalog
```
Expected: FAIL — `Cannot find module '../lib/validate.mjs'`.

- [ ] **Step 3: Implement**

`scripts/catalog/lib/validate.mjs`:
```js
// Catalogue validation (spec §6.2). Returns a list of human-readable errors; empty means valid.
import { ALLOWED_LOAD_TYPES, CATEGORIES, EQUIPMENT, LEVELS, LOAD_TYPES, MUSCLES, TRACKING_TYPES } from './mapping.mjs';

const KEBAB = /^[a-z0-9]+(-[a-z0-9]+)*$/;

function checkInstructions(where, instructions, errors) {
  if (instructions == null) return;
  if (instructions.en.length > 0 && instructions.es.length !== instructions.en.length) {
    errors.push(`${where}: instructions.es has ${instructions.es.length} steps, instructions.en has ${instructions.en.length}`);
  }
}

export function validateCatalog(catalog) {
  const errors = [];
  if (!Number.isInteger(catalog.catalogVersion) || catalog.catalogVersion < 1) errors.push('catalogVersion must be a positive integer');
  const seen = new Set();
  const unique = (id) => {
    if (seen.has(id)) errors.push(`duplicate id ${id}`);
    seen.add(id);
  };

  for (const e of catalog.exercises) {
    unique(e.id);
    if (!KEBAB.test(e.id)) errors.push(`${e.id}: exercise id is not kebab-case`);
    if (!e.name?.en?.trim()) errors.push(`${e.id}: missing name.en`);
    if (!e.name?.es?.trim()) errors.push(`${e.id}: missing name.es`);
    if (!CATEGORIES.includes(e.category)) errors.push(`${e.id}: invalid category ${e.category}`);
    if (!TRACKING_TYPES.includes(e.trackingType)) errors.push(`${e.id}: invalid trackingType ${e.trackingType}`);
    if (!LEVELS.includes(e.level)) errors.push(`${e.id}: invalid level ${e.level}`);
    if (e.primaryMuscles.length === 0) errors.push(`${e.id}: no primary muscles`);
    for (const m of [...e.primaryMuscles, ...e.secondaryMuscles]) {
      if (!MUSCLES.includes(m)) errors.push(`${e.id}: invalid muscle ${m}`);
    }
    checkInstructions(e.id, e.instructions, errors);
    if (e.variants.length === 0) errors.push(`${e.id}: no variants`);

    for (const v of e.variants) {
      unique(v.id);
      const [prefix, suffix, extra] = v.id.split('.');
      if (prefix !== e.id || !suffix || extra !== undefined || !KEBAB.test(suffix)) {
        errors.push(`${v.id}: variant id must be "${e.id}.<kebab-suffix>"`);
      }
      if (!EQUIPMENT.includes(v.equipment)) errors.push(`${v.id}: invalid equipment ${v.equipment}`);
      else if (!LOAD_TYPES.includes(v.loadType) || !ALLOWED_LOAD_TYPES[v.equipment].includes(v.loadType)) {
        errors.push(`${v.id}: loadType ${v.loadType} not allowed for ${v.equipment}`);
      }
      if (['WEIGHT_REPS', 'WEIGHT_TIME'].includes(e.trackingType) && ['NONE', 'BODYWEIGHT'].includes(v.loadType)) {
        errors.push(`${v.id}: ${e.trackingType} exercise with loadType ${v.loadType}`);
      }
      if (e.trackingType === 'BODYWEIGHT_REPS' && !['BODYWEIGHT', 'BAND', 'MACHINE'].includes(v.loadType)) {
        errors.push(`${v.id}: BODYWEIGHT_REPS exercise with loadType ${v.loadType}`);
      }
      checkInstructions(v.id, v.instructions, errors);
    }
  }
  return errors;
}
```

- [ ] **Step 4: Run the tests**

```bash
npm test --prefix scripts/catalog
```
Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add scripts/catalog/lib/validate.mjs scripts/catalog/test/validate.test.mjs
git commit -m "feat(catalog): validation rules for ids, translations, enums and load coherence"
```

### Task 5: Attribution and the `build` command

**Files:**
- Create: `scripts/catalog/lib/attribution.mjs`, `scripts/catalog/build.mjs`

- [ ] **Step 1: Attribution renderer**

`scripts/catalog/lib/attribution.mjs`:
```js
// Renders catalog/ATTRIBUTION.md, shown in the app under Settings -> Credits.

export function renderAttribution({ sources, usedWger }) {
  const lines = [
    '# Exercise catalogue attribution',
    '',
    'Generated by `scripts/catalog/build.mjs`. Do not edit by hand.',
    '',
    '## free-exercise-db',
    '',
    `Exercise data and images from [${sources.freeExerciseDb.repo}](https://github.com/${sources.freeExerciseDb.repo}) at commit \`${sources.freeExerciseDb.commit}\`, released under the ${sources.freeExerciseDb.license} (public domain).`,
    '',
    '## wger',
    '',
    'Spanish exercise names from the [wger](https://wger.de) exercise database, used under their Creative Commons licences:',
    '',
    '| wger exercise | English | Spanish | Licence | Author |',
    '|---|---|---|---|---|',
    ...usedWger.map((w) => `| [${w.wgerId}](https://wger.de/exercise/${w.wgerId}/view/) | ${w.en} | ${w.es} | ${w.license} | ${w.author} |`),
    '',
    '## Ironlog',
    '',
    'Grouping into exercises and variants, Spanish translations not listed above and the additional exercises are by the Ironlog authors.',
    '',
  ];
  return lines.join('\n');
}
```

- [ ] **Step 2: Build command**

`scripts/catalog/build.mjs`:
```js
// Offline catalogue build. Usage:
//   node scripts/catalog/build.mjs            write catalog.json, ATTRIBUTION.md and ids.lock.json
//   node scripts/catalog/build.mjs --check    fail if the committed outputs are stale (CI)
//   node scripts/catalog/build.mjs --missing-es  print the source ids that still need Spanish
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parse } from 'yaml';
import { buildCatalog } from './lib/build-catalog.mjs';
import { validateCatalog } from './lib/validate.mjs';
import { renderAttribution } from './lib/attribution.mjs';

const repo = fileURLToPath(new URL('../../', import.meta.url));
const path = (p) => repo + p;
const OUT_CATALOG = 'composeApp/src/commonMain/composeResources/files/catalog.json';
const OUT_ATTRIBUTION = 'catalog/ATTRIBUTION.md';
const OUT_LOCK = 'catalog/ids.lock.json';

const readJson = async (p, fallback) => (existsSync(path(p)) ? JSON.parse(await readFile(path(p), 'utf8')) : fallback);
const readYaml = async (p, fallback) => (existsSync(path(p)) ? (parse(await readFile(path(p), 'utf8')) ?? fallback) : fallback);
const readText = async (p) => (existsSync(path(p)) ? readFile(path(p), 'utf8') : null);

const sources = await readJson('catalog/sources/sources.json');
const overrides = await readYaml('catalog/overrides.yaml', { catalogVersion: 1 });
const result = buildCatalog({
  sources: await readJson('catalog/sources/free-exercise-db.json'),
  wger: await readJson('catalog/sources/wger-es.json', []),
  overrides,
  custom: await readYaml('catalog/custom.yaml', { exercises: [] }),
  es: await readYaml('catalog/i18n/es.yaml', {}),
  lockedIds: await readJson(OUT_LOCK, []),
});

if (process.argv.includes('--missing-es')) {
  console.log(JSON.stringify(result.missingEs, null, 1));
  process.exit(0);
}

const errors = validateCatalog(result.catalog);
if (errors.length > 0) {
  console.error(`Catalogue invalid (${errors.length} errors):\n` + errors.slice(0, 50).join('\n'));
  if (result.missingEs.length > 0) console.error(`${result.missingEs.length} source exercises lack Spanish: run with --missing-es`);
  process.exit(1);
}

const outputs = {
  [OUT_CATALOG]: JSON.stringify(result.catalog) + '\n',
  [OUT_ATTRIBUTION]: renderAttribution({ sources, usedWger: result.usedWger }),
  [OUT_LOCK]: JSON.stringify(result.lockedIds, null, 1) + '\n',
};

if (process.argv.includes('--check')) {
  const stale = [];
  for (const [p, content] of Object.entries(outputs)) if ((await readText(p)) !== content) stale.push(p);
  if (stale.length > 0) {
    console.error(`Stale catalogue outputs: ${stale.join(', ')}. Run: node scripts/catalog/build.mjs`);
    process.exit(1);
  }
  console.log(`Catalogue up to date: v${result.catalog.catalogVersion}, ${result.catalog.exercises.length} exercises`);
  process.exit(0);
}

// Once a catalogue version has shipped (`released: true`), its content is frozen: changes need a new version.
const previous = await readJson(OUT_CATALOG, null);
if (overrides.released && previous && previous.catalogVersion === result.catalog.catalogVersion && previous.contentHash !== result.catalog.contentHash) {
  console.error(`Catalogue v${previous.catalogVersion} is released: bump catalogVersion and set released: false in catalog/overrides.yaml.`);
  process.exit(1);
}
for (const [p, content] of Object.entries(outputs)) {
  await mkdir(dirname(path(p)), { recursive: true });
  await writeFile(path(p), content);
}
const variants = result.catalog.exercises.reduce((n, e) => n + e.variants.length, 0);
console.log(`catalog v${result.catalog.catalogVersion}: ${result.catalog.exercises.length} exercises, ${variants} variants, ${result.catalog.deprecatedIds.length} deprecated ids`);
```

The "bump catalogVersion" guard only applies when `overrides.yaml` says `released: true` (set when a version ships to testers, F5). Until then the catalogue is edited freely at version 1.

- [ ] **Step 3: Check the missing-Spanish report works on raw sources**

```bash
node scripts/catalog/build.mjs --missing-es | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>console.log(JSON.parse(s).length))'
```
Expected: a number between 600 and 876 (sources without wger match or own translation).

- [ ] **Step 4: Commit**

```bash
git add scripts/catalog/lib/attribution.mjs scripts/catalog/build.mjs
git commit -m "feat(catalog): build command with check mode, attribution and id lock"
```

### Task 6: Curation — groups, patches and excludes

**Files:**
- Create: `catalog/overrides.yaml`

Groups cover the movements the MVP routine catalogue (spec §6.3) needs; the rest of the sources stay as single-variant exercises. A group only merges variants of the *same movement* that differ by equipment (or cable attachment, via `suffix`); bodyweight versions of weighted movements stay separate exercises (their tracking type differs).

- [ ] **Step 1: Write `catalog/overrides.yaml`**

```yaml
# Curation of the exercise catalogue (spec §6.2). Code and keys in English; `es` values in Spanish.
# Once a version ships, set released: true; any later change then needs catalogVersion + 1 and released: false.
catalogVersion: 1
released: false

# Source ids (free-exercise-db) left out: strongman implements and band/chain powerlifting setups
# a regular gym does not have.
exclude:
  - Car_Deadlift
  - Rickshaw_Deadlift
  - Axle_Deadlift
  - Bench_Press_with_Chains
  - Deadlift_with_Chains
  - Squat_with_Chains
  - Box_Squat_with_Chains
  - Sumo_Deadlift_with_Chains
  - Reverse_Band_Bench_Press
  - Reverse_Band_Box_Squat
  - Reverse_Band_Deadlift
  - Reverse_Band_Power_Squat
  - Reverse_Band_Sumo_Deadlift

# Per-source corrections applied before grouping.
patches:
  Rope_Jumping: { trackingType: TIME }
  Trap_Bar_Deadlift: { equipment: TRAP_BAR }
  Band_Assisted_Pull-Up: { equipment: BAND }
  Plank: { trackingType: TIME }
  Pullups: { trackingType: BODYWEIGHT_REPS }
  Chin-Up: { trackingType: BODYWEIGHT_REPS }
  Dips_-_Triceps_Version: { trackingType: BODYWEIGHT_REPS }
  Dips_-_Chest_Version: { equipment: BODYWEIGHT, trackingType: BODYWEIGHT_REPS }
  Parallel_Bar_Dip: { equipment: BODYWEIGHT, trackingType: BODYWEIGHT_REPS }
  Muscle_Up: { equipment: BODYWEIGHT, category: CALISTHENICS, trackingType: BODYWEIGHT_REPS }
  Handstand_Push-Ups: { category: CALISTHENICS }
  Hanging_Leg_Raise: { trackingType: BODYWEIGHT_REPS }
  Inverted_Row: { trackingType: BODYWEIGHT_REPS }

groups:
  - id: bench-press
    name: { en: Bench Press, es: Press de banca }
    variants:
      - source: Barbell_Bench_Press_-_Medium_Grip
      - source: Dumbbell_Bench_Press
      - source: Smith_Machine_Bench_Press
      - source: Machine_Bench_Press
  - id: incline-bench-press
    name: { en: Incline Bench Press, es: Press inclinado }
    variants:
      - source: Barbell_Incline_Bench_Press_-_Medium_Grip
      - source: Incline_Dumbbell_Press
      - source: Smith_Machine_Incline_Bench_Press
      - source: Leverage_Incline_Chest_Press
      - source: Incline_Cable_Chest_Press
  - id: decline-bench-press
    name: { en: Decline Bench Press, es: Press declinado }
    variants:
      - source: Decline_Barbell_Bench_Press
      - source: Decline_Dumbbell_Bench_Press
      - source: Smith_Machine_Decline_Press
  - id: close-grip-bench-press
    name: { en: Close-Grip Bench Press, es: Press de banca con agarre cerrado }
    variants:
      - source: Close-Grip_Barbell_Bench_Press
      - source: Smith_Machine_Close-Grip_Bench_Press
  - id: squat
    name: { en: Squat, es: Sentadilla }
    variants:
      - source: Barbell_Squat
      - source: Smith_Machine_Squat
      - source: Dumbbell_Squat
      - source: Squats_-_With_Bands
  - id: front-squat
    name: { en: Front Squat, es: Sentadilla frontal }
    variants:
      - source: Front_Barbell_Squat
      - source: Front_Squats_With_Two_Kettlebells
  - id: hack-squat
    name: { en: Hack Squat, es: Sentadilla hack }
    variants:
      - source: Hack_Squat
      - source: Barbell_Hack_Squat
  - id: deadlift
    name: { en: Deadlift, es: Peso muerto }
    variants:
      - source: Barbell_Deadlift
      - source: Trap_Bar_Deadlift
      - source: Leverage_Deadlift
      - source: Cable_Deadlifts
  - id: romanian-deadlift
    name: { en: Romanian Deadlift, es: Peso muerto rumano }
    variants:
      - source: Romanian_Deadlift
      - source: Smith_Machine_Stiff-Legged_Deadlift
  - id: stiff-legged-deadlift
    name: { en: Stiff-Legged Deadlift, es: Peso muerto con piernas rígidas }
    variants:
      - source: Stiff-Legged_Barbell_Deadlift
      - source: Stiff-Legged_Dumbbell_Deadlift
  - id: overhead-press
    name: { en: Overhead Press, es: Press militar }
    variants:
      - source: Standing_Military_Press
      - source: Dumbbell_Shoulder_Press
      - source: Smith_Machine_Overhead_Shoulder_Press
      - source: Leverage_Shoulder_Press
      - source: Cable_Shoulder_Press
      - source: Two-Arm_Kettlebell_Military_Press
      - source: Shoulder_Press_-_With_Bands
  - id: bent-over-row
    name: { en: Bent-Over Row, es: Remo inclinado }
    variants:
      - source: Bent_Over_Barbell_Row
      - source: Bent_Over_Two-Dumbbell_Row
      - source: Smith_Machine_Bent_Over_Row
  - id: one-arm-row
    name: { en: One-Arm Row, es: Remo a una mano }
    variants:
      - source: One-Arm_Dumbbell_Row
      - source: One-Arm_Kettlebell_Row
  - id: seated-row
    name: { en: Seated Row, es: Remo sentado }
    variants:
      - source: Seated_Cable_Rows
      - source: Leverage_Iso_Row
  - id: t-bar-row
    name: { en: T-Bar Row, es: Remo en T }
    variants:
      - source: T-Bar_Row_with_Handle
      - source: Lying_T-Bar_Row
  - id: upright-row
    name: { en: Upright Row, es: Remo al mentón }
    variants:
      - source: Upright_Barbell_Row
      - source: Standing_Dumbbell_Upright_Row
      - source: Smith_Machine_Upright_Row
      - source: Upright_Cable_Row
      - source: Upright_Row_-_With_Bands
  - id: pull-up
    name: { en: Pull-Up, es: Dominada }
    variants:
      - source: Pullups
      - source: Band_Assisted_Pull-Up
        suffix: band-assisted
  - id: lat-pulldown
    name: { en: Lat Pulldown, es: Jalón al pecho }
    variants:
      - source: Wide-Grip_Lat_Pulldown
        suffix: wide-grip
      - source: Close-Grip_Front_Lat_Pulldown
        suffix: close-grip
      - source: V-Bar_Pulldown
        suffix: v-bar
  - id: biceps-curl
    name: { en: Biceps Curl, es: Curl de bíceps }
    variants:
      - source: Barbell_Curl
      - source: Dumbbell_Bicep_Curl
      - source: EZ-Bar_Curl
      - source: Standing_Biceps_Cable_Curl
      - source: Machine_Bicep_Curl
  - id: hammer-curl
    name: { en: Hammer Curl, es: Curl martillo }
    variants:
      - source: Alternate_Hammer_Curl
      - source: Cable_Hammer_Curls_-_Rope_Attachment
  - id: preacher-curl
    name: { en: Preacher Curl, es: Curl en banco Scott }
    variants:
      - source: Preacher_Curl
      - source: Two-Arm_Dumbbell_Preacher_Curl
      - source: Cable_Preacher_Curl
      - source: Machine_Preacher_Curls
  - id: triceps-pushdown
    name: { en: Triceps Pushdown, es: Extensión de tríceps en polea }
    variants:
      - source: Triceps_Pushdown
        suffix: bar
      - source: Triceps_Pushdown_-_Rope_Attachment
        suffix: rope
      - source: Triceps_Pushdown_-_V-Bar_Attachment
        suffix: v-bar
  - id: skull-crusher
    name: { en: Skull Crusher, es: Press francés }
    variants:
      - source: EZ-Bar_Skullcrusher
      - source: Cable_Lying_Triceps_Extension
      - source: Band_Skull_Crusher
  - id: overhead-triceps-extension
    name: { en: Overhead Triceps Extension, es: Extensión de tríceps por encima de la cabeza }
    variants:
      - source: Dumbbell_One-Arm_Triceps_Extension
      - source: Cable_Rope_Overhead_Triceps_Extension
      - source: Kettlebell_Overhead_Triceps_Extension
  - id: leg-press
    name: { en: Leg Press, es: Prensa de piernas }
    variants:
      - source: Leg_Press
      - source: Smith_Machine_Leg_Press
  - id: lunge
    name: { en: Lunge, es: Zancada }
    variants:
      - source: Barbell_Lunge
      - source: Dumbbell_Lunges
  - id: lateral-raise
    name: { en: Lateral Raise, es: Elevaciones laterales }
    variants:
      - source: Side_Lateral_Raise
      - source: Cable_Seated_Lateral_Raise
      - source: Lateral_Raise_-_With_Bands
  - id: chest-fly
    name: { en: Chest Fly, es: Aperturas }
    variants:
      - source: Dumbbell_Flyes
      - source: Flat_Bench_Cable_Flyes
  - id: rear-delt-fly
    name: { en: Rear Delt Fly, es: Pájaros }
    variants:
      - source: Reverse_Flyes
      - source: Cable_Rear_Delt_Fly
      - source: Reverse_Machine_Flyes
      - source: Back_Flyes_-_With_Bands
  - id: shrug
    name: { en: Shrug, es: Encogimientos de hombros }
    variants:
      - source: Barbell_Shrug
      - source: Dumbbell_Shrug
      - source: Cable_Shrugs
      - source: Leverage_Shrug
  - id: standing-calf-raise
    name: { en: Standing Calf Raise, es: Elevación de gemelos de pie }
    variants:
      - source: Standing_Calf_Raises
      - source: Standing_Barbell_Calf_Raise
      - source: Standing_Dumbbell_Calf_Raise
      - source: Smith_Machine_Calf_Raise
      - source: Calf_Raises_-_With_Bands
  - id: seated-calf-raise
    name: { en: Seated Calf Raise, es: Elevación de gemelos sentado }
    variants:
      - source: Seated_Calf_Raise
      - source: Barbell_Seated_Calf_Raise
  - id: leg-curl
    name: { en: Leg Curl, es: Curl femoral }
    variants:
      - source: Lying_Leg_Curls
        suffix: lying
      - source: Seated_Leg_Curl
        suffix: seated
      - source: Standing_Leg_Curl
        suffix: standing
  - id: running
    name: { en: Running, es: Correr }
    variants:
      - source: Jogging_Treadmill
        suffix: treadmill
      - suffix: outdoor
        equipment: NONE
```

- [ ] **Step 2: Build and fix structural errors**

```bash
node scripts/catalog/build.mjs 2>&1 | grep -v "missing name.es\|instructions.es" | head -30
```
Expected: only Spanish-related errors remain (filtered out above), i.e. the command prints `Catalogue invalid (N errors):` and nothing else. Fix any other error (unknown source id, load/tracking coherence) by correcting the group or adding a patch, and rerun.

- [ ] **Step 3: Commit**

```bash
git add catalog/overrides.yaml
git commit -m "feat(catalog): group the main movements into exercises with equipment variants"
```

### Task 7: Additional exercises

**Files:**
- Create: `catalog/custom.yaml`

- [ ] **Step 1: Write `catalog/custom.yaml`** (exercises the source lacks: outdoor endurance, calisthenics skills, sports)

```yaml
# Exercises not in free-exercise-db (spec §6.2 step 4). No media yet.
exercises:
  - id: swimming
    name: { en: Swimming, es: Natación }
    category: CARDIO
    trackingType: DISTANCE_TIME
    level: BEGINNER
    primaryMuscles: [FULL_BODY]
    secondaryMuscles: []
    instructions:
      en: [Swim at a steady pace, keeping a regular breathing rhythm., Log the distance and the total time.]
      es: [Nada a un ritmo constante manteniendo una respiración regular., Registra la distancia y el tiempo total.]
    variants:
      - { suffix: pool, equipment: NONE }
      - { suffix: open-water, equipment: NONE }
  - id: walking
    name: { en: Walking, es: Caminar }
    category: CARDIO
    trackingType: DISTANCE_TIME
    level: BEGINNER
    primaryMuscles: [FULL_BODY]
    secondaryMuscles: []
    instructions:
      en: [Walk at a brisk pace., Log the distance and the total time.]
      es: [Camina a paso ligero., Registra la distancia y el tiempo total.]
    variants:
      - { suffix: outdoor, equipment: NONE }
  - id: burpee
    name: { en: Burpee, es: Burpee }
    category: PLYOMETRICS
    trackingType: REPS
    level: BEGINNER
    primaryMuscles: [FULL_BODY]
    secondaryMuscles: []
    instructions:
      en: [From standing, squat and place your hands on the floor., Jump your feet back into a plank and do a push-up., Jump your feet back to your hands and jump up with your arms overhead.]
      es: [De pie, agáchate y apoya las manos en el suelo., Lleva los pies atrás de un salto hasta la plancha y haz una flexión., Vuelve a llevar los pies hacia las manos y salta con los brazos por encima de la cabeza.]
    variants:
      - { suffix: bodyweight, equipment: BODYWEIGHT, loadType: NONE }
  - id: pistol-squat
    name: { en: Pistol Squat, es: Sentadilla pistol }
    category: CALISTHENICS
    trackingType: BODYWEIGHT_REPS
    level: EXPERT
    primaryMuscles: [QUADRICEPS]
    secondaryMuscles: [GLUTES, HAMSTRINGS]
    instructions:
      en: [Stand on one leg with the other leg straight in front of you., Squat down on the standing leg as low as you can while keeping the heel down., Drive back up to standing.]
      es: [Apóyate sobre una pierna con la otra estirada al frente., Baja en sentadilla sobre la pierna de apoyo todo lo que puedas sin despegar el talón., Sube de nuevo hasta estar de pie.]
    variants:
      - { suffix: bodyweight, equipment: BODYWEIGHT }
  - id: l-sit
    name: { en: L-Sit, es: L-sit }
    category: CALISTHENICS
    trackingType: TIME
    level: INTERMEDIATE
    primaryMuscles: [ABDOMINALS]
    secondaryMuscles: [TRICEPS, QUADRICEPS]
    instructions:
      en: [Support yourself on parallel bars or the floor with straight arms., Lift your legs straight in front of you until they are parallel to the floor., Hold the position.]
      es: [Apóyate en paralelas o en el suelo con los brazos estirados., Eleva las piernas estiradas al frente hasta que queden paralelas al suelo., Mantén la posición.]
    variants:
      - { suffix: parallel-bars, equipment: BODYWEIGHT }
      - { suffix: floor, equipment: BODYWEIGHT }
  - id: handstand-hold
    name: { en: Handstand Hold, es: Pino }
    category: CALISTHENICS
    trackingType: TIME
    level: INTERMEDIATE
    primaryMuscles: [SHOULDERS]
    secondaryMuscles: [TRICEPS, ABDOMINALS]
    instructions:
      en: [Kick up into a handstand, against a wall or freestanding., Keep your body in a straight line with your arms locked., Hold the position.]
      es: [Sube al pino, contra la pared o libre., Mantén el cuerpo en línea recta con los brazos bloqueados., Mantén la posición.]
    variants:
      - { suffix: wall, equipment: BODYWEIGHT }
      - { suffix: freestanding, equipment: BODYWEIGHT }
  - id: hollow-body-hold
    name: { en: Hollow Body Hold, es: Hollow body }
    category: CALISTHENICS
    trackingType: TIME
    level: BEGINNER
    primaryMuscles: [ABDOMINALS]
    secondaryMuscles: []
    instructions:
      en: [Lie on your back with your arms overhead., Press your lower back into the floor and lift your shoulders and legs off the floor., Hold the position.]
      es: [Túmbate boca arriba con los brazos por encima de la cabeza., Pega la zona lumbar al suelo y despega los hombros y las piernas., Mantén la posición.]
    variants:
      - { suffix: bodyweight, equipment: BODYWEIGHT }
  - id: dead-hang
    name: { en: Dead Hang, es: Colgarse de la barra }
    category: CALISTHENICS
    trackingType: TIME
    level: BEGINNER
    primaryMuscles: [FOREARMS]
    secondaryMuscles: [LATS, SHOULDERS]
    instructions:
      en: [Grab a pull-up bar with an overhand grip., Hang with your arms straight and your shoulders active., Hold the position.]
      es: [Agarra la barra de dominadas en pronación., Cuélgate con los brazos estirados y los hombros activos., Mantén la posición.]
    variants:
      - { suffix: bodyweight, equipment: BODYWEIGHT }
  - id: front-lever-hold
    name: { en: Front Lever Hold, es: Front lever }
    category: CALISTHENICS
    trackingType: TIME
    level: EXPERT
    primaryMuscles: [LATS]
    secondaryMuscles: [ABDOMINALS, SHOULDERS]
    instructions:
      en: [Hang from a bar with straight arms., Pull your body up to horizontal, face up, keeping it straight., Hold the position (use a tuck or a band to scale it).]
      es: [Cuélgate de una barra con los brazos estirados., Eleva el cuerpo hasta la horizontal, boca arriba y en línea recta., Mantén la posición (encoge las piernas o usa una goma para facilitarlo).]
    variants:
      - { suffix: bodyweight, equipment: BODYWEIGHT }
      - { suffix: band-assisted, equipment: BAND }
  - id: football
    name: { en: Football, es: Fútbol }
    category: SPORT
    trackingType: TIME
    level: BEGINNER
    primaryMuscles: [FULL_BODY]
    secondaryMuscles: []
    instructions: { en: [Log the playing time.], es: [Registra el tiempo de juego.] }
    variants:
      - { suffix: match, equipment: NONE }
  - id: padel
    name: { en: Padel, es: Pádel }
    category: SPORT
    trackingType: TIME
    level: BEGINNER
    primaryMuscles: [FULL_BODY]
    secondaryMuscles: []
    instructions: { en: [Log the playing time.], es: [Registra el tiempo de juego.] }
    variants:
      - { suffix: match, equipment: NONE }
  - id: tennis
    name: { en: Tennis, es: Tenis }
    category: SPORT
    trackingType: TIME
    level: BEGINNER
    primaryMuscles: [FULL_BODY]
    secondaryMuscles: []
    instructions: { en: [Log the playing time.], es: [Registra el tiempo de juego.] }
    variants:
      - { suffix: match, equipment: NONE }
  - id: basketball
    name: { en: Basketball, es: Baloncesto }
    category: SPORT
    trackingType: TIME
    level: BEGINNER
    primaryMuscles: [FULL_BODY]
    secondaryMuscles: []
    instructions: { en: [Log the playing time.], es: [Registra el tiempo de juego.] }
    variants:
      - { suffix: match, equipment: NONE }
  - id: climbing
    name: { en: Climbing, es: Escalada }
    category: SPORT
    trackingType: TIME
    level: BEGINNER
    primaryMuscles: [FULL_BODY]
    secondaryMuscles: [FOREARMS, LATS]
    instructions: { en: [Log the climbing time.], es: [Registra el tiempo de escalada.] }
    variants:
      - { suffix: bouldering, equipment: NONE }
      - { suffix: rope, equipment: NONE }
```

- [ ] **Step 2: Build and check only Spanish errors from sources remain**

```bash
node scripts/catalog/build.mjs 2>&1 | grep -v "missing name.es\|instructions.es" | head -20
```
Expected: only the `Catalogue invalid (N errors):` header line.

- [ ] **Step 3: Commit**

```bash
git add catalog/custom.yaml
git commit -m "feat(catalog): swimming, walking, calisthenics skills and sports"
```

### Task 8: Spanish translations

**Files:**
- Create: `catalog/i18n/es.yaml`

The pipeline never invents Spanish: every source exercise that lacks it gets an entry in `catalog/i18n/es.yaml`, written by the executor (in batches, one subagent per batch if using subagents) and spot-checked by the owner in the PR.

Format (keys are source ids; `name` may be omitted when wger already provides a good one, `instructions` must have exactly as many steps as the English source):
```yaml
Barbell_Curl:
  name: Curl con barra
  instructions:
    - De pie, sujeta la barra con agarre supino a la anchura de los hombros y los codos pegados al cuerpo. Esta es la posición inicial.
    - Flexiona los codos y sube la barra contrayendo los bíceps hasta la altura de los hombros.
    - Mantén un segundo arriba y baja lentamente a la posición inicial.
    - Repite el número de repeticiones indicado.
```

Glossary (Spain gym Spanish; use consistently):

| English | Spanish |
|---|---|
| bench press | press de banca |
| squat / front squat | sentadilla / sentadilla frontal |
| deadlift / Romanian deadlift | peso muerto / peso muerto rumano |
| overhead / military press | press militar |
| row / bent-over row | remo / remo inclinado |
| pull-up / chin-up | dominada / dominada supina |
| lat pulldown | jalón al pecho |
| dip | fondo |
| push-up | flexión |
| lunge | zancada |
| curl | curl |
| triceps extension / pushdown | extensión de tríceps / extensión en polea |
| lateral raise / front raise | elevación lateral / elevación frontal |
| fly / reverse fly | apertura / pájaro |
| shrug | encogimiento de hombros |
| calf raise | elevación de gemelos |
| leg press / leg extension / leg curl | prensa / extensión de cuádriceps / curl femoral |
| hip thrust / glute bridge | hip thrust / puente de glúteos |
| plank | plancha |
| crunch / sit-up | encogimiento abdominal / abdominal |
| stretch | estiramiento |
| cable / pulley | polea |
| machine / Smith machine | máquina / multipower |
| barbell / dumbbell / kettlebell | barra / mancuerna / kettlebell |
| band | goma elástica |
| grip: overhand / underhand / neutral | agarre prono / supino / neutro |
| rep / set | repetición / serie |
| "This will be your starting position." | "Esta es la posición inicial." |

- [ ] **Step 1: List what is missing and split it into batches of 100**

```bash
node scripts/catalog/build.mjs --missing-es > "$TMP/missing-es.json"
node -e 'const a=require(process.argv[1]);console.log(a.length, "ids;", Math.ceil(a.length/100), "batches")' "$TMP/missing-es.json"
```

- [ ] **Step 2: Translate each batch**

For each batch of 100 ids: read the English `name` and `instructions` of each id from `catalog/sources/free-exercise-db.json`, write the Spanish entry into `catalog/i18n/es.yaml` (keep the file sorted by key; append batch by batch), following the glossary. Do not translate ids or change the number of instruction steps. After each batch:
```bash
node scripts/catalog/build.mjs --missing-es | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>console.log(JSON.parse(s).length, "left"))'
git add catalog/i18n/es.yaml
git commit -m "feat(catalog): Spanish translations, batch <n>"
```
Expected: the "left" count drops by about 100 per batch, ending at `0 left`.

- [ ] **Step 3: Generate the outputs**

```bash
node scripts/catalog/build.mjs
```
Expected: `catalog v1: <N> exercises, <M> variants, 0 deprecated ids` with N between 790 and 860.

- [ ] **Step 4: Commit the generated files**

```bash
git add composeApp/src/commonMain/composeResources/files/catalog.json catalog/ATTRIBUTION.md catalog/ids.lock.json
git commit -m "feat(catalog): generate catalogue v1"
```

### Task 9: CI job and catalogue docs

**Files:**
- Modify: `.github/workflows/ci.yml`
- Create: `catalog/README.md`

- [ ] **Step 1: Add the `catalog` job** to `.github/workflows/ci.yml` under `jobs:` (create the file with the header from F1a Task 7 if it does not exist yet):

```yaml
  catalog:
    name: Exercise catalogue (tests, up to date)
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-node@v6
        with:
          node-version: 22
          cache: npm
          cache-dependency-path: scripts/catalog/package-lock.json
      - run: npm ci --prefix scripts/catalog
      - run: npm test --prefix scripts/catalog
      - run: node scripts/catalog/build.mjs --check
```
The workflow's `paths-ignore` skips `**/*.md`; `catalog/*.yaml` and `catalog/sources/*.json` still trigger it.

- [ ] **Step 2: Write `catalog/README.md`**

```markdown
# Exercise catalogue

Source of the exercise catalogue bundled in the app (spec §6). The app reads the generated
`composeApp/src/commonMain/composeResources/files/catalog.json`; never edit that file by hand.

| File | Role |
|---|---|
| `sources/sources.json` | Pinned free-exercise-db commit and wger fetch date |
| `sources/free-exercise-db.json`, `sources/wger-es.json` | Snapshots refreshed by `node scripts/catalog/fetch.mjs` |
| `overrides.yaml` | `catalogVersion`, excluded sources, per-source patches, exercise groups (variants) |
| `custom.yaml` | Exercises that are not in the sources |
| `i18n/es.yaml` | Spanish names and instructions per source id |
| `ids.lock.json` | Every id ever emitted (ids are never reused; removed ones become `deprecatedIds`) |
| `ATTRIBUTION.md` | Generated credits, shown in the app |

Workflow:

    node scripts/catalog/build.mjs               # regenerate outputs
    node scripts/catalog/build.mjs --missing-es  # source ids still lacking Spanish
    node scripts/catalog/build.mjs --check       # what CI runs
    npm test --prefix scripts/catalog            # unit tests

Rules: ids are stable forever; once a version ships (`released: true`), any change to the generated
catalogue needs a `catalogVersion` bump (the build refuses otherwise); the pipeline never invents
Spanish: it comes from `i18n/es.yaml` or wger.
```

- [ ] **Step 3: Run the job locally**

```bash
npm ci --prefix scripts/catalog && npm test --prefix scripts/catalog && node scripts/catalog/build.mjs --check
```
Expected: tests pass and `Catalogue up to date: v1, <N> exercises`.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/ci.yml catalog/README.md
git commit -m "ci: run the catalogue tests and check the generated catalogue"
```

### Task 10: Spec update, PR and plan status

- [ ] **Step 1: Update the spec's equipment list** in `docs/designs/2026-10-14-ironlog-core-design.md` §3.1 (`ExerciseVariant.equipment` row) to: `BARBELL`, `EZ_BAR`, `TRAP_BAR`, `DUMBBELL`, `KETTLEBELL`, `SMITH`, `MACHINE`, `CABLE`, `BAND`, `BODYWEIGHT`, `MEDICINE_BALL`, `SWISS_BALL`, `FOAM_ROLLER`, `CARDIO_MACHINE`, `OTHER`, `NONE`; and in the `loadType` table, note that `DUMBBELL` covers any single implement (dumbbell, kettlebell, medicine ball). Add `level` (`BEGINNER`, `INTERMEDIATE`, `EXPERT`) to the `Exercise` table and `FULL_BODY` to the muscles. Commit: `docs: equipment, level and muscles as built in the catalogue`.

- [ ] **Step 2: Mark F1b as done** in `docs/plans/README.md`, note deviations at the end of this plan, commit `docs: mark F1b as done`.

- [ ] **Step 3: Push and open the PR**

```bash
git push -u origin f1b-catalog-pipeline
gh pr create --base main --title "F1b: exercise catalogue pipeline" --body "Implements docs/plans/2026-10-14-f1b-catalog-pipeline.md. Owner: please spot-check catalog/i18n/es.yaml."
```
Expected: the `catalog` job is green. Merge (merge commit) when CI is green and the owner has spot-checked the translations.

## Final verification of F1b

- `npm test --prefix scripts/catalog` passes.
- `node scripts/catalog/build.mjs --check` prints `Catalogue up to date`.
- `node scripts/catalog/build.mjs --missing-es` prints `[]`.
- `catalog.json` contains `bench-press` with variants `barbell`, `dumbbell`, `smith`, `machine`, and `running` with `treadmill` and `outdoor`.

## Deviations during execution

Tasks 1–5 were executed as written, then amended after code review. The committed code is the reference where it differs from the snippets above.

- `package.json`: the test script is `node --test "test/*.test.mjs"`, because Node 24 rejects a directory argument. `yaml` is pinned to 2.8.3 (2.8.1 has advisory GHSA-48c2-rrv3-qjmp).
- `.gitattributes` (`* text=auto eol=lf`) keeps generated outputs LF on every platform. `build.mjs` also normalises CRLF when it compares outputs in `--check`.
- `build-catalog.mjs`: exercises are sorted by code unit, not with `localeCompare`, so the order and `contentHash` do not depend on the locale. It throws on `patches` or `exclude` entries that name unknown sources, on a source used twice in groups, and on a source that is both excluded and grouped. `equipmentOf(source, category)` takes the patched category for the null-equipment default.
- `validate.mjs`: malformed entries are reported, not thrown: non-array muscles or variants, and exercise `instructions` that are not `{ en: string[], es: string[] }`. Any mismatch between `es` and `en` step counts is an error, including Spanish steps with no English.
- `build.mjs`: a missing required input (`sources.json`, `free-exercise-db.json`) fails with "missing <file>". A leading UTF-8 BOM is stripped before parsing JSON or YAML. A write prints the newly deprecated ids.
- `lib/text.mjs` exports `stripDiacritics` (`/[\u0300-\u036f]/g`), shared by `slug.mjs` and `wger.mjs`. `indexWger` sorts by wger id itself, so the lowest id wins whatever the input order.
- `attribution.mjs` escapes `|` and flattens line breaks in table cells.
- `fetch.mjs` fetches everything before writing any file, uses a 30 s timeout per request, checks that the payloads are arrays, and sends a User-Agent without a personal email.
- Extra tests: `test/wger.test.mjs`, `test/attribution.test.mjs`, plus error paths and edge cases in the mapping, builder and validation suites.
