# Record 139 — Round 160: the keys land, the same-layout home, the search sources, and the audio integrity

> The user provided all three registrations (the MAL client id, the Trakt
> client id — a Development app for now, and the TMDB API key + the v4 read
> token). The orders: wire the keys, find the keyless data sources for the
> series/movies home + the search (Jikan = the MAL data without MAL; TMDB =
> the series/movies incl. the Chinese/Korean content), the search-with
> source menu, keep the home layout IDENTICAL across the modes, the
> movie-mode download button, the corrupt-audio integrity system, the IMDb
> viability, and the stale docs. The release v1.1.99.

## 1. THE KEYS — the trackers are LIVE (D-915)

TrackerKeys.kt carries the three values (the MAL client id, the Trakt
client id — the Development tier's 16-user cap lifts with a same-page
Production switch, the same client id; the TMDB v3 key + the v4 read token
stored for the future endpoints). MalAuth/TraktAuth read TrackerKeys — the
Trackers screen's cards moved from "Setup pending" to linkable. The TMDB
tier note: the Personal tier covers this scope; the commercial conversation
only triggers past 500k users/revenue.

## 2. THE SAME-LAYOUT HOME (D-916)

The user's report: the Series & movies home must look EXACTLY like the
anime home (the hero, the suggested, the genres — everything). THE REWORK:
- **The generic HeroSlide hero**: HomeHero/HeroCard/the wash consume
  `HeroSlide(title, posterUrl, scorePct, year, genres, onClick)` — the
  anime path pre-binds the AniList navigation; the Series & movies path
  pre-binds the Extension navigation. The carousel machinery is
  data-source blind.
- **The same slots**: the trending slot renders CsShelfRail (the provider's
  first shelf), the suggested slot CsShelfRail (the second shelf), the
  genres slot CsGenreChips (the shelf titles → the category subpages) —
  the SAME section anatomy, the SAME slot order, the SAME
  moodsIndex/scroll arithmetic (mode-blind now: 5 + the airs rows).
- The old cs_home block is RETIRED (its content is distributed into the
  same-layout slots).

## 3. WS-SEARCH — the source menu (D-917)

SearchSource gained TMDB + JIKAN; the top bar's source option opens the
"pick a source" dropdown (AniList — anime / TMDB — series & movies / Jikan
— the MyAnimeList data, keyless / Extensions). The VM dispatch: the TMDB
multi search, the Jikan anime search, the blank-query defaults (the TMDB
trending + the popular TV; the Jikan top anime), the persisted restore.
The results render as `SearchUiState.ExternalSuccess` — the generic poster
grid (the tap opens the source's web page; the dedicated details screens
are the AniList/extension territory). The Bangumi (the donghua) client is
documented as the next addition — the picker's architecture accepts it.

## 4. WS-161 — the audio integrity system (D-918)

The device report: BOTH downloaded audio versions were corrupt (sub-MB
.mp4s) — the 64KB size gate only catches tiny garbage. THE SYSTEM: every
landed variant is probed with FFprobeKit (it must PARSE as media AND run
≥ 60s), and the download RETRIES up to 3 attempts (a fresh fetch each)
before the variant skips honestly. Both paths (the flat GET — extracted to
downloadFlatAudioVariantToCache — and the HLS remux) route through the same
loop. The two P0s from the previous round (the init-order crash, the
$stable stamp) were already in this build.

## 5. THE MOVIE DOWNLOAD BUTTON

A single-episode CS response rendered Play with NO download affordance
(the episode list that carries the row buttons is hidden in movie mode).
The movie hero now pairs Play with a Download button — the SAME
onDownloadEpisode flow (the CS resolve sheet in download mode included).

## 6. THE HONEST ANSWERS (the user's asks)

- **IMDb tracking: NOT VIABLE** — IMDb has no public API; scraping violates
  their terms. The user's "stop and let me know" rule applies. MAL + Trakt
  + (later) Simkl cover the tracking space.
- **Simkl**: free, PKCE, covers anime+TV+movies in ONE account — the
  documented next addition when the user wants it (the translation layer
  accepts it like MAL did).
- **Bangumi** (the donghua): keyless, the picker-ready — the next addition.
- **The TMDB tier**: the Personal tier is correct for now; the commercial
  conversation only triggers past 500k users/revenue.

## 7. CI + THE RELEASE (D-919)

**44787340701 GREEN on f9dc2404**; the Release run **44788991955 GREEN**;
**v1.1.99 / 10199 LIVE on Khurram00964/TEMP** — verified: stable + latest,
the downloaded SHA (6a05c344...) MATCHES. Sideloads over v1.1.98. NEXT: the
device round on v1.1.99 (the live trackers, the same-layout home, the audio
integrity); Simkl/Bangumi as the next additions; the manga groundwork.
