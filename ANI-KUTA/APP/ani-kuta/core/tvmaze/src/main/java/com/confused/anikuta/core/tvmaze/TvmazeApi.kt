package com.confused.anikuta.core.tvmaze

import com.confused.anikuta.core.common.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * ROUND 163 (STEP-3): the TVmaze API client — the KEYLESS complement data
 * source (https://www.tvmaze.com/api — no key at all, free; ≥20 calls/10s per
 * IP; CC BY-SA attribution owed in any public-facing use of the data).
 *
 * TVmaze is the schedule/id specialist — exactly what TMDB does weakest for
 * this app:
 * - /schedule?date= — the airs-by-date rail (the AniList-style "airing
 *   today" shape, per-country web episodes excluded).
 * - /search/shows?q= — the fuzzy search.
 * - /shows/:id?embed=nextepisode — the next-episode air data.
 * TVmaze carries NO movies and NO trending — it complements TMDB, it does
 * not replace it (the research verdict).
 *
 * ⚠ DATA SOURCE ONLY — no streams; playback goes to the CloudStream
 * extensions (the user's round-163 priority rule).
 */
class TvmazeApi(
    private val client: OkHttpClient = OkHttpClient(),
) {

    companion object {
        private const val TAG = "Anikuta:Core:TVMaze"
        private const val BASE = "https://api.tvmaze.com"
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** One piece of TVmaze content — a TV series (no movies exist here). */
    data class TvmazeShow(
        val id: Int,
        val title: String,
        val overview: String?,
        val posterUrl: String?,
        val year: Int?,
        val rating: Double?,
        val network: String?,
        val nextEpisodeName: String?,
        val nextEpisodeAirDate: String?, // ISO local date (no exact time — TVmaze separates the schedule)
        val nextEpisodeNumber: Int?,
    )

    private fun get(url: String): String? = runCatching {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Logger.w(TAG) { "GET ${request.url.encodedPath} failed: ${response.code}" }
                return@use null
            }
            response.body?.string()
        }
    }.getOrNull()

    /** Maps a TVmaze show object (the shared shape across endpoints). */
    private fun parseShow(obj: kotlinx.serialization.json.JsonObject, embeddedNext: Boolean): TvmazeShow? {
        val id = obj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        val title = obj["name"]?.jsonPrimitive?.content ?: return null
        val image = obj["image"]?.jsonObject
        var nextName: String? = null
        var nextDate: String? = null
        var nextNumber: Int? = null
        if (embeddedNext) {
            val next = obj["_embedded"]?.jsonObject?.get("nextepisode")?.jsonObject
            nextName = next?.get("name")?.jsonPrimitive?.content
            nextDate = next?.get("airdate")?.jsonPrimitive?.content
            nextNumber = next?.get("number")?.jsonPrimitive?.content?.toIntOrNull()
        }
        return TvmazeShow(
            id = id,
            title = title,
            overview = obj["summary"]?.jsonPrimitive?.content?.let { summary ->
                // TVmaze summaries are HTML fragments — strip the tags for the line.
                summary.replace(Regex("<[^>]*>"), "").trim()
            },
            posterUrl = image?.get("medium")?.jsonPrimitive?.content
                ?: image?.get("original")?.jsonPrimitive?.content,
            year = obj["premiered"]?.jsonPrimitive?.content?.takeIf { it.length >= 4 }
                ?.substring(0, 4)?.toIntOrNull(),
            rating = obj["rating"]?.jsonObject?.get("average")?.jsonPrimitive?.content?.toDoubleOrNull(),
            network = (obj["network"]?.jsonObject ?: obj["webChannel"]?.jsonObject)
                ?.get("name")?.jsonPrimitive?.content,
            nextEpisodeName = nextName,
            nextEpisodeAirDate = nextDate,
            nextEpisodeNumber = nextNumber,
        )
    }

    /** GET /search/shows?q= — the fuzzy search (score-ordered pairs). */
    suspend fun search(query: String): List<TvmazeShow> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val payload = get("$BASE/search/shows?q=${java.net.URLEncoder.encode(query, "UTF-8")}")
        if (payload.isNullOrBlank()) return@withContext emptyList()
        runCatching {
            json.parseToJsonElement(payload).jsonArray.mapNotNull { element ->
                val obj = element.jsonObject
                parseShow(obj["show"]?.jsonObject ?: return@mapNotNull null, embeddedNext = false)
            }
        }.getOrDefault(emptyList())
    }

    /**
     * GET /schedule?date=YYYY-MM-DD&country=US — the airs-by-date rail. The
     * country defaults to US (the endpoint REQUIRES one; web-only series are
     * excluded — /schedule/web covers them and is called for the same day).
     */
    suspend fun schedule(date: String): List<TvmazeShow> = withContext(Dispatchers.IO) {
        val payload = get("$BASE/schedule?date=$date&country=US")
        val episodes = payload?.let {
            runCatching { json.parseToJsonElement(it).jsonArray }.getOrNull()
        } ?: return@withContext emptyList()
        val seen = mutableSetOf<Int>()
        episodes.mapNotNull { element ->
            val obj = element.jsonObject
            val show = obj["_embedded"]?.jsonObject?.get("show")?.jsonObject
                ?: obj["show"]?.jsonObject
                ?: return@mapNotNull null
            val parsed = parseShow(show, embeddedNext = false) ?: return@mapNotNull null
            // The schedule is PER-EPISODE rows — dedupe to one row per show.
            if (!seen.add(parsed.id)) return@mapNotNull null
            parsed
        }
    }

    /** GET /shows/:id?embed=nextepisode — the detail/next-air read. */
    suspend fun show(id: Int): TvmazeShow? = withContext(Dispatchers.IO) {
        val payload = get("$BASE/shows/$id?embed=nextepisode")
        if (payload.isNullOrBlank()) return@withContext null
        runCatching { parseShow(json.parseToJsonElement(payload).jsonObject, embeddedNext = true) }
            .getOrNull()
    }
}
