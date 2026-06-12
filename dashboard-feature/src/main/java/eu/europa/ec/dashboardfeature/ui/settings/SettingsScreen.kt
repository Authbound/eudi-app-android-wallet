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

package eu.europa.ec.dashboardfeature.ui.settings

import android.content.Context
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.authenticationlogic.model.Profile
import eu.europa.ec.dashboardfeature.ui.component.NotificationIconButton
import eu.europa.ec.dashboardfeature.ui.settings.model.SettingsItemUi
import eu.europa.ec.dashboardfeature.ui.settings.model.SettingsMenuItemType
import eu.europa.ec.dashboardfeature.ui.settings.model.SettingsSection
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.brandNavyDeep
import eu.europa.ec.resourceslogic.theme.values.brandNavyMedium
import eu.europa.ec.resourceslogic.theme.values.glowAccent
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ListItemDataUi
import eu.europa.ec.uilogic.component.ListItemLeadingContentDataUi
import eu.europa.ec.uilogic.component.ListItemMainContentDataUi
import eu.europa.ec.uilogic.component.ListItemTrailingContentDataUi
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.MenuSectionHeader
import eu.europa.ec.uilogic.component.wrap.ProfileAvatar
import eu.europa.ec.uilogic.component.wrap.SwitchDataUi
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapSwitch
import eu.europa.ec.uilogic.extension.openIntentChooser
import eu.europa.ec.uilogic.extension.openUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onEach

@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel,
    notificationCount: Int = 0,
    onNotificationsClick: () -> Unit = {},
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // The bell sits over the navy hero at the top of the scroll content and
    // over the light surface once scrolled; animate between the two tints.
    val heroThresholdPx = with(LocalDensity.current) { 160.dp.toPx() }
    val bellTint by animateColorAsState(
        targetValue = if (scrollState.value < heroThresholdPx) {
            Color.White
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(durationMillis = 200),
        label = "settings_bell_tint"
    )

    LaunchedEffect(Unit) {
        viewModel.setEvent(Event.RefreshPidPortrait)
    }

    ContentScreen(
        navigatableAction = ScreenNavigateAction.NONE,
        isLoading = state.isDeleting || state.isBiometricToggleInProgress,
        onBack = { viewModel.setEvent(Event.Pop) },
        topBar = {
            TopBar(
                notificationCount = notificationCount,
                onNotificationsClick = onNotificationsClick,
                tint = bellTint,
            )
        }
    ) { paddingValues ->
        Content(
            state = state,
            effectFlow = viewModel.effect,
            onEventSend = { viewModel.setEvent(it) },
            onNavigationRequested = { navigationEffect ->
                handleNavigationEffect(navigationEffect, navController, context)
            },
            context = context,
            paddingValues = paddingValues,
            scrollState = scrollState,
        )
    }
}

private fun handleNavigationEffect(
    navigationEffect: Effect.Navigation,
    navController: NavController,
    context: Context,
) {
    when (navigationEffect) {
        is Effect.Navigation.Pop -> navController.popBackStack()
        is Effect.Navigation.OpenUrlExternally -> context.openUrl(uri = navigationEffect.url)
        is Effect.Navigation.SwitchScreen -> {
            navController.navigate(navigationEffect.screenRoute) {
                navigationEffect.popUpToScreenRoute?.let {
                    popUpTo(it) {
                        inclusive = navigationEffect.inclusive
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    notificationCount: Int,
    onNotificationsClick: () -> Unit,
    tint: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = SPACING_SMALL.dp,
                vertical = 4.dp
            )
    ) {
        NotificationIconButton(
            modifier = Modifier.align(Alignment.CenterEnd),
            badgeCount = notificationCount,
            onClick = onNotificationsClick,
            tint = tint,
        )
    }
}

@Composable
private fun Content(
    state: State,
    effectFlow: Flow<Effect>,
    onEventSend: (Event) -> Unit,
    onNavigationRequested: (Effect.Navigation) -> Unit,
    context: Context,
    paddingValues: PaddingValues,
    scrollState: ScrollState,
) {
    val groupedItems = remember(state.settingsItems) {
        state.settingsItems.groupBy { it.section }
    }
    val visibleSections = remember(groupedItems) {
        SettingsSection.entries.filter { !groupedItems[it].isNullOrEmpty() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(bottom = paddingValues.calculateBottomPadding())
        ) {
            EntranceAnimated(index = 0) {
                SettingsHeroHeader(
                    email = state.userEmail,
                    phone = state.authInfo.userInfo?.phone,
                    profile = state.authInfo.profile,
                    pidPortraitBase64 = state.pidPortraitBase64,
                )
            }

            VSpacer.Medium()

            visibleSections.forEachIndexed { index, section ->
                EntranceAnimated(index = index + 1) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SPACING_LARGE.dp)
                    ) {
                        MenuSectionHeader(title = sectionTitle(section))
                        SettingsSectionCard(
                            items = groupedItems.getValue(section),
                            context = context,
                            onEventSent = onEventSend,
                        )
                    }
                }
                VSpacer.Small()
            }

            EntranceAnimated(index = visibleSections.size + 1) {
                LogoutFooter(
                    appVersion = state.appVersion,
                    onEventSend = onEventSend,
                )
            }

            Spacer(modifier = Modifier.height(90.dp))
        }
    }

    LaunchedEffect(Unit) {
        effectFlow.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> onNavigationRequested(effect)
                is Effect.ShareLogFile -> {
                    context.openIntentChooser(
                        effect.intent,
                        effect.chooserTitle
                    )
                }
                is Effect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }.collect()
    }
}

/**
 * Navy gradient hero with decorative accents, the user's avatar
 * (PID portrait or initials monogram), and identity details.
 */
@Composable
private fun SettingsHeroHeader(
    email: String?,
    phone: String?,
    profile: Profile?,
    pidPortraitBase64: String?,
) {
    val resolvedName = remember(profile, email) {
        profile?.displayName?.takeIf { it.isNotBlank() }
            ?: email?.substringBefore("@")?.takeIf { it.isNotBlank() }
            ?: "User"
    }
    val accentColor = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.brandNavyDeep,
                        MaterialTheme.colorScheme.brandNavyMedium,
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                color = accentColor.copy(alpha = 0.08f),
                radius = 45.dp.toPx(),
                center = Offset(size.width - 30.dp.toPx(), 50.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx())
            )
            drawCircle(
                color = accentColor.copy(alpha = 0.12f),
                radius = 20.dp.toPx(),
                center = Offset(size.width - 70.dp.toPx(), 110.dp.toPx())
            )
            drawCircle(
                color = accentColor.copy(alpha = 0.10f),
                radius = 8.dp.toPx(),
                center = Offset(size.width - 24.dp.toPx(), 140.dp.toPx())
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    start = SPACING_LARGE.dp,
                    end = SPACING_LARGE.dp,
                    top = 48.dp,
                    bottom = 32.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(152.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.glowAccent,
                                    Color.Transparent,
                                )
                            ),
                            shape = CircleShape
                        )
                )
                ProfileAvatar(
                    displayName = resolvedName,
                    avatarUrl = null,
                    portraitBase64 = pidPortraitBase64,
                    size = 120,
                )
            }

            VSpacer.Medium()

            Text(
                text = resolvedName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            profile?.handle?.takeIf { it.isNotBlank() }?.let { handle ->
                Text(
                    text = "@$handle",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f),
                )
            }

            VSpacer.Small()

            email?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
            phone?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun sectionTitle(section: SettingsSection): String = stringResource(
    id = when (section) {
        SettingsSection.ACCOUNT -> R.string.settings_section_account
        SettingsSection.SECURITY -> R.string.settings_section_security
        SettingsSection.SUPPORT -> R.string.settings_section_support
    }
)

@Composable
private fun SettingsSectionCard(
    items: List<SettingsItemUi>,
    context: Context,
    onEventSent: (Event) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        ),
    ) {
        Column {
            items.forEachIndexed { index, settingsItemUi ->
                SettingsRow(
                    item = settingsItemUi,
                    context = context,
                    onEventSent = onEventSent,
                )
                if (index != items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 72.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    item: SettingsItemUi,
    context: Context,
    onEventSent: (Event) -> Unit,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.99f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "settings_row_scale"
    )

    val title = (item.data.mainContentData as? ListItemMainContentDataUi.Text)?.text.orEmpty()
    val leadingIcon = (item.data.leadingContentData as? ListItemLeadingContentDataUi.Icon)?.iconData
    val trailingContent = item.data.trailingContentData

    fun sendClickEvent() {
        if (item.type == SettingsMenuItemType.BIOMETRIC_AUTHENTICATION) {
            onEventSent(Event.ToggleBiometricAuthentication(context = context))
        } else {
            onEventSent(Event.ItemClicked(itemType = item.type))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    sendClickEvent()
                }
            )
            .padding(horizontal = SPACING_MEDIUM.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Muted solid navy keeps the white icon legible in both modes while
        // letting the row label, not the chip, lead the visual hierarchy.
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.brandNavyMedium)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            leadingIcon?.let {
                WrapIcon(
                    iconData = it,
                    customTint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(SPACING_MEDIUM.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        when (trailingContent) {
            is ListItemTrailingContentDataUi.Switch -> {
                WrapSwitch(
                    switchData = trailingContent.switchData,
                    onCheckedChange = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onEventSent(Event.ToggleBiometricAuthentication(context = context))
                    },
                )
            }

            else -> {
                WrapIcon(
                    iconData = AppIcons.KeyboardArrowRight,
                    customTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun LogoutFooter(
    appVersion: String,
    onEventSend: (Event) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        VSpacer.Large()

        WrapButton(
            modifier = Modifier
                .wrapContentWidth()
                .padding(SPACING_MEDIUM.dp),
            buttonConfig = ButtonConfig(
                type = ButtonType.SECONDARY,
                isWarning = true,
                onClick = { onEventSend(Event.Logout) },
            )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Logout",
                style = MaterialTheme.typography.labelLarge
            )
        }

        Text(
            modifier = Modifier.fillMaxWidth(),
            text = appVersion,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Staggered fade + slide entrance, 300ms with 50ms delay per index.
 */
@Composable
private fun EntranceAnimated(
    index: Int,
    content: @Composable () -> Unit,
) {
    val visibleState = remember {
        MutableTransitionState(initialState = false).apply { targetState = true }
    }
    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(
            animationSpec = tween(durationMillis = 300, delayMillis = index * 50)
        ) + slideInVertically(
            animationSpec = tween(durationMillis = 300, delayMillis = index * 50)
        ) { fullHeight -> fullHeight / 8 },
    ) {
        content()
    }
}

@ThemeModePreviews
@Composable
private fun SettingsScreenPreview() {
    PreviewTheme {
        val context = LocalContext.current

        val settingsItems = listOf(
            SettingsItemUi(
                type = SettingsMenuItemType.ACCOUNT_DETAILS,
                section = SettingsSection.ACCOUNT,
                data = ListItemDataUi(
                    itemId = "account_details",
                    mainContentData = ListItemMainContentDataUi.Text(text = "Account details"),
                    leadingContentData = ListItemLeadingContentDataUi.Icon(
                        iconData = AppIcons.UserIcon
                    ),
                    trailingContentData = ListItemTrailingContentDataUi.Icon(
                        iconData = AppIcons.KeyboardArrowRight
                    )
                )
            ),
            SettingsItemUi(
                type = SettingsMenuItemType.CHANGE_PIN,
                section = SettingsSection.SECURITY,
                data = ListItemDataUi(
                    itemId = "change_pin",
                    mainContentData = ListItemMainContentDataUi.Text(text = "Change PIN"),
                    leadingContentData = ListItemLeadingContentDataUi.Icon(
                        iconData = AppIcons.ChangePin
                    ),
                    trailingContentData = ListItemTrailingContentDataUi.Icon(
                        iconData = AppIcons.KeyboardArrowRight
                    )
                )
            ),
            SettingsItemUi(
                type = SettingsMenuItemType.BIOMETRIC_AUTHENTICATION,
                section = SettingsSection.SECURITY,
                data = ListItemDataUi(
                    itemId = "biometric_authentication",
                    mainContentData = ListItemMainContentDataUi.Text(text = "Biometric authentication"),
                    leadingContentData = ListItemLeadingContentDataUi.Icon(
                        iconData = AppIcons.TouchId
                    ),
                    trailingContentData = ListItemTrailingContentDataUi.Switch(
                        switchData = SwitchDataUi(isChecked = true)
                    )
                )
            ),
            SettingsItemUi(
                type = SettingsMenuItemType.RETRIEVE_LOGS,
                section = SettingsSection.SUPPORT,
                data = ListItemDataUi(
                    itemId = "retrieve_logs",
                    mainContentData = ListItemMainContentDataUi.Text(
                        text = stringResource(R.string.settings_screen_option_retrieve_logs)
                    ),
                    leadingContentData = ListItemLeadingContentDataUi.Icon(
                        iconData = AppIcons.OpenNew
                    ),
                    trailingContentData = ListItemTrailingContentDataUi.Icon(
                        iconData = AppIcons.KeyboardArrowRight
                    )
                )
            )
        )

        Content(
            state = State(
                settingsItems = settingsItems,
                appVersion = "1.0.0",
                changelogUrl = "",
                userEmail = "john.doe@example.com"
            ),
            effectFlow = emptyFlow(),
            onEventSend = {},
            onNavigationRequested = {},
            context = context,
            paddingValues = PaddingValues(SPACING_MEDIUM.dp),
            scrollState = rememberScrollState(),
        )
    }
}
