// Refreshes the committed source snapshots under catalog/sources/. Needs network; `build` does not.
import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { reduceWger } from './lib/wger.mjs';

const root = fileURLToPath(new URL('../../catalog/sources/', import.meta.url));
const userAgent = 'Ironlog-catalog/1.0 (sebssgarcia502580@gmail.com)';

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
