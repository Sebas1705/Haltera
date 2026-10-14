// Refreshes the committed source snapshots under catalog/sources/. Needs network; `build` does not.
// Everything is fetched first and written at the end, so a failed run leaves the snapshots untouched.
import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { reduceWger } from './lib/wger.mjs';

const root = fileURLToPath(new URL('../../catalog/sources/', import.meta.url));
const userAgent = 'Ironlog-catalog/1.0 (+https://github.com/Sebas1705/Ironlog)';

async function getJson(url) {
  const response = await fetch(url, {
    headers: { 'User-Agent': userAgent, Accept: 'application/json' },
    signal: AbortSignal.timeout(30_000),
  });
  if (!response.ok) throw new Error(`${response.status} ${response.statusText} for ${url}`);
  return response.json();
}

const sources = JSON.parse(await readFile(root + 'sources.json', 'utf8'));

const { repo, commit } = sources.freeExerciseDb;
const exercises = await getJson(`https://raw.githubusercontent.com/${repo}/${commit}/dist/exercises.json`);
if (!Array.isArray(exercises)) throw new Error('free-exercise-db: expected an array of exercises');
console.log(`free-exercise-db@${commit.slice(0, 7)}: ${exercises.length} exercises`);

const results = [];
for (let url = `${sources.wger.api}?limit=100&offset=0`; url; ) {
  const page = await getJson(url);
  if (!Array.isArray(page.results)) throw new Error(`wger: expected a results array at ${url}`);
  results.push(...page.results);
  url = page.next;
}
const wger = reduceWger(results);
console.log(`wger: ${results.length} exercises, ${wger.length} with English and Spanish names`);

sources.wger.fetchedAt = new Date().toISOString().slice(0, 10);
await writeFile(root + 'free-exercise-db.json', JSON.stringify(exercises, null, 1) + '\n');
await writeFile(root + 'wger-es.json', JSON.stringify(wger, null, 1) + '\n');
await writeFile(root + 'sources.json', JSON.stringify(sources, null, 2) + '\n');
