// Offline catalogue build. Usage:
//   node scripts/catalog/build.mjs            write catalog.json, ATTRIBUTION.md (also bundled in the app) and ids.lock.json
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
// Same text bundled in the app for Settings → Credits.
const OUT_ATTRIBUTION_APP = 'composeApp/src/commonMain/composeResources/files/ATTRIBUTION.md';
const OUT_LOCK = 'catalog/ids.lock.json';

/** Reads a UTF-8 file without a leading BOM. A missing file returns `fallback`, or fails when there is none (required input). */
async function readSource(p, fallback) {
  if (!existsSync(path(p))) {
    if (fallback === undefined) throw new Error(`missing ${p}`);
    return null;
  }
  return (await readFile(path(p), 'utf8')).replace(/^\uFEFF/, '');
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
  [OUT_ATTRIBUTION_APP]: renderAttribution({ sources, usedWger: result.usedWger }),
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
// Ids that are deprecated now but were not yet deprecated in the previous catalog.json (i.e. they were live there).
const previouslyDeprecated = new Set(previous?.deprecatedIds ?? []);
const newlyDeprecated = result.catalog.deprecatedIds.filter((id) => !previouslyDeprecated.has(id));
if (newlyDeprecated.length > 0) console.log(`Newly deprecated ids (${newlyDeprecated.length}):\n  ${newlyDeprecated.join('\n  ')}`);
