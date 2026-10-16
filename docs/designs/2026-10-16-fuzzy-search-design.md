# Haltera — fuzzy exercise and food search

- **Date:** 2026-10-16
- **Status:** requested by the owner ("searches are too abrupt: a `contains` with the exact word order; rank by
  weight and share of matching letters, tolerate unordered keywords and typos").
- **Code:** `core:search` (pure engine, no dependencies), used by `data:repository`
  (`sql/ExerciseSearch.kt`, `sql/FoodSearch.kt`, `sql/SearchIndexCache.kt`). No schema change.

## 1. Where it runs

Every exercise search goes through `ExerciseRepository.search(filter, language, limit)`: the Exercises tab, the
routine editor's exercise picker (with filter chips) and the session "add exercise" picker. Food search goes
through `NutritionRepository.search(query, language)`; Open Food Facts barcode lookups are unchanged.

- **Blank query** (or only punctuation): unchanged SQL path, filters only, most logged exercises first.
- **Text query:** an in-memory index of every active exercise (≈800) is built once per app language and
  reused on every keystroke. It is rebuilt only after a write to the exercise or variant tables (a SQLDelight
  query listener bumps a version: catalogue import, custom exercise created/edited/deleted). The muscle,
  equipment and category filters are applied in memory before scoring. Foods work the same way (every
  non-deprecated food, rebuilt after any write to `FoodEntity`).
- **Speed** (JVM unit test, 801 exercises): index build ≈ 10–20 ms, ranking ≈ 0.5 ms per query
  (1 000 queries ≈ 0.4–0.7 s); 705 foods ≈ 0.4 ms per query. The Exercises tab and the routine picker already
  debounce typing (250 ms); the session picker cancels the previous search on each keystroke.
- **Lists scroll to the top when the best result changes.** Result rows are keyed by exercise id, so Compose kept
  the previously first row on screen wherever it moved in the new ranking, hiding the best matches above it
  (seen on the emulator). The three pickers call `scrollToItem(0)` when the first result changes.

## 2. Normalisation (`TextNormalizer`)

1. Lower case; Latin accents and diacritics folded (`Jalón` → `jalon`, `ñ` → `n`).
2. Anything that is not a letter or digit splits words (`Pull-Up` → `pull up`, `3/4` → `3 4`).
3. Stopwords dropped in both languages (`de, del, la, con, en, a, al, y, por, para, sin, the, with, on, of, to…`),
   unless the text has nothing else. So `press de banca` = `press banca`.
4. Light plural stemming shared by both languages; the indexed text and the query go through the same rules,
   so they only need to agree: `curls → curl`, `mancuernas → mancuerna`, `presses → press`, `flies/flyes/flye → fly`,
   `crunches → crunch`, `elevaciones → elevacion`, `dorsales → dorsal`, `cables/cable → cabl`. The unstemmed word is
   indexed too, so a typo is measured against the real word (`aple` → `apple`).

## 3. Matching

Each query term is compared once with every term of the index vocabulary (a few thousand terms):

| Match | Quality |
|---|---|
| Same term | 1.0 |
| Prefix (term of 2+ letters, or the last term while typing) | 0.55 + 0.35 × typed share of the word (< 1) |
| Typo: Damerau–Levenshtein (adjacent transposition = 1 edit), 0 edits up to 3 letters, 1 up to 7, 2 from 8 | 0.8 × (1 − distance / longer length) |
| Typo inside a word being typed (4+ letters, same first letter, ≤ 1 edit from the start of the word) | 0.45 |
| Last word while typing is the start of a stopword in the name (`press d` → `Press de banca`) | 0.6 |

Adjacent query words are also tried joined (`lat pull down` meets `pulldown`), and names index their joined word
pairs (`Pull-Up` also answers `pullup`).

Per query term a document keeps its best `quality × field weight`. Fields and weights:

| Field | Exercises | Foods |
|---|---|---|
| Name in the app language | 1.0 | 1.0 |
| Name in the other language | 0.9 | 0.9 |
| Variant names (both languages) | 0.8 | — |
| Name + equipment of each variant ("Aperturas mancuerna"), name bonuses only | 0.95 (other language 0.85) | — |
| Terms added by the synonym table | 0.9 × the field they came from | — |
| Joined word pairs | 0.95 × the field | 0.95 × the field |
| Primary muscles (English + Spanish words) | 0.7 | — |
| Equipment of the active variants | 0.65 | — |
| Secondary muscles | 0.5 | — |
| Category | 0.45 | — |
| Brand | — | 0.6 |

## 4. Score (`SearchIndex`, constants in `SearchWeights`)

```
score = average term score (0..1)
      + 0.5   when every query term matched
      + 2.0 × name weight when the query is a name (same words, same order; stopwords ignored)
        or 1.7 × name weight when it has the words of a name in another order
        or 0.25 × name weight when a name starts with the query (typing)
      + 0.4 × name weight × covered share of the name's words (each word counts with its best match quality)
      + boost
```

So: exact name > all terms matched > prefix > fuzzy, and name > other names > muscle > equipment > category.
The coverage term makes `Press inclinado` beat `Press inclinado con mancuernas (agarre martillo)` for the same
query, and makes a typo cover less than the exact word.

- **Required terms:** one- and two-word queries need every word; from three words one may be missing.
- **Threshold:** results below 0.25 are dropped.
- **Boost** (exercises): +0.12 × log-scaled number of sessions in which the user completed a set of it
  (saturates at 20), +0.05 if one was in the last 14 days, +0.05 for custom exercises. Foods: +0.3 for
  favourites (they outrank a name-prefix match), +0.05 for own foods and recipes. Boosts never add a document
  that did not match.
- **Stable order:** ties keep the index order (exercises by name in the app language then id; foods by name
  length then name then id), so the same query always returns the same list.

## 5. Synonyms (`core:search/GymSynonyms.kt`)

A curated table of ~90 groups of equivalent phrases in Spanish and English. When a name or variant name
contains a phrase, the index also files it under the other phrases of its group, so the query works in either
language even for a custom exercise named in one language only. Groups cover:

- movements: `press banca = bench press`, `jalón = pulldown`, `jalón al pecho = lat pulldown`, `remo = row`,
  `sentadilla = squat`, `aperturas = fly/flye/pec deck`, `dominadas = pull-up/chin-up`, `zancadas = lunge`,
  `peso muerto = deadlift`, `peso muerto rumano = RDL`, `press militar = overhead press = OHP`, `fondos = dips`,
  `flexiones = push-up`, `elevación lateral = lateral raise`, `pájaros = rear delt fly`, `press francés = skull crusher`…
- grips and positions: `agarre cerrado = close grip`, `sentado = seated`, `de pie = standing`, `una mano = one arm`…
- equipment words: `mancuerna = dumbbell = DB`, `barra = barbell`, `barra Z = EZ bar`, `polea = cable`,
  `multipower = Smith`, `pesa rusa = kettlebell = KB`, `goma = band`…
- body parts used in names: `pecho = chest`, `femoral = hamstring`, `gemelo = calf`, `glúteo = glute`…

Keep groups tight: a loose group ("press" = "prensa") makes every search noisier. Muscle, equipment and category
words do not need entries: they are indexed in both languages from the exercise data (`ExerciseSearchDocuments`).
Foods use no synonym table (their names are already bilingual).

### Name + equipment

The catalogue models equipment as variants ("Aperturas" with dumbbell and cable variants), so a query like
"aperturas mancuerna" is split across the name and a variant. Each exercise therefore also has one
`FieldKind.VARIANT` name per equipment ("Aperturas mancuerna", "Chest Fly dumbbell"). Variant names count only
for the exact-name, name-prefix and coverage bonuses: they never make a document match on their own and add no
term weight, so other rankings move little (every earlier table expectation still holds). "aperturas mancuerna" now ranks
the plain chest fly first.

### Antonyms (`core:search/Antonyms.kt`)

Some opposite gym words are one or two letters apart and would otherwise be typos of each other. The typo step
never links them (exact and prefix matches are unaffected), also inside joined words and word starts:

| One side | Other side |
|---|---|
| inclinado/a, incline(d) | declinado/a, decline(d) |
| supino/a, supinated, supine | prono/a, pronated, prone |
| abductor(es), abduction, abducción | aductor(es), adductor, adduction, aducción |
| interno/a, internal | externo/a, external |
| pulldown | pushdown |
| pullup | pushup |

Add a pair when two opposite words are within the typo allowance (§3) of each other.

## 6. Tests

- `core:search` (common): normaliser, stemming pairs, edit distance, synonym table (the owner's pairs in both
  directions), scoring tiers, field weights, threshold, required terms, stability, filters and boosts.
- `data:repository` (JVM, real bundled data): 122 table-driven exercise queries (reordered words, typos,
  plurals, accents, prefixes, other-language names, synonyms, no-result cases), keystroke-by-keystroke typing,
  filters combined with text, custom exercises and index invalidation, usage boost, stability under shuffled
  input, benchmarks; ~30 food queries and favourites.
