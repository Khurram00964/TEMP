package com.confused.anikuta.feature.animebrowse

import com.confused.anikuta.core.navigation.NavKey
import kotlinx.serialization.Serializable

/**
 * ROUND 151: NavKey for a home catalog page — the "See all" action behind the
 * home screen's rails (the design-handoff rebuild). Carries the AniList
 * browse-sort query the rail was showing ("TRENDING_DESC" for the trending
 * rail) + the human title the destination renders.
 *
 * The destination screen itself is owned by the catalog agent — the home
 * only pushes the key. Until that agent's dispatch lands in MainActivity,
 * pushing this key renders the nav safety-net branch (the logcat warning).
 */
@Serializable
data class HomeCatalogKey(
    val query: String,
    val title: String,
) : NavKey
