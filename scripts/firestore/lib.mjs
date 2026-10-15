// Shared helpers for the Firestore backup / migration scripts: pure logic plus a thin REST client.
import { execFileSync, spawnSync } from "node:child_process";
import { existsSync, readdirSync } from "node:fs";
import { homedir } from "node:os";
import { join, resolve, relative, isAbsolute } from "node:path";
import { createRequire } from "node:module";

export const PROJECT = "ironlog-sebas1705";
export const DOCS_ROOT = `projects/${PROJECT}/databases/(default)/documents`;
const BASE = "https://firestore.googleapis.com/v1";
const FIREBASE_TOOLS = "firebase-tools@15.32.1";

// ---------- pure logic ----------

/** True when `target` is `root` or inside it. */
export function isInside(root, target) {
  const rel = relative(resolve(root), resolve(target));
  return rel === "" || (!rel.startsWith("..") && !isAbsolute(rel));
}

/** Throws when `outDir` would land inside the git repository at `repoRoot`. */
export function assertOutsideRepo(outDir, repoRoot) {
  if (!outDir || outDir === true) throw new Error("--out <dir> is required");
  if (isInside(repoRoot, outDir)) throw new Error(`Refusing to write inside the git repo (${repoRoot}); choose a folder outside it`);
}

/** Path segments after `.../documents/`, e.g. ["users","u1","routines","r1"]. */
export function segments(name) {
  const marker = "/documents/";
  const i = name.indexOf(marker);
  return (i >= 0 ? name.slice(i + marker.length) : name).split("/");
}

/** Maps a document resource name from one user to another; null if it is not under users/<from>/. */
export function mapPath(name, from, to) {
  const s = segments(name);
  if (s.length < 4 || s[0] !== "users" || s[1] !== from) return null;
  return `users/${to}/${s.slice(2).join("/")}`;
}

/** Numeric updatedAt of a REST document, or 0 when absent. Accepts integer, double, timestamp. */
export function updatedAtOf(doc) {
  const v = doc?.fields?.updatedAt;
  if (!v) return 0;
  if (v.integerValue !== undefined) return Number(v.integerValue);
  if (v.doubleValue !== undefined) return Number(v.doubleValue);
  if (v.timestampValue !== undefined) return Date.parse(v.timestampValue);
  return 0;
}

/**
 * Last-writer-wins decision for copying `src` onto `dst` (dst undefined when absent).
 * Returns "copy" | "skip-newer" | "skip-same" | "conflict".
 * Tombstones are ordinary docs: a newer tombstone wins like any other write.
 * conflict = equal updatedAt but different content; the destination is kept.
 */
export function decide(src, dst) {
  if (!dst) return "copy";
  const a = updatedAtOf(src);
  const b = updatedAtOf(dst);
  if (a > b) return "copy";
  if (a < b) return "skip-newer";
  const same = JSON.stringify(src.fields ?? {}) === JSON.stringify(dst.fields ?? {});
  return same ? "skip-same" : "conflict";
}

export function parseArgs(argv) {
  const out = { _: [] };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (!a.startsWith("--")) { out._.push(a); continue; }
    const key = a.slice(2);
    const next = argv[i + 1];
    if (next !== undefined && !next.startsWith("--")) { out[key] = next; i++; } else out[key] = true;
  }
  return out;
}

// ---------- REST client ----------

function firebaseToolsDir() {
  if (process.env.FIREBASE_TOOLS_DIR) return process.env.FIREBASE_TOOLS_DIR;
  execFileSync("npx", ["--yes", FIREBASE_TOOLS, "--version"], { stdio: "ignore", shell: true });
  const roots = [join(process.env.LOCALAPPDATA ?? join(homedir(), ".npm"), "npm-cache", "_npx"), join(homedir(), ".npm", "_npx")].filter(existsSync);
  for (const root of roots) {
    for (const dir of readdirSync(root)) {
      const candidate = join(root, dir, "node_modules", "firebase-tools");
      if (existsSync(join(candidate, "package.json"))) return candidate;
    }
  }
  throw new Error("firebase-tools not found; set FIREBASE_TOOLS_DIR");
}

/** Access token of the account logged in with the firebase CLI. */
export async function accessToken() {
  const require = createRequire(import.meta.url);
  const auth = require(join(firebaseToolsDir(), "lib/auth"));
  const account = auth.getGlobalDefaultAccount();
  if (!account) throw new Error("Run `npx firebase-tools login` first");
  const { access_token } = await auth.getAccessToken(account.tokens.refresh_token, []);
  return access_token;
}

export function repoRoot() {
  const r = spawnSync("git", ["rev-parse", "--show-toplevel"], { encoding: "utf8" });
  return r.status === 0 ? r.stdout.trim() : null;
}

export class Firestore {
  constructor(token) { this.token = token; }

  async call(method, url, body) {
    for (let attempt = 1; ; attempt++) {
      const res = await fetch(`${BASE}/${url}`, {
        method,
        headers: { authorization: `Bearer ${this.token}`, "content-type": "application/json", "x-goog-user-project": PROJECT },
        body: body ? JSON.stringify(body) : undefined,
      });
      if (res.ok) return res.json();
      if ((res.status === 429 || res.status >= 500) && attempt < 5) {
        await new Promise((r) => setTimeout(r, 1000 * attempt));
        continue;
      }
      throw new Error(`Firestore ${method} ${url.split("?")[0]} -> ${res.status}`);
    }
  }

  /** All documents directly in `parent/collectionId` (parent "" = root), paginated. */
  async listDocuments(parent, collectionId, { showMissing = false } = {}) {
    const docs = [];
    let pageToken;
    do {
      const q = new URLSearchParams({ pageSize: "300" });
      if (showMissing) q.set("showMissing", "true");
      if (pageToken) q.set("pageToken", pageToken);
      const path = parent ? `${DOCS_ROOT}/${parent}/${collectionId}` : `${DOCS_ROOT}/${collectionId}`;
      const r = await this.call("GET", `${path}?${q}`);
      docs.push(...(r.documents ?? []));
      pageToken = r.nextPageToken;
    } while (pageToken);
    return docs;
  }

  async listCollectionIds(parent) {
    const ids = [];
    let pageToken;
    do {
      const r = await this.call("POST", `${DOCS_ROOT}/${parent}:listCollectionIds`, { pageSize: 100, pageToken });
      ids.push(...(r.collectionIds ?? []));
      pageToken = r.nextPageToken;
    } while (pageToken);
    return ids;
  }

  /** Every uid under users/, including "missing" parents that only exist through subcollections. */
  async listUserIds() {
    const docs = await this.listDocuments("", "users", { showMissing: true });
    return docs.map((d) => segments(d.name)[1]);
  }

  /** { collection -> docs[] } for one user (REST documents, lossless). */
  async readUser(uid) {
    const out = {};
    for (const col of await this.listCollectionIds(`users/${uid}`)) out[col] = await this.listDocuments(`users/${uid}`, col);
    return out;
  }
}
