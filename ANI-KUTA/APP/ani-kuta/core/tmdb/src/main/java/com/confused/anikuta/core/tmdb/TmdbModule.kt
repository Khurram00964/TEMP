package com.confused.anikuta.core.tmdb

import org.koin.dsl.module

/** ROUND 163 (STEP-3): the TMDB source's Koin wiring. */
val tmdbModule = module {
    single { TmdbPreferences(get()) }
    single { TmdbApi() }
}
