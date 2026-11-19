package com.confused.anikuta.core.trackerapi

/**
 * Supported tracker types.
 *
 * The app's internal tracking system (`:core:activity-tracker`) is PRIMARY —
 * it records everything the user does. External trackers (AniList, MAL) are
 * SECONDARY — they receive data relayed from the internal tracker.
 *
 * Adding a new tracker = implement the [Tracker] interface + register in Koin.
 */
enum class TrackerType(val id: String) {
    ANILIST("anilist"),
    MAL("mal"),           // future
    TRAKT("trakt"),       // ROUND 157 (WS-C): the live-action tracker
    SHIKIMORI("shikimori"), // future
}

/**
 * Watch status for tracker sync.
 *
 * D-242: The [value] strings are the APP's canonical status names. Each
 * tracker implementation maps these to its own API enum at the boundary
 * (e.g. AniList uses `CURRENT` for [WATCHING], `PLANNING` for [PLAN_TO_WATCH]
 * — see `AniListStatusMapper` for the conversion).
 */
enum class TrackStatus(val value: String) {
    WATCHING("WATCHING"),
    COMPLETED("COMPLETED"),
    PAUSED("PAUSED"),
    DROPPED("DROPPED"),
    PLAN_TO_WATCH("PLAN_TO_WATCH"),
    REWATCHING("REWATCHING"),
}

/**
 * A track entry — the data synced to/from an external tracker.
 *
 * @param contentKey The app's mainId (stable UUID). Used to link the track
 *   entry to the local content identity.
 * @param trackerId The tracker's ID for this content (e.g. AniList anime ID).
 * @param status Watch status.
 * @param score User's score (0-100, or null if not rated). AniList-native scale.
 * @param progress Episodes watched.
 * @param totalEpisodes Total episodes (from tracker, nullable).
 * @param listId The tracker's list entry ID (AniList's MediaList.id). Needed
 *   for update/delete operations. Null for new entries (not yet synced).
 * @param startedAt When the user started watching (epoch millis, nullable).
 * @param completedAt When the user finished watching (epoch millis, nullable).
 * @param updatedAt When this entry was last synced (epoch millis).
 */
data class TrackEntry(
    val contentKey: String,
    val trackerId: Int,
    val status: TrackStatus = TrackStatus.WATCHING,
    val score: Int? = null,
    val progress: Int = 0,
    val totalEpisodes: Int? = null,
    val listId: Int? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    // ROUND 157 (WS-C): the kind + the movie semantics — additive with
    // defaults, so every AniList call site keeps compiling untouched. SERIES
    // trackers read [progress]; MOVIE targets read [timesWatched] (a movie
    // has no episode ladder — "watched twice" is the honest progress).
    val kind: TrackKind = TrackKind.SERIES,
    val timesWatched: Int = 0,
)

/** The content kind a track entry describes (ROUND 157, WS-C). */
enum class TrackKind { SERIES, MOVIE }

/**
 * Login state for a tracker.
 */
sealed interface TrackerLoginState {
    data object LoggedOut : TrackerLoginState
    data class LoggedIn(val username: String) : TrackerLoginState
    data class Error(val message: String) : TrackerLoginState
}

/**
 * Sync state for a tracker.
 */
sealed interface TrackerSyncState {
    data object Idle : TrackerSyncState
    data object Syncing : TrackerSyncState
    data class Success(val syncedAt: Long) : TrackerSyncState
    data class Failed(val message: String) : TrackerSyncState
}

/**
 * ROUND 157 (WS-C): the per-tracker ID RESOLUTION seam — the missing half of
 * the translation layer. TrackSyncManager used to hardcode `details.anilistId`
 * as the tracker ID for EVERY tracker; a second tracker needs its OWN id space
 * (Trakt/TMDB ids, MAL ids, ...). Implementations register per [TrackerType] in
 * Koin; the sync manager consults the resolver for the tracker's type and
 * falls back to the AniList id only for ANILIST — byte-identical behavior for
 * the existing integration, a pluggable seam for every new one.
 *
 * The FIRST implementation (core/tracker-trakt) resolves via the extension
 * channel's stored external IDs (ContentDetails.tmdbId / extExtras.imdbId —
 * captured by the WS-B2 bridge work) with a TMDB title+year search fallback.
 */
interface TrackerLinkResolver {
    /** The tracker type this resolver serves. */
    val trackerType: TrackerType

    /**
     * Resolve the tracker's external ID for the content.
     * @return The external id, or null when unknown (the sync skips).
     */
    suspend fun resolve(mainId: String): Int?
}
