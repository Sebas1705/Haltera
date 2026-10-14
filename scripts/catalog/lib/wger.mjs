// Reduces wger exerciseinfo results to the English/Spanish name pairs we use, with their licence.

const ENGLISH = 2;
const SPANISH = 4;

/** Normalised key used to match names across sources. */
export function nameKey(name) {
  return name.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9]/g, '');
}

/** One entry per wger exercise that has both an English and a Spanish translation. */
export function reduceWger(results) {
  const entries = [];
  for (const exercise of results) {
    const en = exercise.translations.find((t) => t.language === ENGLISH);
    const es = exercise.translations.find((t) => t.language === SPANISH);
    if (!en || !es || !es.name.trim()) continue;
    entries.push({
      wgerId: exercise.id,
      en: en.name.trim(),
      enAliases: (en.aliases ?? []).map((a) => (typeof a === 'string' ? a : a.alias)).filter(Boolean),
      es: es.name.trim(),
      license: es.license_title || exercise.license?.short_name || 'CC-BY-SA',
      author: es.license_author || exercise.license_author || 'wger.de',
    });
  }
  return entries.sort((a, b) => a.wgerId - b.wgerId);
}

/** Map nameKey -> entry over English names and aliases; the lowest wger id wins on collisions. */
export function indexWger(entries) {
  const index = new Map();
  for (const entry of entries) {
    for (const name of [entry.en, ...entry.enAliases]) {
      const key = nameKey(name);
      if (key && !index.has(key)) index.set(key, entry);
    }
  }
  return index;
}
