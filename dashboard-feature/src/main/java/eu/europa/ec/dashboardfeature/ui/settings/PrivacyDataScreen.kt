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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.authenticationlogic.model.LegalAcceptanceSnapshot
import eu.europa.ec.dashboardfeature.ui.settings.model.SettingsMenuItemType
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = SPACING_LARGE.dp,
                top = SPACING_LARGE.dp,
                end = SPACING_LARGE.dp,
                bottom = paddingValues.calculateBottomPadding() + SPACING_LARGE.dp
            ),
        verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_privacy_data_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.settings_privacy_data_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LegalLinkCard(
            title = stringResource(R.string.legal_acceptance_terms_title),
            description = stringResource(R.string.settings_terms_of_service_description),
            buttonText = stringResource(R.string.settings_open_link),
            onClick = {
                onEventSend(
                    Event.ItemClicked(SettingsMenuItemType.TERMS_OF_SERVICE)
                )
            }
        )
        LegalLinkCard(
            title = stringResource(R.string.legal_acceptance_privacy_title),
            description = stringResource(R.string.settings_privacy_policy_description),
            buttonText = stringResource(R.string.settings_open_link),
            onClick = {
                onEventSend(
                    Event.ItemClicked(SettingsMenuItemType.PRIVACY_POLICY)
                )
            }
        )
        LegalAcceptanceCard(snapshot = state.legalAcceptance)
        DeleteAccountCard(
            onOpenDeletionPage = {
                onEventSend(
                    Event.ItemClicked(SettingsMenuItemType.ACCOUNT_DELETION_INFO)
                )
            },
            onDeleteAccount = { onEventSend(Event.RequestDeleteAccount) }
        )
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
private fun LegalLinkCard(
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onClick) {
                Text(text = buttonText)
            }
        }
    }
}

@Composable
private fun LegalAcceptanceCard(
    snapshot: LegalAcceptanceSnapshot,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_accepted_documents_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            MetadataRow(
                label = stringResource(R.string.settings_required_terms_version),
                value = snapshot.requiredTermsVersion
            )
            MetadataRow(
                label = stringResource(R.string.settings_accepted_terms_version),
                value = snapshot.acceptedTermsVersion
            )
            MetadataRow(
                label = stringResource(R.string.settings_terms_accepted_at),
                value = snapshot.acceptedTermsAt
            )
            Spacer(modifier = Modifier.height(4.dp))
            MetadataRow(
                label = stringResource(R.string.settings_required_privacy_version),
                value = snapshot.requiredPrivacyVersion
            )
            MetadataRow(
                label = stringResource(R.string.settings_acknowledged_privacy_version),
                value = snapshot.acknowledgedPrivacyVersion
            )
            MetadataRow(
                label = stringResource(R.string.settings_privacy_acknowledged_at),
                value = snapshot.acknowledgedPrivacyAt
            )
        }
    }
}

@Composable
private fun MetadataRow(
    label: String,
    value: String?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.settings_not_available),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DeleteAccountCard(
    onOpenDeletionPage: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_account_deletion_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.settings_account_deletion_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenDeletionPage,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.settings_account_deletion_learn_more))
                }
            }
            Button(
                onClick = onDeleteAccount,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.settings_delete_account_button))
            }
        }
    }
}
