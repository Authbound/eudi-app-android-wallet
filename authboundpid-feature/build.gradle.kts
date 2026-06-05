import project.convention.logic.config.LibraryModule

plugins {
    id("project.android.feature")
}

val candourJitpackAuthToken = providers.gradleProperty("JITPACK_AUTH_TOKEN")
    .orElse(providers.gradleProperty("jitpackAuthToken"))
    .orElse(providers.environmentVariable("JITPACK_AUTH_TOKEN"))
    .orElse(providers.environmentVariable("jitpackAuthToken"))
val hasCandourJitpackAuthToken: Boolean = !candourJitpackAuthToken.orNull.isNullOrBlank()
val isE2eMode: Boolean = providers.gradleProperty("E2E_MODE")
    .orElse(providers.environmentVariable("E2E_MODE"))
    .map(String::toBoolean)
    .getOrElse(false)
val requestedTaskNames: List<String> = gradle.startParameter.taskNames
val ideSyncFlags: List<String> = listOf(
    "android.injected.invoked.from.ide",
    "android.injected.build.model.only",
    "android.injected.build.model.only.advanced",
    "idea.sync.active"
)
val isIdeSyncOrModelFetch: Boolean = ideSyncFlags.any { flagName ->
    providers.gradleProperty(flagName).orNull?.toBoolean() == true
        || providers.systemProperty(flagName).orNull?.toBoolean() == true
}
val requiresCandourSdkAccess: Boolean = requestedTaskNames.any { taskName ->
    val normalizedTaskName = taskName.lowercase()
    normalizedTaskName.contains(":authboundpid-feature:")
        || normalizedTaskName.contains(":app:")
        || normalizedTaskName.contains(":assembly-logic:")
        || normalizedTaskName.startsWith("assemble")
        || normalizedTaskName.startsWith("build")
        || normalizedTaskName.startsWith("bundle")
        || normalizedTaskName.startsWith("install")
        || normalizedTaskName.startsWith("connected")
        || normalizedTaskName == "test"
        || normalizedTaskName == "lint"
} || requestedTaskNames.isEmpty() || isIdeSyncOrModelFetch

if (requiresCandourSdkAccess && !isE2eMode) {
    check(hasCandourJitpackAuthToken) {
        "Candour SDK requires private JitPack credentials. Configure JITPACK_AUTH_TOKEN " +
            "or set jitpackAuthToken in ~/.gradle/gradle.properties."
    }
}

android {
    namespace = "eu.europa.ec.authboundpidfeature"

    if (isE2eMode) {
        sourceSets.getByName("main") {
            java.srcDir("src/e2eCandourStub/java")
            kotlin.directories.add("src/e2eCandourStub/java")
            res.srcDir("src/e2eCandourStub/res")
            manifest.srcFile("src/e2eCandourStub/AndroidManifest.xml")
        }
    }

    buildFeatures {
        dataBinding = true
    }
}

moduleConfig {
    module = LibraryModule.AuthboundPidFeature
}

dependencies {
    implementation(project(LibraryModule.AuthboundPidLogic.path))
    implementation(libs.timber)

    if (!isE2eMode) {
        implementation(libs.candour.sdk) { isTransitive = true }
    }
}
