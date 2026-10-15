// Offline foods build. Usage:
//   node scripts/nutrition/build.mjs              write foods.json, ATTRIBUTION.md (also bundled in the app) and ids.lock.json
//   node scripts/nutrition/build.mjs --check      fail if the committed outputs are stale (CI)
//   node scripts/nutrition/build.mjs --missing-es print the foods that still need a Spanish name
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parse } from 'yaml';
import { buildFoods } from './lib/build-foods.mjs';
import { validateFoods } from './lib/validate.mjs';
import { renderAttribution } from './lib/attribution.mjs';

const repo = fileURLToPath(new URL('../../', import.meta.url));
const path = (p) => repo + p;
const OUT_FOODS = 'composeApp/src/commonMain/composeResources/files/foods.json';
const OUT_ATTRIBUTION = 'nutrition/ATTRIBUTION.md';
// Same text bundled in the app for Settings → Credits.
const OUT_ATTRIBUTION_APP = 'composeApp/src/commonMain/composeResources/files/FOODS_ATTRIBUTION.md';
const OUT_LOCK = 'nutrition/ids.lock.json';

/** Reads a UTF-8 file without a leading BOM; a missing file returns `fallback`, or fails when there is none. */
async function readSource(p, fallback) {
  if (!existsSync(path(p))) {
    if (fallback === undefined) throw new Error(`missing ${p}`);
    return null;
  }
  return (await readFile(path(p), 'utf8')).replace(/^﻿/, '');
}
const readJson = async (p, fallback) => {
  const text = await readSource(p, fallback);
  return text === null ? fallback : JSON.parse(text);
};
const readYaml = async (p, fallback) => {
  const text = await readSource(p, fallback);
  return text === null ? fallback : (parse(text) ?? fallback);
};
/** Committed outputs, with CRLF normalised so a Windows checkout does not look stale. */
const readText = async (p) => {
  const text = await readSource(p, null);
  return text === null ? null : text.replace(/\r\n/g, '\n');
};

const sources = await readJson('nutrition/sources/sources.json');
const selection = await readYaml('nutrition/foods.yaml');
const result = buildFoods({
  usda: await readJson('nutrition/sources/usda-foods.json'),
  selection,
  es: await readYaml('nutrition/i18n/es.yaml', {}),
  lockedIds: await readJson(OUT_LOCK, []),
});

if (process.argv.includes('--missing-es')) {
  console.log(JSON.stringify(result.missingEs, null, 1));
  process.exit(0);
}

const errors = validateFoods(result.foods);
if (errors.length > 0) {
  console.error(`Foods invalid (${errors.length} errors):\n` + errors.slice(0, 50).join('\n'));
  process.exit(1);
}

const attribution = renderAttribution({ sources, count: result.foods.foods.length });
const outputs = {
  // One food per line: readable diffs.
  [OUT_FOODS]: JSON.stringify({ ...result.foods, foods: undefined }).replace(/}$/, ',"foods":[\n') +
    result.foods.foods.map((f) => JSON.stringify(f)).join(',\n') + '\n]}\n',
  [OUT_ATTRIBUTION]: attribution,
  [OUT_ATTRIBUTION_APP]: attribution,
  [OUT_LOCK]: JSON.stringify(result.lockedIds, null, 1) + '\n',
};

if (process.argv.includes('--check')) {
  const stale = [];
  for (const [p, content] of Object.entries(outputs)) if ((await readText(p)) !== content) stale.push(p);
  if (stale.length > 0) {
    console.error(`Stale foods outputs: ${stale.join(', ')}. Run: node scripts/nutrition/build.mjs`);
    process.exit(1);
  }
  console.log(`Foods up to date: v${result.foods.foodsVersion}, ${result.foods.foods.length} foods`);
  process.exit(0);
}

// Once a foods version has shipped (`released: true`), its content is frozen: changes need a new version.
const previous = await readJson(OUT_FOODS, null);
if (selection.released && previous && previous.foodsVersion === result.foods.foodsVersion && previous.contentHash !== result.foods.contentHash) {
  console.error(`Foods v${previous.foodsVersion} is released: bump foodsVersion and set released: false in nutrition/foods.yaml.`);
  process.exit(1);
}
for (const [p, content] of Object.entries(outputs)) {
  await mkdir(dirname(path(p)), { recursive: true });
  await writeFile(path(p), content);
}
console.log(`foods v${result.foods.foodsVersion}: ${result.foods.foods.length} foods, ${result.foods.deprecatedIds.length} deprecated ids`);
