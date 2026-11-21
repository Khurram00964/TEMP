package com.confused.anikuta.feature.animesearch.di

import com.confused.anikuta.feature.animesearch.CsCategoryViewModel
import com.confused.anikuta.feature.animesearch.SearchViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val searchModule = module {
    viewModelOf(::SearchViewModel)
    // ROUND 169: the metadata details VM (the load()-seeded key).
    viewModelOf(::MetadataDetailsViewModel)
    // Task 61 (round 21 — the category subpages): one VM per CsCategoryScreen
    // instance (the provider + shelf identity seed it via load()).
    viewModelOf(::CsCategoryViewModel)
}
