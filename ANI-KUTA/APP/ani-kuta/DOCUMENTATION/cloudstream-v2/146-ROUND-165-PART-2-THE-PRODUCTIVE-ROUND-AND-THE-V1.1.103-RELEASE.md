# Record 146 — Round 165 part 2 (2026-11-21): the productive round + the v1.1.103 debug release

> The user's v1.1.102 device report + the productive-round order. Every
> reported item addressed; the MAL tracking and the remaining priority
> integration are the NEXT session's first items (the scope reality — the
> user's "don't leave anything for the next sessions" met as far as the
> quality bar allowed; the explicit leftover list is below).

## The investigations (the live API tests + the wiring reads)

- The APIs all verified LIVE with curl: the TMDB discover-by-genre ✓, the
  genre list ✓, the TVmaze schedule (124 episodes for today) ✓.
- **The mood-chips bug FOUND**: discoverTmdb's genre map read
  `TmdbApi().let { emptyMap() }` — ALWAYS empty → every movies/series mood
  pool returned nothing (the v1.1.102 report's "nothing was showing"). The
  map fetches lazily through the injected Api now.
- **The collapse bug FOUND**: the search screen's contentAtTop derivation had
  NO DataSourceSuccess branch → the top bar never collapsed on TMDB/TVmaze
  (the user's "that effect was completely gone"). Fixed.
- **The pagination bug FOUND**: loadMore's canPage gate had no
  DataSourceSuccess branch → the TMDB load-more never fired. Fixed.
- **The TVmaze empty-first-load**: /schedule EXCLUDES the web-only series —
  /schedule/web merges in (the default feed is complete now).

## The fixes + the depth (the commits: edb81e45 → 1389f2ca)

- **WS-1**: the TVmaze JsonNull hardening; the Simkl cold-start logout
  restored at construction; the maintenance DB get off the main thread.
- **WS-2**: the search SHARED-LIST refactor (the same gridState skeleton —
  the collapse/the recents/the load-more identical); the TMDB pagination;
  the filters hidden for the data sources; the mode-switch consistency; the
  titles one-line + the meta year/rating; the search-button UX (the empty
  bar = no button; the typing raises the pill); the hand-off AUTO-SEARCH
  (no dialog — the tap resolves on the CS provider + opens the details page
  of the best match); the customizability (Settings → Appearance → Search
  page: the result-card title placement, overlay/below).
- **WS-3**: the airs COLLAPSED = 3 rows (2 released + THE next-to-air — the
  user's round-165 refinement); the expanded = the full window; the see-all
  routes by the enabled kinds (the non-anime kinds open the search screen
  pre-set to their data source — no more Aniyomi catalog on a movies-only
  home); the suggested field samples randomly across the pools; the mood
  chips load the by-genre merged pools per kind; the Settings list format +
  the text removals.
- **WS-4**: the frosted next-air pill (accepted by the user).
- **WS-6**: the picker BRANDED marks (the AniList blue / the TMDB dark / the
  TVmaze gold tiles + the initials), the one-word labels, the metadata note
  removed; the Trackers cards' one-line roles (Anime and manga / Movies and
  TV series).

## THE EXTENSION PRIORITY SYSTEM (the foundation — the spec first, per the user's order)

**DOCUMENTATION/EXTENSION-PRIORITY-SYSTEM.md** = the spec (the events, the
weights, the keys, the integration points, the rules). Implemented:
- The schema v6→v7 (`6.sqm`): the extension_usage table (the counters + the
  denormalized score + the score index) — BACKUP_TABLES parity next.
- **ExtensionUsageTracker** (:core:database, dependency-free): the
  transactional read-modify-write (no UPSERT below SQLite 3.24), the
  SEARCH/Open/WATCH/Resolve/FAIL events with the weights
  1/2/5/2/-3 (floor 0), the score-ordered reads.
- **ExtensionPriorityPreferences**: the toggle (ON default; the recording
  continues while OFF; the history survives the toggling).
- The search VM: the SEARCH records (the CS + the aniyomi), the
  priority-aware CS provider sort (the score desc, the never-used
  alphabetical).

## The compile-round ledger (the round: 6 dispatches, 4 reds — each single-cause, disclosed)

44889325056 RED (the tracker's purity: the Logger/System in the
dependency-free module) → 44889636638 RED (the imports + the scoreFor's
missing brace) → 44890112539 RED (rowsByTier's missing brace) →
44890360448 RED (the setEnabled JVM clash) → 44890753936 RED (the
search-impl's missing :core:database dep) → 44891009039 GREEN (the combine
import + nowMs). THE LESSON: the script-driven edits keep costing compile
cycles — the targeted-compile pre-flight is the standing improvement.

## The v1.1.103 debug release (the AUTOMATIC cadence)

- The branch from the green head dfcdd68f; the bump 10120 → **10203** /
  "1.1.103"; the annotated tag (the message = the user-facing what's-new).
- The run **44891862248 GREEN**; LIVE stable (not prerelease); the APK's
  sha256 **4db05b5ff477cd008656c91625a20b30bc315e9c66378b324145fa767647816f**
  matches SHA256SUMS.txt (the verify copy: planning/verify_v1103/).
- https://github.com/Khurram00964/TEMP/releases/tag/v1.1.103

## THE EXPLICIT LEFTOVER LIST (the next session, in the order)

1. **The MAL tracking implementation** — the full module (the PKCE-plain
   OAuth, the search/link flow, the TrackSheet mapping) + the >1-linked
   tracker picker sheet in the details' 3-dot menu + the "Stop tracking"
   rename. The keys are registered; the flow plan is banked.
2. **The priority system's deep integration** — the resolver's server order
   + the auto-select + the fail attribution + the Settings card + the
   aniyomi-source sort + the backup parity.
3. **The extension-priority data-source hand-off** — the provider pick =
   the highest-scored trusted CS provider (upgrading the remembered/first).
4. **The TVmaze airs integration** (the airs-by-date rows for the series
   mode) + **Bangumi** (the donghua detail source).
5. **The home deep pages** — the catalog/see-all grids' full kind-awareness
   + the all-genres page's combined list.
6. **The startup-hang confirmation** — the update-over-older-version repro
   (v1.1.5-era) + the logcat.
