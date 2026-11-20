package com.confused.anikuta.core.tvmaze

import org.koin.dsl.module

/** ROUND 163 (STEP-3): the TVmaze source's Koin wiring. */
val tvmazeModule = module {
    single { TvmazePreferences(get()) }
    single { TvmazeApi() }
}
