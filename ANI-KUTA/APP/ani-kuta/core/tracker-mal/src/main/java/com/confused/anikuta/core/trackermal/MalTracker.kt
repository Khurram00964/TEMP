package com.confused.anikuta.core.trackermal

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.trackerapi.BaseTracker
import com.confused.anikuta.core.trackerapi.TrackEntry
import com.confused.anikuta.core.trackerapi.TrackStatus
import com.confused.anikuta.core.trackerapi.TrackerKeys
import com.confused.anikuta.core.trackerapi.TrackerLoginState
import com.confused.anikuta.core.trackerapi.TrackerSyncState
import com.confused.anikuta.core.trackerapi.TrackerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * ROUND 168 (the tracker redo, Phase C): the MyAnimeList tracker — the ANIME
 * tracking backend per the user's split (anime = AniList + MAL; movies+TV =
 * Simkl). The client id is registered (08-THE-KEYS.md, redirect
 * `anikuta://mal-auth`).
 *
 * The status/progress mapping (api.myanimelist.net/v2):
 * - statuses: watching / completed / on_hold / dropped / plan_to_watch
 *   (rewatching rides `is_rewatching` + num_times_rewatched — the app maps
 *   REWATCHING → watching, the count survives through MAL's own state).
 * - progress: num_episodes_watched (0..num_episodes).
 * - score: 0-10 (the app's 0-100 → /10 at the boundary; 0 = unrated).
 *
 * The fire-and-forget rule (05-T-5): a failed sync NEVER crashes — every
 * path surfaces through TrackerSyncState and returns false.
 */
class MalTracker(
    private val api: MalApi,
    private val preferences: MalPreferences,
    private val client: OkHttpClient,
) : BaseTracker(TrackerType.MAL, "MyAnimeList") {

    companion object {
        private const val TAG = "Anikuta:Core:Tracker:Mal"
        private const val REFRESH_MARGIN_MS = 60_000L
    }

    val isConfigured: Boolean get() = TrackerKeys.MAL_CLIENT_ID.isNotBlank()

    init {
        // The Simkl precedent (the round-165 device bug): restore the LoggedIn
        // state from the stored tokens at construction — the cold start must
        // not show MAL as unlinked when the tokens survived.
        if (preferences.isLinked) {
            _loginState.value = TrackerLoginState.LoggedIn(preferences.userName)
            Logger.i(TAG) { "init — MAL login restored from the stored tokens (user=${preferences.userName})" }
        }
    }

    // ── authentication ──

    override suspend fun isLoggedIn(): Boolean =
        isConfigured && preferences.isLinked

    override suspend fun startLogin(): String? {
        if (!isConfigured) {
            Logger.w(TAG) { "startLogin — not configured (no client id)" }
            return null
        }
        return MalOAuth.startLogin()
    }

    /**
     * The redirect handler — MainActivity's mal-auth dispatch calls this with
     * the authorization code. Exchanges it (the plain-PKCE verifier rides in
     * memory from startLogin), stores the tokens, fetches the account name.
     */
    override suspend fun handleLoginCallback(code: String): Boolean = withContext(Dispatchers.IO) {
        val tokens = MalOAuth.exchangeCode(client, theJson(), code)
        if (tokens == null) {
            _loginState.value = TrackerLoginState.Error(
                "MAL login failed — the code exchange didn't go through.",
            )
            return@withContext false
        }
        storeTokens(tokens)
        val me = api.usersMe(tokens.access_token)
        preferences.userName = me?.name.orEmpty()
        _loginState.value = TrackerLoginState.LoggedIn(preferences.userName)
        Logger.i(TAG) { "handleLoginCallback — linked as ${preferences.userName}" }
        true
    }

    override suspend fun logout() = withContext(Dispatchers.IO) {
        MalOAuth.cancelLogin()
        preferences.clear()
        _loginState.value = TrackerLoginState.LoggedOut
        Logger.i(TAG) { "logout — tokens cleared" }
    }

    // ── sync ──

    override suspend fun syncEntry(entry: TrackEntry): Boolean = withContext(Dispatchers.IO) {
        if (!ensureFreshToken()) return@withContext false
        val token = preferences.accessToken
        val updated = api.updateListStatus(
            id = entry.trackerId,
            status = mapStatus(entry.status),
            episodesWatched = entry.progress.coerceAtLeast(0),
            score = entry.score?.let { toMalScore(it) },
            accessToken = token,
        )
        if (updated == null) {
            _syncState.value = TrackerSyncState.Failed("MAL sync failed (the list-status write)")
            return@withContext false
        }
        _syncState.value = TrackerSyncState.Success(System.currentTimeMillis())
        true
    }

    override suspend fun fetchEntry(trackerId: Int): TrackEntry? = withContext(Dispatchers.IO) {
        if (!ensureFreshToken()) return@withContext null
        val anime = api.animeWithListStatus(trackerId, preferences.accessToken) ?: return@withContext null
        val state = anime.my_list_status ?: return@withContext null
        TrackEntry(
            contentKey = "", // the caller keys by mainId — the fetch is identity-free
            trackerId = trackerId,
            status = mapFromMal(state.status ?: return@withContext null),
            score = if (state.score > 0) state.score * 10 else null,
            progress = state.num_episodes_watched,
            totalEpisodes = anime.num_episodes,
            listId = null,
            startedAt = isoToMillis(state.start_date),
            completedAt = isoToMillis(state.finish_date),
            updatedAt = state.updated_at?.let { isoToMillis(it) } ?: System.currentTimeMillis(),
        )
    }

    override suspend fun search(query: String): List<TrackEntry> = withContext(Dispatchers.IO) {
        api.searchAnime(query).map { node ->
            // NOTE: TrackEntry carries no title field (the linking UIs surface
            // titles through their own search models — the Simkl precedent);
            // these rows are the id-bearing contract for the pickers.
            TrackEntry(
                contentKey = "",
                trackerId = node.id,
                status = TrackStatus.PLAN_TO_WATCH,
                score = null,
                progress = 0,
                totalEpisodes = node.num_episodes,
                listId = null,
                startedAt = null,
                completedAt = null,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    /**
     * ROUND 168 (Phase D): the search with the DISPLAY fields — the
     * MalLinkSheet's pick rows (the title + the cover + the episodes). The
     * app-auth read (no user token needed).
     */
    suspend fun searchWithMeta(query: String, limit: Int = 10, offset: Int = 0): List<MalAnime> =
        withContext(Dispatchers.IO) {
            api.searchAnime(query, limit, offset)
        }

    /**
     * ROUND 168 (Phase E): the popularity ranking (the search source's
     * blank-query default). App-auth — works unlinked.
     */
    suspend fun topAnimeForUi(limit: Int = 20, offset: Int = 0): List<MalAnime> =
        withContext(Dispatchers.IO) {
            api.topAnime(limit, offset)
        }

    /**
     * ROUND 173 (D1-driven): the FULL detail for the enrichment pass (the
     * synopsis/genres/year/episodes/status merge — the data-limited MAL
     * pages' fix). The failures THROW (the surfaced reason).
     */
    suspend fun detail(id: Int): MalDetail = withContext(Dispatchers.IO) {
        api.detail(id)
    }

    /**
     * ROUND 168 (Phase D): the REAL remote delete — the trash-can path (the
     * AniListTracker.deleteEntry contract). False when not linked or the
     * write fails.
     */
    suspend fun deleteEntry(trackerId: Int): Boolean = withContext(Dispatchers.IO) {
        if (!ensureFreshToken()) return@withContext false
        api.deleteListStatus(trackerId, preferences.accessToken)
    }

    // ── internals ──

    private fun theJson(): kotlinx.serialization.json.Json =
        kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    private suspend fun ensureFreshToken(): Boolean {
        if (!isConfigured) {
            Logger.w(TAG) { "ensureFreshToken — not configured" }
            return false
        }
        if (!preferences.isLinked) {
            _loginState.value = TrackerLoginState.LoggedOut
            return false
        }
        val expiresAt = preferences.accessExpiresAtMs
        if (expiresAt > System.currentTimeMillis() + REFRESH_MARGIN_MS) return true
        val refreshed = MalOAuth.refresh(client, theJson(), preferences.refreshToken)
        if (refreshed == null) {
            Logger.w(TAG) { "token refresh failed — the user re-links" }
            return false
        }
        storeTokens(refreshed)
        return true
    }

    private fun storeTokens(tokens: MalTokens) {
        preferences.accessToken = tokens.access_token
        preferences.refreshToken = tokens.refresh_token
        preferences.accessExpiresAtMs = System.currentTimeMillis() + tokens.expires_in * 1000L
    }

    private fun mapStatus(status: TrackStatus): String = when (status) {
        TrackStatus.WATCHING, TrackStatus.REWATCHING -> "watching"
        TrackStatus.COMPLETED -> "completed"
        TrackStatus.PAUSED -> "on_hold"
        TrackStatus.DROPPED -> "dropped"
        TrackStatus.PLAN_TO_WATCH -> "plan_to_watch"
    }

    private fun mapFromMal(status: String): TrackStatus = when (status.lowercase()) {
        "watching" -> TrackStatus.WATCHING
        "completed" -> TrackStatus.COMPLETED
        "on_hold" -> TrackStatus.PAUSED
        "dropped" -> TrackStatus.DROPPED
        "plan_to_watch" -> TrackStatus.PLAN_TO_WATCH
        else -> TrackStatus.WATCHING
    }

    private fun toMalScore(appScore: Int): Int =
        (appScore.coerceIn(0, 100) / 10.0).let { kotlin.math.round(it) }.toInt().coerceIn(1, 10)

    /**
     * ROUND 179 (THE POPULATE): the user's FULL animelist — the Populate
     * Library import. Fetches through [MalApi.userAnimelist] (paginated) and
     * maps each row's list status through [mapFromMal]. Rows without a list
     * status are skipped. Throws on a failed first page (the caller surfaces
     * the reason); a mid-pagination failure keeps the rows fetched so far.
     */
    suspend fun userAnimelist(): List<MalListEntry> = withContext(Dispatchers.IO) {
        if (!ensureFreshToken()) return@withContext emptyList()
        val token = preferences.accessToken
        val anime = api.userAnimelist(token)
        anime.mapNotNull { row ->
            val ls = row.my_list_status ?: return@mapNotNull null
            val rawStatus = ls.status ?: "watching"
            MalListEntry(row, mapFromMal(rawStatus), rawStatus)
        }
    }

    /** MAL dates: `YYYY-MM-DD` (or null). */
    private fun isoToMillis(date: String?): Long? = runCatching {
        java.time.LocalDate.parse(date!!.substringBefore("T"))
            .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    }.getOrNull()
}

/**
 * ROUND 179 (THE POPULATE): one row of the user's MAL animelist — the anime
 * node + its list status mapped to the app's [TrackStatus] (the raw status
 * rides along for the category mapping).
 */
data class MalListEntry(
    val anime: MalListAnime,
    val status: TrackStatus,
    val rawStatus: String,
)
