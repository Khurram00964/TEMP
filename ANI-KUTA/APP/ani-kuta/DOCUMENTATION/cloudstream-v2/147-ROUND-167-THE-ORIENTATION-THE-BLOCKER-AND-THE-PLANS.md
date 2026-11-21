# Record 147 — Round 167 (2026-11-21): the orientation, the DB-v7 blocker, the plans, the cleanup

> The user's orders after the round-166 orientation report: investigate the
> review findings properly, manage the improvements, clean the planning
> clutter, plan the giant-file optimization, refresh the undocumented docs.
> A DOCS/PLAN round — no app code changed.

## 🚨 The headline: the DB-v7 schema downgrade blocker (09-B, D-934)

- The devices' DB = schema **v7** (v1.1.103 shipped `4.sqm`+`5.sqm`+`6.sqm`).
- The reverted main = schema **v4** (3 `.sqm` files only).
- Any release from main, installed over the devices (every future install is
  an update-over) → the first DB open → `onDowngrade` →
  androidx.sqlite **2.4.0**'s `Callback` **THROWS**
  `"Can't downgrade database from version 7 to version 4"` — verified by
  extracting `SupportSQLiteOpenHelper$Callback.class` from the build's own
  cached aar (`~/.gradle/caches/.../androidx.sqlite/sqlite/2.4.0/`), not
  from memory.
- **The fix = the redo item 0**: re-land `4/5/6.sqm` + the paired `.sq`/
  query updates verbatim from `release/1.1.103` BEFORE any release from
  main. Main's version → 7 = the devices'; 7→7 opens run no migration; the
  redo's future migrations continue forward from v7. The `.sqm` files never
  travel without the `.sq` updates they pair with (the `5.sqm` track
  rebuild). Full detail: `IMPROVEMENTS-TO-DO-2/09-THE-PLAN.md` section B.

## The PM-6 investigation (09-A, D-935)

- The round-165 commit `edb81e45`'s own message CONFIRMS the mechanism:
  "the eager get() opened the DB + ran the v6 migration on the main thread
  during onCreate, the prime suspect for the update-over-old splash hang".
- The fix moved that one `get()` — the hang RECURRED → a main-thread DB
  path remained. The audit found the heavyweight legacy path:
  `migrateSchemaIfNeeded` runs on EVERY open (`onOpen` + `onUpgrade`) and
  executes DROP statements + an UNCONDITIONAL `onCreate(db)` full-schema
  exec (`DatabaseDriverFactory.kt:151`) on whatever thread opens the DB.
- The ANR signature matches the report: an ANR is not an uncaught exception
  → the ErrorActivity net never fires → the black screen; clear-data wipes
  the DB → the next open skips the migration → works.
- The hardening plan: gate the legacy block behind shape probes + force the
  first open inside `Application.onCreate` on a background dispatcher + the
  repro protocol stands (the logcat on the next update-over).

## The normalization plan (09-D, D-936)

| # | File | Lines | The shape | The move |
|---|---|---|---|---|
| 1 | LibraryScreen.kt | 4,607 | ~15 private composables | Mechanical sibling files |
| 2 | DetailsViewModel.kt | 4,413 | Single class | The D-923 clusters continue |
| 3 | MainActivity.kt | ~3,360 | Host + nav graph | The wiring out to files |
| 4 | WatchScreen.kt | 4,480 | ONE ~3,100-line composable (lines 168→3301) | A design pass FIRST, then the section seams |
| 5 | HomeScreen.kt | 2,358 | — | DEFERRED to the redo (03 restructures it) |

One cluster per CI cycle; verbatim moves; zero behavior change.

## The cleanup + the docs

- planning/: **5.75GB → 75MB** — 8 old APKs, 14 log zips, r142-release/
  r142-artifact/r156-release, verify_v1100-03, v1181-1185,
  taste-skill-clone. The released builds remain recoverable from the TEMP
  tags/releases; every record/script/reference kept.
- NEW_AGENT_SETUP.md: the dead-repo URL → the TEMP live line, 57→59
  modules, 25/17→26 tables + the schema-floor pointer, docs 72-97→100-146,
  the IMPROVEMENTS-TO-DO-2 read inserted as item 4.5.
- SESSION.md: the current state → round 167 (the blocker + the plan
  pointers). IMPROVEMENTS-TO-DO-2/README.md: 09 listed.
- The ledgers: progress.md (the round-167 top block + the section),
  decisions.md (D-934..D-936), changelog.md, lessons-learned.md (the
  REVERT-CARRIES-A-SCHEMA-FLOOR lesson).

## The next session

The redo item 0 (the migration re-land) is READY TO IMPLEMENT — the first
code round of the redo. Then the PM-6 hardening (09-C), then the redo
order (07 items 2-10).
