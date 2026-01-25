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
    id("project.android.library")
    alias(libs.plugins.ksp)
}

android {
    namespace = "eu.europa.ec.quickidlogic"
}

moduleConfig {
    module = LibraryModule.QuickIdLogic
}

dependencies {
    implementation(project(LibraryModule.BusinessLogic.path))
    implementation(project(LibraryModule.NetworkLogic.path))
    implementation(project(LibraryModule.CoreLogic.path))
    implementation(project(LibraryModule.ResourcesLogic.path))
    implementation(project(LibraryModule.AuthenticationLogic.path))
    implementation(project(LibraryModule.UiLogic.path))

    // JMRTD for passport NFC reading
    implementation("org.jmrtd:jmrtd:0.7.40")
    implementation("net.sf.scuba:scuba-sc-android:0.0.23")
    implementation("org.bouncycastle:bcprov-jdk18on:1.77")

    // Supabase for authentication
    implementation(platform(libs.bom))
    implementation(libs.supabase.auth.kt)

    // Gson for serialization
    implementation(libs.gson)

    // ML Kit Text Recognition for on-device MRZ OCR
    implementation(libs.mlkit.text.recognition)

    // Koin annotations support
    implementation(libs.koin.annotations)
    ksp(libs.koin.ksp)

    implementation(libs.kotlinx.datetime)

    testImplementation(project(LibraryModule.TestLogic.path))
    androidTestImplementation(project(LibraryModule.TestLogic.path))
}

excludeFromKoverReport(
    excludedClasses = KoverExclusionRules.QuickIdLogic.classes,
    excludedPackages = KoverExclusionRules.QuickIdLogic.packages,
)
