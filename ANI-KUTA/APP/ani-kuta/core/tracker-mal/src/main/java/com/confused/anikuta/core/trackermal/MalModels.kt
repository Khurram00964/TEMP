package com.confused.anikuta.core.trackermal

import kotlinx.serialization.Serializable

/**
 * ROUND 168 (the tracker redo, Phase C): the MAL DTOs (api.myanimelist.net/v2).
 * The shapes are hand-picked subsets — `ignoreUnknownKeys` handles the rest of
 * MAL's verbose responses.
 */

@Serializable
data class MalTokens(
    val access_token: String,
    val refresh_token: String,
    val expires_in: Long,
)

@Serializable
data class MalMainPicture(
    val medium: String? = null,
    val large: String? = null,
)

@Serializable
data class MalMyListStatus(
    val status: String? = null,
    val score: Int = 0,
    val num_episodes_watched: Int = 0,
    val num_times_rewatched: Int = 0,
    val is_rewatching: Boolean = false,
    val start_date: String? = null,
    val finish_date: String? = null,
    val updated_at: String? = null,
)

@Serializable
data class MalAnime(
    val id: Int,
    val title: String? = null,
    val main_picture: MalMainPicture? = null,
    val num_episodes: Int? = null,
    val mean: Float? = null,
    val my_list_status: MalMyListStatus? = null,
)

@Serializable
data class MalSearchResponse(
    val data: List<MalSearchRow> = emptyList(),
)

@Serializable
data class MalSearchRow(
    val node: MalAnime,
)

@Serializable
data class MalUser(
    val name: String,
)

/** GET /anime/ranking — the ranking rows wrap the anime node (Phase E defaults). */
@Serializable
data class MalRankingResponse(
    val data: List<MalRankingRow> = emptyList(),
)

@Serializable
data class MalRankingRow(
    val node: MalAnime,
)
