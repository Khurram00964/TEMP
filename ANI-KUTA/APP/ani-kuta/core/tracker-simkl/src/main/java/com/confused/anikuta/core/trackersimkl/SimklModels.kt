package com.confused.anikuta.core.trackersimkl

import kotlinx.serialization.Serializable

/**
 * ROUND 163 (STEP-2): Simkl API DTOs (https://api.simkl.org). Only the fields
 * the app reads/writes are declared; every response field is nullable-tolerant
 * (Simkl's payloads vary per content kind). `ignoreUnknownKeys` everywhere —
 * the API grows fields freely.
 */

/** The OAuth token pair (AUTH V2: access 7 days, refresh 180 days sliding). */
data class SimklTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
)

/** GET /users/me — the account identity (the Settings card's display). */
@Serializable
data class SimklUser(
    val user: SimklUserIdentity? = null,
)

@Serializable
data class SimklUserIdentity(
    val name: String? = null,
    val joined_at: String? = null,
)

/**
 * POST /sync/add-to-list + /sync/history + /sync/ratings — the write bodies.
 * One array per kind; Simkl resolves ids server-side (send whichever external
 * id we hold: anilist for anime, tmdb for movies/TV, simkl when known).
 */
@Serializable
data class SimklSyncBody(
    val to: String? = null, // add-to-list only: watching/plantowatch/completed/hold/dropped
    val movies: List<SimklSyncItem> = emptyList(),
    val shows: List<SimklSyncItem> = emptyList(),
    val anime: List<SimklSyncItem> = emptyList(),
)

@Serializable
data class SimklSyncItem(
    val ids: Map<String, kotlinx.serialization.json.JsonPrimitive>,
    val to: String? = null, // per-item status override (shows/anime add-to-list)
    val watched_at: String? = null, // history: ISO 8601
    val rating: Int? = null, // ratings: 1-10
    val seasons: List<SimklSeason>? = null, // shows/anime episode marks
)

@Serializable
data class SimklSeason(
    val number: Int,
    val episodes: List<SimklEpisodeMark> = emptyList(),
)

@Serializable
data class SimklEpisodeMark(
    val number: Int,
    val watched_at: String? = null,
)

/**
 * GET /redirect?tmdb=|anilist=|imdb= — the id bridge. Returns the resolved
 * entry (or 404): `simkl` id + the honest `type` ("movie"/"show"/"anime") +
 * the other known ids. THE bridge between the app's AniList side and the
 * Simkl writes; also how a TMDB movie/TV id resolves before its sync.
 */
@Serializable
data class SimklIdResolution(
    val simkl: Long? = null,
    val type: String? = null, // movie | show | anime (Simkl's honest answer)
    val tmdb: Long? = null,
    val imdb: String? = null,
    val anilist: Long? = null,
    val mal: Long? = null,
)

/**
 * GET /search/{movie|tv|anime|all}?q= — the linking search (the TrackSheet /
 * the Settings search flows). Rows carry the ids + the display fields.
 */
@Serializable
data class SimklSearchResponse(
    val movie: List<SimklSearchItem> = emptyList(),
    val tv: List<SimklSearchItem> = emptyList(),
    val anime: List<SimklSearchItem> = emptyList(),
)

@Serializable
data class SimklSearchItem(
    val simkl_id: Long? = null,
    val title: String? = null,
    val poster_url: String? = null,
    val year: Int? = null,
    val ids: SimklIdBundle? = null,
)

// ── ROUND 169: the LIVE-VERIFIED media-item shape (the /trending responses;
//    the /search responses with the user token carry the same fields) —
//    the ids live at ids.simkl_id, the poster is a simkl.in PATH (built at
//    the mapper: https://simkl.in/posters/{path}_m.jpg), the date is
//    MM/DD/YYYY. The old SimklSearchItem shape never parsed these → every
//    row mapped remoteId = -1 → the duplicate-key crash (the v1.1.104
//    device report).
@Serializable
data class SimklMediaItem(
    val title: String? = null,
    val poster: String? = null,
    val ids: SimklMediaIds? = null,
    val release_date: String? = null,
    val overview: String? = null,
    val total_episodes: Int? = null,
    val ratings: SimklRatings? = null,
)

@Serializable
data class SimklMediaIds(
    val simkl_id: Long? = null,
    val slug: String? = null,
)

@Serializable
data class SimklRatings(
    val simkl: SimklRating? = null,
)

@Serializable
data class SimklRating(
    val rating: Double? = null,
)

@Serializable
data class SimklIdBundle(
    val simkl: Long? = null,
    val tmdb: Long? = null,
    val imdb: String? = null,
    val anilist: Long? = null,
    val mal: Long? = null,
)

/**
 * GET /sync/all-items/{movie|tv|anime}/{status} — the library reads (the
 * "Populate Library"-style flows + the reverse sync). Each row is a media
 * object with the user's list metadata inside.
 */
@Serializable
data class SimklLibraryItem(
    val ids: SimklIdBundle? = null,
    val title: String? = null,
    val poster_url: String? = null,
    val last_watched_at: String? = null,
    val user_rating: Int? = null,
    val status: String? = null,
    val movie_type: String? = null,
    val anime_type: String? = null,
    val seasons: List<SimklLibrarySeason> = emptyList(),
)

@Serializable
data class SimklLibrarySeason(
    val number: Int,
    val episodes: List<SimklLibraryEpisode> = emptyList(),
)

@Serializable
data class SimklLibraryEpisode(
    val number: Int,
    val watched_at: String? = null,
)
