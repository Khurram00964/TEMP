package com.confused.anikuta.feature.animedetails

import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.trackerapi.TrackEntry
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

/** The tracking entry point — the 3-dot menu + the tracking button call THIS. */
fun DetailsViewModel.openTrackerPickerOrSheet() {
    val mal = malTracker
    val anilist = aniListTracker
    if (mal == null || anilist == null) {
        openTrackSheet()
        return
    }
    viewModelScope.launch {
        val anilistLinked = runCatching { anilist.isLoggedIn() }.getOrDefault(false)
        val malLinked = runCatching { mal.isLoggedIn() }.getOrDefault(false)
        if (anilistLinked && malLinked) {
            // >1 linked → the picker decides (the spec's rule).
            _showTrackerPicker.value = true
        } else {
            _activeTrackerType.value = if (malLinked) TrackerType.MAL else TrackerType.ANILIST
            openTrackSheet()
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
    if (type == TrackerType.MAL) openMalTrackSheet() else openTrackSheet()
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
            Logger.i(TAG_MT) { "openMalTrackSheet — no MAL mapping; opening MalLinkSheet" }
            _showMalLinkSheet.value = true
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
                repo.upsert(entry, TrackerType.MAL)
                _trackEntry.value = entry
                Logger.i(TAG_MT) { "openMalTrackSheet — fetched from MAL: $entry" }
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

    _activeTrackerType.value = TrackerType.MAL
    _showTrackSheet.value = true
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
