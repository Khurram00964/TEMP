# 05 — The TRACKING Spec (the decisions + the FULL MAL plan)

> The user's tracking decisions, final: **anime = AniList + MAL; movies +
> TV = Simkl.** The AniList + Simkl modules were built in the rounds
> 163–165 (reverted with the code — the recovery branches hold them); the
> MAL module was NEVER built (the explicit leftover). This file is the
> redo's tracking contract.

## T-1. The tracker roles

| The kind | The trackers | The state |
|---|---|---|
| Anime (+ manga, the Aniyomi side) | **AniList** (the OAuth, live) + **MAL** (the OAuth + the sync — TO BUILD) | AniList works end to end; MAL = the keys registered, the module unbuilt |
| Movies + TV series | **Simkl** (the PKCE-S256 OAuth V2 — TO REBUILD from the recovery branch) | The v1.1.102/103 rounds proved the link flow works on device (the user confirmed); the code lives on release/1.1.103 |

## T-2. The MAL plan (the FULL module — the next session's first item, banked)

- **The OAuth**: PKCE-plain (`code_challenge` = the base64url of the SHA-256
  hex — MAL's quirk, NOT S256-with-verifier-echo), the 64-char verifier, the
  `mal://redirect` scheme (the key `3ef19e73545f5029bbb1f2bfd62fd6c5` — 08).
- **The endpoints**: the authorize URL `https://myanimelist.net/v1/oauth2/authorize`,
  the token `https://myanimelist.net/v1/oauth2/token`, the API
  `https://api.myanimelist.net/v2` (`/anime/{id}` the fields, the
  `/users/@me/animelist` PUT the status/progress/score).
- **The search/link flow**: the details' TrackSheet gains the MAL row → the
  MAL search (`/anime?q=`) → the pick → the mapping stored.
- **The TrackSheet mapping**: the watching/completed/on-hold/dropped/
  plan-to-watch ↔ the app's states; the episodes ↔ the progress; the score
  0–10.
- **The >1-linked tracker picker**: when MORE than one tracker is linked,
  the details' 3-dot menu shows the tracker picker sheet (the AniList / the
  MAL rows for anime; the Simkl row for the movies/TV) — the pick targets
  the track action.
- **The rename**: the "Stop tracking" wording (the user's order) replaces
  the old untrack label.
- The Trackers cards (the Settings): the one-line roles (Anime and manga /
  Movies and TV series) — the approved round-165 shape.

## T-3. The Simkl plan (the rebuild from the recovery)

- The module: core/tracker-simkl (the PKCE-S256, the sync family, the
  /redirect id bridge accepting the anilist/tmdb ids, SimklTracker, the
  preferences) — the recovery branch `release/1.1.103` holds the LAST good
  shape (the login-state restore at construction included — the cold-start
  logout bug fixed there).
- The client id: `bad5a61d7eb08f8bd0059650b1e66bf0135542014f900100bbb8069c8cd604ba` (08).
- The scope: movies + TV series; the anime stays with the AniList/MAL (the
  user's explicit split).

## T-4. The DB contract

- The track_entry + the content_tracking_state tables (the v5 rebuild —
  FK-free, the times_watched, the UNIQUE tracker-id index) + the v4 denorm
  (the episode_number + the season_number) — the recovery branches hold
  the migrations; the redo re-lands them WITH the migration discipline
  (the STANDING rule: the versioned migrations, the lock tests) AND the
  update-over hang diagnosed FIRST (08/README rule 8 — the migration is the
  hang's prime suspect).

## T-5. The hard rules

- The tracking NEVER blocks the playback (the fire-and-forget records).
- The tracker failures NEVER crash (the runCatching everywhere — the
  SimklTracker precedent).
- The tokens refresh silently; the cold-start restore reads the tokens at
  construction (the round-165 lesson).
