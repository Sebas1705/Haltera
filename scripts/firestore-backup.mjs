#!/usr/bin/env node
// Read-only dump of every document under users/* (Firestore REST, lossless typed values).
//   node scripts/firestore-backup.mjs --out <dir outside the repo>
// Writes <dir>/users/<uid>.json (one per user) and <dir>/index.json (counts only).
// The output is personal data: keep it out of git. Nothing is written to Firestore.
import { mkdirSync, writeFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { Firestore, accessToken, assertOutsideRepo, parseArgs, repoRoot } from "./firestore/lib.mjs";

const args = parseArgs(process.argv.slice(2));
const root = repoRoot();
if (root) assertOutsideRepo(args.out, root);
else if (!args.out || args.out === true) throw new Error("--out <dir> is required");
const out = resolve(args.out);

const fs = new Firestore(await accessToken());
const uids = await fs.listUserIds();
mkdirSync(join(out, "users"), { recursive: true });

const index = { project: "ironlog-sebas1705", createdAt: new Date().toISOString(), userCount: uids.length, users: {}, totals: {} };
for (const uid of uids) {
  const data = await fs.readUser(uid);
  writeFileSync(join(out, "users", `${uid}.json`), JSON.stringify({ uid, collections: data }, null, 2));
  const counts = Object.fromEntries(Object.entries(data).map(([c, docs]) => [c, docs.length]));
  index.users[uid] = counts;
  for (const [c, n] of Object.entries(counts)) index.totals[c] = (index.totals[c] ?? 0) + n;
}
writeFileSync(join(out, "index.json"), JSON.stringify(index, null, 2));
console.log(`Users: ${uids.length}`);
for (const [c, n] of Object.entries(index.totals).sort()) console.log(`  ${c}: ${n}`);
console.log(`Backup written to ${out}`);
