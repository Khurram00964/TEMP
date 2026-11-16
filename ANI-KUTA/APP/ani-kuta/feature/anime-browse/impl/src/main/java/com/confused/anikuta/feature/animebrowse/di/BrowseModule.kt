package com.confused.anikuta.feature.animebrowse.di

import com.confused.anikuta.feature.animebrowse.BrowseViewModel
import com.confused.anikuta.feature.animebrowse.HomeCategoryViewModel
import com.confused.anikuta.feature.animebrowse.HomeGenresViewModel
import com.confused.anikuta.feature.animebrowse.HomeViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Round 148: [HomeViewModel] joins [BrowseViewModel] here — the home is the
 * Browse tab's sibling surface (same module, same Koin init via
 * AnikutaApp.browseModule), so its DI lives next to Browse's. ROUND 151
 * swapped the constructor (UpdateStore dropped with the round-148 sections;
 * HomePreferences in for the "Airs today" source toggle) — every dep is
 * still an existing singleton on the app graph, no new bindings needed.
 * ROUND 152: the browse-destination VMs join them (the "All genres" index +
 * the shared paged genre/tag/catalog grid — one instance re-seeded per
 * screen entry, the CsCategory idiom).
 */
val browseModule = module {
    viewModelOf(::BrowseViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::HomeGenresViewModel)
    viewModelOf(::HomeCategoryViewModel)
}
