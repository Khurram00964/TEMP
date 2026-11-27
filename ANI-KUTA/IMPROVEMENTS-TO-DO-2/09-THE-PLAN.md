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

---

## F. THE ROUND-168 ORDER (the user's, 2026-11-21 — this SUPERSEDES the E queue)

The user's decisions:

1. **Item 0 (the migration re-land) is SKIPPED as a standalone.** The user
   will clean-install the next debug build (delete v1.1.103 completely,
   install 1.1.104 fresh) — the downgrade path never runs. The DB stays
   v4 on main UNLESS a feature needs the newer shapes (the Simkl port
   brings 4/5/6.sqm + the paired .sq updates as PART of the tracker work —
   fresh installs get the new schema; and since the devices then match v7,
   BOTH install paths stay safe anyway).
2. **The professional line keeps the migration discipline** — when the
   release line ships 1.1.9 (over the installed 1.1.8), the DB migration
   MUST run smoothly on update-over. That is the release-branch work at
   1.1.9 time, not a debug-line blocker.
3. **The first priority: the boot-path hardening (C.2 + C.3)** — the gated
   legacy migrate + the async first-open, handled with proper care.
4. **Then the trackers**: the Simkl port (the recovery branch's proven
   shape, login-state restore included) + the FULL MAL module (the OAuth
   PKCE-plain, the link flow, the Jikan search per the user's pointer,
   api.myanimelist.net/v2 for the authenticated list ops).
5. **Then the SEARCH PAGE** (04-THE-SEARCH-PAGE-SPEC.md is the contract,
   extended by the user's round-168 adds):
   - The search sources: AniList (current) + TMDB + TVmaze (the recovery
     shape) + **MAL via Jikan** (new) + **Simkl** (new).
   - The picker = the bottom-sheet menu from the source chip (the
     extension-sources parity), the REAL logos (04-S-2), the instant apply,
     the persistence.
   - The ONE universal base for every source (04-S-1).
   - **The details pages loadable from EVERY source** (the user's
     round-168 add — extends 04-S-3's hand-off: metadata sources render a
     real details surface + the CS-first hand-off for playback).
   - **Settings → Appearance → the Search page** (04-S-5, the user
     re-confirmed): the title placement (on-cover/below), the rating
     badge, the details line, the grid density (the columns), the recents
     toggle.
6. **NOTHING ELSE this round** (the airs contract, the see-all catalogs,
   the mood depth, the priority integration, the TVmaze airs, Bangumi —
   all explicitly deferred by the user's "your task is only this").

The phase order: A (boot) → B (DB + Simkl) → C (MAL) → D (tracker UI) →
E (search sources + picker) → F (per-source details) → G (the
customization page). One CI cycle per phase. Routine A closes the round
(1.1.104/10204).

---

## G. THE SEARCH DESIGN (Phases E–G — written before the implementation, the design-first rule)

### E. The search sources + the picker

- **The sources (5):** AniList (the existing GraphQL path) + TMDB + TVmaze
  (the recovery branch's port) + **MAL** + **Simkl** (new).
- **MAL rides the MAL v2 app-auth search** (MalApi.searchAnime — the
  X-MAL-CLIENT-ID header, built in Phase C). **Jikan stays the documented
  FALLBACK** (the user's "if needed"): only if the device round shows MAL
  v2 search limits do we add a JikanApi (api.jikan.moe/v4, keyless,
  3 req/s). Not built preemptively.
- **Simkl rides SimklApi.search** (built in Phase B).
- **The parity contract (the recovery's proven trick):** every non-AniList
  result maps to a SYNTHETIC AniListAnime with a NEGATIVE id — the grid,
  the cards, the titles render UNCHANGED (the user's "the front end stays
  exactly the same"). The negative id marks the item as metadata-only.
- **The picker sheet:** the recovery's DataSourcePickerSheet shell (the
  ModalBottomSheet, the icon+name+check rows) extended to the 5 rows, each
  with the REAL logo asset (doc 04-S-2: the initials letters were rejected
  twice; tiny assets ~10-20KB each ride the 5MB budget). The
  MAL row's subtitle: "Anime"; Simkl: "Movies & TV". The selection
  persists (SearchPreferences' datasource key), applies instantly, the
  recents survive.
- **The ONE shared base (04-S-1):** the recovery's shared-list refactor
  (the collapse's DataSourceSuccess branch + the canPage pagination + the
  one-line titles + the shared skeleton) ports verbatim; the MAL/Simkl
  sources join the SAME branches.
- **The priority deps STRIP:** the recovery SearchViewModel's
  ExtensionUsageTracker/ExtensionPriorityPreferences params + the SEARCH
  records + the provider sort are DEFERRED (the priority phase re-lands
  them) — the port removes those references.

### F. The details from every source + the hand-off

- Tapping a metadata-source result (the negative id) → the CS-first
  hand-off (04-S-3): the auto-search on the highest-priority trusted CS
  provider (the remembered/first provider until the priority phase), no
  intermediate dialog (the round-165 approved shape).
- The DETAILS SURFACE for the metadata sources: the synthetic-parity item
  opens the existing Details screen in its metadata mode (the hero + the
  meta + the actions) where supported; the CS hand-off remains the playback
  path. The per-source details pages reuse the existing anatomy — no
  parallel screen (the design language: one system).

### G. The Appearance → Search page customization

- The recovery's SearchPageSettingsScreen (145 lines) + SearchPreferences
  port as the base; the settings: the title placement (the overlay
  on-cover / below-cover), the rating badge, the details line, the grid
  density (the columns), the recents visibility.
- Settings → Appearance gains the "Search page" row (the recovery's
  AppearanceScreen row — the round-165 placement, the user-approved).
- The SearchScreen reads the prefs reactively (the PreferenceStore flows).

---

## H. THE ROUND-169 QUEUE (the v1.1.104 device report — the remaining items)

DONE this round:
1. 🚨 The details-page NPE (the declaration-order bug) — FIXED.
2. 🚨 The Simkl duplicate-key crash — FIXED (the LIVE-VERIFIED shapes: the
   search needs the user token; the trending ids at ids.simkl_id; the
   simkl.in poster paths).
3. The search universality (the blur on all sources, the universal title
   placement, the rating pill top-right on every card, the grid 1-5, the
   Appearance row's own section + icon, the animated search-bar swaps, the
   real AniList/MAL/Simkl favicons).
4. THE METADATA DETAILS PAGES: the taps open the details page OF the
   tapped source (MetadataDetailsScreen + MetadataDetailsViewModel + the
   MetadataDetailsKey), the DB LINKING via content_details' data axis
   (data_source_type/data_source_ref_id — the schema's designed axis), the
   Watch → the CS route, the Track → the serving tracker.

NEXT CYCLE (the ordered queue):
1. **THE PER-SOURCE SEARCH CACHING** (the user's order): the first TWO
   pages of each source's results cached (the AniList/TMDB/TVmaze/MAL/
   Simkl SEPARATE caches); the beyond-2-pages loads live; a REFRESH
   action (the cache clears, the fresh data fetches); the automatic
   12-hour refresh (the smooth background refresh). THE DESIGN: ride the
   browse_cache table's expiry pattern (the round-163 design) — the rows
   keyed (source_kind, cache_key) + expires_at; the store = a
   SearchCacheStore in core:database (the dependency-free store, the
   nowMs passed in); the VM reads cache-first, then fetches + writes.
2. The metadata details' enrichment (the TMDB/TVmaze/MAL/Simkl DETAIL
   endpoints for the full pages — the episodes, the cast; the v1 renders
   the search-result data).
3. The Trackers cards' linked-state polish (the user: "blank and simple,
   unpleasant") + the Simkl Populate Library question (ask the user: the
   Simkl library import exists? — the allItems reads exist).
4. The TMDB/TVmaze real-logo assets (the Commons route blocked; the
   remaining favicons need proper sourcing).

---

## I. THE ROUND-170 ORDER (the v1.1.105 device report — THE SCOPE: only these)

1. **🔴 THE DETAILS UNIFICATION (the user's biggest redirect):** the
   MetadataDetailsScreen/VM/Key are DELETED — the taps open THE EXISTING
   single details page (the AniList one the extensions use) with the data
   TRANSLATED from the sources (the synthetic-AniListAnime parity — the
   round-164 mechanism). The cover animations, the title placement, the
   background art, the year/status, the genres, the synopsis — everything
   identical. The 3-dot "link to data source" becomes **"Link to other"**:
   the menu transforms into the sources list in THIS order: AniList →
   MyAnimeList → Simkl → TMDB → TVmaze; after linking it shows the linked
   source's name + "Unlink {source}". The DB manages the new sources via
   the designed data axis.
2. **🔴 THE SEARCH PAGE = ONE STABLE PAGE** — no per-source copies; the
   grid layout IDENTICAL on all (the user: "they show exactly the same
   data").
3. **🔴 THE CARD FIXES:** the rating pill glitches on the non-AniList
   sources (the pill rendered under the cover — the DS card's structure
   differs from the AniList one → UNIFY into ONE card composable); the
   release year at the TOP-LEFT OF THE TITLE (the overlay style) / at the
   BOTTOM-LEFT OF THE COVER (the below style); the title moved DOWN a bit
   (everything else unchanged); the BELOW-style space bug on AniList (the
   title rendered ABOVE the covers).
4. **🔴 THE TRACK LOGIC:** ONE TRACKER AT A TIME (the user's explicit
   rule) — a tracked content's track action opens THAT tracker's sheet
   directly; the picker only when untracked + >1 linked. The isTracked
   flag dispatches by the tracker (the MAL "not tracking" bug). The Simkl
   row joins the picker when linked. The picker rows show the username
   ONLY below the name (no role text). The TrackSheet shows the tracking
   system's favicon at its top.
5. **🔴 THE SIMKL SEARCH:** no results immediately — the search response
   shape ≠ the trending shape (the dual-shape DTO; the live verification
   needs the user token — parse leniently for BOTH shapes).
6. **🟠 THE APPEARANCE SEARCH-PAGE REDESIGN:** the proper sections, the
   one-line descriptions (the RULE), the accent-highlighted headings, the
   background/foreground separation — the UI references: the General page
   + the Details Background page.
7. **🟠 THE TRACKERS CARDS COMPACT:** the real tracker icons; NO
   "Connected as", NO the role descriptions; the Populate Library button +
   the delete icon colored.
8. **🟠 THE PICKER ICONS:** TMDB/TVmaze/Simkl re-sourced (the AniList/MAL
   ones work).
9. **🟠 THE PER-SOURCE SEARCH CACHING:** the 2-page caches per source, the
   refresh, the 12h auto — the unified system (NOT per-source copies).

---

## J. THE ROUND-172 ORDER (the v1.1.107 device report — the correctness round)

The verdicts first: the enrichment + the unified cards + the compact
trackers + the quick re-verifications = APPROVED. The caching + the
pagination = the explicit orders. The rest = the fixes.

1. **🔴 CYCLE A — THE METADATA AUTO-LINK = THE ANILIST PATTERN.** The
   v1.1.107 metadata loader silently linked the FIRST provider's FIRST
   search result (no scoring, no UI, no prompt — the "STEPS opens another
   anime" mismatch) and persisted the link under a STALE key
   (currentAnimeId was never set to the packed id → KEY_SOURCE_LINK +
   0/the previous anime → the "extension linked at the top but Link-to-
   other at the right" contradiction). THE FIX, exactly the AniList
   semantics: (a) the full per-load state reset + the generation guard +
   currentAnimeId = packedId in loadMetadataContent; (b) the silent
   auto-search REPLACED by the ReverseAutoLinkService flow (the scored
   matcher across the trusted providers) driving _reverseAutoLinkState —
   the SAME live preview the AniList page shows (Searching extensions… →
   the Matched card → the link + the episodes; NoMatch → "Link source
   manually").
2. **🔴 CYCLE B — THE ONE LINK SHEET (the new bottom-up menu).** ONE new
   LinkSourceSheet for ALL 5 sources (AniList included — ManualLinkSheet
   retires), the UI taking its inspiration from the three-dot menu (the
   section labels, the crimped cards, the disc rows). NO description
   text (the user: "remove all of it"). The rows: cover + title +
   RATING + YEAR. The picks LINK FOR REAL + the page reflects it: the
   menu shows the linked sources + "Unlink {source}" per source (the
   MAL/Simkl mappings = track_entry rows; the AniList = anilistId; the
   TMDB/TVmaze = the data-axis identity slot — ONE external identity per
   row in v1, documented).
3. **🔴 CYCLE C — THE MENU RESET + THE ANIMATION.** The menu ALWAYS opens
   in the default state (the LaunchedEffect(onDismissRequest) reset was
   dead — the lambda never changed identity; reset on `expanded` → false
   like sharePage does). The Link-to-other expansion ANIMATES. The
   AniList row gets its logo (ic_tracker_anilist).
4. **🔴 CYCLE D — THE TRACK-ON PICKER.** 0 linked → the picker sheet with
   the honest "No trackers are connected" (NOT the AniList not-connected
   sheet). 1 linked (ANY) → the direct open. >1 → the picker with
   WORKING rows — the MAL/Simkl no-mapping path opens the LINK sheet
   (the openTrackSheet → openManualLinkSheet pattern; the dead
   _showMalLinkSheet/_showSimklLinkSheet flags were never hosted — the
   sheets never rendered).
5. **🟠 CYCLE E — THE TRACKERS CARDS.** MAL/Simkl: the "Not linked" tag
   REPLACED by the Link button; linked → the Unlink button at the NAME
   ROW's rightmost (not the bottom-right). The AniList card unchanged.
6. **🔴 CYCLE F — THE SEARCH PAGINATION.** MAL joins the paginated set
   (the v2 offset: limit=20&offset=(page-1)*20); TMDB/AniList verified
   wired; TVmaze/Simkl = the one-shot APIs (the honest "end of results"
   footer replaces the silent stop). The defaults feeds paginate where
   the API pages (TMDB trending ?page=, MAL ranking offset).
7. **🔴 CYCLE G — THE PER-SOURCE SEARCH CACHING (09-H's design).** The
   source's DEFAULT feed (the blank-query view) cached per source — the
   first 2 pages — riding the browse_cache expiry pattern; cache-first
   instant render; the 12h auto-refresh; the REFRESH action (clear +
   refetch). TVmaze/Simkl (one-shot) cache their single page.
8. **📋 THE CHECKLISTS GO LOGICAL** (the user's order): the steps follow
   the real user flow — the search page's source menu first, then the
   cards, then the details, then the menu, then the links, then the
   tracking, then the settings.

---

## K. THE ROUND-173 ORDER (the v1.1.108 device report — the identity round)

CONFIRMED (do not regress): the pagination (all 5), the caching (all 5 +
offline), the metadata details, the visible auto-link, the trackers cards.
Deferred by the user: the search-page refresh animations/polish.

**THE FIXES:**
1. **F1** the refresh-failure toast: "Refresh failed due to network error"
   / "Refresh failed" — all 5 sources + the AniList default feed. The
   cache-stays behavior KEPT.
2. **F2 🚨 THE TMDB AUTO-LINK IDENTITY LOSS** — the extension auto-links →
   the TMDB identity vanishes + the library save doesn't survive the
   reopen from TMDB. THE SUBAGENT AUDIT runs first (the user's order);
   the full identity model: the data axis + the extension axis + the
   library row + the saved-link key + the reverse map + the reopen
   resolution for the kind-prefixed mainIds.
3. **F3** the menu state machine: the Unlink rows show for EVERY linked
   source in the default state (the stale/incorrect linkedIdentities
   computation); the re-link pick = the explicit "Replace" semantics; the
   phantom MAL unlink (the cross-content track_entry leak investigation);
   the placement folds into N2's redesign.
4. **F3.6+F4 THE SIMKL CLUSTER (live-verified):** EVERY Simkl request
   requires the user token (the live 401s) — trendingItems NEVER attached
   it (always empty live; the §1 confirm was the CACHE serving); search
   Items reads the token WITHOUT ensureFreshToken and swallows every
   failure; syncEntry resolves the trackerId as anilist/tmdb but the
   link sheet stores SIMKL-NATIVE ids → "unresolved" → the sync aborts
   (F4's "Couldn't reach Simkl"). FIX: the token everywhere + the
   SIMKL-NATIVE-FIRST probe (/{kind}/{id} live: 302 = exists) + the
   error surfacing.
5. **F5** the MAL search: the error surfacing (the silent empties die),
   the re-search affordance, the live endpoint check (the app-auth works
   unlinked — the query the website handles should resolve; the
   fields=string covers the display), the details display format review.

**THE NEW ORDERS:**
6. **N1** the live extension-search status: WHICH extension + the elapsed
   timer + the cancel button — the whole details auto-search flow, every
   source.
7. **N2 THE MULTI-LINK MODEL** (the user approved): several sources linked
   simultaneously + the top-level data-source picker; the SUBAGENT AUDIT
   report before building; the DB evaluation: a dedicated content_identity
   table (main_id, source_kind, source_ref_id, ...) vs stretching the
   current axes — a NEW SCHEMA if needed ("we most definitely build them").
8. **N3** the tracking UI polish: the cover + the title in the tracking
   sheet; the not-in-library status = None (NOT "Watching"); the MAL link
   sheet in the three-dot-menu design language + the re-search button.
9. **D1 THE RAW-DATA SURVEY** (the in-chat deliverable): one real series ×
   5 sources, LIVE-queried (Tokyo Ghoul: AniList 20605, TMDB 61374,
   TVmaze 1440, MAL 22319; the Simkl = the token-gated shapes + the live
   401 proofs) — the raw → shown → keep/drop/translate tables. THE KEY
   FINDING: the MAL rows fetch 7 of ~25 fields — no synopsis/genres/
   studios/themes detail fetch exists (the data-limited pages' root
   cause); the TMDB detail skips the cast/tagline/runtime/networks.
10. **D2+D3** the UI-reuse principle + the state-reset/generation/identity
    rules documented; D4 the DB coherence audit rides N2's schema.

---

## L. THE ROUND-173 IMPLEMENTATION RECORD (the audit → the builds)

**THE SUBAGENT AUDIT (the user's order, findings before building) — the
verified root causes:**
- **F2 = THREE breaks:** (1) the metadata anime lived ONLY in _state →
  linkSource's remerge (the ext-only path) replaced it — the "TMDB identity
  vanishes"; (2) linkExtensionToExisting regenerated contentId into the
  6-segment shape (the metadata 2-segment shape destroyed); (3) the library
  reopen: loadMetadataContent never called refreshContentAndLibraryStatus
  (the saved rows reopened "NOT SAVED") and MainActivity's dispatch dropped
  the metadata-only rows / ext-linked rows lost the identity.
- **F3:** refreshLinkedIdentities was called ONLY on the metadata path (the
  library entries showed NO Unlink rows); the phantom MAL unlink = the
  v1.1.107-era silent picks' REAL track_entry rows resurfacing + the mixed
  read race (mid before launch, _state inside — the cross-content bleed).
- **N2:** the current storage cannot hold N identities (the 1:1 axes); the
  recommendation = the NEW content_identity table (main_id, source_kind PK;
  the reverse-unique index; display_rank) + the 6.sqm v6→v7 seeding from
  both axes + the track_entry identity rows. 10 landmines recorded
  (the dead reverse lookup, the undocumented 'metadata' display_source, the
  contentId shape drift, the wrong-axis unlink, the currentAnimeId re-key,
  the axis-wiping upsert, the unopenable library rows, the mixed-read race,
  the stale entryMode, the dual link-key slots).

**THE BUILDS (cycle 1 + cycle 2):**
- **Cycle 1** (334b3dee): the Simkl cluster (the token everywhere, the
  native-first sync probe, the strict errors), the MAL cluster (getStrict +
  detail() + the enrichment mal branch), F1 (the refresh toast), F5 part 2
  (the sheet's error + the re-search), N1 (the live search status + the
  timer + the cancel), N3 (the tracking sheet's cover+title + the 'None'
  status), the F3 identities wiring.
- **Cycle 2** (the F2/N2 cycle): the metadataBase (the audit Break 1) +
  the remerge rework (the metadata slot + the entryMode fix — landmine 9);
  refreshContentAndLibraryStatus on the metadata reopens (Break 3a); the
  reopen routing (loadFromExtension's kind-prefixed route + MainActivity's
  library dispatch — Break 3b + landmine 7); **content_identity** (the
  table + 6.sqm v6→v7 + the seeding + the ContentIdentity model + the
  repo APIs); the write hooks (every link/unlink/pick maintains the
  identity rows; the axis-wiping upsert GONE — landmine 6);
  refreshLinkedIdentities reads the authoritative table with the gen guard
  (landmine 8).
- **THE DOCS:** 10-THE-UI-REUSE-PRINCIPLE.md (D2/D3 — the one-surface rule,
  the menu vocabulary, the sheet anatomy, the state/generation/identity
  rules, the error-surfacing rule, the checklist rule).
- **THE N2 FOLLOW-UP (the next cycle):** the N-way DISPLAY merge reading
  the identity rows (v1 keeps the metadata-vs-extension 2-slot display with
  ALL the linked sources shown in the menu), the AnimeDetailsKey.Main(mainId)
  route, the contentId freezing onto main_id, the switchDataSource →
  display_rank.

---

## M. THE ROUND-174 ORDER (the v1.1.109 device report — THE FINAL-ATTEMPT ROUND)

**THE META-ORDER (the user):** plan end-to-end BEFORE any code; audit the
logics/workflows first (the subagent pattern reused); error handling +
management + DISPLAY at every layer; document everything; logical tests.
THE THEME: no half-sync systems that fix themselves silently — the async-
relay UX is the core failure class of 172-173.

**THE FIXES:**
1. **T1 THE PULL-REFRESH UX:** (a) the OFFLINE spinner NEVER dismisses —
   the dismissal keys on a uiState CHANGE and the cache-stays failure emits
   NO new state → a completion SIGNAL replaces the state-change signal +
   the network-failure fast-path (the bounded ~10s wait); (b) the OS Toast
   RETIRES — the in-app notice system (the TrackingNotice pattern) for
   EVERY transient message on all 5 sources + the AniList feed; (c) the
   colorful jumping-dots animation (the pull-refresh + the load-more, the
   shared component, the bounded lifetime).
2. **T2 THE MENU/DISPLAY IDENTITY LAYER:** the chips show the ACTUAL
   identities (TMDB | Extension | …) as tappable N-way switches (the
   2-slot pair retired — THE HEADLINE BUILD); the section-collapse on the
   reopen (the flag gaps); the blank extension name (the restore's empty
   LinkedSource name re-derives from the ExtensionManager); the unlink
   CONFIRMATION everywhere; the view-switch affordance after every link.
3. **T3 THE TRACKING SHEET:** the 'None' status keys on the TRACKING OPT-IN
   (isTracked), NOT the mapping existence (the link-sheet pick seeds
   PLAN_TO_WATCH → the v1.1.109 display lied again); the title-row spacing;
   the link-sheet redesign pass to the menu-vocabulary quality bar.
4. **T4 🚨 THE PROGRESS-SYNC RELAY:** the optimistic local update + the
   sync indicator + the success/failure notices; the failure KEEPS the
   user's value (never the silent clobber); the menu-open re-fetch MERGES
   (the newer-local protection) instead of overwriting; every silent-loss
   point in the relay chains dies.
5. **T5 THE IDENTITY-READ GAP:** refreshLinkedIdentities reads
   content_identity ONLY — the mal/simkl mappings live in track_entry →
   INVISIBLE in the menu while driving the tracking opens. FIX: the union
   read (+ evaluate the mapping migration). The stale-mapping hygiene: the
   Unlink rows cover everything visible.

**THE PROCESS:** the subagent audit FIRST (the relay chains + the menu
state machine); 09-M/L documented; the DB tests run explicitly on the
identity changes; the release after CI green (1.1.1NN, Routine A) + the
LOGICAL checklist (no confirmed-items re-listing).

---

## N. THE ROUND-175 ORDER (the v1.1.110 device report — the display truth round)

**THE META-ORDERS:** the DEBUGGING is a deliverable (the Logger across the
identity/display/unlink/refresh paths — the user reads the logcat); THE
COORDINATION RULE (the AniList experience is the REFERENCE — every source
identical; fix the CLASS not the symptom); the error display at every layer.

1. **F1 🚨 the dots render ALWAYS** — the custom indicator lambda lacks the
   isRefreshing gate (the M3 default gates internally). FIX: the gate + the
   bounded lifetime.
2. **F2 🚨 the offline refresh WIPES the metadata sources** — the pull
   CLEARS the cache row BEFORE the load (the round-172 refresh design); the
   offline load finds no cache + no network → Empty. THE CLASS FIX: the
   cache clears AFTER a successful fresh fetch (the write-through), never
   before — the failed refresh NEVER downgrades a cached state. (The
   AniList path never cleared — the asymmetry's root.)
3. **F3+F4 the capsule dots** — the capsule follows the drag distance, the
   threshold haptic (the HapticHelper), the spring to rest; ONE component
   for the pull + the load-more.
4. **F5 the notice placement** — above the bottom nav on the search screen.
5. **F6 🚨 the display switch** — THE AUDIT FIRST (the subagent): the
   ext-kind mapping hypothesis ("aniyomi" ≠ "extension"), the unlink's
   metadataBase rebuild, the extension-switch decoupling.
6. **F7 the icon chips** — the user's explicit layout: the top-right
   extension icon, the driver's icon left of it, the linked others below.
7. **F8 the details-page pull-refresh.**
8. **F9 the relay gaps:** the rating notice, the AniList failure's REAL
   reason (+ the refresh-and-retry-once), the connectivity-triggered sweep.
9. **F10 the link-sheet polish pass.**

---

## P. THE ROUND-177 ORDER (the DB advisory — the user's "go, complete everything")

**THE VERIFICATION (every claim checked against the code — the user's
"dont trust blindly" honored):** S2 CONFIRMED (the plain INSERT + the
pre-created UNIQUE index + the reachable duplicates via
resolveOrCreateForExtension's ext-only rows and linkAniList's
no-claim-check merge + verifyMigrations never referenced + the stale
"version (6)" comment + 5.sqm's dedup pattern proving the doctrine
existed); S1 CONFIRMED (the INSERT OR REPLACE identity write + the
mediaType dropped from UnifiedLinkResult + the enrichment's
tmdb_movie/tmdb_tv switch vs the bare-tmdb pick write + lookupIdentityMain
dormant); the WAL absence CONFIRMED (only foreign_keys = ON); the
app_settings mirror CONFIRMED write-dead (the only touch is a read);
S7's TTL mismatch CONFIRMED (the round-176 write used the 6h default vs
the 12h read gate).

**THE BUILDS:**
1. **S2** the 6.sqm data-axis seed → INSERT OR IGNORE (the 5.sqm doctrine;
   the first main wins, nothing aborts).
2. **S3** THE TEST-SUITE REPAIR (the discovery: CI never ran the tests —
   the fixtures lacked content_details/track_entry/content_tracking_state,
   so EVERY chain reaching v7/v8 threw silently since round 173): the
   fixtures repaired (the setUp + createV4Shape), ALL the migrate
   end-versions bumped 6 → 8, the stale comment fixed, THE LOCK TESTS
   appended (the S2 duplicate-seed survival + the S1 coarse→fine
   re-split).
3. **S1** the fine-kind fix: the packed-id indices 5 = tmdb_movie /
   6 = tmdb_tv (the decode + the search-tap encode + the routing prefixes
   + the unlink decode), UnifiedLinkResult.mediaType, the pick writes the
   FINE identity kind (the enrichment's switch works again), the data-axis
   writes the fine type, the chips' labels/order updated, **7.sqm (v7→v8)**
   re-normalizes the seeded coarse rows from content_details' preserved
   fine types. THE SCHEMA IS NOW v8.
4. **The ride-alongs:** WAL (PRAGMA journal_mode = WAL — the player's
   writes stop serializing against the readers), S7 (the search's trending
   write → upsertBrowseCacheWithTtl 12h, matching the read gate), S6 (the
   honest backup label: "App settings (mirror — the prefs file is the real
   backup)").
5. **THE DB TESTS RUN EXPLICITLY** (the user's order this round — the
   schema-touching paths get the pass).
**DEFERRED (the user's answers pending):** S4 (the updateActualAt sweep),
S5 (the doc drift), S8 (the extension_id duality), F10 (the link-sheet
polish).
