# IMPROVEMENTS TO DO — the post-v1.1.8 work log, findings, keys, and the redo plan

> **What this file is**: the single record of EVERYTHING we planned, built,
> researched and learned AFTER the professional **v1.1.8** release (rounds
> 157–160), written before the revert back to the post-v1.1.8 state. The
> code for all of it lives on the release branches (recoverable — see §7);
> this file is the map for redoing it PROPERLY.
>
> **Why the revert**: the device verdict on the post-release builds was "not
> satisfied — a lot of things not handled properly, a lot of issues". The
> specific failures are in §4. Rather than patching a mess forward, the
> main branch returns to `1c02a924` (the exact post-v1.1.8 state incl. the
> doc-135 record) and every item here gets redone on that clean base.

---

## 1. The keys (PROVIDED — keep with the app)

| Service | Key | Notes |
|---|---|---|
| **MyAnimeList** (client id) | `3ef19e73545f5029bbb1f2bfd62fd6c5` | Registered at myanimelist.net/apiconfig; redirect `anikuta://mal-auth` |
| **Trakt** (client id) | `RCANXjQblhWRIIYj0ax1ZmWH_S8e-IM0Va3Cz1A9qSY` | Development app (a 16-user cap until the same-page Production switch — same client id, nothing to redo); redirect `anikuta://trakt-auth` |
| **TMDB** (API key, v3) | `210d23e812a0b2f4ea75fe4e934ff526` | Personal tier (correct for the scope) |
| **TMDB** (v4 read token) | `eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiIyMTBkMjNlODEyYTBiMmY0ZWE3NWZlNGU5MzRmZjUyNiIsIm5iZiI6MTc5NTE5MjMwNi4yNjcsInN1YiI6IjZiMDA3NWYyZDA0N2RiYmRmMjhmNzJiZSIsInNjb3BlcyI6WyJhcGlfcmVhZCJdLCJ2ZXJzaW9uIjoxfQ.t5xheRXvTuL5hMVL5nLTuLZxx76q-hDfYJ_R39KazHA` | The v4 bearer — stored for the future v4 endpoints |

---

## 2. What was built after v1.1.8 (the summary of rounds 157–160)

- **Round 157 — "the groundwork"**: the DB v5 migration (the episode_number
  denormalization), the transactional bulk writes, the cache prunes, the
  restore reconcile, the migration tests, the DATABASE-CONTRACT.md; the
  audio-delay control; the CS aspect toggle; PiP; the content-type axis
  (movie/tv_series/anime); the home content mode; the tracking translation
  layer; the Trakt skeleton. Released as **v1.1.96**.
- **Round 158 — "the verification"**: three passes; the CS home loader's
  plugin-scan wait; the picker-trap fix; the PiP aspect. Released as
  **v1.1.97**.
- **Round 159 — "the device report"**: the two P0 crash fixes (the startup
  black screen — the init-order; the plugin load — the $stable stamp), the
  full MyAnimeList tracker, the background playback service, the Trakt/TMDB
  groundwork. Released as **v1.1.98**.
- **Round 160 — "the keys land"**: the three keys wired, the same-layout
  home attempt, the search source menu, the audio integrity system, the
  movie download button. Released as **v1.1.99**.

---

## 3. What WORKED and is worth keeping (verified on device or by the tests)

- **The two P0 crash fixes** — the startup black screen (the init-order:
  a collect fired synchronously in the constructor touching state declared
  after init) and the plugin load (the compose `$stable` stamp: STATIC
  FINAL, getstatic-read — now on BasePlugin + MainAPI). Both were real,
  device-confirmed, and the fixes are correct.
- **The audio-delay control** (the player) and **the CS aspect toggle** —
  no complaints; the patterns are solid (the subtitle-delay clone).
- **The DB hardening** — the migration tests (8/8), the transactional bulk
  writes, the prunes. No regressions reported.
- **The 6-hour home cache** (pre-v1.1.8, kept) — working.
- **The search-with-sources IDEA** — right idea, wrong execution (§4).

---

## 4. What FAILED / was badly done (the user's verdict + the specifics)

1. **MyAnimeList tracking** — "not implemented properly or handled well":
   - The Trackers card's UI doesn't match the AniList card (a different
     visual language).
   - **The linking doesn't work at all** — the OAuth flow/the card's state
     handling needs a full redo against the AniList card's proven pattern.
2. **The home page (the modes)** — "not handled well": the Series & movies
   mode replaced the anime layout with a stripped CS block instead of
   rendering the SAME sections with the new data. The attempted round-160
   same-layout fix (the generic HeroSlide + the CS slots) was part of the
   unsatisfactory state — redo from the clean base with the layout parity
   as the FIRST requirement.
3. **The search page** — "there were two options... you removed that
   option": the two-segment source toggle (AniList / Extensions) was
   replaced by a dropdown menu — a regression of the existing UX. The
   redo must KEEP the two-option toggle and ADD the new sources as
   additional entries/segments without removing the existing behavior.
4. **The background playback** — implemented but unverified on device;
   the MediaSession upgrade is missing; treat as a redo candidate with
   device testing first.
5. **The audio integrity system** — implemented (FFprobeKit probe + the
   3-attempt retry) but never device-verified against the actual corrupt
   files; re-test with the real failing URLs before trusting it.

---

## 5. The research findings (keep — they save the next attempt)

- **MAL OAuth**: PKCE with `code_challenge_method=plain` ONLY; the access
  token is 1h (the refresh flow is mandatory); `PUT /anime/{id}/my_list_status`
  (partial update); the score scale 0-10 (the app stores 0-100 → /10);
  `GET /anime/{id}?fields=my_list_status` for the fetch;
  `GET /anime?q=` for the search.
- **MAL ids come free for AniList-linked entries**: AniList's payload
  carries `idMal`, already persisted in `ContentDetails.dataExtras.idMal`
  (the `malId` accessor) — no search needed for linked entries.
- **Trakt**: OAuth PKCE (S256), `/sync/history` accepts `ids: {tmdb|imdb}`
  directly — the TMDB id resolution is the bridge; the Development app's
  cap lifts with the same-page Production switch.
- **TMDB**: the v3 key works as the `api_key` query param; the images at
  `image.tmdb.org/t/p/{size}{path}`; `/trending/all/day`, `/search/multi`,
  `/tv/popular`, `/movie/popular` are the home/search endpoints.
- **Jikan** (KEYLESS — the MAL data without MAL): `api.jikan.moe/v4/anime?q=`,
  `/top/anime`; the 3 req/s / 60/min fair-use limits.
- **Bangumi** (KEYLESS — the donghua/Chinese source): `api.bgm.tv` — the
  documented next addition for the Chinese content.
- **The compose $stable lesson**: the plugin host classes MUST carry the
  compose-compiler stamp the plugins were built against — STATIC FINAL
  int `$stable` (getstatic-read; an instance field throws
  IncompatibleClassChangeError instead).
- **The init-order lesson**: a `viewModelScope.launch` collect in an init
  block runs its FIRST emission synchronously (Main.immediate) — any state
  it touches must be declared BEFORE the init block.
- **IMDb tracking**: NOT VIABLE — no public API; scraping violates the
  terms. Closed.
- **Simkl**: free + PKCE + covers anime/TV/movies in one account — the
  candidate for a later unified tracker; the translation layer accepts it
  like MAL did.

---

## 6. The redo list (in the priority order, on the reverted clean base)

1. **MyAnimeList — redo properly**: the Trackers card MUST match the
   AniList card's visual language; the linking flow MUST work end to end
   (test against the real MAL registration before shipping). The tracker
   code from release/1.1.99 is the reference — the UI and the link
   handling are the redo.
2. **The search sources — redo properly**: KEEP the existing two-option
   toggle (AniList / Extensions) EXACTLY as it was; add the new sources
   (TMDB / Jikan) as additional menu entries off the existing toggle —
   never remove or replace the working UX.
3. **The home modes — redo properly**: the Series & movies mode renders
   the SAME sections with the CS/TMDB data (the hero, the rail, the
   suggested, the genres — the layout parity first, the data source
   second). Design the data mapping BEFORE touching the UI.
4. **Background playback — finish + device-test**: the service exists on
   release/1.1.99; verify on device, add the MediaSession upgrade, THEN
   ship.
5. **The audio integrity — device-verify**: probe + retry against the
   real corrupt URLs from the device report.
6. **The DB/player groundwork** (the v5 migration, the transactions, the
   prunes, the audio delay, the CS resize, PiP): re-land piece by piece —
   each was verified in isolation; re-apply with the same care and the
   same tests.

---

## 7. Where the reverted work lives (the recovery map)

| The work | The branch / tag on TEMP |
|---|---|
| Round 157 (the groundwork) + v1.1.96 | `release/1.1.96` / tag `v1.1.96` |
| Round 158 (the verification) + v1.1.97 | `release/1.1.97` / tag `v1.1.97` |
| Round 159 (MAL + background + the P0 fixes) + v1.1.98 | `release/1.1.98` / tag `v1.1.98` |
| Round 160 (the keys + the same-layout + the search) + v1.1.99 | `release/1.1.99` / tag `v1.1.99` |

The records: DOCUMENTATION/cloudstream-v2/136–139 (the full per-round
details, including the crash fixes worth re-applying — the init-order and
the $stable stamp).

## 8. THE VERSION NOTE (critical for the next release)

The reverted main carries versionCode **10120 / 1.1.20** (the release
branches carry the bumps). The next release branch MUST bump to
**≥ 10200** so it updates in place over the installed v1.1.99 (10199).
The suggested next version: **1.1.100 / 10200** — or a clean **1.2.0 /
10200**.
