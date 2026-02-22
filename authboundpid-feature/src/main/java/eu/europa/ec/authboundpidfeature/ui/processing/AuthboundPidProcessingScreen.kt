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

package eu.europa.ec.authboundpidfeature.ui.processing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
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
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.utils.LifecycleEffect
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.extension.paddingFrom
import eu.europa.ec.uilogic.navigation.AuthboundPidScreens
import eu.europa.ec.uilogic.navigation.DashboardScreens
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach

@Composable
fun AuthboundPidProcessingScreen(
    navController: NavController,
    viewModel: AuthboundPidProcessingViewModel
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()

    ContentScreen(
        isLoading = false,
        navigatableAction = ScreenNavigateAction.NONE,
        onBack = { /* Prevent back during processing */ },
        contentErrorConfig = state.error
    ) { paddingValues ->
        Content(
            state = state,
            effectFlow = viewModel.effect,
            onEventSend = { viewModel.setEvent(it) },
            onNavigationRequested = { navigationEffect ->
                when (navigationEffect) {
                    is Effect.Navigation.NavigateToIssuance -> {
                        navController.navigate(navigationEffect.screenRoute) {
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
                    is Effect.Navigation.NavigateToResult -> {
                        navController.navigate(
                            AuthboundPidScreens.Result.screenRoute
                                .replace("{resultType}", navigationEffect.resultType.name)
                        ) {
                            popUpTo(AuthboundPidScreens.Processing.screenRoute) {
                                inclusive = true
                            }
                        }
                    }
                    is Effect.Navigation.PopToIntro -> {
                        navController.navigate(AuthboundPidScreens.Intro.screenRoute) {
                            popUpTo(AuthboundPidScreens.Intro.screenRoute) {
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
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 4.dp
        )

        VSpacer.Large()

        Text(
            text = state.statusMessage,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        VSpacer.Medium()

        Text(
            text = stringResource(R.string.authboundpid_processing_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    LaunchedEffect(Unit) {
        effectFlow.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> onNavigationRequested(effect)
            }
        }.collect()
    }
}
