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

package eu.europa.ec.dashboardfeature.ui.mydata

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.dashboardfeature.ui.mydata.model.DataCategoryUi
import eu.europa.ec.dashboardfeature.ui.mydata.model.PersonalDataField
import eu.europa.ec.dashboardfeature.ui.mydata.model.VerificationStatus
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.content.ToolbarConfig
import eu.europa.ec.uilogic.component.utils.LifecycleEffect
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach

@Composable
fun MyDataScreen(
    navController: NavController,
    viewModel: MyDataViewModel
) {
    val state by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleEffect(
        lifecycleOwner = LocalLifecycleOwner.current,
        lifecycleEvent = Lifecycle.Event.ON_CREATE
    ) {
        viewModel.setEvent(Event.Init)
    }

    ContentScreen(
        isLoading = state.isLoading,
        navigatableAction = ScreenNavigateAction.BACKABLE,
        toolBarConfig = ToolbarConfig(
            title = stringResource(R.string.my_data_screen_title),
        ),
        contentErrorConfig = state.error,
        onBack = { viewModel.setEvent(Event.Pop) }
    ) { paddingValues ->
        MyDataContent(
            state = state,
            paddingValues = paddingValues,
            onEventSent = { viewModel.setEvent(it) }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.effect.onEach { effect ->
            when (effect) {
                is Effect.Navigation.Pop -> navController.popBackStack()
                is Effect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }.collect()
    }
}

@Composable
private fun MyDataContent(
    state: State,
    paddingValues: PaddingValues,
    onEventSent: (Event) -> Unit
) {
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            delay(100)
            showContent = true
        }
    }

    AnimatedVisibility(
        visible = showContent,
        enter = fadeIn(tween(300))
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(
                horizontal = SPACING_MEDIUM.dp,
                vertical = SPACING_MEDIUM.dp
            ),
            verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
        ) {
            // Description
            item {
                Text(
                    text = stringResource(R.string.my_data_screen_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                VSpacer.Medium()
            }

            // Categories
            state.categories.forEachIndexed { categoryIndex, category ->
                item(key = "category_${category.category.name}") {
                    DataCategoryCard(
                        category = category,
                        animationDelay = categoryIndex * 100,
                        onFieldClick = { fieldId -> onEventSent(Event.FieldClicked(fieldId)) },
                        onVerifyClick = { fieldId -> onEventSent(Event.VerifyFieldClicked(fieldId)) }
                    )
                }
            }

            // Coming Soon Card
            item {
                VSpacer.Medium()
                ComingSoonCard()
            }

            item {
                VSpacer.Large()
            }
        }
    }
}

@Composable
private fun DataCategoryCard(
    category: DataCategoryUi,
    animationDelay: Int = 0,
    onFieldClick: (String) -> Unit,
    onVerifyClick: (String) -> Unit
) {
    var isVisible by remember { mutableStateOf(animationDelay == 0) }

    LaunchedEffect(Unit) {
        if (animationDelay > 0) {
            delay(animationDelay.toLong())
            isVisible = true
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 }
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(modifier = Modifier.padding(SPACING_MEDIUM.dp)) {
                // Category header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        WrapIcon(
                            iconData = category.icon,
                            customTint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = category.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                VSpacer.Medium()

                // Fields
                category.fields.forEachIndexed { index, field ->
                    DataFieldItem(
                        field = field,
                        onClick = { onFieldClick(field.id) },
                        onVerifyClick = { onVerifyClick(field.id) }
                    )

                    if (index < category.fields.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = SPACING_SMALL.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DataFieldItem(
    field: PersonalDataField,
    onClick: () -> Unit,
    onVerifyClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = field.isEditable) { onClick() }
            .padding(vertical = SPACING_SMALL.dp)
    ) {
        // Label
        Text(
            text = field.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Value
        Text(
            text = field.value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Status row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Status indicator
            val (statusColor, statusIcon, statusText) = when (field.status) {
                VerificationStatus.VERIFIED -> Triple(
                    Color(0xFF4CAF50),
                    AppIcons.Verified,
                    stringResource(R.string.my_data_status_verified)
                )
                VerificationStatus.SELF_DECLARED -> Triple(
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    null,
                    stringResource(R.string.my_data_status_self_declared)
                )
                VerificationStatus.PENDING -> Triple(
                    Color(0xFFFFA726),
                    AppIcons.ClockTimer,
                    stringResource(R.string.my_data_status_pending)
                )
            }

            if (statusIcon != null) {
                WrapIcon(
                    iconData = statusIcon,
                    customTint = statusColor,
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(statusColor)
                )
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor
            )

            // Source credential
            if (!field.sourceCredential.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.my_data_source_from, field.sourceCredential),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Verify button for self-declared fields
            if (field.status == VerificationStatus.SELF_DECLARED) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.my_data_button_verify),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onVerifyClick() }
                )
            }
        }
    }
}

@Composable
private fun ComingSoonCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier.padding(SPACING_LARGE.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            WrapIcon(
                iconData = AppIcons.Info,
                customTint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )

            VSpacer.Small()

            Text(
                text = stringResource(R.string.my_data_coming_soon_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            VSpacer.Small()

            Text(
                text = stringResource(R.string.my_data_coming_soon_form_fill),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            VSpacer.ExtraSmall()

            Text(
                text = stringResource(R.string.my_data_coming_soon_form_fill_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            VSpacer.Medium()

            Button(
                onClick = { /* Future: Navigate to learn more */ },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = stringResource(R.string.my_data_coming_soon_learn_more))
            }
        }
    }
}
