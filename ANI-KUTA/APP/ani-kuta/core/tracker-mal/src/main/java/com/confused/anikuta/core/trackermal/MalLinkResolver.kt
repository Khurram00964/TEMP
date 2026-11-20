package com.confused.anikuta.core.trackermal

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.trackerapi.TrackerLinkResolver
import com.confused.anikuta.core.trackerapi.TrackerType

/**
 * ROUND 159 (WS-MAL): the MAL id resolution — the translation layer's MAL
 * half. The beautiful part: AniList's OWN payload carries `idMal` for every
 * anime, and the app already stores it (`ContentDetails.dataExtras.idMal`,
 * surfaced by the `malId` accessor) — so every ANIYOMI entry linked to
 * AniList resolves to MAL with ZERO network. Unlinked entries resolve null
 * (the sync skips honestly; the MAL manual-link UI is a later piece).
 */
class MalLinkResolver(
    private val contentRepository: ContentRepository,
) : TrackerLinkResolver {

    companion object {
        private const val TAG = "Anikuta:Core:Tracker:Mal"
    }

    override val trackerType: TrackerType = TrackerType.MAL

    override suspend fun resolve(mainId: String): Int? {
        val details = contentRepository.getContentDetails(mainId)
        val id = details?.malId
        if (id == null || id <= 0) {
            Logger.d(TAG) { "resolve — no MAL id for mainId=$mainId" }
            return null
        }
        return id
    }
}
