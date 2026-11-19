plugins {
    id("anikuta.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.confused.anikuta.feature.animebrowse"
}

dependencies {
    implementation(project(":feature:anime-browse:api"))
    implementation(project(":feature:anime-details:api"))
    implementation(project(":core:debug-api"))  // DB-7: LocalDebugContext
    implementation(project(":core:navigation-api"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:preferences"))  // D-320: cover-transition gate
    implementation(project(":core:anilist"))
    implementation(project(":core:common"))
    implementation(project(":core:data-cache"))
    // ROUND 157 (WS-D2): the CS home shelves — the Series & movies feed.
    implementation(project(":data:cloudstream"))
    // Phase 3: continue-watching carousel
    implementation(project(":core:watch-progress"))
    implementation(project(":core:content"))
    // Round 148: the home's Releasing-soon + Recently-released rails read the
    // schedule + update stores directly (no new module).
    implementation(project(":core:schedule"))
    implementation(project(":core:updates"))
    // Round 151 (the design-handoff home rebuild): the home's rails push the
    // History ("See all") + Updates ("Schedule") keys directly.
    implementation(project(":feature:anime-history:api"))
    implementation(project(":feature:anime-search:api"))  // WS-D2: CsCategoryKey
    implementation(project(":feature:updates:api"))

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.foundation)
    // D-322: explicit pin (icons deprecated after 1.7.8) — the home's
    // Pause/Casino glyphs are extended-set icons.
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.compose.viewmodel)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.ui.tooling)
}
