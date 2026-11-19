package com.confused.anikuta.core.trackertrakt

import com.confused.anikuta.core.trackerapi.Tracker
import org.koin.dsl.module

/**
 * ROUND 157 (WS-C): the Trakt module. The tracker joins the named("trackers")
 * list — TrackSyncManager + the sync bridge pick it up automatically; it stays
 * dormant (honest "not configured") until the app registration's client_id
 * lands in TraktAuth.
 */
val traktModule = module {
    single { TraktTracker(get()) }
    single<Tracker> { get<TraktTracker>() }
}
