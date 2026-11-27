package com.confused.anikuta.core.trackermal

import kotlinx.serialization.SerialName
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
data class MalStartSeason(
    val year: Int? = null,
    val season: String? = null,
)

/**
 * ROUND 173 (D1-driven): the FULL MAL detail — the display-relevant subset
 * of the live-verified field set (MAL 22319). The enrichment merges this
 * into content_details' data axis (the synopsis/genres/year/episodes/
 * status/score the details page renders).
 */
@Serializable
data class MalDetail(
    val id: Int,
    val title: String? = null,
    val main_picture: MalMainPicture? = null,
    val synopsis: String? = null,
    val mean: Float? = null,
    val rank: Int? = null,
    val popularity: Int? = null,
    val num_episodes: Int? = null,
    val start_season: MalStartSeason? = null,
    val status: String? = null,
    val genres: List<MalGenre> = emptyList(),
    val themes: List<MalGenre> = emptyList(),
    val studios: List<MalGenre> = emptyList(),
    val average_episode_duration: Int? = null,
    val rating: String? = null,
    val background: String? = null,
    val alternative_titles: MalAlternativeTitles? = null,
)

@Serializable
data class MalGenre(
    val id: Int,
    val name: String,
)

@Serializable
data class MalAlternativeTitles(
    val synonyms: List<String> = emptyList(),
    val en: String? = null,
    val ja: String? = null,
)

@Serializable
data class MalAnime(
    val id: Int,
    val title: String? = null,
    val main_picture: MalMainPicture? = null,
    val num_episodes: Int? = null,
    val mean: Float? = null,
    // ROUND 172: the search rows' year (the unified link sheet's meta row).
    val start_season: MalStartSeason? = null,
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

// ── ROUND 179 (THE POPULATE): the /users/@me/animelist shape ──
// The animelist endpoint names the list state `list_status` while the
// per-anime endpoint (animeWithListStatus) uses `my_list_status` — a shared
// DTO with a SerialName remap would break the latter's parse. Dedicated
// node/row types keep both shapes exact.

/** The animelist node — MalAnime's fields with the `list_status` key. */
@Serializable
data class MalListAnime(
    val id: Int,
    val title: String? = null,
    val main_picture: MalMainPicture? = null,
    val num_episodes: Int? = null,
    val mean: Float? = null,
    val start_season: MalStartSeason? = null,
    @SerialName("list_status")
    val my_list_status: MalMyListStatus? = null,
)

@Serializable
data class MalListRow(
    val node: MalListAnime,
)

@Serializable
data class MalListResponse(
    val data: List<MalListRow> = emptyList(),
    val paging: MalPaging? = null,
)

/** The MAL list pagination envelope (paging.next = the full next URL). */
@Serializable
data class MalPaging(
    val next: String? = null,
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
