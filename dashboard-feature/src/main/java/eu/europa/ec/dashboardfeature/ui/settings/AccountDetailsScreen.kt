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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.component.wrap.WrapCard
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import androidx.compose.foundation.layout.Row

@Composable
fun AccountDetailsScreen(
    navController: NavController,
    viewModel: SettingsViewModel,
){
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()

    ContentScreen(
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack = { navController.popBackStack() }
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
    ) {
        VSpacer.Large()

        AuthInfoSection(authInfo = state.authInfo)

        VSpacer.Large()

        Text(
            text = state.appVersion,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = SPACING_MEDIUM.dp)
        )
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun AuthInfoSection(authInfo: AuthInfoUi) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SPACING_MEDIUM.dp)
    ) {
        Text(
            text = "Account details",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        VSpacer.Medium()

        val status = if (authInfo.isAuthenticated) "Authenticated" else "Not Authenticated"
        InfoRow(label = "Status", value = status)

        authInfo.userInfo?.let { user ->
            VSpacer.Medium()
            InfoRow(label = "User ID", value = user.id)
            VSpacer.Small()
            InfoRow(label = "Phone", value = user.phone.takeIf { !it.isNullOrBlank() } ?: "N/A")
            VSpacer.Small()
            user.createdAt?.let {
                InfoRow(
                    label = "Member since",
                    value = it.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
                )
            }
            VSpacer.Small()
            user.lastSignInAt?.let {
                InfoRow(
                    label = "Last sign-in",
                    value = it.toLocalDateTime(TimeZone.currentSystemDefault()).toString().replace("T", " ")
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

