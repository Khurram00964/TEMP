package com.confused.anikuta.core.tvmaze

import com.confused.anikuta.core.preferences.PreferenceStore

/**
 * ROUND 163 (STEP-3): the TVmaze source's user toggle (the Settings → Data
 * sources switch). ON by default — the source is keyless and free.
 */
class TvmazePreferences(private val store: PreferenceStore) {

    var enabled: Boolean
        get() = store.getBoolean(KEY_ENABLED, true)
        set(value) = store.putBoolean(KEY_ENABLED, value)

    /** The reactive read (the search screen's toggle-aware rendering). */
    val enabledFlow: kotlinx.coroutines.flow.Flow<Boolean> = store.booleanFlow(KEY_ENABLED, true)

    companion object {
        private const val KEY_ENABLED = "datasource_tvmaze_enabled"
    }
}
