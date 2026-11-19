package com.confused.anikuta.core.trackertrakt

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.trackerapi.BaseTracker
import com.confused.anikuta.core.trackerapi.TrackEntry
import com.confused.anikuta.core.trackerapi.TrackKind
import com.confused.anikuta.core.trackerapi.TrackStatus
import com.confused.anikuta.core.trackerapi.TrackerSyncState
import com.confused.anikuta.core.trackerapi.TrackerType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * ROUND 157 (WS-C): the Trakt tracker — the live-action tracking target
 * (episode/season granularity via /sync/history, scrobble-ready). The
 * translation layer's first CONSUMER: the sync manager relays through the
 * generic [Tracker] contract, the id comes from a TrackerLinkResolver
 * (TMDB/IMDb ids captured by the WS-B2 bridge work).
 *
 * NOT CONFIGURED YET: the app registration is pending (the user provides the
 * client_id). Every flow gates on [TraktAuth.isConfigured] and reports an
 * honest "not configured" instead of failing silently.
 */
class TraktTracker(
    private val preferences: com.confused.anikuta.core.preferences.PreferenceStore,
) : BaseTracker(TrackerType.TRAKT, "Trakt") {

    companion object {
        private const val TAG = "Anikuta:Core:Tracker:Trakt"
        private const val KEY_ACCESS_TOKEN = "trakt_access_token"
        private const val KEY_REFRESH_TOKEN = "trakt_refresh_token"
        private const val KEY_VERIFIER = "trakt_pkce_verifier"

        /** The status mapping: the app's canonical → Trakt's list names. */
        fun statusName(status: TrackStatus): String? = when (status) {
            TrackStatus.WATCHING -> "watching"
            TrackStatus.COMPLETED -> "completed"
            TrackStatus.PAUSED -> "pause"
            TrackStatus.DROPPED -> "dropped"
            TrackStatus.PLAN_TO_WATCH -> "watchlist"
            TrackStatus.REWATCHING -> "watching"
        }
    }

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun isLoggedIn(): Boolean =
        TraktAuth.isConfigured && preferences.getString(KEY_ACCESS_TOKEN, "").isNotBlank()

    override suspend fun startLogin(): String? {
        if (!TraktAuth.isConfigured) {
            Logger.i(TAG) { "startLogin — the Trakt app registration is pending; login unavailable" }
            return null
        }
        val verifier = TraktAuth.newCodeVerifier()
        preferences.putString(KEY_VERIFIER, verifier)
        return TraktAuth.authorizeUrl(verifier)
    }

    /**
     * The PKCE token exchange: the code + the stored verifier → the tokens.
     * The redirect carries ?code= (a QUERY param — AniList's fragment twin is
     * not reusable here).
     */
    override suspend fun handleLoginCallback(code: String): Boolean {
        if (!TraktAuth.isConfigured) return false
        val verifier = preferences.getString(KEY_VERIFIER, "")
        if (verifier.isBlank()) {
            Logger.w(TAG) { "handleLoginCallback — no stored verifier; aborting" }
            return false
        }
        return try {
            val body = okhttp3.FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("client_id", TraktAuth.CLIENT_ID)
                .add("redirect_uri", TraktAuth.REDIRECT_URI)
                .add("code_verifier", verifier)
                .build()
            val request = Request.Builder()
                .url(TraktAuth.TOKEN_URL)
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                val payload = response.body?.string() ?: return false
                if (!response.isSuccessful) {
                    Logger.w(TAG) { "token exchange failed: ${response.code} ${payload.take(120)}" }
                    return false
                }
                val obj = json.parseToJsonElement(payload).jsonObject
                preferences.putString(KEY_ACCESS_TOKEN, obj["access_token"]?.jsonPrimitive?.content ?: "")
                preferences.putString(KEY_REFRESH_TOKEN, obj["refresh_token"]?.jsonPrimitive?.content ?: "")
                Logger.i(TAG) { "Trakt login succeeded" }
                true
            }
        } catch (e: Exception) {
            Logger.w(TAG) { "handleLoginCallback failed: ${e.message}" }
            false
        }
    }

    override suspend fun logout() {
        preferences.putString(KEY_ACCESS_TOKEN, "")
        preferences.putString(KEY_REFRESH_TOKEN, "")
    }

    /**
     * The sync: a series entry → /sync/history (episodes watched); a movie
     * entry → /sync/history (a movie, timesWatched plays). The JSON body
     * carries the Trakt-accepted tmdb id (the resolver supplied it as
     * trackerId). Gated on the configured client id + the login.
     */
    override suspend fun syncEntry(entry: TrackEntry): Boolean {
        if (!TraktAuth.isConfigured || !isLoggedIn()) return false
        _syncState.value = TrackerSyncState.Syncing
        try {
            val idsJson = when (entry.kind) {
                TrackKind.MOVIE ->
                    """{"movies":[{"ids":{"tmdb":${entry.trackerId}},"plays":${entry.timesWatched}}]}"""
                else ->
                    """{"shows":[{"ids":{"tmdb":${entry.trackerId}}}]}"""
            }
            val request = Request.Builder()
                .url(TraktAuth.API_BASE + "/sync/history")
                .header("trakt-api-version", "2")
                .header("trakt-api-key", TraktAuth.CLIENT_ID)
                .header("Authorization", "Bearer " + preferences.getString(KEY_ACCESS_TOKEN, ""))
                .post(idsJson.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Logger.i(TAG) { "syncEntry — ${entry.kind} tmdb=${entry.trackerId} accepted" }
                    _syncState.value = TrackerSyncState.Idle
                    return true
                }
                Logger.w(TAG) { "syncEntry failed: ${response.code}" }
                _syncState.value = TrackerSyncState.Idle
                return false
            }
        } catch (e: Exception) {
            Logger.w(TAG) { "syncEntry failed: ${e.message}" }
            _syncState.value = TrackerSyncState.Idle
            return false
        }
    }

    override suspend fun fetchEntry(trackerId: Int): TrackEntry? {
        // The remote merge lands with the app registration round.
        return null
    }

    override suspend fun search(query: String): List<TrackEntry> {
        // /search/movie,show lands with the app registration round.
        return emptyList()
    }
}
