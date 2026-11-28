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

    /**
     * ROUND 173 (F3.6/F5): the STRICT get — throws SimklRequestException
     * carrying the HTTP code on failure (the surfacing base: the callers'
     * runCatching → the UI's error state shows WHY, not a silent empty).
     */
    private fun getStrict(url: String, accessToken: String?): String {
        val request = Request.Builder()
            .url(url)
            .get()
            .apply { accessToken?.let { header("Authorization", "Bearer $it") } }
            .header("User-Agent", "$APP_NAME/$APP_VERSION (Android)")
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Logger.w(TAG) { "GET ${response.request.url.encodedPath} — HTTP ${response.code}: ${raw.take(200)}" }
                throw SimklRequestException(
                    when (response.code) {
                        401 -> "Simkl rejected the request (401) — re-link Simkl in Settings → Trackers"
                        404 -> "Simkl has no such endpoint/entry (404)"
                        else -> "Simkl HTTP ${response.code}"
                    },
                )
            }
            return raw
        }
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
    /**
     * ROUND 173 (F3.6): the token param JOINS — Simkl requires the user
     * token on EVERY request (the live 401 "user_token_required"); the
     * v1.1.108 shape attached NONE so the live trending always returned
     * empty (the device saw the Cycle-G CACHE). Strict: the failures THROW.
     */
    fun trending(kind: String, accessToken: String): List<SimklMediaItem> {
        val raw = getStrict(url("/$kind/trending"), accessToken)
        return runCatching {
            json.decodeFromString(kotlinx.serialization.serializer<List<SimklMediaItem>>(), raw)
        }.getOrElse { throw SimklRequestException("Simkl trending parse failed: ${it.message}") }
    }

    /**
     * ROUND 173 (F4): the NATIVE-ID probe — GET /{kind}/{id}. 200/302 =
     * the id exists in THAT category (OkHttp follows the canonical
     * redirect), the non-2xx = not in it. Lets the sync/fetch treat the
     * link-sheet picks' SIMKL ids directly (the anilist/tmdb ladder never
     * resolves them).
     */
    fun lookupId(kind: String, id: Int, accessToken: String): Boolean =
        runCatching {
            getStrict(url("/$kind/$id"), accessToken)
            true
        }.getOrDefault(false)

    /**
     * ROUND 170: the USER-TOKEN search — the search endpoints REQUIRE a user
     * access token (the live 401: "user_token_required"), and ONLY
     * /search/movie|tv|anime exist (NO /search/all — the v1.1.105 device
     * report's empty results: the 'all' kind 404'd). The per-kind parse
     * accepts BOTH shapes (the bare array OR the wrapped object).
     */
    fun searchKind(kind: String, query: String, accessToken: String): List<SimklMediaItem> =
        parseMediaList(getStrict(url("/search/$kind", mapOf("q" to query)), accessToken))

    /** The lenient parse: the bare array first, then the wrapped object. */
    private fun parseMediaList(raw: String): List<SimklMediaItem> {
        runCatching {
            return json.decodeFromString(kotlinx.serialization.serializer<List<SimklMediaItem>>(), raw)
        }
        return runCatching {
            val wrapped = json.decodeFromString(SimklSearchResponse.serializer(), raw)
            val toMedia: (SimklSearchItem) -> SimklMediaItem? = { item ->
                val id = item.simkl_id?.toLong() ?: item.ids?.simkl?.toLong()
                id?.let {
                    SimklMediaItem(
                        title = item.title,
                        poster_url = item.poster_url,
                        simkl_id = it,
                    )
                }
            }
            (wrapped.movie + wrapped.tv + wrapped.anime).mapNotNull(toMedia)
        }.getOrDefault(emptyList())
    }

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
     *
     * ROUND 182 (the populate device report — the "library is empty" toast
     * with a POPULATED Simkl account): the shape is TOLERANT-parsed — Simkl
     * returns the entries as a BARE ARRAY, or wrapped in an {"items": [...]}
     * object, or as a per-type keyed object ({"anime": [...], ...}), and
     * each entry may be FLAT (the ids/title at the top) or wrapped under a
     * movie/show/anime key. The strict single-shape parse silently returned
     * empty on the real device.
     */
    fun allItems(kind: String, status: String, accessToken: String): List<SimklLibraryItem> {
        val raw = get(url("/sync/all-items/$kind/$status"), accessToken) ?: return emptyList()
        return runCatching {
            val root = json.parseToJsonElement(raw)
            val array: kotlinx.serialization.json.JsonArray = when (val obj = root as? kotlinx.serialization.json.JsonObject) {
                null -> root as kotlinx.serialization.json.JsonArray
                else -> (obj["items"] ?: obj["entries"])?.let {
                    kotlinx.serialization.json.jsonArray.takeIf { true }?.let { _ -> it as? kotlinx.serialization.json.JsonArray }
                } ?: buildList {
                    for (key in listOf("anime", "movies", "movie", "tv", "shows", "show", "entries")) {
                        (obj[key] as? kotlinx.serialization.json.JsonArray)?.let { addAll(it) }
                    }
                }.let { kotlinx.serialization.json.JsonArray(it) }
            }
            array.mapNotNull { element ->
                val obj = element as? kotlinx.serialization.json.JsonObject
                val inner = obj?.entries?.firstNotNullOfOrNull { (key, value) ->
                    if (key in setOf("movie", "show", "anime") &&
                        value is kotlinx.serialization.json.JsonObject
                    ) value else null
                }
                if (inner != null) {
                    json.decodeFromJsonElement(SimklLibraryItem.serializer(), inner)
                } else {
                    json.decodeFromJsonElement(SimklLibraryItem.serializer(), element)
                }
            }
        }.getOrDefault(emptyList())
    }
}

/**
 * ROUND 173 (F3.6/F5): the Simkl request failure with the WHY — the
 * search/sync flows rethrow this so the UI surfaces the real reason
 * (the v1.1.108 bug: every failure collapsed to a silent empty list).
 */
class SimklRequestException(message: String) : java.io.IOException(message)
