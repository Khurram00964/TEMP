# Record 145 — Round 165 (2026-11-21): the device-fix round + the v1.1.102 debug release

> The v1.1.101 device report was extensive (the startup hang, the TVmaze
> crash, the search's separate-screen behavior, the home modes' depth gaps,
> the Simkl relink bug, the UI polish set). EVERY reported item addressed
> except the MAL tracking implementation (the explicit first item of the next
> round — the plan + the keys are ready).

## WS-1 — the P0 device fixes (edb81e45)

- **The TVmaze crash** (the confirmed stack: JsonNull is not a JsonObject at
  parseShow:92): TVmaze emits literal JSON nulls for the absent objects —
  every object access now goes through the null-safe cast + the JsonNull
  primitives are filtered (a null summary can't render the literal "null").
- **The Simkl cold-start logout bug**: the tokens persisted but
  BaseTracker's in-memory login flow seeded LoggedOut → every cold start
  showed Simkl unlinked. Restored at construction from the stored tokens.
- **The startup hang hardening**: the maintenance pass's eager
  get<AnikutaDatabase>() opened the DB + ran the v6 migration on the MAIN
  thread during onCreate — moved inside the coroutine. (The hang's full
  diagnosis needs the next update-over-old repro's logcat — the ErrorActivity
  safety net means any crash surfaces now; an ANR was the silent shape.)

## WS-2 — the search shared-list refactor (d25c7297 + 3 rounds; CI 44882161133 RED→44881554112 RED→44881937671 RED→44882161133 GREEN)

The user's architectural verdict: "the front end should stay exactly the same
regardless of anything" — the data-source results now ride the SAME
LazyGridState skeleton as AniList/the extensions (the collapse, the recents
header, the approach-bottom load-more). Plus: the TMDB pagination (20/page),
the filters honestly hidden for the data sources (the sheet is AniList-native),
the mode-switch consistency (the AniList tab routes by the data source), the
titles one-line + the meta year/rating only (no source name), the search
button UX (the empty bar = no button; typing = the left icon drops + the pill
rises), THE HAND-OFF AUTO-SEARCH (no dialog — the tap resolves on the
remembered/first trusted CS provider and opens the details page of the best
match; no match = the CS results stay), and the customizability
(SearchPreferences + ResultTitleStyle overlay/below + Settings → Appearance →
Search page).

## WS-4 — the airs expand/collapse + the frosted pill (d1317b01; CI 44882674397 GREEN)

The collapsed view keeps the 6-row contract; the expanded view shows the full
window (released desc + upcoming asc) with the animateItem glides; the Show
more/Show less control; the rail line STOPS at the last row's node center;
the next-air pill wears the frosted translucent background.

## WS-3 — the home feed depth (6be26034 + 2 rounds; CI 44883517686 RED→44883794013 RED→44884070751 GREEN)

- The per-kind POOLS drive the composition: the hero = 5 random items PER
  enabled kind, interleaved in a RANDOM kind order (5/10/15/20 as kinds
  stack); the rail = the rating-ranked round-robin interleave.
- The suggested field samples RANDOMLY across the pools.
- The mood chips load the BY-GENRE merged pools per kind (anime by name,
  donghua the CN query + genre, TMDB discover by the genre id — a kind
  without the genre contributes nothing).
- The surprise shuffles the whole merged pool (random old/new).
- The Settings screen: the heading spacing + the LIST format (4 bold rows, no
  descriptions) per the device order.

## WS-6 — the branding + the roles (129c3729; CI 44884682177 GREEN)

The picker rows carry the BRANDED marks (the AniList blue / the TMDB dark /
the TVmaze gold tiles + the initials), the subtitles simplified (no
GraphQL/keyless tech-speak), the metadata-only note removed; the Trackers
cards say the roles in one line (AniList = "Anime and manga", Simkl =
"Movies and TV series") with the paragraphs removed.

## The v1.1.102 debug release (the AUTOMATIC cadence)

- The branch from the green head 129c3729; the bump 10120 → **10202** /
  "1.1.102" (the mainline carries the D-430 dev values); the annotated tag.
- The run **44885266115 GREEN**; LIVE stable (not prerelease); the APK's
  sha256 **31cb26edc429a5d3d38a9c8d8c3ea142755b11129fbd6ca2b5b57e2e38262692**
  matches SHA256SUMS.txt (the verify copy: planning/verify_v1102/).
- https://github.com/Khurram00964/TEMP/releases/tag/v1.1.102

## The compile-round ledger (the round: 6 build dispatches, 4 reds — disclosed)

WS-1's dispatch rode no red (its commit landed with WS-2's); the reds: the
WS-2 batch (2: the state's missing val + the GridItemSpan qualification), the
Appearance imports, the Settings screen's scope/import rounds — each
single-cause, fixed + pushed immediately.

## QUEUED (the next round, in order)

1. **The MAL tracking implementation** (the user's explicit order — the
   client id is registered; the PKCE-plain OAuth + the search/link flow +
   the TrackSheet mapping + the >1-linked tracker picker sheet in the
   details' 3-dot menu + the "Stop tracking" rename).
2. **The startup-hang confirmation** (the update-over-old repro + the
   logcat — the hardening landed, the confirmation pending).
3. **The extension-priority system** (the most-used providers first,
   persisted + backed up — the user's future order).
4. **The TVmaze airs integration** (the airs-by-date rows for the series
   mode) + **Bangumi** (the donghua detail source).
5. The home's deep pages' kind-awareness (the catalog/see-all grids beyond
   the search-screen routing, the all-genres page's combined list).
