// Pure catalogue builder: sources + curation -> catalog.json content (spec §6.2).
import { createHash } from 'node:crypto';
import { categoryOf, equipmentOf, levelOf, loadTypeOf, musclesOf, trackingOf } from './mapping.mjs';
import { slug } from './slug.mjs';
import { indexWger, nameKey } from './wger.mjs';

/** Locale-independent ordering by code unit, so the output and its hash are the same on every machine. */
const byId = (a, b) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0);

/** Curation entries must list their variants; fail with a readable message instead of a TypeError. */
function requireVariants(where, entry) {
  if (!Array.isArray(entry?.variants) || entry.variants.length === 0) throw new Error(`${where}: variants must be a non-empty list`);
}

/** A source exercise after mappings and patches, before grouping. */
function prepare(source, patch = {}) {
  const category = patch.category ?? categoryOf(source);
  const equipment = patch.equipment ?? equipmentOf(source, category);
  return {
    sourceId: source.id,
    nameEn: source.name,
    category,
    equipment,
    loadType: patch.loadType ?? loadTypeOf(equipment),
    trackingType: patch.trackingType ?? trackingOf(category, equipment, source.force),
    level: levelOf(source.level),
    primaryMuscles: patch.primaryMuscles ?? musclesOf(source.primaryMuscles),
    secondaryMuscles: patch.secondaryMuscles ?? musclesOf(source.secondaryMuscles),
    instructionsEn: source.instructions,
    frames: source.images,
  };
}

export function buildCatalog({ sources, wger, overrides, custom, es, lockedIds }) {
  const wgerIndex = indexWger(wger);
  const usedWger = new Map();
  const missingEs = new Set();
  const excluded = new Set(overrides.exclude ?? []);
  const sourceIds = new Set(sources.map((s) => s.id));
  for (const id of Object.keys(overrides.patches ?? {})) {
    if (!sourceIds.has(id)) throw new Error(`patches: unknown source "${id}"`);
  }
  for (const id of excluded) {
    if (!sourceIds.has(id)) throw new Error(`exclude: unknown source "${id}"`);
  }
  const prepared = new Map(
    sources.filter((s) => !excluded.has(s.id)).map((s) => [s.id, prepare(s, overrides.patches?.[s.id])]),
  );

  const spanishName = (p) => {
    const own = es[p.sourceId]?.name;
    if (own) return own;
    const match = wgerIndex.get(nameKey(p.nameEn));
    if (match) {
      usedWger.set(match.wgerId, match);
      return match.es;
    }
    missingEs.add(p.sourceId);
    return '';
  };
  const spanishInstructions = (p) => {
    const own = es[p.sourceId]?.instructions;
    if (own?.length || p.instructionsEn.length === 0) return own ?? [];
    missingEs.add(p.sourceId);
    return [];
  };
  const instructionsOf = (p) => ({ en: p.instructionsEn, es: spanishInstructions(p) });

  const exercises = [];
  const grouped = new Set();

  for (const group of overrides.groups ?? []) {
    requireVariants(`Group ${group?.id}`, group);
    const variants = group.variants.map((v) => {
      if (!v.source) {
        return { id: `${group.id}.${v.suffix}`, equipment: v.equipment, loadType: v.loadType ?? loadTypeOf(v.equipment), name: v.name ?? null, instructions: null, frames: [] };
      }
      if (excluded.has(v.source)) throw new Error(`Group ${group.id}: source "${v.source}" is excluded`);
      const p = prepared.get(v.source);
      if (!p) throw new Error(`Group ${group.id}: unknown source "${v.source}"`);
      if (grouped.has(v.source)) throw new Error(`Group ${group.id}: source "${v.source}" is already grouped`);
      grouped.add(v.source);
      const equipment = v.equipment ?? p.equipment;
      return {
        id: `${group.id}.${v.suffix ?? slug(equipment)}`,
        equipment,
        loadType: v.loadType ?? (v.equipment ? loadTypeOf(v.equipment) : p.loadType),
        name: v.name ?? null,
        instructions: instructionsOf(p),
        frames: p.frames,
      };
    });
    const primary = prepared.get(group.variants.find((v) => v.source)?.source);
    if (!primary) throw new Error(`Group ${group.id}: needs at least one source variant`);
    exercises.push({
      id: group.id,
      name: group.name,
      category: group.category ?? primary.category,
      trackingType: group.trackingType ?? primary.trackingType,
      level: group.level ?? primary.level,
      primaryMuscles: group.primaryMuscles ?? primary.primaryMuscles,
      secondaryMuscles: group.secondaryMuscles ?? primary.secondaryMuscles,
      instructions: instructionsOf(primary),
      variants,
    });
  }

  for (const p of prepared.values()) {
    if (grouped.has(p.sourceId)) continue;
    const id = slug(p.sourceId);
    exercises.push({
      id,
      name: { en: p.nameEn, es: spanishName(p) },
      category: p.category,
      trackingType: p.trackingType,
      level: p.level,
      primaryMuscles: p.primaryMuscles,
      secondaryMuscles: p.secondaryMuscles,
      instructions: instructionsOf(p),
      variants: [{ id: `${id}.${slug(p.equipment)}`, equipment: p.equipment, loadType: p.loadType, name: null, instructions: null, frames: p.frames }],
    });
  }

  for (const c of custom.exercises ?? []) {
    requireVariants(`Custom exercise ${c?.id}`, c);
    exercises.push({
      id: c.id,
      name: c.name,
      category: c.category,
      trackingType: c.trackingType,
      level: c.level,
      primaryMuscles: c.primaryMuscles,
      secondaryMuscles: c.secondaryMuscles,
      instructions: c.instructions,
      variants: c.variants.map((v) => ({
        id: `${c.id}.${v.suffix}`, equipment: v.equipment, loadType: v.loadType ?? loadTypeOf(v.equipment), name: v.name ?? null, instructions: null, frames: [],
      })),
    });
  }

  exercises.sort(byId);
  const liveIds = exercises.flatMap((e) => [e.id, ...e.variants.map((v) => v.id)]);
  const live = new Set(liveIds);
  const deprecatedIds = lockedIds.filter((id) => !live.has(id)).sort();
  const contentHash = createHash('sha256').update(JSON.stringify({ exercises, deprecatedIds })).digest('hex').slice(0, 16);

  return {
    catalog: { catalogVersion: overrides.catalogVersion, contentHash, exercises, deprecatedIds },
    lockedIds: [...new Set([...lockedIds, ...liveIds])].sort(),
    missingEs: [...missingEs].sort(),
    usedWger: [...usedWger.values()].sort((a, b) => a.wgerId - b.wgerId),
  };
}
