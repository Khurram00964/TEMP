# Record 140 — The revert: main returns to the post-v1.1.8 state

> The user's verdict on the post-v1.1.8 builds (v1.1.96–99): "not satisfied —
> a lot of things not handled properly... we outright messed the things up
> bad." The specifics: the MAL tracker's UI didn't match the AniList card and
> the linking didn't work; the home/search regressions (the two-option source
> toggle was replaced). THE ORDER: (1) a single file documenting everything
> post-v1.1.8 (the plans, the findings, THE KEYS), (2) revert main to the
> exact post-v1.1.8 state (the docs updates included), (3) the file survives.

## 1. THE FILE

**ANI-KUTA/IMPROVEMENTS-TO-DO.md** — the single record: the keys (the MAL
client id, the Trakt client id, the TMDB v3 key + the v4 read token), what
was built (the rounds 157–160 summary), what worked, what failed (the
specifics), the research findings (the MAL/Trakt/TMDB/Jikan/Bangumi API
essentials, the $stable + init-order lessons, the IMDb not-viable verdict),
the prioritized redo list, and the recovery map (the release branches + the
tags).

## 2. THE REVERT

- main: `b25e5210` → **`1c02a924`** (the doc-135 record — the exact
  post-v1.1.8 state) via the hard reset; then the FOUR docs records
  (136–139) cherry-picked back (the user's "documentation updating... needs
  to be included"), then IMPROVEMENTS-TO-DO.md committed (**1ff1ad47**).
- Force-pushed TEMP main (with-lease). The sanity Build
  **44794149475 GREEN** — the restored tree compiles.
- The version on main: **10120 / 1.1.20** — the next release MUST bump to
  ≥ **10200** (the in-place update over the installed v1.1.99, 10199).

## 3. THE RECOVERY MAP

Every reverted round lives on its release branch + tag on TEMP:
release/1.1.96..99 + v1.1.96..99 (the round-157..160 work, recoverable
piece by piece). The lessons worth carrying: the two P0 fixes (the
init-order + the $stable static stamp) — real bugs with real fixes; the
DB hardening (the v5 migration + the tests); the audio-delay/CS-resize/PiP
implementations. The failures to NOT repeat: the MAL card's UI mismatch +
the broken linking; the home/search UX regressions (the removed
two-option toggle; the stripped CS layout).

## 4. THE REDO LIST (the order)

1. MyAnimeList — the card matching the AniList visual language + the
   linking verified end to end.
2. The search sources — KEEP the two-option toggle; add the sources as
   entries, never replace.
3. The home modes — the layout parity FIRST, the data mapping second.
4. Background playback — finish + device-test + MediaSession.
5. The audio integrity — device-verify against the real corrupt URLs.
6. The DB/player groundwork — re-land piece by piece with the tests.
