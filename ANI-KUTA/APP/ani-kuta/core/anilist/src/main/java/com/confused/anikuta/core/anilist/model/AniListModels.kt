package com.confused.anikuta.core.anilist.model

import kotlinx.serialization.Serializable

@Serializable
data class AniListAnime(
    val id: Int,
    val title: AnimeTitle = AnimeTitle(),
    val coverImage: CoverImage = CoverImage(),
    val averageScore: Int? = null,
    val episodes: Int? = null,
    val description: String? = null,
    val bannerImage: String? = null,
    val genres: List<String>? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val status: String? = null,
    val idMal: Int? = null,
    // D-235: Next airing episode — fetched on-demand when the user opens an anime.
    // AniList returns airingAt as Unix SECONDS. Convert to millis (* 1000) when storing.
    val nextAiringEpisode: AniListAiringEpisode? = null,
) {
    val displayName: String get() = title.english ?: title.romaji ?: "Unknown"
    val coverUrl: String? get() = coverImage.extraLarge ?: coverImage.large
}

@Serializable
data class AnimeTitle(
    val romaji: String? = null,
    val english: String? = null,
)

@Serializable
data class CoverImage(
    val large: String? = null,
    val extraLarge: String? = null,
)

/**
 * D-235: Next airing episode data from AniList.
 * @param airingAt Unix SECONDS — convert to millis (* 1000) when storing.
 * @param episode The episode number that will air next.
 * @param timeUntilAiring Seconds until the episode airs (ROUND 152 — the
 *   global airing schedule sorts its RELEASING feed client-side with it).
 */
@Serializable
data class AniListAiringEpisode(
    val airingAt: Long,
    val episode: Int,
    val timeUntilAiring: Long? = null,
)

// Response wrappers (match AniList JSON structure)
@Serializable
data class TrendingResponse(val data: TrendingData)

@Serializable
data class TrendingData(val Page: MediaPage)

@Serializable
data class MediaPage(val media: List<AniListAnime>)

@Serializable
data class DetailsResponse(val data: DetailsData)

@Serializable
data class DetailsData(val Media: AniListAnime)

// Search response (reuses TrendingData/Page shape — AniList returns the same Media list)
@Serializable
data class SearchResponse(val data: TrendingData)

// ── Streaming episodes response ──
@Serializable
data class AniListStreamingEpisode(
    val title: String? = null,
    val thumbnail: String? = null,
)

@Serializable
data class StreamingEpisodesResponse(val data: StreamingEpisodesData)

@Serializable
data class StreamingEpisodesData(val Media: StreamingEpisodesMedia)

@Serializable
data class StreamingEpisodesMedia(
    val streamingEpisodes: List<AniListStreamingEpisode>? = null,
)

// ── ROUND 152: the genre/tag/category + global-airing additions ─────────────

/** AniList Page.pageInfo — the infinite grids' hasNextPage flag rides here. */
@Serializable
data class AniListPageInfo(
    val total: Int? = null,
    val currentPage: Int? = null,
    val hasNextPage: Boolean = false,
)

/**
 * A paged Page.media response (genre/tag browse): the same Media shape as
 * [TrendingResponse] plus pageInfo, so the paged screens know when to stop.
 */
@Serializable
data class PagedMediaResponse(val data: PagedMediaData)

@Serializable
data class PagedMediaData(val Page: PagedMediaPage)

@Serializable
data class PagedMediaPage(
    val pageInfo: AniListPageInfo? = null,
    val media: List<AniListAnime> = emptyList(),
)

/**
 * One decoded media page + whether more pages follow. Plain result (not on
 * the wire) — the genre/tag screens' infinite pagination contract.
 */
data class AniListMediaPage(
    val media: List<AniListAnime>,
    val hasNextPage: Boolean,
)

/** One AniList media tag (name + the adult flags the category filter needs). */
@Serializable
data class AniListTag(
    val name: String,
    val rank: Int? = null,
    val isAdult: Boolean? = null,
    val isAdultSpoiler: Boolean? = null,
)

/** Response of the ONE query that returns GenreCollection + the top-50 tags. */
@Serializable
data class BrowseCategoriesResponse(val data: BrowseCategoriesData)

@Serializable
data class BrowseCategoriesData(
    val GenreCollection: List<String> = emptyList(),
    val Page: TagUsagePage? = null,
)

@Serializable
data class TagUsagePage(val media: List<TaggedMedia> = emptyList())

@Serializable
data class TaggedMedia(val tags: List<AniListTag> = emptyList())

/**
 * The aggregated "All genres" index payload — AniList's genre collection
 * (Hentai pinned last, per the handoff) + the 30 most common non-adult tags
 * across the top-50 popular anime (adult + descriptive meta-tags filtered).
 */
data class AniListBrowseCategories(
    val genres: List<String>,
    val tags: List<String>,
)
