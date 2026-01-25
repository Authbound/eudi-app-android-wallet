/*
 * Copyright (c) 2025 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

import project.convention.logic.config.LibraryModule
import project.convention.logic.kover.KoverExclusionRules
import project.convention.logic.kover.excludeFromKoverReport

plugins {
    id("project.android.feature")
}

android {
    namespace = "eu.europa.ec.quickidfeature"
}

moduleConfig {
    module = LibraryModule.QuickIdFeature
}

dependencies {
    implementation(project(LibraryModule.QuickIdLogic.path))

    // AWS Amplify Face Liveness
    implementation("com.amplifyframework:core:2.33.0")
    implementation("com.amplifyframework.ui:liveness:1.9.0")
    implementation("com.amplifyframework:aws-auth-cognito:2.33.0")

    // CameraX for MRZ scanning
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    // Accompanist permissions
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")
}

excludeFromKoverReport(
    excludedClasses = KoverExclusionRules.QuickIdFeature.classes,
    excludedPackages = KoverExclusionRules.QuickIdFeature.packages,
)
