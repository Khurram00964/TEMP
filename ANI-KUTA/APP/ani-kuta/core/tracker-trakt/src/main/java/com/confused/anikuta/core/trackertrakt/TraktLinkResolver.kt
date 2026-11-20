package com.confused.anikuta.core.trackertrakt

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.trackerapi.TrackerLinkResolver
import com.confused.anikuta.core.trackerapi.TrackerType

/**
 * ROUND 159 (WS-C completion): the Trakt id resolution — the translation
 * layer's Trakt half. Trakt accepts TMDB ids directly in /sync/history, so
 * the resolver reads the extension channel's captured ids (WS-B2's bridge
 * work): extExtras.tmdbId first, then the IMDb id path when the TMDB search
 * capability lands (needs the TMDB API key — the ONLY piece pending).
 *
 * UNCONFIGURED-SAFE: the resolver never touches the network; entries without
 * a captured id resolve null and the sync skips honestly.
 */
class TraktLinkResolver(
    private val contentRepository: ContentRepository,
) : TrackerLinkResolver {

    companion object {
        private const val TAG = "Anikuta:Core:Tracker:Trakt"
    }

    override val trackerType: TrackerType = TrackerType.TRAKT

    override suspend fun resolve(mainId: String): Int? {
        val details = contentRepository.getContentDetails(mainId) ?: return null
        // The extension channel first (the CS bridge's captured syncData).
        val tmdbId = details.extExtras.tmdbId?.toIntOrNull()
        if (tmdbId != null && tmdbId > 0) {
            return tmdbId
        }
        // The data axis (a future TMDB metadata provider writes it here).
        val axisId = details.tmdbId
        if (axisId != null && axisId > 0) {
            return axisId
        }
        Logger.d(TAG) { "resolve — no TMDB id for mainId=$mainId (the TMDB search fallback lands with the API key)" }
        return null
    }
}
