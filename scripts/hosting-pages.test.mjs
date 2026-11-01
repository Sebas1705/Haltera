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
  const ids = ["controller", "data", "health-connect", "purposes", "recipients", "retention", "rights", "children", "health", "changes", "contact"];
  for (const page of ["privacy.html", "privacidad.html"]) {
    const html = read(page);
    for (const id of ids) assert.match(html, new RegExp(`<h2 id="${id}">`), `${page} lacks section ${id}`);
    assert.match(html, /2026/, `${page} has no effective date`);
    assert.match(html, /github\.com\/Sebas1705\/Haltera\/issues/, `${page} has no contact`);
    assert.doesNotMatch(html, /@gmail|mailto:/, `${page} exposes an email address`);
  }
});

test("the Health Connect section says what is read, that it stays on the phone, is not used for ads and can be disconnected", () => {
  const facts = {
    "privacy.html": [/steps/, /never uploaded/, /not used for advertising/i, /Disconnect/, /Settings → Integrations → Health Connect/],
    "privacidad.html": [/pasos/, /nunca se suben/, /no se comparten ni se usan para publicidad/i, /Desconectar/, /Ajustes → Integraciones → Health Connect/],
  };
  for (const [page, patterns] of Object.entries(facts)) {
    const section = read(page).split('<h2 id="health-connect">')[1]?.split("<h2")[0] ?? "";
    for (const pattern of patterns) assert.match(section, pattern, `${page}: Health Connect section lacks ${pattern}`);
  }
});

test("both policies disclose the Android crash reports and how to turn them off", () => {
  const facts = {
    "privacy.html": [/Firebase Crashlytics/, /Settings → Privacy → Send crash reports/, /installation identifier/, /90 days/],
    "privacidad.html": [/Firebase Crashlytics/, /Ajustes → Privacidad → Enviar informes de errores/, /identificador aleatorio de instalación/, /90 días/],
  };
  for (const [page, patterns] of Object.entries(facts)) {
    const html = read(page);
    assert.match(html, /id="crash-reports"/, `${page} lacks the crash reports paragraph`);
    for (const pattern of patterns) assert.match(html, pattern, `${page}: lacks ${pattern}`);
    assert.doesNotMatch(html, /crash-reporting service|informes de fallos./, `${page} still says there is no crash reporting`);
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

test("every page has Open Graph and Twitter card tags with an absolute 1200x630 share image", () => {
  const origin = "https://haltera.web.app";
  for (const page of pages) {
    const html = read(page);
    const meta = (name) => html.match(new RegExp(`<meta (?:property|name)="${name}" content="([^"]*)"`))?.[1];
    assert.equal(meta("og:type"), "website", `${page}: og:type`);
    assert.ok(meta("og:title") && html.includes(`<title>${meta("og:title")}</title>`), `${page}: og:title must match <title>`);
    assert.equal(meta("og:description"), meta("description"), `${page}: og:description must match the description`);
    assert.equal(meta("og:image"), `${origin}/og-image.png`, `${page}: og:image`);
    assert.equal(meta("og:image:width"), "1200", page);
    assert.equal(meta("og:image:height"), "630", page);
    assert.ok(meta("og:image:alt"), `${page}: og:image:alt`);
    assert.equal(meta("twitter:card"), "summary_large_image", `${page}: twitter:card`);
    const lang = html.match(/<html lang="(\w+)">/)[1];
    assert.equal(meta("og:locale"), lang === "es" ? "es_ES" : "en_US", `${page}: og:locale`);
    const url = meta("og:url");
    assert.ok(url?.startsWith(`${origin}/`), `${page}: og:url must be absolute`);
    const path = url.slice(origin.length);
    assert.equal(resolve(path), page, `${page}: og:url ${url} does not point at this page`);
  }
  const png = readFileSync(join(hosting, "og-image.png"));
  assert.equal(png.subarray(1, 4).toString(), "PNG");
  assert.deepEqual([png.readUInt32BE(16), png.readUInt32BE(20)], [1200, 630], "og-image.png must be 1200x630");
  assert.ok(png.length < 300 * 1024, `og-image.png is ${png.length} bytes, keep it under 300 KB`);
});
