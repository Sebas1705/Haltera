// node --test scripts/agent-guard.test.mjs
import assert from "node:assert/strict";
import { test } from "node:test";
import { evaluate } from "./agent-guard.mjs";

const edit = (file_path) => evaluate({ tool_name: "Edit", tool_input: { file_path } });
const bash = (command, branch = "chore/x") => evaluate({ tool_name: "Bash", tool_input: { command } }, { branch });

test("generated and shipped files are blocked, with Windows and POSIX paths", () => {
  for (const p of [
    "C:\\repo\\data\\local\\src\\commonMain\\sqldelight\\databases\\8.db",
    "/repo/brand/icon.svg",
    "/repo/androidApp/src/main/res/mipmap-hdpi/ic_launcher.png",
    "/repo/androidApp/src/main/res/values-v31/themes.xml",
    "/repo/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/Contents.json",
    "/repo/.templetry-answers.yml",
  ]) {
    assert.equal(edit(p).block, true, p);
  }
});

test("secret files are blocked for writes", () => {
  for (const p of ["/repo/androidApp/google-services.json", "/repo/.env", "/repo/.env.local", "/repo/release.keystore", "/repo/secrets.properties"]) {
    assert.equal(edit(p).block, true, p);
  }
});

test("ordinary source, docs and the brand tokens are allowed", () => {
  for (const p of [
    "/repo/feature/log/src/commonMain/kotlin/Foo.kt",
    "/repo/data/local/src/commonMain/sqldelight/migrations/8.sqm",
    "/repo/data/local/src/commonMain/sqldelight/databases/README.md",
    "/repo/brand/tokens.json",
    "/repo/androidApp/src/main/res/values/strings.xml",
    "/repo/docs/RELEASE.md",
  ]) {
    assert.equal(edit(p).block, false, p);
  }
});

test("force pushes and skipped hooks are blocked", () => {
  assert.equal(bash("git push --force origin chore/x").block, true);
  assert.equal(bash("git push origin chore/x -f").block, true);
  assert.equal(bash("git push --force-with-lease").block, true);
  assert.equal(bash("git commit --no-verify -m x").block, true);
  assert.equal(bash("git push --no-verify").block, true);
});

test("committing or pushing on main is blocked, on a branch it is allowed", () => {
  assert.equal(bash("git commit -m x", "main").block, true);
  assert.equal(bash("git push origin main", "main").block, true);
  assert.equal(bash("git commit -m x", "chore/agent-pilot").block, false);
  assert.equal(bash("git push -u origin chore/agent-pilot").block, false);
  assert.equal(bash("git status", "main").block, false);
});

test("pushing to main from another branch is blocked, branch names that contain main are not", () => {
  assert.equal(bash("git push origin main").block, true);
  assert.equal(bash("git push origin HEAD:main").block, true);
  assert.equal(bash("git push origin HEAD:refs/heads/master").block, true);
  assert.equal(bash("git push -u origin chore/main-menu").block, false);
  assert.equal(bash("git push -u origin fix/issue-12").block, false);
  assert.equal(bash("git log main..HEAD").block, false);
});

test("Doppler secret values are not printed, names are fine", () => {
  assert.equal(bash("doppler secrets -p ironlog -c prd").block, true);
  assert.equal(bash("doppler secrets get FOO --plain").block, true);
  assert.equal(bash("doppler secrets -p ironlog -c prd --only-names").block, false);
  assert.equal(bash("doppler run -p ironlog -c prd -- ./gradlew assembleRelease").block, false);
});

test("reading secret files through a shell is blocked", () => {
  assert.equal(bash("cat androidApp/google-services.json").block, true);
  assert.equal(bash("Get-Content .env").block, true);
  assert.equal(bash("cp release.keystore /tmp/").block, true);
  assert.equal(bash("git add androidApp/src && git status").block, false);
});

test("a payload without a tool is allowed", () => {
  assert.equal(evaluate({}).block, false);
});
