package com.confused.anikuta.core.anilist.api

import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.anilist.model.AniListBrowseCategories
import com.confused.anikuta.core.anilist.model.AniListStreamingEpisode
import com.confused.anikuta.core.anilist.model.BrowseCategoriesResponse
import com.confused.anikuta.core.anilist.model.DetailsResponse
import com.confused.anikuta.core.anilist.model.AniListMediaPage
import com.confused.anikuta.core.anilist.model.PagedMediaResponse
import com.confused.anikuta.core.anilist.model.SearchResponse
import com.confused.anikuta.core.anilist.model.StreamingEpisodesResponse
import com.confused.anikuta.core.anilist.model.TrendingResponse
import com.confused.anikuta.core.common.DispatcherProvider
import com.confused.anikuta.core.common.Logger
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale

/**
 * AniList GraphQL API client.
 * Ponytail: raw POST + kotlinx.serialization. No Apollo client.
 */
class AniListApi(
    private val httpClient: OkHttpClient,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
) {
    private val endpoint = "https://graphql.anilist.co"
    private val jsonMediaType = "application/json".toMediaType()

    suspend fun fetchTrending(page: Int = 1, perPage: Int = 20): List<AniListAnime> =
        withContext(dispatchers.io) {
            val query = """
                {
                    Page(page: $page, perPage: $perPage) {
                        media(type: ANIME, sort: TRENDING_DESC) {
                            id
                            title { romaji english }
                            coverImage { large extraLarge }
                            averageScore
                            episodes
                        }
                    }
                }
            """.trimIndent()

            Logger.d("Anikuta:Core:AniList") { "fetchTrending(page=$page)" }

            val response = executeQuery(query)
            json.decodeFromString<TrendingResponse>(response).data.Page.media
        }

    /**
     * D-249: Browse-section fetch — sorted anime WITHOUT a search term (browsing).
     * Same shape as fetchTrending but parameterized by sort + banner field for the
     * hero. Used for "Popular" (POPULARITY_DESC) and "Top Rated" (SCORE_DESC).
     */
    suspend fun fetchBrowseSection(
        sort: String,
        page: Int = 1,
        perPage: Int = 20,
    ): List<AniListAnime> = withContext(dispatchers.io) {
        val query = """
            {
                Page(page: $page, perPage: $perPage) {
                    media(type: ANIME, sort: $sort) {
                        id
                        title { romaji english }
                        coverImage { large extraLarge }
                        bannerImage
                        averageScore
                        episodes
                        seasonYear
                        genres
                        status
                    }
                }
            }
        """.trimIndent()

        Logger.d("Anikuta:Core:AniList") { "fetchBrowseSection(sort=$sort, page=$page)" }

        val response = executeQuery(query)
        json.decodeFromString<TrendingResponse>(response).data.Page.media
    }

    /**
     * ROUND 152: one page of a genre's most popular titles (the home's
     * suggestion-field cover pool + the genre browse grid). Same Media shape
     * as [fetchBrowseSection]; pageInfo.hasNextPage drives the grid's infinite
     * pagination. Genre names are passed as GraphQL variables (escaped, the
     * [searchAnime] pattern).
     */
    suspend fun fetchByGenre(
        genre: String,
        page: Int = 1,
        perPage: Int = 24,
    ): AniListMediaPage = withContext(dispatchers.io) {
        fetchCategoryPage("genre_in", genre, page, perPage)
    }

    /**
     * ROUND 152: one page of a tag's most popular titles (`tag_in`) — the
     * "Popular tags" half of the category index opens the same paged grid.
     */
    suspend fun fetchByTag(
        tag: String,
        page: Int = 1,
        perPage: Int = 24,
    ): AniListMediaPage = withContext(dispatchers.io) {
        fetchCategoryPage("tag_in", tag, page, perPage)
    }

    /** The shared genre_in / tag_in paged query body (the lib/anilist.ts fetchTitles shape). */
    private suspend fun fetchCategoryPage(
        filter: String,
        name: String,
        page: Int,
        perPage: Int,
    ): AniListMediaPage = withContext(dispatchers.io) {
        val gqlQuery = """
            query (${'$'}name: String, ${'$'}page: Int, ${'$'}perPage: Int) {
                Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                    pageInfo { hasNextPage }
                    media(type: ANIME, $filter: [${'$'}name], sort: POPULARITY_DESC) {
                        id
                        title { romaji english }
                        coverImage { large extraLarge }
                        bannerImage
                        averageScore
                        episodes
                        seasonYear
                        genres
                        status
                    }
                }
            }
        """.trimIndent()

        val variables = buildJsonObject {
            put("name", name)
            put("page", page)
            put("perPage", perPage)
        }

        Logger.d("Anikuta:Core:AniList") { "fetchCategoryPage($filter='$name', page=$page)" }

        val response = executeQueryWithVariables(gqlQuery, variables)
        val parsed = json.decodeFromString<PagedMediaResponse>(response)
        val media = parsed.data.Page.media
        AniListMediaPage(
            media = media,
            hasNextPage = parsed.data.Page.pageInfo?.hasNextPage ?: (media.size >= perPage),
        )
    }

    /**
     * ROUND 152: the "All genres" index payload — ONE round trip returns
     * AniList's GenreCollection AND the top-50 popular anime's tags. The tags
     * are aggregated by usage count (AniList exposes no tag-popularity field),
     * dropping adult tags, the descriptive META_TAGS set, and tags that
     * duplicate a genre; the top 30 survive. Hentai stays in the genre list
     * but is pinned to the very bottom (the handoff's GenresScreen rule).
     */
    suspend fun fetchPopularTags(): AniListBrowseCategories = withContext(dispatchers.io) {
        val query = """
            query {
                GenreCollection
                Page(page: 1, perPage: 50) {
                    media(type: ANIME, sort: POPULARITY_DESC) {
                        tags { name rank isAdult isAdultSpoiler }
                    }
                }
            }
        """.trimIndent()

        Logger.d("Anikuta:Core:AniList") { "fetchPopularTags()" }

        val response = executeQuery(query)
        val parsed = json.decodeFromString<BrowseCategoriesResponse>(response)
        val genres = parsed.data.GenreCollection
        val genreNames = genres.mapTo(HashSet()) { it.lowercase(Locale.US) }

        val counts = LinkedHashMap<String, Int>()
        for (media in parsed.data.Page?.media.orEmpty()) {
            for (tag in media.tags) {
                if (tag.isAdult == true || tag.isAdultSpoiler == true) continue
                if (tag.name in META_TAGS) continue
                if (tag.name.lowercase(Locale.US) in genreNames) continue
                counts.merge(tag.name, 1, Int::plus)
            }
        }
        val tags = counts.entries
            .sortedByDescending { it.value }
            .take(CATEGORY_TAG_COUNT)
            .map { it.key }

        AniListBrowseCategories(
            genres = genres.filter { it != "Hentai" } + genres.filter { it == "Hentai" },
            tags = tags,
        )
    }

    /**
     * ROUND 152: the GLOBAL "Airs today" feed — AniList's RELEASING shows
     * (their nextAiringEpisode carries the only airing clock the app gets
     * without a schedule query for ids we don't have). Sorted client-side by
     * the next episode's airingAt; the home's GLOBAL branch composes its
     * 2-released + next + 3-after rows from this list. Same Media shape as
     * [fetchBrowseSection] with the airing fields added.
     */
    suspend fun fetchGlobalAiringSchedule(perPage: Int = 50): List<AniListAnime> =
        withContext(dispatchers.io) {
            val query = """
                {
                    Page(page: 1, perPage: $perPage) {
                        media(type: ANIME, status: RELEASING, sort: POPULARITY_DESC) {
                            id
                            title { romaji english }
                            coverImage { large extraLarge }
                            bannerImage
                            averageScore
                            episodes
                            seasonYear
                            genres
                            status
                            nextAiringEpisode { airingAt episode timeUntilAiring }
                        }
                    }
                }
            """.trimIndent()

            Logger.d("Anikuta:Core:AniList") { "fetchGlobalAiringSchedule(perPage=$perPage)" }

            val response = executeQuery(query)
            json.decodeFromString<TrendingResponse>(response).data.Page.media
                .filter { it.nextAiringEpisode != null }
                .sortedBy { it.nextAiringEpisode?.airingAt ?: Long.MAX_VALUE }
        }

    suspend fun fetchAnimeDetails(id: Int): AniListAnime =
        withContext(dispatchers.io) {
            val query = """
                {
                    Media(id: $id, type: ANIME) {
                        id
                        idMal
                        title { romaji english }
                        coverImage { large extraLarge }
                        bannerImage
                        description
                        averageScore
                        episodes
                        genres
                        season
                        seasonYear
                        status
                        nextAiringEpisode { airingAt episode }
                    }
                }
            """.trimIndent()

            Logger.d("Anikuta:Core:AniList") { "fetchAnimeDetails(id=$id)" }

            val response = executeQuery(query)
            json.decodeFromString<DetailsResponse>(response).data.Media
        }

    /**
     * Search anime on AniList by query string.
     *
     * @param query Search term (matched against romaji/english/native titles).
     * @param page 1-indexed page number for pagination.
     * @param perPage Results per page (default 20).
     * @param sort AniList sort enum string (e.g. "POPULARITY_DESC", "SCORE_DESC",
     *   "START_DATE_DESC", "TITLE_ROMAJI", "TRENDING_DESC", "FAVOURITES_DESC").
     */
    suspend fun searchAnime(
        query: String,
        page: Int = 1,
        perPage: Int = 20,
        sort: String = "POPULARITY_DESC",
    ): List<AniListAnime> = withContext(dispatchers.io) {
        // GraphQL variables — passed via the variables field so the user query
        // is properly escaped (no string interpolation injection risk).
        val gqlQuery = """
            query (${'$'}search: String, ${'$'}page: Int, ${'$'}perPage: Int, ${'$'}sort: [MediaSort]) {
                Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                    media(type: ANIME, search: ${'$'}search, sort: ${'$'}sort) {
                        id
                        title { romaji english }
                        coverImage { large extraLarge }
                        averageScore
                        episodes
                        seasonYear
                    }
                }
            }
        """.trimIndent()

        val variables = buildJsonObject {
            put("search", query)
            put("page", page)
            put("perPage", perPage)
            put("sort", sort)
        }

        Logger.d("Anikuta:Core:AniList") { "searchAnime(q='$query', page=$page, sort=$sort)" }

        val response = executeQueryWithVariables(gqlQuery, variables)
        json.decodeFromString<SearchResponse>(response).data.Page.media
    }

    private fun executeQuery(query: String): String {
        val requestBody = buildJsonObject {
            put("query", query)
        }.toString().toRequestBody(jsonMediaType)

        return executeRequest(requestBody)
    }

    /**
     * Fetch streaming episode metadata (title + thumbnail) from AniList.
     *
     * AniList's `streamingEpisodes` field provides per-episode titles + thumbnails
     * from streaming services. This is the simplest source — no authentication
     * needed, just the AniList anime ID.
     *
     * Returns a list of (title, thumbnailUrl) pairs, ordered by episode number.
     * May be empty if AniList has no streaming episode data for this anime.
     */
    suspend fun fetchStreamingEpisodes(id: Int): List<AniListStreamingEpisode> =
        withContext(dispatchers.io) {
            val query = """
                {
                    Media(id: $id, type: ANIME) {
                        streamingEpisodes {
                            title
                            thumbnail
                        }
                    }
                }
            """.trimIndent()

            Logger.d("Anikuta:Core:AniList") { "fetchStreamingEpisodes(id=$id)" }

            try {
                val response = executeQuery(query)
                val parsed = json.decodeFromString<StreamingEpisodesResponse>(response)
                parsed.data.Media.streamingEpisodes ?: emptyList()
            } catch (e: Exception) {
                Logger.w("Anikuta:Core:AniList") { "fetchStreamingEpisodes failed: ${e.message}" }
                emptyList()
            }
        }

    private fun executeQueryWithVariables(query: String, variables: kotlinx.serialization.json.JsonObject): String {
        val requestBody = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }.toString().toRequestBody(jsonMediaType)

        return executeRequest(requestBody)
    }

    /**
     * Phase SC: Fetch the airing schedule for a batch of anime (up to 50 IDs).
     * Returns the status, nextAiringEpisode, + airingSchedule nodes for each anime.
     * AniList returns airingAt as Unix SECONDS — convert to millis (* 1000) when storing.
     */
    suspend fun fetchAiringSchedule(ids: List<Int>): List<AiringMedia> =
        withContext(dispatchers.io) {
            if (ids.isEmpty()) return@withContext emptyList()
            val idsStr = ids.joinToString(",")
            val query = """
                {
                    Page(perPage: 50) {
                        media(id_in: [$idsStr], type: ANIME) {
                            id
                            status
                            nextAiringEpisode { airingAt episode }
                            airingSchedule(notYetAired: true) { nodes { episode airingAt } }
                        }
                    }
                }
            """.trimIndent()

            Logger.d("Anikuta:Core:AniList") { "fetchAiringSchedule(ids.size=${ids.size})" }

            val response = executeQuery(query)
            json.decodeFromString<AiringResponse>(response).data.Page.media
        }

    private fun executeRequest(requestBody: okhttp3.RequestBody): String {
        val request = Request.Builder()
            .url(endpoint)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Logger.e("Anikuta:Core:AniList") { "AniList API error: ${response.code}" }
                throw RuntimeException("AniList API error: ${response.code}")
            }
            return response.body?.string()
                ?: throw RuntimeException("AniList API: empty response body")
        }
    }
}

// ── Phase SC: Airing schedule response models ──

@kotlinx.serialization.Serializable
data class AiringResponse(val data: AiringData)

@kotlinx.serialization.Serializable
data class AiringData(val Page: AiringPage)

@kotlinx.serialization.Serializable
data class AiringPage(val media: List<AiringMedia>)

@kotlinx.serialization.Serializable
data class AiringMedia(
    val id: Int,
    val status: String? = null,
    val nextAiringEpisode: AiringEpisode? = null,
    val airingSchedule: AiringScheduleNodes? = null,
)

@kotlinx.serialization.Serializable
data class AiringEpisode(val airingAt: Long, val episode: Int)

@kotlinx.serialization.Serializable
data class AiringScheduleNodes(val nodes: List<AiringEpisode> = emptyList())

// ── ROUND 152: the category-index filter sets (the lib/anilist.ts port) ─────

/** How many aggregated tags the category index serves (the handoff: 30). */
private const val CATEGORY_TAG_COUNT = 30

/**
 * Descriptive meta-tags (cast composition, protagonist gender, production
 * notes, archetype slang) that are noise on a browse-by-category screen —
 * the ported META_TAGS set from the design's lib/anilist.ts.
 */
private val META_TAGS = setOf(
    "Male Protagonist",
    "Female Protagonist",
    "Primarily Teen Cast",
    "Primarily Adult Cast",
    "Primarily Male Cast",
    "Primarily Female Cast",
    "Ensemble Cast",
    "Heterosexual",
    "CGI",
    "Time Skip",
    "Kuudere",
    "Tsundere",
    "Yandere",
    "Osananajimi",
)
