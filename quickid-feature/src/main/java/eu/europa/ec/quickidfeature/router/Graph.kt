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

package eu.europa.ec.quickidfeature.router

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import eu.europa.ec.quickidfeature.BuildConfig
import eu.europa.ec.quickidfeature.ui.intro.QuickIdIntroScreen
import eu.europa.ec.quickidfeature.ui.liveness.LivenessScreen
import eu.europa.ec.quickidfeature.ui.mrzscan.MrzScanScreen
import eu.europa.ec.quickidfeature.ui.nfc.PassportNfcScreen
import eu.europa.ec.quickidfeature.ui.processing.ProcessingScreen
import eu.europa.ec.quickidfeature.ui.result.VerificationResultScreen
import eu.europa.ec.uilogic.navigation.ModuleRoute
import eu.europa.ec.uilogic.navigation.QuickIdScreens
import org.koin.androidx.compose.koinViewModel

fun NavGraphBuilder.featureQuickIdGraph(navController: NavController) {
    navigation(
        startDestination = QuickIdScreens.Intro.screenRoute,
        route = ModuleRoute.QuickIdModule.route
    ) {
        // Intro Screen
        composable(
            route = QuickIdScreens.Intro.screenRoute,
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = BuildConfig.DEEPLINK + QuickIdScreens.Intro.screenRoute
                }
            )
        ) {
            QuickIdIntroScreen(
                navController = navController,
                viewModel = koinViewModel()
            )
        }

        // MRZ Scan Screen (Camera OCR)
        composable(
            route = QuickIdScreens.MrzScan.screenRoute,
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = BuildConfig.DEEPLINK + QuickIdScreens.MrzScan.screenRoute
                }
            )
        ) {
            MrzScanScreen(
                navController = navController,
                viewModel = koinViewModel()
            )
        }

        // NFC Reading Screen
        composable(
            route = QuickIdScreens.NfcReading.screenRoute,
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = BuildConfig.DEEPLINK + QuickIdScreens.NfcReading.screenRoute
                }
            )
        ) {
            PassportNfcScreen(
                navController = navController,
                viewModel = koinViewModel()
            )
        }

        // Liveness Screen
        composable(
            route = QuickIdScreens.Liveness.screenRoute,
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = BuildConfig.DEEPLINK + QuickIdScreens.Liveness.screenRoute
                }
            )
        ) {
            LivenessScreen(
                navController = navController,
                viewModel = koinViewModel()
            )
        }

        // Processing Screen
        composable(
            route = QuickIdScreens.Processing.screenRoute,
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = BuildConfig.DEEPLINK + QuickIdScreens.Processing.screenRoute
                }
            )
        ) {
            ProcessingScreen(
                navController = navController,
                viewModel = koinViewModel()
            )
        }

        // Result Screen
        composable(
            route = QuickIdScreens.Result.screenRoute,
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = BuildConfig.DEEPLINK + QuickIdScreens.Result.screenRoute
                }
            ),
            arguments = listOf(
                navArgument("success") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) {
            VerificationResultScreen(
                navController = navController,
                viewModel = koinViewModel()
            )
        }
    }
}
