package com.confused.anikuta.core.trackersimkl

import okhttp3.OkHttpClient
import org.koin.dsl.module

/**
 * ROUND 163 (STEP-2): the Simkl tracker's Koin wiring. The OkHttpClient is
 * the app-shared one (the network module's single — the AniList module's
 * precedent). The tracker JOINS the TrackSyncManager's named("trackers") list
 * via the composition root's combined declaration (AnikutaApp) — keeping this
 * module free of a tracker-anilist dependency.
 */
val trackerSimklModule = module {
    single { SimklPreferences(get()) }
    single { SimklApi(get<OkHttpClient>()) }
    single { SimklTracker(get(), get(), get<OkHttpClient>()) }
}
