# Record 150 — Round 170 (2026-11-22): the details unification + the device-report fixes

> The v1.1.105 device report's orders. THE BIGGEST: the metadata-details
> screen was the WRONG approach — the ONE existing details page with the
> translated data is the way. The screen/VM/key are DELETED.

## 🔴 The details unification (the user's redirect)

- The taps build a **SYNTHETIC AniListAnime** (the id packs kind+remoteId
  negatively: `-(remoteId*8+kindIndex)`, tmdb=1/tvmaze=2/mal=3/simkl=4) and
  ride the **standard details route** — the ONE details page renders every
  source from content_details' designed data axis.
- The loadFromAniList overload: the metadata branch decodes the id, links
  the DB (main_entry + content_details), and emits the **UnifiedAnime**
  Success from the data axis (the title/cover ride the key).
- The 3-dot **"Link to other"**: the menu expands in-place into the sources
  list (AniList → MyAnimeList → Simkl → TMDB → TVmaze — the user's order);
  the AniList pick = the existing flow, the MAL/Simkl picks = the link
  sheets, the TMDB/TVmaze picks = the search hand-off pre-set to the
  source.

## 🔴 The track logic (the device orders)

- **THE ONE-TRACKER RULE**: a tracked content's track action opens THAT
  tracker's sheet directly (the tracked-tracker lookup across the three);
  the picker only for an untracked content with >1 linked.
- The **isTracked flow dispatches by the active tracker** (the MAL "Not
  tracking" bug — the flow read the AniList default).
- **Simkl joins the dispatch**: the ctor/DI + the full cluster
  (open/push/remove/delete/searchForLink) + the SimklLinkSheet + the
  picker's third row.
- The picker rows show the **username only** (the role text gone).
- The **TrackSheet's top bar leads with the tracker's favicon**.

## 🔴 The Simkl search fix

- **NO /search/all endpoint exists** (the v1.1.105 empty results = that
  404) — the three per-kind searches (movie/tv/anime) merge, deduped.
- The DTO covers BOTH shape families (the trending's ids.simkl_id +
  poster-path; the search's candidates) with every id/poster path mapped.

## ✅ The unified result card

- ONE **UnifiedResultCard** + UnifiedCardModel renders EVERY source's card
  (the three cards = the thin adapters) — the pill/title/year placements
  structurally identical everywhere:
  - the pill at the cover's top-right always;
  - the OVERLAY: the YEAR at the title's top-left + the title moved DOWN a
    bit (the gradient unchanged);
  - the BELOW: the year as the on-cover chip at the cover's bottom-left +
    the title under the poster (the AniList BELOW-space bug dies with the
    old divergent bodies).

## ✅ The compact tracker cards

- ONE CompactTrackerCard base: the real favicon leads (40dp), the name +
  the username (linked only), the status chip — NO "Connected as", NO role
  descriptions. The actions colored: the Populate in the primary container,
  the delete in the error tint.

## ✅ The search-page settings redesign

- The Details-background anatomy (the user's pointer): the proper
  sections (Result cards / Layout), the SwitchCard rows, the SettingsCard
  groups, the ONE-LINE descriptions, the background fill + the blur.

## The next session (09-I's remaining)

1. The per-source search caching (the 2-page caches, the refresh, the 12h
   auto — the design in 09-H).
2. The TMDB/TVmaze/Simkl picker icon re-sourcing.
3. The browse-section shelves' universality.

## The release

Routine A: **v1.1.106 / 10206** — the update-over works normally (the
schema unchanged).
