package com.confused.anikuta.core.trackermal

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.preferences.PreferenceStore
import com.confused.anikuta.core.trackerapi.BaseTracker
import com.confused.anikuta.core.trackerapi.TrackEntry
import com.confused.anikuta.core.trackerapi.TrackStatus
import com.confused.anikuta.core.trackerapi.TrackerSyncState
import com.confused.anikuta.core.trackerapi.TrackerLoginState
import com.confused.anikuta.core.trackerapi.TrackerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * ROUND 159 (WS-MAL): the MyAnimeList tracker — the SECOND anime tracking
 * service, the full implementation. The translation layer's second CONSUMER:
 * the sync manager relays through the generic [Tracker] contract, the id
 * comes from [MalLinkResolver] (ContentDetails.malId — AniList's own idMal
 * field, already flowing for every AniList-linked entry; the manual-link
 * sheet covers the rest).
 *
 * Auth: PKCE (plain challenge) with a ONE-HOUR access token — every 401
 * refreshes (the refresh token lives ~1 month). Dormant until
 * [MalAuth.CLIENT_ID] lands (the user's app registration — the instructions
 * ship in the round report).
 */
class MalTracker(
    private val preferences: PreferenceStore,
) : BaseTracker(TrackerType.MAL, "MyAnimeList") {

    companion object {
        private const val TAG = "Anikuta:Core:Tracker:Mal"
        private const val KEY_ACCESS_TOKEN = "mal_access_token"
        private const val KEY_REFRESH_TOKEN = "mal_refresh_token"
        private const val KEY_VERIFIER = "mal_pkce_verifier"
        private const val KEY_USERNAME = "mal_username"

        /** The status mapping: the app's canonical → MAL's list names. */
        fun statusName(status: TrackStatus): String = when (status) {
            TrackStatus.WATCHING -> "watching"
            TrackStatus.COMPLETED -> "completed"
            TrackStatus.PAUSED -> "on_hold"
            TrackStatus.DROPPED -> "dropped"
            TrackStatus.PLAN_TO_WATCH -> "plan_to_watch"
            TrackStatus.REWATCHING -> "watching"
        }
    }

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    /** The token reads/writes are serialized on IO (the refresh races the sync). */
    private fun accessToken(): String = preferences.getString(KEY_ACCESS_TOKEN, "")

    /**
     * Refreshes the access token. MAL's refresh grant needs the ORIGINAL
     * refresh token; the response carries BOTH fresh tokens (the old refresh
     * may be invalidated — store whatever comes back).
     */
    private suspend fun refreshTokens(): Boolean = withContext(Dispatchers.IO) {
        val refreshToken = preferences.getString(KEY_REFRESH_TOKEN, "")
        if (refreshToken.isBlank()) return@withContext false
        try {
            val body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", MalAuth.CLIENT_ID)
                .add("refresh_token", refreshToken)
                .build()
            client.newCall(Request.Builder().url(MalAuth.TOKEN_URL).post(body).build())
                .execute()
                .use { response ->
                    val payload = response.body?.string() ?: return@withContext false
                    if (!response.isSuccessful) {
                        Logger.w(TAG) { "refresh failed: ${response.code} ${payload.take(120)}" }
                        return@withContext false
                    }
                    val obj = json.parseToJsonElement(payload).jsonObject
                    preferences.putString(
                        KEY_ACCESS_TOKEN,
                        obj["access_token"]?.jsonPrimitive?.content ?: "",
                    )
                    preferences.putString(
                        KEY_REFRESH_TOKEN,
                        obj["refresh_token"]?.jsonPrimitive?.content ?: refreshToken,
                    )
                    Logger.i(TAG) { "refresh OK" }
                    true
                }
        } catch (e: Exception) {
            Logger.w(TAG) { "refresh failed: ${e.message}" }
            false
        }
    }

    /** An authenticated GET/PUT that refreshes once on a 401 and retries. */
    private suspend fun executeAuthenticated(
        build: (token: String) -> Request,
        method: String,
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        var token = accessToken()
        if (token.isBlank()) return@withContext Pair(false, "Not logged in")
        var response = client.newCall(build(token)).execute()
        if (response.code == 401) {
            response.close()
            Logger.i(TAG) { "$method — 401; refreshing" }
            if (!refreshTokens()) return@withContext Pair(false, "Token expired and refresh failed")
            token = accessToken()
            response = client.newCall(build(token)).execute()
        }
        val payload = response.body?.string() ?: ""
        val ok = response.isSuccessful
        response.close()
        Pair(ok, payload)
    }

    override suspend fun isLoggedIn(): Boolean {
        val loggedIn = MalAuth.isConfigured && accessToken().isNotBlank()
        _loginState.value = if (loggedIn) {
            TrackerLoginState.LoggedIn(username())
        } else {
            TrackerLoginState.LoggedOut
        }
        return loggedIn
    }

    override suspend fun startLogin(): String? {
        if (!MalAuth.isConfigured) {
            Logger.i(TAG) { "startLogin — the MAL app registration is pending; login unavailable" }
            return null
        }
        val verifier = MalAuth.newCodeVerifier()
        preferences.putString(KEY_VERIFIER, verifier)
        return MalAuth.authorizeUrl(verifier)
    }

    /** The PKCE exchange (plain challenge — the verifier IS the challenge). */
    override suspend fun handleLoginCallback(code: String): Boolean {
        if (!MalAuth.isConfigured) return false
        val verifier = preferences.getString(KEY_VERIFIER, "")
        if (verifier.isBlank()) {
            Logger.w(TAG) { "handleLoginCallback — no stored verifier; aborting" }
            return false
        }
        return try {
            withContext(Dispatchers.IO) {
                val body = FormBody.Builder()
                    .add("grant_type", "authorization_code")
                    .add("code", code)
                    .add("client_id", MalAuth.CLIENT_ID)
                    .add("redirect_uri", MalAuth.REDIRECT_URI)
                    .add("code_verifier", verifier)
                    .build()
                client.newCall(Request.Builder().url(MalAuth.TOKEN_URL).post(body).build())
                    .execute()
                    .use { response ->
                        val payload = response.body?.string() ?: return@withContext false
                        if (!response.isSuccessful) {
                            Logger.w(TAG) { "token exchange failed: ${response.code} ${payload.take(120)}" }
                            return@withContext false
                        }
                        val obj = json.parseToJsonElement(payload).jsonObject
                        preferences.putString(
                            KEY_ACCESS_TOKEN,
                            obj["access_token"]?.jsonPrimitive?.content ?: "",
                        )
                        preferences.putString(
                            KEY_REFRESH_TOKEN,
                            obj["refresh_token"]?.jsonPrimitive?.content ?: "",
                        )
                        true
                    }
            } && verifyIdentity()
        } catch (e: Exception) {
            Logger.w(TAG) { "handleLoginCallback failed: ${e.message}" }
            false
        }
    }

    /** GET /users/@me — verifies the session and stores the username. */
    private suspend fun verifyIdentity(): Boolean = try {
        val (ok, payload) = executeAuthenticated(
            { token ->
                Request.Builder()
                    .url(MalAuth.API_BASE + "/users/@me")
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
            },
            "verify",
        )
        if (!ok) {
            Logger.w(TAG) { "verifyIdentity failed: ${payload.take(120)}" }
            false
        } else {
            val name = json.parseToJsonElement(payload).jsonObject["name"]
                ?.jsonPrimitive?.content ?: ""
            preferences.putString(KEY_USERNAME, name)
            Logger.i(TAG) { "MAL login OK — user=$name" }
            true
        }
    } catch (e: Exception) {
        Logger.w(TAG) { "verifyIdentity failed: ${e.message}" }
        false
    }

    override suspend fun logout() {
        preferences.putString(KEY_ACCESS_TOKEN, "")
        preferences.putString(KEY_REFRESH_TOKEN, "")
        preferences.putString(KEY_USERNAME, "")
    }

    /**
     * The sync: PUT /anime/{id}/my_list_status — partial-update semantics.
     * status + num_watched_episodes always ride; the score maps 0-100 → 0-10
     * (only when the user set one — 0/blank never overwrites MAL's value).
     * REWATCHING maps to watching + is_rewatching=true.
     */
    override suspend fun syncEntry(entry: TrackEntry): Boolean {
        if (!MalAuth.isConfigured || !isLoggedIn()) {
            _syncState.value = TrackerSyncState.Failed("Not logged in")
            return false
        }
        _syncState.value = TrackerSyncState.Syncing
        val result = try {
            val form = FormBody.Builder()
                .add("status", statusName(entry.status))
                .add("num_watched_episodes", entry.progress.toString())
            if (entry.status == TrackStatus.REWATCHING) {
                form.add("is_rewatching", "true")
            }
            entry.score?.takeIf { it > 0 }?.let { raw ->
                // AniList-style 0-100 → MAL's 0-10 (the app stores 0-100
                // internally; MAL's scale is one digit per ten points).
                form.add("score", (raw / 10).coerceIn(0, 10).toString())
            }
            val formBody = form.build()
            val (ok, payload) = executeAuthenticated(
                { token ->
                    Request.Builder()
                        .url(MalAuth.API_BASE + "/anime/${entry.trackerId}/my_list_status")
                        .header("Authorization", "Bearer $token")
                        .put(formBody)
                        .build()
                },
                "sync",
            )
            if (ok) {
                Logger.i(TAG) {
                    "syncEntry — OK: malId=${entry.trackerId}, status=${entry.status}, " +
                        "progress=${entry.progress}, score=${entry.score}"
                }
                _syncState.value = TrackerSyncState.Success(System.currentTimeMillis())
                true
            } else {
                Logger.w(TAG) { "syncEntry failed: ${payload.take(140)}" }
                _syncState.value = TrackerSyncState.Failed(payload.take(120))
                false
            }
        } catch (e: Exception) {
            Logger.e(TAG) { "syncEntry failed: ${e.message}" }
            _syncState.value = TrackerSyncState.Failed(e.message ?: "Unknown error")
            false
        }
        return result
    }

    /**
     * GET /anime/{id}?fields=my_list_status — the remote merge source (the
     * TrackSheet's pull).
     */
    override suspend fun fetchEntry(trackerId: Int): TrackEntry? {
        if (!MalAuth.isConfigured || !isLoggedIn()) return null
        return try {
            val (ok, payload) = executeAuthenticated(
                { token ->
                    Request.Builder()
                        .url(MalAuth.API_BASE + "/anime/$trackerId?fields=my_list_status")
                        .header("Authorization", "Bearer $token")
                        .get()
                        .build()
                },
                "fetch",
            )
            if (!ok) {
                Logger.w(TAG) { "fetchEntry failed: ${payload.take(120)}" }
                return null
            }
            val obj = json.parseToJsonElement(payload).jsonObject
            val status = obj["my_list_status"]?.jsonObject ?: return null
            val statusName = status["status"]?.jsonPrimitive?.content ?: return null
            val progress = status["num_episodes_watched"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val score10 = status["score"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            TrackEntry(
                contentKey = "",
                trackerId = trackerId,
                status = when (statusName) {
                    "watching" -> if (status["is_rewatching"]?.jsonPrimitive?.content == "true") {
                        TrackStatus.REWATCHING
                    } else TrackStatus.WATCHING
                    "completed" -> TrackStatus.COMPLETED
                    "on_hold" -> TrackStatus.PAUSED
                    "dropped" -> TrackStatus.DROPPED
                    "plan_to_watch" -> TrackStatus.PLAN_TO_WATCH
                    else -> TrackStatus.WATCHING
                },
                score = (score10 * 10).takeIf { it > 0 },
                progress = progress,
            )
        } catch (e: Exception) {
            Logger.w(TAG) { "fetchEntry failed: ${e.message}" }
            null
        }
    }

    /**
     * GET /anime?q= — the MAL search. The interface carries [TrackEntry]
     * (no title field yet — the MAL manual-link UI lands later; the entries
     * here are the ID resolution seeds).
     */
    override suspend fun search(query: String): List<TrackEntry> {
        if (!MalAuth.isConfigured || !isLoggedIn()) return emptyList()
        return try {
            val (ok, payload) = executeAuthenticated(
                { token ->
                    Request.Builder()
                        .url(
                            MalAuth.API_BASE + "/anime?q=" +
                                android.net.Uri.encode(query) +
                                "&limit=10&fields=id,title,main_picture",
                        )
                        .header("Authorization", "Bearer $token")
                        .get()
                        .build()
                },
                "search",
            )
            if (!ok) {
                Logger.w(TAG) { "search failed: ${payload.take(120)}" }
                return emptyList()
            }
            val array = json.parseToJsonElement(payload).jsonObject["data"]?.jsonArray
                ?: return emptyList()
            array.mapNotNull { element ->
                val node = element.jsonObject["node"]?.jsonObject ?: return@mapNotNull null
                val id = node["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null
                TrackEntry(
                    contentKey = "",
                    trackerId = id,
                )
            }
        } catch (e: Exception) {
            Logger.w(TAG) { "search failed: ${e.message}" }
            emptyList()
        }
    }

    /** The account identity for the Trackers card. */
    fun username(): String = preferences.getString(KEY_USERNAME, "")
}
