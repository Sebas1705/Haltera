#!/usr/bin/env node
// Release notes for testers and stores from the GitHub Release body that `gh release create
// --generate-notes` wrote ("* <PR title> by @user in <url>").
//
//   node scripts/release-notes.mjs <version> <body-file> <out-dir>    (release.yml runs it)
//
// Writes <out-dir>/en-US.txt and <out-dir>/es-ES.txt (Google Play allows 500 characters per
// language). Only user-facing changes are listed: feat/fix/perf PRs, with the conventional prefix
// removed; docs/ci/chore/build/test/refactor/style ones are left out. PR titles are in English, so
// both languages list them under a translated heading.
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const [version, bodyFile, outDir] = process.argv.slice(2);
if (!version || !bodyFile || !outDir) {
  console.error("usage: release-notes.mjs <version> <body-file> <out-dir>");
  process.exit(2);
}

const PLAY_LIMIT = 500;
const HIDDEN = /^(docs|ci|chore|build|test|refactor|style)(\(.*?\))?!?:/i;
const PREFIX = /^[a-z]+(\(.*?\))?!?:\s*/i;

const titles = readFileSync(bodyFile, "utf8")
  .split("\n")
  .map((line) => /^\*\s+(.*?)\s+by\s+@\S+\s+in\s+\S+\s*$/.exec(line)?.[1])
  .filter(Boolean)
  .filter((title) => !HIDDEN.test(title))
  .map((title) => title.replace(PREFIX, ""))
  .map((title) => title.charAt(0).toUpperCase() + title.slice(1));

/** Heading plus as many bullets as fit in Play's limit; a fallback line when nothing is user-facing. */
function notes(heading, fallback) {
  if (titles.length === 0) return `${heading}\n${fallback}`;
  let text = heading;
  for (const title of titles) {
    const next = `${text}\n• ${title}`;
    if (next.length > PLAY_LIMIT) break;
    text = next;
  }
  return text;
}

mkdirSync(outDir, { recursive: true });
writeFileSync(join(outDir, "en-US.txt"), notes(`What's new in ${version}:`, "• Improvements and fixes."));
writeFileSync(join(outDir, "es-ES.txt"), notes(`Novedades de la ${version}:`, "• Mejoras y correcciones."));
console.log(readFileSync(join(outDir, "en-US.txt"), "utf8"));
