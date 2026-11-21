package com.confused.anikuta.core.trackermal

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.trackerapi.TrackerKeys
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.SecureRandom

/**
 * ROUND 168 (the tracker redo, Phase C): MyAnimeList OAuth2 — the
 * authorization-code flow with PKCE, **PLAIN only** (MAL's docs: S256 is not
 * supported; the challenge must equal the verifier verbatim — the 05-T-2
 * spec's "PKCE-plain", NOT the S256 pattern Simkl uses).
 *
 * The flow (https://myanimelist.net/apiconfig/references/authorization):
 * 1. [startLogin] builds the authorize URL
 *    (`https://myanimelist.net/v1/oauth2/authorize?...&code_challenge=<verifier>`)
 *    and keeps the verifier in memory until the redirect lands. The verifier
 *    is a 64-char url-safe random string (MAL wants 43-128 chars).
 * 2. The browser redirect hits `anikuta://mal-auth?code=...` (the manifest
 *    intent-filter + the MainActivity dispatch — the client id's registered
 *    redirect, per 08-THE-KEYS.md).
 * 3. [exchangeCode] POSTs `https://myanimelist.net/v1/oauth2/token` with the
 *    code + verifier → the access token (~31 days) + the refresh token.
 * 4. [refresh] re-POSTs with the refresh token when the access token ages out.
 */
object MalOAuth {

    private const val TAG = "Anikuta:Core:Tracker:Mal"

    const val AUTHORIZE_URL = "https://myanimelist.net/v1/oauth2/authorize"
    const val TOKEN_URL = "https://myanimelist.net/v1/oauth2/token"
    const val REDIRECT_URI = "anikuta://mal-auth"

    /** The in-flight PKCE verifier — set at [startLogin], consumed at [exchangeCode]. */
    @Volatile
    private var pendingVerifier: String? = null

    /** True when a login flow is mid-flight (the redirect can be matched). */
    val isLoginPending: Boolean get() = pendingVerifier != null

    /**
     * Builds the authorize URL + stores the fresh PKCE pair.
     * Plain method: the challenge IS the verifier (MAL compares them verbatim).
     */
    fun startLogin(): String? {
        val clientId = TrackerKeys.MAL_CLIENT_ID
        if (clientId.isBlank()) {
            Logger.w(TAG) { "startLogin — MAL client id not configured" }
            return null
        }
        val verifier = generateVerifier()
        pendingVerifier = verifier
        Logger.i(TAG) { "startLogin — plain-PKCE verifier generated (${verifier.length} chars)" }
        return AUTHORIZE_URL +
            "?client_id=$clientId" +
            "&response_type=code" +
            "&redirect_uri=${urlEncode(REDIRECT_URI)}" +
            // PKCE is REQUIRED on MAL; plain = the challenge is the verifier itself.
            "&code_challenge=$verifier" +
            "&code_challenge_method=plain"
    }

    /**
     * Exchanges the redirect's authorization code for the token pair.
     * Returns null on any failure (the caller surfaces the error state).
     */
    fun exchangeCode(client: OkHttpClient, json: Json, code: String): MalTokens? {
        val verifier = pendingVerifier
        if (verifier == null) {
            Logger.w(TAG) { "exchangeCode — no pending PKCE verifier (a stale/foreign redirect?)" }
            return null
        }
        val clientId = TrackerKeys.MAL_CLIENT_ID
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("code_verifier", verifier)
            .add("redirect_uri", REDIRECT_URI)
            .build()
        val request = Request.Builder()
            .url(TOKEN_URL)
            .post(body)
            .header("User-Agent", "ANI-KUTA/1.0.0 (Android)")
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Logger.w(TAG) { "exchangeCode — HTTP ${response.code}: ${raw.take(200)}" }
                    return@use null
                }
                parseTokens(json, raw)
            }
        }.getOrNull().also { tokens ->
            if (tokens != null) {
                pendingVerifier = null
                Logger.i(TAG) { "exchangeCode — tokens acquired (expires_in=${tokens.expires_in})" }
            }
        }
    }

    /**
     * Refreshes the access token with the refresh token (the same-shape
     * response; MAL rotates nothing — the pair just renews).
     */
    fun refresh(client: OkHttpClient, json: Json, refreshToken: String): MalTokens? {
        val clientId = TrackerKeys.MAL_CLIENT_ID
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .build()
        val request = Request.Builder()
            .url(TOKEN_URL)
            .post(body)
            .header("User-Agent", "ANI-KUTA/1.0.0 (Android)")
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Logger.w(TAG) { "refresh — HTTP ${response.code}: ${raw.take(200)}" }
                    return@use null
                }
                parseTokens(json, raw)
            }
        }.getOrNull()
    }

    /** Aborts a pending login (the user backed out). */
    fun cancelLogin() {
        pendingVerifier = null
    }

    // ── internals ──

    /**
     * The 64-char verifier: 48 random bytes → base64url (64 chars, >= the
     * RFC's 43 floor; MAL accepts up to 128).
     */
    private fun generateVerifier(): String {
        val bytes = ByteArray(48)
        SecureRandom().nextBytes(bytes)
        return android.util.Base64.encodeToString(
            bytes,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP,
        )
    }

    private fun urlEncode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    private fun parseTokens(json: Json, raw: String): MalTokens? = runCatching {
        val obj = json.parseToJsonElement(raw).jsonObject
        MalTokens(
            access_token = obj["access_token"]?.jsonPrimitive?.content ?: return null,
            refresh_token = obj["refresh_token"]?.jsonPrimitive?.content.orEmpty(),
            expires_in = obj["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 2_629_800L,
        )
    }.getOrNull()
}
