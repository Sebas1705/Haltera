// Fails when the former app name ("ironlog", any case) appears in a tracked file outside the allow-list
// documented in docs/NAMING.md. Run: `node scripts/check-name.mjs`.
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");

// Internal identifiers that intentionally keep the old name (docs/NAMING.md), allowed on any line.
const keptIdentifiers = [
  /ironlog-sebas1705/g, // Firebase project id
  /ironlog-release\.jks/g, // release keystore file
  /ironlog-ci\b/g, // CI service account
  /ironlog\/prd/g, // Doppler project/config
  /--project ironlog\b/g,
  /\bproject `ironlog`/g,
  /Doppler (project )?ironlog\b/g,
  /DOPPLER_PROJECT\s*=\s*"ironlog"/g,
  /ALIAS="ironlog"/g, // keystore alias
];

// Files that talk about the old name on purpose.
const allowedFiles = new Set([
  "docs/NAMING.md",
  "docs/RELEASE.md", // legacy package and legacy Firebase apps
  "docs/FIRESTORE-DATA.md", // rename runbook
  "README.md", // "formerly called Ironlog"
  "scripts/check-name.mjs",
  // Backward-compatible decoders of the former share formats, and their tests.
  "data/repository/src/commonMain/kotlin/es/sebas1705/haltera/data/repository/share/RoutineExport.kt",
  "data/repository/src/commonMain/kotlin/es/sebas1705/haltera/data/repository/share/RoutineShareCode.kt",
  "data/repository/src/androidUnitTest/kotlin/es/sebas1705/haltera/data/repository/RoutineSharingTest.kt",
  "composeApp/src/androidMain/AndroidManifest.xml", // .ironlog intent filter
  "docs/designs/2026-10-14-haltera-core-design.md", // share format spec mentions the legacy formats
]);

const files = execFileSync("git", ["ls-files", "-z"], { cwd: root, maxBuffer: 1 << 28 }).toString("utf8").split("\0").filter(Boolean);
const bad = [];
for (const f of files) {
  if (allowedFiles.has(f)) continue;
  if (/ironlog/i.test(f)) bad.push(`${f}: file name`);
  let text;
  try { text = readFileSync(join(root, f)); } catch { continue; }
  if (text.includes(0)) continue;
  text.toString("utf8").split("\n").forEach((line, i) => {
    let rest = line;
    for (const re of keptIdentifiers) rest = rest.replace(re, "");
    if (/ironlog/i.test(rest)) bad.push(`${f}:${i + 1}: ${line.trim().slice(0, 140)}`);
  });
}
if (bad.length) {
  console.error(`The former name appears outside the allow-list (docs/NAMING.md):\n${bad.join("\n")}`);
  process.exit(1);
}
console.log("No stray former app name in tracked files.");
