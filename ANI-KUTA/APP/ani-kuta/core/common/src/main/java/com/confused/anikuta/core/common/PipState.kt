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

    // ROUND 158 (V1): the playing video's size — the PiP window follows the
    // CONTENT's aspect instead of a hardcoded 16:9 (portrait/4:3 sources were
    // letterboxed inside a 16:9 hole). The screens write it; 0/0 = unknown →
    // the 16:9 fallback.
    val videoWidth = MutableStateFlow(0)
    val videoHeight = MutableStateFlow(0)

    fun setVideoSize(width: Int, height: Int) {
        videoWidth.value = width
        videoHeight.value = height
    }

    fun requestEnter(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        var current: Context = context
        while (current is ContextWrapper) {
            if (current is Activity) break
            current = current.baseContext
        }
        val activity = current as? Activity ?: return
        val w = videoWidth.value
        val h = videoHeight.value
        val params = if (w > 0 && h > 0) {
            PictureInPictureParams.Builder()
                .setAspectRatio(Rational(w, h))
                .build()
        } else {
            PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
        }
        // A video aspect outside the system's PiP range (2.39:1 .. 1:2.39)
        // throws — retry the neutral 16:9 so the entry never dead-ends.
        runCatching { activity.enterPictureInPictureMode(params) }
            .onFailure {
                if (w > 0 && h > 0) {
                    runCatching {
                        activity.enterPictureInPictureMode(
                            PictureInPictureParams.Builder()
                                .setAspectRatio(Rational(16, 9))
                                .build(),
                        )
                    }
                }
            }
    }
}
