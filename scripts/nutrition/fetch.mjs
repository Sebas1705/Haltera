// Refreshes the committed USDA snapshot nutrition/sources/usda-foods.json from the pinned downloads in
// nutrition/sources/sources.json. Needs network; `build` does not. Each archive is checked against its pinned
// SHA-256 and the snapshot is written at the end, so a failed run leaves it untouched.
import { createHash } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { unzip } from './lib/unzip.mjs';
import { reduceDataset } from './lib/usda.mjs';

const root = fileURLToPath(new URL('../../nutrition/sources/', import.meta.url));
const userAgent = 'Ironlog-nutrition/1.0 (+https://github.com/Sebas1705/Ironlog)';
const sources = JSON.parse(await readFile(root + 'sources.json', 'utf8'));

const foods = [];
for (const dataset of sources.usda.datasets) {
  const response = await fetch(dataset.url, {
    headers: { 'User-Agent': userAgent },
    signal: AbortSignal.timeout(300_000),
  });
  if (!response.ok) throw new Error(`${response.status} ${response.statusText} for ${dataset.url}`);
  const zip = Buffer.from(await response.arrayBuffer());
  const sha256 = createHash('sha256').update(zip).digest('hex');
  if (sha256 !== dataset.sha256) throw new Error(`${dataset.name}: sha256 ${sha256} is not the pinned ${dataset.sha256}`);
  const [entry] = unzip(zip).filter((e) => e.name.endsWith('.json'));
  if (!entry) throw new Error(`${dataset.name}: no JSON file in the archive`);
  const reduced = reduceDataset(JSON.parse(entry.data.toString('utf8')));
  console.log(`${dataset.name}: ${reduced.length} foods`);
  foods.push(...reduced);
}
foods.sort((a, b) => a.fdcId - b.fdcId);
// One food per line: small diffs when the pinned datasets change.
await writeFile(root + 'usda-foods.json', '[\n' + foods.map((f) => JSON.stringify(f)).join(',\n') + '\n]\n');
console.log(`usda-foods.json: ${foods.length} foods`);
