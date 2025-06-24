import project.convention.logic.config.LibraryModule

import project.convention.logic.kover.excludeFromKoverReport

plugins {
    id("project.android.feature")
}

android {
    namespace = "eu.europa.ec.authenticationfeature"
}

dependencies {
    implementation(project(":authentication-logic"))
    implementation(platform(libs.bom))
    implementation(libs.supabase.postgrest.kt)
    implementation(libs.supabase.auth.kt)
    implementation(libs.supabase.realtime.kt)
}

moduleConfig {
    module = LibraryModule.AuthenticationFeature
}

excludeFromKoverReport(
    excludedClasses = listOf(),
    excludedPackages = listOf(),
) 