#!/usr/bin/env node
// Google Play screenshots, captured from the real app on emulators with realistic seeded data:
//   fastlane/metadata/android/<locale>/images/{phoneScreenshots,sevenInchScreenshots,tenInchScreenshots}/N.png
// No frames, no captions: Play shows what the app shows.
//
//   ./gradlew :composeApp:assembleDebug
//   node scripts/play-screenshots.mjs --phone emulator-5560 --tablet emulator-5554 [--only phone|seven|ten] [--locale en-US]
//
// Per device and locale it installs the debug APK, wipes it, lets the app create its database, loads
// scripts/play-screenshots/seed.sql (20 weeks of sessions and body weight, written with run-as), sets the app
// language, copies a famous routine and makes it active, sets nutrition goals, logs some foods and walks the
// screens. Buttons are found by the text of the app's own string resources, so it runs in English and Spanish and
// survives wording edits. Needs adb, Node 22 (node:sqlite) and the debug build. The phone is portrait
// (1080x2400 emulator), the 10-inch tablet keeps its native size (landscape) and the 7-inch set is the same tablet
// resized to 1200x1920 at 320 dpi (reset afterwards). The status bar is in demo mode (9:41, full battery).
// Phone sets are 6 light screens + 2 dark ones (Play accepts up to 8 per type).
import { execFileSync } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from "node:fs";
import { DatabaseSync } from "node:sqlite";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const argv = process.argv.slice(2);
const opt = (name, def) => { const i = argv.indexOf(`--${name}`); return i >= 0 ? argv[i + 1] : def; };
const SDK = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || join(process.env.USERPROFILE || process.env.HOME || "", "Android/sdk");
const ADB = join(SDK, "platform-tools", process.platform === "win32" ? "adb.exe" : "adb");
const PKG = "es.sebas1705.haltera.debug";
const ACTIVITY = `${PKG}/es.sebas1705.haltera.MainActivity`;
const APK = join(root, "composeApp/build/outputs/apk/debug/composeApp-debug.apk");
const SEED = join(root, "scripts/play-screenshots/seed.sql");
const tmp = join(tmpdir(), "haltera-shots");
mkdirSync(tmp, { recursive: true });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// ---------------------------------------------------------------- strings: labels come from the resources
const strings = { en: {}, es: {} };
function collect(dir) {
  for (const f of readdirSync(dir)) {
    const p = join(dir, f);
    if (f === "build" || f === "node_modules" || f.startsWith(".")) continue;
    if (statSync(p).isDirectory()) { collect(p); continue; }
    if (f !== "strings.xml") continue;
    const path = p.replaceAll("\\", "/");
    const lang = path.includes("/values-es/") ? "es" : path.includes("/values/") ? "en" : null;
    if (!lang) continue;
    for (const m of readFileSync(p, "utf8").matchAll(/<string name="([^"]+)"[^>]*>([\s\S]*?)<\/string>/g)) {
      strings[lang][m[1]] = m[2].replace(/\\'/g, "'").replace(/\\"/g, '"').replace(/\\n/g, "\n");
    }
  }
}
for (const d of ["composeApp", "feature", "core"]) collect(join(root, d));

// ---------------------------------------------------------------- adb
class Device {
  constructor(serial) { this.serial = serial; }
  raw(...a) { return execFileSync(ADB, ["-s", this.serial, ...a], { maxBuffer: 1 << 28, env: { ...process.env, MSYS_NO_PATHCONV: "1" } }); }
  adb(...a) { return this.raw(...a).toString("utf8"); }
  shell(...a) { return this.adb("shell", ...a); }
  tap(x, y) { this.shell("input", "tap", String(Math.round(x)), String(Math.round(y))); }
  key(code) { this.shell("input", "keyevent", String(code)); }
  type(s) { this.shell("input", "text", s.replaceAll(" ", "%s")); }
  selectAll() { this.shell("input", "keycombination", "113", "29"); }
  swipe(x1, y1, x2, y2) { this.shell("input", "swipe", ...[x1, y1, x2, y2, 350].map(String)); }
  size() {
    const lines = this.shell("wm", "size").split("\n").filter((l) => /\d+x\d+/.test(l));
    const m = /(\d+)x(\d+)/.exec(lines.at(-1));
    return { w: +m[1], h: +m[2] };
  }
  nodes() {
    this.shell("uiautomator", "dump", "/sdcard/ui.xml");
    const xml = this.raw("exec-out", "cat", "/sdcard/ui.xml").toString("utf8");
    const dec = (s) => s.replaceAll("&quot;", '"').replaceAll("&apos;", "'").replaceAll("&lt;", "<").replaceAll("&gt;", ">").replaceAll("&amp;", "&");
    const out = [];
    for (const m of xml.matchAll(/<node [^>]*>/g)) {
      const get = (n) => new RegExp(` ${n}="([^"]*)"`).exec(m[0])?.[1] ?? "";
      const b = /\[(\d+),(\d+)\]\[(\d+),(\d+)\]/.exec(get("bounds"));
      if (!b) continue;
      const [x1, y1, x2, y2] = b.slice(1).map(Number);
      out.push({ text: dec(get("text")), desc: dec(get("content-desc")), x1, y1, x2, y2, cx: (x1 + x2) / 2, cy: (y1 + y2) / 2 });
    }
    return out;
  }
  async find(match, tries = 8) {
    for (let i = 0; i < tries; i++) {
      const hit = this.nodes().find(match);
      if (hit) return hit;
      await sleep(600);
    }
    throw new Error(`Element not found on ${this.serial}`);
  }
}

// ---------------------------------------------------------------- one device + locale: data and navigation
async function prepare(dev, { locale, lang, resize }) {
  const L = (key) => {
    const v = strings[lang][key];
    if (v === undefined) throw new Error(`No string ${key} (${lang})`);
    return v;
  };
  const label = (n, s) => n.text === s || n.desc === s;
  const press = async (s, index = 0) => {
    for (let i = 0; i < 8; i++) {
      const hits = dev.nodes().filter((n) => label(n, s));
      if (hits.length > index) { dev.tap(hits[index].cx, hits[index].cy); await sleep(900); return; }
      await sleep(600);
    }
    throw new Error(`Cannot find "${s}"`);
  };
  // Bottom-bar tabs: go back until the bar is visible (sub-screens hide it).
  const tab = async (key) => {
    for (let i = 0; i < 8; i++) {
      const hit = dev.nodes().filter((n) => label(n, L(key))).sort((a, b) => b.y1 - a.y1)[0];
      const { w, h } = dev.size();
      if (hit && (hit.x1 < w * 0.2 || hit.y1 > h * 0.85)) { dev.tap(hit.cx, hit.cy); await sleep(900); return; }
      dev.key(4); await sleep(600);
    }
    throw new Error("Cannot reach tab " + key);
  };
  const hideKeyboard = async () => { dev.key(4); await sleep(500); };
  const { w, h } = { w: 0, h: 0 };
  void w; void h;

  // device: size, demo-mode status bar, light mode
  if (resize) { dev.shell("wm", "size", resize.size); dev.shell("wm", "density", String(resize.density)); }
  dev.shell("cmd", "uimode", "night", "no");
  dev.shell("settings", "put", "global", "sysui_demo_allowed", "1");
  const demo = (...a) => dev.shell("am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", ...a);
  demo("enter"); demo("clock", "-e", "hhmm", "0941");
  demo("battery", "-e", "level", "100", "-e", "plugged", "false");
  demo("network", "-e", "wifi", "show", "-e", "level", "4");
  demo("notifications", "-e", "visible", "false");

  // app: fresh install, database created by the app, then the seed
  dev.adb("install", "-r", APK);
  dev.shell("pm", "clear", PKG);
  dev.shell("am", "start", "-n", ACTIVITY); await sleep(6000);
  dev.shell("am", "force-stop", PKG);
  const dbFile = join(tmp, `${dev.serial}.db`);
  writeFileSync(dbFile, dev.raw("exec-out", "run-as", PKG, "cat", "databases/haltera.db"));
  const db = new DatabaseSync(dbFile);
  db.exec(readFileSync(SEED, "utf8"));
  db.close();
  dev.adb("push", dbFile, "/data/local/tmp/haltera-seed.db");
  dev.shell(`run-as ${PKG} rm -f databases/haltera.db-journal databases/haltera.db-wal databases/haltera.db-shm; run-as ${PKG} cp /data/local/tmp/haltera-seed.db databases/haltera.db`);
  dev.shell("cmd", "locale", "set-app-locales", PKG, "--locales", locale);
  dev.shell("am", "start", "-n", ACTIVITY); await sleep(5000);
  const { w: W, h: H } = dev.size();

  // a famous routine, copied and made active ("Make it my active routine" is on by default)
  await tab("tab_routines");
  await press(L("routines_from_catalogue"));
  const first = await dev.find((n) => n.y1 > 350 && n.text.length > 12 && !/^Routine score|^Puntuación/.test(n.text) && !n.text.includes("·") && !n.text.startsWith("Credit") && !n.text.startsWith("Crédito"));
  dev.tap(first.cx, first.cy); await sleep(1500);
  await press(L("routines_add_to_mine")); await sleep(1500);
  dev.key(4); await sleep(800);

  // nutrition goals
  await tab("tab_nutrition");
  await press(L("nutrition_set_goals"));
  const energy = await dev.find((n) => n.text === L("nutrition_kcal_goal"));
  dev.tap(W / 2, energy.cy + 20); dev.selectAll(); dev.type("2600");
  const macros = [[L("nutrition_protein"), "170"], [L("nutrition_carbs"), "300"], [L("nutrition_fat"), "80"]];
  for (const [name, value] of macros) {
    const field = await dev.find((n) => n.text === name && n.y1 > energy.y1 + 100);
    dev.tap(field.cx, field.cy + 30); dev.selectAll(); dev.type(value);
  }
  await hideKeyboard();
  await press(L("nutrition_save"));
  dev.key(4); await sleep(1000);

  // food
    const foods = lang === "es"
    ? [["breakfast", "salvado de avena", 80], ["lunch", "pechuga de pollo a la", 200], ["lunch", "arroz integral, cocido", 150], ["dinner", "salmon cocinado", 150], ["snacks", "platano", 120]]
    : [["breakfast", "oat bran", 80], ["lunch", "chicken breast, grilled", 200], ["lunch", "brown rice, cooked", 150], ["dinner", "salmon, cooked", 150], ["snacks", "banana", 120]];
  for (const [meal, query, grams] of foods) {
    await tab("tab_nutrition");
    const keys = { breakfast: "nutrition_meal_breakfast", lunch: "nutrition_meal_lunch", dinner: "nutrition_meal_dinner", snacks: "nutrition_meal_snack" };
    const names = Object.values(keys).map(L);
    let target = null;
    for (let tries = 0; tries < 6 && !target; tries++) {
      const all = dev.nodes();
      const header = all.find((n) => n.text === L(keys[meal]));
      if (header) {
        const nextHeader = Math.min(...all.filter((n) => names.includes(n.text) && n.y1 > header.y1 + 10).map((n) => n.y1), Infinity);
        target = all.filter((n) => label(n, L("nutrition_add_food")) && n.y1 > header.y1 && n.y1 < nextHeader && n.y2 < H - 150)[0] ?? null;
      }
      if (!target) { dev.swipe(W / 2, H * 0.75, W / 2, H * 0.35); await sleep(700); }
    }
    if (!target) { console.warn("No Add food button for " + meal + ": skipped"); continue; }
    dev.tap(target.cx, target.cy); await sleep(1200);
    const search = await dev.find((n) => n.text.startsWith(L("nutrition_search_hint").slice(0, 8)));
    dev.tap(search.cx, search.cy); dev.type(query); await sleep(1500);
    const hit = await dev.find((n) => /^100 (g|ml)/.test(n.text));
    dev.tap(hit.x1 + 40, hit.y1 - 40); await sleep(1500);
    try { await press(L("nutrition_mode_grams")); } catch { /* liquids use millilitres: keep the unit */ }
    const amount = await dev.find((n) => /^[0-9.,]+$/.test(n.text) && n.y1 > 300 && n.y1 < H * 0.6);
    dev.tap(amount.x1 + 40, amount.cy); dev.selectAll(); dev.type(String(grams));
    await hideKeyboard();
    const prefix = L("nutrition_add_to_meal").split("%1$s")[0];
    const add = await dev.find((n) => n.text.startsWith(prefix) && n.text.length > prefix.length);
    dev.tap(add.cx, add.cy); await sleep(1500);
  }
  return { L, press, tab, dev, W, H };
}

// ---------------------------------------------------------------- screens
const screens = {
  today: async ({ press, tab, L }) => { await tab("tab_today"); },
  analysis: async ({ press, tab, L, dev }) => {
    await tab("tab_routines");
    const row = await dev.find((n) => n.y1 > 300 && n.text.length > 10 && n.text !== L("tab_routines") && !/^Routine score|^Puntuación/.test(n.text));
    dev.tap(row.cx, row.cy); await sleep(1500);
    await press(L("routines_analysis_open")); await sleep(1200);
  },
  overview: async ({ press, tab, L }) => { await tab("tab_progress"); await press(L("progress_tab_overview")); await sleep(1200); },
  exerciseProgress: async ({ press, tab, L }) => {
    await tab("tab_progress"); await press(L("progress_tab_exercises"), 0); await sleep(1500);
  },
  exerciseDetail: async ({ press, tab, L, dev }) => {
    await tab("tab_exercises"); await sleep(800);
    const row = await dev.find((n) => n.text === "Bench Press" || n.text === "Press de banca");
    dev.tap(row.cx, row.cy); await sleep(1500);
  },
  nutrition: async ({ press, tab, L }) => { await tab("tab_nutrition"); await sleep(1000); },
};

const sets = {
  phone: { dir: "phoneScreenshots", plan: ["today", "analysis", "overview", "exerciseProgress", "exerciseDetail", "nutrition", "dark:today", "dark:nutrition"] },
  seven: { dir: "sevenInchScreenshots", plan: ["today", "analysis", "overview", "exerciseProgress", "nutrition"], resize: { size: "1200x1920", density: 320 } },
  ten: { dir: "tenInchScreenshots", plan: ["today", "analysis", "overview", "exerciseProgress", "nutrition"] },
};
const locales = { "en-US": "en", "es-ES": "es" };

if (!existsSync(APK)) { console.error(`Build the debug APK first: ${APK}`); process.exit(1); }
const phone = new Device(opt("phone", "emulator-5560"));
const tablet = new Device(opt("tablet", "emulator-5554"));

for (const [name, set] of Object.entries(sets)) {
  if (opt("only") && opt("only") !== name) continue;
  const dev = name === "phone" ? phone : tablet;
  for (const [locale, lang] of Object.entries(locales)) {
    if (opt("locale") && opt("locale") !== locale) continue;
    const dir = join(root, "fastlane/metadata/android", locale, "images", set.dir);
    rmSync(dir, { recursive: true, force: true });
    mkdirSync(dir, { recursive: true });
    try {
      const ctx = await prepare(dev, { locale, lang, resize: set.resize });
      let n = 0;
      for (const item of set.plan) {
        const night = item.startsWith("dark:");
        dev.shell("cmd", "uimode", "night", night ? "yes" : "no");
        if (night) await sleep(1500);
        // back to the root of the tab stack before each screen
        for (let i = 0; i < 3; i++) dev.key(4);
        dev.shell("am", "start", "-n", ACTIVITY); await sleep(1200);
        await screens[item.replace("dark:", "")](ctx);
        await sleep(800);
        const file = join(dir, `${++n}.png`);
        writeFileSync(file, dev.raw("exec-out", "screencap", "-p"));
        console.log("wrote", file.slice(root.length + 1).replaceAll("\\", "/"), `(${item})`);
      }
    } finally {
      dev.shell("cmd", "uimode", "night", "no");
      if (set.resize) { dev.shell("wm", "size", "reset"); dev.shell("wm", "density", "reset"); }
      dev.shell("am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", "exit");
    }
  }
}
