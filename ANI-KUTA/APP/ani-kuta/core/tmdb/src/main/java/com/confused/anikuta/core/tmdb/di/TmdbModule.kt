package com.confused.anikuta.core.tmdb.di

import com.confused.anikuta.core.tmdb.JikanApi
import com.confused.anikuta.core.tmdb.TmdbApi
import org.koin.dsl.module

/** ROUND 160 (WS-TMDB): the TMDB + Jikan data-source module. */
val tmdbModule = module {
    single { TmdbApi() }
    single { JikanApi() }
}
