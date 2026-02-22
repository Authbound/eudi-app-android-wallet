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

package eu.europa.ec.authboundpidfeature.ui.intro

import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.browser.customtabs.CustomTabsIntent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.extension.paddingFrom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach

@Composable
fun AuthboundPidIntroScreen(
    navController: NavController,
    viewModel: AuthboundPidIntroViewModel
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
                }
            },
            onOpenAuthboundPidFlow = { effect ->
                val customTabsIntent = CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build()
                customTabsIntent.launchUrl(context, Uri.parse(effect.redirectUrl))
            },
            paddingValues = paddingValues
        )
    }
}

@Composable
private fun Content(
    state: State,
    effectFlow: Flow<Effect>,
    onEventSend: (Event) -> Unit,
    onNavigationRequested: (Effect.Navigation) -> Unit,
    onOpenAuthboundPidFlow: (Effect.OpenAuthboundPidFlow) -> Unit,
    paddingValues: PaddingValues
) {
    val view = LocalView.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .paddingFrom(paddingValues)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            HeroSection()

            Spacer(modifier = Modifier.height(24.dp))

            StepVisualization()

            Spacer(modifier = Modifier.height(24.dp))

            RequirementsSection()

            Spacer(modifier = Modifier.height(32.dp))

            WrapButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                buttonConfig = ButtonConfig(
                    type = ButtonType.PRIMARY,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onEventSend(Event.StartVerification)
                    }
                )
            ) {
                Text(
                    text = stringResource(R.string.authboundpid_intro_verify_button),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }

        // Top fade gradient overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .align(Alignment.TopCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background.copy(alpha = 0f)
                        )
                    )
                )
        )
    }

    LaunchedEffect(Unit) {
        effectFlow.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> onNavigationRequested(effect)
                is Effect.OpenAuthboundPidFlow -> onOpenAuthboundPidFlow(effect)
            }
        }.collect()
    }
}

@Composable
private fun HeroSection() {
    val gradientStart = Color(0xFF047857)
    val gradientEnd = Color(0xFF0D9488)
    val accentColor = Color(0xFF34D399)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .height(220.dp)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(gradientStart, gradientEnd),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
    ) {
        Canvas(
            modifier = Modifier
                .size(180.dp)
                .padding(12.dp)
                .align(Alignment.TopEnd)
        ) {
            drawCircle(
                color = accentColor.copy(alpha = 0.15f),
                radius = 65.dp.toPx(),
                center = Offset(size.width * 0.55f, size.height * 0.4f),
                style = Stroke(width = 2.5.dp.toPx())
            )
            drawCircle(
                color = accentColor.copy(alpha = 0.18f),
                radius = 24.dp.toPx(),
                center = Offset(size.width * 0.2f, size.height * 0.65f)
            )
            drawCircle(
                color = accentColor.copy(alpha = 0.25f),
                radius = 10.dp.toPx(),
                center = Offset(size.width * 0.8f, size.height * 0.75f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.2f),
                shape = RoundedCornerShape(50)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WrapIcon(
                        iconData = AppIcons.Verified,
                        customTint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.authboundpid_intro_badge),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.authboundpid_intro_title),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.authboundpid_intro_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f),
                lineHeight = 24.sp
            )
        }
    }
}

@Composable
private fun StepVisualization() {
    data class StepInfo(
        val number: String,
        val title: String,
        val description: String,
        val color: Color
    )

    val steps = listOf(
        StepInfo("1", stringResource(R.string.authboundpid_intro_step_open), stringResource(R.string.authboundpid_intro_step_open_desc), Color(0xFF3B82F6)),
        StepInfo("2", stringResource(R.string.authboundpid_intro_step_scan), stringResource(R.string.authboundpid_intro_step_scan_desc), Color(0xFF8B5CF6)),
        StepInfo("3", stringResource(R.string.authboundpid_intro_step_done), stringResource(R.string.authboundpid_intro_step_done_desc), Color(0xFF10B981))
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.authboundpid_intro_how_it_works),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top
            ) {
                steps.forEach { step ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(step.color.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                WrapIcon(
                                    iconData = when (step.number) {
                                        "1" -> AppIcons.OpenInBrowser
                                        "2" -> AppIcons.NFC
                                        else -> AppIcons.Check
                                    },
                                    customTint = step.color,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(x = 2.dp, y = (-2).dp)
                                    .clip(CircleShape)
                                    .background(step.color),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = step.number,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = step.title,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = step.description,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 14.sp,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RequirementsSection() {
    val requirements = listOf(
        stringResource(R.string.authboundpid_intro_requirement_passport),
        stringResource(R.string.authboundpid_intro_requirement_lighting),
        stringResource(R.string.authboundpid_intro_requirement_time)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.authboundpid_intro_requirements_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                requirements.forEach { requirement ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981).copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            WrapIcon(
                                iconData = AppIcons.Check,
                                modifier = Modifier.size(14.dp),
                                customTint = Color(0xFF10B981)
                            )
                        }
                        Text(
                            text = requirement,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
