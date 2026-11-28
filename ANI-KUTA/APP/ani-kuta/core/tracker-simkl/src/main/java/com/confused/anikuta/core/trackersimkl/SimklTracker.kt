package com.confused.anikuta.core.trackersimkl

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.trackerapi.BaseTracker
import com.confused.anikuta.core.trackerapi.TrackEntry
import com.confused.anikuta.core.trackerapi.TrackStatus
import com.confused.anikuta.core.trackerapi.TrackerKeys
import com.confused.anikuta.core.trackerapi.TrackerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * ROUND 163 (STEP-2): the Simkl tracker — the MOVIES + TV SERIES tracking
 * backend (one Simkl account covers anime too; the anime side stays
 * AniList/MAL per the user's order, and Simkl rows co-exist by tracker_type).
 *
 * The ROLE (the user's round-163 order): anime tracking = AniList (+ the MAL
 * redo, its client id registered); Simkl = movies + TV series. Simkl accepts
 * `anilist`/`tmdb` ids on writes (the /redirect bridge), so a future TV/movies
 * content carries its TMDB id as trackerId and this tracker resolves it
 * honestly through Simkl's own answer.
 *
 * NOT CONFIGURED state: [TrackerKeys.SIMKL_CLIENT_ID] is empty until the user
 * registers the app at simkl.com/apps — every entry point returns honestly
 * (no half-links, the reverted Trakt skeleton's precedent).
 *
 * The status/progress mapping (https://api.simkl.org/api-reference/sync.md):
 * - anime/shows: add-to-list statuses watching/plantowatch/completed/hold/
 *   dropped + history seasons[{number, episodes[{number}]}] marks.
 * - movies: statuses plantowatch/completed/dropped (watching/hold are invalid
 *   for movies); a watched movie rides history's watched_at stamp. A
 *   half-watched movie's real position lives in /sync/playback (future work).
 * - ratings: 1-10 (the app's 0-100 → /10 at the boundary).
 */
class SimklTracker(
    private val api: SimklApi,
    private val preferences: SimklPreferences,
    private val client: OkHttpClient,
) : BaseTracker(TrackerType.SIMKL, "Simkl") {

    companion object {
        private const val TAG = "Anikuta:Core:Tracker:Simkl"
        private const val REFRESH_MARGIN_MS = 60_000L
    }

    val isConfigured: Boolean get() = TrackerKeys.SIMKL_CLIENT_ID.isNotBlank()

    init {
        // ROUND 165 (the device bug): the stored tokens SURVIVE process death
        // (PreferenceStore) but BaseTracker seeds the in-memory login flow
        // LoggedOut — so every cold start showed Simkl as unlinked and forced
        // a pointless re-link. Restore the LoggedIn state from the stored
        // tokens at construction.
        if (preferences.isLinked) {
            _loginState.value = com.confused.anikuta.core.trackerapi.TrackerLoginState.LoggedIn(
                preferences.userName,
            )
            Logger.i(TAG) { "init — Simkl login restored from the stored tokens (user=$preferences.userName)" }
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
        return SimklOAuth.startLogin()
    }

    /**
     * The redirect handler — MainActivity's simkl-auth dispatch calls this
     * with the authorization code. Exchanges it (the PKCE verifier rides in
     * memory from startLogin), stores the tokens, fetches the account name.
     */
    override suspend fun handleLoginCallback(code: String): Boolean = withContext(Dispatchers.IO) {
        val tokens = SimklOAuth.exchangeCode(client, theJson(), code)
        if (tokens == null) {
            _loginState.value = com.confused.anikuta.core.trackerapi.TrackerLoginState.Error(
                "Simkl login failed — the code exchange didn't go through.",
            )
            return@withContext false
        }
        storeTokens(tokens)
        val me = api.usersMe(tokens.accessToken)
        preferences.userName = me?.user?.name.orEmpty()
        _loginState.value = com.confused.anikuta.core.trackerapi.TrackerLoginState.LoggedIn(
            preferences.userName,
        )
        Logger.i(TAG) { "handleLoginCallback — linked as ${preferences.userName}" }
        true
    }

    override suspend fun logout() = withContext(Dispatchers.IO) {
        SimklOAuth.cancelLogin()
        preferences.clear()
        _loginState.value = com.confused.anikuta.core.trackerapi.TrackerLoginState.LoggedOut
        Logger.i(TAG) { "logout — tokens cleared" }
    }

    // ── sync ──

    override suspend fun syncEntry(entry: TrackEntry): Boolean = withContext(Dispatchers.IO) {
        if (!ensureFreshToken()) return@withContext false
        val token = preferences.accessToken
        // ROUND 173 (F4): the SIMKL-NATIVE-FIRST probe — the v1.1.108 bug:
        // the link-sheet picks store SIMKL ids, but the resolution ladder
        // tried ONLY anilist/tmdb → "unresolved" → EVERY unified-sheet
        // Simkl sync aborted ("Couldn't reach Simkl"). Probe /{kind}/{id}
        // first (200 = the owning kind), then the legacy ladder.
        val nativeKind = listOf("tv", "anime", "movie").firstOrNull { kind ->
            api.lookupId(kind, entry.trackerId, token)
        }
        val resolution = resolveEntry(entry.trackerId)
        val simklId: Long
        val type: String
        if (nativeKind != null) {
            simklId = entry.trackerId.toLong()
            type = nativeKind
        } else {
            val r = resolution ?: run {
                Logger.w(TAG) { "syncEntry — id ${entry.trackerId} unresolved on Simkl" }
                return@withContext false
            }
            simklId = r.simkl ?: return@withContext false
            // The legacy ladder's type is nullable (the redirect may not say);
            // 'tv' was the implicit default in the old == comparisons.
            type = r.type ?: "tv"
        }
        val isMovie = type == "movie"
        val ids = mapOf("simkl" to jsonNumber(simklId))

        // 1. The watch status (add-to-list; movies skip watching/hold).
        val status = mapStatus(entry.status, isMovie)
        val item = SimklSyncItem(ids = ids, to = status)
        val body = when {
            isMovie -> SimklSyncBody(movies = listOf(item))
            type == "anime" -> SimklSyncBody(anime = listOf(item))
            else -> SimklSyncBody(shows = listOf(item))
        }
        val statusOk = api.addToList(body, token)

        // 2. The progress — movies stamp watched_at; shows/anime mark episodes.
        val progressOk = when {
            isMovie && entry.progress > 0 -> api.addHistory(
                SimklSyncBody(
                    movies = listOf(
                        SimklSyncItem(
                            ids = ids,
                            watched_at = isoNow(),
                        ),
                    ),
                ),
                token,
            )
            !isMovie && entry.progress > 0 -> api.addHistory(
                SimklSyncBody(
                    shows = listOf(
                        SimklSyncItem(
                            ids = ids,
                            seasons = listOf(
                                SimklSeason(
                                    number = 1,
                                    episodes = (1..entry.progress).map { SimklEpisodeMark(number = it) },
                                ),
                            ),
                        ),
                    ),
                ),
                token,
            )
            else -> true
        }

        // 3. The rating (0-100 → 1-10; 0/null = unrated).
        val ratingOk = entry.score?.takeIf { it > 0 }?.let {
            api.addRatings(
                when {
                    isMovie -> SimklSyncBody(movies = listOf(SimklSyncItem(ids = ids, rating = toSimklScore(it))))
                    type == "anime" -> SimklSyncBody(anime = listOf(SimklSyncItem(ids = ids, rating = toSimklScore(it))))
                    else -> SimklSyncBody(shows = listOf(SimklSyncItem(ids = ids, rating = toSimklScore(it))))
                },
                token,
            ) != null
        } ?: true

        val ok = statusOk && progressOk && ratingOk
        _syncState.value = if (ok) {
            com.confused.anikuta.core.trackerapi.TrackerSyncState.Success(System.currentTimeMillis())
        } else {
            com.confused.anikuta.core.trackerapi.TrackerSyncState.Failed("Simkl sync failed (status=$statusOk progress=$progressOk rating=$ratingOk)")
        }
        ok
    }

    override suspend fun fetchEntry(trackerId: Int): TrackEntry? = withContext(Dispatchers.IO) {
        if (!ensureFreshToken()) return@withContext null
        // ROUND 173 (F4): the SIMKL-NATIVE-FIRST probe — the link-sheet
        // picks store SIMKL ids; the anilist/tmdb ladder never resolves
        // them. /{kind}/{id} 200s for the owning kind → use the id
        // directly with THAT type; the ladder stays for the legacy
        // anilist/tmdb trackerIds.
        val token = preferences.accessToken
        val nativeKind = listOf("tv", "anime", "movie").firstOrNull { kind ->
            api.lookupId(kind, trackerId, token)
        }
        val resolution = resolveEntry(trackerId)
        val simklId: Long
        val kinds: List<String>
        if (nativeKind != null) {
            simklId = trackerId.toLong()
            kinds = listOf(nativeKind)
        } else {
            val r = resolution ?: return@withContext null
            simklId = r.simkl ?: return@withContext null
            kinds = when (r.type) {
                "movie" -> listOf("movie")
                "anime" -> listOf("anime")
                else -> listOf("tv")
            }
        }
        // The list state reads scan the library sections for the id (Simkl has
        // no single-item "my state" endpoint; three bounded reads beat one
        // unbounded guess).
        val nativeIsMovie = nativeKind == "movie" ||
            (nativeKind == null && resolution?.type == "movie")
        for (kind in kinds) {
            for (status in listOf("watching", "plantowatch", "completed", "hold", "dropped")) {
                val items = api.allItems(kind, status, preferences.accessToken)
                val hit = items.firstOrNull { it.ids?.simkl == simklId }
                if (hit != null) {
                    return@withContext TrackEntry(
                        contentKey = "", // the caller keys by mainId — the fetch is identity-free
                        trackerId = trackerId,
                        status = mapFromSimkl(hit.status ?: status),
                        score = hit.user_rating?.times(10),
                        progress = hit.seasons.maxOfOrNull { season -> season.episodes.maxOfOrNull { it.number } ?: 0 }
                            ?: if (nativeIsMovie) 1 else 0,
                        totalEpisodes = null,
                        listId = null,
                        startedAt = null,
                        completedAt = hit.last_watched_at?.let { isoToMillis(it) },
                        updatedAt = System.currentTimeMillis(),
                    )
                }
            }
        }
        null
    }

    override suspend fun search(query: String): List<TrackEntry> = withContext(Dispatchers.IO) {
        val response = api.search("all", query) ?: return@withContext emptyList()
        (response.anime + response.tv + response.movie).mapNotNull { item ->
            val id = item.simkl_id ?: item.ids?.simkl ?: return@mapNotNull null
            // NOTE: TrackEntry carries no title field (the linking UIs surface
            // titles through their own search models); these rows are the
            // id-bearing contract for the TrackSheet-style pickers.
            TrackEntry(
                contentKey = "",
                trackerId = id.toInt(),
                status = TrackStatus.PLAN_TO_WATCH,
                score = null,
                progress = 0,
                totalEpisodes = null,
                listId = null,
                startedAt = null,
                completedAt = null,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    /**
     * ROUND 168 (Phase E): the search WITH the display fields — the search
     * source's result rows (the title + the poster + the year). The 'all'
     * kind returns every Simkl category.
     */
    suspend fun searchItems(query: String): List<SimklMediaItem> = withContext(Dispatchers.IO) {
        // ROUND 170: the search REQUIRES the user token, and the kinds are
        // PER-ENDPOINT (no /search/all — the v1.1.105 empty results came from
        // that 404). The three kinds merge, deduped by id.
        // ROUND 173 (F3.6/F5): the token REFRESHES first (the v1.1.108 bug:
        // the raw token was read WITHOUT ensureFreshToken — an expired token
        // silently emptied every search on both surfaces) and the failures
        // THROW (SimklRequestException) so the UI surfaces the real reason —
        // the silent empties die.
        if (!ensureFreshToken()) {
            throw SimklRequestException("Simkl is not connected — link it in Settings → Trackers")
        }
        val token = preferences.accessToken
        val all = listOf("movie", "tv", "anime").flatMap { kind ->
            api.searchKind(kind, query, token)
        }
        all.distinctBy { it.ids?.simkl_id ?: it.ids?.simkl ?: it.simkl_id }
    }

    /**
     * ROUND 168 (Phase E): the trending default (the blank-query grid fill).
     * ROUND 173 (F3.6): the token REFRESHES + ATTACHES — the v1.1.108 shape
     * attached NONE (the live 401 on EVERY trending call; the device saw the
     * Cycle-G cache, masking it). The failures THROW (the surfaced reason).
     */
    suspend fun trendingItems(kind: String): List<SimklMediaItem> = withContext(Dispatchers.IO) {
        if (!ensureFreshToken()) {
            throw SimklRequestException("Simkl is not connected — link it in Settings → Trackers")
        }
        api.trending(kind, preferences.accessToken)
    }

    // ── internals ──

    private fun theJson(): kotlinx.serialization.json.Json =
        kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /**
     * The id-resolution ladder: the entry's trackerId is an AniList id for
     * anime content and a TMDB id for movies/TV (the two axes' existing
     * semantics). Resolve anilist first; fall through to tmdb.
     */
    private fun resolveEntry(trackerId: Int): SimklIdResolution? {
        return api.resolveExternalId("anilist", trackerId.toLong())
            ?: api.resolveExternalId("tmdb", trackerId.toLong())
    }

    private suspend fun ensureFreshToken(): Boolean {
        if (!isConfigured) {
            Logger.w(TAG) { "ensureFreshToken — not configured" }
            return false
        }
        if (!preferences.isLinked) {
            _loginState.value = com.confused.anikuta.core.trackerapi.TrackerLoginState.LoggedOut
            return false
        }
        val expiresAt = preferences.accessExpiresAtMs
        if (expiresAt > System.currentTimeMillis() + REFRESH_MARGIN_MS) return true
        val refreshed = SimklOAuth.refresh(client, theJson(), preferences.refreshToken)
        if (refreshed == null) {
            Logger.w(TAG) { "token refresh failed — the user re-links (the refresh token is 180d sliding; a failure means revocation or expiry)" }
            return false
        }
        storeTokens(refreshed)
        return true
    }

    private fun storeTokens(tokens: SimklTokens) {
        preferences.accessToken = tokens.accessToken
        preferences.refreshToken = tokens.refreshToken
        preferences.accessExpiresAtMs = System.currentTimeMillis() + tokens.expiresInSeconds * 1000L
    }

    private fun mapStatus(status: TrackStatus, isMovie: Boolean): String = when (status) {
        TrackStatus.WATCHING -> if (isMovie) "completed" else "watching"
        TrackStatus.COMPLETED, TrackStatus.REWATCHING -> "completed"
        TrackStatus.PAUSED -> if (isMovie) "completed" else "hold"
        TrackStatus.DROPPED -> "dropped"
        TrackStatus.PLAN_TO_WATCH -> "plantowatch"
    }

    private fun mapFromSimkl(status: String): TrackStatus = when (status.lowercase()) {
        "watching" -> TrackStatus.WATCHING
        "completed" -> TrackStatus.COMPLETED
        "hold" -> TrackStatus.PAUSED
        "dropped" -> TrackStatus.DROPPED
        "plantowatch" -> TrackStatus.PLAN_TO_WATCH
        else -> TrackStatus.WATCHING
    }

    private fun toSimklScore(appScore: Int): Int =
        ((appScore.coerceIn(0, 100) / 10.0).let { kotlin.math.round(it) }).toInt().coerceIn(1, 10)

    /**
     * ROUND 179 (THE POPULATE): the user's WHOLE Simkl library across the
     * three kinds × the five statuses — the Populate Library import.
     * allItems is one bounded read per (kind, status) — the fetchEntry
     * pattern; a failing section logs and continues (a partial import beats
     * a dead one). Distinct by the Simkl id (an item holds ONE status per
     * kind; the cross-kind overlaps dedupe safely).
     */
    suspend fun userLibrary(): List<SimklListEntry> = withContext(Dispatchers.IO) {
        if (!ensureFreshToken()) return@withContext emptyList()
        val out = mutableListOf<SimklListEntry>()
        var fetchedRows = 0
        for (kind in listOf("anime", "tv", "movie")) {
            for (status in listOf("watching", "plantowatch", "completed", "hold", "dropped")) {
                val items = try {
                    api.allItems(kind, status, preferences.accessToken)
                } catch (e: Exception) {
                    Logger.w(TAG) { "userLibrary: $kind/$status failed: ${e.message}" }
                    continue
                }
                fetchedRows += items.size
                for (item in items) {
                    val id = item.ids?.simkl ?: continue
                    val title = item.title ?: continue
                    val rawStatus = item.status ?: status
                    val progress = item.seasons
                        .maxOfOrNull { season -> season.episodes.maxOfOrNull { it.number } ?: 0 } ?: 0
                    out += SimklListEntry(
                        simklId = id,
                        title = title,
                        posterUrl = item.poster_url,
                        userRating = item.user_rating,
                        status = mapFromSimkl(rawStatus),
                        rawStatus = rawStatus,
                        progress = progress,
                    )
                }
            }
        }
        // ROUND 182 (the diagnostics): the shape evidence — fetched>0 with a
        // small distinct set, or 0 across ALL sections, names where the
        // shape broke (the tolerant parse + this log pin the next report).
        Logger.i(TAG) { "userLibrary: fetched $fetchedRows rows across 15 sections, ${out.distinctBy { it.simklId }.size} distinct entries" }
        out.distinctBy { it.simklId }
    }

    private fun jsonNumber(value: Long): kotlinx.serialization.json.JsonPrimitive =
        kotlinx.serialization.json.JsonPrimitive(value)

    private fun isoNow(): String =
        java.time.Instant.ofEpochMilli(System.currentTimeMillis()).toString()

    private fun isoToMillis(iso: String): Long? =
        runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull()
}

/**
 * ROUND 179 (THE POPULATE): one row of the user's Simkl library — the mapped
 * [TrackStatus] + the raw status (the category mapping) + the watched-episode
 * progress (the deepest numbered episode across the seasons).
 */
data class SimklListEntry(
    val simklId: Long,
    val title: String,
    val posterUrl: String?,
    val userRating: Int?,
    val status: TrackStatus,
    val rawStatus: String,
    val progress: Int,
)
