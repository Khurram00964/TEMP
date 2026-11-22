package com.confused.anikuta.feature.animedetails

import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.common.Logger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// The moved bodies log through the same tag VERBATIM — a file-local mirror of
// DetailsViewModel.TAG (the companion's copy serves the in-class members).
private const val TAG = "Anikuta:Feature:Details"

// ── D-242: AniList Tracking — the method cluster ──
// 2026-11-21 (the god-file normalization): moved VERBATIM out of
// DetailsViewModel.kt (the round-160 review's hotspot list). Same package,
// extension functions on the ViewModel: zero behavior change, zero call-site
// changes (the screens + the host call them unchanged). The cluster's state
// fields (the track-sheet / notice / entry / prompt StateFlows), the
// isTracked member, and the pendingRemoteTrackEntry init-block observer stay
// IN DetailsViewModel.kt — real fields initialized once (an extension
// property would re-instantiate the flow on every read). Their visibility
// widened private → internal, module-only, for exactly this file.
//
// The three private helpers below are FILE-SCOPED extensions — the same
// reachability they had as private members (only this cluster called them).
    /**
     * D-242: Opens the TrackSheet. Pre-loads the cached track entry + fetches
     * the latest from AniList in the background (so the sheet opens instantly
     * with cached data + updates when the network fetch completes).
     *
     * If the anime has no AniList link, opens the ManualLinkSheet instead
     * (the user must link the anime to AniList before tracking).
     */
fun DetailsViewModel.openTrackSheet() {
        val mid = currentMainId ?: return
        val tracker = aniListTracker ?: return
        val repo = trackEntryRepository ?: return

        val anime = (state.value as? DetailsState.Success)?.anime
        val anilistId = anime?.anilistId
        if (anilistId == null) {
            // No AniList link — open the manual link sheet instead.
            Logger.i(TAG) { "openTrackSheet — no AniList link; opening ManualLinkSheet" }
            openManualLinkSheet()
            return
        }

        _showTrackSheet.value = true
        // ROUND 102 (WS-E): every open starts with a clean error surface
        // (the previous session's outcome message doesn't linger).
        _trackSheetError.value = null

        viewModelScope.launch {
            // 1. Load the cached entry (instant).
            val cached = repo.get(mid)
            _trackEntry.value = cached

            // 2. Check login state.
            _isTrackerLoggedIn.value = tracker.isLoggedIn()

            // 3. Fetch the latest from AniList (background).
            if (tracker.isLoggedIn()) {
                runCatching {
                    val remote = tracker.fetchEntry(anilistId)
                    if (remote != null) {
                        val entry = remote.copy(contentKey = mid)
                        // ROUND 174 (T4 — THE FETCH MERGE): a local progress
                        // that DIFFERS from the fetched remote is the user's
                        // pending input (the marks not yet relayed) — the
                        // local WINS and the cache stays (the bridge
                        // re-relays); the v1.1.109 shape assigned the remote
                        // unconditionally, silently overwriting the user's
                        // marks (the "shows progress 2" report).
                        val local = _trackEntry.value
                        if (local != null && local.progress != entry.progress) {
                            Logger.w(TAG) {
                                "openTrackSheet — the fetch disagreed with the local progress " +
                                    "(${entry.progress} vs ${local.progress}); the local keeps"
                            }
                            _trackEntry.value = local
                        } else {
                            repo.upsert(entry)
                            _trackEntry.value = entry
                            Logger.i(TAG) { "openTrackSheet — fetched from AniList: $entry" }
                        }
                    }
                }.onFailure { e ->
                    Logger.w(TAG) { "openTrackSheet — fetchEntry failed (using cached): ${e.message}" }
                }
            }
        }
    }

    /** Dismisses the TrackSheet. */
fun DetailsViewModel.dismissTrackSheet() {
        _showTrackSheet.value = false
    }

    /** Clears the TrackSheet's error surface (sheet re-open / retry). */
fun DetailsViewModel.clearTrackSheetError() {
        _trackSheetError.value = null
    }

    /**
     * ROUND 102 (WS-E) → ROUND 103 (WS-2) AMENDED: THE SAVE BUTTON — sync
     * ONLY.
     *
     * The v1.1.59 device round's explicit order: "the save button should
     * actually not start the tracking system, but it should only be for
     * saving… it should only update it in any list. It should not link both
     * of them together." Saving now means:
     *  1. The draft's PROGRESS drives the local watch_progress follow (the
     *     D-242-fix5 semantics: decreasing unmarks the trailing episodes,
     *     increasing marks the new ones).
     *  2. The remote sync — on success the cache (the confirmed truth) is
     *     updated; on failure the ERROR surfaces and the cache keeps the
     *     last confirmed state (no false "AniList has it" positives).
     * The OPT-IN FLIP that used to live here (the round-102 D-694 "Save =
     * the opt-in" step) MOVED to [startTracking] — this round's order.
     */
fun DetailsViewModel.saveTrackEntry(entry: com.confused.anikuta.core.trackerapi.TrackEntry) {
        pushTrackEntry(entry, flipOptIn = false)
    }

    /**
     * ROUND 103 (WS-2): START TRACKING — the opt-in path.
     *
     * The untracked sheet's primary button ("it should give me the option to
     * start tracking"): the full round-102 Save contract — the OPT-IN flip
     * (linking the app's tracking to the AniList entry: from this moment the
     * bridge relays progress both ways) + the local progress follow + the
     * remote sync. Shares [pushTrackEntry]'s body with Save; only the flip
     * differs.
     */
fun DetailsViewModel.startTracking(entry: com.confused.anikuta.core.trackerapi.TrackEntry) {
        pushTrackEntry(entry, flipOptIn = true)
    }

    /**
     * The shared Save/Start body (ROUND 103): the local progress follow +
     * the remote sync + the stay-open error surface (the SA2-F2 shape),
     * with [flipOptIn] controlling ONLY the tracking opt-in write.
     */
private fun DetailsViewModel.pushTrackEntry(
        entry: com.confused.anikuta.core.trackerapi.TrackEntry,
        flipOptIn: Boolean,
    ) {
        val mid = currentMainId ?: return
        val tracker = aniListTracker ?: return
        val repo = trackEntryRepository ?: return
        val anime = (state.value as? DetailsState.Success)?.anime ?: return
        val anilistId = anime.anilistId ?: return

        val updated = entry.copy(
            contentKey = mid,
            trackerId = anilistId,
            updatedAt = System.currentTimeMillis(),
        )
        // Capture the OLD progress BEFORE the optimistic write below — the
        // local watch-progress follow ranges from it (the D-242-fix5 math).
        val oldProgress = _trackEntry.value?.progress ?: 0
        // SA2-F2: the sheet STAYS OPEN (Saving…) until the sync resolves —
        // success closes it, failure renders the error inline. A double-tap
        // is blocked by the saving flag (the buttons disable).
        _trackSheetSaving.value = true
        _trackEntry.value = updated

        viewModelScope.launch {
            try {
                // ROUND 121 (D2-b): on the FIRST TRACK the started date is
                // seeded from the LOCAL watch history — the earliest
                // watch_progress.first_watched_at of this content. The sheet's
                // fresh draft carries startedAt = null, so without this the
                // first sync wrote NO start date to AniList (and the relay
                // paths' "now" fallback stamped the SYNC time, not the time
                // the user actually started watching). The seeded value rides
                // the sync + the confirmed cache, so the sheet's Started row
                // reads it back on the next open.
                var entryToSync = updated
                if (flipOptIn) {
                    // D-242-fix discipline: capture into a local for the smart
                    // cast (public API properties don't smart-cast cross-module).
                    val draftStartedAt = updated.startedAt
                    if (draftStartedAt == null || draftStartedAt <= 0L) {
                        val firstWatch = runCatching { earliestFirstWatchedAt(mid) }.getOrNull()
                        if (firstWatch != null) {
                            entryToSync = updated.copy(startedAt = firstWatch)
                            Logger.i(TAG) { "pushTrackEntry — seeded startedAt from first watch: $firstWatch" }
                        }
                    }
                }

                // 1. THE OPT-IN — ONLY on the Start Tracking path (ROUND 103:
                //    Save must NOT link the app and AniList together).
                //    SA1-F2 fix (lead-verified): a FAILED opt-in write ABORTS
                //    with the error surface — the sheet stays open, nothing
                //    syncs, no false "tracking started" positive (a silent
                //    swallow would leave a synced remote entry the app never
                //    relays progress for).
                if (flipOptIn) {
                    val optInOk = runCatching { trackingStateRepository?.setTracked(mid, true) }.isSuccess
                    if (!optInOk) {
                        _trackSheetError.value =
                            "Couldn't turn on tracking — nothing was synced. Try again."
                        Logger.e(TAG) { "startTracking — tracking opt-in failed; aborting before the sync" }
                        return@launch
                    }
                }

                // 2. The local watch-progress follow (the D-242-fix5 ranges, on
                //    the identity numbers — CS sub/dub pairs collapse to one).
                runCatching { applyTrackProgressToLocal(mid, oldProgress, updated.progress) }
                    .onFailure { e ->
                        Logger.w(TAG) { "pushTrackEntry — local progress follow failed (non-fatal): ${e.message}" }
                    }

                // 3. The remote sync (+ the confirmed cache on success).
                // ROUND 121 (D2-b): entryToSync (the seeded-startedAt copy)
                // rides the sync AND the cache — the Started row reads back.
                if (tracker.isLoggedIn()) {
                    val success = runCatching { tracker.syncEntry(entryToSync) }
                        .onFailure { e ->
                            Logger.w(TAG) { "pushTrackEntry — sync failed: ${e.message}" }
                        }
                        .getOrDefault(false)
                    if (success) {
                        repo.upsert(entryToSync)
                        _trackEntry.value = entryToSync
                        _trackSheetError.value = null
                        _showTrackSheet.value = false
                        // ROUND 104 (WS-B): the beautiful toast — "after doing
                        // that it should show me a toast notification… saying
                        // that tracking started". Fires ONLY on the Start
                        // Tracking path (Save stays silent — it links nothing).
                        if (flipOptIn) {
                            _trackingNotice.value = TrackingNotice(
                                kind = TrackingNoticeKind.STARTED,
                                id = System.nanoTime(),
                            )
                        }
                        Logger.i(TAG) { "pushTrackEntry(flipOptIn=$flipOptIn) — synced + cached: $entryToSync" }
                    } else {
                        _trackSheetError.value =
                            "Couldn't reach AniList — " +
                                (if (flipOptIn) "tracking is on, but this change didn't sync. Try again."
                                 else "this change didn't sync. Try saving again.")
                    }
                } else {
                    _trackSheetError.value =
                        "Not connected to AniList — connect in Settings → Trackers to sync."
                }
            } finally {
                _trackSheetSaving.value = false
            }
        }
    }

    /**
     * The D-242-fix5 local follow (moved from the old updateTrackProgress's
     * immediate-save body): a progress decrease unmarks the trailing
     * episodes' watch progress; an increase marks the new range.
     */
// ROUND 168: private → internal — the multi-tracker cluster
// (DetailsViewModelMultiTracker.kt) shares the local progress follow (the
// D-923 widening pattern; module-only reachability).
internal suspend fun DetailsViewModel.applyTrackProgressToLocal(mid: String, oldProgress: Int, newProgress: Int) {
        if (oldProgress == newProgress) return
        val episodes = (episodeState.value as? EpisodeState.Loaded)?.episodes ?: return
        if (episodes.isEmpty()) return
        val identities = csEpisodeIdentities(mid, episodes)
        if (newProgress < oldProgress) {
            identities
                .filter { it.first in (newProgress + 1)..oldProgress }
                .map { it.second }
                .distinct()
                .forEach { key -> watchProgressStore.delete(key) }
            Logger.i(TAG) { "applyTrackProgressToLocal — unmarked episodes ${newProgress + 1}..$oldProgress" }
        } else {
            val keysToMark = identities
                .filter { it.first in (oldProgress + 1)..newProgress }
                .map { it.second }
                .distinct()
            if (keysToMark.isNotEmpty()) {
                watchProgressStore.markAllWatched(mid, keysToMark)
                Logger.i(TAG) { "applyTrackProgressToLocal — marked episodes ${oldProgress + 1}..$newProgress" }
            }
        }
    }

    /**
     * ROUND 121 (D2-b): the earliest first_watched_at in the content's LOCAL
     * watch history — the honest "started watching" date for the first-track
     * startedAt seed (pushTrackEntry). Null when the user has no completed
     * local watch rows (a fresh track with no history keeps startedAt null —
     * the relay paths' "now" stamp covers it from there).
     */
private suspend fun DetailsViewModel.earliestFirstWatchedAt(mainId: String): Long? =
        watchProgressStore.observeByMainId(mainId)
            .first()
            .asSequence()
            .mapNotNull { it.firstWatchedAt }
            .filter { it > 0 }
            .minOrNull()

    /**
     * ROUND 102 (WS-E): REMOVE FROM TRACKING — the LEFT button.
     *
     * The user's semantics: "it should unlink the tracking between the
     * AniList and the app itself… it should not remove the tracking
     * completely from AniList itself." So this ONLY flips the opt-in OFF —
     * the app stops syncing this content. KEPT: the AniList LINK (that is a
     * data-source decision), the remote AniList entry (untouched), the local
     * watch progress, the rating, and the cached entry.
     *
     * (The old removeTrackEntry — remote delete + local watch-progress and
     * rating clearing — is REPLACED by this + [deleteFromAniList]; the
     * destructive remote delete moved to the trash can, and the local-data
     * clearing was simply wrong under the new contract.)
     */
fun DetailsViewModel.removeTracking() {
        val mid = currentMainId ?: return

        viewModelScope.launch {
            runCatching {
                trackingStateRepository?.setTracked(mid, false)
                Logger.i(TAG) { "removeTracking — tracking unlinked for mainId=$mid (AniList entry + local data kept)" }
                _showTrackSheet.value = false
                // ROUND 104 (WS-B): the completion toast — "it should properly
                // give me the details, like no longer tracking".
                _trackingNotice.value = TrackingNotice(
                    kind = TrackingNoticeKind.STOPPED,
                    id = System.nanoTime(),
                )
            }.onFailure { e ->
                Logger.e(TAG, e) { "removeTracking failed: ${e.message}" }
                _trackSheetError.value = "Couldn't stop the tracking: ${e.message}"
            }
        }
    }

    /**
     * ROUND 102 (WS-E): DELETE FROM ANILIST — the TRASH CAN (the sheet's
     * top-right icon, behind a "Delete from AniList?"
     * confirmation). This is the REAL remote deletion: AniList's
     * DeleteMediaListEntry + the local cache row + the opt-in OFF. Local
     * watch progress and the rating are KEPT (they are the app's own data —
     * the deletion is the tracker's business, not the library's).
     */
fun DetailsViewModel.deleteFromAniList() {
        val mid = currentMainId ?: return
        val tracker = aniListTracker ?: return
        val repo = trackEntryRepository ?: return
        val anime = (state.value as? DetailsState.Success)?.anime ?: return
        val anilistId = anime.anilistId ?: return

        viewModelScope.launch {
            // The opt-in goes first: a deleted entry must never re-sync.
            runCatching { trackingStateRepository?.setTracked(mid, false) }

            if (!tracker.isLoggedIn()) {
                _trackSheetError.value = "Not connected to AniList — connect in Settings → Trackers first."
                return@launch
            }
            val deleted = runCatching { tracker.deleteEntry(anilistId) }
                .onFailure { e ->
                    Logger.w(TAG) { "deleteFromAniList — remote delete failed: ${e.message}" }
                }
                .getOrDefault(false)
            if (deleted) {
                runCatching { repo.delete(mid) }
                _trackEntry.value = null
                _pendingRemoteTrackEntry.value = null
                _showTrackSheet.value = false
                Logger.i(TAG) { "deleteFromAniList — deleted from AniList + cache cleared for mainId=$mid" }
            } else {
                // An honest failure keeps EVERYTHING — the entry stays on
                // AniList, the sheet stays open with the error visible.
                _trackSheetError.value = "Couldn't delete it from AniList — check your connection and try again."
            }
        }
    }

    /**
     * D-242: Shows the "mark all previous episodes as watched" prompt.
     * Called when the user marks episode N as watched but 1..N-1 aren't.
     *
     * The prompt has a 5-second timeout. If the user doesn't respond in 5s,
     * it auto-confirms (per the user's spec: "still it will mark the previous
     * episodes up until episode 5 as watched in any list").
     */
fun DetailsViewModel.showMarkPreviousPrompt(episodeNumber: Int) {
        _showMarkPreviousPrompt.value = episodeNumber
    }

    /** Dismisses the "mark all previous" prompt (user declined). */
fun DetailsViewModel.dismissMarkPreviousPrompt() {
        _showMarkPreviousPrompt.value = null
    }

    /**
     * D-242: Marks all episodes from 1 to [upToEpisode] (inclusive) as watched.
     * Called when the user confirms the "mark all previous" prompt (or when the
     * 5-second timeout auto-confirms).
     *
     * Also relays the updated progress to AniList.
     */
fun DetailsViewModel.markAllPreviousWatched(upToEpisode: Int) {
        val mid = currentMainId ?: return
        _showMarkPreviousPrompt.value = null // dismiss the prompt

        viewModelScope.launch {
            runCatching {
                val episodes = (episodeState.value as? EpisodeState.Loaded)?.episodes ?: return@runCatching
                // Task 57 (P1): the filter runs on the IDENTITY number — the
                // CS sub/dub ordinal (sub-5/dub-5 count ONCE).
                val keysToMark = csEpisodeIdentities(mid, episodes)
                    .filter { it.first <= upToEpisode }
                    .map { it.second }
                    .distinct()
                watchProgressStore.markAllWatched(mid, keysToMark)
                Logger.i(TAG) { "markAllPreviousWatched — marked ${keysToMark.size} episodes (1..$upToEpisode)" }

                // ROUND 102 (WS-E): the relay is the TrackingWatchSyncBridge's
                // job now — this write re-emits its watch_progress signal, and
                // the bridge reconciles TRACKED contents with the same math.
            }.onFailure { e ->
                Logger.e(TAG, e) { "markAllPreviousWatched failed: ${e.message}" }
            }
        }
    }

    /**
     * D-242: Shows the "mark series as watched" prompt. Only called when the
     * series is FINISHED + all episodes have been watched.
     */
fun DetailsViewModel.showMarkSeriesPrompt() {
        _showMarkSeriesPrompt.value = true
    }

    /** Dismisses the "mark series as watched" prompt. */
fun DetailsViewModel.dismissMarkSeriesPrompt() {
        _showMarkSeriesPrompt.value = false
    }

    /**
     * D-242: Marks the entire series as watched (sets status = COMPLETED on AniList).
     * Called when the user confirms the "mark series as watched" prompt.
     */
fun DetailsViewModel.markSeriesAsWatched() {
        val mid = currentMainId ?: return
        _showMarkSeriesPrompt.value = false

        viewModelScope.launch {
            runCatching {
                val episodes = (episodeState.value as? EpisodeState.Loaded)?.episodes ?: return@runCatching
                // Task 57 (P1): ALL identity keys — sub+dub pairs collapse to one.
                val keysToMark = csProgressKeys(mid, episodes)
                watchProgressStore.markAllWatched(mid, keysToMark)
                Logger.i(TAG) { "markSeriesAsWatched — marked all ${keysToMark.size} episodes" }

                // Set status = COMPLETED on AniList.
                // ROUND 121 (D2-a): relayWatchEvent takes an Int now — the
                // episode count rides directly (no Double round-trip).
                trackSyncManager?.relayWatchEvent(
                    contentKey = mid,
                    episodeNumber = episodes.size,
                    status = com.confused.anikuta.core.trackerapi.TrackStatus.COMPLETED,
                )
            }.onFailure { e ->
                Logger.e(TAG, e) { "markSeriesAsWatched failed: ${e.message}" }
            }
        }
    }
