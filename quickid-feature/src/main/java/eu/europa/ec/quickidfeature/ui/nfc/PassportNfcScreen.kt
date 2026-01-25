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

package eu.europa.ec.quickidfeature.ui.nfc

import android.app.Activity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ContentTitle
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.utils.LifecycleEffect
import eu.europa.ec.uilogic.component.utils.NfcTagHandler
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.extension.paddingFrom
import eu.europa.ec.uilogic.navigation.QuickIdScreens
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import org.koin.compose.koinInject

@Composable
fun PassportNfcScreen(
    navController: NavController,
    viewModel: PassportNfcViewModel
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val nfcTagHandler: NfcTagHandler = koinInject()

    // Enable NFC foreground dispatch when this screen is visible
    DisposableEffect(activity) {
        if (activity != null) {
            nfcTagHandler.requestDispatch(activity)
        }

        onDispose {
            if (activity != null) {
                nfcTagHandler.releaseDispatch(activity)
            }
        }
    }

    // Collect NFC tags from the handler
    LaunchedEffect(Unit) {
        nfcTagHandler.tagFlow.collect { tag ->
            viewModel.setEvent(Event.NfcTagDiscovered(tag))
        }
    }

    ContentScreen(
        isLoading = state.isLoading,
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack = { viewModel.setEvent(Event.GoBack) },
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
                            popUpTo(QuickIdScreens.NfcReading.screenRoute)
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
    val animatedProgress by animateFloatAsState(
        targetValue = state.progressPercent / 100f,
        animationSpec = tween(300),
        label = "progress"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .paddingFrom(paddingValues),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ContentTitle(
            modifier = Modifier.fillMaxWidth(),
            title = "Read Passport NFC",
            subtitle = "Place your phone on the back of your passport and hold it steady."
        )

        VSpacer.ExtraLarge()

        // NFC animation/icon based on state
        when (state.readingState) {
            ReadingState.WaitingForTag -> {
                Icon(
                    imageVector = Icons.Default.Nfc,
                    contentDescription = "NFC",
                    modifier = Modifier.size(120.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            ReadingState.Authenticating,
            ReadingState.Reading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(120.dp),
                    strokeWidth = 8.dp
                )
            }
            ReadingState.Success -> {
                WrapIcon(
                    iconData = AppIcons.Success,
                    modifier = Modifier.size(120.dp),
                    customTint = MaterialTheme.colorScheme.primary
                )
            }
            ReadingState.Failure -> {
                WrapIcon(
                    iconData = AppIcons.Error,
                    modifier = Modifier.size(120.dp),
                    customTint = MaterialTheme.colorScheme.error
                )
            }
        }

        VSpacer.Large()

        // Progress indicator
        if (state.readingState == ReadingState.Reading || state.readingState == ReadingState.Authenticating) {
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth()
            )
            VSpacer.Medium()
        }

        // Status message
        Text(
            text = state.progressMessage,
            style = MaterialTheme.typography.bodyLarge,
            color = if (state.readingState == ReadingState.Failure)
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // Retry button for failures
        if (state.readingState == ReadingState.Failure) {
            VSpacer.Large()
            WrapButton(
                modifier = Modifier.fillMaxWidth(),
                buttonConfig = ButtonConfig(
                    type = ButtonType.SECONDARY,
                    onClick = { onEventSend(Event.Retry) }
                )
            ) {
                Text(text = "Try Again")
            }
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
