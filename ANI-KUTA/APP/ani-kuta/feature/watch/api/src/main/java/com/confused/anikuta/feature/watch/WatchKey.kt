package com.confused.anikuta.feature.watch

import com.confused.anikuta.core.navigation.NavKey
import kotlinx.serialization.Serializable

/**
 * Nav3 key for the Watch screen. Carries the video URL + metadata needed
 * to play the video + the episode list for episode switching.
 */
@Serializable
data class WatchKey(
    val videoUrl: String,
    val animeTitle: String,
    val quality: String,
    val episodeUrl: String = "",
    val episodeNumber: Float = 0f,
    val episodeTitle: String = "",
    /** Serialized episode list for episode switching in the watch screen.
     *  Each entry is "url\u001FepisodeNumber\u001Fname" separated by newlines.
     *
     *  CRITICAL: Uses \u001F (ASCII Unit Separator) as the field delimiter
     *  instead of '|' because episode URLs can contain '|' characters
     *  (e.g. some extensions use '|' in their URL scheme). Using '|' as a
     *  delimiter corrupts the URL, episode number, AND name when the URL
     *  contains '|'. \u001F is a control character that never appears in
     *  URLs or episode names. */
    val episodeListSerialized: String = "",
    /** HTTP headers for the video URL — CRITICAL for playback.
     *  Extensions provide headers (Referer, User-Agent, etc.) that upstream
     *  servers require. Without these, the server returns 403 Forbidden.
     *  Format: "Key: Value,Key2: Value2" (comma-separated, like MPV's
     *  http-header-fields option). */
    val videoHeaders: String = "",

    /** Registry key for the full resolved-servers list (for QualitySheet).
     *  The Details screen resolves the videos, stores them in
     *  [com.confused.anikuta.core.videoresolver.ResolvedVideosRegistry], and
     *  passes the key here. The watch screen reads the servers from the
     *  registry to populate the QualitySheet accordion.
     *  Empty string = no pre-resolved servers (QualitySheet will show empty). */
    val resolvedVideosKey: String = "",

    /** The source ID for re-resolving episodes when switching.
     *  When the user taps a different episode in the list, the watch screen
     *  uses this ID to get the source from the ExtensionManager and call
     *  VideoResolver.resolve() for the new episode. */
    val sourceId: Long = 0L,

    /**
     * The content's stable `main_id` (UUID from `:core:content`).
     *
     * Phase WP (episode_key standardization): used to build the standardized
     * `episode_key` = `"${mainId}|${padded_5_digit}"` for `WatchProgressStore`
     * saves. This format is STABLE across extension reinstalls + source URL
     * changes + backup/restore (unlike the old `"$sourceId|$epUrl"` format
     * which broke when sourceId changed). Empty string = fallback (the old
     * format is used temporarily if mainId isn't available — logged WARN).
     */
    val mainId: String = "",

    /** Serialized subtitle tracks for external subtitle loading via MPV's sub-add.
     *  Format: "url\u001Flang" per line, separated by newlines.
     *  Populated from the picked video's `subtitleTracks` — the extension provides
     *  these as `Track(url, lang)` pairs. For AniKotoS, the URL is a localhost proxy
     *  URL like `http://127.0.0.1:PORT/sub/0/0`.
     *
     *  CRITICAL: Carrying these directly in WatchKey (instead of relying on
     *  ResolvedVideosRegistry lookup) ensures subtitles are always available
     *  even if the registry lookup fails (URL mismatch, key blank, etc.). */
    val subtitleTracksSerialized: String = "",

    /** Serialized audio tracks for external audio loading via MPV's audio-add.
     *  Same format as [subtitleTracksSerialized]. */
    val audioTracksSerialized: String = "",

    /** Serialized episode metadata for the watch page episode list + currently-playing section.
     *  Format: "epNum\u001Ftitle\u001FthumbnailUrl\u001FairDateMillis\u001Fdescription\u001Fscanlator" per line.
     *  Empty string = no metadata available (episode list shows basic rows). */
    val episodeMetadataSerialized: String = "",

    /** Resume position in seconds (from saved WatchProgress).
     *  0 = no resume (start from beginning). WatchScreen seeks to this position
     *  after FILE_LOADED (only on the initial load, not on quality/episode switch).
     *  Phase WP-B3: enables "click same episode → plays from where you left off". */
    val startPosition: Long = 0L,

    /**
     * ROUND 101 (WS-D): the details page's cover-derived accent (ARGB as a
     * Long; 0 = none). The watch screen wraps itself + every sheet in
     * AdaptiveAccentTheme with this value so the player page carries the
     * same per-content theme as the details page it was opened from —
     * the user's call-out: the accent "does not get transferred to the player
     * page… it should be applied properly and thoroughly to everything and
     * every place on the player page". Defaulted (0L) so every existing
     * WatchKey constructor keeps compiling + deserializing unchanged; keys
     * built outside a details-page context (downloaded-episode plays) simply
     * keep the app's global accent.
     */
    val coverAccentArgb: Long = 0L,

    /**
     * ROUND 108 (D-713): the details page's COVER URL — the player episode
     * rows' thumbnail FALLBACK (the details page falls back to the cover;
     * the player used to render a number-tile wall for thumbnail-less
     * series — "a lot of other things… were not being transferred").
     * Defaulted ("") so every existing constructor + persisted key keeps
     * working; the rows treat a blank as "no fallback."
     */
    val coverUrl: String = "",
    // ROUND 153: the clicked episode's AUDIO VARIANT ("dub"/"sub"/"") — the
    // ladder's truth (the metadata map collapses dual-variant rows).
    val episodeAudioVariant: String = "",
)   /** ROUND 153: the clicked episode's AUDIO VARIANT ("dub"/"sub"/"") — the
     * ladder's truth (the metadata map collapses dual-variant rows). */