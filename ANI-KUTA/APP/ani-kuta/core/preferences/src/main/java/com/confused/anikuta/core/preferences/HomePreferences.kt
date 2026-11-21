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

        // ROUND 152: the popular-tags cache keys + the 7-day TTL.
        private const val KEY_POPULAR_TAGS = "popular_tags_json"
        private const val KEY_POPULAR_TAGS_AT = "popular_tags_fetched_at"

        /** The popular-tags cache TTL — 7 days, then a background refresh. */
        const val POPULAR_TAGS_TTL_MS = 7L * 24 * 60 * 60 * 1000

        // ── ROUND 164 (STEP-3d): the multi-select home content kinds ──
        private const val KEY_CONTENT_KINDS = "home_content_kinds"
    }

    private val _airsSource = MutableStateFlow(getAirsSource())
    val airsSource: StateFlow<HomeAirsSource> = _airsSource.asStateFlow()

    // ── ROUND 164 (STEP-3d): the MULTI-SELECT home content kinds ─────────────
    //
    // The user's correction to the reverted round's 3-way enum: the home shows
    // ANY COMBINATION of anime / movies / series / donghua — independent
    // toggles, not a mode cycle. Stored as one delimited string
    // ("anime,movies"); DEFAULT = anime only (the historical behavior — the
    // upgrade is invisible until the user opts into more).
    //
    // INSTANT APPLICATION: [contentKindsFlow] re-emits on every toggle and the
    // VM's flatMapLatest re-derives the merged feed without a screen reload.

    fun getContentKinds(): Set<HomeContentKind> {
        val raw = store.getString(KEY_CONTENT_KINDS, HomeContentKind.ANIME.id)
        val parsed = raw.split(',')
            .mapNotNull { id -> HomeContentKind.entries.firstOrNull { it.id == id.trim() } }
            .toSet()
        // Never empty — an empty home is worse than a fallback.
        return parsed.ifEmpty { setOf(HomeContentKind.ANIME) }
    }

    fun setContentKinds(kinds: Set<HomeContentKind>) {
        val safe = kinds.ifEmpty { setOf(HomeContentKind.ANIME) }
        store.putString(KEY_CONTENT_KINDS, safe.joinToString(",") { it.id })
        _contentKinds.value = safe
    }

    fun toggleContentKind(kind: HomeContentKind): Set<HomeContentKind> {
        val current = getContentKinds()
        val next = if (kind in current) {
            current - kind
        } else {
            current + kind
        }
        setContentKinds(next)
        return getContentKinds()
    }

    private val _contentKinds = MutableStateFlow(getContentKinds())
    val contentKindsFlow: StateFlow<Set<HomeContentKind>> = _contentKinds.asStateFlow()

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
 * ROUND 164 (STEP-3d): a home CONTENT KIND — one toggle of the multi-select
 * (the user's correction to the reverted round's 3-way mode cycle: ANY
 * combination is valid). The kinds merge into ONE feed; the layout stays
 * byte-identical, only the content changes.
 */
enum class HomeContentKind(val id: String, val displayName: String, val subtitle: String) {
    ANIME("anime", "Anime", "Your AniList feed — trending, moods, genres"),
    MOVIES("movies", "Movies", "TMDB — the day's trending + popular films"),
    SERIES("series", "Series", "TMDB shows — trending + popular series"),
    DONGHUA("donghua", "Donghua", "Chinese animation — AniList, keyless"),
    ;

    companion object {
        fun fromId(id: String): HomeContentKind? = entries.firstOrNull { it.id == id }
    }
}
