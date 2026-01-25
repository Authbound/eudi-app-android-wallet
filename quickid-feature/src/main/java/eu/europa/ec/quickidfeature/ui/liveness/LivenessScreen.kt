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

package eu.europa.ec.quickidfeature.ui.liveness

import android.Manifest
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.amplifyframework.ui.liveness.ui.FaceLivenessDetector
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.utils.LifecycleEffect
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.extension.paddingFrom
import eu.europa.ec.uilogic.navigation.QuickIdScreens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach

private const val TAG = "LivenessScreen"

/**
 * Face Liveness verification screen using AWS Amplify FaceLivenessDetector.
 *
 * This screen integrates the AWS Face Liveness SDK to verify that the user
 * performing the verification is a real person and matches the passport photo.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LivenessScreen(
    navController: NavController,
    viewModel: LivenessViewModel
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    ContentScreen(
        isLoading = state.isLoading,
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack = { viewModel.setEvent(Event.GoBack) },
        contentErrorConfig = state.error
    ) { paddingValues ->
        when {
            !cameraPermissionState.status.isGranted -> {
                CameraPermissionContent(
                    onRequestPermission = { cameraPermissionState.launchPermissionRequest() },
                    paddingValues = paddingValues
                )
            }
            state.livenessSessionId.isEmpty() -> {
                LoadingContent(paddingValues = paddingValues)
            }
            else -> {
                LivenessContent(
                    state = state,
                    effectFlow = viewModel.effect,
                    onEventSend = { viewModel.setEvent(it) },
                    onNavigationRequested = { navigationEffect ->
                        when (navigationEffect) {
                            is Effect.Navigation.Pop -> navController.popBackStack()
                            is Effect.Navigation.SwitchScreen -> {
                                navController.navigate(navigationEffect.screenRoute) {
                                    popUpTo(QuickIdScreens.Liveness.screenRoute) {
                                        inclusive = true
                                    }
                                }
                            }
                        }
                    },
                    paddingValues = paddingValues
                )
            }
        }
    }

    LifecycleEffect(
        lifecycleOwner = LocalLifecycleOwner.current,
        lifecycleEvent = Lifecycle.Event.ON_RESUME
    ) {
        viewModel.setEvent(Event.Init)
    }
}

/**
 * Camera permission request UI with premium styling.
 */
@Composable
private fun CameraPermissionContent(
    onRequestPermission: () -> Unit,
    paddingValues: PaddingValues
) {
    // Brand colors
    val gradientStart = Color(0xFF8B5CF6)  // Purple
    val gradientEnd = Color(0xFF6366F1)    // Indigo
    val accentColor = Color(0xFFA78BFA)    // Light purple

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .paddingFrom(paddingValues)
    ) {
        // Hero section
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(400)) + slideInVertically(
                animationSpec = tween(400),
                initialOffsetY = { -it / 4 }
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(gradientStart, gradientEnd),
                            start = Offset(0f, 0f),
                            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                        )
                    )
            ) {
                // Decorative elements
                Canvas(
                    modifier = Modifier
                        .size(140.dp)
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    drawCircle(
                        color = accentColor.copy(alpha = 0.12f),
                        radius = 50.dp.toPx(),
                        center = Offset(size.width * 0.6f, size.height * 0.4f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = accentColor.copy(alpha = 0.15f),
                        radius = 18.dp.toPx(),
                        center = Offset(size.width * 0.3f, size.height * 0.6f)
                    )
                }

                // Large background icon
                WrapIcon(
                    iconData = AppIcons.User,
                    customTint = Color.White.copy(alpha = 0.06f),
                    modifier = Modifier
                        .size(220.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = 50.dp, y = 50.dp)
                        .rotate(-15f)
                )

                // Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Camera Access",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "We need camera access to verify\nyour face matches your passport",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.9f),
                        lineHeight = 24.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Permission explanation
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(300, delayMillis = 200))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    WrapIcon(
                        iconData = AppIcons.User,
                        customTint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Your privacy is protected",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "The camera is only used during verification.\nNo photos are stored on your device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // CTA Button
        WrapButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            buttonConfig = ButtonConfig(
                type = ButtonType.PRIMARY,
                onClick = onRequestPermission
            )
        ) {
            Text(
                text = "Enable Camera",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

/**
 * Loading state while waiting for liveness session.
 */
@Composable
private fun LoadingContent(paddingValues: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .paddingFrom(paddingValues),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Preparing verification...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Main liveness detection content with AWS FaceLivenessDetector.
 */
@Composable
private fun LivenessContent(
    state: State,
    effectFlow: Flow<Effect>,
    onEventSend: (Event) -> Unit,
    onNavigationRequested: (Effect.Navigation) -> Unit,
    paddingValues: PaddingValues
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .paddingFrom(paddingValues)
    ) {
        // AWS Face Liveness Detector
        FaceLivenessDetector(
            sessionId = state.livenessSessionId,
            region = state.region,
            onComplete = {
                Log.i(TAG, "Liveness check completed successfully")
                onEventSend(Event.LivenessComplete)
            },
            onError = { error ->
                Log.e(TAG, "Liveness check failed: ${error.message}", error.throwable)
                onEventSend(Event.LivenessFailed(error.message))
            }
        )

        // Overlay instruction at the bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.7f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    WrapIcon(
                        iconData = AppIcons.TouchId,
                        customTint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Follow the on-screen prompts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
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
