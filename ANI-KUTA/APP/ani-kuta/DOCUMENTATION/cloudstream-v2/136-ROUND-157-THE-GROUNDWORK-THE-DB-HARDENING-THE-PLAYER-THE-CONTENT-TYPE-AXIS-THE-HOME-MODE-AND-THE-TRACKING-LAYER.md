# Record 136 — Round 157: the groundwork — the DB hardening, the player, the content-type axis, the home mode, and the tracking translation layer

> The user's order after the v1.1.8 professional release: implement the
> roadmap's workstreams (the player gaps, the cross-content tracking systems,
> the home content mode) with the DATABASE kept "well maintained... properly
> normalized... and the version and migration everything handled well" — the
> groundwork must make manga/novel later CHEAP. Sub-agents for review only;
> the implementation by hand. A critic sub-agent reviewed the whole round and
> caught a P0. The debug release v1.1.96 at the end.

## 1. The planning round (the recon)

Four Explore agents: the DB audit (the dual migration systems, the unbounded
tables, the CAST/SUBSTR scan, the missing transactions — vs the GOOD: the real
FKs, the backup parity, the batch discipline), the tracking viability
(VIABLE — CS syncData carries tmdb/imdb but the bridge dropped it; Trakt is
the live-action tracker; TMDB is identity only; Simkl the later single-service
option), the home architecture (the fixed item keys, the airs-toggle
precedent, the CS browse pipeline reusable verbatim), and the player wiring
(the ON_STOP pauses, the dead onPiPClick hook, the subtitle-delay pattern to
clone). MAL: SKIPPED by the user's order — the groundwork IS its enabler.

## 2. WS-A — the DB hardening (D-904)

- **4.sqm (v4→v5)**: `watch_progress.episode_number INTEGER NOT NULL DEFAULT 0`
  + the in-file backfill + `idx_watch_progress_main_number`. The hot queries
  (`getHighestWatchedEpisodeNumber`, the reactive batch twin) read the column —
  the per-save CAST/SUBSTR table scan is dead. Every upsert caller passes
  `episodeNumberFromKey` (0 for malformed keys — the old parse's semantics).
- **Transactions**: markAllWatched (markWatchedInternal extracted — a
  withContext INSIDE a SQLDelight suspend transaction breaks the
  thread-bound transaction), the library multi-select batches
  (addManyToCategory/removeManyFromCategory/removeManyFromLibrary), the
  download completion trio (DownloadStore.completeWithEpisode).
- **The growth bounds**: watch_session >400d (the profile's heatmap caps at
  365d), data_cache_episode 180d-fetched (RAISED from 30d by the critic — the
  Library's audio pills aggregate these rows with no re-fetch path), expired
  browse_cache rows deleted. The sweep runs on app start (the playback-cache
  launch pattern).
- **The post-restore reconcile**: BackupManager recomputes episode_number from
  the keys after the DB commit (pre-column backups restore DEFAULT-0 rows).
- **The tests**: SchemaMigrationTest extended (the 1→5 chain backfills; the
  column serves the aggregates) — 8/8 pass. TWO LATENT BUGS fixed en route:
  two pre-existing tests declared `fun "name"()` (double-quoted — never
  compiled; CI builds no unit tests) and createV3Shape collided with setUp
  (the v3→v4 tests never ran).
- **DATABASE-CONTRACT.md** (DOCUMENTATION/database/): the schema, the key
  formats, the 7 migration rules, the content-type axis, the change checklist.

## 3. WS-E — the player (D-905)

- **The audio delay** (MPV): PlayerPreferences.audioDelay + AudioSettingsSheet
  (the subtitle-delay stepper's audio twin) + the audio-sheet "Audio delay…"
  row + applyAudioPreferences (setOptionString at init, setPropertyDouble
  live).
- **The CS aspect toggle**: the PlayerView resize modes (Fit/Fill W/Fill H/
  Crop) behind the MPV tray's Zoom-button pattern; session-only.
- **PiP**: the manifest flag; core/common PipState (eligible/isInPip/
  requestEnter with the ContextWrapper unwrap); onUserLeaveHint auto-enters
  when the screens marked eligible (fullscreen + playing); the opt-in tray
  button in BOTH stacks (the MPV onPiPClick dead param now live via
  FullscreenMode's pass-through); the controls hide while in PiP; the flags
  reset on screen dispose. The ON_STOP pause observers are PiP-safe (PiP is
  PAUSED, not STOPPED).

## 4. WS-B — the content-type axis (D-906)

- **SAnime** gained `external_ids`/`content_kind` (the Task-46 default-accessor
  pattern) — AND SAnimeImpl OVERRIDES them (the critic's P0: without the
  overrides the interface's no-op setters made the whole channel dead).
- **The bridge** captures syncData (≤8 non-blank entries) + the kind from the
  LoadResponse type (MovieLoadResponse→movie, TvSeriesLoadResponse→tv_series,
  AnimeLoadResponse→anime). toUnifiedAnime carries both.
- **content.sq** `updateContentType` (guarded anime|tv_series|movie) + the
  repository wrapper + the resolver's `contentType` param + the exists-path
  re-classification (the convergence guard: only when the row's type differs —
  the critic's P2: every open otherwise bumped updated_at).
- **The movie mode**: a CS movie opens with a Play-movie hero (the standard
  resolve flow) and hides the episode list.

## 5. WS-D — the home content mode (D-907)

- `HomeContentMode {ANIME, SERIES_MOVIES, BOTH}` + the Settings > Home feed >
  Home content row (the Airs-toggle precedent).
- The CS block: the first non-NSFW hasMainPage provider's shelves via the
  search page's phased loader; the provider name on the head; the cards route
  to AnimeDetailsKey.Extension (CsSourceIds.idFor), the heads to CsCategoryKey.
- The gating: SERIES_MOVIES runs ZERO AniList discovery; BOTH loads both;
  a runtime mode switch re-targets the feeds (the critic's P1-2). The loader
  awaits `sourcesLoaded` (the critic's P1-1: the plugin scan is async — the
  init-time pick saw an empty list). The CS fetch-flag clears on the null-
  provider path (retryable).

## 6. WS-C — the tracking translation layer (D-908)

- TrackerType.TRAKT + TrackKind (SERIES/MOVIE) + TrackEntry.kind/timesWatched
  (additive defaults — AniList untouched).
- **TrackerLinkResolver**: the per-tracker id seam. TrackSyncManager consults
  the resolver for the tracker's type (relayWatchEvent AND relayRating — the
  critic's P1-4: the rating relay would have leaked an AniList id into a TMDB
  id space); ANILIST falls back to its own path byte-identically.
- **core/tracker-trakt**: TraktAuth (PKCE S256, the redirect anikuta://trakt-
  auth, CLIENT_ID = "" = not configured) + TraktTracker (the login/token
  exchange OFF Main per the critic, the movie /sync/history sync, the series
  sync honestly deferred to the id-resolution round). The trackers list
  carries it — dormant until the user's app registration lands. The manifest
  filter + the MainActivity dispatch (both cold and warm paths).

## 7. The critic round (D-909)

The adversarial Explore review of all 7 commits caught: **P0-1** (the SAnimeImpl
channel — without it the content-type axis, the movie mode, and the identity
capture were ALL inert on device), P1-1 (the async plugin-scan race), P1-2
(BOTH never loaded CS; no live mode switch), P1-3 (the Trakt exchange on
Main), P1-4 (the rating id leak), P1-5 (the in-PiP hiding unimplemented +
the stale flags), and the P2 warts (the convergence, the prune/pills clash,
the honest series deferral, the dead module file, the double KDoc). ALL
fixed + compiled. The reviewer also verified the migration shape parity, the
restore placement (after COMMIT), the PiP lifecycle safety, and the ANIME
default byte-identity.

## 8. CI + THE RELEASE (D-910)

- The dispatches: 44621200236 (pre-critic) GREEN, **44625819352 GREEN on
  2811c395** (post-fix), **44626990190 GREEN** (the Release APK run).
- **v1.1.96 / 10196 LIVE on Khurram00964/TEMP**: stable + latest,
  ani-kuta-v1.1.96-debug-arm64-v8a.apk (76,685,744 bytes); the downloaded SHA
  (663877dd...) MATCHES. Sideloads over v1.1.95.
- **NEXT ROUND**: background playback (the engine-ownership move — the round's
  one deferred structural item), the search-with picker + the Trakt/TMDB
  activation (awaits the user's TMDB API key + the Trakt client_id), MAL when
  ordered. The manga/novel groundwork stands (the contract doc's axis).
