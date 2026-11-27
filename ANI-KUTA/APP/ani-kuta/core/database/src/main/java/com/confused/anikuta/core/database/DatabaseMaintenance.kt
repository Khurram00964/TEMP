package com.confused.anikuta.core.database

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
 * (the user's data — never silently deleted), `episode_update`/
 * `notification_sent` (their own prunes exist).
 * ROUND 179 (S4 — the audit): `episode_schedule` JOINED the sweeps — the
 * doc previously claimed "their own prunes exist" for it, but
 * ScheduleStore.deleteOldSchedule had ZERO production callers and aired
 * rows accumulated forever. The cutoff matches the metadata window (180d
 * past the scheduled air time; upcoming rows are never touched).
 *
 * Run ONCE per day (the caller throttles with a preference stamp — the
 * app-open path), off the main thread, with `nowMs` passed IN (this module
 * stays dependency-free: no Logger, no clock — the caller owns both).
 */
object DatabaseMaintenance {

    /** The app-level bounds (days). The caller derives the cutoffs. */
    const val WATCH_SESSION_RETENTION_DAYS = 400L
    const val EPISODE_METADATA_RETENTION_DAYS = 180L
    const val SCHEDULE_RETENTION_DAYS = 180L

    /**
     * Runs all four sweeps. Never throws — every sweep is runCatching-wrapped
     * so one failing sweep can't block the others (and the app-open caller
     * can't be taken down by a prune). The caller logs via the central Logger.
     */
    fun run(database: AnikutaDatabase, nowMs: Long): MaintenanceResult {
        val sessionCutoff = nowMs - WATCH_SESSION_RETENTION_DAYS * 24L * 60L * 60L * 1000L
        val metadataCutoff = nowMs - EPISODE_METADATA_RETENTION_DAYS * 24L * 60L * 60L * 1000L
        val scheduleCutoff = nowMs - SCHEDULE_RETENTION_DAYS * 24L * 60L * 60L * 1000L

        val sessions = runCatching {
            database.watchQueries.deleteWatchSessionsOlderThan(sessionCutoff)
            true
        }.getOrDefault(false)

        val metadata = runCatching {
            database.dataCacheQueries.deleteEpisodeMetadataOlderThan(metadataCutoff)
            true
        }.getOrDefault(false)

        val browse = runCatching {
            database.dataCacheQueries.deleteExpiredBrowseCache(nowMs)
            true
        }.getOrDefault(false)

        val schedule = runCatching {
            database.episodeScheduleQueries.deleteOldSchedule(scheduleCutoff)
            true
        }.getOrDefault(false)

        return MaintenanceResult(sessions, metadata, browse, schedule)
    }

    /** Which sweeps completed (the caller logs; failure-isolation per sweep). */
    data class MaintenanceResult(
        val watchSessionsSwept: Boolean,
        val episodeMetadataSwept: Boolean,
        val browseCacheSwept: Boolean,
        val scheduleSwept: Boolean = false,
    )
}
