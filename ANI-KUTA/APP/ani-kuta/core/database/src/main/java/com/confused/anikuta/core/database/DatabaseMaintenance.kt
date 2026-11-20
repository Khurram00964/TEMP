package com.confused.anikuta.core.database

import com.confused.anikuta.core.common.Logger

/**
 * ROUND 163 (STEP-1): the growth-bound maintenance pass — the reverted
 * round-157 WS-A bounds, re-landed lean.
 *
 * The three append-heavy/cache surfaces had NO prunes on main:
 * - `watch_session` — one row per continuous watching streak, forever
 *   (the Profile's charts read at most the last months; the all-time
 *   time-DNA reads it all, so the bound is generous at 400 days).
 * - `data_cache_episode` — per-episode metadata rows, never reclaimed
 *   (the library's audio pills read them, so the window is 180 days —
 *   the reverted round's own policy; a live entry refetches on demand).
 * - `browse_cache` — expired rows were read-side misses but lived forever
 *   (stale sections for abandoned keys). The sweep reclaims their storage.
 *
 * NOT pruned (deliberate): `activity_event` (365d — already pruned by
 * ActivityTracker), `watch_progress`/`track_entry`/`content_tracking_state`
 * (the user's data — never silently deleted), `episode_schedule`/
 * `episode_update`/`notification_sent` (their own prunes exist).
 *
 * Run ONCE per day (the caller throttles with a preference stamp — the
 * app-open path), off the main thread. Every prune is independent +
 * failure-isolated: a failing sweep logs and lets the rest run.
 */
object DatabaseMaintenance {

    /** The app-level bounds (days). The caller derives the cutoffs. */
    const val WATCH_SESSION_RETENTION_DAYS = 400L
    const val EPISODE_METADATA_RETENTION_DAYS = 180L

    private const val TAG = "Anikuta:Core:Database"

    /**
     * Runs all three sweeps. Never throws — every sweep is runCatching-wrapped
     * so one failing sweep can't block the others (and the app-open caller
     * can't be taken down by a prune).
     */
    fun run(database: AnikutaDatabase, nowMs: Long = System.currentTimeMillis()) {
        val sessionCutoff = nowMs - WATCH_SESSION_RETENTION_DAYS * 24L * 60L * 60L * 1000L
        val metadataCutoff = nowMs - EPISODE_METADATA_RETENTION_DAYS * 24L * 60L * 60L * 1000L

        runCatching {
            database.watchQueries.deleteWatchSessionsOlderThan(sessionCutoff)
        }.onFailure { e -> Logger.w(TAG) { "watch_session prune failed: ${e.message}" } }

        runCatching {
            database.dataCacheQueries.deleteEpisodeMetadataOlderThan(metadataCutoff)
        }.onFailure { e -> Logger.w(TAG) { "data_cache_episode prune failed: ${e.message}" } }

        runCatching {
            database.dataCacheQueries.deleteExpiredBrowseCache(nowMs)
        }.onFailure { e -> Logger.w(TAG) { "browse_cache sweep failed: ${e.message}" } }

        Logger.d(TAG) { "maintenance — sweeps done (session cutoff $sessionCutoff, metadata cutoff $metadataCutoff)" }
    }
}
