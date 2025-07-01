/*
 * Copyright (c) 2024 European Commission
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
package eu.europa.ec.authenticationfeature.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.navigation.AuthenticationScreens
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.WrapButton
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun WalletSetupScreen(
    viewModel: AuthenticationViewModel,
    navController: NavController,
    logController: LogController,
    onNavigateBackToLogin: () -> Unit
) {
    val state by viewModel.viewState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            logController.d { "WalletSetupScreen: Effect: $effect" }
            when (effect) {
                is Effect.Navigation.NavigateBackToLogin -> {
                    // Debug the navigation back stack
                    val currentEntry = navController.currentBackStackEntry
                    val previousEntry = navController.previousBackStackEntry
                    val currentDestination = navController.currentDestination
                    
                    logController.d { 
                        """
                        WalletSetupScreen Navigation Debug:
                        - Current Entry: ${currentEntry?.destination?.route}
                        - Previous Entry: ${previousEntry?.destination?.route}
                        - Current Destination: ${currentDestination?.route}

                        - Can Pop Back: ${navController.previousBackStackEntry != null}
                        """.trimIndent()
                    }
                    
                    // Check if we can actually navigate back
                    if (navController.previousBackStackEntry != null) {
                        logController.d { "Navigating back using popBackStack()" }
                        onNavigateBackToLogin()
                    } else {
                        logController.w { "No previous back stack entry - navigating to Login directly" }
                        // If no previous entry, navigate directly to Login screen
                        navController.navigate(AuthenticationScreens.Login.screenRoute) {
                            popUpTo(AuthenticationScreens.WalletSetup.screenRoute) {
                                inclusive = true
                            }
                        }
                    }
                }
                else -> {
                    // Handle other effects if needed in the future
                }
            }
        }
    }

    // Add initial back stack debugging
    LaunchedEffect(Unit) {
        logController.d { 
            """
            WalletSetupScreen Initial State:
            - Current Route: ${navController.currentDestination?.route}
            - Previous Entry: ${navController.previousBackStackEntry?.destination?.route}

            """.trimIndent()
        }
    }

    ContentScreen(
        isLoading = state.isActivating,
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack = {
            logController.d { "WalletSetupScreen: Back button pressed" }
            viewModel.setEvent(Event.NavigateBack)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Secure Your Wallet",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "To keep your information safe, we need to create a secure key on this device. This is a one-time setup.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            WrapButton(
                buttonConfig = ButtonConfig(
                    type = ButtonType.PRIMARY,
                    onClick = { viewModel.setEvent(Event.ActivateWallet) },
                )
            ) {
                Text(text = "Create Secure Wallet")
            }
        }
    }
} 