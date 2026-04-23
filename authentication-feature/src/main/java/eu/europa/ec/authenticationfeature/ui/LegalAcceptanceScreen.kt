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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.legal_acceptance_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start
            )
            Text(
                text = stringResource(R.string.legal_acceptance_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start
            )
        }
        
        LegalDocumentRow(
            title = stringResource(R.string.legal_acceptance_terms_title),
            version = state.snapshot.requiredTermsVersion.ifBlank {
                stringResource(R.string.legal_acceptance_version_unavailable)
            },
            updatedAt = stringResource(R.string.legal_terms_alpha_updated_at),
            onClick = { onEvent(LegalAcceptanceEvent.OpenTerms) }
        )
        LegalDocumentRow(
            title = stringResource(R.string.legal_acceptance_privacy_title),
            version = state.snapshot.requiredPrivacyVersion.ifBlank {
                stringResource(R.string.legal_acceptance_version_unavailable)
            },
            updatedAt = stringResource(R.string.legal_privacy_policy_updated_at),
            onClick = { onEvent(LegalAcceptanceEvent.OpenPrivacy) }
        )
        ConsentSection(
            state = state,
            onEvent = onEvent
        )

        if (!state.error.isNullOrBlank()) {
            Text(
                text = state.error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Start
            )
            OutlinedButton(
                onClick = { onEvent(LegalAcceptanceEvent.Retry) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.legal_acceptance_retry))
            }
        }
        Button(
            onClick = { onEvent(LegalAcceptanceEvent.Continue) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .applyTestTag(AuthTestTags.LegalAcceptance.CONTINUE_BUTTON),
            enabled = state.canContinue
        ) {
            Text(text = stringResource(R.string.legal_acceptance_continue))
        }
        TextButton(
            onClick = { onEvent(LegalAcceptanceEvent.SignOut) },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .applyTestTag(AuthTestTags.LegalAcceptance.SIGN_OUT_BUTTON)
        ) {
            Text(
                text = stringResource(R.string.legal_acceptance_sign_out),
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun LegalDocumentRow(
    title: String,
    version: String,
    updatedAt: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.legal_acceptance_updated_label, updatedAt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.legal_acceptance_version_label, version),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConsentSection(
    state: LegalAcceptanceState,
    onEvent: (LegalAcceptanceEvent) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.legal_acceptance_confirm_section_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.legal_acceptance_confirm_section_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ConsentRow(
            checked = state.hasAcceptedTerms,
            text = stringResource(R.string.legal_acceptance_terms_checkbox),
            modifier = Modifier.applyTestTag(AuthTestTags.LegalAcceptance.TERMS_CHECKBOX),
            onCheckedChange = { onEvent(LegalAcceptanceEvent.TermsToggled(it)) }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ConsentRow(
            checked = state.hasAcknowledgedPrivacy,
            text = stringResource(R.string.legal_acceptance_privacy_checkbox),
            modifier = Modifier.applyTestTag(AuthTestTags.LegalAcceptance.PRIVACY_CHECKBOX),
            onCheckedChange = { onEvent(LegalAcceptanceEvent.PrivacyToggled(it)) }
        )
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
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.align(Alignment.Top)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}
