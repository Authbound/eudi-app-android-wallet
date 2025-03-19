package eu.europa.ec.dashboardfeature.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ListItemData
import eu.europa.ec.uilogic.component.ListItemLeadingContentData
import eu.europa.ec.uilogic.component.ListItemMainContentData
import eu.europa.ec.uilogic.component.ListItemTrailingContentData
import eu.europa.ec.uilogic.component.SectionTitle
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.wrap.*
import eu.europa.ec.uilogic.extension.finish

@Composable
fun SettingsScreen(
    navHostController: NavController,
    viewModel: SettingsViewModel
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    ContentScreen(
        isLoading = state.isLoading,
        navigatableAction = ScreenNavigateAction.NONE,
        onBack = { context.finish() },
        topBar = {
            TopBar()
        }
    ) { paddingValues ->
        Content(
            state = state,
            effectFlow = viewModel.effect,
            onEventSent = { event -> viewModel.setEvent(event) },
            onNavigationRequested = { navigationEffect ->
                handleNavigationEffect(navigationEffect, navHostController, context)
            },
            paddingValues = paddingValues
        )
    }

    if (state.isBottomSheetOpen && state.sheetContent != null) {
        @OptIn(ExperimentalMaterial3Api::class)
        WrapModalBottomSheet(
            sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true
            ),
            onDismissRequest = {
                viewModel.setEvent(Event.BottomSheet.UpdateBottomSheetState(isOpen = false))
            }
        ) {
            when (state.sheetContent) {
                is SettingsBottomSheetContent.DeleteConfirmation -> {
                    DialogBottomSheet(
                        textData = BottomSheetTextData(
                            title = stringResource(R.string.settings_delete_data_confirmation_title),
                            message = stringResource(R.string.settings_delete_data_confirmation_message),
                            positiveButtonText = stringResource(R.string.settings_delete_data_confirm),
                            negativeButtonText = stringResource(R.string.settings_delete_data_cancel)
                        ),
                        onPositiveClick = {
                            viewModel.setEvent(Event.BottomSheet.ConfirmDeleteData)
                        },
                        onNegativeClick = {
                            viewModel.setEvent(Event.BottomSheet.Close)
                        }
                    )
                }
                null -> Unit
            }
        }
    }
}

@Composable
private fun TopBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 16.dp,
                vertical = 12.dp
            )
    ) {
        Text(
            modifier = Modifier.align(Alignment.Center),
            text = stringResource(R.string.settings_screen_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun Content(
    state: State,
    effectFlow: kotlinx.coroutines.flow.Flow<Effect>,
    onEventSent: (Event) -> Unit,
    onNavigationRequested: (Effect.Navigation) -> Unit,
    paddingValues: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        ProfileSection(
            profile = state.profile,
            onEditProfile = { onEventSent(Event.EditProfilePressed) }
        )

        SecuritySection(
            securitySettings = state.securitySettings,
            onBiometricToggled = { onEventSent(Event.BiometricToggled(it)) },
            onChangePinPressed = { onEventSent(Event.ChangePinPressed) },
            onChangePasswordPressed = { onEventSent(Event.ChangePasswordPressed) }
        )

        PrivacySection(
            onDataSharingPressed = { onEventSent(Event.DataSharingPressed) },
            onActivityLogPressed = { onEventSent(Event.ActivityLogPressed) },
            onDeleteDataPressed = { onEventSent(Event.DeleteDataPressed) }
        )

        NotificationsSection(
            onNotificationsPressed = { onEventSent(Event.NotificationsPressed) }
        )
    }

    LaunchedEffect(Unit) {
        effectFlow.collect { effect ->
            when (effect) {
                is Effect.Navigation -> onNavigationRequested(effect)
                is Effect.ShowBiometricPrompt -> {
                    // TODO: Show biometric prompt
                }
                is Effect.ShowBottomSheet -> Unit
                is Effect.CloseBottomSheet -> Unit
            }
        }
    }
}

@Composable
private fun ProfileSection(
    profile: ProfileData,
    onEditProfile: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .align(Alignment.CenterHorizontally)
        ) {
            Text(
                text = profile.avatarText,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = profile.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = profile.email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = profile.phone,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Button(
            onClick = onEditProfile,
            modifier = Modifier.fillMaxWidth()
        ) {
            WrapIcon(iconData = AppIcons.Edit)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.settings_edit_profile))
        }
    }
}

@Composable
private fun SecuritySection(
    securitySettings: SecuritySettings,
    onBiometricToggled: (Boolean) -> Unit,
    onChangePinPressed: () -> Unit,
    onChangePasswordPressed: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionTitle(modifier= Modifier, text = stringResource(R.string.settings_security))

        // Biometric Authentication
        WrapListItem(
            item = ListItemData(
                itemId = "biometric",
                mainContentData = ListItemMainContentData.Text(
                    text = stringResource(R.string.settings_biometric_authentication)
                ),
                supportingText = stringResource(R.string.settings_biometric_description),
                leadingContentData = ListItemLeadingContentData.Icon(
                    iconData = AppIcons.WalletSecured
                ),
                trailingContentData = ListItemTrailingContentData.Checkbox(
                    checkboxData = CheckboxData(
                        isChecked = securitySettings.isBiometricEnabled,
                        onCheckedChange = onBiometricToggled
                    )
                )
            ),
            onItemClick = null
        )

        // Change PIN
        WrapListItem(
            item = ListItemData(
                itemId = "change_pin",
                mainContentData = ListItemMainContentData.Text(
                    text = stringResource(R.string.settings_change_pin),
//                    supportingText = stringResource(R.string.settings_change_pin_description)
                ),
                leadingContentData = ListItemLeadingContentData.Icon(
                    iconData = AppIcons.WalletSecured
                ),
                trailingContentData = ListItemTrailingContentData.Icon(
                    iconData = AppIcons.KeyboardArrowRight
                )
            ),
            onItemClick = { onChangePinPressed() }
        )

        // Change Password
        WrapListItem(
            item = ListItemData(
                itemId = "change_password",
                mainContentData = ListItemMainContentData.Text(
                    text = stringResource(R.string.settings_change_password),
//                    supportingText = stringResource(R.string.settings_change_password_description)
                ),
                leadingContentData = ListItemLeadingContentData.Icon(
                    iconData = AppIcons.Certified
                ),
                trailingContentData = ListItemTrailingContentData.Icon(
                    iconData = AppIcons.KeyboardArrowRight
                )
            ),
            onItemClick = { onChangePasswordPressed() }
        )
    }
}

@Composable
private fun PrivacySection(
    onDataSharingPressed: () -> Unit,
    onActivityLogPressed: () -> Unit,
    onDeleteDataPressed: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionTitle(modifier= Modifier,text = stringResource(R.string.settings_privacy))

        // Data Sharing
        WrapListItem(
            item = ListItemData(
                itemId = "data_sharing",
                mainContentData = ListItemMainContentData.Text(
                    text = stringResource(R.string.settings_data_sharing),
//                    supportingText = stringResource(R.string.settings_data_sharing_description)
                ),
                leadingContentData = ListItemLeadingContentData.Icon(
                    iconData = AppIcons.Add
                ),
                trailingContentData = ListItemTrailingContentData.Icon(
                    iconData = AppIcons.KeyboardArrowRight
                )
            ),
            onItemClick = { onDataSharingPressed() }
        )

        // Activity Log
        WrapListItem(
            item = ListItemData(
                itemId = "activity_log",
                mainContentData = ListItemMainContentData.Text(
                    text = stringResource(R.string.settings_activity_log),
//                    supportingText = stringResource(R.string.settings_activity_log_description)
                ),
                leadingContentData = ListItemLeadingContentData.Icon(
                    iconData = AppIcons.Certified
                ),
                trailingContentData = ListItemTrailingContentData.Icon(
                    iconData = AppIcons.KeyboardArrowRight
                )
            ),
            onItemClick = { onActivityLogPressed() }
        )

        // Delete My Data
        WrapListItem(
            item = ListItemData(
                itemId = "delete_data",
                mainContentData = ListItemMainContentData.Text(
                    text = stringResource(R.string.settings_delete_data),
//                    supportingText = stringResource(R.string.settings_delete_data_description)
                ),
                leadingContentData = ListItemLeadingContentData.Icon(
                    iconData = AppIcons.Delete,
                    tint = MaterialTheme.colorScheme.error
                ),
                trailingContentData = ListItemTrailingContentData.Icon(
                    iconData = AppIcons.KeyboardArrowRight
                )
            ),
            onItemClick = { onDeleteDataPressed() }
        )
    }
}

@Composable
private fun NotificationsSection(
    onNotificationsPressed: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionTitle(modifier= Modifier,text = stringResource(R.string.settings_notifications))

        WrapListItem(
            item = ListItemData(
                itemId = "notifications",
                mainContentData = ListItemMainContentData.Text(
                    text = stringResource(R.string.settings_notifications),
//                    supportingText = stringResource(R.string.settings_notifications_description)
                ),
                leadingContentData = ListItemLeadingContentData.Icon(
                    iconData = AppIcons.Notifications
                ),
                trailingContentData = ListItemTrailingContentData.Icon(
                    iconData = AppIcons.KeyboardArrowRight
                )
            ),
            onItemClick = { onNotificationsPressed() }
        )
    }
}

private fun handleNavigationEffect(
    navigationEffect: Effect.Navigation,
    navController: NavController,
    context: Context
) {
    when (navigationEffect) {
        is Effect.Navigation.SwitchScreen -> {
            navController.navigate(navigationEffect.screenRoute)
        }
    }
}