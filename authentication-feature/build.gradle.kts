import project.convention.logic.config.LibraryModule

import project.convention.logic.kover.excludeFromKoverReport

plugins {
    id("project.android.feature")
}

android {
    namespace = "eu.europa.ec.authenticationfeature"
}

dependencies {
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(project(":authentication-logic"))
    implementation(project(":ui-logic"))
    implementation(project(":resources-logic"))
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