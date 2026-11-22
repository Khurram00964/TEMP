package com.confused.anikuta.feature.animedetails

import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.common.model.UnifiedAnime
import com.confused.anikuta.core.videoresolver.ResolvedVideo
import com.confused.anikuta.core.videoresolver.ResolverServer
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode

// ── State types ──
// 2026-11-21 (the god-file normalization): moved VERBATIM out of
// DetailsViewModel.kt (the file was 4,965 lines — the round-160 review's
// hotspot list). Same package, zero behavior change; DetailsViewModel and the
// screens see them unchanged.

sealed interface DetailsState {
    data object Loading : DetailsState
    data class Success(val anime: UnifiedAnime) : DetailsState
    data class Error(val message: String) : DetailsState
}

/** A linked source — the source ID + name + the SAnime's URL on that source. */
data class LinkedSource(
    val sourceId: Long,
    val sourceName: String,
    val animeUrl: String,
)

/**
 * D-234: Next-episode release info — shows the upcoming episode with a countdown.
 *
 * @param episodeNumber The episode number that will air next.
 * @param airingAtMillis The Unix timestamp (millis) when the episode airs.
 * @param isComingSoon D-237: true when the air time has passed but the episode
 *   isn't available on the source yet. Shows "Coming soon" instead of a countdown.
 */
data class NextEpisodeInfo(
    val episodeNumber: Int,
    val airingAtMillis: Long,
    val isComingSoon: Boolean = false,
)

/** Manual search state (source linking — AniList entries). */
sealed interface ManualSearchState {
    data object Idle : ManualSearchState
    data object Searching : ManualSearchState
    data class Results(val source: AnimeCatalogueSource, val sAnimes: List<SAnime>) : ManualSearchState
    data class Error(val sourceName: String, val message: String) : ManualSearchState
}

// ── Phase B: Auto-link + AniList search states ──

/**
 * Auto-link lifecycle for extension entries.
 *
 * - [Idle]: Not an extension entry, or not yet started.
 * - [Searching]: AutoLinkService is running (cache check + AniList search + SmartMatcher).
 * - [Matched]: A confident match was found (and merged into UnifiedAnime).
 * - [NoMatch]: No confident match — manual link sheet should be shown.
 * - [Skipped]: Auto-link disabled for this source, or strategy = MANUAL, or user skipped.
 * - [Error]: AniList search or matching failed.
 */
sealed interface AutoLinkState {
    data object Idle : AutoLinkState
    data object Searching : AutoLinkState
    data class Matched(val anilistId: Int, val score: Float, val cached: Boolean) : AutoLinkState
    data class NoMatch(val bestScore: Float, val searchedTitle: String) : AutoLinkState
    data class Skipped(val reason: String) : AutoLinkState
    data class Error(val message: String) : AutoLinkState
}

/**
 * D-225c: Reverse auto-link state — the AniList → extensions search
 * direction, rendered INLINE by the details screen (ROUND 121 D6-a: the
 * old [AutoLinkPopup] composable is deleted; the screen consumes this
 * state directly).
 *
 * - [Idle]: nothing happening (initial / dismissed / skipped).
 * - [Searching]: extensions are being queried (inline card shows spinner + "Searching…").
 * - [Matched]: a source was linked (inline card confirms).
 * - [NoMatch]: no confident match (inline card offers a "Link manually" action).
 * - [Error]: search failed (inline card shows the error).
 */
sealed interface ReverseAutoLinkState {
    data object Idle : ReverseAutoLinkState

    /**
     * ROUND 173 (N1): the LIVE search status — the sourceName carries the
     * extension CURRENTLY being searched (the ReverseAutoLinkService's
     * per-source callback; blank = the pre-search beat). The elapsed
     * timer + the cancel button render from this state on the screen.
     */
    data class Searching(val sourceName: String = "") : ReverseAutoLinkState
    data class Matched(
        val sourceName: String,
        val animeTitle: String,
        val thumbnailUrl: String?,
        val score: Float,
    ) : ReverseAutoLinkState
    data class NoMatch(
        val bestScore: Float,
        val searchedTitle: String,
    ) : ReverseAutoLinkState
    data class Error(val message: String) : ReverseAutoLinkState
}

/** AniList search state for the manual link sheet. */
sealed interface AniListSearchState {
    data object Idle : AniListSearchState
    data object Searching : AniListSearchState
    data object Empty : AniListSearchState
    data class Results(val anime: List<AniListAnime>) : AniListSearchState
    data class Error(val message: String) : AniListSearchState
}

/** Episode list state. */
sealed interface EpisodeState {
    data object Idle : EpisodeState
    data object Loading : EpisodeState
    data object Empty : EpisodeState
    data class Loaded(val episodes: List<SEpisode>) : EpisodeState
    data class Error(val message: String) : EpisodeState
    /**
     * D-209: Cloudflare blocked the episode fetch + the headless solver failed.
     * The UI shows an "Open in WebView" button + a "Refresh" button.
     */
    data class CloudflareBlocked(val url: String, val sourceName: String) : EpisodeState
}

// ── D.3 refresh types ──
// D-314: RefreshStage + RefreshState (the multi-stage pull-to-refresh types)
// are DELETED — pull-to-refresh is now a simple full refresh. See the D-314
// note at the old refresh-state declaration.

/** Video resolution state (for the resolver sheet). */
sealed interface ResolverState {
    data object Idle : ResolverState
    data object Loading : ResolverState
    data class Success(
        val videos: List<ResolvedVideo>,
        val servers: List<ResolverServer> = emptyList(),
    ) : ResolverState
    data class Error(val message: String) : ResolverState
}

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 104 (WS-B): the tracking toast's payload — the one-shot notice the
//  TrackingToastHost renders (STARTED on startTracking's confirmed sync;
//  STOPPED on removeTracking's success). The `id` (System.nanoTime) forces a
//  DISTINCT emission even when the same kind fires twice in a row — the
//  host's LaunchedEffect keys on it.
// ════════════════════════════════════════════════════════════════════════════

enum class TrackingNoticeKind { STARTED, STOPPED }

data class TrackingNotice(
    val kind: TrackingNoticeKind,
    val id: Long,
)
