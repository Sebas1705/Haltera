// The app's display name, read from the single place that defines it (the English app_name resource),
// so store tooling never hardcodes it. Used as a module, or printed: `node scripts/lib/app-name.mjs`.
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "../..");
const xml = readFileSync(join(root, "composeApp/src/commonMain/composeResources/values/strings.xml"), "utf8");
export const appName = /<string name="app_name">([^<]+)<\/string>/.exec(xml)?.[1]?.trim();
if (!appName) throw new Error("app_name not found in composeResources/values/strings.xml");

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) console.log(appName);
