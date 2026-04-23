/*
 * Copyright (c) 2026 European Commission
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
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.dashboardfeature.ui.settings.model.SettingsMenuItemType
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.extension.openIntentChooser
import eu.europa.ec.uilogic.extension.openUrl
import kotlinx.coroutines.flow.collectLatest

@Composable
fun PrivacyDataScreen(
    navController: NavController,
    viewModel: SettingsViewModel,
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.effect.collectLatest { effect: Effect ->
            when (effect) {
                is Effect.Navigation -> handlePrivacyDataNavigationEffect(
                    navigationEffect = effect,
                    navController = navController,
                    context = context
                )

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
        }
    }

    ContentScreen(
        navigatableAction = ScreenNavigateAction.BACKABLE,
        isLoading = state.isDeleting,
        onBack = { viewModel.setEvent(Event.Pop) },
    ) { paddingValues: PaddingValues ->
        PrivacyDataContent(
            state = state,
            paddingValues = paddingValues,
            onEventSend = viewModel::setEvent
        )
    }
}

private fun handlePrivacyDataNavigationEffect(
    navigationEffect: Effect.Navigation,
    navController: NavController,
    context: Context,
) {
    when (navigationEffect) {
        is Effect.Navigation.Pop -> navController.popBackStack()
        is Effect.Navigation.OpenUrlExternally -> context.openUrl(uri = navigationEffect.url)
        is Effect.Navigation.SwitchScreen -> {
            navController.navigate(navigationEffect.screenRoute) {
                navigationEffect.popUpToScreenRoute?.let { route: String ->
                    popUpTo(route) {
                        inclusive = navigationEffect.inclusive
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyDataContent(
    state: State,
    paddingValues: PaddingValues,
    onEventSend: (Event) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = paddingValues.calculateTopPadding())
            .padding(bottom = paddingValues.calculateBottomPadding() + SPACING_LARGE.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        HeroHeader()

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 })
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SPACING_LARGE.dp)
                    .padding(top = SPACING_LARGE.dp),
                verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_privacy_data_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                LegalDocumentCard(
                    icon = Icons.Default.Gavel,
                    iconTint = Color(0xFF1E3A5F),
                    iconBackground = Color(0xFFDCEAFF),
                    title = stringResource(R.string.legal_acceptance_terms_title),
                    description = stringResource(R.string.settings_terms_of_service_description),
                    buttonText = stringResource(R.string.settings_open_link),
                    onClick = {
                        onEventSend(Event.ItemClicked(SettingsMenuItemType.TERMS_OF_SERVICE))
                    }
                )

                LegalDocumentCard(
                    icon = Icons.Default.PrivacyTip,
                    iconTint = Color(0xFF047857),
                    iconBackground = Color(0xFFD1FAE5),
                    title = stringResource(R.string.legal_acceptance_privacy_title),
                    description = stringResource(R.string.settings_privacy_policy_description),
                    buttonText = stringResource(R.string.settings_open_link),
                    onClick = {
                        onEventSend(Event.ItemClicked(SettingsMenuItemType.PRIVACY_POLICY))
                    }
                )

                DeleteAccountCard(
                    onOpenDeletionPage = {
                        onEventSend(Event.ItemClicked(SettingsMenuItemType.ACCOUNT_DELETION_INFO))
                    },
                    onDeleteAccount = { onEventSend(Event.RequestDeleteAccount) }
                )

                Spacer(modifier = Modifier.height(SPACING_LARGE.dp))
            }
        }
    }

    if (state.showDeleteAccountConfirmation) {
        AlertDialog(
            onDismissRequest = { onEventSend(Event.DismissDeleteConfirmation) },
            title = {
                Text(text = stringResource(R.string.settings_delete_account_title))
            },
            text = {
                Text(text = stringResource(R.string.settings_delete_account_message))
            },
            confirmButton = {
                TextButton(onClick = { onEventSend(Event.ConfirmDeleteAccount) }) {
                    Text(
                        text = stringResource(R.string.settings_delete_account_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { onEventSend(Event.DismissDeleteConfirmation) }) {
                    Text(text = stringResource(R.string.settings_delete_account_cancel))
                }
            }
        )
    }
}

@Composable
private fun HeroHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF0A1A36), Color(0xFF1E3A5F)),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
    ) {
        // Decorative circles
        Box(
            modifier = Modifier
                .size(120.dp)
                .offset(x = (-30).dp, y = (-30).dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.04f))
        )
        Box(
            modifier = Modifier
                .size(80.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 20.dp, y = 20.dp)
                .clip(CircleShape)
                .background(Color(0xFF3B82F6).copy(alpha = 0.15f))
        )

        Column(
            modifier = Modifier
                .padding(horizontal = SPACING_LARGE.dp, vertical = SPACING_LARGE.dp)
                .align(Alignment.BottomStart)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.settings_privacy_data_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun LegalDocumentCard(
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color,
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(SPACING_LARGE.dp),
            verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = buttonText)
            }
        }
    }
}

@Composable
private fun DeleteAccountCard(
    onOpenDeletionPage: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(SPACING_LARGE.dp),
            verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.settings_account_deletion_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(
                text = stringResource(R.string.settings_account_deletion_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenDeletionPage,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.settings_account_deletion_learn_more))
                }
                Button(
                    onClick = onDeleteAccount,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(text = stringResource(R.string.settings_delete_account_button))
                }
            }
        }
    }
}
