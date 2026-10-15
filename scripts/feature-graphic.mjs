#!/usr/bin/env node
// Google Play feature graphic (1024 x 500, 24-bit PNG, no alpha) for each store language, from
// brand/tokens.json (colours) and brand/logo.svg (the barbell mark). Font: Barlow Semi Condensed 600
// (core/ui composeResources), Inter for the tagline.
//
//   node scripts/feature-graphic.mjs          # writes brand/feature-graphic-<lang>.svg and the PNGs
//   node scripts/feature-graphic.mjs --check  # fails if the SVGs on disk differ from what would be
//                                             # generated, or a PNG is missing, not 1024x500 or has alpha
//
// The PNG is rendered with headless Edge or Chrome, like scripts/brand.mjs --png.
import { execFileSync } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { deflateSync, inflateSync } from "node:zlib";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
export const WIDTH = 1024;
export const HEIGHT = 500;

const tokens = JSON.parse(readFileSync(join(root, "brand/tokens.json"), "utf8"));
const dark = tokens.colors.dark;
const logoSvg = readFileSync(join(root, "brand/logo.svg"), "utf8");

export const LANGUAGES = {
  "en-US": { tagline: "Your training and nutrition logbook", label: "Haltera, your training and nutrition logbook" },
  "es-ES": { tagline: "Tu diario de entrenamiento y nutrición", label: "Haltera, tu diario de entrenamiento y nutrición" },
};

const escapeXml = (text) => text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");

/** The inner drawing of brand/logo.svg (everything inside its root element). */
function logoInner() {
  const match = logoSvg.match(/<svg[^>]*>([\s\S]*)<\/svg>/);
  if (!match) throw new Error("brand/logo.svg has no <svg> root");
  return match[1];
}

/** The graphic as SVG text. Fonts are referenced by family; the renderer page loads the files. */
export function featureGraphicSvg(lang) {
  const { tagline, label } = LANGUAGES[lang];
  const logoSize = 270;
  const logoX = 80;
  const logoY = (HEIGHT - logoSize) / 2;
  const textX = logoX + logoSize + 48;
  // The mark's own 108-unit grid scaled to logoSize; its rounded square is graphite, like the background.
  const scale = logoSize / tokens.logo.grid;
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${WIDTH}" height="${HEIGHT}" viewBox="0 0 ${WIDTH} ${HEIGHT}" role="img" aria-label="${escapeXml(label)}">
<rect width="${WIDTH}" height="${HEIGHT}" fill="${tokens.logo.background}"/>
<rect x="0" y="${HEIGHT - 10}" width="${WIDTH}" height="10" fill="${dark.accent}"/>
<g transform="translate(${logoX} ${logoY}) scale(${scale})">${logoInner()}</g>
<text x="${textX}" y="248" font-family="'Barlow Semi Condensed', 'Arial Narrow', sans-serif" font-weight="600" font-size="150" letter-spacing="2" fill="${dark.onBackground}">Haltera</text>
<rect x="${textX}" y="282" width="96" height="8" rx="4" fill="${dark.accent}"/>
<text x="${textX}" y="348" font-family="Inter, Arial, sans-serif" font-weight="500" font-size="32" fill="${dark.onSurfaceVariant}">${escapeXml(tagline)}</text>
</svg>
`;
}

/** Decodes an 8-bit non-interlaced RGB or RGBA PNG. */
export function decodePng(buffer) {
  if (buffer.readUInt32BE(0) !== 0x89504e47) throw new Error("not a PNG");
  let offset = 8;
  let header;
  const data = [];
  while (offset < buffer.length) {
    const length = buffer.readUInt32BE(offset);
    const type = buffer.toString("ascii", offset + 4, offset + 8);
    const body = buffer.subarray(offset + 8, offset + 8 + length);
    if (type === "IHDR") {
      header = { width: body.readUInt32BE(0), height: body.readUInt32BE(4), depth: body[8], colorType: body[9], interlace: body[12] };
    } else if (type === "IDAT") data.push(body);
    offset += 12 + length;
  }
  if (!header) throw new Error("PNG without IHDR");
  return { ...header, data: Buffer.concat(data) };
}

/** Pixels (RGB, row after row) of a decoded PNG, with filters undone. */
function pixels({ width, height, depth, colorType, interlace, data }) {
  if (depth !== 8 || interlace !== 0 || (colorType !== 2 && colorType !== 6)) {
    throw new Error(`unsupported PNG (depth ${depth}, colour type ${colorType}, interlace ${interlace})`);
  }
  const bpp = colorType === 6 ? 4 : 3;
  const stride = width * bpp;
  const raw = inflateSync(data);
  const out = Buffer.alloc(height * stride);
  for (let y = 0; y < height; y++) {
    const filter = raw[y * (stride + 1)];
    for (let x = 0; x < stride; x++) {
      const v = raw[y * (stride + 1) + 1 + x];
      const left = x >= bpp ? out[y * stride + x - bpp] : 0;
      const up = y > 0 ? out[(y - 1) * stride + x] : 0;
      const upLeft = y > 0 && x >= bpp ? out[(y - 1) * stride + x - bpp] : 0;
      let predictor = 0;
      if (filter === 1) predictor = left;
      else if (filter === 2) predictor = up;
      else if (filter === 3) predictor = (left + up) >> 1;
      else if (filter === 4) {
        const p = left + up - upLeft;
        const [pa, pb, pc] = [Math.abs(p - left), Math.abs(p - up), Math.abs(p - upLeft)];
        predictor = pa <= pb && pa <= pc ? left : pb <= pc ? up : upLeft;
      }
      out[y * stride + x] = (v + predictor) & 0xff;
    }
  }
  if (bpp === 3) return out;
  const rgb = Buffer.alloc(width * height * 3);
  for (let i = 0, j = 0; i < out.length; i += 4, j += 3) {
    // The graphic is fully opaque; composite on the background colour anyway so alpha never leaks.
    const a = out[i + 3] / 255;
    const bg = [0x1e, 0x21, 0x26];
    for (let c = 0; c < 3; c++) rgb[j + c] = Math.round(out[i + c] * a + bg[c] * (1 - a));
  }
  return rgb;
}

const crcTable = Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});
function crc32(buffer) {
  let c = 0xffffffff;
  for (const byte of buffer) c = crcTable[(c ^ byte) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function chunk(type, body) {
  const head = Buffer.alloc(8);
  head.writeUInt32BE(body.length, 0);
  head.write(type, 4, "ascii");
  const tail = Buffer.alloc(4);
  tail.writeUInt32BE(crc32(Buffer.concat([head.subarray(4), body])), 0);
  return Buffer.concat([head, body, tail]);
}

/** Encodes RGB pixels (cropped or padded with the background to width x height) as a 24-bit PNG. */
export function encodeRgbPng(rgb, srcWidth, srcHeight, width, height) {
  const stride = width * 3;
  const rows = Buffer.alloc(height * (stride + 1));
  for (let y = 0; y < height; y++) {
    const row = y * (stride + 1);
    rows[row] = 0;
    for (let x = 0; x < stride; x++) {
      rows[row + 1 + x] = y < srcHeight && x < srcWidth * 3 ? rgb[y * srcWidth * 3 + x] : [0x1e, 0x21, 0x26][x % 3];
    }
  }
  const header = Buffer.alloc(13);
  header.writeUInt32BE(width, 0);
  header.writeUInt32BE(height, 4);
  header[8] = 8; // bit depth
  header[9] = 2; // truecolour, no alpha
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", header),
    chunk("IDAT", deflateSync(rows, { level: 9 })),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}

function findBrowser() {
  const candidates = [
    "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
    "C:/Program Files/Microsoft/Edge/Application/msedge.exe",
    "C:/Program Files/Google/Chrome/Application/chrome.exe",
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "/usr/bin/google-chrome",
    "/usr/bin/chromium",
  ];
  const browser = candidates.find(existsSync);
  if (!browser) throw new Error("No Edge or Chrome found to render the PNG");
  return browser;
}

const fontFace = (family, weight, file) =>
  `@font-face{font-family:'${family}';font-weight:${weight};src:url('${pathToFileURL(join(root, file)).href}')}`;

function render(svg, out) {
  const page = join(tmpdir(), "haltera-feature-graphic.html");
  const shot = join(tmpdir(), "haltera-feature-graphic.png");
  writeFileSync(
    page,
    `<!doctype html><meta charset="utf-8"><style>${fontFace("Barlow Semi Condensed", 600, "core/ui/src/commonMain/composeResources/font/barlow_semicondensed_semibold.ttf")}${fontFace("Inter", "400 500", "core/ui/src/commonMain/composeResources/font/inter_variable.ttf")}html,body{margin:0;background:#1E2126}svg{display:block}</style><body>${svg}</body>`,
  );
  rmSync(shot, { force: true });
  execFileSync(
    findBrowser(),
    ["--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1", `--window-size=${WIDTH},${HEIGHT}`, `--screenshot=${shot}`, pathToFileURL(page).href],
    { stdio: "ignore", timeout: 60_000 },
  );
  // Headless Edge can return before the screenshot is on disk.
  for (let i = 0; i < 100 && !existsSync(shot); i++) execFileSync(process.execPath, ["-e", "setTimeout(()=>{},100)"]);
  const decoded = decodePng(readFileSync(shot));
  const png = encodeRgbPng(pixels(decoded), decoded.width, decoded.height, WIDTH, HEIGHT);
  mkdirSync(dirname(out), { recursive: true });
  writeFileSync(out, png);
  rmSync(page);
  rmSync(shot);
}

const svgPath = (lang) => join(root, `brand/feature-graphic-${lang}.svg`);
const pngPath = (lang) => join(root, `fastlane/metadata/android/${lang}/images/featureGraphic.png`);

/** Problems with the SVG sources and PNGs on disk (empty when everything is up to date). */
export function check() {
  const problems = [];
  for (const lang of Object.keys(LANGUAGES)) {
    if (!existsSync(svgPath(lang)) || readFileSync(svgPath(lang), "utf8") !== featureGraphicSvg(lang)) {
      problems.push(`brand/feature-graphic-${lang}.svg is out of date (run node scripts/feature-graphic.mjs)`);
    }
    if (!existsSync(pngPath(lang))) {
      problems.push(`${lang}: featureGraphic.png is missing`);
      continue;
    }
    const header = decodePng(readFileSync(pngPath(lang)));
    if (header.width !== WIDTH || header.height !== HEIGHT) problems.push(`${lang}: PNG is ${header.width}x${header.height}, not ${WIDTH}x${HEIGHT}`);
    if (header.colorType !== 2 || header.depth !== 8) problems.push(`${lang}: PNG is not 24-bit RGB without alpha`);
  }
  return problems;
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  if (process.argv.includes("--check")) {
    const problems = check();
    if (problems.length) {
      console.error(problems.join("\n"));
      process.exit(1);
    }
    console.log("Feature graphics up to date");
  } else {
    for (const lang of Object.keys(LANGUAGES)) {
      writeFileSync(svgPath(lang), featureGraphicSvg(lang));
      render(featureGraphicSvg(lang), pngPath(lang));
      console.log("wrote", `fastlane/metadata/android/${lang}/images/featureGraphic.png`);
    }
  }
}
