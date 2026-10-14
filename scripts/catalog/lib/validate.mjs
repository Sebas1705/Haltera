// Catalogue validation (spec §6.2). Returns a list of human-readable errors; empty means valid.
import { ALLOWED_LOAD_TYPES, CATEGORIES, EQUIPMENT, LEVELS, LOAD_TYPES, MUSCLES, TRACKING_TYPES } from './mapping.mjs';

const KEBAB = /^[a-z0-9]+(-[a-z0-9]+)*$/;

function checkInstructions(where, instructions, errors) {
  if (instructions == null) return;
  if (instructions.en.length > 0 && instructions.es.length !== instructions.en.length) {
    errors.push(`${where}: instructions.es has ${instructions.es.length} steps, instructions.en has ${instructions.en.length}`);
  }
}

export function validateCatalog(catalog) {
  const errors = [];
  if (!Number.isInteger(catalog.catalogVersion) || catalog.catalogVersion < 1) errors.push('catalogVersion must be a positive integer');
  const seen = new Set();
  const unique = (id) => {
    if (seen.has(id)) errors.push(`duplicate id ${id}`);
    seen.add(id);
  };

  for (const e of catalog.exercises) {
    unique(e.id);
    if (!KEBAB.test(e.id)) errors.push(`${e.id}: exercise id is not kebab-case`);
    if (!e.name?.en?.trim()) errors.push(`${e.id}: missing name.en`);
    if (!e.name?.es?.trim()) errors.push(`${e.id}: missing name.es`);
    if (!CATEGORIES.includes(e.category)) errors.push(`${e.id}: invalid category ${e.category}`);
    if (!TRACKING_TYPES.includes(e.trackingType)) errors.push(`${e.id}: invalid trackingType ${e.trackingType}`);
    if (!LEVELS.includes(e.level)) errors.push(`${e.id}: invalid level ${e.level}`);
    if (e.primaryMuscles.length === 0) errors.push(`${e.id}: no primary muscles`);
    for (const m of [...e.primaryMuscles, ...e.secondaryMuscles]) {
      if (!MUSCLES.includes(m)) errors.push(`${e.id}: invalid muscle ${m}`);
    }
    checkInstructions(e.id, e.instructions, errors);
    if (e.variants.length === 0) errors.push(`${e.id}: no variants`);

    for (const v of e.variants) {
      unique(v.id);
      const [prefix, suffix, extra] = v.id.split('.');
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
      checkInstructions(v.id, v.instructions, errors);
    }
  }
  return errors;
}
