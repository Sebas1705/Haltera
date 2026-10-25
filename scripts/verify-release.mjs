#!/usr/bin/env node
// Release verification: builds the release AAB and APK (R8 on) and checks what Google Play cares about,
// then writes docs/play/RELEASE-AUDIT.md.
//
//   node scripts/verify-release.mjs                 build, check, write the report
//   node scripts/verify-release.mjs --skip-build    check the outputs of the last build
//   node scripts/verify-release.mjs --no-report     check only (CI); never writes the report
//
// Checks (exit code 1 when one fails):
//   - every native .so of the AAB and APK is aligned for 16 KB pages (ELF PT_LOAD p_align >= 0x4000 on the
//     64-bit ABIs; Play requires it for apps targeting Android 15+), and `zipalign -c -P 16` passes on the APK
//   - targetSdk 36, minSdk 24, not debuggable, no test-only libraries in the release dex
//   - R8 mapping.txt exists
// Needs JAVA_HOME and ANDROID_HOME (build-tools for zipalign). Pure Node, no other dependency: it reads
// the zip files itself, so it runs the same on Windows, macOS and Linux.
import { execFileSync, spawnSync } from "node:child_process";
import { existsSync, mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { inflateRawSync } from "node:zlib";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const args = new Set(process.argv.slice(2));
const win = process.platform === "win32";
const out = join(root, "androidApp/build/outputs");
const files = {
  apk: join(out, "apk/release/androidApp-release.apk"),
  aab: join(out, "bundle/release/androidApp-release.aab"),
  mapping: join(out, "mapping/release/mapping.txt"),
};
const EXPECT = { targetSdk: 36, minSdk: 24, applicationId: "es.sebas1705.haltera" };
const failures = [];
const fail = (msg) => failures.push(msg);

// ---------------------------------------------------------------- build
if (!args.has("--skip-build")) {
  console.log("Building the release AAB and APK...");
  const gradle = join(root, win ? "gradlew.bat" : "gradlew");
  const run = spawnSync(gradle, [":androidApp:bundleRelease", ":androidApp:assembleRelease", "--stacktrace"], {
    cwd: root, stdio: "inherit", shell: win,
  });
  if (run.status !== 0) { console.error("Gradle failed"); process.exit(1); }
}
for (const [name, path] of Object.entries(files)) {
  if (!existsSync(path)) { console.error(`Missing ${name}: ${path}`); process.exit(1); }
}

// ---------------------------------------------------------------- zip reader
function readZip(path) {
  const buf = readFileSync(path);
  let eocd = buf.length - 22;
  while (eocd >= 0 && buf.readUInt32LE(eocd) !== 0x06054b50) eocd--;
  if (eocd < 0) throw new Error(`${path} is not a zip`);
  const count = buf.readUInt16LE(eocd + 10);
  let p = buf.readUInt32LE(eocd + 16);
  const entries = new Map();
  for (let i = 0; i < count; i++) {
    const method = buf.readUInt16LE(p + 10);
    const csize = buf.readUInt32LE(p + 20);
    const size = buf.readUInt32LE(p + 24);
    const nameLen = buf.readUInt16LE(p + 28), extraLen = buf.readUInt16LE(p + 30), commentLen = buf.readUInt16LE(p + 32);
    const local = buf.readUInt32LE(p + 42);
    const name = buf.toString("utf8", p + 46, p + 46 + nameLen);
    entries.set(name, { name, method, csize, size, local });
    p += 46 + nameLen + extraLen + commentLen;
  }
  const read = (name) => {
    const e = entries.get(name);
    const start = e.local + 30 + buf.readUInt16LE(e.local + 26) + buf.readUInt16LE(e.local + 28);
    const data = buf.subarray(start, start + e.csize);
    return e.method === 0 ? data : inflateRawSync(data);
  };
  return { entries, read };
}

// ---------------------------------------------------------------- ELF alignment
/** p_align of the PT_LOAD segments of a 64-bit little-endian ELF (the minimum is what counts). */
function elfLoadAlign(data) {
  if (data.readUInt32BE(0) !== 0x7f454c46) return { error: "not an ELF file" };
  const is64 = data[4] === 2;
  const phoff = is64 ? Number(data.readBigUInt64LE(0x20)) : data.readUInt32LE(0x1c);
  const phentsize = data.readUInt16LE(is64 ? 0x36 : 0x2a);
  const phnum = data.readUInt16LE(is64 ? 0x38 : 0x2c);
  let min = Infinity;
  for (let i = 0; i < phnum; i++) {
    const h = phoff + i * phentsize;
    if (data.readUInt32LE(h) !== 1) continue; // PT_LOAD
    const align = is64 ? Number(data.readBigUInt64LE(h + 48)) : data.readUInt32LE(h + 28);
    min = Math.min(min, align);
  }
  return { align: min === Infinity ? 0 : min, is64 };
}

function checkLibs(label, zip, prefix) {
  const rows = [];
  for (const name of zip.entries.keys()) {
    const m = new RegExp(`^${prefix}lib/([^/]+)/(.+\\.so)$`).exec(name);
    if (!m) continue;
    const r = elfLoadAlign(zip.read(name));
    const abi = m[1];
    const required = abi === "arm64-v8a" || abi === "x86_64"; // 64-bit ABIs: the 16 KB rule
    const ok = !r.error && (!required || r.align >= 0x4000);
    if (!ok) fail(`${label}: ${name} is not 16 KB aligned (p_align ${r.align ?? r.error})`);
    rows.push({ abi, lib: m[2], align: r.align ?? 0, ok, required });
  }
  return rows;
}

const apk = readZip(files.apk);
const aab = readZip(files.aab);
const aabLibs = checkLibs("AAB", aab, "base/");
const apkLibs = checkLibs("APK", apk, "");

// zipalign (APK only; the AAB has no page alignment of its own: bundletool does it on the split APKs)
function findZipalign() {
  const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT;
  if (!sdk || !existsSync(join(sdk, "build-tools"))) return null;
  const versions = readdirSync(join(sdk, "build-tools")).sort((a, b) => a.localeCompare(b, undefined, { numeric: true }));
  for (const v of versions.reverse()) {
    const exe = join(sdk, "build-tools", v, win ? "zipalign.exe" : "zipalign");
    if (existsSync(exe)) return { exe, version: v };
  }
  return null;
}
const za = findZipalign();
let zipalignResult = "zipalign not found (set ANDROID_HOME); ELF check above still applies";
if (za) {
  const r = spawnSync(za.exe, ["-c", "-P", "16", "-v", "4", files.apk], { encoding: "utf8" });
  const last = (r.stdout || r.stderr).trim().split("\n").filter(Boolean).pop() ?? "";
  zipalignResult = `build-tools ${za.version}: \`zipalign -c -P 16 -v 4\` ${r.status === 0 ? "passed" : "FAILED"} (${last})`;
  if (r.status !== 0) fail(`APK: zipalign -P 16 failed: ${last}`);
}

// ---------------------------------------------------------------- merged manifest
function findManifest() {
  const base = join(root, "androidApp/build/intermediates/merged_manifests/release");
  const stack = [base];
  while (stack.length) {
    const d = stack.pop();
    if (!existsSync(d)) continue;
    for (const f of readdirSync(d)) {
      const p = join(d, f);
      if (statSync(p).isDirectory()) stack.push(p);
      else if (f === "AndroidManifest.xml") return p;
    }
  }
  return null;
}
let manifestPath = findManifest();
let manifestXml;
if (manifestPath) {
  manifestXml = readFileSync(manifestPath, "utf8");
} else {
  const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT;
  const tool = join(sdk ?? "", "cmdline-tools/latest/bin", win ? "apkanalyzer.bat" : "apkanalyzer");
  manifestXml = execFileSync(tool, ["manifest", "print", files.apk], { encoding: "utf8", shell: win });
  manifestPath = "apkanalyzer manifest print";
}

/** Minimal XML tree: enough for AndroidManifest.xml (no CDATA, no entities that matter). */
function parseXml(xml) {
  const tokens = xml.replace(/<\?[\s\S]*?\?>|<!--[\s\S]*?-->/g, "").match(/<[^>]+>/g) ?? [];
  const rootNode = { tag: "#root", attrs: {}, children: [] };
  const stack = [rootNode];
  for (const t of tokens) {
    if (t.startsWith("</")) { stack.pop(); continue; }
    const tag = /^<([^\s/>]+)/.exec(t)[1];
    const attrs = {};
    for (const m of t.matchAll(/([\w:.-]+)\s*=\s*"([^"]*)"/g)) attrs[m[1]] = m[2];
    const node = { tag, attrs, children: [] };
    stack.at(-1).children.push(node);
    if (!t.endsWith("/>")) stack.push(node);
  }
  return rootNode.children[0];
}
const manifest = parseXml(manifestXml);
const kids = (n, tag) => n.children.filter((c) => c.tag === tag);
const attr = (n, name) => n.attrs[`android:${name}`];
const app = kids(manifest, "application")[0];
const usesSdk = kids(manifest, "uses-sdk")[0]?.attrs ?? {};
const permissions = kids(manifest, "uses-permission").map((p) => attr(p, "name")).sort();
const permissionsSdk23 = kids(manifest, "uses-permission-sdk-23").map((p) => attr(p, "name")).sort();
const features = kids(manifest, "uses-feature").map((f) => `${attr(f, "name") ?? attr(f, "glEsVersion")}${attr(f, "required") === "false" ? " (optional)" : ""}`);
const components = ["activity", "activity-alias", "service", "receiver", "provider"].flatMap((tag) =>
  kids(app, tag).map((n) => ({
    tag,
    name: attr(n, "name"),
    exported: attr(n, "exported") ?? "(unset)",
    permission: attr(n, "permission") ?? "",
    foregroundServiceType: attr(n, "foregroundServiceType") ?? "",
    filters: kids(n, "intent-filter").map((f) => ({
      actions: kids(f, "action").map((a) => attr(a, "name")),
      categories: kids(f, "category").map((a) => attr(a, "name")),
      data: kids(f, "data").map((d) => Object.entries(d.attrs).map(([k, v]) => `${k.replace("android:", "")}=${v}`).join(" ")),
    })),
    properties: kids(n, "property").map((p) => `${attr(p, "name")}=${attr(p, "value")}`),
  })));

const targetSdk = Number(usesSdk["android:targetSdkVersion"]);
const minSdk = Number(usesSdk["android:minSdkVersion"]);
if (targetSdk !== EXPECT.targetSdk) fail(`targetSdk is ${targetSdk}, expected ${EXPECT.targetSdk}`);
if (minSdk !== EXPECT.minSdk) fail(`minSdk is ${minSdk}, expected ${EXPECT.minSdk}`);
if (manifest.attrs.package !== EXPECT.applicationId) fail(`package is ${manifest.attrs.package}`);
const debuggable = attr(app, "debuggable");
if (debuggable === "true") fail("the release manifest is debuggable");

// ---------------------------------------------------------------- test-only code in release
const TEST_ONLY = ["leakcanary", "androidx/compose/ui/tooling", "androidx/compose/ui/test", "androidx/test/espresso", "androidx/test/runner", "androidx/test/ext", "org/junit/"];
const dexNames = [...apk.entries.keys()].filter((n) => /^classes\d*\.dex$/.test(n));
const testHits = [];
for (const dex of dexNames) {
  const text = apk.read(dex).toString("latin1");
  for (const needle of TEST_ONLY) if (text.includes(needle)) testHits.push(`${needle} in ${dex}`);
}
const apkTestEntries = [...apk.entries.keys()].filter((n) => /leakcanary/i.test(n));
if (testHits.length || apkTestEntries.length) fail(`test-only code in the release APK: ${[...testHits, ...apkTestEntries].join(", ")}`);

// ---------------------------------------------------------------- report
const mb = (p) => `${(statSync(p).size / 1048576).toFixed(2)} MB`;
const mappingLines = readFileSync(files.mapping, "utf8").split("\n").length;
const policy = [];
const has = (name) => permissions.includes(name) || permissionsSdk23.includes(name);
const flag = (cond, text) => policy.push(`- [${cond ? "FLAG" : "ok"}] ${text}`);
flag(has("android.permission.SCHEDULE_EXACT_ALARM"), "`SCHEDULE_EXACT_ALARM` (restricted: Play only accepts apps whose core function is an alarm or calendar; the declaration is in Play Console, App content)");
flag(has("android.permission.USE_EXACT_ALARM"), "`USE_EXACT_ALARM` (granted at install, Play allows it only for alarm clock or calendar apps; a rest timer is not one)");
flag(has("android.permission.POST_NOTIFICATIONS"), "`POST_NOTIFICATIONS` (runtime permission on Android 13+; the app must work when it is denied)");
flag(has("android.permission.FOREGROUND_SERVICE"), "`FOREGROUND_SERVICE` (every foreground service must declare a type, and Play asks for a declaration per type)");
const specialUse = components.filter((c) => c.foregroundServiceType.includes("specialUse"));
flag(specialUse.length > 0, `foreground service type \`specialUse\`${specialUse.length ? ` on ${specialUse.map((c) => c.name).join(", ")}: needs the \`PROPERTY_SPECIAL_USE_FGS_SUBTYPE\` property and the Play Console foreground service declaration with a description and a demo video` : ""}`);
const health = [...permissions, ...permissionsSdk23].filter((p) => p.startsWith("android.permission.health."));
flag(health.length > 0, `Health Connect permissions${health.length ? ` (${health.map((p) => p.replace("android.permission.health.", "")).join(", ")})` : ""}: each needs the Play Console Health Connect declaration with a justification (PLAY-CONSOLE-GUIDE.md 3.8.1), the rationale activity and a Health Connect section in the privacy policy`);
flag(health.some((p) => /BACKGROUND|HISTORY/.test(p)), "Health Connect background or history read permission (not used by Haltera: remove it unless a reviewed feature needs it)");
flag(has("android.permission.INTERNET"),"`INTERNET` (needed for optional sync and sign-in; declare network use in the Data safety form)");
const exported = components.filter((c) => c.exported === "true");
flag(exported.some((c) => c.tag !== "activity"), "exported components other than activities (check each is intended)");
const backup = {
  allowBackup: attr(app, "allowBackup") ?? "(default true)",
  dataExtractionRules: attr(app, "dataExtractionRules") ?? "(none)",
  fullBackupContent: attr(app, "fullBackupContent") ?? "(none)",
};

const table = (head, rows) => `| ${head.join(" | ")} |\n|${head.map(() => "---").join("|")}|\n${rows.map((r) => `| ${r.join(" | ")} |`).join("\n")}`;
const libRows = (libs) => libs.map((l) => [l.abi, `\`${l.lib}\``, `0x${l.align.toString(16)} (${l.align / 1024} KB)`, l.ok ? "ok" : "**FAIL**"]);
const generated = new Date().toISOString().slice(0, 10);

const report = `# Release audit

Generated by \`node scripts/verify-release.mjs\` on ${generated}. Do not edit by hand: run the script and
review the diff. Decisions that need judgement (backup, exact alarms) are in
[the decisions section](#decisions) and are kept by the script only as far as the findings show.

## Result

${failures.length ? failures.map((f) => `- **FAIL**: ${f}`).join("\n") : "All automated checks passed."}

## Build outputs

${table(["Output", "Path", "Size"], [
  ["AAB", "`androidApp/build/outputs/bundle/release/androidApp-release.aab`", mb(files.aab)],
  ["APK (universal)", "`androidApp/build/outputs/apk/release/androidApp-release.apk`", mb(files.apk)],
  ["R8 mapping", `\`androidApp/build/outputs/mapping/release/mapping.txt\``, `${mb(files.mapping)}, ${mappingLines} lines`],
])}

Locally the release build is signed with the debug key (no keystore variables); CI signs with the upload key.
\`release.yml\` passes the mapping file to Play so crashes and ANRs are deobfuscated in the Console.

## SDK levels and flags

${table(["Check", "Value", "Expected"], [
  ["applicationId", manifest.attrs.package, EXPECT.applicationId],
  ["targetSdk", String(targetSdk), String(EXPECT.targetSdk)],
  ["minSdk", String(minSdk), String(EXPECT.minSdk)],
  ["debuggable", debuggable ?? "(unset, false)", "false / unset"],
  ["test-only code in the release dex", testHits.length ? testHits.join(", ") : "none", "none"],
])}

Searched in the release dex: ${TEST_ONLY.map((t) => `\`${t}\``).join(", ")}. (A bare androidx/test/platform/app/InstrumentationRegistry reference is a known
ProfileInstaller/startup type name, not test code, and is not flagged.)

## 16 KB page size

Play requires native libraries to be aligned for 16 KB pages for apps targeting Android 15+. The rule is checked
on the ELF program headers (every \`PT_LOAD\` segment must have \`p_align\` >= 0x4000) of the 64-bit ABIs;
32-bit ABIs are listed for information.

- ${zipalignResult}
- Native libraries in the AAB: ${aabLibs.length}; in the APK: ${apkLibs.length}. ${aabLibs.length + apkLibs.length === 0 ? "**The app ships no native library**: nothing to align." : ""}

${aabLibs.length ? `### AAB\n\n${table(["ABI", "Library", "p_align", "16 KB"], libRows(aabLibs))}\n` : ""}
${apkLibs.length ? `### APK\n\n${table(["ABI", "Library", "p_align", "16 KB"], libRows(apkLibs))}\n` : ""}

## Merged release manifest

Source: ${manifestPath.startsWith("apkanalyzer") ? manifestPath : "`" + manifestPath.replace(root + "\\", "").replace(root + "/", "").replaceAll("\\", "/") + "`"}

### Permissions

${[...permissions, ...permissionsSdk23.map((p) => `${p} (sdk 23+)`)].map((p) => `- \`${p}\``).join("\n") || "- none"}

### Features

${features.map((f) => `- \`${f}\``).join("\n") || "- none"}

### Application

${table(["Attribute", "Value"], [
  ["allowBackup", backup.allowBackup],
  ["dataExtractionRules", backup.dataExtractionRules],
  ["fullBackupContent", backup.fullBackupContent],
  ["usesCleartextTraffic", attr(app, "usesCleartextTraffic") ?? "(unset)"],
  ["networkSecurityConfig", attr(app, "networkSecurityConfig") ?? "(none)"],
])}

### Components

${table(["Kind", "Name", "Exported", "Permission", "FGS type"], components.map((c) => [c.tag, `\`${c.name}\``, c.exported, c.permission ? `\`${c.permission}\`` : "", c.foregroundServiceType || ""]))}

### Intent filters

${components.filter((c) => c.filters.length).map((c) => `**${c.name}**\n\n${c.filters.map((f) =>
  `- actions: ${f.actions.map((a) => `\`${a}\``).join(", ")}${f.categories.length ? `; categories: ${f.categories.map((a) => `\`${a.replace("android.intent.category.", "")}\``).join(", ")}` : ""}${f.data.length ? `; data: ${f.data.map((d) => `\`${d}\``).join(", ")}` : ""}`).join("\n")}`).join("\n\n") || "none"}

## Play policy review

${policy.join("\n")}

## Decisions

${readDecisions()}
`;

function readDecisions() {
  // The judgement text lives in docs/play/audit-decisions.md so this file stays reproducible.
  const p = join(root, "docs/play/audit-decisions.md");
  return existsSync(p) ? readFileSync(p, "utf8").trim() : "_No decisions recorded (docs/play/audit-decisions.md)._";
}

if (!args.has("--no-report")) {
  mkdirSync(join(root, "docs/play"), { recursive: true });
  writeFileSync(join(root, "docs/play/RELEASE-AUDIT.md"), report);
  console.log("Wrote docs/play/RELEASE-AUDIT.md");
}
console.log(`AAB ${mb(files.aab)}, APK ${mb(files.apk)}, targetSdk ${targetSdk}, minSdk ${minSdk}, native libs ${aabLibs.length} in AAB`);
if (failures.length) {
  console.error(failures.map((f) => `FAIL: ${f}`).join("\n"));
  process.exit(1);
}
console.log("Release verification passed");
