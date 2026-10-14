/** Removes combining diacritical marks: "Pádel" -> "Padel". */
export function stripDiacritics(text) {
  return text.normalize('NFD').replace(/[\u0300-\u036f]/g, '');
}
