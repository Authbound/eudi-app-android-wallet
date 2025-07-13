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

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.content.ToolbarConfig
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.WrapButton
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun WalletSetupScreen(
    viewModel: WalletSetupViewModel = koinViewModel(),
    logController: LogController,
    onNavigateToHome: () -> Unit,
    onNavigateToLogin: () -> Unit,
) {
    val state by viewModel.viewState.collectAsState()
    val context = LocalContext.current

    // This screen's purpose is to activate the wallet, so trigger it on launch.
    LaunchedEffect(Unit) {
        logController.d("WalletSetupScreen", "Screen launched, triggering wallet activation.")
        viewModel.setEvent(WalletSetupEvent.ActivateWallet)
    }

    // Handle navigation and error effects from the ViewModel
    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is WalletSetupEffect.NavigateToHome -> onNavigateToHome()
                is WalletSetupEffect.NavigateToLogin -> onNavigateToLogin()
                is WalletSetupEffect.ShowError -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Hardware back button handling
    BackHandler(enabled = state.canNavigateBack) {
        logController.d("WalletSetupScreen", "Hardware back button pressed")
        if (state.activationError != null) {
            viewModel.setEvent(WalletSetupEvent.BackToLogin)
        } else {
            viewModel.setEvent(WalletSetupEvent.CancelSetup)
        }
    }

    // Determine screen navigation action and toolbar config
    val (navigatableAction, toolbarConfig, onBack) = when {
        !state.canNavigateBack -> Triple(ScreenNavigateAction.NONE, null, null)
        state.activationError != null -> Triple(
            ScreenNavigateAction.CANCELABLE,
            ToolbarConfig(title = "Wallet Setup Failed"),
            { 
                logController.d("WalletSetupScreen", "Toolbar back button pressed (error state)")
                viewModel.setEvent(WalletSetupEvent.BackToLogin) 
            }
        )
        else -> Triple(
            ScreenNavigateAction.CANCELABLE,
            ToolbarConfig(title = "Setting up Wallet"),
            { 
                logController.d("WalletSetupScreen", "Toolbar back button pressed (loading state)")
                viewModel.setEvent(WalletSetupEvent.CancelSetup) 
            }
        )
    }

    ContentScreen(
        isLoading = state.isActivating,
        navigatableAction = navigatableAction,
        toolBarConfig = toolbarConfig,
        onBack = onBack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state.activationError != null) {
                // Error state
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
                    text = state.activationError.orEmpty(),
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
                            viewModel.setEvent(WalletSetupEvent.Retry) 
                        },
                    )
                ) {
                    Text(text = "Try Again")
                }
                Spacer(modifier = Modifier.height(16.dp))
                WrapButton(
                    modifier = Modifier.fillMaxWidth(),
                    buttonConfig = ButtonConfig(
                        type = ButtonType.SECONDARY,
                        onClick = {
                            logController.d("WalletSetupScreen", "Sign Out button pressed")
                            viewModel.setEvent(WalletSetupEvent.SignOut)
                        },
                    )
                ) {
                    Text(text = "Sign Out")
                }
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

        // Confirmation dialog for canceling setup during activation
        if (state.showConfirmationDialog) {
            AlertDialog(
                onDismissRequest = { 
                    logController.d("WalletSetupScreen", "Confirmation dialog dismissed")
                    viewModel.setEvent(WalletSetupEvent.DismissConfirmationDialog) 
                },
                title = { Text("Cancel Wallet Setup?") },
                text = {
                    Text(
                        "Your wallet setup is in progress. Canceling will sign you out and you'll need to start over.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            logController.d("WalletSetupScreen", "Confirmation dialog - Yes, Cancel pressed")
                            viewModel.setEvent(WalletSetupEvent.ConfirmCancelSetup)
                        }
                    ) {
                        Text("Yes, Cancel", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            logController.d("WalletSetupScreen", "Confirmation dialog - Continue Setup pressed")
                            viewModel.setEvent(WalletSetupEvent.DismissConfirmationDialog)
                        }
                    ) {
                        Text("Continue Setup")
                    }
                }
            )
        }
    }
} 