package com.confused.anikuta.feature.animesearch

import com.confused.anikuta.core.navigation.NavKey
import kotlinx.serialization.Serializable

/**
 * NavKey for the Search screen.
 *
 * ROUND 164 (STEP-3d): the PREFILL fields — the home's metadata hand-off
 * navigates here with the title (the user picks the CloudStream provider from
 * the sheet that auto-opens, and the title becomes that provider's search).
 * Both default null = the plain search entry (all existing callers unchanged).
 */
@Serializable
data class AnimeSearchKey(
    val prefillQuery: String? = null,
) : NavKey
