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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.Job
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
    // ROUND 164 (STEP-3d): the movies/series source for the merged feed
    // (metadata-only; the home's TMDB taps hand off to the CS search).
    private val tmdbApi: com.confused.anikuta.core.tmdb.TmdbApi,
    private val tmdbPreferences: com.confused.anikuta.core.tmdb.TmdbPreferences,
) : ViewModel() {

    companion object {
        private const val TAG = "Anikuta:Feature:Home"

        // The home_trending storage key (BrowseCacheCodec owns the namespace —
        // no drift). The old trending/popular/top_rated keys stay untouched —
        // Search co-owns those (D-278); home_popular / home_top_rated are
        // retired with the round-148 sections.
        private const val SECTION_TRENDING = BrowseCacheCodec.SECTION_HOME_TRENDING
        // ROUND 164 (STEP-3d): the per-kind section keys (the SAME codec —
        // every kind encodes as AniListAnime rows; TMDB items ride synthetic
        // NEGATIVE ids so the UI's tap routing can mark them).
        private const val SECTION_TRENDING_MOVIES = "home_trending_movies"
        private const val SECTION_TRENDING_SERIES = "home_trending_series"
        private const val SECTION_TRENDING_DONGHUA = "home_trending_donghua"

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

    // ── ROUND 165 (the device order): the EXPAND/COLLAPSE — the collapsed
    // view shows the 6-row contract (2 released + next + 3); the expanded
    // view shows the FULL window (released desc + upcoming asc), the
    // animateItem glides animating the additions (a new released row lands at
    // the TOP, the upcoming rows extend the BOTTOM).
    private val _airsExpanded = MutableStateFlow(false)
    val airsExpanded: StateFlow<Boolean> = _airsExpanded.asStateFlow()

    fun setAirsExpanded(expanded: Boolean) {
        _airsExpanded.value = expanded
    }

    // ── ROUND 165 (STEP-3d depth): the PER-KIND POOLS — the merged feed's
    // intelligence reads these (the hero's 5-per-kind random interleave, the
    // rail's ranked round-robin, the field's random covers, the mood pools).
    private val _poolsByKind =
        MutableStateFlow<Map<com.confused.anikuta.core.preferences.HomeContentKind, List<AniListAnime>>>(emptyMap())
    val poolsByKind: StateFlow<Map<com.confused.anikuta.core.preferences.HomeContentKind, List<AniListAnime>>> =
        _poolsByKind.asStateFlow()

    /** The active mood chip's by-genre merged pool (null = the idle/local state). */
    private val _moodPool = MutableStateFlow<List<AniListAnime>?>(null)
    val moodPool: StateFlow<List<AniListAnime>?> = _moodPool.asStateFlow()

    /** The TMDB genre maps (fetched once per VM; the mood chips' id resolution). */
    private var tmdbGenreMap: Map<Int, String>? = null

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
        loadTrending()
        observeContinueWatching()
        observeAirsToday()
        observeSuggestedPool()
        // ROUND 164 (STEP-3d): INSTANT APPLY — a Settings toggle re-derives
        // the merged feed without a screen reload (the flatMapLatest lands
        // the change into the same cache-first pipeline).
        viewModelScope.launch {
            homePreferences.contentKindsFlow.collect {
                    trendingSectionsJob?.cancel()
                    loadTrending()
                }
        }
    }

    // ── Trending (the EXACT round-148 cache-first loadSection, one section) ──

    private var trendingSectionsJob: Job? = null

    /**
     * ROUND 164 (STEP-3d): the MERGED multi-kind load — cache-first per
     * enabled kind, the sections INTERLEAVED into one pool (round-robin so
     * the hero reads varied), each section cached under its own key with the
     * SAME codec. LAYOUT PARITY: everything maps into AniListAnime-shaped
     * data — the home's composables are untouched.
     */
    private fun loadTrending() {
        val kinds = homePreferences.getContentKinds()
        trendingSectionsJob = viewModelScope.launch {
            var servedFromFreshCache = false
            val pools = mutableMapOf<com.confused.anikuta.core.preferences.HomeContentKind, List<AniListAnime>>()
            val toFetch = mutableListOf<com.confused.anikuta.core.preferences.HomeContentKind>()

            // 1. The cache-first pass per enabled kind (the POOLS build here).
            for (kind in kinds) {
                val section = sectionKeyFor(kind)
                val cached = withContext(Dispatchers.IO) { dataCacheRepository.getBrowseCache(section) }
                var rows: List<AniListAnime> = emptyList()
                if (cached != null) {
                    rows = withContext(Dispatchers.IO) {
                        runCatching { BrowseCacheCodec.decode(cached.dataJson) }
                            .onFailure { Logger.w(TAG) { "Failed to parse $section cache: ${it.message}" } }
                            .getOrDefault(emptyList())
                    }
                }
                if (rows.isNotEmpty()) {
                    Logger.i(TAG) { "Loaded ${rows.size} $section from cache" }
                    pools[kind] = rows
                    servedFromFreshCache = true
                }
                if (cached == null || dataCacheRepository.isBrowseCacheExpired(section)) {
                    toFetch += kind
                }
            }

            if (pools.isNotEmpty()) {
                _poolsByKind.value = pools
                onTrending(composeMergedOrder(pools))
            }
            if (toFetch.isEmpty()) {
                settleHome(null, servedFromFreshCache)
            } else {
                fetchTrendingSections(toFetch)
            }
        }
    }

    private fun sectionKeyFor(kind: com.confused.anikuta.core.preferences.HomeContentKind): String = when (kind) {
        com.confused.anikuta.core.preferences.HomeContentKind.ANIME -> SECTION_TRENDING
        com.confused.anikuta.core.preferences.HomeContentKind.MOVIES -> SECTION_TRENDING_MOVIES
        com.confused.anikuta.core.preferences.HomeContentKind.SERIES -> SECTION_TRENDING_SERIES
        com.confused.anikuta.core.preferences.HomeContentKind.DONGHUA -> SECTION_TRENDING_DONGHUA
    }

    /** Fetches ONLY the expired/missing sections; each is failure-isolated. */
    private suspend fun fetchTrendingSections(kinds: List<com.confused.anikuta.core.preferences.HomeContentKind>) {
        _isRefreshing.value = true
        try {
            val fetched = mutableMapOf<com.confused.anikuta.core.preferences.HomeContentKind, List<AniListAnime>>()
            for (kind in kinds) {
                val rows = runCatching { fetchSectionFor(kind) }
                    .onFailure { Logger.e(TAG, it) { "Failed to fetch the $kind section: ${it.message}" } }
                    .getOrDefault(emptyList())
                if (rows.isNotEmpty()) {
                    fetched[kind] = rows
                    val json = withContext(Dispatchers.IO) { BrowseCacheCodec.encode(rows) }
                    withContext(Dispatchers.IO) {
                        dataCacheRepository.upsertBrowseCache(sectionKeyFor(kind), json)
                    }
                    Logger.i(TAG) { "Cached ${rows.size} rows for $kind" }
                }
            }
            // Re-read the caches (the fresh sections + the still-valid ones)
            // into the POOLS — the single source of truth stays the cache; the
            // composed order rides composeMergedOrder.
            val pools = mutableMapOf<com.confused.anikuta.core.preferences.HomeContentKind, List<AniListAnime>>()
            for (kind in homePreferences.getContentKinds()) {
                val cached = withContext(Dispatchers.IO) { dataCacheRepository.getBrowseCache(sectionKeyFor(kind)) }
                if (cached != null) {
                    val rows = withContext(Dispatchers.IO) {
                        runCatching { BrowseCacheCodec.decode(cached.dataJson) }.getOrDefault(emptyList())
                    }
                    if (rows.isNotEmpty()) pools[kind] = rows
                }
            }
            _poolsByKind.value = pools
            val merged = composeMergedOrder(pools)
            onTrending(merged)
            settleHome(null, merged.isNotEmpty())
        } finally {
            _isRefreshing.value = false
        }
    }

    /** One kind's network fetch (the fan-out lives in fetchTrendingSections). */
    private suspend fun fetchSectionFor(
        kind: com.confused.anikuta.core.preferences.HomeContentKind,
    ): List<AniListAnime> = when (kind) {
        com.confused.anikuta.core.preferences.HomeContentKind.ANIME ->
            anilistApi.fetchBrowseSection(TRENDING_SORT)
        com.confused.anikuta.core.preferences.HomeContentKind.DONGHUA ->
            anilistApi.fetchTrendingDonghua()
        com.confused.anikuta.core.preferences.HomeContentKind.MOVIES ->
            tmdbApi.trending()
                .filter { it.mediaType == "movie" }
                .ifEmpty { tmdbApi.popularMovies() }
                .map { it.toSyntheticAnime("Movies") }
        com.confused.anikuta.core.preferences.HomeContentKind.SERIES -> {
            val trending = tmdbApi.trending().filter { it.mediaType == "tv" }
            val rows = trending.ifEmpty { tmdbApi.popularTv() }
            rows.map { it.toSyntheticAnime("Series") }
        }
    }

    /**
     * THE PARITY TRICK — a TMDB item rides the home as a SYNTHETIC
     * AniListAnime with a NEGATIVE id (-tmdbId): every home composable renders
     * it unchanged, and the tap routing recognizes the marker (a negative id
     * opens the metadata hand-off instead of the AniList details).
     */
    private fun com.confused.anikuta.core.tmdb.TmdbApi.TmdbItem.toSyntheticAnime(kindGenre: String): AniListAnime {
        // v1: the kind rides as the single genre (the mood chips filter on
        // names; a per-item TMDB genre map lands with the genre rails work).
        val names = listOf(kindGenre)
        return AniListAnime(
            id = -id,
            title = com.confused.anikuta.core.anilist.model.AnimeTitle(romaji = title, english = title),
            coverImage = com.confused.anikuta.core.anilist.model.CoverImage(large = posterUrl, extraLarge = posterUrl),
            averageScore = rating?.let { (it * 10).toInt().coerceIn(0, 100) },
            description = overview,
            genres = names,
            seasonYear = year,
            status = "RELEASING",
        )
    }


    private fun onTrending(anime: List<AniListAnime>) {
        // ROUND 165: the pools drive the composition — the merged list's ORDER
        // carries the intelligence (the hero's 5-per-kind random interleave,
        // the rail's ranked round-robin); the screen renders it unchanged.
        _trending.value = anime
        if (_state.value is HomeState.Loading && anime.isNotEmpty()) _state.value = HomeState.Content
        promoteOutOfError()
        if (anime.isNotEmpty() && _fieldCovers.value.isEmpty()) loadFieldCovers()
    }

    /**
     * ROUND 165 (STEP-3d depth): composes the merged pool's ORDER from the
     * per-kind pools:
     * - THE HERO: 5 random items PER enabled kind, interleaved in a RANDOM
     *   kind order (the user's spec: 5 → 10 → 15 → 20 as kinds stack, the
     *   kinds shuffled, never one type dominant at the top).
     * - THE RAIL: each kind's remainder sorted by rating desc, round-robin
     *   interleaved (k1[0], k2[0], … — the popularity algorithm per the user's
     *   "organized based on their ratings and popularity").
     */
    private fun composeMergedOrder(
        pools: Map<com.confused.anikuta.core.preferences.HomeContentKind, List<AniListAnime>>,
    ): List<AniListAnime> {
        if (pools.isEmpty()) return emptyList()
        val heroPerKind = 5
        val kinds = pools.keys.toList()

        // The hero: 5 random per kind, the KIND ORDER randomized, the items
        // round-robin interleaved so no two adjacent slots share a kind more
        // than necessary.
        val heroPicks = kinds.associateWith { kind -> pools[kind].orEmpty().shuffled().take(heroPerKind) }
        val hero = mutableListOf<AniListAnime>()
        var index = 0
        while (hero.size < heroPerKind * kinds.size) {
            var added = false
            for (kind in kinds) {
                heroPicks[kind]?.getOrNull(index)?.let {
                    hero += it
                    added = true
                }
            }
            if (!added) break
            index++
        }

        // The rail: the remainder per kind, rating-ranked, round-robin.
        val rest = kinds.associateWith { kind ->
            pools[kind].orEmpty().drop(heroPerKind).sortedByDescending { it.averageScore ?: 0 }
        }
        val rail = mutableListOf<AniListAnime>()
        index = 0
        while (true) {
            var added = false
            for (kind in kinds) {
                rest[kind]?.getOrNull(index)?.let {
                    rail += it
                    added = true
                }
            }
            if (!added) break
            index++
        }
        return hero + rail
    }

    /**
     * ROUND 165: the mood chips' BY-GENRE merged pool — for each enabled kind
     * the genre resolves natively (anime: the AniList genre search; donghua:
     * the CN query + the genre; TMDB: the discover with the genre id) and the
     * pools merge. A kind without the genre contributes nothing (the user's
     * exact spec). POPULARITY-ordered per kind (the "overall ones, not the
     * trending-now").
     */
    fun loadMoodPool(genre: String) {
        viewModelScope.launch {
            _moodPool.value = null
            val kinds = homePreferences.getContentKinds()
            val merged = mutableListOf<AniListAnime>()
            for (kind in kinds) {
                val rows = runCatching {
                    when (kind) {
                        com.confused.anikuta.core.preferences.HomeContentKind.ANIME ->
                            anilistApi.fetchByGenre(genre).media
                        com.confused.anikuta.core.preferences.HomeContentKind.DONGHUA ->
                            anilistApi.fetchTrendingDonghua(genre = genre)
                        com.confused.anikuta.core.preferences.HomeContentKind.MOVIES ->
                            discoverTmdb("movie", genre)
                        com.confused.anikuta.core.preferences.HomeContentKind.SERIES ->
                            discoverTmdb("tv", genre)
                    }
                }.onFailure { Logger.w(TAG) { "mood pool $kind/$genre failed: ${it.message}" } }
                    .getOrDefault(emptyList())
                merged += rows
            }
            _moodPool.value = merged
            Logger.i(TAG) { "mood pool '$genre' — ${merged.size} rows across ${kinds.size} kinds" }
        }
    }

    /** The TMDB discover by the genre NAME (the id resolves via the genre map). */
    private suspend fun discoverTmdb(mediaType: String, genreName: String): List<AniListAnime> {
        if (!tmdbPreferences.enabled) return emptyList()
        // ROUND 165 (the device bug): the map read was  —
        // ALWAYS empty, so every movies/series mood pool returned nothing (the
        // v1.1.102 device report). The map fetches lazily through the injected
        // Api and caches for the VM lifetime.
        val map = tmdbGenreMap ?: tmdbApi.genreNames().also { tmdbGenreMap = it }
        val id = map.entries.firstOrNull { it.value.equals(genreName, ignoreCase = true) }?.key
            ?: return emptyList()
        return tmdbApi.discoverByGenre(mediaType, id).map { it.toSyntheticAnime(genreName) }
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
            _airsToday.value.isNotEmpty()

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

        // ROUND 165: the EXPANDED view shows the full window (released desc +
        // upcoming asc); the collapsed view keeps the 6-row contract.
        val window: List<GlobalAirsRow> = if (_airsExpanded.value) {
            released + upcoming
        } else {
            val releasedHead = released.take(AIRS_RELEASED_COUNT)
            val upcomingBody = upcoming.take(AIRS_TOTAL - releasedHead.size)
            val releasedPad = released.drop(releasedHead.size)
                .take((AIRS_TOTAL - releasedHead.size - upcomingBody.size).coerceAtLeast(0))

            // The CONTRACT (round-163 device fix): slot 3 is ALWAYS the next to
            // air — the released pad rows fill AFTER the upcoming body. The old
            // assembly displaced the next-to-air below released filler whenever
            // released >= 3 and upcoming <= 3 (the steady state of a small
            // airing library — the device verdict "it shows three of the ones
            // which have been released").
            releasedHead + upcomingBody + releasedPad
        }
        return window.map { row ->
        // air — the released pad rows fill AFTER the upcoming body. The old
        // `releasedHead + releasedPad + upcomingBody` displaced the next-to-air
        // below released filler whenever released >= 3 and upcoming <= 3 (the
        // steady state of a small airing library — the device verdict "it
        // shows three of the ones which have been released").

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
     * upcoming → pad from the released — AFTER the upcoming body (slot 3 is
     * ALWAYS the next to air; the round-163 device fix). The released pool is
     * bounded to [GLOBAL_AIRS_WINDOW_MS] like the global composer — the ±7d
     * observation window let week-old episodes pose as "released today" AND
     * displace the next-to-air (the schedule table only ever receives future
     * episodes which age into this pool and are never pruned). Enriched in one
     * pass (runCatching per item — a row without a main_entry skips itself).
     */
    private suspend fun composeAirsToday(entries: List<ScheduleEntry>, now: Long): List<HomeAirsItem> {
        val deduped = entries
            .distinctBy { Triple(it.mainId, it.episodeNumber, it.scheduledAt) }
        val released = deduped
            .filter { it.scheduledAt <= now && now - it.scheduledAt <= GLOBAL_AIRS_WINDOW_MS }
            .sortedByDescending { it.scheduledAt }
        val upcoming = deduped
            .filter { it.scheduledAt > now }
            .sortedBy { it.scheduledAt }

        // ROUND 165: the expanded view = the full window (released desc +
        // upcoming asc); the collapsed view keeps the 6-row contract.
        val window: List<ScheduleEntry> = if (_airsExpanded.value) {
            released + upcoming
        } else {
            val releasedHead = released.take(AIRS_RELEASED_COUNT)
            val upcomingBody = upcoming.take(AIRS_TOTAL - releasedHead.size)
            val releasedPad = released.drop(releasedHead.size)
                .take((AIRS_TOTAL - releasedHead.size - upcomingBody.size).coerceAtLeast(0))
            releasedHead + upcomingBody + releasedPad
        }

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
        // ROUND 165 (STEP-3d depth): the pools FIRST — the honeycomb's covers
        // sample RANDOMLY across the enabled kinds' pools (the user's spec:
        // the suggested field shows the enabled kinds' content, combined and
        // randomly placed). The by-genre fetch remains the fallback when the
        // pools are empty (a cold start before any section landed).
        val pools = _poolsByKind.value
        if (pools.isNotEmpty()) {
            val combined = pools.values.flatten()
            if (combined.isNotEmpty()) {
                _fieldCovers.value = combined.shuffled().take(35).map {
                    HomeFieldCover(anilistId = it.id, url = it.coverUrl)
                }
                return
            }
        }
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

    // ── Refresh (pull-to-refresh + the error Retry) ──────────────────────────

    /**
     * Parallel re-pull: the trending fetch is the only network section (the
     * personal rails are reactive store flows). The suggestion pool rebuilds
     * and its token bumps so the field reshuffles; ROUND 152 — the GLOBAL
     * airs source re-fetches through the reload token.
     */
    fun refresh() {
        viewModelScope.launch { fetchTrendingSections(homePreferences.getContentKinds().toList()) }
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
