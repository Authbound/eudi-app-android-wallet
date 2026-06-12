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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.dashboardfeature.interactor.CredentialSummaryUi
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.brandNavyDeep
import eu.europa.ec.resourceslogic.theme.values.brandNavyMedium
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.content.ToolbarConfig
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.component.wrap.ProfileAvatar
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun AccountDetailsScreen(
    navController: NavController,
    viewModel: SettingsViewModel,
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.setEvent(Event.RefreshPidPortrait)
    }

    ContentScreen(
        toolBarConfig = ToolbarConfig(
            title = stringResource(R.string.account_details_title),
        ),
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack = { navController.popBackStack() },
    ) { paddingValues ->
        AccountDetailsContent(
            state = state,
            paddingValues = paddingValues
        )
    }
}

@Composable
private fun AccountDetailsContent(
    state: State,
    paddingValues: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SPACING_MEDIUM.dp),
        verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
    ) {
        VSpacer.Small()

        // Compact profile hero card
        ProfileHeroCard(
            authInfo = state.authInfo,
            credentialCount = state.credentialCount,
            pidPortraitBase64 = state.pidPortraitBase64
        )

        // Contact section
        ContactSection(authInfo = state.authInfo)

        // Security & Activity section
        ActivitySection(authInfo = state.authInfo)

        // Credentials section
        if (state.credentials.isNotEmpty()) {
            CredentialsSection(credentials = state.credentials)
        }

        // App version footer
        Text(
            text = state.appVersion,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = SPACING_MEDIUM.dp)
        )
    }
}

/**
 * Compact premium profile hero card with gradient background.
 * Horizontal layout: avatar initials + name/handle + credential badge.
 */
@Composable
private fun ProfileHeroCard(
    authInfo: AuthInfoUi,
    credentialCount: Int,
    pidPortraitBase64: String?
) {
    val displayName = authInfo.profile?.displayName ?: "User"
    val handle = authInfo.profile?.handle

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 4.dp,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.brandNavyDeep,
                            MaterialTheme.colorScheme.brandNavyMedium
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shared avatar: PID portrait or initials monogram
                ProfileAvatar(
                    displayName = displayName,
                    avatarUrl = null,
                    portraitBase64 = pidPortraitBase64,
                    size = 52,
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Name and handle
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!handle.isNullOrBlank()) {
                        Text(
                            text = "@$handle",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Credential count badge
                if (credentialCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            WrapIcon(
                                iconData = AppIcons.Documents,
                                customTint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (credentialCount == 1) {
                                    stringResource(R.string.account_details_credential_count_single)
                                } else {
                                    stringResource(
                                        R.string.account_details_credentials_count,
                                        credentialCount
                                    )
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Contact information section with email and phone.
 */
@Composable
private fun ContactSection(authInfo: AuthInfoUi) {
    val email = authInfo.userInfo?.email
    val phone = authInfo.userInfo?.phone

    // Only show section if there's at least email or phone
    if (email.isNullOrBlank() && phone.isNullOrBlank()) return

    SectionCard(title = stringResource(R.string.account_details_section_contact)) {
        if (!email.isNullOrBlank()) {
            InfoRow(
                icon = AppIcons.Message,
                label = stringResource(R.string.account_details_email),
                value = email
            )
        }
        if (!email.isNullOrBlank() && !phone.isNullOrBlank()) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 40.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
        InfoRow(
            icon = AppIcons.UserIcon,
            label = stringResource(R.string.account_details_phone),
            value = if (!phone.isNullOrBlank()) phone
            else stringResource(R.string.account_details_phone_not_set)
        )
    }
}

/**
 * Security and activity section showing verification status, member since, last active.
 */
@Composable
private fun ActivitySection(authInfo: AuthInfoUi) {
    SectionCard(title = stringResource(R.string.account_details_section_activity)) {
        // Verification status
        InfoRow(
            icon = AppIcons.Verified,
            label = stringResource(R.string.account_details_status),
            value = if (authInfo.isAuthenticated) {
                stringResource(R.string.account_details_status_verified)
            } else {
                stringResource(R.string.account_details_status_unverified)
            },
            valueColor = if (authInfo.isAuthenticated) {
                Color(0xFF059669) // Green for verified
            } else {
                MaterialTheme.colorScheme.error
            }
        )

        authInfo.userInfo?.createdAt?.let { createdAt ->
            HorizontalDivider(
                modifier = Modifier.padding(start = 40.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            val localDate = createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
            val formatted = "${localDate.month.name.lowercase().replaceFirstChar { it.uppercase() }
                .take(3)} ${localDate.year}"
            InfoRow(
                icon = AppIcons.DateRange,
                label = stringResource(R.string.account_details_member_since),
                value = formatted
            )
        }

        authInfo.userInfo?.lastSignInAt?.let { lastSignIn ->
            HorizontalDivider(
                modifier = Modifier.padding(start = 40.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            val localDateTime = lastSignIn.toLocalDateTime(TimeZone.currentSystemDefault())
            val month = localDateTime.month.name.lowercase().replaceFirstChar { it.uppercase() }
                .take(3)
            val day = localDateTime.dayOfMonth
            val year = localDateTime.year
            val hour = localDateTime.hour
            val minute = localDateTime.minute.toString().padStart(2, '0')
            val amPm = if (hour < 12) "AM" else "PM"
            val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            val formatted = "$month $day, $year at $displayHour:$minute $amPm"
            InfoRow(
                icon = AppIcons.ClockTimer,
                label = stringResource(R.string.account_details_last_active),
                value = formatted
            )
        }
    }
}

/**
 * Subtle credentials list showing document names and issuers.
 */
@Composable
private fun CredentialsSection(credentials: List<CredentialSummaryUi>) {
    SectionCard(title = stringResource(R.string.account_details_section_credentials)) {
        credentials.forEachIndexed { index, credential ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 40.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WrapIcon(
                    iconData = AppIcons.Id,
                    customTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = credential.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!credential.issuerName.isNullOrBlank()) {
                        Text(
                            text = credential.issuerName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Reusable grouped section card with a title header.
 */
@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                content()
            }
        }
    }
}

/**
 * Single info row with icon, label, and value.
 */
@Composable
private fun InfoRow(
    icon: eu.europa.ec.uilogic.component.IconDataUi,
    label: String,
    value: String,
    valueColor: Color? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WrapIcon(
            iconData = icon,
            customTint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = valueColor ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
