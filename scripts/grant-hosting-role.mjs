#!/usr/bin/env node
// One-off: lets the CI service account deploy Firebase Hosting (.github/workflows/deploy-firebase.yml).
// Adds roles/firebasehosting.admin to ironlog-ci@ironlog-sebas1705.iam.gserviceaccount.com on the project.
// Same mechanism as scripts/setup-ci-access.mjs (firebase-tools as a library, the account of
// `npx firebase-tools login`) but it only edits the IAM policy: no key, no Doppler or GitHub change.
//
//   node scripts/grant-hosting-role.mjs           # grant
//   node scripts/grant-hosting-role.mjs --check   # print whether the role is already bound, change nothing
import { execFileSync } from "node:child_process";
import { existsSync, readdirSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";
import { createRequire } from "node:module";

const PROJECT = "ironlog-sebas1705";
const EMAIL = `ironlog-ci@${PROJECT}.iam.gserviceaccount.com`;
const ROLE = "roles/firebasehosting.admin";
const FIREBASE_TOOLS = "firebase-tools@15.32.1";
const check = process.argv.includes("--check");

/** firebase-tools as a library: the copy npx keeps in its cache (or FIREBASE_TOOLS_DIR). */
function firebaseTools() {
  if (process.env.FIREBASE_TOOLS_DIR) return process.env.FIREBASE_TOOLS_DIR;
  execFileSync("npx", ["--yes", FIREBASE_TOOLS, "--version"], { stdio: "ignore", shell: true });
  const cache = join(process.env.LOCALAPPDATA ?? join(homedir(), ".npm"), "npm-cache", "_npx");
  for (const root of [cache, join(homedir(), ".npm", "_npx")].filter(existsSync)) {
    for (const dir of readdirSync(root)) {
      const candidate = join(root, dir, "node_modules", "firebase-tools");
      if (existsSync(join(candidate, "package.json"))) return candidate;
    }
  }
  throw new Error("firebase-tools not found; set FIREBASE_TOOLS_DIR");
}

const require = createRequire(import.meta.url);
const ft = firebaseTools();
const auth = require(join(ft, "lib/auth"));
const { requireAuth } = require(join(ft, "lib/requireAuth"));
const { Client } = require(join(ft, "lib/apiv2"));

const account = auth.getGlobalDefaultAccount();
if (!account) throw new Error("Run `npx firebase-tools login` first");
await requireAuth({ project: PROJECT, user: account.user, tokens: account.tokens });

const crm = new Client({ urlPrefix: "https://cloudresourcemanager.googleapis.com", apiVersion: "v1" });
const member = `serviceAccount:${EMAIL}`;
const { body: policy } = await crm.post(`projects/${PROJECT}:getIamPolicy`, {});
policy.bindings ??= [];
const binding = policy.bindings.find((b) => b.role === ROLE);
const bound = binding?.members.includes(member) ?? false;
if (check || bound) {
  console.log(`${EMAIL} ${bound ? "has" : "does NOT have"} ${ROLE} on ${PROJECT}`);
  process.exit(check && !bound ? 1 : 0);
}
if (binding) binding.members.push(member);
else policy.bindings.push({ role: ROLE, members: [member] });
await crm.post(`projects/${PROJECT}:setIamPolicy`, { policy });
console.log(`Granted ${ROLE} to ${EMAIL} on ${PROJECT}`);
