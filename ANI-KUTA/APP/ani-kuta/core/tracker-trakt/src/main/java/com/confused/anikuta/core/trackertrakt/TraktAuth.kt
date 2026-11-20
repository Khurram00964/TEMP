package com.confused.anikuta.core.trackertrakt

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * ROUND 157 (WS-C): the Trakt OAuth2 (authorization code + PKCE) constants
 * and helpers.
 *
 * THE APP REGISTRATION IS PENDING (the user provides the client_id — a free
 * self-serve registration at trakt.tv/oauth/applications with the redirect
 * `anikuta://trakt-auth`). Until [CLIENT_ID] is non-blank, the tracker
 * reports "not configured" honestly: no login URL, no syncs.
 *
 * PKCE: Trakt requires S256 — the verifier (43-128 chars) is sent ONLY in the
 * token exchange; the SHA-256 challenge rides the authorize URL.
 */
object TraktAuth {
    /** The registered app's client id — TrackerKeys.TRAKT_CLIENT_ID (see
     * that file's instructions). Blank = dormant. */
    val CLIENT_ID: String get() = com.confused.anikuta.core.common.TrackerKeys.TRAKT_CLIENT_ID

    const val REDIRECT_URI = "anikuta://trakt-auth"
    const val AUTHORIZE_URL = "https://trakt.tv/oauth/authorize"
    const val TOKEN_URL = "https://api.trakt.tv/oauth/token"
    const val API_BASE = "https://api.trakt.tv"

    val isConfigured: Boolean get() = CLIENT_ID.isNotBlank()

    /** The redirect's host segment — the MainActivity intent-filter dispatch key. */
    const val REDIRECT_HOST = "trakt-auth"

    fun newCodeVerifier(): String {
        val bytes = ByteArray(48)
        SecureRandom().nextBytes(bytes)
        return base64Url(bytes)
    }

    fun codeChallenge(verifier: String): String =
        base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))

    fun authorizeUrl(verifier: String): String =
        "$AUTHORIZE_URL?response_type=code" +
            "&client_id=$CLIENT_ID" +
            "&redirect_uri=${android.net.Uri.encode(REDIRECT_URI)}" +
            "&code_challenge=${codeChallenge(verifier)}" +
            "&code_challenge_method=S256"

    private fun base64Url(bytes: ByteArray): String =
        android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
            .trimEnd('=')
}
