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

package eu.europa.ec.dashboardfeature.ui.verification

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.dashboardfeature.model.verification.VerificationRecipientSession
import eu.europa.ec.dashboardfeature.model.verification.VerificationRequestedAttribute
import eu.europa.ec.dashboardfeature.model.verification.humanizeVerificationStatus
import eu.europa.ec.dashboardfeature.model.verification.isVerificationHistoryStatus
import eu.europa.ec.dashboardfeature.ui.component.NotificationIconButton
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.InlineSnackbar
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.utils.LifecycleEffect
import eu.europa.ec.uilogic.component.utils.OneTimeLaunchedEffect
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_SMALL
import eu.europa.ec.uilogic.extension.applyTestTag
import eu.europa.ec.uilogic.extension.openUrlInBrowser
import eu.europa.ec.uilogic.test.DashboardTestTags
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VerificationRecipientScreen(
    navController: NavController,
    viewModel: VerificationRecipientViewModel,
    sessionId: String,
    accessToken: String,
    verificationUrl: String? = null,
    notificationCount: Int = 0,
    onNotificationsClick: () -> Unit = {},
) {
    val state: VerificationRecipientState by viewModel.viewState.collectAsStateWithLifecycle()
    val context: Context = LocalContext.current

    ContentScreen(
        isLoading = state.isLoading,
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack = { viewModel.setEvent(VerificationRecipientEvent.NavigateBack) },
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    modifier = Modifier.align(Alignment.Center),
                    text = stringResource(R.string.verification_recipient_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                NotificationIconButton(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    badgeCount = notificationCount,
                    onClick = onNotificationsClick,
                )
            }
        }
    ) { paddingValues ->
        VerificationRecipientContent(
            state = state,
            paddingValues = paddingValues,
            onConsentChanged = { viewModel.setEvent(VerificationRecipientEvent.ConsentChanged(it)) },
            onStartVerification = { viewModel.setEvent(VerificationRecipientEvent.StartVerification) },
            onOpenInBrowser = { viewModel.setEvent(VerificationRecipientEvent.OpenInBrowser) },
            onRefresh = { viewModel.setEvent(VerificationRecipientEvent.Refresh) },
            onDismissError = { viewModel.setEvent(VerificationRecipientEvent.DismissError) },
        )
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is VerificationRecipientEffect.Navigation.Back -> navController.popBackStack()
                is VerificationRecipientEffect.Navigation.SwitchScreen -> {
                    navController.navigate(effect.screenRoute) {
                        popUpTo(effect.popUpToScreenRoute) {
                            inclusive = effect.inclusive
                        }
                    }
                }

                is VerificationRecipientEffect.OpenUrlInBrowser -> {
                    context.openUrlInBrowser(Uri.parse(effect.url))
                }
                is VerificationRecipientEffect.ShowToast -> {
                    android.widget.Toast.makeText(context, effect.message, android.widget.Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    OneTimeLaunchedEffect {
        viewModel.setEvent(
            VerificationRecipientEvent.Init(
                sessionId = sessionId,
                accessToken = accessToken,
                verificationUrl = verificationUrl?.let(Uri::decode)
            )
        )
    }

    LifecycleEffect(
        lifecycleOwner = LocalLifecycleOwner.current,
        lifecycleEvent = Lifecycle.Event.ON_RESUME
    ) {
        viewModel.setEvent(VerificationRecipientEvent.Refresh)
    }
}

@Composable
private fun VerificationRecipientContent(
    state: VerificationRecipientState,
    paddingValues: PaddingValues,
    onConsentChanged: (Boolean) -> Unit,
    onStartVerification: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onRefresh: () -> Unit,
    onDismissError: () -> Unit,
) {
    val session: VerificationRecipientSession? = state.session
    val canStartVerification: Boolean = session != null && !isVerificationHistoryStatus(session.status)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .applyTestTag(DashboardTestTags.VerificationRecipient.ROOT)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (session == null) {
                item {
                    Text(
                        text = state.error ?: stringResource(R.string.verification_recipient_unavailable),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                item {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onOpenInBrowser,
                        enabled = true
                    ) {
                        Text(stringResource(R.string.verification_recipient_open_in_browser))
                    }
                }
                return@LazyColumn
            }

            item {
                SessionOverviewCard(session = session)
            }

            item {
                PurposeCard(session = session)
            }

            item {
                RequestedAttributesCard(attributes = session.requestedAttributes)
            }

            item {
                ConsentCard(
                    checked = state.hasConsent,
                    onCheckedChange = onConsentChanged
                )
            }

            item {
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .applyTestTag(DashboardTestTags.VerificationRecipient.PRIMARY_BUTTON),
                    onClick = onStartVerification,
                    enabled = canStartVerification && state.hasConsent
                ) {
                    Text(
                        text = if (session.status.equals("active", ignoreCase = true)) {
                            stringResource(R.string.verification_recipient_continue)
                        } else {
                            stringResource(R.string.verification_recipient_start)
                        }
                    )
                }
            }

            item {
                OutlinedButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .applyTestTag(DashboardTestTags.VerificationRecipient.BROWSER_BUTTON),
                    onClick = onOpenInBrowser
                ) {
                    Text(stringResource(R.string.verification_recipient_open_in_browser))
                }
            }
        }

        state.error?.let { error ->
            InlineSnackbar(
                message = error,
                onRetry = onRefresh,
                onDismiss = onDismissError,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = SPACING_EXTRA_SMALL.dp)
            )
        }
    }
}

@Composable
private fun SessionOverviewCard(session: VerificationRecipientSession) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = requesterDisplayName(session),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.verification_recipient_requester_caption),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            MetadataRow(
                label = stringResource(R.string.verification_recipient_status),
                value = humanizeVerificationStatus(session.status)
            )
            MetadataRow(
                label = stringResource(R.string.verification_recipient_created),
                value = formatTimestamp(session.createdAt)
            )
            session.expiresAt?.let {
                MetadataRow(
                    label = stringResource(R.string.verification_recipient_expires),
                    value = formatTimestamp(it)
                )
            }
        }
    }
}

@Composable
private fun PurposeCard(session: VerificationRecipientSession) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.verification_recipient_purpose_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = session.purpose,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RequestedAttributesCard(attributes: List<VerificationRequestedAttribute>) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.verification_recipient_attributes_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            attributes.forEach { attribute ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = attribute.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    if (attribute.description.isNotBlank()) {
                        Text(
                            text = attribute.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsentCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(
                modifier = Modifier.applyTestTag(DashboardTestTags.VerificationRecipient.CONSENT_CHECKBOX),
                checked = checked,
                onCheckedChange = onCheckedChange
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.verification_recipient_consent_title),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.verification_recipient_consent_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MetadataRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun requesterDisplayName(session: VerificationRecipientSession): String {
    val requester = session.requester
    return requester?.displayName
        ?: requester?.handle?.takeIf { it.isNotBlank() }?.let { "@$it" }
        ?: session.id
}

private fun formatTimestamp(value: Long): String {
    return SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(value))
}
