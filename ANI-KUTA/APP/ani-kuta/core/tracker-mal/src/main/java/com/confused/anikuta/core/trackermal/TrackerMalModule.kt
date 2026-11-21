package com.confused.anikuta.core.trackermal

import okhttp3.OkHttpClient
import org.koin.dsl.module

/**
 * ROUND 168 (the tracker redo, Phase C): the MAL tracker's Koin wiring (the
 * Simkl module's pattern). The OkHttpClient is the app-shared one. The
 * tracker JOINS the TrackSyncManager's named("trackers") list via the
 * composition root's combined declaration (AnikutaApp) — keeping this module
 * free of the other tracker modules' dependencies.
 */
val trackerMalModule = module {
    single { MalPreferences(get()) }
    single { MalApi(get<OkHttpClient>()) }
    single { MalTracker(get(), get(), get<OkHttpClient>()) }
}
