# Record 149 — Round 169 (2026-11-21): the v1.1.104 device-report fixes

> The user's itemized v1.1.104 report: two crashes, the universality gaps,
> the metadata-taps redirect, the rating-pill design, the favicons, the
> settings placement. This round fixes everything fixable now and queues
> the caching system (09-H).

## 🚨 The crashes (both fixed)

1. **The details-page NPE** (`MutableStateFlow.setValue on null` at
   `DetailsViewModel.<init>`): the linked-username StateFlows were declared
   BELOW the init block assigning them — the trackers restore at
   construction → the synchronous first emission (Main.immediate) hit null.
   The states moved above the init. The round-162 lesson, generalized
   (the lesson banked).
2. **The Simkl duplicate-key crash** (`Key "simkl--1" was already used`):
   the §23 LIVE curl verification proved the shapes — the /search/*
   endpoints REQUIRE the user token (the client-id 401:
   user_token_required) and the /trending items carry `ids.simkl_id` +
   simkl.in POSTER PATHS — the old DTO parsed nothing → every row mapped
   remoteId = -1 → the duplicate LazyGrid keys. The fix: the SimklMediaItem
   DTO, searchAuthed (the token rides), the mapper builds the poster URL +
   skips the id-less rows.

## ⚠️ The new trap: Kotlin NESTED comments

A KDoc's literal `/search/*` text opened a NESTED block comment → the
file's remainder swallowed → the bogus "Missing '}'"/"Unclosed comment"
cascade. De-slashed; the lesson banked.

## ✅ The universality (the user's consistency order)

- The scroll blur covers DataSourceSuccess (the effect was AniList-only —
  the when missed the branch).
- The title placement UNIVERSAL: the AniList + extension cards gained the
  OVERLAY/BELOW branches.
- **THE RATING PILL** (the user's design): the tiny pill at every card's
  cover top-right (the AniList averageScore as 0-10, the data-source
  ratings); the rating MOVED out of the meta line.
- The grid density 1-5 (+ Adaptive) on ALL three grids.
- The Appearance row: its OWN "Search Page" section + the standard
  MoreListRow anatomy with the Search icon.
- The search-bar swaps ANIMATE (the icon fades+scales out on typing; the
  X + the pill fade+scale/expand in).
- The REAL favicons: AniList/MAL/Simkl (the official PNGs; the ICO
  PNG-layers extracted with a minimal parser) — the TMDB/TVmaze tiles
  continue until their assets are sourced.

## ✅ The metadata details pages (the user's redirect)

- The taps open the details page OF the tapped source
  (MetadataDetailsKey/Screen/VM) — NOT the CS hand-off switch.
- The DB LINKING via content_details' DESIGNED data axis
  (data_source_type/data_source_ref_id + the meta fields; main_entry
  display_source = "metadata") — no schema change needed.
- WATCH → the CS route (the staged-title prefill); TRACK → the serving
  tracker (MAL for anime, Simkl otherwise — the fire-and-forget seed).

## The next cycle (09-H)

1. The per-source search caching (the 2-page caches, the refresh, the 12h
   auto — riding the browse_cache expiry pattern).
2. The details enrichment (the source detail endpoints).
3. The Trackers cards' polish + the Simkl populate question.
4. The TMDB/TVmaze logo sourcing.
