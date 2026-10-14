// Catalogue validation (spec §6.2). Returns a list of human-readable errors; empty means valid.
// Malformed entries are reported, never thrown, so one bad curation line does not hide the others.
import { ALLOWED_LOAD_TYPES, CATEGORIES, EQUIPMENT, LEVELS, LOAD_TYPES, MUSCLES, TRACKING_TYPES } from './mapping.mjs';

const KEBAB = /^[a-z0-9]+(-[a-z0-9]+)*$/;

const isObject = (value) => value != null && typeof value === 'object' && !Array.isArray(value);
const isText = (value) => typeof value === 'string' && value.trim() !== '';
const isStringArray = (value) => Array.isArray(value) && value.every((s) => typeof s === 'string');

/** Exercise instructions are required; variant instructions may be null (they inherit the exercise's). */
function checkInstructions(where, instructions, errors, { required }) {
  if (instructions == null && !required) return;
  if (instructions == null || !isStringArray(instructions.en) || !isStringArray(instructions.es)) {
    errors.push(`${where}: instructions must be { en: string[], es: string[] }`);
    return;
  }
  if (instructions.es.length !== instructions.en.length) {
    errors.push(`${where}: instructions.es has ${instructions.es.length} steps, instructions.en has ${instructions.en.length}`);
  }
}

function checkMuscles(where, field, list, errors) {
  if (!Array.isArray(list)) {
    errors.push(`${where}: ${field} must be an array`);
    return [];
  }
  for (const m of list) {
    if (!MUSCLES.includes(m)) errors.push(`${where}: invalid muscle ${m}`);
  }
  return list;
}

/** Variants that share equipment are told apart only by their names, so those names must exist and differ. */
function checkVariantNames(e, errors) {
  const variants = e.variants.filter(isObject);
  const byEquipment = new Map();
  for (const v of variants) byEquipment.set(v.equipment, [...(byEquipment.get(v.equipment) ?? []), v]);
  for (const [equipment, list] of byEquipment) {
    if (list.length < 2) continue;
    for (const v of list) if (v.name == null) errors.push(`${v.id}: variants sharing ${equipment} need a name`);
  }
  for (const lang of ['en', 'es']) {
    const seen = new Set();
    for (const v of variants) {
      const name = v.name?.[lang];
      if (typeof name !== 'string') continue;
      if (seen.has(name)) errors.push(`${e.id}: duplicate variant name.${lang} "${name}"`);
      seen.add(name);
    }
  }
}

export function validateCatalog(catalog) {
  const errors = [];
  if (!Number.isInteger(catalog.catalogVersion) || catalog.catalogVersion < 1) errors.push('catalogVersion must be a positive integer');
  if (!Array.isArray(catalog.exercises)) return [...errors, 'exercises must be an array'];
  const seen = new Set();
  const unique = (id) => {
    if (seen.has(id)) errors.push(`duplicate id ${id}`);
    seen.add(id);
  };

  for (const e of catalog.exercises) {
    if (!isObject(e)) {
      errors.push(`exercise entry must be an object, got ${JSON.stringify(e)}`);
      continue;
    }
    unique(e.id);
    if (typeof e.id !== 'string' || !KEBAB.test(e.id)) errors.push(`${e.id}: exercise id is not kebab-case`);
    if (!isText(e.name?.en)) errors.push(`${e.id}: missing name.en`);
    if (!isText(e.name?.es)) errors.push(`${e.id}: missing name.es`);
    if (!CATEGORIES.includes(e.category)) errors.push(`${e.id}: invalid category ${e.category}`);
    if (!TRACKING_TYPES.includes(e.trackingType)) errors.push(`${e.id}: invalid trackingType ${e.trackingType}`);
    if (!LEVELS.includes(e.level)) errors.push(`${e.id}: invalid level ${e.level}`);
    const primary = checkMuscles(e.id, 'primaryMuscles', e.primaryMuscles, errors);
    if (Array.isArray(e.primaryMuscles) && primary.length === 0) errors.push(`${e.id}: no primary muscles`);
    checkMuscles(e.id, 'secondaryMuscles', e.secondaryMuscles, errors);
    checkInstructions(e.id, e.instructions, errors, { required: true });
    if (!Array.isArray(e.variants)) {
      errors.push(`${e.id}: variants must be an array`);
      continue;
    }
    if (e.variants.length === 0) errors.push(`${e.id}: no variants`);

    for (const v of e.variants) {
      if (!isObject(v)) {
        errors.push(`${e.id}: variant entry must be an object, got ${JSON.stringify(v)}`);
        continue;
      }
      unique(v.id);
      const [prefix, suffix, extra] = String(v.id).split('.');
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
      checkInstructions(v.id, v.instructions, errors, { required: false });
      if (v.name != null && !(isText(v.name.en) && isText(v.name.es))) errors.push(`${v.id}: variant name needs en and es`);
    }
    checkVariantNames(e, errors);
  }
  return errors;
}
