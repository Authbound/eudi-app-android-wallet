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

package eu.europa.ec.dashboardfeature.ui.verification

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import eu.europa.ec.dashboardfeature.model.verification.VerificationRecipient
import eu.europa.ec.dashboardfeature.model.verification.VerificationRecipientContactType
import eu.europa.ec.dashboardfeature.model.verification.VerificationSession
import eu.europa.ec.dashboardfeature.model.verification.humanizeVerificationStatus
import eu.europa.ec.dashboardfeature.model.verification.lastStatusChangedAt
import eu.europa.ec.dashboardfeature.model.verification.lastUpdatedAt
import eu.europa.ec.dashboardfeature.ui.component.NotificationIconButton
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.InlineSnackbar
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.utils.LifecycleEffect
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.utils.OneTimeLaunchedEffect
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_SMALL
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val publicTokenDisplayQueryKeys = setOf(
    "token",
    "access_token",
    "verification_token",
    "authbound_verification_token",
    "x-authbound-verification-token",
    "x_authbound_verification_token",
    "public_token"
)

internal fun displayPublicVerificationUrl(publicUrl: String?): String? {
    val value = publicUrl?.takeIf { it.isNotBlank() } ?: return null
    val fragmentStripped = value.substringBefore('#')

    val hasTokenQuery = fragmentStripped
        .substringAfter('?', missingDelimiterValue = "")
        .split('&')
        .map { it.substringBefore('=').lowercase(Locale.ROOT) }
        .any(publicTokenDisplayQueryKeys::contains)

    return if (hasTokenQuery) {
        fragmentStripped.substringBefore('?')
    } else {
        fragmentStripped
    }
}

@Composable
fun VerificationSharingScreen(
    navController: NavController,
    viewModel: VerificationSharingViewModel,
    notificationCount: Int = 0,
    onNotificationsClick: () -> Unit = {},
) {
    val state: VerificationSharingState by viewModel.viewState.collectAsStateWithLifecycle()
    val context: Context = LocalContext.current

    ContentScreen(
        isLoading = state.isLoading,
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack = { viewModel.setEvent(VerificationSharingEvent.NavigateBack) },
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    modifier = Modifier.align(Alignment.Center),
                    text = stringResource(R.string.verification_sharing_title),
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
        VerificationSharingContent(
            state = state,
            paddingValues = paddingValues,
            onCopy = { viewModel.setEvent(VerificationSharingEvent.CopyLink) },
            onShare = { viewModel.setEvent(VerificationSharingEvent.ShareLink) },
            onRefresh = { viewModel.setEvent(VerificationSharingEvent.Refresh) },
            onDismissError = { viewModel.setEvent(VerificationSharingEvent.DismissError) },
            onDone = { viewModel.setEvent(VerificationSharingEvent.NavigateHome) }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is VerificationSharingEffect.Navigation -> handleVerificationSharingNavigation(
                    navigationEffect = effect,
                    navController = navController
                )
                is VerificationSharingEffect.CopyToClipboard -> {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(effect.label, effect.content))
                    Toast.makeText(
                        context,
                        effect.confirmationMessage,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is VerificationSharingEffect.ShareContent -> {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, effect.title)
                        putExtra(Intent.EXTRA_TEXT, effect.content)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, effect.title))
                }
                is VerificationSharingEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    OneTimeLaunchedEffect {
        viewModel.setEvent(VerificationSharingEvent.Init)
    }

    LifecycleEffect(
        lifecycleOwner = LocalLifecycleOwner.current,
        lifecycleEvent = Lifecycle.Event.ON_RESUME
    ) {
        viewModel.setEvent(VerificationSharingEvent.Refresh)
    }
}

@Composable
private fun VerificationSharingContent(
    state: VerificationSharingState,
    paddingValues: PaddingValues,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRefresh: () -> Unit,
    onDismissError: () -> Unit,
    onDone: () -> Unit,
) {
    val session = state.session
    val hasShareTarget = session?.let { !it.publicUrl.isNullOrBlank() || !it.qrPayload.isNullOrBlank() } == true
    val prefersPublicLink = !session?.publicUrl.isNullOrBlank()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
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
                        text = state.error ?: stringResource(R.string.verification_sharing_unavailable),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                return@LazyColumn
            }

            item {
                SessionSummaryCard(session = session)
            }

            item {
                ShareTargetsCard(session = session)
            }

            item {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onShare,
                    enabled = hasShareTarget
                ) {
                    Text(
                        text = if (prefersPublicLink) {
                            stringResource(R.string.verification_sharing_share)
                        } else {
                            stringResource(R.string.verification_sharing_share_wallet_request)
                        }
                    )
                }
            }

            item {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onCopy,
                    enabled = hasShareTarget
                ) {
                    Text(
                        text = if (prefersPublicLink) {
                            stringResource(R.string.verification_sharing_copy_link)
                        } else {
                            stringResource(R.string.verification_sharing_copy_wallet_request)
                        }
                    )
                }
            }

            item {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDone
                ) {
                    Text(stringResource(R.string.verification_sharing_done))
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
private fun SessionSummaryCard(session: VerificationSession) {
    val lastUpdatedAt = session.lastUpdatedAt()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = session.purpose,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            MetadataRow(
                label = stringResource(R.string.verification_sharing_metadata_status),
                value = humanizeVerificationStatus(session.status),
                valueColor = MaterialTheme.colorScheme.primary
            )
            MetadataRow(
                label = stringResource(R.string.verification_sharing_metadata_created),
                value = formatVerificationDateTime(session.createdAt)
            )
            if (lastUpdatedAt > session.createdAt) {
                MetadataRow(
                    label = stringResource(R.string.verification_sharing_metadata_last_updated),
                    value = formatVerificationDateTime(lastUpdatedAt)
                )
            }
            session.expiresAt?.let { expiresAt ->
                MetadataRow(
                    label = stringResource(R.string.verification_sharing_metadata_expires),
                    value = formatVerificationDateTime(expiresAt)
                )
            }
            if (session.creditsDeducted != null || session.creditsRemaining != null) {
                session.creditsDeducted?.let { charged ->
                    MetadataRow(
                        label = stringResource(R.string.verification_sharing_metadata_credits_charged),
                        value = charged.toString()
                    )
                }
                session.creditsRemaining?.let { remaining ->
                    MetadataRow(
                        label = stringResource(R.string.verification_sharing_metadata_credits_remaining),
                        value = remaining.toString()
                    )
                }
            }
            Text(
                text = stringResource(R.string.verification_sharing_requested_attributes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            session.requestedAttributes.forEach { attribute ->
                val suffix = attribute.expectedValue.takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty()
                Text(
                    text = "${attribute.label}$suffix",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ShareTargetsCard(session: VerificationSession) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.verification_sharing_public_link_heading),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = displayPublicVerificationUrl(session.publicUrl)
                    ?: stringResource(R.string.verification_sharing_public_link_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            if (!session.qrPayload.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.verification_sharing_wallet_payload_heading),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = session.qrPayload,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
                session.qrPayloadExpiresAt?.let { expiresAt ->
                    Text(
                        text = stringResource(
                            R.string.verification_sharing_wallet_payload_expires,
                            formatVerificationDateTime(expiresAt)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = stringResource(R.string.verification_sharing_recipients_heading),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (session.recipients.isEmpty()) {
                Text(
                    text = stringResource(R.string.verification_sharing_recipients_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                session.recipients.forEach { recipient ->
                    RecipientStatusCard(recipient = recipient)
                }
            }
        }

    }
}

@Composable
private fun MetadataRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor
        )
    }
}

@Composable
private fun RecipientStatusCard(recipient: VerificationRecipient) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = recipient.value,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = recipientMetadata(recipient),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                recipientTimelineText(recipient)?.let { timeline ->
                    Text(
                        text = timeline,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = humanizeRecipientStatus(recipient.status),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.recipientStatusColor(recipient.status)
            )
        }
    }
}

private fun recipientMetadata(recipient: VerificationRecipient): String {
    return buildString {
        append(humanizeRecipientContactType(recipient.contactType))
        if (recipient.resolvedUserId != null) {
            append(" • Wallet user")
        }
    }
}

private fun recipientTimelineText(recipient: VerificationRecipient): String? {
    val timelineLabel = when (recipient.status?.lowercase()) {
        null, "", "pending", "invited" -> "Invited"
        "opened" -> "Opened"
        "verified" -> "Verified"
        "failed" -> "Failed"
        "expired" -> "Expired"
        "rejected" -> "Rejected"
        "invalid" -> "Invalid"
        "canceled" -> "Canceled"
        else -> humanizeRecipientStatus(recipient.status)
    }

    return recipient.lastStatusChangedAt()?.let { "$timelineLabel ${formatVerificationDateTime(it)}" }
}

private fun humanizeRecipientContactType(contactType: VerificationRecipientContactType): String = when (contactType) {
    VerificationRecipientContactType.HANDLE -> "Handle"
    VerificationRecipientContactType.EMAIL -> "Email"
    VerificationRecipientContactType.PHONE -> "Phone"
    VerificationRecipientContactType.LINK -> "Link"
}

private fun humanizeRecipientStatus(status: String?): String = when (status?.lowercase()) {
    null, "", "pending", "invited" -> "Invited"
    "opened" -> "Opened"
    "verified" -> "Verified"
    "failed" -> "Failed"
    "expired" -> "Expired"
    "rejected" -> "Rejected"
    "invalid" -> "Invalid"
    "canceled" -> "Canceled"
    else -> humanizeVerificationStatus(status)
}

private fun ColorScheme.recipientStatusColor(status: String?): Color = when (status?.lowercase()) {
    "verified" -> primary
    "failed", "expired", "rejected", "invalid", "canceled" -> error
    "opened" -> tertiary
    else -> onSurfaceVariant
}

private fun formatVerificationDateTime(timestamp: Long): String {
    val dateFormat = SimpleDateFormat("MMM d, yyyy 'at' HH:mm", Locale.getDefault())
    return dateFormat.format(Date(timestamp))
}

private fun handleVerificationSharingNavigation(
    navigationEffect: VerificationSharingEffect.Navigation,
    navController: NavController
) {
    when (navigationEffect) {
        VerificationSharingEffect.Navigation.Back -> navController.popBackStack()
        is VerificationSharingEffect.Navigation.SwitchScreen -> {
            navController.navigate(navigationEffect.screenRoute) {
                popUpTo(navigationEffect.popUpToScreenRoute) {
                    inclusive = navigationEffect.inclusive
                }
            }
        }
    }
}
