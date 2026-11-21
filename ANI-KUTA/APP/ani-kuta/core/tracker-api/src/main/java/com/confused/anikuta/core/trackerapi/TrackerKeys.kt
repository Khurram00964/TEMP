package com.confused.anikuta.core.trackerapi

/**
 * The tracker/metadata registrations (the user's own apps; the values also
 * recorded in ANI-KUTA/IMPROVEMENTS-TO-DO.md §1). Client ids are public by
 * design (OAuth PKCE carries no secret) — the SECRETS never live here.
 *
 * ROUND 163 (STEP-2): re-landed (the reverted round-160 shape) + Simkl added.
 * The Simkl client id is EMPTY until the user registers the app at
 * simkl.com/apps — [SimklTracker] reports "not configured" honestly while
 * empty (the reverted Trakt skeleton's precedent), so nothing half-links.
 *
 * Per-tracker roles (the user's round-163 order):
 * - ANIME tracking = AniList (primary) + MAL (the redo pending; the client id
 *   below is its registration).
 * - MOVIES + TV SERIES tracking = Simkl (one account covers all three kinds;
 *   it accepts anilist/mal/tmdb ids on writes — the bridge to the anime side).
 */
object TrackerKeys {

    /** MyAnimeList client id (registered at myanimelist.net/apiconfig; redirect `anikuta://mal-auth`). */
    const val MAL_CLIENT_ID = "3ef19e73545f5029bbb1f2bfd62fd6c5"

    /** Trakt client id (Development tier until the same-page Production switch; redirect `anikuta://trakt-auth`). */
    const val TRAKT_CLIENT_ID = "RCANXjQblhWRIIYj0ax1ZmWH_S8e-IM0Va3Cz1A9qSY"

    /** TMDB v3 API key (query-param auth) — the movies/series data source + metadata. */
    const val TMDB_API_KEY = "210d23e812a0b2f4ea75fe4e934ff526"

    /** TMDB v4 read token (the bearer for future v4 endpoints; NOT an account token). */
    const val TMDB_V4_READ_TOKEN =
        "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiIyMTBkMjNlODEyYTBiMmY0ZWE3NWZlNGU5MzRmZjUyNiIsIm5iZiI6MTc5NTE5MjMwNi4yNjcsInN1YiI6IjZiMDA3NWYyZDA0N2RiYmRmMjhmNzJiZSIsInNjb3BlcyI6WyJhcGlfcmVhZCJdLCJ2ZXJzaW9uIjoxfQ.t5xheRXvTuL5hMVL5nLTuLZxx76q-hDfYJ_R39KazHA"

    /**
     * Simkl client id — the user's registration (2026-11-21, redirect
     * anikuta://simkl-auth). LINKED-LIVE: the Trackers card's link button is
     * active and the OAuth flow is wired.
     */
    const val SIMKL_CLIENT_ID = "bad5a61d7eb08f8bd0059650b1e66bf0135542014f900100bbb8069c8cd604ba"
}
