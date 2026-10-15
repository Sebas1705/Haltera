# Generic foods database

Source of the generic foods bundled in the app (nutrition design §2). The app reads the generated
`composeApp/src/commonMain/composeResources/files/foods.json`; never edit that file by hand.

| File | Role |
|---|---|
| `sources/sources.json` | Pinned USDA FoodData Central downloads (Foundation Foods, SR Legacy) with their SHA-256 |
| `sources/usda-foods.json` | Reduced snapshot of every food in those downloads, refreshed by `node scripts/nutrition/fetch.mjs` |
| `foods.yaml` | `foodsVersion`, `released` flag, serving kinds and the curated list of foods (USDA id, English name, unit, serving) |
| `i18n/es.yaml` | Spanish name per USDA id |
| `ids.lock.json` | Every id ever emitted (removed ones become `deprecatedIds`) |
| `ATTRIBUTION.md` | Generated credits, shown in the app |

Workflow:

    node scripts/nutrition/fetch.mjs                # refresh the snapshot (network)
    node scripts/nutrition/build.mjs                # regenerate outputs
    node scripts/nutrition/build.mjs --missing-es   # foods still lacking Spanish
    node scripts/nutrition/build.mjs --check        # what CI runs
    npm test --prefix scripts/nutrition             # unit tests

Rules:

- Values are per 100 g. Foods with `unit: ML` (drinks) are shown per 100 ml, taking 1 ml as 1 g.
  Salt is sodium × 2.5; negative USDA values (carbohydrate by difference) become 0.
- Ids are `usda-<fdcId>` and stable forever once shipped; a food removed from `foods.yaml` is deprecated
  in the app (hidden from search, kept for logged days), never deleted.
- While `released: false`, the current `foodsVersion` can be edited freely. Once a version ships, set
  `released: true`: any later change to the generated file needs `foodsVersion` + 1 and `released: false`
  again (the build refuses otherwise). The app imports a bundled version only when it is newer.
- The pipeline never invents Spanish: every food needs its name in `i18n/es.yaml`, written by hand with
  Spain vocabulary and the same "Food, state" style as the English name.
- Only foods with energy, protein, carbohydrate and fat values can be listed (the build validates it).
