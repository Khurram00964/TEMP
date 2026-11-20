package com.confused.anikuta.core.tmdb

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
 * ROUND 160 (WS-SEARCH): the Jikan API client — the UNOFFICIAL MyAnimeList
 * data API, completely KEYLESS (the user's ask: "an option which provides
 * the exact same data of MyAnimeList without going through MyAnimeList").
 *
 * Rate limits (Jikan's documented fair use): 3 req/s, 60/min — the search's
 * single call sits well inside.
 */
class JikanApi {

    companion object {
        private const val TAG = "Anikuta:Core:Jikan"
        private const val BASE = "https://api.jikan.moe/v4"
    }

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    /** One Jikan search result (an anime entry). */
    data class JikanItem(
        val malId: Int,
        val title: String,
        val posterUrl: String?,
        val year: Int?,
        val score: Double?,
        val episodes: Int?,
        val type: String?,       // TV / Movie / OVA
    )

    /** GET /anime?q={query} — the anime search. */
    suspend fun search(query: String): List<JikanItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val request = Request.Builder()
                .url("$BASE/anime?q=${android.net.Uri.encode(query)}&limit=20&order_by=popularity")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Logger.w(TAG) { "search failed: ${response.code}" }
                    return@withContext emptyList()
                }
                val payload = response.body?.string() ?: return@withContext emptyList()
                parse(payload)
            }
        } catch (e: Exception) {
            Logger.w(TAG) { "search failed: ${e.message}" }
            emptyList()
        }
    }

    /** GET /top/anime — the blank-query default for the Jikan source. */
    suspend fun topAnime(): List<JikanItem> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE/top/anime?limit=20")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Logger.w(TAG) { "top failed: ${response.code}" }
                    return@withContext emptyList()
                }
                val payload = response.body?.string() ?: return@withContext emptyList()
                parse(payload)
            }
        } catch (e: Exception) {
            Logger.w(TAG) { "top failed: ${e.message}" }
            emptyList()
        }
    }

    private fun parse(payload: String): List<JikanItem> = try {
        val array = json.parseToJsonElement(payload).jsonObject["data"]?.jsonArray
            ?: return emptyList()
        array.mapNotNull { element ->
            val obj = element.jsonObject
            val malId = obj["mal_id"]?.jsonPrimitive?.content?.toIntOrNull()
                ?: return@mapNotNull null
            val title = obj["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
            JikanItem(
                malId = malId,
                title = title,
                posterUrl = obj["images"]?.jsonObject?.get("jpg")?.jsonObject
                    ?.get("image_url")?.jsonPrimitive?.content,
                year = obj["year"]?.jsonPrimitive?.content?.toIntOrNull()
                    ?: obj["aired"]?.jsonObject?.get("prop")?.jsonObject
                        ?.get("from")?.jsonObject?.get("year")?.jsonPrimitive?.content
                        ?.toIntOrNull(),
                score = obj["score"]?.jsonPrimitive?.content?.toDoubleOrNull(),
                episodes = obj["episodes"]?.jsonPrimitive?.content?.toIntOrNull(),
                type = obj["type"]?.jsonPrimitive?.content,
            )
        }
    } catch (e: Exception) {
        Logger.w(TAG) { "parse failed: ${e.message}" }
        emptyList()
    }
}
