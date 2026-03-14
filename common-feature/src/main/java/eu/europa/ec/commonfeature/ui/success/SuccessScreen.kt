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

package eu.europa.ec.commonfeature.ui.success

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.commonfeature.config.SuccessUIConfig
import eu.europa.ec.commonfeature.util.TestTag
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.resourceslogic.theme.values.success
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.PERCENTAGE_25
import eu.europa.ec.uilogic.component.utils.SIZE_MEDIUM
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapImage
import eu.europa.ec.uilogic.config.ConfigNavigation
import eu.europa.ec.uilogic.config.NavigationType
import eu.europa.ec.uilogic.extension.applyTestTag
import eu.europa.ec.uilogic.extension.cacheDeepLink
import eu.europa.ec.uilogic.navigation.StartupScreens
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow

@Composable
fun SuccessScreen(
    navController: NavController,
    viewModel: SuccessViewModel
) {
    val context = LocalContext.current
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()

    ContentScreen(
        isLoading = false,
        onBack = { viewModel.setEvent(Event.BackPressed) },
        navigatableAction = ScreenNavigateAction.NONE
    ) { paddingValues ->
        SuccessScreenView(
            state = state,
            effectFlow = viewModel.effect,
            onEventSent = { event -> viewModel.setEvent(event) },
            onNavigationRequested = { navigationEffect ->
                when (navigationEffect) {
                    is Effect.Navigation.SwitchScreen -> {
                        navController.navigate(navigationEffect.screenRoute) {
                            navigationEffect.popUpRoute?.let { popUpToRoute ->
                                popUpTo(popUpToRoute) {
                                    inclusive = true
                                }
                            }
                        }
                    }

                    is Effect.Navigation.PopBackStackUpTo -> {
                        navController.popBackStack(
                            route = navigationEffect.screenRoute,
                            inclusive = navigationEffect.inclusive
                        )
                    }

                    is Effect.Navigation.DeepLink -> {
                        context.cacheDeepLink(navigationEffect.link)
                        navigationEffect.routeToPop?.let {
                            navController.popBackStack(
                                route = it,
                                inclusive = false
                            )
                        } ?: navController.popBackStack()
                    }

                    is Effect.Navigation.Pop -> navController.popBackStack()
                }
            },
            paddingValues = paddingValues
        )
    }
}

@Composable
private fun SuccessScreenView(
    state: State,
    effectFlow: Flow<Effect>,
    onEventSent: (Event) -> Unit,
    onNavigationRequested: (Effect.Navigation) -> Unit,
    paddingValues: PaddingValues
) {
    // Entrance animations
    val iconScale = remember { Animatable(0f) }
    val ringProgress = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(0f) }
    val buttonsAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Staggered entrance: ring draws → icon pops in → text fades → buttons slide up
        ringProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(600, easing = FastOutSlowInEasing)
        )
        iconScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(400, easing = FastOutSlowInEasing)
        )
        contentAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(350)
        )
        buttonsAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(300)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 24.dp),
    ) {
        // Center content area — takes available space
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val imageConfig = state.successConfig.imageConfig

            // Animated icon with ring backdrop
            Box(
                modifier = Modifier.size(140.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Animated ring that draws in
                val ringColor = when (imageConfig.type) {
                    is SuccessUIConfig.ImageConfig.Type.Default ->
                        MaterialTheme.colorScheme.success
                    is SuccessUIConfig.ImageConfig.Type.Drawable ->
                        imageConfig.tint ?: MaterialTheme.colorScheme.success
                }

                Canvas(modifier = Modifier.size(140.dp)) {
                    val sweepAngle = 360f * ringProgress.value
                    drawArc(
                        color = ringColor.copy(alpha = 0.12f),
                        startAngle = -90f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    )
                }

                // Inner filled circle backdrop
                Canvas(modifier = Modifier.size(120.dp)) {
                    drawCircle(
                        color = ringColor.copy(alpha = 0.08f),
                    )
                }

                // The actual icon — scales in after ring
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .alpha(iconScale.value),
                    contentAlignment = Alignment.Center,
                ) {
                    when (imageConfig.type) {
                        is SuccessUIConfig.ImageConfig.Type.Default -> WrapImage(
                            modifier = Modifier.size(64.dp),
                            iconData = AppIcons.Success,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.success),
                            contentScale = ContentScale.Fit
                        )

                        is SuccessUIConfig.ImageConfig.Type.Drawable -> WrapImage(
                            modifier = Modifier.size(64.dp),
                            iconData = imageConfig.type.icon,
                            colorFilter = imageConfig.tint?.let { ColorFilter.tint(it) },
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Title text — fades in after icon
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(contentAlpha.value),
                text = state.successConfig.textElementsConfig.text,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = state.successConfig.textElementsConfig.color
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Description text
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .alpha(contentAlpha.value),
                text = state.successConfig.textElementsConfig.description,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                textAlign = TextAlign.Center,
            )
        }

        // Bottom action buttons with proper spacing
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(buttonsAlpha.value)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            state.successConfig.buttonConfig.forEach { buttonConfig ->
                Button(
                    onEventSent = onEventSent,
                    config = buttonConfig
                )
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

@Composable
private fun Button(
    onEventSent: (Event) -> Unit,
    config: SuccessUIConfig.ButtonConfig
) {
    when (config.style) {
        SuccessUIConfig.ButtonConfig.Style.PRIMARY -> {
            WrapButton(
                buttonConfig = ButtonConfig(
                    type = ButtonType.PRIMARY,
                    onClick = { onEventSent(Event.ButtonClicked(config)) },
                ),
                modifier = Modifier
                    .applyTestTag(TestTag.SuccessScreen.PRIMARY_BUTTON)
                    .fillMaxWidth(),
            ) {
                ButtonRow(text = config.text)
            }
        }

        SuccessUIConfig.ButtonConfig.Style.OUTLINE -> {
            WrapButton(
                buttonConfig = ButtonConfig(
                    type = ButtonType.SECONDARY,
                    onClick = { onEventSent(Event.ButtonClicked(config)) },
                ),
                modifier = Modifier
                    .applyTestTag(TestTag.SuccessScreen.SECONDARY_BUTTON)
                    .fillMaxWidth(),
            ) {
                ButtonRow(text = config.text)
            }
        }
    }
}

@Composable
private fun ButtonRow(text: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@ThemeModePreviews
@Composable
private fun SuccessDefaultPreview() {
    PreviewTheme {
        SuccessScreenView(
            state = State(
                successConfig = SuccessUIConfig(
                    textElementsConfig = SuccessUIConfig.TextElementsConfig(
                        text = stringResource(R.string.generic_success),
                        description = stringResource(R.string.quick_pin_change_success_description),
                    ),
                    imageConfig = SuccessUIConfig.ImageConfig(),
                    buttonConfig = listOf(
                        SuccessUIConfig.ButtonConfig(
                            text = "Back",
                            style = SuccessUIConfig.ButtonConfig.Style.PRIMARY,
                            navigation = ConfigNavigation(
                                navigationType = NavigationType.PopTo(StartupScreens.Splash),
                            )
                        )
                    ),
                    onBackScreenToNavigate = ConfigNavigation(
                        navigationType = NavigationType.PopTo(StartupScreens.Splash),
                    ),
                )
            ),
            effectFlow = Channel<Effect>().receiveAsFlow(),
            onEventSent = {},
            onNavigationRequested = {},
            paddingValues = PaddingValues(SIZE_MEDIUM.dp)
        )
    }
}

@ThemeModePreviews
@Composable
private fun SuccessPendingPreview() {
    PreviewTheme {
        SuccessScreenView(
            state = State(
                successConfig = SuccessUIConfig(
                    textElementsConfig = SuccessUIConfig.TextElementsConfig(
                        text = stringResource(R.string.issuance_add_document_deferred_success_text),
                        description = stringResource(R.string.issuance_add_document_deferred_success_description),
                        color = ThemeColors.pending,
                    ),
                    imageConfig = SuccessUIConfig.ImageConfig(
                        type = SuccessUIConfig.ImageConfig.Type.Drawable(icon = AppIcons.InProgress),
                        tint = ThemeColors.primary,
                        screenPercentageSize = PERCENTAGE_25,
                    ),
                    buttonConfig = listOf(
                        SuccessUIConfig.ButtonConfig(
                            text = "back",
                            style = SuccessUIConfig.ButtonConfig.Style.PRIMARY,
                            navigation = ConfigNavigation(
                                navigationType = NavigationType.PopTo(StartupScreens.Splash),
                            )
                        )
                    ),
                    onBackScreenToNavigate = ConfigNavigation(
                        navigationType = NavigationType.PopTo(StartupScreens.Splash),
                    ),
                )
            ),
            effectFlow = Channel<Effect>().receiveAsFlow(),
            onEventSent = {},
            onNavigationRequested = {},
            paddingValues = PaddingValues(SIZE_MEDIUM.dp)
        )
    }
}
