# Record 144 — Round 164 (2026-11-21): Step 3d complete + the v1.1.101 debug release

> The user's round-163 verdict: good work, one improvement — the debug release
> should have been cut AUTOMATICALLY at the round's end (the standing cadence,
> banked in the agent memory). The Simkl app was registered: the client id
> landed and is LIVE. The order: wire Simkl, build Step 3d (the bold
> multi-select Settings + the home modes), then the release.

## Step 3d — the home content modes (1cd80f8b + the 4 compile rounds 7f209bc5/384aa5f1/86e90bbe/44747465; CI 44872500172 RED→44872892544 RED→44873101488 RED→44873527976 RED→44873812866 GREEN)

- **HomePreferences**: the MULTI-SELECT kinds (the user's correction — any
  combination of anime/movies/series/donghua; the delimited-string storage;
  the anime-only default = the invisible upgrade) + the reactive
  [contentKindsFlow] (INSTANT APPLY).
- **The merged pipeline**: per-kind section keys (`home_trending_movies/
  series/donghua`) under the SAME codec, cache-first, failure-isolated
  fetches, the concat merge; LAYOUT PARITY via the synthetic mapping — TMDB
  items ride as AniListAnime with NEGATIVE ids, every home composable renders
  them unchanged.
- **The tap routing**: a negative id navigates to the SEARCH screen with the
  title staged (AnimeSearchKey gained the prefill fields — additive defaults;
  the search screen auto-opens the CS provider sheet; the pick runs the
  playback search — the CS-first rule end to end).
- **The donghua feed**: AniList `countryOfOrigin: CN` + TRENDING_DESC
  (keyless; Step 4's core landed).
- **The Settings screen**: HomeContentScreen — the BIG BOLD 2×2 cards (the
  icon tile + the ExtraBold name + the subtitle + the selected check), the
  live mix line, applied instantly. The Settings hub row (the Content group)
  + the nav branch.

## The Simkl id LIVE

`TrackerKeys.SIMKL_CLIENT_ID` = the user's registration (redirect
anikuta://simkl-auth). The Trackers card's link button is ACTIVE — the OAuth
flow runs end to end (the PKCE exchange + the token storage + the account
name). The setup-pending state can no longer render (the id is non-empty).

## The v1.1.101 debug release (the AUTOMATIC cadence — the user's lesson, applied)

- The branch `release/1.1.101` cut from the CI-green main head 44747465; ONE
  bump commit (10120 → **10201** / "1.1.20" → **"1.1.101"** — the D-430
  discipline: the mainline carries the dev values, bumps ride release
  branches); the annotated tag v1.1.101 (its message = the release body).
- The release-apk.yml dispatch on TEMP → run **44874251132 GREEN** (the
  version gate, the arm64-only gate, the apksigner gate all passed).
- LIVE: stable, NOT prerelease; the APK (76620140 → verified) +
  SHA256SUMS.txt; the downloaded APK's sha256 **b77244cc354288425a89ff6c8d3fc7e65dcd95c8e8c7a564f33759922ed4c3b9**
  matches BOTH sides (the verify copy: planning/verify_v1101/).
- The URL: https://github.com/Khurram00964/TEMP/releases/tag/v1.1.101
- Updates in place over v1.1.100/10200 ✓.

## What v1.1.101 carries (vs v1.1.100)

EVERYTHING from rounds 163-164 on the v1.1.100 base: the Airs-today contract
fix + Option A; the DB v6 (the tracking survival, times_watched, the season
axis, the UNIQUE links, the prunes, the standing migration discipline);
Simkl (movies+TV, PKCE S256, the id bridge); the search's data-source picker
+ the metadata grid + the CS-first hand-off; the home's multi-select content
modes + the bold Settings screen + the donghua feed.

## The compile-round ledger (the 3d build: 5 dispatches, 4 reds — disclosed)

The 3d batch carried 4 single-cause compile rounds: the moods onOpen's
orphaned brace (a replace left the lambda's tail) + the redundant
distinctUntilChanged on a StateFlow + the P2R caller; a Set-vs-List arg + a
single-quoted log literal; the nav script's steps 1-2 clobbered by its own
fresh re-read (the key + the branch never landed) + a missing comma + escaped
quotes; the Settings row landed inside the Player item's scope + a
RowScope-receiver extension + the missing import. THE LESSON: script-driven
multi-anchor edits on big files need a compile-fast verification loop — the
CI round-trip caught each, but the pattern (write → dispatch → read the
errors → fix) cost 4 cycles; a local `kotlinc`-less syntax sanity pass on the
edited regions would have caught most.

## The session facts

- Main: 44747465 (the 3d green head) + the post-release docs commit.
- The release: v1.1.101/10201 LIVE on TEMP (stable, sha-verified).
- The next decision: D-930; the next record: 145.
