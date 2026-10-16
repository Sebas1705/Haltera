#!/usr/bin/env node
// One-off: gives the release workflow what it needs to upload to Google Play (docs/play/PLAY-CONSOLE-GUIDE.md, phase 5).
//  1. Enables the Google Play Android Developer API in the Firebase/Cloud project.
//  2. Service account play-publisher with NO IAM roles (its rights come from the invitation in Play Console).
//  3. A key for it in Doppler ironlog/prd as PLAY_SERVICE_ACCOUNT_JSON (never written to disk, never printed).
//
//   node scripts/setup-play-access.mjs
//
// Afterwards invite the printed email in Play Console -> Users and permissions, then `gh variable set PLAY_TRACK`.
// Uses the account of `npx firebase-tools login` (no gcloud needed) and doppler, both logged in.
// Running it again keeps the account and adds a new key (rotation).
import { execFileSync, spawnSync } from "node:child_process";
import { existsSync, readdirSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";
import { createRequire } from "node:module";

const PROJECT = "ironlog-sebas1705";
const ACCOUNT_ID = "play-publisher";
const EMAIL = `${ACCOUNT_ID}@${PROJECT}.iam.gserviceaccount.com`;
const APIS = ["iam.googleapis.com", "androidpublisher.googleapis.com"];
const DOPPLER_PROJECT = "ironlog";
const DOPPLER_CONFIG = "prd";
const FIREBASE_TOOLS = "firebase-tools@15.32.1";

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
const { ensure } = require(join(ft, "lib/ensureApiEnabled"));
const { Client } = require(join(ft, "lib/apiv2"));

const account = auth.getGlobalDefaultAccount();
if (!account) throw new Error("Run `npx firebase-tools login` first");
await requireAuth({ project: PROJECT, user: account.user, tokens: account.tokens });
for (const api of APIS) await ensure(PROJECT, api, "play-publisher", false);

const iam = new Client({ urlPrefix: "https://iam.googleapis.com", apiVersion: "v1" });
try {
  await iam.post(`projects/${PROJECT}/serviceAccounts`, {
    accountId: ACCOUNT_ID,
    serviceAccount: { displayName: "Haltera Play publisher" },
  });
  console.log(`Created ${EMAIL}`);
} catch (e) {
  if (e.status !== 409) throw e;
  console.log(`${EMAIL} already exists`);
}

// A new account takes a moment to be visible: retry the key request while it "does not exist".
let key;
for (let attempt = 1; ; attempt++) {
  try {
    ({ body: key } = await iam.post(`projects/${PROJECT}/serviceAccounts/${EMAIL}/keys`, {}));
    break;
  } catch (e) {
    if (e.status !== 404 || attempt >= 10) throw e;
    await new Promise((resolve) => setTimeout(resolve, 6000));
  }
}
const keyJson = Buffer.from(key.privateKeyData, "base64").toString("utf8");
const stored = spawnSync(
  "doppler",
  ["secrets", "set", "PLAY_SERVICE_ACCOUNT_JSON", "--project", DOPPLER_PROJECT, "--config", DOPPLER_CONFIG, "--silent"],
  { input: keyJson, encoding: "utf8", shell: true },
);
if (stored.status !== 0) throw new Error(`doppler failed: ${stored.stderr}`);
console.log(`Stored the key in Doppler ${DOPPLER_PROJECT}/${DOPPLER_CONFIG} as PLAY_SERVICE_ACCOUNT_JSON`);
console.log(`Invite ${EMAIL} in Play Console -> Users and permissions.`);
process.exit(0);
