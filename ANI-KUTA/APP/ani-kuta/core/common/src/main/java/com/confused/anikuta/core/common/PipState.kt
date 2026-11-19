package com.confused.anikuta.core.common

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.util.Rational
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ROUND 157 (WS-E3): the process-wide picture-in-picture state + the entry
 * helper. An object (not DI) because both player stacks reach it without
 * graph coupling and the activity is always a process singleton.
 *
 * - [eligible]: the screens set it while fullscreen + playing — the activity's
 *   onUserLeaveHint auto-enters PiP for them.
 * - [isInPip]: the activity writes it on every mode change; the screens hide
 *   their controls while it is true (the surface alone + tap-to-expand).
 * - [requestEnter]: the tray button's entry (finds the activity behind any
 *   ContextWrapper — the Compose LocalContext is often a wrapper).
 */
object PipState {
    private val _isInPip = MutableStateFlow(false)
    val isInPip: StateFlow<Boolean> = _isInPip.asStateFlow()

    /** The activity's mode-changed write (StateFlow is read-only outside). */
    fun setInPip(value: Boolean) {
        _isInPip.value = value
    }

    val eligible = MutableStateFlow(false)

    fun requestEnter(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        var current: Context = context
        while (current is ContextWrapper) {
            if (current is Activity) break
            current = current.baseContext
        }
        val activity = current as? Activity ?: return
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
        runCatching { activity.enterPictureInPictureMode(params) }
    }
}
