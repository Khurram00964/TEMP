package com.confused.anikuta.core.tmdb

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.common.TrackerKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * ROUND 160 (WS-TMDB): the TMDB API client — the movies/series data source
 * (the v3 API key from TrackerKeys; the user's Personal-tier registration —
 * registered ONCE by the app owner, no per-user setup, exactly the
 * AniList-like convenience the user asked for).
 *
 * The endpoints used:
 * - /trending/all/day — the home's Series & movies hero rail.
 * - /search/multi — the search page's TMDB source (movies + TV in one call).
 * - /movie/popular, /tv/popular — the home shelves.
 *
 * The image base: TMDB serves posters at image.tmdb.org/t/p/{size}{path}.
 */
class TmdbApi {

    companion object {
        private const val TAG = "Anikuta:Core:TMDB"
        private const val BASE = "https://api.themoviedb.org/3"
        private const val IMAGE_BASE = "https://image.tmdb.org/t/p/w342"

        /** 0 = "unreleased"/unknown year. */
        private fun yearOf(date: String?): Int? =
            date?.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()
    }

    private val client = OkHttpClient()
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
                        ?.let { "https://image.tmdb.org/t/p/w780" + it },
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

    /** The day's trending (movies + TV mixed) — the home's Series & movies rail. */
    suspend fun trending(): List<TmdbItem> = withContext(Dispatchers.IO) {
        if (!configured()) return@withContext emptyList()
        parseItems(get("/trending/all/day", "language" to "en-US"))
    }

    /** The multi search (movies + TV) — the search page's TMDB source. */
    suspend fun search(query: String): List<TmdbItem> = withContext(Dispatchers.IO) {
        if (!configured() || query.isBlank()) return@withContext emptyList()
        parseItems(
            get("/search/multi", "language" to "en-US", "query" to query, "include_adult" to "false"),
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
}
