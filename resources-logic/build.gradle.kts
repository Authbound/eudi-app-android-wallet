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

import com.android.build.api.dsl.LibraryExtension
import project.convention.logic.config.LibraryModule
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

plugins {
    id("project.android.library")
    id("project.android.library.compose")
}

extensions.configure<LibraryExtension>("android") {
    namespace = "eu.europa.ec.resourceslogic"
}

moduleConfig {
    module = LibraryModule.ResourcesLogic
}

providers.gradleProperty("authboundM31ProofResourcesDir").orNull?.let { rawPath ->
    val proofResourcesPath = Path.of(rawPath)
    require(proofResourcesPath.isAbsolute)
    val proofResources = proofResourcesPath.toFile()
    val proofRaw = proofResourcesPath.resolve("raw")
    val proofXml = proofResourcesPath.resolve("xml")
    val proofCa = proofRaw.resolve("authbound_verifier_root_ca.pem")
    val proofNetworkCa = proofRaw.resolve("authbound_m31_network_ca.pem")
    val proofNetworkConfig = proofXml.resolve("authbound_m31_network_security_config.xml")

    require(Files.isDirectory(proofResourcesPath, NOFOLLOW_LINKS))
    require(Files.isDirectory(proofRaw, NOFOLLOW_LINKS))
    require(Files.isDirectory(proofXml, NOFOLLOW_LINKS))
    require(Files.isRegularFile(proofCa, NOFOLLOW_LINKS))
    require(Files.isRegularFile(proofNetworkCa, NOFOLLOW_LINKS))
    require(Files.isRegularFile(proofNetworkConfig, NOFOLLOW_LINKS))

    extensions.configure<LibraryExtension>("android") {
        sourceSets.named("demo") {
            res.srcDir(proofResources)
        }
    }
}

dependencies {
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.compose.material3.windowSizeClass)
    api(libs.material)
}
