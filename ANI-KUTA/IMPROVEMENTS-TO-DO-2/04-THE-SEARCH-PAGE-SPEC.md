# 04 — The SEARCH PAGE Spec (everything as discussed — the redo's contract)

> The consolidated search-page requirement from the device rounds. The redo
> implements against THIS file. The core principle: **ONE shared search
> base** — every source (the AniList, the TMDB, the TVmaze, the extensions)
> rides the SAME skeleton; no source has its own half-page.

## S-1. The ONE SHARED BASE (the user's explicit rule)

Every source gets ALL of:
- **The collapse** (contentAtTop): the top bar collapses on scroll — the
  DataSourceSuccess branch MUST be present (the round-165 lesson: the
  missing branch = "that effect was completely gone").
- **The recents** (the recent searches strip).
- **The load-more / pagination** (canPage + the DataSourceSuccess branch —
  the round-165 lesson: the missing gate = the TMDB load-more dead).
- **The result-card title styles** (the Settings customizability — below).
- **The rating/details display** (the meta year/rating on the cards).
- The titles ONE-LINE (the ellipsis, no wraps).
- The empty state, the error state, the loading state — identical shapes.

## S-2. The DATA-SOURCE PICKER

- The picker sheet = the AniList / TMDB / TVmaze options (like the
  extension-side pickers — the user's parity demand).
- **The branded marks**: the real LOGOS (the AniList blue / the TMDB dark /
  the TVmaze gold), NOT the initials letters (the user rejected the "A/T/T"
  letters twice). The logo assets ride the 5MB budget (the incremental
  loading).
- The one-word labels; the metadata note removed.
- The picker opens from the search bar's source chip; the selection applies
  INSTANTLY (the defaults reload; the recents survive).
- The last-selected source persists (the preference store).

## S-3. The HAND-OFF (the CS-first playback)

- Tapping a TMDB/TVmaze result (a metadata-only item) → the app resolves it
  on the CloudStream providers → opens the details/playback of the best
  match. The AUTO-SEARCH flow (no intermediate dialog — the round-165
  approved shape).
- The negative-id synthetic items route the taps to the hand-off.
- The provider pick for the hand-off = the highest-scored trusted CS
  provider (the priority system's integration, 06).

## S-4. The search-bar UX

- The empty bar = NO search button; typing raises the pill (the round-165
  approved shape; the polish pending).
- The mode-switch consistency (the switching the source keeps the query +
  the state shape).

## S-5. The CUSTOMIZABILITY (the user's explicit demand — "more depth")

- Settings → Appearance → the Search page. The user called the single
  title-placement option insufficient ("only the title-style option").
- The redo ships AT LEAST: the title placement (the overlay/below), the
  rating badge on/off, the details line on/off, the grid density (the
  columns), and the recents visibility toggle.
- The Settings row's PLACEMENT: inside Appearance (the round-165 move) —
  the user's v1.1.102 complaint (the misplaced/ugly Search page row) is
  the regression guard.

## S-6. The filters

- The AniList filters stay; the TMDB/TVmaze sources hide the AniList
  filters (the round-165 approved shape).

## S-7. What the search page is NOT

- NOT a catalog. The see-all NEVER routes here (03-H-5). The search page is
  the user-driven query surface only.
