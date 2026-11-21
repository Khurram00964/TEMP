package com.confused.anikuta.core.trackersimkl

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.trackerapi.TrackerKeys
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * ROUND 163 (STEP-2): Simkl OAuth AUTH V2 — the authorization-code flow with
 * MANDATORY PKCE, **S256 only** (the docs: `plain` is rejected; mobile apps
 * need NO client_secret).
 *
 * The flow (https://api.simkl.org/authentication):
 * 1. [startLogin] builds the authorize URL
 *    (`https://simkl.com/oauth2/authorize?...&code_challenge=<S256(verifier)>`)
 *    and keeps the verifier in memory until the redirect lands.
 * 2. The browser redirect hits `anikuta://simkl-auth?code=...` (the manifest
 *    intent-filter + the MainActivity dispatch).
 * 3. [exchangeCode] POSTs `https://api.simkl.com/oauth2/token` with the code +
 *    verifier → the access token (7 days) + the refresh token (180 days,
 *    sliding, non-rotating).
 * 4. [refresh] re-POSTs with the refresh token when the access token ages out.
 *
 * Token shapes (AUTH V2): `expires_in: 604800` (7 days); the refresh token is
 * non-rotating but every refresh returns a fresh access token. Scopes:
 * `media:read media:write` (omitted = read-only — we need both to sync).
 */
object SimklOAuth {

    private const val TAG = "Anikuta:Core:Tracker:Simkl"

    const val AUTHORIZE_URL = "https://simkl.com/oauth2/authorize"
    const val TOKEN_URL = "https://api.simkl.com/oauth2/token"
    const val REDIRECT_URI = "anikuta://simkl-auth"
    const val SCOPE = "media:read media:write"

    /** The in-flight PKCE verifier — set at [startLogin], consumed at [exchangeCode]. */
    @Volatile
    private var pendingVerifier: String? = null

    /** The in-flight challenge — kept for debug logging only. */
    @Volatile
    private var pendingChallenge: String? = null

    /** True when a login flow is mid-flight (the redirect can be matched). */
    val isLoginPending: Boolean get() = pendingVerifier != null

    /**
     * Builds the authorize URL + stores the fresh PKCE pair.
     * The verifier is a 64-char base64url string (>= 43 chars per RFC 7636);
     * the challenge = BASE64URL(SHA256(verifier)) with the padding stripped.
     */
    fun startLogin(): String? {
        val clientId = TrackerKeys.SIMKL_CLIENT_ID
        if (clientId.isBlank()) {
            Logger.w(TAG) { "startLogin — Simkl client id not configured (register the app at simkl.com/apps)" }
            return null
        }
        val verifier = generateVerifier()
        val challenge = s256Challenge(verifier)
        pendingVerifier = verifier
        pendingChallenge = challenge
        Logger.i(TAG) { "startLogin — PKCE pair generated (challenge=$challenge)" }
        return AUTHORIZE_URL +
            "?client_id=$clientId" +
            "&response_type=code" +
            "&redirect_uri=${urlEncode(REDIRECT_URI)}" +
            "&scope=${urlEncode(SCOPE)}" +
            "&code_challenge=$challenge" +
            "&code_challenge_method=S256"
    }

    /**
     * Exchanges the redirect's authorization code for the token pair.
     * Returns null on any failure (the caller surfaces the error state).
     */
    fun exchangeCode(client: OkHttpClient, json: Json, code: String): SimklTokens? {
        val verifier = pendingVerifier
        if (verifier == null) {
            Logger.w(TAG) { "exchangeCode — no pending PKCE verifier (a stale/foreign redirect?)" }
            return null
        }
        val clientId = TrackerKeys.SIMKL_CLIENT_ID
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
                pendingChallenge = null
                Logger.i(TAG) { "exchangeCode — tokens acquired (expires_in=${tokens.expiresInSeconds})" }
            }
        }
    }

    /**
     * Refreshes the access token with the 180-day sliding refresh token
     * (non-rotating — the same refresh token comes back).
     */
    fun refresh(client: OkHttpClient, json: Json, refreshToken: String): SimklTokens? {
        val clientId = TrackerKeys.SIMKL_CLIENT_ID
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
        pendingChallenge = null
    }

    // ── internals ──

    private fun generateVerifier(): String {
        val bytes = ByteArray(48)
        SecureRandom().nextBytes(bytes)
        return base64Url(bytes)
    }

    private fun s256Challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return base64Url(digest)
    }

    private fun base64Url(bytes: ByteArray): String =
        android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)

    private fun urlEncode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    private fun parseTokens(json: Json, raw: String): SimklTokens? = runCatching {
        val obj = json.parseToJsonElement(raw).jsonObject
        SimklTokens(
            accessToken = obj["access_token"]?.jsonPrimitive?.content ?: return null,
            refreshToken = obj["refresh_token"]?.jsonPrimitive?.content.orEmpty(),
            expiresInSeconds = obj["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 604_800L,
        )
    }.getOrNull()
}
