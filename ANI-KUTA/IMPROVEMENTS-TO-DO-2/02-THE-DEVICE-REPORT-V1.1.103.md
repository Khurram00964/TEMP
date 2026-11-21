# 02 — The Device Report v1.1.103 (2026-11-21, itemized + post-mortemed)

> The user's exact findings on the v1.1.103/10203 update-over. Each item →
> the post-mortem id in `01-THE-STATE-BEFORE-THE-REVERT.md`. The verdict:
> **"nothing was done properly at all... we need to stop here, revert back
> from where we started."** THIS REPORT IS THE REVERT'S REASON.

## The report, itemized

| # | The finding | The user's words | The post-mortem |
|---|---|---|---|
| R-1 | The UPDATE-OVER showed a BLACK SCREEN; the clear-data + the setup was needed to get in | "shows a black screen" | **PM-6** (still undiagnosed — the logcat needed) |
| R-2 | The AIRS showed **2 rows**, not 3; the schedule had **9+ entries** to show | "it should be 3 rows" | **PM-2** (the 24h bound emptied the released pool) |
| R-3 | The AIRS **Show more did NOTHING** | "the show more button was not doing anything" | **PM-1** (the flag read in the data pipeline, not the UI state) |
| R-4 | The MOVIES-only home: the hero + the trending OK, but the **SEE-ALL opened the SEARCH page** | "which was not good" | **PM-3** (the routing rejected outright) |
| R-5 | The SUGGESTED field still showed ANIME on the movies/series home | "it was still showing anime" | **PM-4** (the stale DB-cached covers survived the kind switch) |
| R-6 | The MOOD chips: NOTHING showing for movies/series | "nothing was showing" | **PM-5** (the always-empty genre map — root-caused in 165 but the pool depth still wrong) |
| R-7 | The ALL-GENRES page was ANILIST-ONLY | — | **PM-7** (no per-kind genre fetch on the deep page) |
| R-8 | The DONGHUA selection: only PARTIAL (the suggested + the surprise switched; the rest stayed anime-shaped) | "it worked a little" | **PM-7** (the depth incomplete) |
| R-9 | The overall verdict | "nothing was done properly at all" | — |

## What the report did NOT flag (the neutral-to-good items)

- The TVmaze crash did NOT recur (the round-165 JsonNull hardening held).
- The Simkl link survived the restarts (the restore fix held).
- The install-over itself worked (the APK was fine; the launch was the hang).
- The search picker still opened + the movies search still returned.

## The revert order (the user's, verbatim-intent)

1. **FIRST** create a folder (the IMPROVEMENTS-TO-DO precedent) carrying the
   previously-implemented items + this round's items + ALL the credentials/
   keys + the requirements (the home page, the search page, everything as
   discussed).
2. **THEN** revert main to the state after the round 162 (`49983800`) and
   continue from there.
3. The redo then follows the rules in `README.md` (the 8 rules) — the
   post-mortems' fix patterns are mandatory, the see-all's search routing is
   BANNED, the airs 3-row contract is the spec.
