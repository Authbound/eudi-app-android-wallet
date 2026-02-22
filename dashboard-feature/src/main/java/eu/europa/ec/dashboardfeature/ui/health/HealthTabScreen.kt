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

package eu.europa.ec.dashboardfeature.ui.health

import android.net.Uri
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.FeatureHighlight
import eu.europa.ec.uilogic.component.wrap.PremiumActionButton
import eu.europa.ec.uilogic.component.wrap.PremiumEmptyState
import eu.europa.ec.uilogic.component.wrap.EmptyStateIllustration
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthTabContent(
    navController: NavController,
    viewModel: HealthViewModel,
    paddingValues: PaddingValues,
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.setEvent(Event.OnResume)
    }

    LaunchedEffect(Unit) {
        viewModel.setEvent(Event.Init)
        viewModel.effect.onEach { effect ->
            when (effect) {
                is Effect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is Effect.LaunchMaisaAuth -> {
                    val customTabsIntent = CustomTabsIntent.Builder().build()
                    customTabsIntent.launchUrl(context, Uri.parse(effect.authorizationUrl))
                }
                is Effect.Navigation.SwitchScreen -> {
                    navController.navigate(effect.screenRoute) {
                        popUpTo(effect.popUpToScreenRoute) {
                            inclusive = effect.inclusive
                        }
                    }
                }
            }
        }.collect()
    }

    // Bottom sheet handling
    if (state.sheetContent != HealthSheetContent.None) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { viewModel.setEvent(Event.DismissSheet) },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            when (val content = state.sheetContent) {
                is HealthSheetContent.MedicationDetail -> {
                    MedicationDetailSheet(
                        medication = content.medication,
                        onShareClick = { viewModel.setEvent(Event.SharePressed(content.medication.id, HealthCategoryUi.Medications)) },
                        onDismiss = { viewModel.setEvent(Event.DismissSheet) }
                    )
                }
                is HealthSheetContent.ShareOptions -> {
                    ShareOptionsSheet(
                        onDismiss = { viewModel.setEvent(Event.DismissSheet) }
                    )
                }
                is HealthSheetContent.ServiceInfo -> {
                    ServiceSelectionSheet(
                        kantaStatus = state.kantaStatus,
                        maisaStatus = state.maisaStatus,
                        isConnecting = state.isConnecting,
                        onConnectKanta = { viewModel.setEvent(Event.ConnectKantaPressed) },
                        onConnectMaisa = { viewModel.setEvent(Event.ConnectMaisaPressed) },
                        onDismiss = { viewModel.setEvent(Event.DismissSheet) }
                    )
                }
                HealthSheetContent.None -> {}
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            state.showEmptyState -> {
                HealthEmptyState(
                    kantaStatus = state.kantaStatus,
                    maisaStatus = state.maisaStatus,
                    onImportClick = { viewModel.setEvent(Event.ImportPressed) },
                    onConnectKanta = { viewModel.setEvent(Event.ConnectKantaPressed) },
                    onConnectMaisa = { viewModel.setEvent(Event.ConnectMaisaPressed) }
                )
            }
            else -> {
                HealthDataContent(
                    state = state,
                    onMedicationClick = { id -> viewModel.setEvent(Event.CredentialClicked(id, HealthCategoryUi.Medications)) },
                    onPrescriptionClick = { id -> viewModel.setEvent(Event.CredentialClicked(id, HealthCategoryUi.Prescriptions)) },
                    onRecordClick = { id -> viewModel.setEvent(Event.CredentialClicked(id, HealthCategoryUi.HealthRecords)) },
                    onVaccinationClick = { id -> viewModel.setEvent(Event.CredentialClicked(id, HealthCategoryUi.Vaccinations)) },
                    onShareClick = { id, type -> viewModel.setEvent(Event.SharePressed(id, type)) },
                    onRefresh = { viewModel.setEvent(Event.RefreshPressed) },
                    onImportMore = { viewModel.setEvent(Event.ImportPressed) }
                )
            }
        }
    }
}

@Composable
private fun HealthEmptyState(
    kantaStatus: ConnectionStatus,
    maisaStatus: ConnectionStatus,
    onImportClick: () -> Unit,
    onConnectKanta: () -> Unit,
    onConnectMaisa: () -> Unit,
) {
    PremiumEmptyState(
        illustration = EmptyStateIllustration.HEALTH,
        title = stringResource(R.string.health_empty_title),
        description = stringResource(R.string.health_empty_description),
        features = listOf(
            FeatureHighlight(
                icon = AppIcons.Health,
                title = stringResource(R.string.health_kanta_title),
                description = stringResource(R.string.health_kanta_short_description)
            ),
            FeatureHighlight(
                icon = AppIcons.HealthRecord,
                title = stringResource(R.string.health_maisa_title),
                description = stringResource(R.string.health_maisa_short_description)
            )
        ),
        // actionLabel = stringResource(R.string.health_import_button),
        // onActionClick = onImportClick,
        actionLabel = stringResource(R.string.coming_soon_badge),
        onActionClick = { },
        enableAnimations = false
    )
}

@Composable
private fun HealthDataContent(
    state: State,
    onMedicationClick: (String) -> Unit,
    onPrescriptionClick: (String) -> Unit,
    onRecordClick: (String) -> Unit,
    onVaccinationClick: (String) -> Unit,
    onShareClick: (String, HealthCategoryUi) -> Unit,
    onRefresh: () -> Unit,
    onImportMore: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = SPACING_MEDIUM.dp, vertical = SPACING_SMALL.dp),
        verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
    ) {
        // Connection status banner
        item {
            ConnectionStatusBanner(
                kantaStatus = state.kantaStatus,
                maisaStatus = state.maisaStatus,
                lastSyncTime = state.lastSyncTime,
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh
            )
        }

        // Medications section
        if (state.medications.isNotEmpty()) {
            item {
                HealthSectionHeader(
                    title = stringResource(R.string.health_category_medications),
                    count = state.medications.size,
                    icon = AppIcons.Medication
                )
            }
            itemsIndexed(
                items = state.medications,
                key = { _, item -> "med_${item.id}" }
            ) { _, medication ->
                MedicationItem(
                    medication = medication,
                    onClick = { onMedicationClick(medication.id) },
                    onShareClick = { onShareClick(medication.id, HealthCategoryUi.Medications) }
                )
            }
        }

        // Prescriptions section
        if (state.prescriptions.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                HealthSectionHeader(
                    title = stringResource(R.string.health_category_prescriptions),
                    count = state.prescriptions.size,
                    icon = AppIcons.Prescription
                )
            }
            itemsIndexed(
                items = state.prescriptions,
                key = { _, item -> "presc_${item.id}" }
            ) { _, prescription ->
                PrescriptionItem(
                    prescription = prescription,
                    onClick = { onPrescriptionClick(prescription.id) },
                    onShareClick = { onShareClick(prescription.id, HealthCategoryUi.Prescriptions) }
                )
            }
        }

        // Health Records section
        if (state.healthRecords.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                HealthSectionHeader(
                    title = stringResource(R.string.health_category_records),
                    count = state.healthRecords.size,
                    icon = AppIcons.HealthRecord
                )
            }
            itemsIndexed(
                items = state.healthRecords,
                key = { _, item -> "record_${item.id}" }
            ) { _, record ->
                HealthRecordItem(
                    record = record,
                    onClick = { onRecordClick(record.id) },
                    onShareClick = { onShareClick(record.id, HealthCategoryUi.HealthRecords) }
                )
            }
        }

        // Vaccinations section
        if (state.vaccinations.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                HealthSectionHeader(
                    title = stringResource(R.string.health_category_vaccinations),
                    count = state.vaccinations.size,
                    icon = AppIcons.Vaccination
                )
            }
            itemsIndexed(
                items = state.vaccinations,
                key = { _, item -> "vacc_${item.id}" }
            ) { _, vaccination ->
                VaccinationItem(
                    vaccination = vaccination,
                    onClick = { onVaccinationClick(vaccination.id) },
                    onShareClick = { onShareClick(vaccination.id, HealthCategoryUi.Vaccinations) }
                )
            }
        }

        // Import more button at the bottom
        item {
            Spacer(modifier = Modifier.height(16.dp))
            // PremiumActionButton(
            //     text = stringResource(R.string.health_import_more),
            //     onClick = onImportMore,
            //     icon = AppIcons.Add,
            //     modifier = Modifier.fillMaxWidth()
            // )
            PremiumActionButton(
                text = stringResource(R.string.coming_soon_badge),
                onClick = { },
                icon = AppIcons.Add,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(90.dp))
        }
    }
}

@Composable
private fun ConnectionStatusBanner(
    kantaStatus: ConnectionStatus,
    maisaStatus: ConnectionStatus,
    lastSyncTime: String?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    val isConnected = kantaStatus == ConnectionStatus.CONNECTED || maisaStatus == ConnectionStatus.CONNECTED
    val connectedService = when {
        kantaStatus == ConnectionStatus.CONNECTED && maisaStatus == ConnectionStatus.CONNECTED -> "Kanta & Maisa"
        kantaStatus == ConnectionStatus.CONNECTED -> "Kanta.fi"
        maisaStatus == ConnectionStatus.CONNECTED -> "Maisa"
        else -> null
    }

    if (isConnected && connectedService != null) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.health_connected_to, connectedService),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (lastSyncTime != null) {
                        Text(
                            text = stringResource(R.string.health_last_sync, lastSyncTime),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = onRefresh) {
                        WrapIcon(
                            iconData = AppIcons.Refresh,
                            customTint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthSectionHeader(
    title: String,
    count: Int,
    icon: eu.europa.ec.uilogic.component.IconDataUi,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WrapIcon(
            iconData = icon,
            customTint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$title ($count)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun MedicationItem(
    medication: MedicationUi,
    onClick: () -> Unit,
    onShareClick: () -> Unit,
) {
    val view = LocalView.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        when (medication.status) {
                            MedicationStatus.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
                            MedicationStatus.EXPIRING_SOON -> MaterialTheme.colorScheme.tertiaryContainer
                            MedicationStatus.EXPIRED -> MaterialTheme.colorScheme.errorContainer
                            MedicationStatus.DISCONTINUED -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                WrapIcon(
                    iconData = AppIcons.Medication,
                    customTint = when (medication.status) {
                        MedicationStatus.ACTIVE -> MaterialTheme.colorScheme.primary
                        MedicationStatus.EXPIRING_SOON -> MaterialTheme.colorScheme.tertiary
                        MedicationStatus.EXPIRED -> MaterialTheme.colorScheme.error
                        MedicationStatus.DISCONTINUED -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = medication.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = medication.dosage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (medication.validUntil != null) {
                    Text(
                        text = stringResource(R.string.health_valid_until, medication.validUntil),
                        style = MaterialTheme.typography.bodySmall,
                        color = when (medication.status) {
                            MedicationStatus.EXPIRING_SOON -> MaterialTheme.colorScheme.tertiary
                            MedicationStatus.EXPIRED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            // Share button
            IconButton(onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onShareClick()
            }) {
                WrapIcon(
                    iconData = AppIcons.Share,
                    customTint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun PrescriptionItem(
    prescription: PrescriptionUi,
    onClick: () -> Unit,
    onShareClick: () -> Unit,
) {
    val view = LocalView.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                WrapIcon(
                    iconData = AppIcons.Prescription,
                    customTint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = prescription.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.health_prescribed_by, prescription.prescribedBy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (prescription.renewalDate != null) {
                    Text(
                        text = stringResource(R.string.health_renewal_date, prescription.renewalDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onShareClick()
            }) {
                WrapIcon(
                    iconData = AppIcons.Share,
                    customTint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun HealthRecordItem(
    record: HealthRecordUi,
    onClick: () -> Unit,
    onShareClick: () -> Unit,
) {
    val view = LocalView.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                WrapIcon(
                    iconData = AppIcons.HealthRecord,
                    customTint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${record.category} • ${record.date}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = record.provider,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onShareClick()
            }) {
                WrapIcon(
                    iconData = AppIcons.Share,
                    customTint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun VaccinationItem(
    vaccination: VaccinationUi,
    onClick: () -> Unit,
    onShareClick: () -> Unit,
) {
    val view = LocalView.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (vaccination.isBooster)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                WrapIcon(
                    iconData = AppIcons.Vaccination,
                    customTint = if (vaccination.isBooster)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = vaccination.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (vaccination.isBooster) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = stringResource(R.string.health_booster),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "${vaccination.date} • ${vaccination.provider}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (vaccination.nextDoseDate != null) {
                    Text(
                        text = stringResource(R.string.health_next_dose, vaccination.nextDoseDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            IconButton(onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onShareClick()
            }) {
                WrapIcon(
                    iconData = AppIcons.Share,
                    customTint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun MedicationDetailSheet(
    medication: MedicationUi,
    onShareClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = medication.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = medication.dosage,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (medication.prescribedBy != null) {
            DetailRow(label = stringResource(R.string.health_prescribed_by_label), value = medication.prescribedBy)
        }
        if (medication.pharmacy != null) {
            DetailRow(label = stringResource(R.string.health_pharmacy_label), value = medication.pharmacy)
        }
        if (medication.validUntil != null) {
            DetailRow(label = stringResource(R.string.health_valid_until_label), value = medication.validUntil)
        }
        DetailRow(
            label = stringResource(R.string.health_status_label),
            value = when (medication.status) {
                MedicationStatus.ACTIVE -> stringResource(R.string.health_status_active)
                MedicationStatus.EXPIRING_SOON -> stringResource(R.string.health_status_expiring_soon)
                MedicationStatus.EXPIRED -> stringResource(R.string.health_status_expired)
                MedicationStatus.DISCONTINUED -> stringResource(R.string.health_status_discontinued)
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        PremiumActionButton(
            text = stringResource(R.string.health_share_credential),
            onClick = onShareClick,
            icon = AppIcons.Share,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
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

@Composable
private fun ShareOptionsSheet(
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = stringResource(R.string.health_share_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.health_share_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        // QR Code option
        ShareOptionCard(
            icon = AppIcons.QrScanner,
            title = stringResource(R.string.health_share_qr_title),
            description = stringResource(R.string.health_share_qr_description),
            onClick = { /* TODO: Navigate to QR presentation */ }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Deep link option
        ShareOptionCard(
            icon = AppIcons.OpenNew,
            title = stringResource(R.string.health_share_link_title),
            description = stringResource(R.string.health_share_link_description),
            onClick = { /* TODO: Generate and share deep link */ }
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ShareOptionCard(
    icon: eu.europa.ec.uilogic.component.IconDataUi,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    val view = LocalView.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                WrapIcon(
                    iconData = icon,
                    customTint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            WrapIcon(
                iconData = AppIcons.KeyboardArrowRight,
                customTint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ServiceSelectionSheet(
    kantaStatus: ConnectionStatus,
    maisaStatus: ConnectionStatus,
    isConnecting: Boolean,
    onConnectKanta: () -> Unit,
    onConnectMaisa: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = stringResource(R.string.health_import_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.health_import_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Kanta.fi service card
        HealthServiceCard(
            service = HealthService.KANTA,
            title = stringResource(R.string.health_kanta_title),
            description = stringResource(R.string.health_kanta_description),
            isConnected = kantaStatus == ConnectionStatus.CONNECTED,
            isConnecting = isConnecting,
            onClick = onConnectKanta
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Maisa service card
        HealthServiceCard(
            service = HealthService.MAISA,
            title = stringResource(R.string.health_maisa_title),
            description = stringResource(R.string.health_maisa_description),
            isConnected = maisaStatus == ConnectionStatus.CONNECTED,
            isConnecting = isConnecting,
            onClick = onConnectMaisa
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun HealthServiceCard(
    service: HealthService,
    title: String,
    description: String,
    isConnected: Boolean,
    isConnecting: Boolean,
    onClick: () -> Unit,
) {
    val view = LocalView.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isConnecting && !isConnected) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
        shape = RoundedCornerShape(16.dp),
        color = if (isConnected)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else
            MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isConnected)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else
                            MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                WrapIcon(
                    iconData = when (service) {
                        HealthService.KANTA -> AppIcons.Health
                        HealthService.MAISA -> AppIcons.HealthRecord
                    },
                    customTint = if (isConnected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else if (isConnected) {
                WrapIcon(
                    iconData = AppIcons.Check,
                    customTint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                WrapIcon(
                    iconData = AppIcons.KeyboardArrowRight,
                    customTint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
