package com.confused.anikuta.feature.animedetails

import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.trackerapi.TrackEntry
import com.confused.anikuta.core.trackerapi.TrackStatus
import com.confused.anikuta.core.trackerapi.TrackerType
import kotlinx.coroutines.launch

// ── ROUND 168 (the tracker redo, Phase D): the MULTI-TRACKER dispatch ──
// The extension-function cluster (the D-923 pattern): the >1-linked tracker
// picker (the spec 05-T-2's 3-dot surface) + the MAL paths of the sheet's
// open/save/delete. The AniList paths stay in DetailsViewModelTracking.kt
// untouched (the frozen surface); this file ADDS the dispatch around them.
//
// The mapping contract: the MAL entry's trackerId is the MAL anime id,
// stored in track_entry (tracker_type = 'mal') by the MalLinkSheet pick —
// the AniList path keys by the content's anilistId, the MAL path by the
// stored mapping. No mapping + a linked MAL → the MalLinkSheet opens.
private const val TAG_MT = "Anikuta:Feature:Details"

/**
 * The tracking entry point — the 3-dot menu + the tracking button call THIS.
 *
 * ROUND 170 (the device orders): (a) THE ONE-TRACKER RULE — a tracked
 * content's track action opens THAT tracker's sheet DIRECTLY (no picker);
 * the picker only appears for an UNTRACKED content with >1 tracker linked.
 * (b) Simkl joins the picker when linked.
 */
fun DetailsViewModel.openTrackerPickerOrSheet() {
    val mid = currentMainId ?: return
    viewModelScope.launch {
        val repo = trackingStateRepository
        // (a) The tracked-tracker lookup — the first tracked one wins.
        if (repo != null) {
            if (aniListTracker != null &&
                runCatching { repo.isTracked(mid, TrackerType.ANILIST) }.getOrDefault(false)
            ) {
                _activeTrackerType.value = TrackerType.ANILIST
                openTrackSheet()
                return@launch
            }
            if (malTracker != null &&
                runCatching { repo.isTracked(mid, TrackerType.MAL) }.getOrDefault(false)
            ) {
                _activeTrackerType.value = TrackerType.MAL
                openMalTrackSheet()
                return@launch
            }
            if (simklTracker != null &&
                runCatching { repo.isTracked(mid, TrackerType.SIMKL) }.getOrDefault(false)
            ) {
                _activeTrackerType.value = TrackerType.SIMKL
                openSimklTrackSheet()
                return@launch
            }
        }
        // (b) The untracked content: the picker when >1 linked, else direct.
        val linked = mutableListOf<TrackerType>()
        if (aniListTracker != null && runCatching { aniListTracker!!.isLoggedIn() }.getOrDefault(false)) {
            linked.add(TrackerType.ANILIST)
        }
        if (malTracker != null && runCatching { malTracker!!.isLoggedIn() }.getOrDefault(false)) {
            linked.add(TrackerType.MAL)
        }
        if (simklTracker != null && runCatching { simklTracker!!.isLoggedIn() }.getOrDefault(false)) {
            linked.add(TrackerType.SIMKL)
        }
        when {
            // ROUND 172 (the device orders): 0 linked → the picker's honest
            // "No trackers are connected" (the v1.1.107 bug fell through to
            // the ANILIST default → the AniList not-connected sheet).
            linked.isEmpty() -> _showTrackerPicker.value = true
            // 1 linked → the DIRECT open (that tracker's flow — the sheet
            // or, with no mapping, the unified link sheet's tracking entry;
            // the v1.1.107 dead taps were the never-hosted link sheets).
            linked.size == 1 -> {
                _activeTrackerType.value = linked.first()
                when (_activeTrackerType.value) {
                    TrackerType.MAL -> openMalTrackSheet()
                    TrackerType.SIMKL -> openSimklTrackSheet()
                    else -> openTrackSheet()
                }
            }
            // >1 linked → the picker.
            else -> _showTrackerPicker.value = true
        }
    }
}

fun DetailsViewModel.dismissTrackerPicker() {
    _showTrackerPicker.value = false
}

/** The picker's pick — targets the track action at the chosen tracker. */
fun DetailsViewModel.pickTracker(type: TrackerType) {
    _showTrackerPicker.value = false
    _activeTrackerType.value = type
    when (type) {
        TrackerType.MAL -> openMalTrackSheet()
        TrackerType.SIMKL -> openSimklTrackSheet()
        else -> openTrackSheet()
    }
}

/**
 * The MAL open path: the cached mapping (tracker_type = 'mal') seeds the
 * sheet, then the remote fetch updates it. No mapping + linked → the
 * MalLinkSheet (the search → the pick → the mapping stored).
 */
fun DetailsViewModel.openMalTrackSheet() {
    val mid = currentMainId ?: return
    val mal = malTracker ?: return
    val repo = trackEntryRepository ?: return

    viewModelScope.launch {
        val linked = runCatching { mal.isLoggedIn() }.getOrDefault(false)
        if (!linked) {
            // Not linked — open the sheet with the logged-out surface (the
            // AniList path's honest state; the sheet's strings say MAL).
            _activeTrackerType.value = TrackerType.MAL
            _showTrackSheet.value = true
            _trackSheetError.value = null
            _trackEntry.value = null
            _isTrackerLoggedIn.value = false
            return@launch
        }
        val cached = runCatching { repo.get(mid, TrackerType.MAL) }.getOrNull()
        if (cached == null) {
            // ROUND 172: the unified link sheet (fromTracking = true — the
            // pick lands the mapping AND returns here). The v1.1.107 bug:
            // the MalLinkSheet flag was never hosted, so the tap showed
            // NOTHING.
            Logger.i(TAG_MT) { "openMalTrackSheet — no MAL mapping; opening the unified link sheet" }
            openUnifiedLinkSheet("mal", fromTracking = true)
            return@launch
        }
        _activeTrackerType.value = TrackerType.MAL
        _showTrackSheet.value = true
        _trackSheetError.value = null
        _trackEntry.value = cached
        _isTrackerLoggedIn.value = true

        runCatching {
            val remote = mal.fetchEntry(cached.trackerId)
            if (remote != null) {
                val entry = remote.copy(contentKey = mid)
                // ROUND 174 (T4 — the fetch merge): the local-differs case
                // wins (the user's pending input; the bridge re-relays).
                val local = _trackEntry.value
                if (local != null && local.progress != entry.progress) {
                    Logger.w(TAG_MT) {
                        "openMalTrackSheet — the fetch disagreed with the local progress " +
                            "(${entry.progress} vs ${local.progress}); the local keeps"
                    }
                    _trackEntry.value = local
                } else {
                    repo.upsert(entry, TrackerType.MAL)
                    _trackEntry.value = entry
                    Logger.i(TAG_MT) { "openMalTrackSheet — fetched from MAL: $entry" }
                }
            }
        }.onFailure { e ->
            Logger.w(TAG_MT) { "openMalTrackSheet — fetchEntry failed (using cached): ${e.message}" }
        }
    }
}

/** The MalLinkSheet's search (the display fields for the pick rows). */
fun DetailsViewModel.searchMalForLink(query: String) {
    val mal = malTracker ?: return
    viewModelScope.launch {
        _malLinkSearching.value = true
        runCatching { mal.searchWithMeta(query) }
            .onSuccess { _malLinkResults.value = it }
            .onFailure { e ->
                Logger.w(TAG_MT) { "searchMalForLink failed: ${e.message}" }
                _malLinkResults.value = emptyList()
            }
        _malLinkSearching.value = false
    }
}

fun DetailsViewModel.dismissMalLinkSheet() {
    _showMalLinkSheet.value = false
    _malLinkResults.value = emptyList()
}

/**
 * The MalLinkSheet's pick — stores the mapping (tracker_type = 'mal', the
 * PLAN_TO_WATCH seed) and opens the sheet driving MAL.
 */
fun DetailsViewModel.pickMalAnime(malId: Int, totalEpisodes: Int?) {
    val mid = currentMainId ?: return
    val repo = trackEntryRepository ?: return
    _showMalLinkSheet.value = false
    _malLinkResults.value = emptyList()

    // ROUND 171 (the device order): LINK ≠ TRACK — the pick stores the
    // identity mapping ONLY; the tracking sheet never opens from a link.
    _activeTrackerType.value = TrackerType.MAL
    _trackSheetError.value = null
    val seed = TrackEntry(
        contentKey = mid,
        trackerId = malId,
        status = com.confused.anikuta.core.trackerapi.TrackStatus.PLAN_TO_WATCH,
        score = null,
        progress = 0,
        totalEpisodes = totalEpisodes,
        listId = null,
        startedAt = null,
        completedAt = null,
        updatedAt = System.currentTimeMillis(),
    )
    _trackEntry.value = seed
    viewModelScope.launch {
        runCatching { repo.upsert(seed, TrackerType.MAL) }
            .onFailure { e -> Logger.w(TAG_MT) { "pickMalAnime — the mapping seed write failed: ${e.message}" } }
    }
}

/**
 * The MAL save/start body — the pushTrackEntry contract with the MAL
 * tracker + the stored-mapping keying. The local progress follow is shared
 * (tracker-agnostic).
 */
fun DetailsViewModel.pushMalTrackEntry(entry: TrackEntry, flipOptIn: Boolean) {
    val mid = currentMainId ?: return
    val mal = malTracker ?: return
    val repo = trackEntryRepository ?: return

    viewModelScope.launch {
        val mapping = runCatching { repo.get(mid, TrackerType.MAL) }.getOrNull()
        if (mapping == null) {
            _trackSheetError.value = "No MyAnimeList entry is linked — link one first."
            return@launch
        }
        val updated = entry.copy(
            contentKey = mid,
            trackerId = mapping.trackerId,
            updatedAt = System.currentTimeMillis(),
        )
        val oldProgress = _trackEntry.value?.progress ?: 0
        _trackSheetSaving.value = true
        _trackEntry.value = updated
        try {
            if (flipOptIn) {
                val optInOk = runCatching {
                    trackingStateRepository?.setTracked(mid, true, TrackerType.MAL)
                }.isSuccess
                if (!optInOk) {
                    _trackSheetError.value = "Couldn't turn on tracking — nothing was synced. Try again."
                    return@launch
                }
            }
            runCatching { applyTrackProgressToLocal(mid, oldProgress, updated.progress) }
                .onFailure { e ->
                    Logger.w(TAG_MT) { "pushMalTrackEntry — local follow failed (non-fatal): ${e.message}" }
                }
            if (runCatching { mal.isLoggedIn() }.getOrDefault(false)) {
                val success = runCatching { mal.syncEntry(updated) }.getOrDefault(false)
                if (success) {
                    repo.upsert(updated, TrackerType.MAL)
                    _trackEntry.value = updated
                    _trackSheetError.value = null
                    _showTrackSheet.value = false
                    if (flipOptIn) {
                        _trackingNotice.value = TrackingNotice(
                            kind = TrackingNoticeKind.STARTED,
                            id = System.nanoTime(),
                        )
                    }
                } else {
                    _trackSheetError.value =
                        "Couldn't reach MyAnimeList — " +
                            (if (flipOptIn) "tracking is on, but this change didn't sync. Try again."
                             else "this change didn't sync. Try saving again.")
                }
            } else {
                _trackSheetError.value =
                    "Not connected to MyAnimeList — connect in Settings → Trackers to sync."
            }
        } finally {
            _trackSheetSaving.value = false
        }
    }
}

/** The MAL stop-tracking — the opt-in flip for the MAL axis (the removeTracking contract). */
fun DetailsViewModel.removeMalTracking() {
    val mid = currentMainId ?: return
    viewModelScope.launch {
        runCatching {
            trackingStateRepository?.setTracked(mid, false, TrackerType.MAL)
            _showTrackSheet.value = false
            _trackingNotice.value = TrackingNotice(
                kind = TrackingNoticeKind.STOPPED,
                id = System.nanoTime(),
            )
        }.onFailure { e ->
            _trackSheetError.value = "Couldn't stop the tracking: ${e.message}"
        }
    }
}

/** The MAL trash-can — the real remote delete (the deleteFromAniList contract). */
fun DetailsViewModel.deleteFromMal() {
    val mid = currentMainId ?: return
    val mal = malTracker ?: return
    val repo = trackEntryRepository ?: return

    viewModelScope.launch {
        runCatching { trackingStateRepository?.setTracked(mid, false, TrackerType.MAL) }
        val mapping = runCatching { repo.get(mid, TrackerType.MAL) }.getOrNull()
        if (mapping == null) {
            _trackSheetError.value = "No MyAnimeList entry is linked."
            return@launch
        }
        if (!runCatching { mal.isLoggedIn() }.getOrDefault(false)) {
            _trackSheetError.value = "Not connected to MyAnimeList — connect in Settings → Trackers first."
            return@launch
        }
        // MalApi.deleteListStatus rides the tracker's api through logout-free
        // auth — the tracker exposes the delete honestly.
        val deleted = runCatching { mal.deleteEntry(mapping.trackerId) }
            .onFailure { e -> Logger.w(TAG_MT) { "deleteFromMal — remote delete failed: ${e.message}" } }
            .getOrDefault(false)
        if (deleted) {
            runCatching { repo.delete(mid, TrackerType.MAL) }
            _trackEntry.value = null
            _showTrackSheet.value = false
        } else {
            _trackSheetError.value = "Couldn't delete it from MyAnimeList — check your connection and try again."
        }
    }
}

// ── ROUND 170: THE SIMKL CLUSTER (the MAL cluster's mirror — the picker's
//    third row; the one-tracker rule opens these for a Simkl-tracked
//    content). The Simkl mapping lives in track_entry (tracker_type =
//    'simkl'); the id resolution: the entry's trackerId rides Simkl's own
//    id bridge (the anilist/tmdb ids resolve server-side).

/** The Simkl open path — the mapping lookup, then the sheet driving Simkl. */
fun DetailsViewModel.openSimklTrackSheet() {
    val mid = currentMainId ?: return
    val simkl = simklTracker ?: return
    val repo = trackEntryRepository ?: return

    viewModelScope.launch {
        val linked = runCatching { simkl.isLoggedIn() }.getOrDefault(false)
        if (!linked) {
            _activeTrackerType.value = TrackerType.SIMKL
            _showTrackSheet.value = true
            _trackSheetError.value = null
            _trackEntry.value = null
            _isTrackerLoggedIn.value = false
            return@launch
        }
        val cached = runCatching { repo.get(mid, TrackerType.SIMKL) }.getOrNull()
        if (cached == null) {
            // ROUND 172: the unified link sheet (the same fromTracking return).
            Logger.i(TAG_MT) { "openSimklTrackSheet — no Simkl mapping; opening the unified link sheet" }
            openUnifiedLinkSheet("simkl", fromTracking = true)
            return@launch
        }
        _activeTrackerType.value = TrackerType.SIMKL
        _showTrackSheet.value = true
        _trackSheetError.value = null
        _trackEntry.value = cached
        _isTrackerLoggedIn.value = true

        runCatching {
            val remote = simkl.fetchEntry(cached.trackerId)
            if (remote != null) {
                val entry = remote.copy(contentKey = mid)
                // ROUND 174 (T4 — the fetch merge): the same local-wins rule.
                val local = _trackEntry.value
                if (local != null && local.progress != entry.progress) {
                    Logger.w(TAG_MT) {
                        "openSimklTrackSheet — the fetch disagreed with the local progress " +
                            "(${entry.progress} vs ${local.progress}); the local keeps"
                    }
                    _trackEntry.value = local
                } else {
                    repo.upsert(entry, TrackerType.SIMKL)
                    _trackEntry.value = entry
                }
            }
        }.onFailure { e ->
            Logger.w(TAG_MT) { "openSimklTrackSheet — fetchEntry failed (using cached): ${e.message}" }
        }
    }
}

/** The SimklLinkSheet's search (the display fields). */
fun DetailsViewModel.searchSimklForLink(query: String) {
    val simkl = simklTracker ?: return
    viewModelScope.launch {
        _simklLinkSearching.value = true
        runCatching { simkl.searchItems(query) }
            .onSuccess { _simklLinkResults.value = it }
            .onFailure { e ->
                Logger.w(TAG_MT) { "searchSimklForLink failed: ${e.message}" }
                _simklLinkResults.value = emptyList()
            }
        _simklLinkSearching.value = false
    }
}

fun DetailsViewModel.dismissSimklLinkSheet() {
    _showSimklLinkSheet.value = false
    _simklLinkResults.value = emptyList()
}

/** The SimklLinkSheet's pick — the mapping seed + the sheet driving Simkl. */
fun DetailsViewModel.pickSimklItem(simklId: Int) {
    val mid = currentMainId ?: return
    val repo = trackEntryRepository ?: return
    _showSimklLinkSheet.value = false
    _simklLinkResults.value = emptyList()

    // ROUND 171: LINK ≠ TRACK — the mapping stores only.
    _activeTrackerType.value = TrackerType.SIMKL
    _trackSheetError.value = null
    val seed = TrackEntry(
        contentKey = mid,
        trackerId = simklId,
        status = TrackStatus.PLAN_TO_WATCH,
        progress = 0,
        totalEpisodes = null,
        listId = null,
        startedAt = null,
        completedAt = null,
        updatedAt = System.currentTimeMillis(),
    )
    _trackEntry.value = seed
    viewModelScope.launch {
        runCatching { repo.upsert(seed, TrackerType.SIMKL) }
            .onFailure { e -> Logger.w(TAG_MT) { "pickSimklItem — the mapping seed write failed: ${e.message}" } }
    }
}

/** The Simkl save/start body — the pushTrackEntry contract on the Simkl axis. */
fun DetailsViewModel.pushSimklTrackEntry(entry: TrackEntry, flipOptIn: Boolean) {
    val mid = currentMainId ?: return
    val simkl = simklTracker ?: return
    val repo = trackEntryRepository ?: return

    viewModelScope.launch {
        val mapping = runCatching { repo.get(mid, TrackerType.SIMKL) }.getOrNull()
        if (mapping == null) {
            _trackSheetError.value = "No Simkl entry is linked — link one first."
            return@launch
        }
        val updated = entry.copy(
            contentKey = mid,
            trackerId = mapping.trackerId,
            updatedAt = System.currentTimeMillis(),
        )
        val oldProgress = _trackEntry.value?.progress ?: 0
        _trackSheetSaving.value = true
        _trackEntry.value = updated
        try {
            if (flipOptIn) {
                val optInOk = runCatching {
                    trackingStateRepository?.setTracked(mid, true, TrackerType.SIMKL)
                }.isSuccess
                if (!optInOk) {
                    _trackSheetError.value = "Couldn't turn on tracking — nothing was synced. Try again."
                    return@launch
                }
            }
            runCatching { applyTrackProgressToLocal(mid, oldProgress, updated.progress) }
                .onFailure { e ->
                    Logger.w(TAG_MT) { "pushSimklTrackEntry — local follow failed (non-fatal): ${e.message}" }
                }
            if (runCatching { simkl.isLoggedIn() }.getOrDefault(false)) {
                val success = runCatching { simkl.syncEntry(updated) }.getOrDefault(false)
                if (success) {
                    repo.upsert(updated, TrackerType.SIMKL)
                    _trackEntry.value = updated
                    _trackSheetError.value = null
                    _showTrackSheet.value = false
                    if (flipOptIn) {
                        _trackingNotice.value = TrackingNotice(
                            kind = TrackingNoticeKind.STARTED,
                            id = System.nanoTime(),
                        )
                    }
                } else {
                    _trackSheetError.value =
                        "Couldn't reach Simkl — " +
                            (if (flipOptIn) "tracking is on, but this change didn't sync. Try again."
                             else "this change didn't sync. Try saving again.")
                }
            } else {
                _trackSheetError.value =
                    "Not connected to Simkl — connect in Settings → Trackers to sync."
            }
        } finally {
            _trackSheetSaving.value = false
        }
    }
}

/** The Simkl stop-tracking — the opt-in flip on the Simkl axis. */
fun DetailsViewModel.removeSimklTracking() {
    val mid = currentMainId ?: return
    viewModelScope.launch {
        runCatching {
            trackingStateRepository?.setTracked(mid, false, TrackerType.SIMKL)
            _showTrackSheet.value = false
            _trackingNotice.value = TrackingNotice(
                kind = TrackingNoticeKind.STOPPED,
                id = System.nanoTime(),
            )
        }.onFailure { e ->
            _trackSheetError.value = "Couldn't stop the tracking: ${e.message}"
        }
    }
}

/** The Simkl trash-can — the un-mark path (Simkl has no hard delete; the history remove + the mapping clear). */
fun DetailsViewModel.deleteFromSimkl() {
    val mid = currentMainId ?: return
    val simkl = simklTracker ?: return
    val repo = trackEntryRepository ?: return

    viewModelScope.launch {
        runCatching { trackingStateRepository?.setTracked(mid, false, TrackerType.SIMKL) }
        val mapping = runCatching { repo.get(mid, TrackerType.SIMKL) }.getOrNull()
        if (mapping == null) {
            _trackSheetError.value = "No Simkl entry is linked."
            return@launch
        }
        if (!runCatching { simkl.isLoggedIn() }.getOrDefault(false)) {
            _trackSheetError.value = "Not connected to Simkl — connect in Settings → Trackers first."
            return@launch
        }
        // The local mapping clears (the honest Simkl-side removal = the
        // status overwrite via the next sync; the v1 keeps it simple: the
        // mapping + the opt-in clear).
        runCatching { repo.delete(mid, TrackerType.SIMKL) }
        _trackEntry.value = null
        _showTrackSheet.value = false
        Logger.i(TAG_MT) { "deleteFromSimkl — the mapping cleared for mainId=$mid" }
    }
}


// ── ROUND 171: THE UNIFIED LINK FLOW — ONE sheet for MAL/Simkl/TMDB/TVmaze
//    (the device order: the same UI layer, the different backends). The
//    pick handlers: the MAL/Simkl mappings (the LINK ≠ TRACK separation);
//    the TMDB/TVmaze picks enrich the page from that source's data (the
//    "the data source displays the data" semantics).

fun DetailsViewModel.openUnifiedLinkSheet(source: String, fromTracking: Boolean = false) {
    _showTrackSheet.value = false
    // ROUND 172: the tracking flows' no-mapping reroute lands here with
    // fromTracking=true — the pick then opens the tracker's sheet. The
    // LINK entry ("Link to other") leaves it false: the pick stores the
    // identity ONLY (the LINK ≠ TRACK rule).
    _unifiedLinkFromTracking.value = fromTracking
    _showUnifiedLinkSheet.value = source
    _unifiedLinkResults.value = emptyList()
    _unifiedLinkError.value = null // ROUND 173 (F5): every open starts clean
}

fun DetailsViewModel.dismissUnifiedLinkSheet() {
    _showUnifiedLinkSheet.value = null
    _unifiedLinkResults.value = emptyList()
    _unifiedLinkFromTracking.value = false
    _unifiedLinkError.value = null
}

fun DetailsViewModel.searchUnifiedLink(source: String, query: String) {
    viewModelScope.launch {
        _unifiedLinkSearching.value = true
        _unifiedLinkError.value = null // ROUND 173 (F5): each attempt starts clean
        val results: List<UnifiedLinkResult> = try {
            when (source) {
                // ROUND 172: the AniList backend joins — ONE sheet for ALL 5
                // sources. The ratings normalize to the 10-scale for the
                // shared meta row (AniList's averageScore is 0-100).
                "anilist" -> anilistApi.searchAnime(query, page = 1, perPage = 20).map { node ->
                    UnifiedLinkResult(
                        id = node.id,
                        title = node.displayName,
                        posterUrl = node.coverUrl,
                        rating = node.averageScore?.toDouble()?.div(10.0),
                        year = node.seasonYear,
                    )
                }
                "mal" -> malTracker?.searchWithMeta(query).orEmpty().map { node ->
                    UnifiedLinkResult(
                        id = node.id,
                        title = node.title ?: "—",
                        posterUrl = node.main_picture?.medium ?: node.main_picture?.large,
                        rating = node.mean?.toDouble(),
                        year = node.start_season?.year,
                    )
                }
                "simkl" -> simklTracker?.searchItems(query).orEmpty().mapNotNull { node ->
                    val id = (node.ids?.simkl_id ?: node.ids?.simkl ?: node.simkl_id)?.toInt() ?: return@mapNotNull null
                    UnifiedLinkResult(
                        id = id,
                        title = node.title ?: "—",
                        posterUrl = node.poster?.let { "https://simkl.in/posters/" + it + "_m.jpg" } ?: node.poster_url,
                        rating = null,
                        year = node.release_date?.takeLast(4)?.toIntOrNull(),
                    )
                }
                "tmdb" -> tmdbApi?.search(query).orEmpty().map { item ->
                    UnifiedLinkResult(
                        id = item.id,
                        title = item.title,
                        posterUrl = item.posterUrl,
                        rating = item.rating,
                        year = item.year,
                        // ROUND 176 (S1): the mediaType rides — the pick
                        // writes the FINE identity kind from it (the movie/
                        // TV id sequences overlap; the coarse key collided).
                        mediaType = item.mediaType,
                    )
                }
                "tvmaze" -> tvmazeApi?.search(query).orEmpty().map { show ->
                    UnifiedLinkResult(
                        id = show.id,
                        title = show.title,
                        posterUrl = show.posterUrl,
                        rating = show.rating?.toDouble(),
                        year = show.year,
                    )
                }
                else -> emptyList()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // ROUND 173 (F5): the surfaced reason — SimklRequestException/
            // MalRequestException (the strict getters) carry the real why;
            // the sheet renders it with the Search again button.
            Logger.w(TAG_MT) { "searchUnifiedLink failed ($source): " + (e.message ?: "") }
            _unifiedLinkError.value = e.message ?: "The search failed — try again"
            emptyList()
        }
        _unifiedLinkResults.value = results
        _unifiedLinkSearching.value = false
    }
}

/**
 * ROUND 172: the unified pick LINKS FOR REAL on every source:
 *  • anilist — the extension entry → linkAniListEntry (the proven cache +
 *    persist + merge + display-switch path); the metadata entry →
 *    mergeAniListIntoUnified (the fetch + the DB axis + the remerge).
 *  • mal/simkl — the track_entry mapping (the LINK ≠ TRACK identity), and
 *    when the entry point was TRACKING (the no-mapping reroute) the
 *    tracker's sheet opens after the mapping lands.
 *  • tmdb/tvmaze — the NATIVE content → the track_entry identity row (the
 *    generic mapping table; the tracking dispatch never consults these
 *    types); the METADATA row → the picked entry's data merges into the
 *    data axis (the v1.1.107 semantics the device approved).
 * Every branch refreshes _linkedIdentities (the menu's linked state).
 */
fun DetailsViewModel.pickUnifiedLink(source: String, result: UnifiedLinkResult) {
    val fromTracking = _unifiedLinkFromTracking.value
    _showUnifiedLinkSheet.value = null
    _unifiedLinkResults.value = emptyList()
    _unifiedLinkFromTracking.value = false
    val mid = currentMainId ?: return
    when (source) {
        "anilist" -> {
            val anime = (state.value as? DetailsState.Success)?.anime
            if (anime?.sourceId != null && anime.animeUrl != null) {
                linkAniListEntry(result.id)
                refreshLinkedIdentities()
            } else {
                viewModelScope.launch {
                    runCatching { mergeAniListIntoUnified(result.id) }
                        .onFailure { e ->
                            Logger.w(TAG_MT) { "the metadata AniList link failed: " + (e.message ?: "") }
                        }
                    refreshLinkedIdentities()
                }
            }
        }
        "mal", "simkl" -> {
            val repo = trackEntryRepository ?: return
            val type = if (source == "mal") TrackerType.MAL else TrackerType.SIMKL
            viewModelScope.launch {
                runCatching {
                    repo.upsert(
                        TrackEntry(
                            contentKey = mid,
                            trackerId = result.id,
                            status = TrackStatus.PLAN_TO_WATCH,
                            progress = 0,
                            totalEpisodes = null,
                            listId = null,
                            startedAt = null,
                            completedAt = null,
                            updatedAt = System.currentTimeMillis(),
                        ),
                        type,
                    )
                }.onFailure { e ->
                    Logger.w(TAG_MT) { "the $source link store failed: " + (e.message ?: "") }
                }
                // ROUND 182 (THE DEVICE REPORT — the missing MAL chip): this
                // branch wrote ONLY the track_entry mapping — no
                // content_identity row — so the pick produced NO CHIP (the
                // menu's Unlink row came from the union read of the MAPPINGS,
                // which masked the gap) and NO PAYLOAD (the N-way display had
                // nothing to show: the user's "the data did not change"). The
                // identity row rides now, carrying the link-time payload
                // (title/cover/score/year) exactly like the tmdb/tvmaze
                // branch; the claim guard refuses cross-main steals.
                contentRepository?.upsertIdentity(
                    com.confused.anikuta.core.content.ContentIdentity(
                        mainId = mid,
                        sourceKind = source,
                        sourceRefId = result.id.toString(),
                        displayTitle = result.title,
                        coverUrl = result.posterUrl,
                        synopsis = null,
                        score = result.rating?.let { (it * 10).toInt() },
                        episodes = null,
                        status = null,
                        genres = null,
                        year = result.year,
                        displayRank = 2,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                refreshLinkedIdentities()
                // The TRACKING entry's return path: the mapping now exists →
                // the tracker's sheet opens driving that tracker.
                if (fromTracking) {
                    if (source == "mal") openMalTrackSheet() else openSimklTrackSheet()
                }
            }
        }
        "tmdb", "tvmaze" -> {
            // ROUND 173 (N2): BOTH paths write the content_identity ROW — the
            // authoritative multi-link record (the reverse index resolves the
            // reopens; the menu's Unlink rows read this table).
            val identityRepo = contentRepository
            if (identityRepo != null) {
                viewModelScope.launch {
                    runCatching {
                        val now = System.currentTimeMillis()
                        // ROUND 176 (S1): the FINE identity kind — the TMDB
                        // pick writes tmdb_movie/tmdb_tv from the result's
                        // mediaType (the coarse (tmdb, id) key collided
                        // across the overlapping movie/TV id sequences).
                        val fineKind = if (source == "tmdb") {
                            if (result.mediaType == "tv") "tmdb_tv" else "tmdb_movie"
                        } else {
                            source
                        }
                        // ROUND 178 (the audit — DB-2): the claim guard —
                        // upsertIdentity REFUSES when the (kind, ref) pair is
                        // already claimed by ANOTHER content (the old REPLACE
                        // silently ERASED that content's identity row). The
                        // refusal surfaces instead of silently no-oping.
                        val claimed = identityRepo.upsertIdentity(
                            com.confused.anikuta.core.content.ContentIdentity(
                                mainId = mid,
                                sourceKind = fineKind,
                                sourceRefId = result.id.toString(),
                                displayTitle = result.title,
                                coverUrl = result.posterUrl,
                                synopsis = null,
                                score = result.rating?.let { (it * 10).toInt() },
                                episodes = null,
                                status = null,
                                genres = null,
                                year = result.year,
                                displayRank = 2,
                                createdAt = now,
                                updatedAt = now,
                            ),
                        )
                        if (claimed) {
                            Logger.i(TAG_MT) { "the $fineKind identity linked: " + result.title }
                        } else {
                            Logger.w(TAG_MT) { "the $fineKind identity REFUSED (claimed by another content)" }
                            com.confused.anikuta.core.designsystem.component.toast.AppToast.show(
                                "Already linked to another entry",
                                com.confused.anikuta.core.designsystem.component.toast.AppToastTone.NEUTRAL,
                            )
                        }
                    }
                    refreshLinkedIdentities()
                }
            }
            if (!mid.contains(":") || mid.substringBefore(":") in setOf("anilist", "cloudstream")) {
                // The NATIVE content: the track_entry mapping ALSO stays (the
                // LINK ≠ TRACK identity row; the tracking flows never consult
                // the TMDB/TVMAZE types).
                val repo = trackEntryRepository ?: return
                val type = if (source == "tmdb") TrackerType.TMDB else TrackerType.TVMAZE
                viewModelScope.launch {
                    runCatching {
                        repo.upsert(
                            TrackEntry(
                                contentKey = mid,
                                trackerId = result.id,
                                status = TrackStatus.PLAN_TO_WATCH,
                                progress = 0,
                                totalEpisodes = null,
                                listId = null,
                                startedAt = null,
                                completedAt = null,
                                updatedAt = System.currentTimeMillis(),
                            ),
                            type,
                        )
                    }.onFailure { e ->
                        Logger.w(TAG_MT) { "the $source identity store failed: " + (e.message ?: "") }
                    }
                }
            } else {
                // The METADATA row: the data-axis merge rides ONLY for the
                // row's OWN kind (the v1.1.107 same-kind enrichment the
                // device approved). The audit landmine 6: the old full-row
                // upsert WIPED the ext axis on a linked row — gone.
                val ownKind = mid.substringBefore(":")
                // ROUND 176 (S1): the fine mainIds (tmdb_movie:/tmdb_tv:) —
                // the own-kind check matches the fine prefix; the data-axis
                // write carries the FINE type (the enrichment's
                // tmdb_movie/tmdb_tv switch works for these rows again —
                // the v1.1.109 bare-tmdb write silently broke it).
                if (source == ownKind || ownKind.startsWith("tmdb")) {
                    val fineAxisKind = if (ownKind == "tmdb_tv" || result.mediaType == "tv") "tmdb_tv" else "tmdb_movie"
                    viewModelScope.launch {
                        runCatching {
                            val repo = contentRepository ?: return@launch
                            val now = System.currentTimeMillis()
                            repo.upsertContentDetails(
                                com.confused.anikuta.core.content.ContentDetails(
                                    mainId = mid,
                                    dataSourceType = fineAxisKind,
                                    dataSourceRefId = result.id.toString(),
                                    dataCoverUrl = result.posterUrl,
                                    dataUpdatedAt = now,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * ROUND 172: the per-source UNLINK (the menu's Unlink rows). anilist
 * routes by the content kind (the extension path / the metadata reload);
 * the other four clear their track_entry identity row.
 */
fun DetailsViewModel.unlinkUnifiedIdentity(source: String) {
    if (source == "anilist") {
        if (_isMetadataEntry.value) unlinkAniListFromMetadata() else unlinkAniList()
        return
    }
    val mid = currentMainId ?: return
    val type = when (source) {
        "mal" -> TrackerType.MAL
        "simkl" -> TrackerType.SIMKL
        // ROUND 178 (the audit — C/DB-5): the FINE kinds route here too — the
        // v8 picks wrote tmdb_movie/tmdb_tv identity rows, the menu's key
        // stayed "tmdb", and the else->return silently DROPPED the unlink
        // (nothing happened, no log — the Unlink row reappeared forever).
        "tmdb", "tmdb_movie", "tmdb_tv" -> TrackerType.TMDB
        "tvmaze" -> TrackerType.TVMAZE
        else -> return
    }
    val repo = trackEntryRepository ?: return
    // ROUND 175 (F6.2): the OWN-KIND determination — unlinking the row's
    // own source (tmdb unlink on tmdb:123) deletes the content's PRIMARY
    // identity: the metadataBase must clear and the display must re-derive
    // from the REMAINING identities (the ext base) — the v1.1.110 shape
    // kept the TMDB cover/synopsis forever after the unlink.
    val ownKind = mid.substringBefore(":")
    // ROUND 178 (the audit — C): the FINE mainIds are OWN kinds for the tmdb
    // unlink (tmdb_movie:123 IS the row's TMDB identity).
    val isOwnKind = source == ownKind || (source == "tmdb" && ownKind.startsWith("tmdb"))
    viewModelScope.launch {
        runCatching { repo.delete(mid, type) }
            .onFailure { e ->
                Logger.w(TAG_MT) { "unlink $source failed: " + (e.message ?: "") }
            }
        // ROUND 173 (N2): the identity row goes with the unlink.
        // ROUND 178 (the audit — C): the FINE-KIND sweep — deleteIdentity is
        // an exact source_kind match; the v8 picks wrote tmdb_movie/tmdb_tv
        // rows that the "tmdb" delete silently MISSED (the row survived and
        // could re-elect itself).
        runCatching {
            contentRepository.identitiesForContent(mid)
                .filter { it.sourceKind == type.id || it.sourceKind.startsWith("${type.id}_") }
                .forEach { contentRepository.deleteIdentity(mid, it.sourceKind) }
        }
        if (isOwnKind) {
            Logger.i(TAG_MT) { "unlink $source = the row's OWN kind — the display re-derives from the remaining identities" }
            metadataBase = null
            _displaySourceKind.value = null
            if (extensionBase != null) {
                remergeBases(com.confused.anikuta.core.common.model.DataSourcePriority.EXTENSION)
            }
            // The data axis in content_details nulls too (the row's identity
            // is gone — the next load must not re-hydrate the deleted data).
            runCatching {
                contentRepository.upsertContentDetails(
                    com.confused.anikuta.core.content.ContentDetails(
                        mainId = mid,
                        dataSourceType = null,
                        dataSourceRefId = null,
                        dataUpdatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
        refreshLinkedIdentities()
        Logger.i(TAG_MT) { "the $source identity unlinked for $mid (own-kind=$isOwnKind)" }
    }
}
