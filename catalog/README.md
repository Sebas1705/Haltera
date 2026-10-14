# Exercise catalogue

Source of the exercise catalogue bundled in the app (spec §6). The app reads the generated
`composeApp/src/commonMain/composeResources/files/catalog.json`; never edit that file by hand.

| File | Role |
|---|---|
| `sources/sources.json` | Pinned free-exercise-db commit and wger fetch date |
| `sources/free-exercise-db.json`, `sources/wger-es.json` | Snapshots refreshed by `node scripts/catalog/fetch.mjs` |
| `overrides.yaml` | `catalogVersion`, `released` flag, excluded sources, per-source patches, exercise groups (variants) |
| `custom.yaml` | Exercises that are not in the sources |
| `i18n/es.yaml` | Spanish names and instructions per source id |
| `ids.lock.json` | Every id ever emitted (ids are never reused; removed ones become `deprecatedIds`) |
| `ATTRIBUTION.md` | Generated credits, shown in the app |

Workflow:

    node scripts/catalog/build.mjs               # regenerate outputs
    node scripts/catalog/build.mjs --missing-es  # source ids still lacking Spanish
    node scripts/catalog/build.mjs --check       # what CI runs
    npm test --prefix scripts/catalog            # unit tests

Rules:

- Ids are stable forever.
- While `released: false` in `overrides.yaml`, the current `catalogVersion` can be edited freely. Once a
  version ships, set `released: true`: from then on, any change to the generated catalogue needs
  `catalogVersion` + 1 and `released: false` again (the build refuses otherwise).
- The pipeline never invents Spanish. A source exercise's Spanish name comes from `i18n/es.yaml`, or
  failing that from wger (credited in `ATTRIBUTION.md`); its Spanish instructions come only from
  `i18n/es.yaml`. Groups and `custom.yaml` carry their own Spanish.
- Quote YAML strings that contain commas when they are inside a flow list (`[...]`), or each comma
  splits the step in two.
