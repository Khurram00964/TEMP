package com.confused.anikuta.core.preferences

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ROUND 165: the EXTENSION PRIORITY SYSTEM's toggle (the spec:
 * APP/ani-kuta/DOCUMENTATION/EXTENSION-PRIORITY-SYSTEM.md). ON by default —
 * the learned order applies everywhere (the pickers, the linking, the
 * resolver server order, the auto-select). OFF = the historical order (the
 * recording continues; the history survives the toggling).
 */
class ExtensionPriorityPreferences(private val store: PreferenceStore) {

    companion object {
        private const val KEY_ENABLED = "extension_priority_enabled"
    }

    var enabled: Boolean
        get() = store.getBoolean(KEY_ENABLED, true)
        set(value) = store.putBoolean(KEY_ENABLED, value)

    private val _enabled = MutableStateFlow(enabled)
    val enabledFlow: StateFlow<Boolean> = _enabled.asStateFlow()

    /** The Settings toggle write (the flow re-emits → the sort sites re-derive). */
    fun setEnabled(value: Boolean) {
        enabled = value
        _enabled.value = value
    }
}
