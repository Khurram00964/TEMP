package com.confused.anikuta.core.trackersimkl

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.trackerapi.TrackerKeys
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * ROUND 163 (STEP-2): the Simkl HTTP surface (https://api.simkl.org).
 *
 * EVERY call carries `client_id` + `app-name` + `app-version` as query params
 * and an honest User-Agent header (the API rules). Authenticated calls add the
 * Bearer token. Rate limits: 10 GET/s + 1 POST/s per token — the app's write
 * paths are single events, never loops, so no client-side limiter is built
 * (a 400 rate_limit response surfaces as a failed sync + the caller retries).
 */
class SimklApi(
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
) {

    companion object {
        private const val TAG = "Anikuta:Core:Tracker:Simkl"
        private const val BASE = "https://api.simkl.com"
        private const val APP_NAME = "ANI-KUTA"
        private const val APP_VERSION = "1.0.0"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }

    // ── low-level ──

    private fun url(path: String, params: Map<String, String> = emptyMap()): String {
        val base = "$BASE$path?client_id=${TrackerKeys.SIMKL_CLIENT_ID}&app-name=$APP_NAME&app-version=$APP_VERSION"
        val extra = params.entries.joinToString("&") { "${it.key}=${java.net.URLEncoder.encode(it.value, "UTF-8")}" }
        return if (extra.isEmpty()) base else "$base&$extra"
    }

    private fun get(url: String, accessToken: String?): String? {
        val request = Request.Builder()
            .url(url)
            .get()
            .apply { accessToken?.let { header("Authorization", "Bearer $it") } }
            .header("User-Agent", "$APP_NAME/$APP_VERSION (Android)")
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Logger.w(TAG) { "GET ${response.request.url.encodedPath} — HTTP ${response.code}: ${raw.take(200)}" }
                    return@use null
                }
                raw
            }
        }.getOrNull()
    }

    private fun post(url: String, bodyJson: String, accessToken: String): String? {
        val request = Request.Builder()
            .url(url)
            .post(bodyJson.toRequestBody(JSON_MEDIA))
            .header("Authorization", "Bearer $accessToken")
            .header("User-Agent", "$APP_NAME/$APP_VERSION (Android)")
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Logger.w(TAG) { "POST ${response.request.url.encodedPath} — HTTP ${response.code}: ${raw.take(200)}" }
                    return@use null
                }
                raw
            }
        }.getOrNull()
    }

    // ── identity ──

    /** GET /users/me — the account name for the Settings card. Null when not authed. */
    fun usersMe(accessToken: String): SimklUser? =
        get(url("/users/me"), accessToken)?.let { raw ->
            runCatching { json.decodeFromString(SimklUser.serializer(), raw) }.getOrNull()
        }

    // ── the id bridge ──

    /**
     * GET /redirect?anilist=|tmdb=|imdb= — resolves ONE external id to the
     * Simkl id + the honest type. 404 = unknown (null).
     */
    fun resolveExternalId(kind: String, id: Long): SimklIdResolution? =
        get(url("/redirect", mapOf(kind to id.toString())), null)?.let { raw ->
            runCatching { json.decodeFromString(SimklIdResolution.serializer(), raw) }.getOrNull()
        }

    // ── search (the linking flows) ──

    /** GET /search/{kind}?q= — kind: movie | tv | anime | all. */
    fun search(kind: String, query: String): SimklSearchResponse? =
        get(url("/search/$kind", mapOf("q" to query)), null)?.let { raw ->
            runCatching { json.decodeFromString(SimklSearchResponse.serializer(), raw) }.getOrNull()
        }

    /**
     * GET /{movie|tv|anime}/trending — the blank-query default (the search
     * source's grid fill; Phase E). ROUND 169: the LIVE-VERIFIED shape —
     * the ids at ids.simkl_id, the poster as a simkl.in path.
     */
    fun trending(kind: String): List<SimklMediaItem> =
        get(url("/$kind/trending"), null)?.let { raw ->
            runCatching {
                json.decodeFromString(kotlinx.serialization.serializer<List<SimklMediaItem>>(), raw)
            }.getOrNull()
        } ?: emptyList()

    /**
     * ROUND 169: the USER-TOKEN search — the /search/* endpoints REQUIRE a
     * user access token (the live 401: "user_token_required"); the client id
     * alone never worked. Null token → the honest empty (the UI shows the
     * not-linked state).
     */
    fun searchAuthed(kind: String, query: String, accessToken: String): List<SimklMediaItem> =
        get(url("/search/$kind", mapOf("q" to query)), accessToken)?.let { raw ->
            runCatching {
                json.decodeFromString(kotlinx.serialization.serializer<List<SimklMediaItem>>(), raw)
            }.getOrNull()
        } ?: emptyList()

    // ── the sync family (all require the Bearer token) ──

    /**
     * POST /sync/add-to-list — the watch status (watching/plantowatch/
     * completed/hold/dropped; movies skip watching/hold per the docs).
     */
    fun addToList(body: SimklSyncBody, accessToken: String): Boolean =
        post(
            url("/sync/add-to-list"),
            json.encodeToString(SimklSyncBody.serializer(), body),
            accessToken,
        ) != null

    /**
     * POST /sync/history — episode marks + movie watched_at stamps. Movies
     * with `watched_at` count as completed; shows/anime carry seasons.
     */
    fun addHistory(body: SimklSyncBody, accessToken: String): Boolean =
        post(
            url("/sync/history"),
            json.encodeToString(SimklSyncBody.serializer(), body),
            accessToken,
        ) != null

    /** POST /sync/history/remove — the un-mark path. */
    fun removeHistory(body: SimklSyncBody, accessToken: String): Boolean =
        post(
            url("/sync/history/remove"),
            json.encodeToString(SimklSyncBody.serializer(), body),
            accessToken,
        ) != null

    /** POST /sync/ratings — 1-10 (the app's 0-100 converts at the boundary). */
    fun addRatings(body: SimklSyncBody, accessToken: String): Boolean =
        post(
            url("/sync/ratings"),
            json.encodeToString(SimklSyncBody.serializer(), body),
            accessToken,
        ) != null

    /**
     * GET /sync/all-items/{kind}/{status} — the library reads (kind: movie |
     * tv | anime; status: watching/plantowatch/completed/hold/dropped or
     * "all" where supported).
     */
    fun allItems(kind: String, status: String, accessToken: String): List<SimklLibraryItem> =
        get(url("/sync/all-items/$kind/$status"), accessToken)?.let { raw ->
            runCatching {
                json.decodeFromString(kotlinx.serialization.serializer<List<SimklLibraryItem>>(), raw)
            }.getOrNull()
        } ?: emptyList()
}
