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

package eu.europa.ec.authboundpidfeature.router

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.navigation.navigation
import eu.europa.ec.authboundpidfeature.ui.intro.AuthboundPidIntroScreen
import eu.europa.ec.authboundpidfeature.ui.result.AuthboundPidResultScreen
import eu.europa.ec.uilogic.BuildConfig
import eu.europa.ec.uilogic.navigation.AuthboundPidScreens
import eu.europa.ec.uilogic.navigation.ModuleRoute
import org.koin.androidx.compose.koinViewModel

fun NavGraphBuilder.featureAuthboundPidGraph(navController: NavController) {
    navigation(
        startDestination = AuthboundPidScreens.Intro.screenRoute,
        route = ModuleRoute.AuthboundPidModule.route
    ) {
        composable(
            route = AuthboundPidScreens.Intro.screenRoute,
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = BuildConfig.DEEPLINK + AuthboundPidScreens.Intro.screenRoute
                }
            )
        ) {
            AuthboundPidIntroScreen(
                navController = navController,
                viewModel = koinViewModel()
            )
        }
        composable(
            route = AuthboundPidScreens.Result.screenRoute,
            arguments = listOf(
                navArgument("resultType") {
                    type = NavType.StringType
                    defaultValue = "FAILED"
                }
            )
        ) {
            AuthboundPidResultScreen(
                navController = navController,
                viewModel = koinViewModel()
            )
        }
    }
}
