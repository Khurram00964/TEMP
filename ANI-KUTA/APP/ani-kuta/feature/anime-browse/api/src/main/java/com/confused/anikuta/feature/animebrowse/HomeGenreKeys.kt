package com.confused.anikuta.feature.animebrowse

import com.confused.anikuta.core.navigation.NavKey
import kotlinx.serialization.Serializable

/**
 * ROUND 152: NavKey for the home's "All genres" index (the handoff's
 * GenresScreen) — the Browse-by-mood section action pushes it; the index
 * opens [HomeGenreKey] grids. A [Serializable] data object: the screen is
 * stateless (its data is fetched per composition).
 */
@Serializable
data object HomeGenresKey : NavKey

/**
 * ROUND 152: NavKey for one category's title grid (the handoff's
 * GenreScreen) — [kind] is [KIND_GENRE] or [KIND_TAG] (AniList genre name or
 * aggregated popular tag name), [name] the category label; the grid pages
 * 24-at-a-time in popularity order.
 */
@Serializable
data class HomeGenreKey(
    val kind: String,
    val name: String,
) : NavKey {
    companion object {
        /** An AniList genre — fetched with the genre_in filter. */
        const val KIND_GENRE = "genre"

        /** An aggregated popular tag — fetched with the tag_in filter. */
        const val KIND_TAG = "tag"
    }
}
