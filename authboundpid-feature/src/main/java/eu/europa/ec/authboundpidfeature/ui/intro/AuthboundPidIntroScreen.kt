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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconDataUi
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

// ── Brand colors ────────────────────────────────────────────────────
private val StepBlue = Color(0xFF3B82F6)
private val StepPurple = Color(0xFF8B5CF6)
private val StepGreen = Color(0xFF10B981)

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
            // Small top padding before hero (user requested)
            Spacer(modifier = Modifier.height(12.dp))

            HeroSection()

            Spacer(modifier = Modifier.height(28.dp))

            StepTimeline()

            Spacer(modifier = Modifier.height(28.dp))

            RequirementsSection()

            Spacer(modifier = Modifier.height(36.dp))

            // CTA button — taller with proper vertical padding
            WrapButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 0.dp),
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

            Spacer(modifier = Modifier.height(32.dp))
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

// ── Hero Section (UNTOUCHED per user request) ───────────────────────

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

// ── Step Timeline (vertical with connecting line) ───────────────────

private data class StepInfo(
    val number: String,
    val title: String,
    val description: String,
    val color: Color,
    val icon: IconDataUi
)

@Composable
private fun StepTimeline() {
    val steps = listOf(
        StepInfo(
            "1",
            stringResource(R.string.authboundpid_intro_step_open),
            stringResource(R.string.authboundpid_intro_step_open_desc),
            StepBlue,
            AppIcons.OpenInBrowser
        ),
        StepInfo(
            "2",
            stringResource(R.string.authboundpid_intro_step_scan),
            stringResource(R.string.authboundpid_intro_step_scan_desc),
            StepPurple,
            AppIcons.QrScanner
        ),
        StepInfo(
            "3",
            stringResource(R.string.authboundpid_intro_step_done),
            stringResource(R.string.authboundpid_intro_step_done_desc),
            StepGreen,
            AppIcons.Verified
        )
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
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                steps.forEachIndexed { index, step ->
                    StepRow(step = step, isLast = index == steps.lastIndex)
                }
            }
        }
    }
}

@Composable
private fun StepRow(step: StepInfo, isLast: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top
    ) {
        // Left rail: icon circle + connecting line
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(48.dp)
        ) {
            // Step number circle with colored background
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(step.color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                WrapIcon(
                    iconData = step.icon,
                    customTint = step.color,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Connecting line (skip on last step)
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp)
                        .background(
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Right side: text content
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                // Step number badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = step.color.copy(alpha = 0.10f)
                ) {
                    Text(
                        text = step.number,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = step.color,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = step.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

// ── Requirements Section (individual icon per requirement) ──────────

private data class RequirementInfo(
    val text: String,
    val icon: IconDataUi,
    val color: Color
)

@Composable
private fun RequirementsSection() {
    val iconTint = MaterialTheme.colorScheme.onSurfaceVariant
    val requirements = listOf(
        RequirementInfo(
            text = stringResource(R.string.authboundpid_intro_requirement_passport),
            icon = AppIcons.Id,
            color = iconTint
        ),
        RequirementInfo(
            text = stringResource(R.string.authboundpid_intro_requirement_lighting),
            icon = AppIcons.Visibility,
            color = iconTint
        ),
        RequirementInfo(
            text = stringResource(R.string.authboundpid_intro_requirement_time),
            icon = AppIcons.ClockTimer,
            color = iconTint
        )
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
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                requirements.forEachIndexed { index, requirement ->
                    RequirementRow(requirement = requirement)
                    if (index < requirements.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 60.dp, end = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RequirementRow(requirement: RequirementInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Neutral icon circle
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            WrapIcon(
                iconData = requirement.icon,
                modifier = Modifier.size(16.dp),
                customTint = requirement.color
            )
        }

        Text(
            text = requirement.text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        // Checkmark on the right
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(StepGreen.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            WrapIcon(
                iconData = AppIcons.Check,
                modifier = Modifier.size(12.dp),
                customTint = StepGreen
            )
        }
    }
}
