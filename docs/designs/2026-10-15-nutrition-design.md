# Ironlog — nutrition design (F7)

- **Date:** 2026-10-15
- **Status:** approved scope from the core spec (§1 "body weight, meals, calories and macros with statistics over time", §10.2 sources); details decided here.
- **Principle:** a logbook (see AGENTS.md). Goals are optional guidance, never gates; every entry is editable; statistics are descriptive and explained.

## 1. What the user can do

- Log what they ate per day in four meals (breakfast, lunch, dinner, snacks): pick a food, enter the amount (grams/ml, or servings when the food defines one), see kcal and macros update.
- Find foods by: search in the bundled generic foods database (offline, es + en names); barcode scan (Android: the Google code scanner already used for routine QR; iOS: stub allowed) looking up Open Food Facts; recent and favourite foods; custom foods and recipes (a recipe = list of foods with amounts and a yield).
- Copy a meal or a whole day from another day (common in real use).
- Set optional daily goals: kcal, protein, carbs, fat (grams or % of kcal). A goal calculator suggests them from profile data (sex, age, height, latest body weight, activity level, objective lose/maintain/gain) using Mifflin–St Jeor × activity factor, with an ⓘ explaining the formula; the user can override anything.
- See statistics over time (Nutrition tab → Stats), all with ⓘ explanations: daily kcal vs goal (bars + goal line), macro split per day (stacked bars) and average (donut), weekly averages and adherence (% of days within ±10 % of the goal), protein per kg of body weight, and kcal intake vs body-weight trend (dual axis, 7-day moving averages) to relate eating to weight change.

## 2. Data

New tables (SQLDelight, **migration `3.sqm` v3→v4**, snapshot `4.db`, migration test):

- `FoodEntity`: id, source (`GENERIC` | `OFF` | `USER` | `RECIPE`), nameEn, nameEs, nameSearch (accent-insensitive), brand?, barcode?, kcal/protein/carbs/fat/fiber?/sugar?/salt? **per 100 g (or 100 ml)**, unit (`G`|`ML`), servingName?, servingGrams?, favourite, deprecated, updatedAt.
- `RecipeItemEntity`: recipeId → FoodEntity(id), foodId, grams, position (a recipe's per-100 g values are computed from its items and yield).
- `MealEntryEntity`: id, date (local `yyyy-MM-dd`), meal (`BREAKFAST`|`LUNCH`|`DINNER`|`SNACK`), foodId, grams, position, createdAt. Nutrients are computed from the food at read time (a later edit of a user food updates past days — documented).
- `NutritionGoal` lives in settings (local + synced via the existing settings payload): kcal, protein, carbs, fat, mode; profile (sex, birth year, height cm, activity, objective).

Generic foods are bundled like the exercise catalogue: `scripts/nutrition/` builds `composeApp/.../files/foods.json` from a pinned USDA FoodData Central (CC0) subset (Foundation + SR Legacy, ~600–900 common foods), with Spanish names written in `nutrition/i18n/es.yaml` (same rules as the exercise catalogue: never machine-invented by the pipeline, reviewed), versioned, imported with the same idempotent upsert/deprecate pattern. Open Food Facts results are cached as `source = OFF` foods (ODbL attribution in Credits; User-Agent `Ironlog/<ver> (+https://github.com/Sebas1705/Ironlog)`; respect 15 reads/min).

## 3. Sync

New synced collections in `data:sync` (triggers in the same `3.sqm`): `foods` (USER and RECIPE foods with their items, and OFF foods the user logged), `mealDays/{date}` (one document per day with all its entries — the aggregate root is the day). Generic foods are not synced (bundled). Same LWW and tombstone rules.

## 4. UI

New tab **Nutrition** between Exercises and Progress (5 tabs): day view (date switcher, totals ring vs goal, macro bars, the four meals with entries, quick add), food search/scan screen, food detail with amount editor, custom food and recipe editors, goals screen with calculator, stats screen. Today shows a small "today's nutrition" card only if the user has logged food or set goals (added in a later polish, not required now).

## 5. Out of scope now

Micronutrients beyond fibre/sugar/salt, photo recognition, water tracking, meal plans.
