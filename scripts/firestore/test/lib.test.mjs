import { test } from "node:test";
import assert from "node:assert/strict";
import { decide, mapPath, assertOutsideRepo, updatedAtOf, parseArgs, segments } from "../lib.mjs";

const doc = (t, extra = {}) => ({ fields: { updatedAt: { integerValue: String(t) }, payload: { stringValue: "x" }, ...extra } });
const P = "projects/p/databases/(default)/documents/";

test("LWW: absent destination copies", () => assert.equal(decide(doc(1), undefined), "copy"));
test("LWW: newer source copies", () => assert.equal(decide(doc(5), doc(3)), "copy"));
test("LWW: newer destination is kept", () => assert.equal(decide(doc(3), doc(5)), "skip-newer"));
test("LWW: equal and identical is skipped", () => assert.equal(decide(doc(4), doc(4)), "skip-same"));
test("LWW: equal but different is a conflict", () =>
  assert.equal(decide(doc(4), doc(4, { payload: { stringValue: "y" } })), "conflict"));
test("LWW: newer destination tombstone beats older source", () =>
  assert.equal(decide(doc(3), doc(9, { deleted: { booleanValue: true } })), "skip-newer"));
test("LWW: newer source tombstone wins", () =>
  assert.equal(decide(doc(9, { deleted: { booleanValue: true } }), doc(3)), "copy"));
test("updatedAt accepts timestamps", () =>
  assert.equal(updatedAtOf({ fields: { updatedAt: { timestampValue: "1970-01-01T00:00:01Z" } } }), 1000));

test("mapPath rewrites the uid", () => assert.equal(mapPath(`${P}users/a/routines/r1`, "a", "b"), "users/b/routines/r1"));
test("mapPath ignores other users", () => assert.equal(mapPath(`${P}users/c/routines/r1`, "a", "b"), null));
test("segments strips the prefix", () => assert.deepEqual(segments(`${P}users/a/x/y`), ["users", "a", "x", "y"]));

test("backup refuses an output inside the repo", () => {
  assert.throws(() => assertOutsideRepo("/repo/backup", "/repo"), /Refusing/);
  assert.throws(() => assertOutsideRepo("/repo", "/repo"), /Refusing/);
  assert.doesNotThrow(() => assertOutsideRepo("/elsewhere/backup", "/repo"));
  assert.throws(() => assertOutsideRepo(undefined, "/repo"), /required/);
});

test("dry run is the default: apply only with the explicit flag", () => {
  assert.notEqual(parseArgs(["--from", "a", "--to", "b"]).apply, true);
  assert.equal(parseArgs(["--from", "a", "--to", "b", "--apply"]).apply, true);
});
