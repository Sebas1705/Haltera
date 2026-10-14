import { test } from 'node:test';
import assert from 'node:assert/strict';
import { renderAttribution } from '../lib/attribution.mjs';

const sources = { freeExerciseDb: { repo: 'yuhonas/free-exercise-db', commit: 'abc', license: 'Unlicense' } };

test('pipes in wger names do not break the table', () => {
  const usedWger = [{ wgerId: 7, en: 'Push-Ups | Incline', es: 'Flexiones | inclinadas', license: 'CC-BY-SA 4', author: 'a|b' }];
  const row = renderAttribution({ sources, usedWger }).split('\n').find((l) => l.startsWith('| [7]'));
  assert.equal(row, '| [7](https://wger.de/exercise/7/view/) | Push-Ups \\| Incline | Flexiones \\| inclinadas | CC-BY-SA 4 | a\\|b |');
});
