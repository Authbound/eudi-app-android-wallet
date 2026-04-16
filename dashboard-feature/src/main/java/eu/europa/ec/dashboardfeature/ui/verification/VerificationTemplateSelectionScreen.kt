/*
 * Copyright (c) 2023 European Commission
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
 * ANY KIND, either express or implied. See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package eu.europa.ec.dashboardfeature.ui.verification

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.dashboardfeature.model.verification.VerificationDraftAttribute
import eu.europa.ec.dashboardfeature.model.verification.VerificationTemplate
import eu.europa.ec.dashboardfeature.model.verification.VerificationTemplateType
import eu.europa.ec.dashboardfeature.ui.component.NotificationIconButton
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.success
import eu.europa.ec.resourceslogic.theme.values.warning
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.utils.OneTimeLaunchedEffect
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapModalBottomSheet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationTemplateSelectionScreen(
    navController: NavController,
    viewModel: VerificationViewModel,
    notificationCount: Int = 0,
    onNotificationsClick: () -> Unit = {},
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val context: Context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ContentScreen(
        isLoading = state.isLoading && state.templates.isEmpty(),
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack = { navController.popBackStack() },
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    modifier = Modifier.align(Alignment.Center),
                    text = stringResource(R.string.verification_creation_template_selection_title),
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
        TemplateSelectionContent(
            state = state,
            effectFlow = viewModel.effect,
            onEventSent = { event -> viewModel.setEvent(event) },
            onNavigationRequested = { navigationEffect ->
                handleNavigationEffect(context, navigationEffect, navController)
            },
            paddingValues = paddingValues
        )
    }

    // Quick Confirm bottom sheet for pre-built templates
    state.confirmingTemplate?.let { template ->
        WrapModalBottomSheet(
            onDismissRequest = { viewModel.setEvent(Event.DismissConfirmSheet) },
            sheetState = sheetState
        ) {
            TemplateConfirmSheet(
                template = template,
                attributes = state.attributes,
                isLoading = state.isLoading,
                error = state.error,
                onConfirm = { viewModel.setEvent(Event.ConfirmCreateTemplate) }
            )
        }
    }

    OneTimeLaunchedEffect {
        viewModel.setEvent(Event.Init)
    }
}

@Composable
private fun TemplateSelectionContent(
    state: State,
    effectFlow: Flow<Effect>,
    onEventSent: (Event) -> Unit,
    onNavigationRequested: (Effect.Navigation) -> Unit,
    paddingValues: PaddingValues
) {
    LaunchedEffect(effectFlow) {
        effectFlow.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> onNavigationRequested(effect)
                else -> {}
            }
        }.collect()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        if (state.isLoading && state.templates.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (state.templates.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = stringResource(R.string.verification_template_selection_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.templates) { template ->
                        TemplateCard(
                            template = template,
                            onClick = { onEventSent(Event.SelectTemplate(template)) }
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = state.error ?: "No templates available",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun TemplateCard(
    template: VerificationTemplate,
    onClick: () -> Unit
) {
    val isCustom = template.type == VerificationTemplateType.CUSTOM
    val primary = MaterialTheme.colorScheme.primary
    val success = MaterialTheme.colorScheme.success
    val warning = MaterialTheme.colorScheme.warning

    val (iconBg, iconTint, attrColor) = when (template.type) {
        VerificationTemplateType.AGE_VERIFICATION ->
            Triple(success.copy(alpha = 0.15f), success, success)
        VerificationTemplateType.IDENTITY_VERIFICATION ->
            Triple(warning.copy(alpha = 0.15f), warning, warning)
        VerificationTemplateType.NATIONALITY_VERIFICATION ->
            Triple(primary.copy(alpha = 0.15f), primary, primary)
        VerificationTemplateType.CUSTOM ->
            Triple(primary.copy(alpha = 0.15f), primary, primary)
    }

    val icon = when (template.type) {
        VerificationTemplateType.AGE_VERIFICATION -> AppIcons.WalletActivated
        VerificationTemplateType.IDENTITY_VERIFICATION -> AppIcons.VerificationIdentity
        VerificationTemplateType.NATIONALITY_VERIFICATION -> AppIcons.VerificationNationality
        VerificationTemplateType.CUSTOM -> AppIcons.Edit
    }

    val attrLabel = when {
        template.attributes.isEmpty() -> "Custom attributes"
        template.attributes.size == 1 -> "1 attribute"
        else -> "${template.attributes.size} attributes"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (isCustom) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = if (isCustom) 1.5.dp else 1.dp,
            color = if (isCustom) primary.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        ),
        shadowElevation = if (isCustom) 0.dp else 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Coloured icon box
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(color = iconBg, shape = RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                WrapIcon(
                    iconData = icon,
                    customTint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Text block
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = template.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isCustom) primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = template.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = attrLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = attrColor
                )
            }

            WrapIcon(
                iconData = AppIcons.KeyboardArrowRight,
                customTint = if (isCustom) primary.copy(alpha = 0.6f)
                             else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateConfirmSheet(
    template: VerificationTemplate,
    attributes: List<VerificationDraftAttribute>,
    isLoading: Boolean,
    error: String?,
    onConfirm: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val success = MaterialTheme.colorScheme.success
    val warning = MaterialTheme.colorScheme.warning

    val (iconBg, iconTint) = when (template.type) {
        VerificationTemplateType.AGE_VERIFICATION ->
            Pair(success.copy(alpha = 0.15f), success)
        VerificationTemplateType.IDENTITY_VERIFICATION ->
            Pair(warning.copy(alpha = 0.15f), warning)
        VerificationTemplateType.NATIONALITY_VERIFICATION ->
            Pair(primary.copy(alpha = 0.15f), primary)
        VerificationTemplateType.CUSTOM ->
            Pair(primary.copy(alpha = 0.15f), primary)
    }

    val icon = when (template.type) {
        VerificationTemplateType.AGE_VERIFICATION -> AppIcons.WalletActivated
        VerificationTemplateType.IDENTITY_VERIFICATION -> AppIcons.VerificationIdentity
        VerificationTemplateType.NATIONALITY_VERIFICATION -> AppIcons.VerificationNationality
        VerificationTemplateType.CUSTOM -> AppIcons.Edit
    }

    val selectedAttributes = attributes.filter { it.selected }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header: icon + title + description
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(color = iconBg, shape = RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                WrapIcon(
                    iconData = icon,
                    customTint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = template.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = template.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Purpose card (read-only)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "PURPOSE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(
                        1f, androidx.compose.ui.unit.TextUnitType.Sp
                    )
                )
                Text(
                    text = template.purpose,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Attribute pills
        if (selectedAttributes.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "VERIFIES",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(
                        1f, androidx.compose.ui.unit.TextUnitType.Sp
                    )
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    selectedAttributes.forEach { attr ->
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = primary.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, primary.copy(alpha = 0.25f))
                        ) {
                            Text(
                                text = attr.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        // Error message
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        // Gradient CTA button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF1D4ED8), Color(0xFF3B82F6))
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
                .clickable(enabled = !isLoading, onClick = onConfirm),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = stringResource(R.string.verification_home_create_button),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}
