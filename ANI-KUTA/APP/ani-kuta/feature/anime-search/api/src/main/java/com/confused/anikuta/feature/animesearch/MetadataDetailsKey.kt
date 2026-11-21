package com.confused.anikuta.feature.animesearch

import com.confused.anikuta.core.navigation.NavKey
import kotlinx.serialization.Serializable

/**
 * ROUND 169 (the v1.1.104 device order): the METADATA DETAILS key — the
 * details page OF the tapped data-source result (TMDB / TVmaze / MAL /
 * Simkl), loaded from that source's data (NOT the CS hand-off switch the
 * tap used to do). The DB links the content to its data source via the
 * content_details' data axis (data_source_type / data_source_ref_id).
 *
 * The fields mirror [DataSourceResult]'s display set (the search result's
 * own data renders the page — no extra fetch needed for v1).
 */
@Serializable
data class MetadataDetailsKey(
    /** "tmdb" | "tvmaze" | "mal" | "simkl" (the DataSourceKind id). */
    val source: String,
    val remoteId: Int,
    val title: String,
    val posterUrl: String? = null,
    val year: Int? = null,
    val rating: Double? = null,
    val overview: String? = null,
    val mediaType: String = "",
) : NavKey {

    /** The synthetic main_id (the DB identity): "tmdb:12345" etc. */
    val mainId: String get() = "$source:$remoteId"

    companion object {
        /** The main_id prefix → the display source name (the Settings surfaces). */
        fun displaySourceOf(source: String): String = when (source) {
            "tmdb" -> "TMDB"
            "tvmaze" -> "TVMaze"
            "mal" -> "MyAnimeList"
            "simkl" -> "Simkl"
            else -> source.uppercase()
        }
    }
}
