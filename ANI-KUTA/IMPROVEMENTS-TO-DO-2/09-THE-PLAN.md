# 09 — THE PLAN (the PM-6 findings, the DB BLOCKER, the normalization plan)

> Written in the round-167 orientation round (2026-11-21), from a full
> code-level investigation. This file ADDS to 01–08; it does not replace
> them. The redo order in 07 stands — with one NEW item inserted before
> everything else (see B — it is a hard release blocker).

---

## A. The PM-6 investigation — what the code shows (the evidence)

The suspects from 01/PM-6 were checked against the code. Result:

1. **CONFIRMED (the prime suspect, from the round-165 record itself):** the
   maintenance pass's eager DB `get()` opened the DB and ran the schema
   migration ON THE MAIN THREAD during `Application.onCreate` — the
   round-165 commit `edb81e45` states it verbatim ("the eager get() opened
   the DB + ran the v6 migration on the main thread during onCreate, the
   prime suspect for the update-over-old splash hang"). The fix moved THAT
   get() into a coroutine — but the hang RECURRED (the v1.1.103 report),
   so a main-thread DB path REMAINED.
2. **CONFIRMED (the heavyweight legacy path):** `DatabaseDriverFactory.kt`
   runs `migrateSchemaIfNeeded(db)` on EVERY open (`onOpen` + `onUpgrade`),
   and that function — on top of ~20 PRAGMA probes and the guarded ALTERs —
   executes `DROP TABLE` statements and an UNCONDITIONAL `onCreate(db)`
   (line 151: drop `content` + recreate `main_entry` + run the FULL .sq
   schema: all 26 tables + every index, `IF NOT EXISTS`) on every open,
   on whatever thread first opens the DB. On real device data this is a
   measurable main-thread cost even on a healthy install — and during an
   update-over it rides on top of the versioned migration.
3. **CONFIRMED (the versioned-migration cost):** SQLDelight runs the `.sqm`
   chain inside `onUpgrade` on the FIRST DB access — wherever that access
   happens. The round-162-era code has ViewModels/composables resolving the
   DB lazily through Koin at first composition; if the first consumer is a
   main-thread init, the migration runs on main → ANR → the black screen
   (an ANR is NOT an uncaught exception — the ErrorActivity net never
   fires, which matches the reported "black screen, no error UI").

### The diagnosis protocol (unchanged — 07 item 1)

The logcat from the next repro remains the required final proof. The three
fix directions below are the code-side counterparts.

---

## B. 🚨 THE NEW HARD BLOCKER — the DB SCHEMA DOWNGRADE (not in 01–08)

> **Any release built from the reverted main will CRASH AT BOOT when
> installed over the devices' current v1.1.103 install.**

The facts (verified 2026-11-21):

| The side | The schema version |
|---|---|
| The devices' DB (v1.1.103 shipped `4.sqm`+`5.sqm`+`6.sqm`) | **7** |
| The reverted main (`1.sqm`+`2.sqm`+`3.sqm` only) | **4** |

- SQLDelight derives the schema version from the `.sqm` count → main's
  `AnikutaDatabase.Schema.version` = 4.
- androidx.sqlite **2.4.0** (the exact artifact in the build's gradle cache)
  `SupportSQLiteOpenHelper.Callback.onDowngrade` **THROWS**
  `"Can't downgrade database from version 7 to version 4"` — verified by
  extracting the class from the cached aar (the message string is in
  `SupportSQLiteOpenHelper$Callback.class`).
- The devices run 10203 → every future install is an update-over → the
  first DB open goes down the downgrade path → throw → boot death (the
  crash handler MAY show ErrorActivity; pre-handler deaths show the black
  screen).

**THE FIX (item 0 of the redo order — before ANY release from main):**

Re-land the versioned-migration chain `4.sqm` + `5.sqm` + `6.sqm` onto main
(verbatim from the recovery branch `release/1.1.103`), so main's schema
version = 7 = the devices' version. Then:
- 7→7 update-over = NO migration runs, NO downgrade throw.
- The redo's future migrations (the priority table re-land, the tracking
  tables) continue FORWARD from v7 — which is exactly what 05-T-4 already
  demands ("the redo re-lands them WITH the migration discipline").
- **The .sqm files ride together with the `.sq`/query updates they pair
  with** — `5.sqm` REBUILT `track_entry`/`content_tracking_state` (FK-free,
  `times_watched`, the UNIQUE index); the v4-era queries must match the
  post-rebuild shapes. Diff the recovery branch's `core/database` `.sq`
  files against main's and bring BOTH sides (the migrations + the matched
  schema/queries). The queries main's code calls stay the v4-era ones
  UNLESS the rebuild renamed/dropped a column they read — the diff decides
  what moves.
- The `extension_usage` table (from `6.sqm`) lands as an unused-but-present
  table until the priority redo re-lands its tracker — harmless, and it
  keeps the version line monotonic.

**This is why PM-6's "NO schema-carrying release" rule now has a second
barb: not only the hang — the version floor itself.** The doc 07 version
floor (versionCode ≥ 10204) stays; this adds the SCHEMA floor (≥ v7).

---

## C. The PM-6 fix plan (the code-side hardening, with the diagnosis)

Ordered, each item its own commit + CI cycle:

1. **B's migration re-land** (the blocker fix) — also removes the
   `onUpgrade` pre-flight class of risk: the device DB stops crossing
   version boundaries entirely.
2. **Gate the legacy `migrateSchemaIfNeeded`** — the ALTER/DROP/`onCreate`
   block must run ONLY when a legacy shape is actually detected (the
   existing `hasColumn` guards are the template; the unconditional
   `DROP content` + `onCreate(db)` at DatabaseDriverFactory.kt:151 moves
   behind a `hasColumn(db, "content", ...)` probe or an equivalent one-shot
   marker). Healthy opens stop paying the full-schema exec.
3. **The boot DB-open audit** — find every main-thread-resolvable first
   consumer of `AnikutaDatabase` (the ViewModel inits, the app-scope gets,
   the ContentSeeder path) and force the FIRST open to happen inside
   `Application.onCreate` on a background dispatcher (an explicit eager
   open on IO, awaited by nothing on main), so no UI-path thread can ever
   be the migration's executor.
4. **The repro protocol** (unchanged, 07 item 1) — logcat on the next
   update-over repro; the ErrorActivity-vs-black-screen distinction tells
   us crash vs ANR; the three items above ship BEFORE the repro release
   so the repro itself no longer reproduces.

---

## D. The normalization plan (the 5 giants — the D-923 continuation)

The D-923 pattern is the law: whole-type moves to files; method clusters →
same-package extension functions (private→internal widening); initialized-
once fields + init blocks STAY in class; ONE cluster per CI cycle; zero
behavior change per move.

| # | File | Lines | The shape found | The split |
|---|---|---|---|---|
| 1 | `LibraryScreen.kt` | 4,607 | ~15 private composables after the screen (tabs, dialogs, sheets, header, cards) | Mechanical: sibling files `LibraryTabs.kt`, `LibraryDialogs.kt`, `LibraryCustomizeSheet.kt`, `LibraryHeader.kt` — whole-composable verbatim moves |
| 2 | `WatchScreen.kt` | 4,480 | `WatchScreen` body = lines 168→3301 ≈ 3,100 lines in ONE function | The hardest: carve the body into internal sub-composables at the section seams (the top bar, the sheets, the gesture layers, the controls host), verbatim extract, then the `controls/`-style siblings |
| 3 | `DetailsViewModel.kt` | 4,413 | Single class (4,965 → 4,413 done) | Continue D-923: the auto-link family, the episode-normalizer cluster, the resolver cluster → extension functions |
| 4 | `MainActivity.kt` | ~3,360 | Host + nav graph + wiring | The nav-graph builder + the host wiring lambdas → their own files |
| 5 | `HomeScreen.kt` | 2,358 | The redo (03) restructures this anyway | **DEFER to the redo** — normalize AFTER the home work lands, not before (no double churn) |

**The sequence:** 1 → 3 → 4 → 2 → 5. Items 1/3/4 are mechanical and
low-risk; item 2 needs its own design pass first (the seams are not
pre-existing functions); item 5 rides the redo.

**The discipline:** each move = its own commit + push + dispatch + GREEN CI
before the next; the file's tests (if any) run unchanged; no signature
edits inside a verbatim move.

---

## E. The redo execution order (the consolidated queue)

The 07 order stands, with B inserted as item 0 and D interleaved:

0. **The DB blocker re-land** (B) — before any release from main. 🔴
1. **The PM-6 hardening** (C.2 + C.3) — the boot path made safe.
2. The airs contract (03-H-1).
3. The see-all real catalogs (03-H-5, designed first).
4. The suggested field (03-H-3).
5. The mood/genre + depth (03-H-4/6/7).
6. The search page (04).
7. The tracking (05 — the MAL module + the Simkl rebuild).
8. The priority deep integration (06's leftover list).
9. The TVmaze airs + Bangumi.
10. Routine A closes every multi-step round (the automatic debug release).

The normalization items (D.1/3/4) run BETWEEN the redo items whenever a
redo round touches one of those files anyway (one visit per file, no
double churn). D.2 (WatchScreen) gets its design pass when the watch work
queue reaches it.
