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
 * CORE_RULES §20: logged with tag "Anikuta:Core:Preferences:Home".
 * CORE_RULES §23: reactive via StateFlow.
 */
class HomePreferences(
    private val store: PreferenceStore,
) {

    companion object {
        // CORE_RULES §20 tag when logging lands: "Anikuta:Core:Preferences:Home".
        private const val KEY_AIRS_SOURCE = "home_airs_source"
    }

    private val _airsSource = MutableStateFlow(getAirsSource())
    val airsSource: StateFlow<HomeAirsSource> = _airsSource.asStateFlow()

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
