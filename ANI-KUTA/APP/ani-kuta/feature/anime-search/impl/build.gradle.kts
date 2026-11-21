plugins {
    id("anikuta.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.confused.anikuta.feature.animesearch"
}

dependencies {
    implementation(project(":feature:anime-search:api"))
    implementation(project(":feature:anime-details:api"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:anilist"))
    implementation(project(":core:tmdb")) // ROUND 163 (STEP-3): the movies/series data source
    implementation(project(":core:database")) // ROUND 165: the extension-priority tracker
    implementation(project(":core:tvmaze")) // ROUND 163 (STEP-3): the keyless TV data source
    implementation(project(":core:tracker-api"))  // ROUND 168: the Tracker supertypes (BaseTracker) must be on the classpath for the tracker-backed sources
    implementation(project(":core:content"))  // ROUND 169: the metadata details DB linking (ContentRepository/Record/Details)
    implementation(project(":core:tracker-mal"))  // ROUND 168 (Phase E): the MAL search source
    implementation(project(":core:tracker-simkl"))  // ROUND 168 (Phase E): the Simkl search source
    implementation(project(":core:common"))
    implementation(project(":core:data-cache"))  // D-278: offline trending fallback (browse_cache)
    implementation(project(":core:navigation-api"))
    implementation(project(":core:preferences"))
    implementation(project(":core:source-api"))
    implementation(project(":core:activity-tracker"))  // D-192: activity tracking
    implementation(project(":data:extension"))
    // Session 3 (CloudStream execution phase 1): provider browse/search through
    // CloudstreamContentRepository + the persisted NSFW gate on AppPreferences.
    implementation(project(":data:cloudstream"))

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.material.icons.extended)  // D-322: explicit pin (icons deprecated after 1.7.8)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.compose.viewmodel)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)

    // D-402 (round 28): the feature module's first test source set — the
    // top-bar reveal policy (searchBarNextCollapsed) is unit-tested.
    testImplementation(libs.junit)
}
