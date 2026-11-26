---
description: Run the checks AGENTS.md requires before finishing, and report what was and was not verified
---

Verify the current branch the way AGENTS.md requires. Do not change code in this command; report.

1. Read the `templetry:checks` fence in `AGENTS.md` and run exactly that command. If the diff touches
   `brand/` or `scripts/brand.mjs`, also run `node scripts/brand.mjs --check`. If it touches `scripts/`, run the
   matching `node --test` files. If it touches `firebase/firestore.rules`, run the rules tests from AGENTS.md.
2. If the schema changed, confirm a `migrations/n.sqm`, a new snapshot and a migration test exist, and that no shipped
   snapshot was edited (`git diff --stat -- data/local/src/commonMain/sqldelight/databases`).
3. If UI strings changed, confirm every key exists in both `values/strings.xml` and `values-es/strings.xml`.
4. Report a table: check, command, result. List separately what you could **not** test (for example iOS runtime,
   emulator flows) and why. Never write "should work" for something you did not run.

Emulator QA is a separate pass: both emulators (5554 and 5556), Spanish and English, back, rotation and process death.
Say whether it was done.
