import { test } from 'node:test';
import assert from 'node:assert/strict';
import { deflateRawSync } from 'node:zlib';
import { reduceDataset, reduceFood } from '../lib/usda.mjs';
import { unzip } from '../lib/unzip.mjs';

const nutrient = (number, amount, unitName = 'g') => ({ nutrient: { number, unitName }, amount });

test('reduces a food to the kept nutrients per 100 g', () => {
  const food = {
    fdcId: 7,
    description: 'Egg, whole, raw',
    foodCategory: { description: 'Dairy and Egg Products' },
    foodNutrients: [
      nutrient('208', 143.4, 'kcal'),
      nutrient('208', 600, 'kJ'),
      nutrient('203', 12.564),
      nutrient('204', 9.51),
      nutrient('205', 0.72),
      nutrient('307', 142, 'mg'),
    ],
  };
  assert.deepEqual(reduceFood(food, 'sr_legacy'), {
    fdcId: 7,
    dataType: 'sr_legacy',
    description: 'Egg, whole, raw',
    category: 'Dairy and Egg Products',
    kcal: 143,
    protein: 12.56,
    fat: 9.51,
    carbs: 0.72,
    fiber: null,
    sugar: null,
    sodium: 142,
  });
});

test('falls back to the Atwater energy and NLEA fat when the main rows are missing', () => {
  const food = { fdcId: 1, description: 'x', foodNutrients: [nutrient('958', 99, 'kcal'), nutrient('298', 3.3)] };
  const reduced = reduceFood(food, 'foundation');
  assert.equal(reduced.kcal, 99);
  assert.equal(reduced.fat, 3.3);
});

test('reads both dataset shapes and skips null entries', () => {
  const foods = reduceDataset({ SRLegacyFoods: [null, { fdcId: 2, description: 'y', foodNutrients: [] }] });
  assert.deepEqual(foods.map((f) => [f.fdcId, f.dataType]), [[2, 'sr_legacy']]);
  assert.throws(() => reduceDataset({}), /no FoundationFoods or SRLegacyFoods/);
});

/** A one-file ZIP archive with a deflated entry. */
function zipOf(name, text) {
  const data = Buffer.from(text);
  const compressed = deflateRawSync(data);
  const nameBytes = Buffer.from(name);
  const local = Buffer.alloc(30);
  local.writeUInt32LE(0x04034b50, 0);
  local.writeUInt16LE(8, 8);
  local.writeUInt32LE(compressed.length, 18);
  local.writeUInt32LE(data.length, 22);
  local.writeUInt16LE(nameBytes.length, 26);
  const central = Buffer.alloc(46);
  central.writeUInt32LE(0x02014b50, 0);
  central.writeUInt16LE(8, 10);
  central.writeUInt32LE(compressed.length, 20);
  central.writeUInt32LE(data.length, 24);
  central.writeUInt16LE(nameBytes.length, 28);
  central.writeUInt32LE(0, 42);
  const centralOffset = local.length + nameBytes.length + compressed.length;
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(1, 8);
  end.writeUInt16LE(1, 10);
  end.writeUInt32LE(central.length + nameBytes.length, 12);
  end.writeUInt32LE(centralOffset, 16);
  return Buffer.concat([local, nameBytes, compressed, central, nameBytes, end]);
}

test('unzips a deflated entry', () => {
  const [entry] = unzip(zipOf('foods.json', '{"SRLegacyFoods":[]}'));
  assert.equal(entry.name, 'foods.json');
  assert.equal(entry.data.toString(), '{"SRLegacyFoods":[]}');
});

test('rejects data that is not a zip archive', () => {
  assert.throws(() => unzip(Buffer.from('not a zip at all, just some text bytes')), /not a zip archive/);
});
