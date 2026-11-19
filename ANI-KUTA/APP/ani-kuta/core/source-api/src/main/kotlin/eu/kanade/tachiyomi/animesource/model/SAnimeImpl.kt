@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.animesource.model

class SAnimeImpl : SAnime {

    override lateinit var url: String

    override lateinit var title: String

    override var artist: String? = null

    override var author: String? = null

    override var description: String? = null

    override var genre: String? = null

    override var status: Int = 0

    override var thumbnail_url: String? = null

    override var background_url: String? = null

    override var initialized: Boolean = false

    override var update_strategy: AnimeUpdateStrategy = AnimeUpdateStrategy.ALWAYS_UPDATE

    override var fetch_type: FetchType = FetchType.Episodes

    override var season_number: Double = -1.0

    // Task 46: optional enrichment channel — null unless a source provides it
    // (see the SAnime interface docs).
    override var year: Int? = null

    override var score: Double? = null

    // ROUND 157 (WS-B2): the external-ID + content-kind channel — the
    // interface's no-op defaults are for EXTERNAL implementers; the real impl
    // (the bridge + every local flow) MUST store, or the whole channel is
    // dead (the P0 the round-157 critic caught).
    override var external_ids: Map<String, String>? = null

    override var content_kind: String? = null
}
