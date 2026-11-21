# 01 — The State Before the Revert (the rounds 163–165's implemented work + the post-mortems)

> The honest record: what was built in the rounds 163–165, what worked on
> device, what FAILED and why. The code lives on `release/1.1.101/102/103`
> (+ the tags v1.1.101/102/103 on TEMP).

## Round 163 — Steps 0–3c (the commits 9fb3d46d…2528c820 + the release/1.1.101 line)

**What was built:**
1. The Airs-today reorder (releasedHead + upcomingBody + releasedPad) + the
   24h released bound + Option A (the static accent pill + the one-shot
   entrance).
2. The DB v4→v6: 4.sqm (the episode_number denorm + season_number), 5.sqm
   (the track_entry/content_tracking_state FK-free rebuild + times_watched +
   the UNIQUE tracker-id index + the browse expires index), the 7 lock tests.
3. core/tracker-simkl (the PKCE-S256 OAuth V2, the sync family, the /redirect
   id bridge accepting anilist/tmdb ids, SimklTracker, the Trackers card).
4. The args bundles (WatchLaunchArgs/CsEpisodeLaunchArgs — the named-arg
   navigation) + the dead onPiPClick removed.
5. The DetailsViewModel normalization start (the state types + the tracking
   cluster as verbatim extension moves).
6. The doc drift fixes + the 4 specialized agents (.zcode/agents) + the
   CORE_RULES §3.1 (the response format).

**What the device confirmed (the v1.1.101 round):** the install-over worked,
the checklist mostly passed; Simkl linked end to end.

**What the device REJECTED (the v1.1.103 round — the regressions):** see 02.

## Round 164 — Step 3d + v1.1.102 (the commit 1cd80f8b + the release/1.1.102 line)

1. The home MULTI-SELECT content modes (HomePreferences.contentKinds — the
   delimited-string set, the anime-only default) + the reactive flow (the
   INSTANT APPLY via the VM's collect).
2. The merged multi-kind pipeline (the per-kind section keys
   home_trending_movies/series/donghua under the SAME codec; the concat
   merge; the failure-isolated fetches).
3. THE PARITY TRICK: the TMDB items = synthetic AniListAnime with NEGATIVE
   ids (the home composables render them unchanged; the negative-id taps
   route to the search hand-off).
4. The bold Settings screen (HomeContentScreen — the 2×2 cards → the list
   format per the device order) + the nav branch + the hub row.
5. The Simkl client id LIVE (the user's registration).
6. v1.1.102 released automatically (the cadence lesson).

## Round 165 — the device-fix round (the commits edb81e45…1389f2ca + the release/1.1.103 line)

1. The TVmaze JsonNull hardening (the literal nulls threw on
   `?.jsonObject` — every object access null-safe cast now). **DEVICE:
   the crash FIXED** (no crash on the TVmaze selection).
2. The Simkl login-state restore at construction. **DEVICE: FIXED** (stays
   linked across restarts).
3. The startup hardening: the maintenance DB get inside the coroutine (the
   main-thread DB open at boot was the hang's prime suspect). **DEVICE: the
   hang RECURRED on the update-over** (the black screen at launch, cleared
   by clear-data) — the full diagnosis is STILL OPEN (the logcat needed).
4. The search SHARED-LIST refactor: the data-source grid rides the same
   gridState skeleton (the collapse/the recents/the load-more), the TMDB
   pagination, the filters hidden, the mode-switch consistency, the titles
   one-line, the search-button UX, the hand-off auto-search (no dialog).
5. The mood by-genre pools, the see-all routing (→ the search prefill —
   **DEVICE: REJECTED, see 02**), the HomeContentScreen texts.
6. The extension-priority FOUNDATION (the spec + the schema v7 + the tracker
   + the toggle + the CS provider sort + the SEARCH records).

## THE POST-MORTEMS (what failed and WHY — the fix patterns for the redo)

### PM-1: The Show more button did nothing
The expand flag (`_airsExpanded`) was read INSIDE the compose functions
(`composeAirsToday`/`composeGlobalAirs`) — which run in the DATA pipeline
(the load/refresh paths), NOT on the flag change. Toggling the flag triggered
NO recomposition of `_airsToday` (the rows only re-emitted on the ticker/
loads) → the button was dead. **THE FIX PATTERN**: the expand must live in
the UI layer (the screen keeps the collapsed/expanded LIST STATE derived from
the full row list + the flag — a `derivedStateOf` split, recomposed on the
toggle), not in the data composers.

### PM-2: The Airs rows showed 2, not 3
The 24h released bound (the round-163 fix for "the week-old rows posing as
released") emptied the released pool on the user's device → the collapsed
3-row build (`released.take(2) + upcoming.take(1)`) rendered as 2 (or fewer).
**THE FIX PATTERN**: the released pool needs the honest "released TODAY OR
since the last expand" semantics — the time-bound must be forgiving (48h+ or
the since-local-midnight), and the collapsed count must GUARANTEE 3 rows
(the fill logic: 2 released + 1 next; if the released < 2, the upcoming fill
to 3).

### PM-3: The see-all opened the SEARCH page
The round-165 routing (the non-anime kinds' see-all → the search screen with
the prefilled data source) was REJECTED by the user — the search page is not
a catalog. **THE FIX PATTERN**: the kind catalogs need REAL pages (a
kind-aware catalog grid: the trending/popular per kind with the
infinite scroll) — a new screen or the HomeCatalogKey extended with the kind
+ the data-source fetcher. Designed BEFORE implemented.

### PM-4: The suggested field stayed anime
The field's DB-cached covers (home_field_covers from the anime sessions)
served through the cache-first path — the pools-first sample check ran too
late (after the cache read) AND the kinds change didn't invalidate the field
cache. **THE FIX PATTERN**: the field derives from the pools ONLY (no
by-genre DB cache for the multi-kind era) + the kinds-flow triggers the field
re-derivation.

### PM-5: The mood pools were empty for movies/series
discoverTmdb's genre map read `TmdbApi().let { emptyMap() }` — the map was
ALWAYS empty (a round-163 script replace landed wrong). **THE FIX PATTERN**
(the shipped-in-165 fix, keep): the map fetches lazily via the injected Api;
the live curl verification (the discover works) precedes any "the API is
broken" conclusion.

### PM-6: The update-over hang RECURRED (the black screen at launch)
The round-165 hardening (the maintenance DB get inside the coroutine) did NOT
fix it. The remaining suspects: (a) the v4→v6 migration's runtime on the
user's real data (the main-thread open at the first access — WHICH thread is
the first access?), (b) the AnikutaApp's eager TrackingWatchSyncBridge/
Koin-resolution failures, (c) the AppRoot composition blocking. **THE FIX
PATH**: the logcat from the repro (the ErrorActivity net catches the crashes;
the splash hang = an ANR or a pre-handler death) + consider the defensive
migration timeout + move the DB open fully off main. THIS IS THE #1 BLOCKER
for shipping any schema-carrying release.

### PM-7: The home modes' depth was shallow
The mood chips (the movies: "nothing was showing" — the empty-map PM-5), the
all-genres page (the AniList tags only), the surprise depth (the trending
feed is ~20 recent items — the randomization has no old content to draw), the
see-all (PM-3). **THE FIX PATTERN**: the per-kind pools need DEPTH (the
trending + the popular + the top-rated per kind — 3 calls each) and the
by-genre fetches per kind (the TMDB discover + the AniList genre) — designed
in 03-THE-HOME-PAGE-SPEC.md.

## What SURVIVED on device (the keep-list)

- The TVmaze crash fix (the JsonNull hardening) — the crash is gone.
- The Simkl linking + the stay-linked (after the restore fix) — the user
  confirmed the link flow works end to end.
- The search picker (the opening + the three options + the selection) —
  the structure works; the logos/labels are the redo polish.
- The TMDB/TVmaze search results render (the movies search worked).
- The home content kinds toggle + the Settings screen (the user: "it was
  shown properly there. It was perfect") — the UI stays; the home's data
  layer is the redo.
- The donghua feed (the user: the donghua selection switched the suggested +
  the surprise correctly — the partial depth worked).
- The backup/restore on the v6 schema (the user's restore worked).
