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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.dashboardfeature.model.verification.VerificationDraftAttribute
import eu.europa.ec.dashboardfeature.model.verification.VerificationRecipient
import eu.europa.ec.dashboardfeature.model.verification.VerificationRecipientContactType
import eu.europa.ec.dashboardfeature.model.verification.VerificationTemplateType
import eu.europa.ec.dashboardfeature.ui.component.NotificationIconButton
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.utils.OneTimeLaunchedEffect
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapIconButton
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

    val screenTitle = state.selectedTemplate?.title
        ?: stringResource(R.string.verification_creation_custom_title)

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
                    text = screenTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
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
        // Purpose card
        item {
            PurposeCard(
                purpose = state.purpose,
                onPurposeChanged = { onEventSent(Event.UpdatePurpose(it)) }
            )
        }

        // Parameters section header
        item {
            Text(
                text = stringResource(R.string.verification_creation_parameters_title).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        itemsIndexed(state.attributes, key = { _, attr -> attr.key }) { _, attribute ->
            AttributeEditorCard(
                attribute = attribute,
                onToggle = { selected -> onEventSent(Event.ToggleAttribute(attribute.key, selected)) },
                onExpectedValueChanged = { value ->
                    onEventSent(Event.UpdateExpectedValue(attribute.key, value))
                }
            )
        }

        // Recipients section
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

        // Error
        if (state.error != null) {
            item {
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        // Gradient submit button
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF1D4ED8), Color(0xFF3B82F6))
                        )
                    )
                    .clickable(enabled = !state.isLoading) { onEventSent(Event.CreateSession) },
                contentAlignment = Alignment.Center
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = stringResource(R.string.verification_creation_submit_button),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun PurposeCard(
    purpose: String,
    onPurposeChanged: (String) -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "PURPOSE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            BasicTextField(
                value = purpose,
                onValueChange = onPurposeChanged,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (purpose.isEmpty()) {
                        Text(
                            text = "e.g. Verify customer is of legal age",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    inner()
                }
            )
            Text(
                text = "Shown to the recipient when they open the request",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun AttributeEditorCard(
    attribute: VerificationDraftAttribute,
    onToggle: (Boolean) -> Unit,
    onExpectedValueChanged: (String) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (attribute.selected) MaterialTheme.colorScheme.surfaceContainer
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        border = BorderStroke(
            width = 1.dp,
            color = if (attribute.selected) primary.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
        ),
        shadowElevation = if (attribute.selected) 2.dp else 0.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Custom toggle box
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (attribute.selected) primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                        .border(
                            width = 1.5.dp,
                            color = if (attribute.selected) Color.Transparent
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = ripple(bounded = true, color = primary.copy(alpha = 0.3f))
                        ) { onToggle(!attribute.selected) },
                    contentAlignment = Alignment.Center
                ) {
                    if (attribute.selected) {
                        WrapIcon(
                            iconData = AppIcons.Check,
                            customTint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = attribute.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (attribute.selected) onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = attribute.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (attribute.selected) 0.8f else 0.5f
                        )
                    )
                }
            }

            // Expected value field — animated expand inside the card
            AnimatedVisibility(
                visible = attribute.selected && attribute.requiresExpectedValue,
                enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(150)) + fadeOut(tween(150))
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Expected value (optional)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        BasicTextField(
                            value = attribute.expectedValue,
                            onValueChange = onExpectedValueChanged,
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = onSurface),
                            cursorBrush = SolidColor(primary),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (attribute.expectedValue.isEmpty()) {
                                    Text(
                                        text = "Leave blank to accept any value",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                }
                                inner()
                            }
                        )
                    }
                }
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
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "RECIPIENTS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Segmented contact type selector — PremiumTabRow style
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(modifier = Modifier.padding(3.dp)) {
                VerificationRecipientContactType.entries.forEach { type ->
                    val isSelected = type == contactType
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onContactTypeSelected(type) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.surface
                                else Color.Transparent,
                        shadowElevation = if (isSelected) 2.dp else 0.dp
                    ) {
                        Text(
                            text = type.apiValue.replaceFirstChar { it.uppercase() },
                            modifier = Modifier.padding(vertical = 8.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Input row + add button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                BasicTextField(
                    value = recipientValue,
                    onValueChange = onRecipientValueChanged,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = onSurface),
                    cursorBrush = SolidColor(primary),
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    decorationBox = { inner ->
                        val placeholder = when (contactType) {
                            VerificationRecipientContactType.HANDLE -> "handle"
                            VerificationRecipientContactType.EMAIL -> "email@example.com"
                            VerificationRecipientContactType.PHONE -> "+1 555 0000"
                        }
                        if (recipientValue.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                        inner()
                    }
                )
            }
            WrapIconButton(
                iconData = AppIcons.Add,
                customTint = primary,
                onClick = onAddRecipient
            )
        }

        if (recipients.isEmpty()) {
            Text(
                text = "Optional — you can also share the QR code or link after creating",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recipients.forEachIndexed { index, recipient ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = recipient.value,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = recipient.contactType.apiValue
                                        .replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            WrapIconButton(
                                iconData = AppIcons.Delete,
                                customTint = MaterialTheme.colorScheme.error,
                                onClick = { onRemoveRecipient(index) }
                            )
                        }
                    }
                }
            }
        }
    }
}
