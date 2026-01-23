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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import eu.europa.ec.businesslogic.model.error.WalletActivationError
import eu.europa.ec.businesslogic.model.error.getErrorTitle
import eu.europa.ec.businesslogic.model.error.getUserFriendlyMessage
import eu.europa.ec.businesslogic.model.error.isPermanent
import eu.europa.ec.businesslogic.model.error.isRetryable
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
    onNavigateToDeviceSecurity: () -> Unit,
) {
    val state by viewModel.viewState.collectAsState()
    val context = LocalContext.current

    // Only trigger wallet activation if wallet is not already activated on device
    // This prevents unnecessary activation attempts and error screens
    LaunchedEffect(viewModel, state.isWalletAlreadyActivated, state.isActivating) {
        if (!state.isWalletAlreadyActivated && !state.isActivating && state.activationError == null) {
            logController.d("WalletSetupScreen", "Wallet not activated on device, triggering activation.")
            viewModel.setEvent(WalletSetupEvent.ActivateWallet)
        } else {
            logController.d("WalletSetupScreen", "Skipping activation - Already activated: ${state.isWalletAlreadyActivated}, In progress: ${state.isActivating}, Has error: ${state.activationError != null}")
        }
    }

    // Handle navigation and error effects from the ViewModel
    LaunchedEffect(viewModel) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is WalletSetupEffect.NavigateToHome -> onNavigateToHome()
                is WalletSetupEffect.NavigateToLogin -> onNavigateToLogin()
                is WalletSetupEffect.NavigateToDeviceSecurity -> onNavigateToDeviceSecurity()
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
            if (state.isWalletAlreadyActivated) {
                // Wallet already activated - show success message
                Icon(
                    painter = painterResource(id = R.drawable.ic_success),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Wallet Already Activated",
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Your wallet is already set up and ready to use.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Capture error in local val for smart cast
                val activationError = state.activationError
                when {
                    activationError != null -> {
                        // Error state with enhanced UX based on error type
                        WalletActivationErrorContent(
                            error = activationError,
                            retryCountdown = state.retryCountdown,
                            isDeleting = state.isDeleting,
                            onRetry = {
                                logController.d("WalletSetupScreen", "Retry button pressed")
                                viewModel.setEvent(WalletSetupEvent.Retry)
                            },
                            onSignOut = {
                                logController.d("WalletSetupScreen", "Sign Out button pressed")
                                viewModel.setEvent(WalletSetupEvent.SignOut)
                            },
                            onDeleteWallet = {
                                logController.d("WalletSetupScreen", "Delete Wallet button pressed")
                                viewModel.setEvent(WalletSetupEvent.DeleteWallet)
                            }
                        )
                    }
                    state.autoRetrying -> {
                        // Auto-retry state
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Retrying automatically...",
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Refreshing security session",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
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

        // Delete confirmation dialog
        if (state.showDeleteConfirmationDialog) {
            AlertDialog(
                onDismissRequest = { 
                    logController.d("WalletSetupScreen", "Delete confirmation dialog dismissed")
                    viewModel.setEvent(WalletSetupEvent.DismissDeleteConfirmationDialog) 
                },
                title = { Text("Delete Wallet Activation?") },
                text = {
                    Text(
                        "This will permanently delete your wallet activation from the server. You'll need to set up your wallet again. This action cannot be undone.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            logController.d("WalletSetupScreen", "Delete confirmation dialog - Yes, Delete pressed")
                            viewModel.setEvent(WalletSetupEvent.ConfirmDeleteWallet)
                        }
                    ) {
                        Text("Yes, Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            logController.d("WalletSetupScreen", "Delete confirmation dialog - Cancel pressed")
                            viewModel.setEvent(WalletSetupEvent.DismissDeleteConfirmationDialog)
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

/**
 * Enhanced error content with different visuals based on error type.
 */
@Composable
private fun WalletActivationErrorContent(
    error: WalletActivationError,
    retryCountdown: Int?,
    isDeleting: Boolean,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteWallet: () -> Unit,
) {
    // Choose icon and color based on error type
    val (iconRes, iconTint) = when (error) {
        is WalletActivationError.ChallengeRateLimited -> {
            R.drawable.ic_clock_timer to MaterialTheme.colorScheme.tertiary
        }
        is WalletActivationError.NetworkFailure,
        is WalletActivationError.TimeoutError -> {
            R.drawable.ic_error to MaterialTheme.colorScheme.error
        }
        is WalletActivationError.DeviceSecurityInsufficient,
        is WalletActivationError.AttestationRejected -> {
            R.drawable.ic_warning to MaterialTheme.colorScheme.error
        }
        else -> {
            R.drawable.ic_error to MaterialTheme.colorScheme.error
        }
    }

    Icon(
        painter = painterResource(id = iconRes),
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = iconTint
    )
    Spacer(modifier = Modifier.height(24.dp))

    // Title
    Text(
        text = error.getErrorTitle(),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(16.dp))

    // Message
    Text(
        text = error.getUserFriendlyMessage(),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    // Rate limit countdown
    AnimatedVisibility(
        visible = retryCountdown != null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { (retryCountdown?.toFloat() ?: 0f) / 60f },
                    modifier = Modifier.size(80.dp),
                    strokeWidth = 6.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${retryCountdown ?: 0}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "seconds",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // Device security requirements (for device-related errors)
    if (error is WalletActivationError.DeviceSecurityInsufficient) {
        Spacer(modifier = Modifier.height(24.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Required security features:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            error.missingRequirements.forEach { requirement ->
                Text(
                    text = "• $requirement",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(32.dp))

    // Action buttons based on error type
    if (error.isRetryable() && retryCountdown == null) {
        WrapButton(
            modifier = Modifier.fillMaxWidth(),
            buttonConfig = ButtonConfig(
                type = ButtonType.PRIMARY,
                onClick = onRetry,
            )
        ) {
            Text(text = "Try Again")
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    // Always show Sign Out option
    WrapButton(
        modifier = Modifier.fillMaxWidth(),
        buttonConfig = ButtonConfig(
            type = ButtonType.SECONDARY,
            onClick = onSignOut,
        )
    ) {
        Text(text = "Sign Out")
    }

    // Show Delete Wallet for permanent errors
    if (error.isPermanent()) {
        Spacer(modifier = Modifier.height(16.dp))
        WrapButton(
            modifier = Modifier.fillMaxWidth(),
            buttonConfig = ButtonConfig(
                type = ButtonType.SECONDARY,
                onClick = onDeleteWallet,
                enabled = !isDeleting
            )
        ) {
            Text(text = if (isDeleting) "Deleting..." else "Delete Wallet")
        }
    }
} 