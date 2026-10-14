/** Kebab-case id from a source id or name: "Barbell_Bench_Press_-_Medium_Grip" -> "barbell-bench-press-medium-grip". */
export function slug(text) {
  return text
    .toLowerCase()
    .normalize('NFD').replace(/[̀-ͯ]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}
