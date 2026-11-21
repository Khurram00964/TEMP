package com.confused.anikuta.feature.animesearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.content.ContentDetails
import com.confused.anikuta.core.content.ContentRecord
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.trackerapi.TrackEntry
import com.confused.anikuta.core.trackerapi.TrackStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ROUND 169 (the v1.1.104 device order): the METADATA DETAILS VM.
 *
 * The DB LINKING (the user's "it needs to be properly managed in our
 * database too, like this is linked to this data source"): on the first
 * open the content lands in main_entry (main_id = "{kind}:{id}",
 * display_source = "metadata") + content_details' DATA axis
 * (data_source_type / data_source_ref_id / the meta fields) — the metadata
 * content becomes a first-class row the app's own systems can reference.
 *
 * The TRACK actions dispatch by the source: MAL → the MAL tracker (the
 * remoteId IS the MAL id); TMDB/TVmaze/Simkl → Simkl (the tmdb id bridge
 * resolves; the Simkl id resolves directly). Fire-and-forget — the tracking
 * NEVER blocks the page (the 05-T-5 rule).
 */
class MetadataDetailsViewModel(
    private val contentRepository: ContentRepository,
    private val malTracker: com.confused.anikuta.core.trackermal.MalTracker?,
    private val simklTracker: com.confused.anikuta.core.trackersimkl.SimklTracker?,
) : ViewModel() {

    /** The loaded key (the screen seeds it via [load] — the CsCategoryViewModel pattern). */
    private var key: MetadataDetailsKey? = null

    companion object {
        private const val TAG = "Anikuta:Feature:MetadataDetails"
    }

    private val _trackState = MutableStateFlow<TrackState>(TrackState.Idle)
    val trackState: StateFlow<TrackState> = _trackState.asStateFlow()

    private val _trackTrackerName = MutableStateFlow("Simkl")
    val trackTrackerName: StateFlow<String> = _trackTrackerName.asStateFlow()

    /**
     * The screen seeds the key; the FIRST load runs the DB linking (the
     * idempotent main_entry + content_details upserts — the user's
     * "properly managed in our database" order).
     */
    fun load(key: MetadataDetailsKey) {
        if (this.key == key) return
        this.key = key
        _trackState.value = TrackState.Idle
        _trackTrackerName.value = when (key.source) {
            "mal" -> "MyAnimeList"
            else -> "Simkl"
        }
        linkInDb(key)
    }

    private fun linkInDb(key: MetadataDetailsKey) {

        viewModelScope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                contentRepository.insertMainEntryIfMissing(
                    ContentRecord(
                        mainId = key.mainId,
                        contentId = key.mainId,
                        title = key.title,
                        contentType = "anime",
                        contentFormat = "video",
                        displaySource = "metadata",
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                contentRepository.upsertContentDetails(
                    ContentDetails(
                        mainId = key.mainId,
                        dataSourceType = key.source,
                        dataSourceRefId = key.remoteId.toString(),
                        dataScore = key.rating?.let { (it * 10).toInt().toLong() },
                        dataEpisodes = null,
                        dataSeasonYear = key.year?.toLong(),
                        dataSynopsis = key.overview,
                        dataCoverUrl = key.posterUrl,
                        dataUpdatedAt = now,
                    ),
                )
                Logger.i(TAG) { "metadata content linked: ${key.mainId} (${key.source})" }
            }.onFailure { e ->
                Logger.w(TAG) { "metadata DB linking failed (non-fatal): ${e.message}" }
            }
        }
    }

    /** The track action — the plan-to-watch seed on the serving tracker. */
    fun trackOnServingTracker() {
        val key = key ?: return
        viewModelScope.launch {
            _trackState.value = TrackState.Saving
            val ok = runCatching {
                when (key.source) {
                    "mal" -> {
                        val tracker = malTracker ?: return@runCatching false
                        tracker.syncEntry(
                            TrackEntry(
                                contentKey = key.mainId,
                                trackerId = key.remoteId,
                                status = TrackStatus.PLAN_TO_WATCH,
                                progress = 0,
                                updatedAt = System.currentTimeMillis(),
                            ),
                        )
                    }
                    else -> {
                        val tracker = simklTracker ?: return@runCatching false
                        tracker.syncEntry(
                            TrackEntry(
                                contentKey = key.mainId,
                                trackerId = key.remoteId,
                                status = TrackStatus.PLAN_TO_WATCH,
                                progress = 0,
                                updatedAt = System.currentTimeMillis(),
                            ),
                        )
                    }
                }
            }.getOrDefault(false)
            _trackState.value = if (ok) TrackState.Tracked else TrackState.Failed
            Logger.i(TAG) { "trackOnServingTracker — ok=$ok (${key.source}:${key.remoteId})" }
        }
    }

    sealed interface TrackState {
        data object Idle : TrackState
        data object Saving : TrackState
        data object Tracked : TrackState
        data object Failed : TrackState
    }
}
