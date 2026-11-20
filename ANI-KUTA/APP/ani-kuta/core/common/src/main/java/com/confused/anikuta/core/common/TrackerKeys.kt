package com.confused.anikuta.core.common

/**
 * ROUND 159: THE TRACKER SERVICE KEYS — the ONE file to edit when the
 * registrations land.
 *
 * ## How to get each key (the user's instructions)
 *
 * ### 1. MyAnimeList (anime tracking — fully implemented, dormant until the id)
 * - Sign in at myanimelist.net, open https://myanimelist.net/apiconfig
 *   (Settings → API — scroll to "My Client IDs").
 * - "Create a New Client": App Name = `ANI-KUTA`, App Type = `web`,
 *   Redirect URI = `anikuta://mal-auth`, anything for the homepage.
 * - Copy the **Client Id** into [MAL_CLIENT_ID] below. (The Client Secret is
 *   optional for PKCE — leave it blank.)
 *
 * ### 2. Trakt (live-action series/movies tracking — the sync is ready)
 * - Sign in at trakt.tv, open https://trakt.tv/oauth/applications →
 *   "New Application": Name = `ANI-KUTA`, Redirect URI = `anikuta://trakt-auth`.
 * - Copy the **Client ID** into [TRAKT_CLIENT_ID] below. (PKCE needs no
 *   secret; store it only if you want.)
 *
 * ### 3. TMDB (the identity resolver for the Trakt ids — the /search fallback)
 * - Sign in at themoviedb.org → Settings → API → "Create" → Developer.
 * - Copy the **API Key (v3 auth)** into [TMDB_API_KEY] below.
 *
 * ## On "encrypting" the keys
 * Keys shipped inside an APK are ALWAYS extractable (it's the app's own
 * credential). The honest protection is: these are the user-scoped
 * registration ids (not secrets), the tokens stay in the app's private
 * storage, and the values live in this ONE reviewed file. Nothing else needs
 * or should ship a secret.
 *
 * Every field blank → that tracker stays honestly dormant ("setup pending")
 * with zero network calls; nothing crashes, nothing half-works.
 */
object TrackerKeys {

    /** MyAnimeList — the registered app's Client Id (blank = dormant).
     * ROUND 160: PROVIDED by the user (the mal-apiconfig registration). */
    const val MAL_CLIENT_ID = "3ef19e73545f5029bbb1f2bfd62fd6c5"

    /** Trakt — the registered app's Client Id (blank = dormant).
     * ROUND 160: PROVIDED (the Development app — a 16-user cap; a Production
     * upgrade is a same-page switch that lifts it, same client id). */
    const val TRAKT_CLIENT_ID = "RCANXjQblhWRIIYj0ax1ZmWH_S8e-IM0Va3Cz1A9qSY"

    /** TMDB — the API key (v3) for the identity resolution + the home/search
     * data (blank = the stored-ids-only mode). ROUND 160: PROVIDED (the
     * Personal tier — fine for this scope; the commercial tier needs the
     * 500k-user/revenue threshold and a payment conversation). */
    const val TMDB_API_KEY = "210d23e812a0b2f4ea75fe4e934ff526"

    /** TMDB — the API Read Access Token (v4 bearer; an alternative auth
     * shape — stored for the future v4 endpoints; the v3 key above is what
     * the current calls use). */
    const val TMDB_READ_ACCESS_TOKEN =
        "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiIyMTBkMjNlODEyYTBiMmY0ZWE3NWZlNGU5MzRmZjUyNiIsIm5iZiI6MTc5NTE5MjMwNi4yNjcsInN1YiI6IjZiMDA3NWYyZDA0N2RiYmRmMjhmNzJiZSIsInNjb3BlcyI6WyJhcGlfcmVhZCJdLCJ2ZXJzaW9uIjoxfQ.t5xheRXvTuL5hMVL5nLTuLZxx76q-hDfYJ_R39KazHA"
}
