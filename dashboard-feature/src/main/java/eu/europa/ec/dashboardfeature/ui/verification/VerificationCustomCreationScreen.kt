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
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.dashboardfeature.ui.verification

import android.content.Context
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.dashboardfeature.model.verification.ParameterType
import eu.europa.ec.dashboardfeature.model.verification.ValidationCriteria
import eu.europa.ec.dashboardfeature.model.verification.VerificationParameter
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ListItemData
import eu.europa.ec.uilogic.component.ListItemLeadingContentData
import eu.europa.ec.uilogic.component.ListItemMainContentData
import eu.europa.ec.uilogic.component.ListItemTrailingContentData
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.utils.OneTimeLaunchedEffect
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapIconButton
import eu.europa.ec.uilogic.component.wrap.WrapListItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach

@Composable
fun VerificationCustomCreationScreen(
    navController: NavController,
    viewModel: VerificationViewModel
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    ContentScreen(
        isLoading = false,
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack = { navController.popBackStack() },
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 16.dp,
                        vertical = 12.dp
                    )
            ) {
                Text(
                    modifier = Modifier.align(Alignment.Center),
                    text = stringResource(R.string.verification_creation_custom_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    ) { paddingValues ->
        CustomCreationContent(
            state = state,
            effectFlow = viewModel.effect,
            onEventSent = { event -> viewModel.setEvent(event) },
            onNavigationRequested = { navigationEffect ->
                handleNavigationEffect(context, navigationEffect, navController)
            },
            paddingValues = paddingValues
        )
    }

    OneTimeLaunchedEffect {
        viewModel.setEvent(Event.LoadTemplates)
    }
}

@Composable
private fun CustomCreationContent(
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

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // Title and description fields
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = title,
                onValueChange = { title = it },
                label = { Text("Verification Title") },
                singleLine = true
            )
            
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                maxLines = 3
            )
            
            // Parameters section
            Text(
                text = stringResource(R.string.verification_creation_parameters_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            if (state.parameterEditMode) {
                // Parameter editor
                ParameterEditor(
                    selectedType = state.selectedParameterType,
                    editingParameter = state.editingParameter,
                    onSelectParameterType = { parameterType ->
                        onEventSent(Event.SelectParameterType(parameterType))
                    },
                    onSaveParameter = { label, description, isRequired, validationCriteria ->
                        onEventSent(Event.SaveParameter(label, description, isRequired, validationCriteria))
                    },
                    onCancel = { onEventSent(Event.NavigateBack) }
                )
            } else {
                // Parameter list
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.currentParameters) { parameter ->
                        ParameterItem(
                            parameter = parameter,
                            onEdit = { onEventSent(Event.EditParameter(parameter)) },
                            onDelete = { onEventSent(Event.DeleteParameter(parameter.id)) }
                        )
                    }
                    
                    item {
                        // Add parameter button
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onEventSent(Event.AddParameter) }
                        ) {
                            WrapIcon(iconData = AppIcons.Add)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.verification_creation_add_parameter))
                        }
                    }
                }
                
                // Continue button
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { 
                        if (title.isNotBlank() && state.currentParameters.isNotEmpty()) {
                            onEventSent(Event.CreateSession(title, description))
                        }
                    },
                    enabled = title.isNotBlank() && state.currentParameters.isNotEmpty()
                ) {
                    Text(text = stringResource(R.string.verification_creation_save))
                }
            }
        }
    }
}

@Composable
private fun ParameterItem(
    parameter: VerificationParameter,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = parameter.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Row {
                    WrapIconButton(
                        iconData = AppIcons.Edit,
                        onClick = onEdit
                    )
                    WrapIconButton(
                        iconData = AppIcons.Delete,
                        onClick = onDelete
                    )
                }
            }
            
            Text(
                text = parameter.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Divider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Type: ${parameter.type.name.lowercase().capitalize()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                Text(
                    text = if (parameter.isRequired) "Required" else "Optional",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (parameter.isRequired) 
                        MaterialTheme.colorScheme.error 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParameterEditor(
    selectedType: ParameterType?,
    editingParameter: VerificationParameter?,
    onSelectParameterType: (ParameterType) -> Unit,
    onSaveParameter: (String, String, Boolean, ValidationCriteria?) -> Unit,
    onCancel: () -> Unit
) {
    var label by remember { mutableStateOf(editingParameter?.label ?: "") }
    var description by remember { mutableStateOf(editingParameter?.description ?: "") }
    var isRequired by remember { mutableStateOf(editingParameter?.isRequired ?: true) }
    
    val types = remember { ParameterType.values().toList() }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (editingParameter != null) "Edit Parameter" else "Add Parameter",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // Parameter type selection
            if (selectedType == null) {
                Text(
                    text = "Select parameter type:",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Column {
                    types.forEach { type ->
                        WrapListItem(
                            item = ListItemData(
                                itemId = type.name,
                                mainContentData = ListItemMainContentData.Text(
                                    text = type.name.lowercase().capitalize()
                                ),
                                leadingContentData = ListItemLeadingContentData.Icon(
                                    iconData = when (type) {
                                        ParameterType.AGE -> AppIcons.WalletActivated
                                        ParameterType.NAME -> AppIcons.User
                                        ParameterType.DATE_OF_BIRTH -> AppIcons.ClockTimer
                                        ParameterType.NATIONALITY -> AppIcons.Certified
                                        ParameterType.DOCUMENT_TYPE -> AppIcons.Documents
                                        ParameterType.DOCUMENT_NUMBER -> AppIcons.Id
                                        ParameterType.EXPIRY_DATE -> AppIcons.ClockTimer
                                        ParameterType.CUSTOM -> AppIcons.Edit
                                    }
                                ),
                                trailingContentData = ListItemTrailingContentData.Icon(
                                    iconData = AppIcons.KeyboardArrowRight
                                )
                            ),
                            onItemClick = { onSelectParameterType(type) }
                        )
                    }
                }
            } else {
                // Parameter details form
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true
                )
                
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    maxLines = 3
                )
                
                // Required checkbox
                WrapListItem(
                    item = ListItemData(
                        itemId = "required",
                        mainContentData = ListItemMainContentData.Text(
                            text = "Required"
                        ),
                        trailingContentData = ListItemTrailingContentData.Checkbox(
                            checkboxData = eu.europa.ec.uilogic.component.wrap.CheckboxData(
                                isChecked = isRequired,
                                onCheckedChange = { isRequired = it }
                            )
                        )
                    ),
                    onItemClick = { isRequired = !isRequired }
                )
                
                // Todo: Add validation criteria editor based on parameter type
                
                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onCancel
                    ) {
                        Text(stringResource(id = R.string.verification_navigation_cancel))
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = {
                            // Currently no validation criteria support
                            onSaveParameter(label, description, isRequired, null)
                        },
                        enabled = label.isNotBlank()
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

private fun handleNavigationEffect(
    context: Context,
    navigationEffect: Effect.Navigation,
    navController: NavController
) {
    when (navigationEffect) {
        is Effect.Navigation.SwitchScreen -> {
            navController.navigate(navigationEffect.screenRoute) {
                popUpTo(navigationEffect.popUpToScreenRoute) {
                    inclusive = navigationEffect.inclusive
                }
            }
        }
        
        is Effect.Navigation.Back -> {
            navController.popBackStack()
        }
        
        is Effect.Navigation.Home -> {
            navController.popBackStack()
        }
    }
}

private fun String.capitalize(): String {
    return this.replaceFirstChar { it.uppercase() }
} 