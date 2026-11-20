package com.confused.anikuta.core.watchprogress

/**
 * Round 120 (D-746): the single standardized episode-key builder.
 *
 * The format `"$mainId|%05d"` existed in THREE hand-rolled copies (the MPV watch
 * screen, the CS watch screen, the details page) — one format drift away from
 * silently splitting a user's progress across two keys. Every caller now goes
 * through this object; the format is unchanged so existing stored rows keep
 * resolving.
 */
object EpisodeKeys {

    /**
     * Builds the standardized `episode_key` for a content's episode.
     *
     * @param mainId the content's stable `main_id` (UUID from `:core:content`).
     * @param episodeNumber the episode's number. Callers pass the identity
     *   number they already compute — the raw story number for aniyomi content,
     *   the sub/dub flavor ordinal for CloudStream content (the two stacks'
     *   existing semantics, unchanged by this refactor).
     */
    fun build(mainId: String, episodeNumber: Float): String {
        if (mainId.isBlank()) {
            // Best-effort fallback (NOT the standard format — not backup-stable).
            // Watch keys should always carry a mainId; the blank case is logged
            // by callers that can log.
            return "unknown|${String.format("%05d", episodeNumber.toInt())}"
        }
        return "$mainId|${String.format("%05d", episodeNumber.toInt())}"
    }

    // ── ROUND 163 (STEP-1): the SEASON AXIS ──────────────────────────────────
    //
    // The 2-part format above cannot tell S1E1 from S2E1 — the blocker for TV
    // series progress/tracking. The 3-part format "{mainId}|{season}|{padded}"
    // extends it for season-aware content (movies = season 1, one episode;
    // anime stays 2-part — every stored anime row keeps resolving unchanged).
    // The parsers accept BOTH shapes so a mixed database reads honestly; the
    // denormalized watch_progress.season_number/episode_number columns (v5)
    // are the query-side mirror of these rules.

    /** The season this key carries — the 2-part legacy shape reads as season 1. */
    fun parseSeasonNumber(episodeKey: String): Int {
        val parts = episodeKey.split('|')
        return if (parts.size >= 3) parts[parts.size - 2].toIntOrNull() ?: 1 else 1
    }

    /** The per-season episode number — the LAST segment in both shapes. */
    fun parseEpisodeNumber(episodeKey: String): Int {
        val parts = episodeKey.split('|')
        return parts.last().toIntOrNull() ?: 0
    }

    /**
     * The mainId of a stored key — everything before the season segment
     * (3-part) or before the number (2-part). mainIds are UUIDs and never
     * carry a '|' themselves.
     */
    fun parseMainId(episodeKey: String): String {
        val parts = episodeKey.split('|')
        return when {
            parts.size >= 3 -> parts.dropLast(2).joinToString("|")
            parts.size == 2 -> parts.first()
            else -> episodeKey
        }
    }

    /**
     * Builds the season-aware `episode_key` — "{mainId}|{season}|{padded_5}".
     * Written ONLY for season-aware (TV) content; season 1 anime keeps the
     * 2-part [build] so existing rows keep resolving without a data rewrite.
     */
    fun buildSeasoned(mainId: String, seasonNumber: Int, episodeNumber: Float): String {
        val season = seasonNumber.coerceAtLeast(1)
        if (mainId.isBlank()) {
            return "unknown|$season|${String.format("%05d", episodeNumber.toInt())}"
        }
        return "$mainId|$season|${String.format("%05d", episodeNumber.toInt())}"
    }
}
