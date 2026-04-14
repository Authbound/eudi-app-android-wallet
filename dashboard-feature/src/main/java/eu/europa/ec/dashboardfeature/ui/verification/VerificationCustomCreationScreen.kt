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

import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.dashboardfeature.model.verification.VerificationDraftAttribute
import eu.europa.ec.dashboardfeature.model.verification.VerificationRecipient
import eu.europa.ec.dashboardfeature.model.verification.VerificationRecipientContactType
import eu.europa.ec.dashboardfeature.model.verification.VerificationTemplateType
import eu.europa.ec.dashboardfeature.ui.component.NotificationIconButton
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.utils.OneTimeLaunchedEffect
import kotlinx.coroutines.flow.collectLatest

@Composable
fun VerificationCustomCreationScreen(
    navController: NavController,
    viewModel: VerificationViewModel,
    templateType: VerificationTemplateType?,
    notificationCount: Int = 0,
    onNotificationsClick: () -> Unit = {},
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val context: Context = LocalContext.current

    ContentScreen(
        isLoading = state.isLoading,
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack = { viewModel.setEvent(Event.NavigateBack) },
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    modifier = Modifier.align(Alignment.Center),
                    text = stringResource(R.string.verification_creation_custom_title),
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
        VerificationCustomCreationContent(
            state = state,
            paddingValues = paddingValues,
            onEventSent = viewModel::setEvent,
        )
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is Effect.Navigation -> handleNavigationEffect(context, effect, navController)
                is Effect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    OneTimeLaunchedEffect {
        viewModel.setEvent(Event.Init)
    }

    LaunchedEffect(state.templates, templateType) {
        if (templateType != null && state.templates.isNotEmpty() && state.selectedTemplate?.type != templateType) {
            viewModel.setEvent(Event.InitializeTemplate(templateType))
        }
    }
}

@Composable
private fun VerificationCustomCreationContent(
    state: State,
    paddingValues: PaddingValues,
    onEventSent: (Event) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.purpose,
                onValueChange = { onEventSent(Event.UpdatePurpose(it)) },
                label = { Text("Purpose") },
                supportingText = { Text("This is shown to the recipient when they open the request.") },
            )
        }

        item {
            Text(
                text = stringResource(R.string.verification_creation_parameters_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        itemsIndexed(state.attributes, key = { _, attribute -> attribute.key }) { _, attribute ->
            AttributeEditorCard(
                attribute = attribute,
                onToggle = { selected -> onEventSent(Event.ToggleAttribute(attribute.key, selected)) },
                onExpectedValueChanged = { value ->
                    onEventSent(Event.UpdateExpectedValue(attribute.key, value))
                }
            )
        }

        item {
            RecipientSection(
                recipients = state.recipients,
                recipientValue = state.recipientValue,
                contactType = state.recipientContactType,
                onRecipientValueChanged = { onEventSent(Event.UpdateRecipientValue(it)) },
                onContactTypeSelected = { onEventSent(Event.UpdateRecipientContactType(it)) },
                onAddRecipient = { onEventSent(Event.AddRecipient) },
                onRemoveRecipient = { index -> onEventSent(Event.RemoveRecipient(index)) }
            )
        }

        item {
            if (state.error != null) {
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        item {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onEventSent(Event.CreateSession) }
            ) {
                Text(text = stringResource(R.string.verification_creation_submit_button))
            }
        }
    }
}

@Composable
private fun AttributeEditorCard(
    attribute: VerificationDraftAttribute,
    onToggle: (Boolean) -> Unit,
    onExpectedValueChanged: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Checkbox(
                    checked = attribute.selected,
                    onCheckedChange = onToggle
                )
                Column(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f)
                ) {
                    Text(
                        text = attribute.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = attribute.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (attribute.selected && attribute.requiresExpectedValue) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = attribute.expectedValue,
                    onValueChange = onExpectedValueChanged,
                    label = { Text("Expected value") },
                    supportingText = { Text("Leave no ambiguity for the verifier.") },
                )
            }
        }
    }
}

@Composable
private fun RecipientSection(
    recipients: List<VerificationRecipient>,
    recipientValue: String,
    contactType: VerificationRecipientContactType,
    onRecipientValueChanged: (String) -> Unit,
    onContactTypeSelected: (VerificationRecipientContactType) -> Unit,
    onAddRecipient: () -> Unit,
    onRemoveRecipient: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Recipients",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VerificationRecipientContactType.entries.forEach { type ->
                FilterChip(
                    selected = type == contactType,
                    onClick = { onContactTypeSelected(type) },
                    label = {
                        Text(
                            when (type) {
                                VerificationRecipientContactType.HANDLE -> "Handle"
                                VerificationRecipientContactType.EMAIL -> "Email"
                                VerificationRecipientContactType.PHONE -> "Phone"
                            }
                        )
                    }
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = recipientValue,
                onValueChange = onRecipientValueChanged,
                label = {
                    Text(
                        when (contactType) {
                            VerificationRecipientContactType.HANDLE -> "Recipient handle"
                            VerificationRecipientContactType.EMAIL -> "Recipient email"
                            VerificationRecipientContactType.PHONE -> "Recipient phone"
                        }
                    )
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onAddRecipient) {
                Text("Add")
            }
        }

        if (recipients.isEmpty()) {
            Text(
                text = "Recipients are optional. If you leave this empty, you can still share the session link or QR later.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recipients.forEachIndexed { index, recipient ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = recipient.value,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = recipient.contactType.apiValue,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(onClick = { onRemoveRecipient(index) }) {
                                Text("Remove")
                            }
                        }
                    }
                }
            }
        }
    }
}
