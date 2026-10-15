// node --test scripts/hosting-pages.test.mjs
// Checks the public pages in firebase/hosting: internal links resolve (cleanUrls), each legal page has its
// required sections, no external resources are loaded, the colours match brand/tokens.json, and the
// feature graphics are up to date.
import assert from "node:assert/strict";
import { existsSync, readFileSync, readdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import { check as checkFeatureGraphics } from "./feature-graphic.mjs";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const hosting = join(root, "firebase/hosting");
const pages = readdirSync(hosting).filter((f) => f.endsWith(".html"));
const read = (file) => readFileSync(join(hosting, file), "utf8");

/** URL path of an internal link -> file under firebase/hosting (cleanUrls: /privacy -> privacy.html). */
function resolve(path) {
  if (path === "/") return "index.html";
  const clean = path.replace(/^\//, "");
  if (existsSync(join(hosting, clean)) && clean.includes(".")) return clean;
  return existsSync(join(hosting, `${clean}.html`)) ? `${clean}.html` : null;
}

test("the expected pages exist", () => {
  for (const f of ["index.html", "privacy.html", "privacidad.html", "delete-account.html", "borrar-cuenta.html", "style.css", "favicon.svg"]) {
    assert.ok(existsSync(join(hosting, f)), `${f} missing`);
  }
});

test("internal links and anchors resolve", () => {
  for (const page of pages) {
    const html = read(page);
    for (const [, href] of html.matchAll(/(?:href|src)="([^"]+)"/g)) {
      if (/^(https?:|mailto:)/.test(href)) continue;
      const [path, hash] = href.split("#");
      const target = path === "" ? page : resolve(path);
      assert.ok(target, `${page}: link ${href} does not resolve`);
      if (hash && target.endsWith(".html")) {
        assert.ok(new RegExp(`id="${hash}"`).test(read(target)), `${page}: anchor #${hash} missing in ${target}`);
      }
    }
  }
});

test("pages load nothing from other origins", () => {
  for (const page of pages) {
    const html = read(page);
    assert.doesNotMatch(html, /<script/i, `${page} has a script`);
    for (const [, url] of html.matchAll(/<(?:link|img)[^>]+(?:href|src)="(https?:[^"]+)"/g)) {
      assert.fail(`${page} loads ${url}`);
    }
  }
  assert.doesNotMatch(read("style.css"), /@import|url\(\s*["']?https?:/);
});

test("each page declares its language and the language links point at the twin", () => {
  const twins = { "privacy.html": ["en", "/privacidad"], "privacidad.html": ["es", "/privacy"], "delete-account.html": ["en", "/borrar-cuenta"], "borrar-cuenta.html": ["es", "/delete-account"] };
  for (const [page, [lang, twin]] of Object.entries(twins)) {
    const html = read(page);
    assert.match(html, new RegExp(`<html lang="${lang}">`), page);
    assert.ok(html.includes(`href="${twin}"`), `${page} does not link ${twin}`);
  }
});

test("privacy policies have the required sections", () => {
  const ids = ["controller", "data", "purposes", "recipients", "retention", "rights", "children", "health", "changes", "contact"];
  for (const page of ["privacy.html", "privacidad.html"]) {
    const html = read(page);
    for (const id of ids) assert.match(html, new RegExp(`<h2 id="${id}">`), `${page} lacks section ${id}`);
    assert.match(html, /2026/, `${page} has no effective date`);
    assert.match(html, /github\.com\/Sebas1705\/Haltera\/issues/, `${page} has no contact`);
    assert.doesNotMatch(html, /@gmail|mailto:/, `${page} exposes an email address`);
  }
});

test("account deletion pages have the required sections", () => {
  for (const page of ["delete-account.html", "borrar-cuenta.html"]) {
    const html = read(page);
    for (const id of ["in-app", "deleted", "kept", "timing", "without-app"]) {
      assert.match(html, new RegExp(`<h2 id="${id}">`), `${page} lacks section ${id}`);
    }
    assert.match(html, /Delete my data/, `${page} lacks the issue title`);
  }
});

test("no page names the former app except to explain the rename", () => {
  for (const page of pages) {
    const mentions = [...read(page).matchAll(/Ironlog/g)].length;
    const allowed = ["privacy.html", "privacidad.html", "delete-account.html", "borrar-cuenta.html"].includes(page);
    assert.ok(allowed || mentions === 0, `${page} mentions Ironlog`);
  }
});

test("style.css colours match brand/tokens.json", () => {
  const tokens = JSON.parse(readFileSync(join(root, "brand/tokens.json"), "utf8")).colors;
  const css = read("style.css");
  const light = css.split("@media")[0];
  const dark = css.split("@media (prefers-color-scheme: dark)")[1];
  const pairs = [
    ["--accent", "accent"], ["--on-accent", "onAccent"], ["--accent-text", "accentText"],
    ["--accent-container", "accentContainer"], ["--background", "background"], ["--on-background", "onBackground"],
    ["--surface", "surface"], ["--on-surface-variant", "onSurfaceVariant"], ["--outline", "outline"],
    ["--outline-variant", "outlineVariant"],
  ];
  for (const [variable, token] of pairs) {
    assert.match(light, new RegExp(`${variable}: ${tokens.light[token]};`, "i"), `light ${variable}`);
    // Dark overrides only what differs from light.
    if (tokens.dark[token] !== tokens.light[token]) {
      assert.match(dark, new RegExp(`${variable}: ${tokens.dark[token]};`, "i"), `dark ${variable}`);
    }
  }
});

test("firebase.json serves the haltera site from hosting with security headers", () => {
  const config = JSON.parse(readFileSync(join(root, "firebase/firebase.json"), "utf8"));
  assert.equal(config.hosting.site, "haltera");
  assert.equal(config.hosting.public, "hosting");
  assert.equal(config.hosting.cleanUrls, true);
  const keys = config.hosting.headers.flatMap((h) => h.headers.map((x) => x.key));
  for (const key of ["Content-Security-Policy", "X-Content-Type-Options", "Referrer-Policy", "Cache-Control"]) {
    assert.ok(keys.includes(key), `${key} header missing`);
  }
  assert.ok(config.firestore.rules, "Firestore config must stay");
});

test("feature graphics are up to date, 1024x500 and without alpha", () => {
  assert.deepEqual(checkFeatureGraphics(), []);
});
