import { test } from 'node:test';
import assert from 'node:assert/strict';
import { renderAttribution } from '../lib/attribution.mjs';

const sources = {
  usda: {
    name: 'USDA FoodData Central',
    homepage: 'https://fdc.nal.usda.gov/',
    license: 'CC0 1.0 Universal',
    citation: 'U.S. Department of Agriculture. FoodData Central',
    datasets: [{ name: 'SR Legacy 2018-04' }, { name: 'Foundation Foods 2026-04-30' }],
  },
};

test('credits USDA (CC0) with the datasets and Open Food Facts (ODbL)', () => {
  const text = renderAttribution({ sources, count: 705 });
  assert.ok(text.includes('705 bundled generic foods'));
  assert.ok(text.includes('CC0 1.0 Universal'));
  assert.ok(text.includes('- SR Legacy 2018-04'));
  assert.ok(text.includes('- Foundation Foods 2026-04-30'));
  assert.ok(text.includes('Open Database License (ODbL 1.0)'));
});
