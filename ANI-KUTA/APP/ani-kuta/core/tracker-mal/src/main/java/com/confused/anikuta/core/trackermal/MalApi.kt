package com.confused.anikuta.core.trackermal

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.trackerapi.TrackerKeys
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * ROUND 168 (the tracker redo, Phase C): the MAL HTTP surface
 * (https://api.myanimelist.net/v2).
 *
 * Two auth modes:
 * - PUBLIC reads (search, the anime node) ride the `X-MAL-CLIENT-ID` header
 *   (no user token; MAL's documented app-auth).
 * - USER ops (my_list_status reads/writes, @me) ride the Bearer token.
 *
 * Rate limits: MAL has no published numeric limit — the app's writes are
 * single events (the relay path), never loops. A 429/403 surfaces as a
 * failed sync (the caller's TrackerSyncState.Failed), never a crash.
 */
class MalApi(
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
) {

    companion object {
        private const val TAG = "Anikuta:Core:Tracker:Mal"
        private const val BASE = "https://api.myanimelist.net/v2"
        private const val APP_VERSION = "1.0.0"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }

    // ── low-level ──

    private fun get(path: String, query: String, accessToken: String?): String? {
        val request = Request.Builder()
            .url("$BASE$path?$query")
            .get()
            .header("X-MAL-CLIENT-ID", TrackerKeys.MAL_CLIENT_ID)
            .apply { accessToken?.let { header("Authorization", "Bearer $it") } }
            .header("User-Agent", "ANI-KUTA/$APP_VERSION (Android)")
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Logger.w(TAG) { "GET $path — HTTP ${response.code}: ${raw.take(200)}" }
                    return@use null
                }
                raw
            }
        }.getOrNull()
    }

    private fun put(path: String, bodyJson: String, accessToken: String): String? {
        val request = Request.Builder()
            .url("$BASE$path")
            .put(bodyJson.toRequestBody(JSON_MEDIA))
            .header("Authorization", "Bearer $accessToken")
            .header("User-Agent", "ANI-KUTA/$APP_VERSION (Android)")
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Logger.w(TAG) { "PUT $path — HTTP ${response.code}: ${raw.take(200)}" }
                    return@use null
                }
                raw
            }
        }.getOrNull()
    }

    private fun delete(path: String, accessToken: String): Boolean {
        val request = Request.Builder()
            .url("$BASE$path")
            .delete()
            .header("Authorization", "Bearer $accessToken")
            .header("User-Agent", "ANI-KUTA/$APP_VERSION (Android)")
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Logger.w(TAG) { "DELETE $path — HTTP ${response.code}" }
                    return@use false
                }
                true
            }
        }.getOrDefault(false)
    }

    // ── identity ──

    /** GET /users/@me — the account name for the Settings card. */
    fun usersMe(accessToken: String): MalUser? =
        get("/users/@me", "", accessToken)?.let { raw ->
            runCatching { json.decodeFromString(MalUser.serializer(), raw) }.getOrNull()
        }

    // ── search (the linking flows + the future search source) ──

    /**
     * GET /anime?q=…&fields=… — the app-auth search (no user token). Returns
     * the node rows (id + title + picture + episodes + mean).
     */
    // ROUND 172: the offset joins (the v2 API's pagination — the search
    // page's load-more at page 2+).
    fun searchAnime(query: String, limit: Int = 10, offset: Int = 0): List<MalAnime> {
        val fields = "id,title,main_picture,num_episodes,mean,start_season"
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val offsetPart = if (offset > 0) "&offset=$offset" else ""
        val raw = get("/anime", "q=$q&limit=$limit$offsetPart&fields=$fields", null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(MalSearchResponse.serializer(), raw).data.map { it.node }
        }.getOrDefault(emptyList())
    }

    /**
     * GET /anime/ranking?ranking_type=bypopularity — the blank-query default
     * (the search source's grid fill). App-auth (no user token).
     */
    // ROUND 172: the offset joins (the ranking's load-more).
    fun topAnime(limit: Int = 20, offset: Int = 0): List<MalAnime> {
        val fields = "id,title,main_picture,num_episodes,mean,start_season"
        val offsetPart = if (offset > 0) "&offset=$offset" else ""
        val raw = get("/anime/ranking", "ranking_type=bypopularity&limit=$limit$offsetPart&fields=$fields", null)
            ?: return emptyList()
        return runCatching {
            json.decodeFromString(MalRankingResponse.serializer(), raw).data.map { it.node }
        }.getOrDefault(emptyList())
    }

    // ── the list-status family (all user-token) ──

    /**
     * GET /anime/{id}?fields=my_list_status — the remote state read (the
     * merge path). Null when the node doesn't exist or the read fails.
     */
    fun animeWithListStatus(id: Int, accessToken: String): MalAnime? {
        val fields = "id,title,num_episodes,my_list_status{status,score,num_episodes_watched,num_times_rewatched,is_rewatching,start_date,finish_date,updated_at}"
        return get("/anime/$id", "fields=$fields", accessToken)?.let { raw ->
            runCatching { json.decodeFromString(MalAnime.serializer(), raw) }.getOrNull()
        }
    }

    /**
     * PUT /anime/{id}/my_list_status — the sync write: status + progress +
     * score (0-10) + the dates. Returns the updated status (null = failed).
     */
    fun updateListStatus(
        id: Int,
        status: String,
        episodesWatched: Int,
        score: Int?,
        accessToken: String,
    ): MalMyListStatus? {
        val body = buildJsonObject {
            put("status", status)
            put("num_watched_episodes", episodesWatched)
            if (score != null && score > 0) put("score", score)
        }
        val raw = put("/anime/$id/my_list_status", json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), body), accessToken)
            ?: return null
        return runCatching { json.decodeFromString(MalMyListStatus.serializer(), raw) }.getOrNull()
    }

    /** DELETE /anime/{id}/my_list_status — the untrack path. */
    fun deleteListStatus(id: Int, accessToken: String): Boolean =
        delete("/anime/$id/my_list_status", accessToken)
}
