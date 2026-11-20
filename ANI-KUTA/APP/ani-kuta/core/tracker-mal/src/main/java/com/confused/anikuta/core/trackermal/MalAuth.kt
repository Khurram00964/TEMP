package com.confused.anikuta.core.trackermal

import java.security.SecureRandom

/**
 * ROUND 159 (WS-MAL): the MyAnimeList OAuth2 (authorization code + PKCE)
 * constants and helpers.
 *
 * THE APP REGISTRATION (the user's one-time setup — the same shape as
 * Trakt's): create an app at myanimelist.net/apiconfig (Settings → API —
 * "Create a new client"), set the redirect to `anikuta://mal-auth`, and put
 * the generated Client Id into [CLIENT_ID]. Until then the tracker is
 * honestly "not configured".
 *
 * MAL's PKCE quirks (vs Trakt's):
 * - ONLY `code_challenge_method=plain` is supported (the challenge IS the
 *   verifier, no SHA-256).
 * - The access token lives ONE HOUR; the refresh token ~1 month. Every 401
 *   must refresh (MalTracker.refreshTokens) — AniList's year-long token needs
 *   none of this.
 */
object MalAuth {
    /** The registered app's client id — TrackerKeys.MAL_CLIENT_ID (see that
     * file's instructions). Blank = dormant. */
    val CLIENT_ID: String get() = com.confused.anikuta.core.common.TrackerKeys.MAL_CLIENT_ID

    const val REDIRECT_URI = "anikuta://mal-auth"
    const val AUTHORIZE_URL = "https://myanimelist.net/v1/oauth2/authorize"
    const val TOKEN_URL = "https://myanimelist.net/v1/oauth2/token"
    const val API_BASE = "https://api.myanimelist.net/v2"

    val isConfigured: Boolean get() = CLIENT_ID.isNotBlank()

    /** The redirect's host segment — the MainActivity intent-filter dispatch key. */
    const val REDIRECT_HOST = "mal-auth"

    fun newCodeVerifier(): String {
        // plain method: the verifier itself is the challenge — 64 url-safe chars.
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        val sb = StringBuilder(64)
        val rnd = SecureRandom()
        repeat(64) { sb.append(chars[rnd.nextInt(chars.length)]) }
        return sb.toString()
    }

    fun authorizeUrl(verifier: String): String =
        "$AUTHORIZE_URL?response_type=code" +
            "&client_id=$CLIENT_ID" +
            "&code_challenge=$verifier" +
            "&code_challenge_method=plain" +
            "&redirect_uri=${android.net.Uri.encode(REDIRECT_URI)}"
}
