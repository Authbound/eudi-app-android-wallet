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
package eu.europa.ec.authenticationfeature.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ImePaddingConfig
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AccountDeletionScheduledScreen(
    viewModel: AccountDeletionScheduledViewModel,
    onNavigateToStartup: () -> Unit,
    onNavigateToLogin: () -> Unit,
) {
    val state: AccountDeletionScheduledState by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val errorMessage: String? = state.error
    val titleRes: Int = if (state.isProcessing) {
        R.string.account_deletion_processing_title
    } else {
        R.string.account_deletion_scheduled_title
    }
    val descriptionRes: Int = if (state.isProcessing) {
        R.string.account_deletion_processing_description
    } else {
        R.string.account_deletion_scheduled_description
    }

    LaunchedEffect(viewModel) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is AccountDeletionScheduledEffect.Navigation.NavigateToLogin -> onNavigateToLogin()
                is AccountDeletionScheduledEffect.Navigation.NavigateToStartup -> onNavigateToStartup()
                is AccountDeletionScheduledEffect.ShowError -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    ContentScreen(
        isLoading = state.isLoading || state.isSubmitting,
        imePaddingConfig = ImePaddingConfig.ONLY_CONTENT,
        navigatableAction = ScreenNavigateAction.NONE,
    ) { paddingValues: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.account_deletion_scheduled_for_label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = state.scheduledFor
                            ?: stringResource(R.string.settings_not_available),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                OutlinedButton(
                    onClick = { viewModel.setEvent(AccountDeletionScheduledEvent.Retry) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.account_deletion_retry))
                }
            }
            if (state.canCancel) {
                Button(
                    onClick = { viewModel.setEvent(AccountDeletionScheduledEvent.CancelDeletion) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.account_deletion_cancel_button))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { viewModel.setEvent(AccountDeletionScheduledEvent.SignOut) }) {
                Text(
                    text = stringResource(R.string.account_deletion_sign_out),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
