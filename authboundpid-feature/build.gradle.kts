import project.convention.logic.config.LibraryModule

plugins {
    id("project.android.feature")
}

android {
    namespace = "eu.europa.ec.authboundpidfeature"
}

moduleConfig {
    module = LibraryModule.AuthboundPidFeature
}

dependencies {
    implementation(project(LibraryModule.AuthboundPidLogic.path))

    // Chrome Custom Tabs for opening AuthboundPID's verification URL
    implementation("androidx.browser:browser:1.8.0")
}
