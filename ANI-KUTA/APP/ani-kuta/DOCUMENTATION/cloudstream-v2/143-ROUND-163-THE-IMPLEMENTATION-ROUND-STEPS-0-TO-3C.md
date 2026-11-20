# Record 143 — Round 163 (2026-11-21): the implementation round — Steps 0-3c of the confirmed plan

> The user's device verdict on v1.1.100: the checklist PASSED (one issue — the
> Airs-today section showed three released rows and the next-to-air card
> "flashes"). The confirmed plan, executed in the agreed order: the airs fix
> (Option A) → the DB hardening → the Simkl tracker → the TMDB+TVMaze data
> sources + the search picker → (queued) the home content modes + donghua.

## Step 0 — the Airs-today fix + Option A (9fb3d46d, CI 44826792673 GREEN)

- BOTH composers assemble `releasedHead + upcomingBody + releasedPad` now
  (slot 3 is ALWAYS the next to air; the old pad-before-upcoming displaced it
  whenever released >= 3 && upcoming <= 3 — the steady state of the default
  Library mode: the schedule stores future episodes that age in and are never
  pruned). The library released pool gained the same 24h bound as the global
  composer (week-old episodes stop posing as "released today").
- The next-to-air pill's 4-layer 1.2s infinite strobe is REPLACED by Option A
  (the user's pick): a static subtle accent border + the accent-colored
  release time + a one-shot 450ms entrance (row-keyed, no scroll-return
  replay — the review's P2).

## Step 1 — the DB hardening v4→v6 (7659da49 + the purity fix 9b0be950 + the review fixes 190fbe46; CI 44828410758 RED→44828959511 GREEN→44832472089 GREEN)

- **4.sqm**: the reverted v5 denorm re-lands (watch_progress.episode_number —
  kills the reactive CAST/SUBSTR scan on every 10s save) + the season_number
  column.
- **5.sqm**: THE TRACKING-SURVIVAL FIX (the user's "very huge issue") —
  track_entry + content_tracking_state rebuilt FK-free per the 3.sqm/D-862
  doctrine; times_watched added (the movie-rewatch column);
  (tracker_type, tracker_id) UNIQUE after an in-file dedup; the browse
  expires index.
- **DatabaseMaintenance**: the daily app-open throttled pass — watch_session
  400d, data_cache_episode 180d, expired browse sweep.
- **EpisodeKeys**: the 3-part season format + dual-format parsers (the
  2-part anime keys keep resolving; TV writers activate later).
- **DATABASE-CONTRACT.md** re-landed + the standing migration discipline
  locked into CORE_RULES §30 (the user's order: proper migrations so any
  install migrates on update).
- 7 migration lock tests (incl. the tracker-link survival + the UNIQUE
  enforcement).

## Step 2 — the Simkl tracker (075f76f9; CI 44831234145 GREEN first-try)

- **core/tracker-simkl**: SimklOAuth (the AUTH-V2 PKCE **S256-only** flow —
  no secret; the 7d access/180d sliding refresh), SimklApi (the sync family
  + the /redirect id bridge accepting **anilist/tmdb** ids + search +
  all-items), SimklTracker (the Tracker impl — the status/progress mapping
  per resolved kind; movies skip watching/hold; the 0-100→1-10 score
  boundary; the honest not-configured state), SimklPreferences, the Koin
  module.
- **TrackerKeys** re-landed (the MAL/Trakt/TMDB registrations + the EMPTY
  Simkl client id — pending the user's simkl.com/apps registration).
- The composition root owns the named("trackers") aggregation (AniList +
  Simkl) without the anilist module growing a simkl dependency.
- The manifest gains anikuta://simkl-auth + the MainActivity dispatch; the
  TrackersScreen Simkl card clones the AniList card's visual language (the
  round-160 lesson).
- THE ROLES (the user's order): anime = AniList (+ the MAL redo pending);
  movies + TV = Simkl.

## Step 3a+3b — the data sources (ecaf14d2; CI 44833566340 GREEN)

- **core/tmdb** re-landed (trending/search/popular; the key ships
  configured; TmdbPreferences.enabled).
- **core/tvmaze** NEW (KEYLESS: search/shows, /schedule by date — the
  airs-by-date shape, /shows/:id?embed=nextepisode; no movies/trending —
  it complements TMDB per the research verdict).
- BOTH are DATA-ONLY (no streams — playback routes to the CloudStream
  extensions per the user's priority rule).

## Step 3c — the search data-source picker (066d65bf + 25a70068; CI 44834885516 RED→44835591799 GREEN)

- THE DEAD-TAP FIXED: the AniList segment mirrors the Extension side (the
  label shows the chosen source; an active tap opens the picker).
- **DataSourcePickerSheet**: AniList/TMDB/TVMaze rows, the disabled graying,
  the honest metadata-only footnote.
- The VM's data-source axis (persisted; TMDB trending defaults; TVmaze
  today-schedule defaults; the unified DataSourceResult grid via coil3).
- **THE PLAYBACK HAND-OFF**: a metadata result's tap → find-where-to-watch →
  pick a CloudStream provider from the EXISTING sheet → the title becomes
  that provider's live search (findInCloudstream on the onSelectCs
  intercept).

## THE ADVERSARIAL REVIEW (the user's demand: "test with your sub-agents... never trust them blindly")

The review attacked Steps 0-1 and found a REAL **P0**: the v1.1.96-99 device
cohort (schema v5 with episode_number only — verified via git tag --contains)
upgrades skipping 4.sqm (the generated chain guards oldVersion <= 4) → the v6
mapper's positional reads would crash-loop every query. FIXED:
migrateV5ShapeIfNeeded (the documented no-probes exception, scoped to
oldVersion == 5) + the shape lock test. Two P1s fixed: the legacy 1→2 tests
now run the chain to v6; the restore backfill re-runs inside restoreBackup's
transaction (a pre-v6 archive's rows landed episode_number=0 → the tracking
bridge would have RELAYED THE WIPE DOWN to AniList for restored tracked
content). The P2s fixed: the airs head gates on non-empty; the entrance
no-replay; the repo upsert refuses a foreign main_id claiming an owned remote
id; 4.sqm's header honesty. The reviewer's surviving-claims audit validated
the rest (the 6-row contract in every case, the migration craft, the
dual-format parsers' repo-wide sweep).

## The CI ledger (6 runs, all disclosed)

44826792673 GREEN (Step 0) · 44828410758 RED (the maintenance purity miss —
:core:database stays dependency-free; the caller owns the clock+logging) ·
44828959511 GREEN (Step 1) · 44831234145 GREEN (Step 2, first-try) ·
44833566340 GREEN (3a+3b) · 44834885516 RED (the 3c compile round) ·
44835591799 GREEN (3c). Eight runs for five batches — three reds, each
single-cause, disclosed.

## QUEUED (the next round)

- Step 3d: the home MULTI-SELECT content modes (anime/movies/series/donghua
  toggles — NOT a 4-way enum; the user's correction) + the per-section
  provider seam (LAYOUT PARITY FIRST — swap data, not composables) + the
  Settings data-sources page + the caching keys per source.
- Step 4: the donghua rails (AniList countryOfOrigin=CN — a query+model
  extension; keyless) + Bangumi later.
- The next decision: D-929; the next record: 144.
