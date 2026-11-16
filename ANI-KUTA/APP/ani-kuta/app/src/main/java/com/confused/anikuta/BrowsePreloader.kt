package com.confused.anikuta

import android.content.Context
import coil3.imageLoader
import coil3.request.ImageRequest
import com.confused.anikuta.core.anilist.api.AniListApi
import com.confused.anikuta.core.anilist.api.BrowseCacheCodec
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.datacache.DataCacheRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * D-405 (round 29): the BROWSE PRELOADER — warms the HOME page (the tab the
 * wizard's entry lands on; round 148 renamed the surface, the warm-up keeps
 * its cache-first semantics) WHILE the user works through the onboarding
 * wizard.
 *
 * The device report: "While the user is setting up the application and going
 * through the setup wizard, in the background what will happen is that the
 * whole Browse page will load. All the cover images for all the content on
 * this page will load… After all of that has been completed and done, when
 * the user finally enters the Browse page after completing everything, it
 * will quickly enter the Browse page with all the cover images showing and
 * properly loaded and managed."
 *
 * ## How it works
 *  1. **DATA** — ROUND 151 (the design-handoff home rebuild): the home's ONE
 *     discovery section (home_trending) loads
 *     EXACTLY the way
 *     [com.confused.anikuta.feature.animebrowse.HomeViewModel] loads it on
 *     first composition: cache-first
 *     (`DataCacheRepository.getBrowseCache` + `BrowseCacheCodec.decode`),
 *     and only when the cache is missing/expired a network fetch
 *     + `upsertBrowseCache` — so the cache the Home screen reads on entry
 *     is already warm (or gets warmed here first). The round-148
 *     home_popular / home_top_rated sections are retired with their warmups:
 *     the single trending payload feeds the hero AND the ranked
 *     rail AND the mood-chip filters.
 *  2. **IMAGES** — every cover URL of the payload is enqueued into
 *     the Coil pipeline at the EXACT render sizes the Home screen uses
 *     (`SectionPreloader`'s memory-cache contract: 104×156dp for the ranked
 *     poster rail, 340×454dp for the hero carousel cards — same size + no
 *     transformations = memory-cache HITS on first compose, so the page
 *     renders with covers instantly visible).
 *
 * Runs on `Dispatchers.IO` (SQL + JSON + network) with the Coil enqueue
 * hopping to Coil's own dispatcher. Every stage is logged (the wizard's
 * preload is fully diagnosable from logcat). Failures are soft — the
 * Home screen's own loading path is untouched and unaffected (this is
 * purely a warm-up).
 */
object BrowsePreloader {

    private const val TAG = "Anikuta:BrowsePreloader"

    /** The ranked poster rail's render size (HomeScreen / SectionPreloader). */
    private const val CARD_COVER_WIDTH_DP = 104
    private const val CARD_COVER_HEIGHT_DP = 156

    /** The hero carousel card's render size (HomeScreen / SectionPreloader). */
    private const val HERO_COVER_WIDTH_DP = 340
    private const val HERO_COVER_HEIGHT_DP = 454

    /**
     * Warms the browse data + cover images. Safe to call multiple times
     * (cache-first + idempotent Coil enqueues) — the wizard calls it once.
     */
    suspend fun preload(
        context: Context,
        anilistApi: AniListApi,
        dataCacheRepository: DataCacheRepository,
    ) = withContext(Dispatchers.IO) {
        Logger.i(TAG) { "preload — START (wizard is running; warming Browse)" }
        // ROUND 151: the home has ONE discovery section now — home_trending
        // (the old trending/popular/top_rated keys stay untouched for Search's
        // offline fallback, D-278; home_popular / home_top_rated are retired
        // with the round-148 sections).
        val sections = listOf(
            BrowseCacheCodec.SECTION_HOME_TRENDING to "TRENDING_DESC",
        )
        val all = mutableListOf<AniListAnime>()
        sections.forEach { (sectionKey, sort) ->
            val anime = runCatching { loadSection(anilistApi, dataCacheRepository, sectionKey, sort) }
                .onFailure { e ->
                    Logger.w(TAG) { "preload — $sectionKey failed (soft; Home loads its own way): ${e.message}" }
                }
                .getOrDefault(emptyList())
            all += anime
            Logger.i(TAG) { "preload — $sectionKey ready: ${anime.size} anime" }
        }

        // ── The Coil cover warm-up (exact render sizes → memory hits) ──
        val loader = context.imageLoader
        val density = context.resources.displayMetrics.density
        val cardW = (CARD_COVER_WIDTH_DP * density).toInt()
        val cardH = (CARD_COVER_HEIGHT_DP * density).toInt()
        val heroW = (HERO_COVER_WIDTH_DP * density).toInt()
        val heroH = (HERO_COVER_HEIGHT_DP * density).toInt()

        val coverUrls = all.mapNotNull { it.coverUrl }
            .filter { it.isNotBlank() }
            .distinct()
        coverUrls.forEach { url ->
            loader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .size(cardW, cardH)
                    .build(),
            )
        }
        // The hero pager's posters (trending first — the hero's source) at
        // the hero's own render size.
        val heroUrls = all.take(5)
            .mapNotNull { it.coverUrl }
            .filter { it.isNotBlank() }
            .distinct()
        heroUrls.forEach { url ->
            loader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .size(heroW, heroH)
                    .build(),
            )
        }
        Logger.i(TAG) {
            "preload — DONE: 1 section (${all.size} anime), " +
                "${coverUrls.size} covers enqueued " +
                "at ${CARD_COVER_WIDTH_DP}×${CARD_COVER_HEIGHT_DP}dp " +
                "+ ${heroUrls.size} hero cards at ${HERO_COVER_WIDTH_DP}×${HERO_COVER_HEIGHT_DP}dp"
        }
    }

    /**
     * One section, cache-first — mirrors the HomeViewModel's cache
     * semantics: a fresh cache row is used as-is; a missing/expired
     * row triggers the network fetch + the cache upsert (so the Home
     * screen's cache-first read hits instantly later).
     */
    private suspend fun loadSection(
        anilistApi: AniListApi,
        dataCacheRepository: DataCacheRepository,
        sectionKey: String,
        sort: String,
    ): List<AniListAnime> {
        val cached = dataCacheRepository.getBrowseCache(sectionKey)
        if (cached != null) {
            val cachedAnime = runCatching { BrowseCacheCodec.decode(cached.dataJson) }
                .getOrDefault(emptyList())
            if (cachedAnime.isNotEmpty() && !dataCacheRepository.isBrowseCacheExpired(sectionKey)) {
                return cachedAnime
            }
        }
        val fetched = anilistApi.fetchBrowseSection(sort)
        dataCacheRepository.upsertBrowseCache(sectionKey, BrowseCacheCodec.encode(fetched))
        return fetched
    }
}
