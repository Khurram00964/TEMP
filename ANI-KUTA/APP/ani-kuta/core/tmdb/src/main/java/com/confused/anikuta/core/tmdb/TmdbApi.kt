package com.confused.anikuta.core.tmdb

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.trackerapi.TrackerKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * ROUND 163 (STEP-3, re-land of the reverted round-160 WS-TMDB client, the
 * import adapted to the re-landed TrackerKeys in :core:tracker-api): the
 * TMDB API client — the MOVIES + SERIES data source (the v3 key from
 * TrackerKeys; the user's own registration — no per-user setup, the
 * AniList-like convenience the user asked for).
 *
 * ⚠ DATA SOURCE ONLY — TMDB carries NO streams. Everything it feeds routes
 * to the details page + the CloudStream extension playback search (the user's
 * round-163 priority rule).
 *
 * The endpoints:
 * - /trending/all/day — the home's movies/series trending feed.
 * - /search/multi — the search page's TMDB source (movies + TV in one call).
 * - /movie/popular, /tv/popular — the home shelves.
 *
 * The image base: TMDB serves posters at image.tmdb.org/t/p/{size}{path}.
 */
class TmdbApi(
    private val client: OkHttpClient = OkHttpClient(),
) {

    companion object {
        private const val TAG = "Anikuta:Core:TMDB"
        private const val BASE = "https://api.themoviedb.org/3"
        private const val IMAGE_BASE = "https://image.tmdb.org/t/p/w342"
        private const val BACKDROP_BASE = "https://image.tmdb.org/t/p/w780"

        /** 0 = "unreleased"/unknown year. */
        private fun yearOf(date: String?): Int? =
            date?.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** One piece of TMDB content — a movie or a series. */
    data class TmdbItem(
        val id: Int,
        val mediaType: String,          // "movie" | "tv"
        val title: String,
        val overview: String?,
        val posterUrl: String?,
        val backdropUrl: String?,
        val year: Int?,
        val rating: Double?,            // 0..10
    )

    /** The key guard — every call no-ops (empty) when unconfigured. */
    private fun configured(): Boolean {
        if (TrackerKeys.TMDB_API_KEY.isBlank()) {
            Logger.w(TAG) { "TMDB_API_KEY is blank — the call skipped" }
            return false
        }
        return true
    }

    private fun get(path: String, vararg params: Pair<String, String>): String? =
        runCatching {
            val query = buildString {
                append("?api_key=").append(TrackerKeys.TMDB_API_KEY)
                for ((k, v) in params) append("&").append(k).append("=").append(v)
            }
            val request = Request.Builder().url(BASE + path + query).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Logger.w(TAG) { "GET $path failed: ${response.code}" }
                    return@use null
                }
                response.body?.string()
            }
        }.getOrNull()

    /** Parses a TMDB results array into [TmdbItem]s (the shared shape). */
    private fun parseItems(payload: String?): List<TmdbItem> {
        if (payload.isNullOrBlank()) return emptyList()
        return try {
            val results = json.parseToJsonElement(payload).jsonObject["results"]?.jsonArray
                ?: return emptyList()
            results.mapNotNull { element ->
                val obj = element.jsonObject
                val mediaType = obj["media_type"]?.jsonPrimitive?.content
                    ?: "movie" // /movie/popular + /tv/popular omit media_type
                val id = obj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null
                val title = when (mediaType) {
                    "tv" -> obj["name"]?.jsonPrimitive?.content
                        ?: obj["original_name"]?.jsonPrimitive?.content
                    else -> obj["title"]?.jsonPrimitive?.content
                        ?: obj["original_title"]?.jsonPrimitive?.content
                } ?: return@mapNotNull null
                val posterPath = obj["poster_path"]?.jsonPrimitive?.content
                val backdropPath = obj["backdrop_path"]?.jsonPrimitive?.content
                TmdbItem(
                    id = id,
                    mediaType = mediaType,
                    title = title,
                    overview = obj["overview"]?.jsonPrimitive?.content,
                    posterUrl = posterPath?.takeIf { it.isNotBlank() }
                        ?.let { IMAGE_BASE + it },
                    backdropUrl = backdropPath?.takeIf { it.isNotBlank() }
                        ?.let { BACKDROP_BASE + it },
                    year = yearOf(
                        obj["release_date"]?.jsonPrimitive?.content
                            ?: obj["first_air_date"]?.jsonPrimitive?.content,
                    ),
                    rating = obj["vote_average"]?.jsonPrimitive?.content?.toDoubleOrNull(),
                )
            }
        } catch (e: Exception) {
            Logger.w(TAG) { "parse failed: ${e.message}" }
            emptyList()
        }
    }

    /** The day's trending (movies + TV mixed) — the home's movies/series feed. */
    suspend fun trending(): List<TmdbItem> = withContext(Dispatchers.IO) {
        if (!configured()) return@withContext emptyList()
        parseItems(get("/trending/all/day", "language" to "en-US"))
    }

    /** The multi search (movies + TV) — the search page's TMDB source. */
    suspend fun search(query: String, page: Int = 1): List<TmdbItem> = withContext(Dispatchers.IO) {
        if (!configured() || query.isBlank()) return@withContext emptyList()
        parseItems(
            get(
                "/search/multi",
                "language" to "en-US",
                "query" to query,
                "include_adult" to "false",
                "page" to page.toString(),
            ),
        )
    }

    /** Popular TV series (the home shelf). */
    suspend fun popularTv(): List<TmdbItem> = withContext(Dispatchers.IO) {
        if (!configured()) return@withContext emptyList()
        parseItems(get("/tv/popular", "language" to "en-US", "page" to "1"))
    }

    /** Popular movies (the home shelf). */
    suspend fun popularMovies(): List<TmdbItem> = withContext(Dispatchers.IO) {
        if (!configured()) return@withContext emptyList()
        parseItems(get("/movie/popular", "language" to "en-US", "page" to "1"))
    }

    /**
     * ROUND 165 (STEP-3d depth): the TMDB genre-id → name maps (movie + tv,
     * merged — the ids are disjoint per type but the names collide, so the
     * map keys are the ids as-is and the discover call passes the matching
     * type's id). Fetched once per call site (the VM caches).
     */
    suspend fun genreNames(): Map<Int, String> = withContext(Dispatchers.IO) {
        if (!configured()) return@withContext emptyMap()
        val merged = mutableMapOf<Int, String>()
        for (path in listOf("/genre/movie/list", "/genre/tv/list")) {
            runCatching {
                val payload = get(path, "language" to "en-US") ?: return@runCatching
                val arr = json.parseToJsonElement(payload).jsonObject["genres"]?.jsonArray ?: return@runCatching
                arr.forEach { element ->
                    val obj = element.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@forEach
                    val name = obj["name"]?.jsonPrimitive?.content ?: return@forEach
                    merged[id] = name
                }
            }
        }
        merged
    }

    /**
     * ROUND 165 (STEP-3d depth): the by-genre discover (the mood chips' overall
     * content — NOT the trending). Sorted by popularity, page 1.
     */
    suspend fun discoverByGenre(
        mediaType: String, // "movie" | "tv"
        genreId: Int,
    ): List<TmdbItem> = withContext(Dispatchers.IO) {
        if (!configured()) return@withContext emptyList()
        parseItems(
            get(
                "/discover/$mediaType",
                "language" to "en-US",
                "with_genres" to genreId.toString(),
                "include_adult" to "false",
                "sort_by" to "popularity.desc",
                "page" to "1",
            ),
        )
    }
}
