#!/usr/bin/env node
// One-off: gives GitHub Actions what the distribution workflow needs.
//  1. Service account ironlog-ci in the Firebase project with the least roles it uses:
//     App Distribution (builds for testers) and Service Usage Consumer (to call that API).
//  2. A key for it in Doppler ironlog/prd as FIREBASE_SERVICE_ACCOUNT_JSON.
//  3. A read-only Doppler service token in the GitHub secret DOPPLER_TOKEN.
//  4. The App Distribution group "testers" ([email...] arguments are added to it).
//
//   node scripts/setup-ci-access.mjs [tester-email ...]
//
// Uses the account of `npx firebase-tools login` (no gcloud needed), plus doppler and gh, all
// logged in. Running it again keeps the account and roles and adds a new key (rotation).
// Ironlog has no Firebase SDK in the app: Firebase is only used for App Distribution.
import { execFileSync, spawnSync } from "node:child_process";
import { existsSync, readdirSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";
import { createRequire } from "node:module";

const PROJECT = "ironlog-sebas1705";
const ACCOUNT_ID = "ironlog-ci";
const EMAIL = `${ACCOUNT_ID}@${PROJECT}.iam.gserviceaccount.com`;
const ROLES = ["roles/firebaseappdistro.admin", "roles/serviceusage.serviceUsageConsumer"];
const APIS = [
  "iam.googleapis.com",
  "cloudresourcemanager.googleapis.com",
  "firebaseappdistribution.googleapis.com",
];
const DOPPLER_PROJECT = "ironlog";
const DOPPLER_CONFIG = "prd";
const GITHUB_SECRET = "DOPPLER_TOKEN";
const REPO = "Sebas1705/Ironlog";
const FIREBASE_TOOLS = "firebase-tools@15.32.1";

/** firebase-tools as a library: the copy npx keeps in its cache (or FIREBASE_TOOLS_DIR). */
function firebaseTools() {
  if (process.env.FIREBASE_TOOLS_DIR) return process.env.FIREBASE_TOOLS_DIR;
  execFileSync("npx", ["--yes", FIREBASE_TOOLS, "--version"], { stdio: "ignore", shell: true });
  const cache = join(process.env.LOCALAPPDATA ?? join(homedir(), ".npm"), "npm-cache", "_npx");
  const roots = [cache, join(homedir(), ".npm", "_npx")].filter(existsSync);
  for (const root of roots) {
    for (const dir of readdirSync(root)) {
      const candidate = join(root, dir, "node_modules", "firebase-tools");
      if (existsSync(join(candidate, "package.json"))) return candidate;
    }
  }
  throw new Error("firebase-tools not found; set FIREBASE_TOOLS_DIR");
}

function run(cmd, args, input) {
  const result = spawnSync(cmd, args, { input, encoding: "utf8", shell: true });
  if (result.status !== 0) throw new Error(`${cmd} ${args[0]} failed: ${result.stderr}`);
  return result.stdout.trim();
}

/** firebase-tools through npx; on Windows Node may abort while exiting after it already succeeded. */
function firebase(args) {
  const result = spawnSync("npx", ["--yes", FIREBASE_TOOLS, ...args], { encoding: "utf8", shell: true });
  return `${result.stdout}\n${result.stderr}`;
}

const require = createRequire(import.meta.url);
const ft = firebaseTools();
const auth = require(join(ft, "lib/auth"));
const { requireAuth } = require(join(ft, "lib/requireAuth"));
const { ensure } = require(join(ft, "lib/ensureApiEnabled"));
const { Client } = require(join(ft, "lib/apiv2"));

const account = auth.getGlobalDefaultAccount();
if (!account) throw new Error("Run `npx firebase-tools login` first");
await requireAuth({ project: PROJECT, user: account.user, tokens: account.tokens });
for (const api of APIS) await ensure(PROJECT, api, "ironlog-ci", false);

const iam = new Client({ urlPrefix: "https://iam.googleapis.com", apiVersion: "v1" });
const crm = new Client({ urlPrefix: "https://cloudresourcemanager.googleapis.com", apiVersion: "v1" });

// 1. The account (kept if it exists) and its roles.
try {
  await iam.post(`projects/${PROJECT}/serviceAccounts`, {
    accountId: ACCOUNT_ID,
    serviceAccount: { displayName: "Ironlog CI (App Distribution)" },
  });
  console.log(`Created ${EMAIL}`);
} catch (e) {
  if (e.status !== 409) throw e;
  console.log(`${EMAIL} already exists`);
}
const member = `serviceAccount:${EMAIL}`;
// A new account takes a while to be visible to IAM policies: retry while it "does not exist".
for (let attempt = 1; ; attempt++) {
  const { body: policy } = await crm.post(`projects/${PROJECT}:getIamPolicy`, {});
  policy.bindings ??= [];
  for (const role of ROLES) {
    const binding = policy.bindings.find((b) => b.role === role);
    if (!binding) policy.bindings.push({ role, members: [member] });
    else if (!binding.members.includes(member)) binding.members.push(member);
  }
  try {
    await crm.post(`projects/${PROJECT}:setIamPolicy`, { policy });
    break;
  } catch (e) {
    if (e.status !== 400 || attempt >= 10) throw e;
    await new Promise((resolve) => setTimeout(resolve, 6000));
  }
}
console.log(`${PROJECT}: ${ROLES.join(", ")}`);

// 2. A key, straight into Doppler (never written to disk).
const { body: key } = await iam.post(`projects/${PROJECT}/serviceAccounts/${EMAIL}/keys`, {});
const keyJson = Buffer.from(key.privateKeyData, "base64").toString("utf8");
run("doppler", ["secrets", "set", "FIREBASE_SERVICE_ACCOUNT_JSON", "--project", DOPPLER_PROJECT, "--config", DOPPLER_CONFIG, "--silent"], keyJson);
console.log(`Stored the key in Doppler ${DOPPLER_PROJECT}/${DOPPLER_CONFIG} as FIREBASE_SERVICE_ACCOUNT_JSON`);

// 3. A read-only Doppler token for GitHub Actions.
const token = run("doppler", ["configs", "tokens", "create", `github-actions-${Date.now()}`, "--project", DOPPLER_PROJECT, "--config", DOPPLER_CONFIG, "--plain"]);
run("gh", ["secret", "set", GITHUB_SECRET, "--repo", REPO], token);
console.log(`Set the GitHub secret ${GITHUB_SECRET} on ${REPO} (Doppler ${DOPPLER_PROJECT}/${DOPPLER_CONFIG}, read-only)`);

// 4. Testers.
const emails = process.argv.slice(2);
const created = firebase(["appdistribution:group:create", "Testers", "testers", "--project", PROJECT]);
if (!/created|already exists/i.test(created)) console.warn(`Could not confirm the testers group:\n${created}`);
if (emails.length > 0) {
  firebase(["appdistribution:testers:add", ...emails, "--group-alias", "testers", "--project", PROJECT]);
  console.log(`Testers: ${emails.join(", ")}`);
}
console.log("Done.");
