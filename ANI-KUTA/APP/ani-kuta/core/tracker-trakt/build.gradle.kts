plugins {
    id("anikuta.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.confused.anikuta.core.trackertrakt"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:tracker-api"))
    implementation(project(":core:preferences"))
    implementation(project(":core:content"))
    implementation(project(":core:database"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
}
