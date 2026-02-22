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

package eu.europa.ec.authboundpidfeature.ui.result

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.resourceslogic.R
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
import eu.europa.ec.uilogic.navigation.AuthboundPidScreens
import eu.europa.ec.uilogic.navigation.DashboardScreens
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach

@Composable
fun AuthboundPidResultScreen(
    navController: NavController,
    viewModel: AuthboundPidResultViewModel
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()

    ContentScreen(
        isLoading = false,
        navigatableAction = ScreenNavigateAction.NONE,
        onBack = { /* Prevent back from result */ },
        contentErrorConfig = null
    ) { paddingValues ->
        Content(
            state = state,
            effectFlow = viewModel.effect,
            onEventSend = { viewModel.setEvent(it) },
            onNavigationRequested = { navigationEffect ->
                when (navigationEffect) {
                    is Effect.Navigation.PopToIntro -> {
                        navController.navigate(AuthboundPidScreens.Intro.screenRoute) {
                            popUpTo(AuthboundPidScreens.Intro.screenRoute) {
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
        WrapIcon(
            iconData = if (state.isError) AppIcons.Error else AppIcons.Info,
            modifier = Modifier.size(100.dp),
            customTint = if (state.isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            }
        )

        VSpacer.Large()

        Text(
            text = state.title,
            style = MaterialTheme.typography.headlineMedium,
            color = if (state.isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.Center
        )

        VSpacer.Medium()

        Text(
            text = state.message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        VSpacer.ExtraLarge()

        if (state.showRetryButton) {
            WrapButton(
                modifier = Modifier.fillMaxWidth(),
                buttonConfig = ButtonConfig(
                    type = ButtonType.PRIMARY,
                    onClick = { onEventSend(Event.Retry) }
                )
            ) {
                Text(text = state.retryButtonText)
            }

            VSpacer.Medium()
        }

        WrapButton(
            modifier = Modifier.fillMaxWidth(),
            buttonConfig = ButtonConfig(
                type = ButtonType.SECONDARY,
                onClick = { onEventSend(Event.GoToDashboard) }
            )
        ) {
            Text(text = stringResource(R.string.authboundpid_go_to_dashboard))
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
