package com.confused.anikuta.feature.animebrowse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.anilist.api.AniListApi
import com.confused.anikuta.core.anilist.api.BrowseCacheCodec
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.datacache.DataCacheRepository
import com.confused.anikuta.core.preferences.HomeAirsSource
import com.confused.anikuta.core.preferences.HomePreferences
import com.confused.anikuta.core.schedule.ScheduleEntry
import com.confused.anikuta.core.schedule.ScheduleStore
import com.confused.anikuta.core.watchprogress.WatchProgress
import com.confused.anikuta.core.watchprogress.WatchProgressStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * ViewModel for the HOME screen — ROUND 151, the design-handoff rebuild
 * (this round scrapes the round-148 "My screen" sections: Next up /
 * Releasing soon / Recently released / Popular / Top rated are GONE).
 *
 * The section stack now mirrors the handoff's page.tsx, top → bottom:
 *
 * | Section (home)        | Source                                                            |
 * |-----------------------|-------------------------------------------------------------------|
 * | Hero                  | trending top-5 (the SAME home_trending payload the rail ranks)    |
 *
 * ## The 6-hour home cache (ROUND 156 — the user's contract)
 *
 * EVERY network-fed section is 6-hour cache-first, under the SAME
 * `browse_cache` table + TTL (`DataCacheRepository.BROWSE_CACHE_TTL_MS`):
 * `home_trending` (the hero + the trending rail + the mood rails + the field
 * fallback), `home_airs` (the GLOBAL "Airs today" schedule — the airing
 * times ride the payload, so the countdown chips keep ticking client-side
 * from a cached feed), and `home_field_covers` (the suggestion field's cover
 * pool). On entry a FRESH cache serves with ZERO network; a fetch happens
 * ONLY when the section's cache is expired/missing. The user can always
 * refresh manually by dragging from the top — pull-to-refresh force-fetches
 * every network section and REWRITES the caches, which resets the 6-hour
 * timer. Nothing else ever auto-refreshes. The personal rails (continue
 * watching, the library-schedule airs) are reactive device-data store flows
 * — always live, no cache layer needed. The suggested pool itself is local
 * (the library's genre affinity); its COVERS are the cached network piece.
 *
 * | Continue watching     | [WatchProgressStore.observeContinueWatching] + the user's rules   |
 * | Airs today            | [HomePreferences.airsSource] toggle → [ScheduleStore.observeUpcoming] (see below) |
 * | Trending rail         | the home_trending payload, ranked (the rail shows the rank badge) |
 * | Suggested for you     | the library-genre affinity pool (top-3 genres) for the field agent |
 * | Mood chips            | static genre list; taps filter the TRENDING pool locally (v1)     |
 *
 * ## The one discovery fetch
 * Trending is loaded EXACTLY the round-148 cache-first way (cache read on IO →
 * decode via [BrowseCacheCodec] → instant display; only when the cache is
 * missing/expired ONE [AniListApi.fetchBrowseSection] fetch + upsert, 6h TTL)
 * under the SAME `home_trending` browse_cache key. That single payload feeds
 * the hero AND the rail AND the mood-chip rails — no duplicate fetches.
 *
 * ## The user's continue-watching rules
 * drop `completed` rows; drop `position < 10s` (barely started); order by
 * `lastWatchedAt` DESC; cap 20. Enriched in ONE pass with runCatching per item
 * (the round-148 batch pattern): episode art from
 * [DataCacheRepository.getEpisodeMetadata] with the cover fallback via
 * [ContentRepository].
 *
 * ## The "Airs today" source toggle
 * [HomePreferences.airsSource] ("library" default | "global"). LIBRARY reads
 * [ScheduleStore.observeUpcoming] (next 7 days). ROUND 152: GLOBAL fetches
 * AniList's RELEASING feed through [AniListApi.fetchGlobalAiringSchedule] and
 * composes the same 6-row shape from the shows' nextAiringEpisode clocks
 * (released = aired within the last day). The composition rule is the user's
 * must: EXACTLY 6 rows = 2 released (most recently aired, desc) + the next to
 * air + the 3 after it (asc); shortfalls pad from the other side.
 *
 * ## The suggested pool
 * The library's genre affinity: library mainIds × [ContentRepository.getAllContentDetailsMap],
 * parse `data_genres` CSV, count, top-3. Empty library → empty pool (the field
 * agent renders its demo fallback). Refresh = a token bump.
 *
 * CORE_RULES §20: logged with tag "Anikuta:Feature:Home". §23: reactive state.
 */
class HomeViewModel(
    private val anilistApi: AniListApi,
    private val dataCacheRepository: DataCacheRepository,
    private val watchProgressStore: WatchProgressStore,
    private val scheduleStore: ScheduleStore,
    private val contentRepository: ContentRepository,
    private val homePreferences: HomePreferences,
    // ROUND 157 (WS-D2): the Series & movies feed's source (null in unit
    // tests / environments without CloudStream — the CS block hides).
    private val cloudstreamRepository: com.confused.anikuta.data.cloudstream.content.CloudstreamContentRepository? = null,
) : ViewModel() {

    companion object {
        private const val TAG = "Anikuta:Feature:Home"

        // The home_trending storage key (BrowseCacheCodec owns the namespace —
        // no drift). The old trending/popular/top_rated keys stay untouched —
        // Search co-owns those (D-278); home_popular / home_top_rated are
        // retired with the round-148 sections.
        private const val SECTION_TRENDING = BrowseCacheCodec.SECTION_HOME_TRENDING
    // ROUND 156: the remaining network sections join the same 6h cache.
    private const val SECTION_AIRS = BrowseCacheCodec.SECTION_HOME_AIRS
    private const val SECTION_FIELD_COVERS = BrowseCacheCodec.SECTION_HOME_FIELD_COVERS

        private const val TRENDING_SORT = "TRENDING_DESC"

        /** The hero carousel size (the handoff: 5 slides — the screen takes the payload's head). */
        const val HERO_COUNT = 5

        /** The continue rail's cap (the user's rule). */
        private const val CONTINUE_CAP = 20

        /** A "barely started" episode — position under 10s drops off the rail. */
        private const val MIN_POSITION_SECONDS = 10L

        /** The airs-today window + composition sizes (the user's must). */
        private const val SCHEDULE_WINDOW_MS = 7L * 24 * 60 * 60 * 1000
        private const val AIRS_RELEASED_COUNT = 2
        private const val AIRS_TOTAL = 6

        /**
         * ROUND 152: the GLOBAL source's "released" window — a RELEASING
         * episode counts as released when it aired within the last day.
         */
        private const val GLOBAL_AIRS_WINDOW_MS = 24L * 60 * 60 * 1000

        /** The suggestion pool's genre depth (top-3). */
        private const val SUGGESTED_GENRE_COUNT = 3

        /** ROUND 152: the field's cover pool — per-genre pages merged to 50. */
        private const val SUGGESTED_COVERS_PER_GENRE = 25
        private const val SUGGESTED_COVER_POOL = 50

        /**
         * ROUND 152: the airs recompute cadence — every minute the timeline
         * re-derives its released flags + row identities from the same data,
         * so a release that happens while the user WATCHES actually plays the
         * release transition (top row fades, the rest glide up).
         */
        private const val AIRS_RECOMPUTE_INTERVAL_MS = 60_000L
    }

    private val _state = MutableStateFlow<HomeState>(HomeState.Loading)
    val state: StateFlow<HomeState> = _state.asStateFlow()

    /** Whether a background refresh is in progress (drives pull-to-refresh). */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // ── The ONE trending payload (hero + rail + mood rails share it) ─────────

    private val _trending = MutableStateFlow<List<AniListAnime>>(emptyList())

    /**
     * The ONE trending payload — the hero takes its first [HERO_COUNT] for the
     * carousel, the rail ranks the rest, the mood chips filter it locally.
     */
    val trending: StateFlow<List<AniListAnime>> = _trending.asStateFlow()

    // ── The personal sections ────────────────────────────────────────────────

    private val _continueWatching = MutableStateFlow<List<HomeContinueItem>>(emptyList())
    val continueWatching: StateFlow<List<HomeContinueItem>> = _continueWatching.asStateFlow()

    private val _airsToday = MutableStateFlow<List<HomeAirsItem>>(emptyList())
    val airsToday: StateFlow<List<HomeAirsItem>> = _airsToday.asStateFlow()

    /** The suggestion field's pool + its refresh token (a bump reshuffles the field). */
    private val _suggestedPool = MutableStateFlow(HomeSuggestionPool(emptyList()))
    val suggestedPool: StateFlow<HomeSuggestionPool> = _suggestedPool.asStateFlow()

    private val _suggestedRefreshToken = MutableStateFlow(0)
    val suggestedRefreshToken: StateFlow<Int> = _suggestedRefreshToken.asStateFlow()

    /**
     * ROUND 152: the suggestion field's cover pool — HOISTED out of the field
     * composable so it survives home re-entry (fetched once; the UI reads it
     * as a param). Empty until the first successful fetch, then the trending
     * payload serves as the fallback when the genre fetch lands nothing.
     */
    private val _fieldCovers = MutableStateFlow<List<HomeFieldCover>>(emptyList())
    val fieldCovers: StateFlow<List<HomeFieldCover>> = _fieldCovers.asStateFlow()

    /** The fetch-once guard for [loadFieldCovers]. */
    private var fieldCoversFetched = false

    /** The GLOBAL airing feed's last fetch (the per-minute recompute reads it). */
    private var globalAiringCache: List<AniListAnime> = emptyList()

    /**
     * ROUND 152: the GLOBAL airs branch's reload trigger — pull-to-refresh
     * bumps it so the one-shot global fetch re-runs (the library branch is
     * reactive on its own and ignores this).
     */
    private val _airsReloadToken = MutableStateFlow(0)

    /** The airs source the timeline currently serves (mirrors the toggle). */
    val airsSource: StateFlow<HomeAirsSource> = homePreferences.airsSource

    init {
        // ROUND 157 (WS-D1/D2): the content mode gates the DISCOVERY fetches.
        // ANIME/BOTH run today's AniList path; SERIES_MOVIES skips it entirely
        // (zero AniList traffic) and loads the CS provider shelves instead.
        // The personal rails (continue/airs/suggested) run in every mode.
        when (homePreferences.getContentMode()) {
            com.confused.anikuta.core.preferences.HomeContentMode.SERIES_MOVIES -> loadCsSections()
            com.confused.anikuta.core.preferences.HomeContentMode.BOTH -> {
                loadTrending()
                loadCsSections()
            }
            else -> loadTrending()
        }
        observeContinueWatching()
        observeAirsToday()
        observeSuggestedPool()
        // ROUND 157 (the critic's P1-2): a runtime Settings mode switch
        // re-targets the discovery fetches without a restart.
        viewModelScope.launch {
            homePreferences.contentMode.collect { mode ->
                    when (mode) {
                        com.confused.anikuta.core.preferences.HomeContentMode.SERIES_MOVIES -> {
                            if (_trending.value.isNotEmpty()) _trending.value = emptyList()
                            loadCsSections()
                        }
                        com.confused.anikuta.core.preferences.HomeContentMode.BOTH -> {
                            loadTrending()
                            loadCsSections()
                        }
                        else -> {
                            loadTrending()
                            _csSections.value = emptyList()
                        }
                    }
                }
        }
    }

    // ── Trending (the EXACT round-148 cache-first loadSection, one section) ──

    private fun loadTrending() {
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) {
                dataCacheRepository.getBrowseCache(SECTION_TRENDING)
            }
            var servedFromFreshCache = false
            if (cached != null) {
                val cachedAnime = withContext(Dispatchers.IO) {
                    try {
                        BrowseCacheCodec.decode(cached.dataJson)
                    } catch (e: Exception) {
                        Logger.w(TAG) { "Failed to parse $SECTION_TRENDING cache: ${e.message}" }
                        emptyList()
                    }
                }
                if (cachedAnime.isNotEmpty()) {
                    Logger.i(TAG) { "Loaded ${cachedAnime.size} $SECTION_TRENDING from cache" }
                    onTrending(cachedAnime)
                    servedFromFreshCache = true
                }
            }
            if (cached == null || dataCacheRepository.isBrowseCacheExpired(SECTION_TRENDING)) {
                fetchTrending()
            } else {
                settleHome(null, servedFromFreshCache)
            }
        }
    }

    private suspend fun fetchTrending() {
        _isRefreshing.value = true
        try {
            val anime = anilistApi.fetchBrowseSection(TRENDING_SORT)
            Logger.i(TAG) { "Fetched ${anime.size} $SECTION_TRENDING from network" }
            onTrending(anime)
            val json = withContext(Dispatchers.IO) { BrowseCacheCodec.encode(anime) }
            withContext(Dispatchers.IO) {
                dataCacheRepository.upsertBrowseCache(SECTION_TRENDING, json)
            }
            Logger.i(TAG) { "Cached ${anime.size} $SECTION_TRENDING anime" }
            settleHome(null, anime.isNotEmpty())
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Failed to fetch $SECTION_TRENDING: ${e.message}" }
            settleHome(e.message ?: "Unknown error", false)
        } finally {
            _isRefreshing.value = false
        }
    }

    private fun onTrending(anime: List<AniListAnime>) {
        _trending.value = anime
        if (_state.value is HomeState.Loading && anime.isNotEmpty()) _state.value = HomeState.Content
        promoteOutOfError()
        // ROUND 152: the field's covers fall back to the trending payload —
        // when a cold-start warm-up landed nothing, retry once the payload
        // exists (the fetch-once guard only holds on a non-empty result).
        if (anime.isNotEmpty() && _fieldCovers.value.isEmpty()) loadFieldCovers()
    }

    /**
     * Partial-success Error policy (the round-148 shape, one discovery fetch):
     * home falls to the hard Error state ONLY when the trending fetch settled
     * AND nothing at all loaded (no cached payload AND both personal rails
     * empty). Anything cached → Content; the empty sections don't render.
     */
    private fun settleHome(error: String?, servedSomething: Boolean) {
        if (servedSomething) {
            promoteOutOfError()
            return
        }
        if (_state.value is HomeState.Loading && !anyContentLoaded()) {
            _state.value = HomeState.Error(error ?: "Failed to load")
        }
    }

    /** A rail with data lifts the home out of Error. */
    private fun promoteOutOfError() {
        if (_state.value is HomeState.Error && anyContentLoaded()) {
            _state.value = HomeState.Content
        }
    }

    private fun anyContentLoaded(): Boolean =
        _trending.value.isNotEmpty() ||
            _continueWatching.value.isNotEmpty() ||
            _airsToday.value.isNotEmpty() ||
            _csSections.value.isNotEmpty()

    // ── Continue watching (the user's rules + the round-148 enrichment) ──────

    private fun observeContinueWatching() {
        viewModelScope.launch {
            watchProgressStore.observeContinueWatching(CONTINUE_CAP)
                .collect { rows ->
                    _continueWatching.value = withContext(Dispatchers.IO) { enrichContinue(rows) }
                    promoteOutOfError()
                }
        }
    }

    /**
     * The user's rules, enforced in the VM on top of the store's v4 query:
     * drop completed; drop position < 10s; order lastWatchedAt DESC (the rows
     * arrive ordered, but the rules hold regardless); cap 20; dedupe per
     * mainId (the most recent row wins). Then the round-148 one-pass
     * enrichment — runCatching per item, a failing item skips itself.
     */
    private suspend fun enrichContinue(rows: List<WatchProgress>): List<HomeContinueItem> {
        val candidates = rows
            .filter { !it.completed }
            .filter { it.position >= MIN_POSITION_SECONDS }
            .sortedByDescending { it.lastWatchedAt }
            .filter { it.mainId != null }
            .distinctBy { it.mainId }
            .take(CONTINUE_CAP)
        if (candidates.isEmpty()) return emptyList()

        val items = mutableListOf<HomeContinueItem>()
        for (row in candidates) {
            val mainId = row.mainId!!
            runCatching {
                val content = contentRepository.getMainEntryByMainId(mainId)
                    ?: error("no main_entry for $mainId")
                val details = contentRepository.getContentDetails(mainId)
                // The art: the EPISODE thumbnail when the data axis cached one,
                // falling back to the show's cover.
                val episodeThumb = dataCacheRepository.getEpisodeMetadata(mainId)
                    .firstOrNull { it.episodeNumber.roundToInt() == episodeNumberOf(row) }
                    ?.thumbnailUrl
                val cover = episodeThumb
                    ?: details?.dataCoverUrl
                    ?: details?.extThumbnailUrl
                val duration = row.duration.coerceAtLeast(0L)
                val minutesLeft = if (duration > row.position) {
                    ((duration - row.position + 59) / 60).coerceAtLeast(1L).toInt()
                } else {
                    1
                }
                HomeContinueItem(
                    mainId = mainId,
                    title = content.title,
                    artUrl = cover,
                    episodeNumber = episodeNumberOf(row),
                    progressFraction = row.progressFraction,
                    minutesLeft = minutesLeft,
                )
            }.onSuccess { items += it }
                .onFailure { e -> Logger.w(TAG) { "continue row $mainId skipped: ${e.message}" } }
        }
        return items
    }

    /** The episode number carried by a progress row's standardized key. */
    private fun episodeNumberOf(row: WatchProgress): Int =
        row.episodeKey.substringAfterLast('|').toIntOrNull() ?: 0

    // ── Airs today (the source toggle + the user's 2+4 composition) ──────────

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeAirsToday() {
        viewModelScope.launch {
            // ROUND 152: the GLOBAL branch is live — it fetches AniList's
            // RELEASING feed (fetchGlobalAiringSchedule) and composes the same
            // 2-released + next + 3-after shape as the library source, from
            // the RELEASING shows' nextAiringEpisode clocks. LIBRARY keeps the
            // reactive schedule store. The reload token re-opens the GLOBAL
            // fetch on pull-to-refresh (the library source re-emits on its own).
            // ROUND 152: BOTH branches ride a per-minute ticker so the released
            // flags + countdown anchors recompute while the user watches — a
            // release fires the timeline's release transition without any data
            // write landing first.
            homePreferences.airsSource.flatMapLatest { source ->
                if (source == HomeAirsSource.GLOBAL) {
                    // ROUND 156: the token carries the force flag — 0 is the
                    // initial (cache-first) run; every refresh() bump forces
                    // the fetch + the cache rewrite (the 6h timer resets).
                    _airsReloadToken.flatMapLatest { fetchGlobalAirsItems(force = it > 0) }
                } else {
                    val windowStart = System.currentTimeMillis()
                    combine(
                        scheduleStore.observeUpcoming(
                            from = windowStart - SCHEDULE_WINDOW_MS,
                            to = windowStart + SCHEDULE_WINDOW_MS,
                        ),
                        minuteTicker(),
                    ) { entries, tick ->
                        composeAirsToday(entries, tick)
                    }
                }
            }.collect { items ->
                _airsToday.value = items
                promoteOutOfError()
            }
        }
    }

    /** Emits the current wall clock every [AIRS_RECOMPUTE_INTERVAL_MS]. */
    private fun minuteTicker(): Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(AIRS_RECOMPUTE_INTERVAL_MS)
        }
    }

    /**
     * The GLOBAL "Airs today" feed (ROUND 152): ONE fetch per activation
     * (the flatMapLatest re-run), then the per-minute loop re-composes the
     * released flags from the CACHED payload — no refetch, the countdown
     * chips tick client-side from the air times. A network miss soft-fails
     * to an empty list (the section hides — home stays Content) and RETRIES
     * on the next minute tick.
     */
    private fun fetchGlobalAirsItems(force: Boolean): Flow<List<HomeAirsItem>> = flow {
        // ROUND 156: 6-hour cache-first (home_airs). The round-152 flow
        // fetched from the network on EVERY activation — exactly what the
        // user's caching contract forbids. Now: a fresh cache serves with
        // ZERO network (the airing times ride the payload, so the countdown
        // chips keep ticking client-side); a fetch happens ONLY when the
        // cache is expired/missing — or when [force] (pull-to-refresh),
        // which rewrites the cache and resets the 6h timer. A network miss
        // soft-fails to an empty list (the section hides) and RETRIES on the
        // next minute tick — unchanged.
        val cached = if (!force) {
            withContext(Dispatchers.IO) { dataCacheRepository.getBrowseCache(SECTION_AIRS) }
        } else null
        val cachedAnime = cached?.takeUnless { it.isExpired }?.let { c ->
            withContext(Dispatchers.IO) {
                runCatching { BrowseCacheCodec.decode(c.dataJson) }
                    .onFailure { e -> Logger.w(TAG) { "Failed to parse $SECTION_AIRS cache: ${e.message}" } }
                    .getOrDefault(emptyList())
            }
        }.orEmpty()
        if (cachedAnime.isNotEmpty()) {
            globalAiringCache = cachedAnime
            Logger.i(TAG) { "global airing schedule served from cache: ${cachedAnime.size} RELEASING shows" }
        } else {
            runCatching { anilistApi.fetchGlobalAiringSchedule() }
                .onSuccess { airing ->
                    globalAiringCache = airing
                    if (airing.isNotEmpty()) {
                        val json = withContext(Dispatchers.IO) { BrowseCacheCodec.encode(airing) }
                        withContext(Dispatchers.IO) {
                            dataCacheRepository.upsertBrowseCache(SECTION_AIRS, json)
                        }
                        Logger.i(TAG) { "global airing schedule fetched + cached: ${airing.size} RELEASING shows" }
                    }
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    Logger.e(TAG, e) { "global airing schedule failed: ${e.message}" }
                }
        }
        while (true) {
            emit(composeGlobalAirs(globalAiringCache, System.currentTimeMillis()))
            delay(AIRS_RECOMPUTE_INTERVAL_MS)
        }
    }

    /**
     * The GLOBAL composition — the user's must on AniList data: released rows
     * = episodes that aired within [GLOBAL_AIRS_WINDOW_MS] (a RELEASING
     * episode's airingAt is only known forward, so "released" means "recently
     * hit"), then EXACTLY 6 rows = 2 released (desc) + the next to air + the
     * 3 after it (asc), padding from the other side on shortfalls — the same
     * split [composeAirsToday] applies to the library schedule. Rows keep the
     * library row shape (mainId becomes a synthetic `anilist-N` — the UI
     * routes these through the AniList details path instead).
     */
    private fun composeGlobalAirs(airing: List<AniListAnime>, now: Long): List<HomeAirsItem> {
        data class GlobalAirsRow(
            val anilistId: Int,
            val title: String,
            val thumbUrl: String?,
            val episodeNumber: Int,
            val scheduledAt: Long,
        )

        val rows = airing.mapNotNull { anime ->
            val next = anime.nextAiringEpisode ?: return@mapNotNull null
            GlobalAirsRow(
                anilistId = anime.id,
                title = anime.displayName,
                thumbUrl = anime.coverUrl,
                episodeNumber = next.episode,
                scheduledAt = next.airingAt * 1000L, // AniList airs in unix SECONDS
            )
        }
        val released = rows
            .filter { it.scheduledAt <= now && now - it.scheduledAt <= GLOBAL_AIRS_WINDOW_MS }
            .sortedByDescending { it.scheduledAt }
        val upcoming = rows
            .filter { it.scheduledAt > now }
            .sortedBy { it.scheduledAt }

        val releasedHead = released.take(AIRS_RELEASED_COUNT)
        val upcomingBody = upcoming.take(AIRS_TOTAL - releasedHead.size)
        val releasedPad = released.drop(releasedHead.size)
            .take((AIRS_TOTAL - releasedHead.size - upcomingBody.size).coerceAtLeast(0))

        return (releasedHead + releasedPad + upcomingBody).map { row ->
            HomeAirsItem(
                mainId = "anilist-${row.anilistId}",
                title = row.title,
                thumbUrl = row.thumbUrl,
                episodeNumber = row.episodeNumber,
                scheduledAt = row.scheduledAt,
                released = row.scheduledAt <= now,
                anilistId = row.anilistId,
            )
        }
    }

    /**
     * The user's composition must: EXACTLY 6 rows = 2 released (the most
     * recently aired, desc) + the NEXT to air + the 3 after it (asc). Fewer
     * released than 2 → take what exists and fill with upcoming; fewer
     * upcoming → pad from the released. All released rows keep the top of the
     * timeline (desc), the upcoming rows follow (asc). Enriched in one pass
     * (runCatching per item — a row without a main_entry skips itself).
     */
    private suspend fun composeAirsToday(entries: List<ScheduleEntry>, now: Long): List<HomeAirsItem> {
        val deduped = entries
            .distinctBy { Triple(it.mainId, it.episodeNumber, it.scheduledAt) }
        val released = deduped
            .filter { it.scheduledAt <= now }
            .sortedByDescending { it.scheduledAt }
        val upcoming = deduped
            .filter { it.scheduledAt > now }
            .sortedBy { it.scheduledAt }

        val releasedHead = released.take(AIRS_RELEASED_COUNT)
        val upcomingBody = upcoming.take(AIRS_TOTAL - releasedHead.size)
        val releasedPad = released.drop(releasedHead.size)
            .take((AIRS_TOTAL - releasedHead.size - upcomingBody.size).coerceAtLeast(0))
        val window = releasedHead + releasedPad + upcomingBody

        val items = mutableListOf<HomeAirsItem>()
        for (entry in window) {
            runCatching {
                val content = contentRepository.getMainEntryByMainId(entry.mainId)
                    ?: error("no main_entry for ${entry.mainId}")
                val details = contentRepository.getContentDetails(entry.mainId)
                val episodeThumb = dataCacheRepository.getEpisodeMetadata(entry.mainId)
                    .firstOrNull { it.episodeNumber.roundToInt() == entry.episodeNumber.roundToInt() }
                    ?.thumbnailUrl
                HomeAirsItem(
                    mainId = entry.mainId,
                    title = content.title,
                    thumbUrl = episodeThumb
                        ?: details?.dataCoverUrl
                        ?: details?.extThumbnailUrl,
                    episodeNumber = entry.episodeNumber.roundToInt(),
                    scheduledAt = entry.scheduledAt,
                    released = entry.scheduledAt <= now,
                )
            }.onSuccess { items += it }
                .onFailure { e -> Logger.w(TAG) { "airs row ${entry.mainId} skipped: ${e.message}" } }
        }
        return items
    }

    // ── The suggested pool (library-genre affinity) ──────────────────────────

    private fun observeSuggestedPool() {
        viewModelScope.launch {
            _suggestedPool.value = withContext(Dispatchers.IO) { buildSuggestedPool() }
            // ROUND 152: the field covers warm up AFTER the pool lands (the
            // fetch-once guard makes later triggers a no-op).
            loadFieldCovers()
        }
    }

    /**
     * The library's genre affinity: the library mainIds × the details map,
     * parse `data_genres` CSV, count, top-3. Empty library → empty pool (the
     * field agent owns the demo fallback).
     */
    private fun buildSuggestedPool(): HomeSuggestionPool {
        return runCatching {
            val libraryIds = contentRepository.getLibraryMainIds()
            if (libraryIds.isEmpty()) return HomeSuggestionPool(emptyList())
            val detailsMap = contentRepository.getAllContentDetailsMap()
            val counts = HashMap<String, Int>()
            for (mainId in libraryIds) {
                val genres = detailsMap[mainId]?.dataGenres ?: continue
                for (genre in genres.split(',')) {
                    val g = genre.trim()
                    if (g.isNotBlank()) counts.merge(g, 1, Int::plus)
                }
            }
            val top = counts.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .take(SUGGESTED_GENRE_COUNT)
                .map { it.key }
            Logger.i(TAG) { "suggested pool genres: $top" }
            HomeSuggestionPool(top)
        }.getOrElse { e ->
            Logger.w(TAG) { "suggested pool build failed: ${e.message}" }
            HomeSuggestionPool(emptyList())
        }
    }

    // ── The field's cover pool (ROUND 152: hoisted out of the UI) ────────────

    /**
     * Fetches the field's covers ONCE (guarded): the library's top genres
     * through [suggestionCovers], falling back to the trending payload when
     * the fetch lands empty. The guard only HOLDS on a non-empty result, so
     * a cold start (nothing fetched AND no trending yet) retries on the next
     * trigger (refresh / trending arrival / pool rebuild) instead of parking
     * an empty field forever. Survives home re-entry — the UI reads the pool
     * as a param, no refetch on the way back.
     */
    private fun loadFieldCovers(force: Boolean = false) {
        if (fieldCoversFetched && !force) return
        fieldCoversFetched = true
        viewModelScope.launch {
            // ROUND 156: the covers pool is 6-hour cache-first
            // (home_field_covers) — a fresh cache serves with ZERO network;
            // only an expired/missing pool reaches the per-genre fetch (or a
            // pull-to-refresh, which rewrites + resets the timer). The
            // trending-fallback covers are NOT cached — the trending payload
            // owns its own cache, and the next trigger re-attempts the real
            // genre fetch.
            val cachedCovers = if (!force) {
                val c = withContext(Dispatchers.IO) {
                    dataCacheRepository.getBrowseCache(SECTION_FIELD_COVERS)
                }
                c?.takeUnless { it.isExpired }?.let { entry ->
                    withContext(Dispatchers.IO) {
                        runCatching { BrowseCacheCodec.decode(entry.dataJson) }
                            .onFailure { e -> Logger.w(TAG) { "Failed to parse $SECTION_FIELD_COVERS cache: ${e.message}" } }
                            .getOrDefault(emptyList())
                    }
                }.orEmpty()
            } else emptyList()
            val covers = if (cachedCovers.isNotEmpty()) {
                cachedCovers.map { HomeFieldCover(anilistId = it.id, url = it.coverUrl) }
            } else {
                val genres = _suggestedPool.value.genres
                val fetched = if (genres.isEmpty()) {
                    emptyList()
                } else {
                    runCatching { suggestionCovers(genres) }
                        .onFailure { e -> Logger.w(TAG) { "field covers fetch failed: ${e.message}" } }
                        .getOrElse { emptyList() }
                }
                val fetchedCovers = (fetched.ifEmpty { _trending.value })
                    .map { HomeFieldCover(anilistId = it.id, url = it.coverUrl) }
                if (fetched.isNotEmpty()) {
                    val json = withContext(Dispatchers.IO) { BrowseCacheCodec.encode(fetched) }
                    withContext(Dispatchers.IO) {
                        dataCacheRepository.upsertBrowseCache(SECTION_FIELD_COVERS, json)
                    }
                }
                fetchedCovers
            }
            if (covers.isEmpty()) {
                // Nothing to serve yet (no library genres AND no trending) —
                // un-mark so the next trigger retries.
                fieldCoversFetched = false
            } else {
                Logger.i(TAG) { "field covers ready: ${covers.size}" }
                _fieldCovers.value = covers
            }
        }
    }

    /**
     * The suggestion field's covers for the library's top genres: a
     * [AniListApi.fetchByGenre] page per genre (popularity order), merged
     * round-robin-style and deduped to a 50-cover pool. A failing genre
     * fetch skips itself (the pool falls back to the trending covers when
     * the merge lands empty).
     */
    private suspend fun suggestionCovers(genres: List<String>): List<AniListAnime> =
        withContext(Dispatchers.IO) {
            val merged = ArrayList<AniListAnime>()
            val seen = HashSet<Int>()
            for (genre in genres.take(SUGGESTED_GENRE_COUNT)) {
                runCatching { anilistApi.fetchByGenre(genre, page = 1, perPage = SUGGESTED_COVERS_PER_GENRE) }
                    .onSuccess { page ->
                        for (anime in page.media) {
                            if (seen.add(anime.id)) merged.add(anime)
                        }
                    }
                    .onFailure { e -> Logger.w(TAG) { "suggestion covers for \"$genre\" failed: ${e.message}" } }
            }
            merged.take(SUGGESTED_COVER_POOL)
        }

    // ── The CS "Series & movies" home block (WS-D2) ─────────────────────────

    /**
     * The CloudStream provider's browse shelves, rendered as the home's
     * Series & movies block (SERIES_MOVIES / BOTH modes; hidden in ANIME).
     * The loader is the search page's phased pipeline verbatim — the skeleton
     * arrives first, shelves fill as they land, [CloudstreamBrowseCache] holds
     * the 10-minute snapshot. In-memory only here: the search page's cache
     * serves the same provider within its TTL, and a home-grade cache is a
     * later round once the shelf layout settles.
     */
    private val _csSections = kotlinx.coroutines.flow.MutableStateFlow<List<com.confused.anikuta.data.cloudstream.content.CsBrowseSection>>(emptyList())
    val csSections: kotlinx.coroutines.flow.StateFlow<List<com.confused.anikuta.data.cloudstream.content.CsBrowseSection>> = _csSections.asStateFlow()

    private val _csProviderName = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val csProviderName: kotlinx.coroutines.flow.StateFlow<String?> = _csProviderName.asStateFlow()

    private val _csLoading = kotlinx.coroutines.flow.MutableStateFlow(false)
    val csLoading: kotlinx.coroutines.flow.StateFlow<Boolean> = _csLoading.asStateFlow()

    /** ROUND 157 (WS-D1): the home's content mode, exposed for the screen. */
    val contentMode: kotlinx.coroutines.flow.StateFlow<com.confused.anikuta.core.preferences.HomeContentMode> =
        homePreferences.contentMode

    private var csSectionsFetched = false

    fun loadCsSections(force: Boolean = false) {
        val repo = cloudstreamRepository ?: return
        if (csSectionsFetched && !force) return
        viewModelScope.launch {
            _csLoading.value = true
            try {
                // ROUND 157 (the critic's P1-1): the plugin scan is ASYNC —
                // sources.value is empty at Home init. Await the loaded flow
                // before picking (the repo exposes sourcesLoaded).
                repo.sourcesLoaded.firstOrNull()
                // The provider pick: the first installed source with a main
                // page (the search page's provider picker lets the user drive
                // the detail; the home shows the first capable one — a stored
                // home-provider preference is a later round).
                val provider = repo.sources.value.firstOrNull { it.hasMainPage && !it.isNsfw }
                if (provider == null) {
                    Logger.i(TAG) { "CS home: no capable provider — the block hides" }
                    csSectionsFetched = false // the scan may land later — retryable
                    return@launch
                }
                csSectionsFetched = true
                _csProviderName.value = provider.providerName
                _csSections.value = emptyList()
                repo.browseSectionsProgressive(provider.providerName).collect { event ->
                    when (event) {
                        is com.confused.anikuta.data.cloudstream.content.CsBrowseEvent.Section ->
                            _csSections.value = _csSections.value + event.section
                        is com.confused.anikuta.data.cloudstream.content.CsBrowseEvent.Complete ->
                            _csSections.value = event.sections
                        else -> Unit
                    }
                    promoteOutOfError()
                }
            } catch (e: Exception) {
                Logger.w(TAG) { "CS home shelves failed: ${e.message}" }
            } finally {
                _csLoading.value = false
            }
        }
    }

    // ── Refresh (pull-to-refresh + the error Retry) ──────────────────────────

    /**
     * Parallel re-pull: the trending fetch is the only network section (the
     * personal rails are reactive store flows). The suggestion pool rebuilds
     * and its token bumps so the field reshuffles; ROUND 152 — the GLOBAL
     * airs source re-fetches through the reload token.
     */
    fun refresh() {
        // ROUND 157 (WS-D1): pull-to-refresh force-refreshes whichever feed the
        // current content mode shows (both in BOTH).
        when (homePreferences.getContentMode()) {
            com.confused.anikuta.core.preferences.HomeContentMode.SERIES_MOVIES -> loadCsSections(force = true)
            com.confused.anikuta.core.preferences.HomeContentMode.BOTH -> {
                viewModelScope.launch { fetchTrending() }
                loadCsSections(force = true)
            }
            else -> viewModelScope.launch { fetchTrending() }
        }
        viewModelScope.launch {
            _suggestedPool.value = withContext(Dispatchers.IO) { buildSuggestedPool() }
            _suggestedRefreshToken.value += 1
            // ROUND 156: pull-to-refresh is the USER's refresh — it
            // force-fetches the covers and rewrites home_field_covers, which
            // resets the 6-hour timer (the guard stays out of the way).
            loadFieldCovers(force = true)
        }
        viewModelScope.launch { _airsReloadToken.value += 1 }
    }

    /** The suggestion head's refresh button — a token bump only (no network). */
    fun refreshSuggestions() {
        _suggestedRefreshToken.value += 1
    }
}

/** Home's overall state (the BrowseState shape: Loading → Content | Error). */
sealed interface HomeState {
    data object Loading : HomeState
    data object Content : HomeState
    data class Error(val message: String) : HomeState
}

// ── Section item models ──────────────────────────────────────────────────────

/** One Continue-watching card (16:9 episode art + progress + minutes left). */
data class HomeContinueItem(
    val mainId: String,
    val title: String,
    val artUrl: String?,
    val episodeNumber: Int,
    val progressFraction: Float,
    val minutesLeft: Int,
)

/** One Airs-today timeline row (the 2 released + next + 3 after composition). */
data class HomeAirsItem(
    val mainId: String,
    val title: String,
    val thumbUrl: String?,
    val episodeNumber: Int,
    /** The episode's air time (epoch millis) — the UI formats the clock label. */
    val scheduledAt: Long,
    val released: Boolean,
    /**
     * ROUND 152: the AniList id for GLOBAL-source rows (their [mainId] is a
     * synthetic `anilist-N`) — the UI routes these taps to the AniList
     * details page; library rows leave it null and keep the mainId path.
     */
    val anilistId: Int? = null,
) {
    /**
     * ROUND 152: the home list's item key for the airs rows — stable across
     * the per-minute recompute (same episode = same key, so the release
     * transition ANIMATES instead of resetting) and unique per episode slot
     * (mainId + episode + air time disambiguates the 2-released window).
     */
    val episodeKey: String get() = "$mainId|${episodeNumber}|$scheduledAt"
}

/**
 * The suggestion field's data pool (ROUND 152: the [HomeSuggestionField]
 * honeycomb consumes it — the top-3 library genres drive the cover fetch).
 * Empty genres = the library is empty — the field falls back to the trending
 * covers.
 */
data class HomeSuggestionPool(
    val genres: List<String>,
)
