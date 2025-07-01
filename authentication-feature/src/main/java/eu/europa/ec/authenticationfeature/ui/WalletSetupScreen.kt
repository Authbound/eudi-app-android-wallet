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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.WrapButton
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun WalletSetupScreen(
    viewModel: AuthenticationViewModel,
    logController: LogController,
    onPopBackStack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onSignOutAndNavigateToLogin: () -> Unit
) {
    val state by viewModel.viewState.collectAsState()

    // Initialize the ViewModel to track navigation source
    LaunchedEffect(Unit) {
        logController.d("WalletSetupScreen", "Screen composition - Initializing ViewModel")
        viewModel.setEvent(Event.Initialize)
        // Auto-start wallet activation if not already activating and no error
        if (!state.isActivating && state.walletActivationError == null) {
            logController.d("WalletSetupScreen", "Auto-starting wallet activation")
            viewModel.setEvent(Event.ActivateWallet)
        }
    }

    // Handle navigation effects from ViewModel
    LaunchedEffect(Unit) {
        logController.d("WalletSetupScreen", "Starting effect collection" )
        var effectCount = 0
        var lastEffectTime = 0L
        
        viewModel.effect.collectLatest { effect ->
            effectCount++
            val currentTime = System.currentTimeMillis()
            val timeSinceLastEffect = currentTime - lastEffectTime
            
            logController.d("WalletSetupScreen", "Effect #$effectCount received: $effect (Time since last: ${timeSinceLastEffect}ms)" )
            
            // Emergency fix: Ignore rapid duplicate navigation effects
            val isNavigationEffect = effect is Effect.Navigation.PopBackStack || 
                                   effect is Effect.Navigation.NavigateToLoginAndClearStack ||
                                   effect is Effect.Navigation.SignOutAndNavigateToLogin
            val isTooRapid = timeSinceLastEffect < 500 && effectCount > 1
            
            if (isNavigationEffect && isTooRapid) {
                logController.w("WalletSetupScreen") { "Ignoring rapid duplicate navigation effect #$effectCount (${timeSinceLastEffect}ms)" }
                return@collectLatest
            }
            
            lastEffectTime = currentTime
            
            when (effect) {
                is Effect.Navigation.PopBackStack -> {
                    logController.d("WalletSetupScreen", "Processing PopBackStack effect #$effectCount")
                    onPopBackStack()
                    logController.d("WalletSetupScreen", "PopBackStack effect #$effectCount completed" )
                }
                is Effect.Navigation.NavigateToLoginAndClearStack -> {
                    logController.d("WalletSetupScreen", "Processing NavigateToLoginAndClearStack effect #$effectCount")
                    onNavigateToLogin()
                    logController.d("WalletSetupScreen", "NavigateToLoginAndClearStack effect #$effectCount completed")
                }
                is Effect.Navigation.SignOutAndNavigateToLogin -> {
                    logController.d("WalletSetupScreen", "Processing SignOutAndNavigateToLogin effect #$effectCount - User will be signed out")
                    onSignOutAndNavigateToLogin()
                    logController.d("WalletSetupScreen", "SignOutAndNavigateToLogin effect #$effectCount completed")
                }
                is Effect.ShowError -> {
                    logController.d("WalletSetupScreen", "Ignoring ShowError effect in WalletSetupScreen")
                }
                is Effect.ShowInfo -> {
                    logController.d("WalletSetupScreen", "Ignoring ShowInfo effect in WalletSetupScreen")
                }
                is Effect.NavigateToHome -> {
                    logController.d("WalletSetupScreen","Ignoring NavigateToHome effect in WalletSetupScreen")
                }
                is Effect.Navigation.NavigateToWalletSetup -> {
                    logController.d("WalletSetupScreen", "Ignoring NavigateToWalletSetup effect in WalletSetupScreen")
                }
                else -> {
                    logController.w("WalletSetupScreen") { "Unhandled effect #$effectCount: $effect" }
                }
            }
        }
    }

    ContentScreen(
        isLoading = state.isActivating,
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack = {
            logController.d("WalletSetupScreen", "Back button pressed - will sign out user")
            viewModel.setEvent(Event.NavigateBack)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state.walletActivationError != null) {
                // Error state
                val errorMessage = state.walletActivationError
                Icon(
                    painter = painterResource(id = R.drawable.ic_error),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Wallet Setup Failed",
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessage.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(32.dp))
                WrapButton(
                    modifier = Modifier.fillMaxWidth(),
                    buttonConfig = ButtonConfig(
                        type = ButtonType.PRIMARY,
                        onClick = { 
                            logController.d("WalletSetupScreen", "Retry button pressed")
                            viewModel.setEvent(Event.RetryWalletActivation) 
                        },
                    )
                ) {
                    Text(text = "Try Again")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "If the problem persists, try signing out and signing back in.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Loading state - wallet activation in progress
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 6.dp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Setting up your secure wallet...",
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "This may take a few moments. Please don't close the app.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
} 