package com.confused.anikuta.feature.updates

import com.confused.anikuta.core.navigation.NavKey
import kotlinx.serialization.Serializable

/**
 * NavKey for the Updates screen (Phase UP).
 *
 * Reached from the More screen → "Updates" row, and from the home's
 * "Airs today" → "Schedule" action (ROUND 152: that push carries
 * [initialTab] = 1 so the screen LANDS on the Schedule tab; every other
 * entry point keeps the default 0 = Updates).
 *
 * Shows new-episode releases for the user's library and hosts the
 * Updates | Schedule tab strip (Schedule added in Phase SC).
 *
 * ROUND 152: object → @Serializable data class — the all-default constructor
 * breaks no caller shape, and every bare `pushKey(UpdatesKey)` site became
 * `pushKey(UpdatesKey())`.
 */
@Serializable
data class UpdatesKey(val initialTab: Int = 0) : NavKey
