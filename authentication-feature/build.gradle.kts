import project.convention.logic.config.LibraryModule

import project.convention.logic.kover.excludeFromKoverReport

plugins {
    id("project.android.feature")
}

android {
    namespace = "eu.europa.ec.authenticationfeature"
}

dependencies {
    implementation(platform(libs.bom))
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(project(":authentication-logic"))
    implementation(project(":wallet-activation-logic"))
    implementation(project(":notification-logic"))
    implementation(project(":business-logic"))
    implementation(project(":ui-logic"))
    implementation(project(":resources-logic"))
    implementation(libs.supabase.postgrest.kt)
    implementation(libs.supabase.auth.kt)
    implementation(libs.supabase.realtime.kt)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")

    // Animation dependencies
    implementation("androidx.compose.animation:animation-core")
    implementation("androidx.compose.animation:animation-graphics")

    // Foundation for drawing effects
    implementation("androidx.compose.foundation:foundation")
}

moduleConfig {
    module = LibraryModule.AuthenticationFeature
}

excludeFromKoverReport(
    excludedClasses = listOf(),
    excludedPackages = listOf(),
) 