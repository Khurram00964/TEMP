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

    /** MyAnimeList — the registered app's Client Id (blank = dormant). */
    const val MAL_CLIENT_ID = ""

    /** Trakt — the registered app's Client Id (blank = dormant). */
    const val TRAKT_CLIENT_ID = ""

    /** TMDB — the API key (v3) for the identity resolution (blank = the
     * stored-ids-only mode; the search fallback stays off). */
    const val TMDB_API_KEY = ""
}
