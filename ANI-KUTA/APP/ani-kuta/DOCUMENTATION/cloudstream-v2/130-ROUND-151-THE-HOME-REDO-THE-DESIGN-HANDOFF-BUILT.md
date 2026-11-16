# Record 130 — Round 151: THE HOME REDO — the design handoff, built

> The user's order: scrap the current home screen and ALL of its logic; rebuild from the
> design handoff (ani-kuta-design-handoff/ani-kuta — a Next.js prototype whose code is the
> design truth); add the user's behavioral rules per section; then THE DEBUG RELEASE.

## 1. The feed (agent A)

- **TopBar**: the wordmark + the theme-adapting play-mark chip (primary@0.14); the 44dp
  profile button → ProfileKey.
- **Hero** (the full Hero.tsx port): the 3-copy infinite re-anchor (both directions), the
  snap fling, the 5.5s auto-advance + the 8s hold-off after any manual interaction, the
  direction-aware dot seam (dot 1 = skip-back, the last dot = skip-next) with the
  countdown fill inside the lit dot, the peek 0.88/0.66 via per-frame graphicsLayer math,
  the dynamic two-line title lockup (the eyebrow + the ExtraBold line with the accent on
  the trailing season token), the exact two-layer scrim + the glass chips. Center-tap /
  Play → the details (the D-272 ad gate); a peeking tap centers.
- **ContinueWatching** (the user's rules): the completed dropped, position <10s dropped,
  the midway first (lastWatchedAt DESC, cap 20, deduped per mainId); the 188dp 16:9
  cards, the EP badge, the play chip, the 300ms/800ms animated progress, "N min left";
  **the art tap → PLAYS that episode** (`AnimeDetailsKey.autoPlayEpisode` — the Phase-3
  auto-click → Phase-2 auto-resolve → player; `openDetailsByMainId` gained the autoplay
  arg), **the title tap → the details**; See all → HistoryKey.
- **AirsToday**: the 2dp rail timeline, the node pills (the coral released fill, the
  pulsing next), the Watch/Aired/countdown chips (lib/time.ts's formats); the EXACT-6
  composition = 2 released desc + the next + 3 after asc; **the Library/Global source
  toggle** (HomePreferences.home_airs_source + the Settings "Home feed" group);
  Schedule → UpdatesKey.
- **TrendingRail**: the ranked 104x156 posters → See all → HomeCatalogKey.
- **MoodChips**: Surprise me = a genuinely random shuffle (not genre-based); the genre
  chips filter the trending pool locally (v1); All genres → HomeGenresKey.

## 2. The suggestion field + the genres (agent B)

- **HomeSuggestionField.kt (979 lines)** — the SuggestionField.tsx physics ported: the
  7×5 torus honeycomb (60x90-unit tiles, the odd-row offset), the unit clamp, the drift
  walk (16px/s, the runs 6-11s, the 55/30/15 turn policy, the per-run speed ±27%), the
  drag 1:1 + the fling (decay 3/s, cap 2600), the lens 1.5×→0.72× steepened ×1.25, the
  min-gap solver (30 mobility-weighted passes with the early exit, the 100px/s
  correction cap KEPT — the anti-jitter rule), the directional per-tile edge melt
  (DstOut gradients in the tile's own offscreen layer), the vignette, the field-in fade,
  the seed 0x51ed270b + the mulberry32 port, the shuffled 50-cover deal queue. One
  withFrameNanos loop → per-tile mutableFloatStateOf read in graphicsLayer (no
  recomposition).
- **CENTER-VS-OPEN (the user's order)**: OPEN_RADIUS_FACTOR 1.15 × the center tile's
  scaled half-diagonal — inside → open the details (the ad gate); outside → a 420ms
  smoothstep pan glide centers it; a centered tap always opens.
- **The pool**: the library-genre affinity (the top-3 library genres → fetchByGenre 25
  each, merged/deduped to 50) + the trending fallback.
- **The genres screens**: HomeGenresScreen (the 19 genres + the 30 aggregated popular
  tags — fetchPopularTags with the META_TAGS port, the adult filter, Hentai pinned
  last); HomeGenreScreen / HomeCatalogScreen (the shared paged 3-column grid, 24/page,
  the reached-end trigger, the skeletons, the retry) over the new AniListApi
  fetchByGenre / fetchByTag; the GLOBAL airs source wired via fetchGlobalAiringSchedule
  (RELEASING + nextAiringEpisode, composed per the 2+1+3 rule).

## 3. The verification + the ledger

- CI: 43964423488 RED (the animateFloat extension imports + the roundToInt import + the
  Long pad + CompositingStrategy.Automatic→Auto), 43966136243 RED (the DUPLICATED
  roundToInt import broke the overload resolution — the dedupe), 43968119937 RED (the
  same site — the score is already an Int, the call dropped), 43969911100 RED (the
  browse screens internal while dispatched from :app — publicized), **43971531153 GREEN
  on 84d75fca — the FULL home redo compiles**. 5 runs, disclosed.
- **THE RELEASE (D-886)**: the user's explicit order — the testplay-byte account STILL
  flagged (404 unauthenticated), so Routine A adapted to the TEMP mirror: release/1.1.91
  + ONE bump commit f6b87b2e (10120/1.1.20 → **10191/1.1.91**) + the annotated tag
  v1.1.91 ON THE BUMP COMMIT → the Release APK run **43973174773 GREEN** (dispatched on
  TEMP); verified live: stable + latest, ani-kuta-v1.1.91-debug-arm64-v8a.apk
  (76,334,864 bytes) + SHA256SUMS.txt; the downloaded SHA (981c89f1…) MATCHES. The
  install-over chain v1.1.89 → v1.1.90 → v1.1.91 holds (the committed debug key). The
  testplay-byte release stays pending the unflag + the user's order.
- Next decision: D-887; next record: 131.
