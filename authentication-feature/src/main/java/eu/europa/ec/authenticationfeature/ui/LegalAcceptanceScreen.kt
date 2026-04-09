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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import eu.europa.ec.uilogic.extension.applyTestTag
import eu.europa.ec.uilogic.extension.openUrl
import eu.europa.ec.uilogic.test.AuthTestTags
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LegalAcceptanceScreen(
    viewModel: LegalAcceptanceViewModel,
    onNavigateToStartup: () -> Unit,
    onNavigateToLogin: () -> Unit,
) {
    val state: LegalAcceptanceState by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is LegalAcceptanceEffect.Navigation.NavigateToLogin -> onNavigateToLogin()
                is LegalAcceptanceEffect.Navigation.NavigateToStartup -> onNavigateToStartup()
                is LegalAcceptanceEffect.Navigation.OpenUrlExternally -> context.openUrl(effect.url)
                is LegalAcceptanceEffect.ShowError -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    ContentScreen(
        isLoading = state.isLoading || state.isSubmitting,
        imePaddingConfig = ImePaddingConfig.ONLY_CONTENT,
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack = { viewModel.setEvent(LegalAcceptanceEvent.SignOut) },
    ) { paddingValues: PaddingValues ->
        LegalAcceptanceContent(
            state = state,
            paddingValues = paddingValues,
            onEvent = viewModel::setEvent
        )
    }
}

@Composable
private fun LegalAcceptanceContent(
    state: LegalAcceptanceState,
    paddingValues: PaddingValues,
    onEvent: (LegalAcceptanceEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .applyTestTag(AuthTestTags.LegalAcceptance.ROOT)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.legal_acceptance_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.legal_acceptance_description),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        LegalDocumentCard(
            title = stringResource(R.string.legal_acceptance_terms_title),
            version = state.snapshot.requiredTermsVersion.ifBlank {
                stringResource(R.string.legal_acceptance_version_unavailable)
            },
            updatedAt = stringResource(R.string.legal_terms_alpha_updated_at),
            buttonText = stringResource(R.string.legal_acceptance_open_terms),
            onOpen = { onEvent(LegalAcceptanceEvent.OpenTerms) }
        )
        Spacer(modifier = Modifier.height(12.dp))
        LegalDocumentCard(
            title = stringResource(R.string.legal_acceptance_privacy_title),
            version = state.snapshot.requiredPrivacyVersion.ifBlank {
                stringResource(R.string.legal_acceptance_version_unavailable)
            },
            updatedAt = stringResource(R.string.legal_privacy_policy_updated_at),
            buttonText = stringResource(R.string.legal_acceptance_open_privacy),
            onOpen = { onEvent(LegalAcceptanceEvent.OpenPrivacy) }
        )
        Spacer(modifier = Modifier.height(20.dp))
        LegalRiskCard()
        Spacer(modifier = Modifier.height(20.dp))
        ConsentRow(
            checked = state.hasAcceptedTerms,
            text = stringResource(R.string.legal_acceptance_terms_checkbox),
            modifier = Modifier.applyTestTag(AuthTestTags.LegalAcceptance.TERMS_CHECKBOX),
            onCheckedChange = { onEvent(LegalAcceptanceEvent.TermsToggled(it)) }
        )
        Spacer(modifier = Modifier.height(8.dp))
        ConsentRow(
            checked = state.hasAcknowledgedPrivacy,
            text = stringResource(R.string.legal_acceptance_privacy_checkbox),
            modifier = Modifier.applyTestTag(AuthTestTags.LegalAcceptance.PRIVACY_CHECKBOX),
            onCheckedChange = { onEvent(LegalAcceptanceEvent.PrivacyToggled(it)) }
        )
        if (!state.error.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = state.error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { onEvent(LegalAcceptanceEvent.Retry) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.legal_acceptance_retry))
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { onEvent(LegalAcceptanceEvent.Continue) },
            modifier = Modifier
                .fillMaxWidth()
                .applyTestTag(AuthTestTags.LegalAcceptance.CONTINUE_BUTTON),
            enabled = state.canContinue
        ) {
            Text(text = stringResource(R.string.legal_acceptance_continue))
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(
            onClick = { onEvent(LegalAcceptanceEvent.SignOut) },
            modifier = Modifier.applyTestTag(AuthTestTags.LegalAcceptance.SIGN_OUT_BUTTON)
        ) {
            Text(
                text = stringResource(R.string.legal_acceptance_sign_out),
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun LegalDocumentCard(
    title: String,
    version: String,
    updatedAt: String,
    buttonText: String,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.legal_acceptance_version_label, version),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.legal_acceptance_updated_label, updatedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onOpen) {
                Text(text = buttonText)
            }
        }
    }
}

@Composable
private fun LegalRiskCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.legal_acceptance_alpha_notice_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.legal_acceptance_alpha_notice_body),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ConsentRow(
    checked: Boolean,
    text: String,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}
