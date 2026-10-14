import { test } from 'node:test';
import assert from 'node:assert/strict';
import { indexWger, nameKey, reduceWger } from '../lib/wger.mjs';

const t = (language, name, over = {}) => ({ language, name, ...over });

test('reduceWger keeps only exercises with English and a non-blank Spanish name', () => {
  const entries = reduceWger([
    { id: 3, translations: [t(2, 'Squat'), t(4, 'Sentadilla', { license_title: 'CC-BY-SA 4', license_author: 'ana' })] },
    { id: 1, translations: [t(2, 'Curl')] },
    { id: 2, translations: [t(2, 'Row'), t(4, '   ')] },
    { id: 4, translations: [t(4, 'Solo')] },
  ]);
  assert.deepEqual(entries, [{ wgerId: 3, en: 'Squat', enAliases: [], es: 'Sentadilla', license: 'CC-BY-SA 4', author: 'ana' }]);
});

test('reduceWger accepts aliases as strings or objects', () => {
  const [entry] = reduceWger([{ id: 1, translations: [t(2, 'Squat', { aliases: ['Back Squat', { alias: 'Barbell Squat' }, { alias: '' }] }), t(4, 'Sentadilla')] }]);
  assert.deepEqual(entry.enAliases, ['Back Squat', 'Barbell Squat']);
});

const entry = (wgerId, en, enAliases = []) => ({ wgerId, en, enAliases, es: `es ${wgerId}`, license: 'CC0', author: 'x' });

test('indexWger matches aliases and the lowest id wins', () => {
  const index = indexWger([entry(5, 'Bench Press'), entry(9, 'Squat', ['Back Squat']), entry(2, 'Bench-Press')]);
  assert.equal(index.get(nameKey('bench press')).wgerId, 2);
  assert.equal(index.get(nameKey('Back Squat')).wgerId, 9);
});
