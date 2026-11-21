# 03 — The HOME PAGE Spec (everything as discussed — the redo's contract)

> This is the consolidated home-page requirement from ALL the device rounds
> (the v1.1.100 → v1.1.103 reports + the orders). The redo implements
> against THIS file, section by section. The base: the post-162 home (the
> v1.1.100-era layout) — the layout language STAYS.

## H-1. The AIRS-TODAY section (the #1 priority — the rejected twice)

- **The COLLAPSED view = exactly THREE rows**, always:
  1. Row 1–2: the two most-recently RELEASED episodes (today's window —
     see the pool rules below).
  2. Row 3: THE next-to-air episode (the soonest upcoming) — the frosted
     next-air pill stays (the user accepted it).
- **The fill rule (PM-2's lesson)**: if the released pool has < 2 entries,
  the UPCOMING entries fill the 3 slots. The collapsed view NEVER shows
  fewer than 3 when the schedule has ≥ 3 entries. With 9+ entries available
  the user must see 3.
- **The EXPANDED view = the full window**: the released episodes (desc, the
  newest first, risen to the top) then the upcoming (asc — the soonest
  first). The glides animate the expand/collapse.
- **Show more / Show less MUST WORK** (PM-1's lesson): the expand state
  lives in the UI layer — the screen derives the visible list from the full
  rows + the flag (a `derivedStateOf`-style split recomposed on the
  toggle). The flag is NEVER read inside the data-pipeline compose
  functions.
- **The released pool semantics (PM-2's lesson)**: "released" = aired TODAY
  or since the last expand — a FORGIVING bound (the since-local-midnight or
  48h, NOT the hard 24h that emptied the pool). The week-old rows posing as
  released stay excluded.
- **The accent**: Option A — the static accent pill on the released rows +
  the one-shot entrance (the round-163 approved shape).
- The GLOBAL airs (the series kind): the same 3-row contract.

## H-2. The CONTENT MODES (multi-select kinds)

- The kinds: **ANIME / SERIES / MOVIES / DONGHUA** — multi-select, the
  instant apply (the kinds flow → the VM re-collects).
- The Settings screen = the HomeContentScreen list format (the user
  approved the round-164 screen: "it was shown properly there. It was
  perfect") — KEEP that UI; the data layer under it is the redo.
- **The merged home**: every enabled kind contributes its sections under
  the same layout codec. The failure-isolated fetches (one kind's failure
  never blanks the home).
- The TMDB/TVmaze items render via the synthetic-AniListAnime parity (the
  negative ids) — the layout is IDENTICAL to the anime cards (the user's
  "the front end should stay exactly the same").

## H-3. The SUGGESTED field (PM-4's lesson)

- The field follows the ENABLED KINDS always — NO stale anime covers.
- The field derives from the pools ONLY (no by-genre DB cache in the
  multi-kind era); the kinds change invalidates/re-derives the field
  immediately (the kinds-flow triggers it).
- The samples randomly across the kind pools (the round-165 intent, kept).

## H-4. The MOOD / GENRE chips (PM-5 + PM-7's lessons)

- The chips per kind: the anime by name, the donghua the CN+genre, the
  TMDB discover by the genre id (the movies/series). A kind without a genre
  contribution shows nothing for itself — but the MERGED pool must never be
  empty when the enabled kinds have genres.
- The genre map fetches lazily via the injected Api (the round-165 fix —
  the ALWAYS-empty `TmdbApi().let { emptyMap() }` class bug is banned).
- The live curl verification of the discover/genre endpoints precedes any
  "the API is broken" conclusion.

## H-5. The SEE-ALL / the catalogs (PM-3 — the BANNED routing)

- **BANNED**: the see-all → the search page. The search page is NOT a
  catalog (the user rejected it outright: "which was not good").
- **REQUIRED**: real kind-aware catalog pages — a catalog grid per kind
  (the trending/popular per kind, the infinite scroll), reached from the
  see-all. EITHER a new screen OR the HomeCatalogKey extended with the
  kind + the data-source fetcher. **Designed BEFORE implemented** (the
  user's design-first order).
- The anime kind keeps the existing AniList catalog unchanged.

## H-6. The ALL-GENRES page (PM-7)

- The combined list: the per-kind genre fetches MERGED (the AniList genres
  + the TMDB genres per kind) — NOT AniList-only.

## H-7. The SURPRISE / depth (PM-7)

- The surprise shuffles the whole merged pool; the pools need DEPTH: the
  trending + the popular + the top-rated per kind (3 calls each), so the
  randomization has real content to draw from.

## H-8. The unchanged keepers

- The hero + the trending sections worked on device — the shapes stay.
- The frosted next-air pill stays.
- The home section keys/codec structure stays (the per-kind keys ride the
  same codec).
- The 5MB asset budget + the incremental loading + the design quality bar
  (the memory: ani-kuta-design-quality-bar).
