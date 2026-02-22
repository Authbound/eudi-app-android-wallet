import project.convention.logic.config.LibraryModule

plugins {
    id("project.android.library")
    alias(libs.plugins.ksp)
}

android {
    namespace = "eu.europa.ec.authboundpidlogic"
}

moduleConfig {
    module = LibraryModule.AuthboundPidLogic
}

dependencies {
    implementation(project(LibraryModule.BusinessLogic.path))
    implementation(project(LibraryModule.NetworkLogic.path))
    implementation(project(LibraryModule.CoreLogic.path))
    implementation(project(LibraryModule.ResourcesLogic.path))

    // Supabase for auth token retrieval
    implementation(platform(libs.bom))
    implementation(libs.supabase.auth.kt)

    // Koin annotations support
    implementation(libs.koin.annotations)
    ksp(libs.koin.ksp)

    testImplementation(project(LibraryModule.TestLogic.path))
    androidTestImplementation(project(LibraryModule.TestLogic.path))
}
