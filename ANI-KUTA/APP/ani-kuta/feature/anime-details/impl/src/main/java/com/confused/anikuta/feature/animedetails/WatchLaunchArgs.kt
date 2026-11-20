package com.confused.anikuta.feature.animedetails

/**
 * The ONE argument bundle for [DetailsScreen]'s `onNavigateToWatch` callback
 * (2026-11-21 — replaces the former 17-positional-parameter lambda, the
 * transposition-bug risk called out in the round-160 review).
 *
 * Why a data class and not 17 primitives: every call site constructs it with
 * NAMED arguments, so a swapped pair cannot compile — the failure mode moves
 * from "silently plays the wrong field" to "red build" (CORE_RULES §5: reuse
 * + one responsibility). The host (MainActivity) reads the fields to build
 * the `WatchKey`; the field NAMES here intentionally mirror WatchKey's own
 * (plus `mainId`/`sourceId` which the host swaps into WatchKey's order).
 *
 * Stays in `:feature:anime-details:impl` — the module's no-feature-to-feature
 * deps rule (the `onNavigateToCsWatch` comment block) is untouched: the host
 * already imports the impl module to wire these lambdas.
 */
data class WatchLaunchArgs(
    val mainId: String,
    val videoUrl: String,
    val animeTitle: String,
    val quality: String,
    val episodeUrl: String,
    val episodeNumber: Float,
    val episodeTitle: String,
    /** Same wire format as WatchKey.episodeListSerialized (\u001F-delimited). */
    val episodeListSerialized: String,
    /** HTTP headers for the video URL ("Key: Value,..." — WatchKey's format). */
    val videoHeaders: String,
    /** ResolvedVideosRegistry key ("" = no pre-resolved list, e.g. cache replay). */
    val resolvedVideosKey: String,
    val sourceId: Long,
    /** Serialized subtitle tracks (url\u001Flang per line — WatchKey's format). */
    val subtitleTracksSerialized: String,
    /** Serialized audio tracks (same format as the subtitle tracks). */
    val audioTracksSerialized: String,
    /** Serialized episode metadata for the watch page's episode list. */
    val episodeMetadataSerialized: String,
    /** ROUND 101 (WS-D): the details accent rides the nav (0L = none). */
    val coverAccentArgb: Long,
    /** ROUND 108 (D-713): the cover rides the nav — the player rows' thumbnail fallback. */
    val coverUrl: String,
    /** ROUND 153: the clicked episode's audio variant ("dub"/"sub"/"" — the ladder's truth). */
    val audioVariant: String,
)

/**
 * The ONE argument bundle for the THREE CloudStream callbacks of
 * [DetailsScreen] — `onNavigateToCsWatch`, `onDownloadCsEpisode`,
 * `onNavigateToCsOfflineWatch` (2026-11-21 — replaces the 11/9/12-positional
 * lambdas, the same transposition-bug class as [WatchLaunchArgs]).
 *
 * The three routes share the SAME episode context (the former "SAME 9-arg
 * context" comments); the accent/cover pair rides every route since ROUND
 * 101/108, and only the offline DASH route sets [offlineMediaUri]. The host
 * builds the `CsWatchKey` from these fields.
 */
data class CsEpisodeLaunchArgs(
    val providerName: String,
    val animeTitle: String,
    /** The CS data handle (episode.url). */
    val episodeData: String,
    val episodeNumber: Float,
    /** Tag-stripped (F3a) — the pill carries the flavor, the title never repeats it. */
    val episodeTitle: String,
    val episodeListSerialized: String,
    val mainId: String,
    val sourceId: Long,
    /** Task 54 wire format — the same one the aniyomi watch key consumes. */
    val episodeMetadataSerialized: String,
    /** ROUND 101 (WS-D): the details accent rides the nav (0L = none). */
    val coverAccentArgb: Long = 0L,
    /** ROUND 108 (D-713): the cover rides the nav. */
    val coverUrl: String = "",
    /** D-539/D-548: set ONLY by the offline DASH route (csdash: payload or the .dashmeta uri). */
    val offlineMediaUri: String = "",
)
