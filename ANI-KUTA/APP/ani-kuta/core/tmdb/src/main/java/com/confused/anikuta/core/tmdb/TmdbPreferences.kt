package com.confused.anikuta.core.tmdb

import com.confused.anikuta.core.preferences.PreferenceStore

/**
 * ROUND 163 (STEP-3): the TMDB source's user toggle (the Settings → Data
 * sources switch). ON by default (the key ships configured; the user turns a
 * source OFF when they don't want its content).
 */
class TmdbPreferences(private val store: PreferenceStore) {

    var enabled: Boolean
        get() = store.getBoolean(KEY_ENABLED, true)
        set(value) = store.putBoolean(KEY_ENABLED, value)

    /** The reactive read (the search screen's toggle-aware rendering). */
    val enabledFlow: kotlinx.coroutines.flow.Flow<Boolean> = store.booleanFlow(KEY_ENABLED, true)

    companion object {
        private const val KEY_ENABLED = "datasource_tmdb_enabled"
    }
}
