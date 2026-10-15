#!/usr/bin/env node
// Copies users/<from>/** into users/<to>/** with last-writer-wins on updatedAt.
//   node scripts/firestore-migrate-user.mjs --list
//   node scripts/firestore-migrate-user.mjs --from <uid> --to <uid>            (dry run)
//   node scripts/firestore-migrate-user.mjs --from <uid> --to <uid> --apply    (writes)
// Never overwrites a newer destination doc. Never deletes. Never prints payloads.
import { Firestore, accessToken, decide, mapPath, parseArgs, updatedAtOf, DOCS_ROOT, segments } from "./firestore/lib.mjs";

const args = parseArgs(process.argv.slice(2));
const fs = new Firestore(await accessToken());
const iso = (ms) => (ms ? new Date(ms).toISOString() : "-");

if (args.list) {
  for (const uid of await fs.listUserIds()) {
    const data = await fs.readUser(uid);
    let max = 0;
    const counts = Object.entries(data).map(([c, docs]) => {
      for (const d of docs) max = Math.max(max, updatedAtOf(d));
      return `${c}=${docs.length}`;
    });
    console.log(`${uid}  max updatedAt ${iso(max)}  ${counts.join(" ") || "(no docs)"}`);
  }
  process.exit(0);
}

const { from, to } = args;
if (typeof from !== "string" || typeof to !== "string") throw new Error("Usage: --from <uid> --to <uid> [--apply] | --list");
if (from === to) throw new Error("--from and --to must differ");
const apply = args.apply === true; // dry run unless explicitly applied

const source = await fs.readUser(from);
const dest = await fs.readUser(to);
const blank = () => ({ copy: 0, "skip-newer": 0, "skip-same": 0, conflict: 0 });
const tally = blank();
const perCollection = {};
const writes = [];
for (const [col, docs] of Object.entries(source)) {
  const existing = new Map((dest[col] ?? []).map((d) => [segments(d.name).at(-1), d]));
  perCollection[col] = blank();
  for (const doc of docs) {
    const verdict = decide(doc, existing.get(segments(doc.name).at(-1)));
    tally[verdict]++;
    perCollection[col][verdict]++;
    if (verdict === "copy") {
      const target = mapPath(doc.name, from, to);
      writes.push({ update: { name: `${DOCS_ROOT}/${target}`, fields: doc.fields ?? {} } });
    }
  }
}
console.log(`${apply ? "APPLY" : "DRY RUN"}: users/${from} -> users/${to}`);
for (const [c, t] of Object.entries(perCollection)) {
  console.log(`  ${c}: copy=${t.copy} skip-newer=${t["skip-newer"]} identical=${t["skip-same"]} conflict=${t.conflict}`);
}
console.log(`Total: would copy ${tally.copy}, skip newer ${tally["skip-newer"]}, identical ${tally["skip-same"]}, conflicts ${tally.conflict}`);

if (!apply) {
  console.log("Nothing written. Re-run with --apply to write.");
  process.exit(0);
}
for (let i = 0; i < writes.length; i += 400) {
  await fs.call("POST", `${DOCS_ROOT}:commit`, { writes: writes.slice(i, i + 400) });
}
console.log(`Wrote ${writes.length} documents.`);
