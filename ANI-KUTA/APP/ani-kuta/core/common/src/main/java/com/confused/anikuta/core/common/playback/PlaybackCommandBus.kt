package com.confused.anikuta.core.common.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ROUND 159 (WS-BG): the bridge between the player screens and the
 * background-playback service. The screens REGISTER the current engine's
 * controls when an episode plays and UNREGISTER on dispose; the service
 * reads them for the media notification and routes its actions back.
 *
 * Lives in core/common because BOTH player stacks depend on it (cs-watch
 * does not depend on core/player) and the service must be stack-agnostic.
 *
 * Threading: the flows are snapshot-state backed — safe to write from any
 * thread; the service collects.
 */
object PlaybackCommandBus {

    /**
     * The current episode's controls. Null = nothing playing (the service
     * stops itself).
     */
    val controls = MutableStateFlow<PlaybackControls?>(null)

    /**
     * Bumped by the screens whenever the playback state changes (play/pause,
     * episode switch) — the service rebuilds the notification on it.
     */
    val version = MutableStateFlow(0)

    fun notifyChanged() {
        version.value += 1
    }

    /**
     * Whether the app is FOREGROUND (the service is redundant — the user
     * sees the player). Written by MainActivity; read by the service.
     */
    val appForeground = MutableStateFlow(true)

    fun setForeground(foreground: Boolean) {
        appForeground.value = foreground
    }
}

/**
 * The controls one playing episode exposes to the notification. Registered
 * by the player screens (MPV and CS alike) while their episode lives.
 */
interface PlaybackControls {
    /** Human-readable title line (the anime name). */
    val title: String

    /** The second line (e.g. "Episode 5 — The Train"). */
    val subtitle: String

    /** The poster/cover URL for the notification art (null = the app icon). */
    val posterUrl: String?

    /** Whether playback is RUNNING right now (the pause icon shows). */
    val isPlaying: Boolean

    fun play()

    fun pause()

    /** Seek by [deltaSeconds] (±10 typical). */
    fun seekRelative(deltaSeconds: Int)

    /**
     * Start the NEXT episode (when one exists). Returns false when this is
     * the last episode (the notification hides the action).
     */
    fun playNext(): Boolean
}
