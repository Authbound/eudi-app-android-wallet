/*
 * Copyright (c) 2025 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission (subsequent versions of the EUPL (the "Licence"); You may not use this work
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

package eu.europa.ec.quickidfeature.ui.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.utils.LifecycleEffect
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.extension.paddingFrom
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.QuickIdScreens
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach

@Composable
fun VerificationResultScreen(
    navController: NavController,
    viewModel: VerificationResultViewModel
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()

    ContentScreen(
        isLoading = state.isLoading || state.isIssuingCredential,
        navigatableAction = ScreenNavigateAction.NONE,
        onBack = { /* Prevent back navigation from result */ },
        contentErrorConfig = state.error
    ) { paddingValues ->
        Content(
            state = state,
            effectFlow = viewModel.effect,
            onEventSend = { viewModel.setEvent(it) },
            onNavigationRequested = { navigationEffect ->
                when (navigationEffect) {
                    is Effect.Navigation.Pop -> navController.popBackStack()
                    is Effect.Navigation.SwitchScreen -> {
                        navController.navigate(navigationEffect.screenRoute) {
                            popUpTo(QuickIdScreens.Intro.screenRoute) {
                                inclusive = true
                            }
                        }
                    }
                    is Effect.Navigation.NavigateToIssuance -> {
                        // Navigate to the document offer screen with the offer URI
                        navController.navigate(
                            IssuanceScreens.DocumentOffer.screenRoute
                                .replace("{offerConfig}", navigationEffect.offerUri)
                        ) {
                            popUpTo(QuickIdScreens.Intro.screenRoute) {
                                inclusive = true
                            }
                        }
                    }
                    is Effect.Navigation.PopToDashboard -> {
                        navController.navigate(DashboardScreens.Dashboard.screenRoute) {
                            popUpTo(DashboardScreens.Dashboard.screenRoute) {
                                inclusive = true
                            }
                        }
                    }
                }
            },
            paddingValues = paddingValues
        )
    }

    LifecycleEffect(
        lifecycleOwner = LocalLifecycleOwner.current,
        lifecycleEvent = Lifecycle.Event.ON_RESUME
    ) {
        viewModel.setEvent(Event.Init)
    }
}

@Composable
private fun Content(
    state: State,
    effectFlow: Flow<Effect>,
    onEventSend: (Event) -> Unit,
    onNavigationRequested: (Effect.Navigation) -> Unit,
    paddingValues: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .paddingFrom(paddingValues),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (state.isSuccess) {
            SuccessContent(
                firstName = state.firstName,
                lastName = state.lastName,
                isIssuingCredential = state.isIssuingCredential,
                onIssueCredential = { onEventSend(Event.IssueCredential) },
                onGoToDashboard = { onEventSend(Event.GoToDashboard) }
            )
        } else {
            FailureContent(
                errorMessage = state.errorMessage,
                isRecoverable = state.isRecoverable,
                onRetry = { onEventSend(Event.Retry) },
                onGoToDashboard = { onEventSend(Event.GoToDashboard) }
            )
        }
    }

    LaunchedEffect(Unit) {
        effectFlow.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> onNavigationRequested(effect)
            }
        }.collect()
    }
}

@Composable
private fun SuccessContent(
    firstName: String?,
    lastName: String?,
    isIssuingCredential: Boolean,
    onIssueCredential: () -> Unit,
    onGoToDashboard: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        WrapIcon(
            iconData = AppIcons.Success,
            modifier = Modifier.size(100.dp),
            customTint = MaterialTheme.colorScheme.primary
        )

        VSpacer.Large()

        Text(
            text = "Identity Verified!",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        VSpacer.Medium()

        if (firstName != null || lastName != null) {
            Text(
                text = listOfNotNull(firstName, lastName).joinToString(" "),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            VSpacer.Medium()
        }

        Text(
            text = "Your identity has been successfully verified. You can now issue your Authbound ID credential.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        VSpacer.ExtraLarge()

        WrapButton(
            modifier = Modifier.fillMaxWidth(),
            buttonConfig = ButtonConfig(
                type = ButtonType.PRIMARY,
                enabled = !isIssuingCredential,
                onClick = onIssueCredential
            )
        ) {
            Text(text = if (isIssuingCredential) "Issuing Credential..." else "Get Authbound ID")
        }

        VSpacer.Medium()

        WrapButton(
            modifier = Modifier.fillMaxWidth(),
            buttonConfig = ButtonConfig(
                type = ButtonType.SECONDARY,
                enabled = !isIssuingCredential,
                onClick = onGoToDashboard
            )
        ) {
            Text(text = "Maybe Later")
        }
    }
}

@Composable
private fun FailureContent(
    errorMessage: String?,
    isRecoverable: Boolean,
    onRetry: () -> Unit,
    onGoToDashboard: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        WrapIcon(
            iconData = AppIcons.Error,
            modifier = Modifier.size(100.dp),
            customTint = MaterialTheme.colorScheme.error
        )

        VSpacer.Large()

        Text(
            text = "Verification Failed",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )

        VSpacer.Medium()

        Text(
            text = errorMessage ?: "We couldn't verify your identity. Please try again.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        VSpacer.ExtraLarge()

        if (isRecoverable) {
            WrapButton(
                modifier = Modifier.fillMaxWidth(),
                buttonConfig = ButtonConfig(
                    type = ButtonType.PRIMARY,
                    onClick = onRetry
                )
            ) {
                Text(text = "Try Again")
            }

            VSpacer.Medium()
        }

        WrapButton(
            modifier = Modifier.fillMaxWidth(),
            buttonConfig = ButtonConfig(
                type = ButtonType.SECONDARY,
                onClick = onGoToDashboard
            )
        ) {
            Text(text = "Back to Dashboard")
        }
    }
}
