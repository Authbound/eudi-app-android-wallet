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

package eu.europa.ec.startupfeature.ui.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import eu.europa.ec.resourceslogic.theme.ThemeManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.utils.OneTimeLaunchedEffect
import eu.europa.ec.uilogic.component.wrap.WrapImage
import eu.europa.ec.uilogic.extension.applyTestTag
import eu.europa.ec.uilogic.navigation.ModuleRoute
import eu.europa.ec.uilogic.navigation.StartupScreens
import eu.europa.ec.uilogic.test.StartupTestTags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach

@Composable
fun SplashScreen(
    navController: NavController,
    viewModel: SplashViewModel
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    Content(
        state = state,
        effectFlow = viewModel.effect,
        onNavigationRequested = { effect ->
            when (effect) {
                is Effect.Navigation.SwitchModule -> {
                    navController.navigate(effect.moduleRoute.route) {
                        popUpTo(ModuleRoute.StartupModule.route) { inclusive = true }
                    }
                }

                is Effect.Navigation.SwitchScreen -> {
                    navController.navigate(effect.route) {
                        popUpTo(StartupScreens.Splash.screenRoute) { inclusive = true }
                    }
                }
            }
        }
    )

    OneTimeLaunchedEffect {
        viewModel.setEvent(Event.Initialize)
    }
}

@Composable
private fun Content(
    state: State,
    effectFlow: Flow<Effect>,
    onNavigationRequested: (navigationEffect: Effect.Navigation) -> Unit
) {
    val visibilityState = remember {
        MutableTransitionState(false).apply {
            targetState = true
        }
    }
    
    val isDark = ThemeManager.instance.set.isInDarkMode
    
    // Modern gradient background colors based on theme
    val gradientStart = MaterialTheme.colorScheme.surface
    val gradientEnd = if (isDark) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    }

    Scaffold { paddingValues ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .applyTestTag(StartupTestTags.Splash.ROOT)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(gradientStart, gradientEnd),
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Decorative circles for a professional "premium" look
            DecorativeCircles(
                modifier = Modifier.align(Alignment.TopEnd),
                color = if (isDark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            )

            AnimatedVisibility(
                visibleState = visibilityState,
                enter = fadeIn(animationSpec = tween(state.logoAnimationDuration)),
                exit = fadeOut(animationSpec = tween(state.logoAnimationDuration)),
            ) {
                // Logo color changes based on theme for maximum contrast and professional look
                val logoTint = if (isDark) Color.White else MaterialTheme.colorScheme.primary
                
                WrapImage(
                    iconData = AppIcons.AuthboundLogoBold,
                    modifier = Modifier.height(180.dp),
                    colorFilter = ColorFilter.tint(logoTint),
                    contentScale = ContentScale.Fit
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
private fun DecorativeCircles(
    modifier: Modifier = Modifier,
    color: Color
) {
    Canvas(
        modifier = modifier
            .size(280.dp)
            .padding(16.dp)
    ) {
        // Large circle (outline) - slightly offset for modern feel
        drawCircle(
            color = color.copy(alpha = 0.08f),
            radius = 110.dp.toPx(),
            center = Offset(size.width * 0.8f, size.height * 0.2f),
            style = Stroke(width = 1.5.dp.toPx())
        )
        // Medium circle (filled)
        drawCircle(
            color = color.copy(alpha = 0.12f),
            radius = 60.dp.toPx(),
            center = Offset(size.width * 0.5f, size.height * 0.4f)
        )
        // Small circle (filled)
        drawCircle(
            color = color.copy(alpha = 0.10f),
            radius = 25.dp.toPx(),
            center = Offset(size.width * 0.9f, size.height * 0.6f)
        )
    }
}
