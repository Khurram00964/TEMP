plugins {
    id("anikuta.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.confused.anikuta.core.tmdb"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:tracker-api")) // TrackerKeys — the TMDB registrations
    implementation(project(":core:preferences"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
}
