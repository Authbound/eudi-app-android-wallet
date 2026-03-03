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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.dashboardfeature.ui.settings.model.SettingsItemUi
import eu.europa.ec.dashboardfeature.ui.settings.model.SettingsMenuItemType
import eu.europa.ec.dashboardfeature.ui.component.NotificationIconButton
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ImageOrPlaceholder
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
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapListItem
import eu.europa.ec.uilogic.component.wrap.WrapCard
import eu.europa.ec.uilogic.component.wrap.WrapImage
import eu.europa.ec.uilogic.component.wrap.WrapAsyncImage
import eu.europa.ec.uilogic.extension.openIntentChooser
import eu.europa.ec.uilogic.extension.openUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onEach
import eu.europa.ec.authenticationlogic.model.Profile

@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel,
    notificationCount: Int = 0,
    onNotificationsClick: () -> Unit = {},
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
                onNotificationsClick = onNotificationsClick
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
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = paddingValues.calculateBottomPadding())
        ) {
            // Scrollable top band background - lighter blue and taller
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(232.dp)
                    .background(Color(0xFFB3D4FF))
            )

            ProfileHeader(
                email = state.userEmail,
                phone = state.authInfo.userInfo?.phone,
                profile = state.authInfo.profile,
                pidPortraitBase64 = state.pidPortraitBase64
            )

            // Section separator (top, full width to minimize visual gap)
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .align(Alignment.CenterHorizontally),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
            SettingsItemsSection(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SPACING_LARGE.dp),
                context = context,
                items = state.settingsItems,
                onEventSent = onEventSend,
            )
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .align(Alignment.CenterHorizontally),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            VSpacer.Large()

            WrapButton(
                modifier = Modifier
                    .wrapContentWidth()
                    .padding(SPACING_MEDIUM.dp)
                    .align(Alignment.CenterHorizontally),
                buttonConfig = ButtonConfig(
                    type = ButtonType.PRIMARY,
                    onClick = { onEventSend(Event.Logout) },
                    buttonColors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = SPACING_MEDIUM.dp),
                text = state.appVersion,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

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

    // Delete wallet confirmation dialog
    if (state.showDeleteWalletConfirmation) {
        AlertDialog(
            onDismissRequest = { onEventSend(Event.DismissDeleteConfirmation) },
            title = { Text(stringResource(R.string.settings_delete_wallet_title)) },
            text = {
                Text(
                    stringResource(R.string.settings_delete_wallet_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onEventSend(Event.ConfirmDeleteWallet) }
                ) {
                    Text(
                        stringResource(R.string.settings_delete_wallet_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onEventSend(Event.DismissDeleteConfirmation) }
                ) {
                    Text(stringResource(R.string.settings_delete_wallet_cancel))
                }
            }
        )
    }
}

@Composable
private fun ProfileHeader(email: String?, phone: String?, profile: Profile?, pidPortraitBase64: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = (-84).dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Outer white ring to appear outside the avatar circle (with shadow)
        Box(
            modifier = Modifier
                .size(148.dp)
                .shadow(elevation = 12.dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .size(138.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                // Using local avatar resource
                if (!pidPortraitBase64.isNullOrBlank()) {
                    ImageOrPlaceholder(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        base64Image = pidPortraitBase64,
                        contentScale = ContentScale.Crop,
                        fallbackIcon = AppIcons.User,
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.authbound_avatar_placeholder_mascot),
                        contentDescription = "User Avatar",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
        VSpacer.Medium()
        Text(
            text = profile?.displayName?.takeIf { it.isNotBlank() } ?: "User Profile",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = profile?.handle?.let { "@$it" } ?: email ?: "Email not available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Small top padding before contact info rows
        VSpacer.Small()
        // Contact info rows below name
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SPACING_LARGE.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Phone",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = phone?.takeIf { it.isNotBlank() } ?: "Phone not set",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        VSpacer.Small()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SPACING_LARGE.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Mail",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = email ?: "N/A",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SettingsItemsSection(
    modifier: Modifier = Modifier,
    context: Context,
    items: List<SettingsItemUi>,
    onEventSent: (Event) -> Unit,
) {
    SettingsItems(
        context = context,
        items = items,
        onEventSent = onEventSent
    )
}

@Composable
private fun SettingsItems(
    context: Context,
    items: List<SettingsItemUi>,
    onEventSent: (Event) -> Unit,
) {
    Column {
        items.forEachIndexed { index, settingsItemUi ->
            // Enlarge leading icons and main text
            val adjustedData: ListItemDataUi = when (val lead = settingsItemUi.data.leadingContentData) {
                is ListItemLeadingContentDataUi.Icon ->
                    settingsItemUi.data.copy(leadingContentData = lead.copy(size = 36))
                else -> settingsItemUi.data
            }
            WrapListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = SPACING_MEDIUM.dp, end = SPACING_SMALL.dp),
                item = adjustedData,
                onItemClick = {
                    if (settingsItemUi.type == SettingsMenuItemType.BIOMETRIC_AUTHENTICATION) {
                        onEventSent(
                            Event.ToggleBiometricAuthentication(context = context)
                        )
                    } else {
                        onEventSent(
                            Event.ItemClicked(itemType = settingsItemUi.type)
                        )
                    }
                },
                throttleClicks = false,
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                mainContentVerticalPadding = SPACING_LARGE.dp,
                mainContentTextStyle = MaterialTheme.typography.titleMedium,
            )

            if (index != items.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .align(Alignment.CenterHorizontally),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@ThemeModePreviews
@Composable
private fun SettingsScreenPreview() {
    PreviewTheme {
        val context = LocalContext.current

        val settingsItems = listOf(

            SettingsItemUi(
                type = SettingsMenuItemType.RETRIEVE_LOGS,
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
            paddingValues = PaddingValues(SPACING_MEDIUM.dp)
        )
    }
}