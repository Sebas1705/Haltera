import { stripDiacritics } from './text.mjs';

/** Kebab-case id from a source id or name: "Barbell_Bench_Press_-_Medium_Grip" -> "barbell-bench-press-medium-grip". */
export function slug(text) {
  return stripDiacritics(text.toLowerCase())
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}
