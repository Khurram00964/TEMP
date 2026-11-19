package com.confused.anikuta.core.preferences

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ROUND 151: user preferences for the HOME screen (the design-handoff
 * rebuild).
 *
 * Stored via [PreferenceStore] (SharedPreferences) — NOT the DB (the home
 * feed's source toggle is a UI preference, not content data).
 *
 * ## Settings
 * - `home_airs_source`: "library" (default) | "global" — where the home's
 *   "Airs today" timeline pulls its rows from. Library = the schedule store's
 *   library semi-join ([com.confused.anikuta.core.schedule.ScheduleStore]);
 *   Global = AniList's global airing data. The Settings hub's "Home feed"
 *   group cycles it.
 *
 * ## The popular-tags cache (ROUND 152)
 * The "All genres" index's popular tags come from ONE AniList round trip
 * (fetchPopularTags). A rate-limit blip or an offline moment must not blank
 * the index, so the FIRST successful fetch persists and every later entry
 * reads the cache before the network (refresh in the background past the 7-day
 * TTL). Stored under `popular_tags_json` (the round-152 key name); the payload
 * is newline-joined tag names — tag names never contain newlines and this
 * module has no kotlinx-serialization serializer on its classpath. Plain
 * read-through getters (not a StateFlow) on purpose: a cache, not a setting —
 * the index screen reads on entry and writes on success.
 *
 * CORE_RULES §20: logged with tag "Anikuta:Core:Preferences:Home".
 * CORE_RULES §23: reactive via StateFlow.
 */
class HomePreferences(
    private val store: PreferenceStore,
) {

    companion object {
        // CORE_RULES §20 tag when logging lands: "Anikuta:Core:Preferences:Home".
        private const val KEY_AIRS_SOURCE = "home_airs_source"
        private const val KEY_CONTENT_MODE = "home_content_mode"

        // ROUND 152: the popular-tags cache keys + the 7-day TTL.
        private const val KEY_POPULAR_TAGS = "popular_tags_json"
        private const val KEY_POPULAR_TAGS_AT = "popular_tags_fetched_at"

        /** The popular-tags cache TTL — 7 days, then a background refresh. */
        const val POPULAR_TAGS_TTL_MS = 7L * 24 * 60 * 60 * 1000
    }

    private val _airsSource = MutableStateFlow(getAirsSource())
    val airsSource: StateFlow<HomeAirsSource> = _airsSource.asStateFlow()

    // ROUND 157 (WS-D1): the home's content mode — which feeds the home shows.
    // ANIME (default): today's behavior byte-for-byte. SERIES_MOVIES: the
    // CloudStream providers' shelves only (the AniList discovery fetches are
    // skipped entirely). BOTH: the anime sections + the CS shelves interleave.
    private val _contentMode = MutableStateFlow(getContentMode())
    val contentMode: StateFlow<HomeContentMode> = _contentMode.asStateFlow()

    fun getContentMode(): HomeContentMode =
        HomeContentMode.fromValue(store.getString(KEY_CONTENT_MODE, HomeContentMode.ANIME.value))

    fun setContentMode(mode: HomeContentMode) {
        store.putString(KEY_CONTENT_MODE, mode.value)
        _contentMode.value = mode
    }

    fun cycleContentMode(): HomeContentMode {
        val next = when (_contentMode.value) {
            HomeContentMode.ANIME -> HomeContentMode.SERIES_MOVIES
            HomeContentMode.SERIES_MOVIES -> HomeContentMode.BOTH
            HomeContentMode.BOTH -> HomeContentMode.ANIME
        }
        setContentMode(next)
        return next
    }

    fun getAirsSource(): HomeAirsSource =
        HomeAirsSource.fromValue(store.getString(KEY_AIRS_SOURCE, HomeAirsSource.LIBRARY.value))

    fun setAirsSource(source: HomeAirsSource) {
        store.putString(KEY_AIRS_SOURCE, source.value)
        _airsSource.value = source
    }

    /** Settings-row helper: flips Library → Global → Library, returns the new value. */
    fun cycleAirsSource(): HomeAirsSource {
        val next = if (_airsSource.value == HomeAirsSource.LIBRARY) HomeAirsSource.GLOBAL else HomeAirsSource.LIBRARY
        setAirsSource(next)
        return next
    }

    // ── The popular-tags cache (ROUND 152) ───────────────────────────────────

    /** The cached popular tags, or null when the index has never fetched successfully. */
    fun getPopularTags(): List<String>? {
        val payload = store.getString(KEY_POPULAR_TAGS, "")
        if (payload.isBlank()) return null
        return payload.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { null }
    }

    /** When the tags cache was written (epoch millis; 0 = never). */
    fun getPopularTagsFetchedAt(): Long = store.getLong(KEY_POPULAR_TAGS_AT, 0L)

    /** True when the tags cache is absent or past [POPULAR_TAGS_TTL_MS]. */
    fun isPopularTagsStale(now: Long = System.currentTimeMillis()): Boolean {
        val at = getPopularTagsFetchedAt()
        if (at <= 0L) return true
        return now - at > POPULAR_TAGS_TTL_MS
    }

    /** Persists a successful fetch (the write-through half of the cache). */
    fun setPopularTags(tags: List<String>) {
        store.putString(KEY_POPULAR_TAGS, tags.joinToString("\n"))
        store.putLong(KEY_POPULAR_TAGS_AT, System.currentTimeMillis())
    }
}

/**
 * Where the home "Airs today" timeline sources its rows.
 *
 * GLOBAL is DECLARED here but ROUND 151 serves the schedule store's data for
 * BOTH values — the AniList API client has no global airing-schedule query
 * yet (`fetchAiringSchedule` is per-batch-of-ids), so the VM falls back to
 * the library schedule regardless of the toggle until that query lands.
 */
enum class HomeAirsSource(val value: String, val displayName: String) {
    LIBRARY("library", "Library"),
    GLOBAL("global", "Global"),
    ;

    companion object {
        fun fromValue(value: String): HomeAirsSource =
            entries.firstOrNull { it.value == value } ?: LIBRARY
    }
}

/**
 * ROUND 157 (WS-D1): the home's content mode — the same axis as the Airs
 * toggle, one level up. The CloudStream providers carry live-action series
 * AND movies; the AniList discovery carries anime. BOTH interleaves them.
 */
enum class HomeContentMode(val value: String, val displayName: String) {
    ANIME("anime", "Anime"),
    SERIES_MOVIES("series_movies", "Series & movies"),
    BOTH("both", "Both"),
    ;

    companion object {
        fun fromValue(value: String): HomeContentMode =
            entries.firstOrNull { it.value == value } ?: ANIME
    }
}
