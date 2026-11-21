# Record 148 — Round 168 (2026-11-21): the tracker + search redo

> The user's order: the boot hardening FIRST, then the trackers (the Simkl
> + the FULL MAL), then the search page (the multi-source search, the
> picker, the per-source details, the Appearance customization). The item-0
> migration re-land SKIPPED (the clean-install decision, D-937).

## Phase A — the boot hardening (PM-6) ✅ GREEN

- `DatabaseDriverFactory`: the D-198 block's `onCreate(db)` FULL-schema exec
  (every CREATE statement re-parsed on EVERY open) gated behind the
  `hasColumn(content)` probe — legacy installs still migrate, healthy opens
  skip the waste.
- `AnikutaApp`: the FIRST DB open forced on `Dispatchers.IO` in
  `Application.onCreate` (the driver + the migration run off the main
  thread before any UI can resolve the DB — the black-screen ANR executor
  is gone).
- CI 44921008091 GREEN.

## Phase B — the DB v6 + the Simkl port ✅ GREEN

- `4.sqm` (the watch_progress denorm + the season axis) + `5.sqm` (the
  track tables' FK-free rebuild + times_watched + the UNIQUE index) →
  schema v6, with the 7 lock tests, the .sq updates, DatabaseMaintenance.
- `core/tracker-simkl` ported VERBATIM from `release/1.1.103` (the
  device-proven shape, the login-state restore included) + the wiring (the
  composition root's tracker list, the manifest's simkl-auth filter, the
  MainActivity handler, the Trackers card).
- The recovery-checkout trap: the recovery AnikutaApp bound the DEFERRED
  phases' symbols (tmdb/tvmaze/ExtensionUsageTracker/priority prefs) —
  stripped. The failure summary required the untruncated logs ZIP (the
  streamed log truncates at ~10MB — the lesson banked).

## Phase C — the FULL MAL module ✅ GREEN

- `core/tracker-mal`: the OAuth **plain**-PKCE (MAL's docs: plain-only, the
  challenge = the verifier — the 05-T-2 spec's garbled phrase resolved by
  the official docs, D-938), the token exchange + the silent refresh,
  MalApi (the X-MAL-CLIENT-ID public reads + the Bearer user ops),
  MalTracker (the login restore, the fire-and-forget sync, deleteEntry),
  the module wiring, the mal-auth intent filter + the MainActivity
  dispatch, the Trackers card + the unlink confirm.

## Phase D — the >1-linked picker + the dispatch ✅

- The 3-dot entry routes `openTrackerPickerOrSheet`: both linked →
  `TrackerPickerSheet` (the rows with the linked usernames); else the
  direct path. The TrackSheet drives the ACTIVE tracker (the callbacks
  dispatch — the AniList paths verbatim; the MAL paths in
  `DetailsViewModelMultiTracker.kt`). The `MalLinkSheet` (the search → the
  pick → the mapping stored, seeded by the content title). The TrackSheet's
  `trackerName` param (the strings swap, the layout frozen).

## Phase E — the 5-source search ✅

- The TMDB/TVmaze modules + the shared-list search machinery ported (the
  ONE shared base: the collapse branch + the pagination + the one-line
  titles + the recents + the prefill). The priority references STRIPPED.
- The NEW MAL + Simkl DataSourceKinds: the tracker-backed sources (the
  app-auth searches work UNLINKED), the defaults (the MAL v2 popularity
  ranking / the Simkl TV trending — the new `MalApi.topAnime` +
  `SimklApi.trending` + `SimklTracker.searchItems`), the mappers, the
  5-row picker, the AnimeSearchKey prefill wiring, the when-exhaustiveness
  fixes, the tracker-api classpath fix.

## Phase F — the per-source details ✅ (delivered by the hand-off)

- The tap on ANY metadata result → the CS auto-search (the 04-S-3
  contract) → the best match's DETAILS PAGE opens. No parallel
  metadata-details screen (the design language: one system); the
  metadata-native surface = the explicit follow-up if the device round
  asks.

## Phase G — the Appearance search page ✅

- SearchPreferences + the title style (ported) + the NEW settings (the
  rating badge, the details line, the grid density adaptive/2/3/4, the
  recents toggle) — all reactive.
- SearchPageSettingsScreen ported + the new sections; the SearchScreen
  consumes all four; the SearchPageSettingsKey nav + the Appearance row
  wired (the approved placement).

## The known deltas

- The picker's REAL-LOGO assets deferred one cycle (the quick CDN grabs
  returned stubs — the branded tiles ship; proper sourcing next).
- Jikan not built (the MAL v2 search covers it — the user's "if needed").
- The PM-6 repro protocol stands (the logcat on the next update-over).

## The release

Routine A: **v1.1.104 / versionCode 10204** (the automatic cadence) — the
user CLEAN-INSTALLS (delete v1.1.103 first, per the user's decision).
