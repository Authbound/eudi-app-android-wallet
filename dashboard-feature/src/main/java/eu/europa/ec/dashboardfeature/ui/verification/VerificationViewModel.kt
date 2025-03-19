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

import androidx.lifecycle.viewModelScope
import eu.europa.ec.dashboardfeature.model.verification.ParameterType
import eu.europa.ec.dashboardfeature.model.verification.ValidationCriteria
import eu.europa.ec.dashboardfeature.model.verification.VerificationParameter
import eu.europa.ec.dashboardfeature.model.verification.VerificationSession
import eu.europa.ec.dashboardfeature.model.verification.VerificationTemplate
import eu.europa.ec.dashboardfeature.model.verification.VerificationTemplateType
import eu.europa.ec.dashboardfeature.repository.VerificationRepository
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import java.util.*

data class State(
    val isLoading: Boolean = false,
    val templates: List<VerificationTemplate> = emptyList(),
    val selectedTemplate: VerificationTemplate? = null,
    val currentParameters: List<VerificationParameter> = emptyList(),
    val verificationSession: VerificationSession? = null,
    val selectedParameterType: ParameterType? = null,
    val parameterEditMode: Boolean = false,
    val editingParameter: VerificationParameter? = null,
    val sessionDeepLink: String? = null,
    val qrCodeGenerated: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
) : ViewState

sealed class Event : ViewEvent {
    data object Init : Event()
    data object LoadTemplates : Event()
    data object NavigateBack : Event()
    data object NavigateHome : Event()
    
    // Template selection
    data class SelectTemplate(val template: VerificationTemplate) : Event()
    
    // Parameter management
    data object AddParameter : Event()
    data class SelectParameterType(val type: ParameterType) : Event()
    data class SaveParameter(
        val label: String,
        val description: String,
        val isRequired: Boolean,
        val validationCriteria: ValidationCriteria?
    ) : Event()
    data class EditParameter(val parameter: VerificationParameter) : Event()
    data class DeleteParameter(val parameterId: String) : Event()
    
    // Session creation
    data class CreateSession(val title: String, val description: String) : Event()
    
    // Sharing
    data object GenerateQrCode : Event()
    data object CopyLink : Event()
    data object ShareLink : Event()
    data class ShareVia(val method: SharingMethod) : Event()
}

enum class SharingMethod {
    WHATSAPP,
    EMAIL,
    MESSAGES
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data class SwitchScreen(
            val screenRoute: String,
            val popUpToScreenRoute: String = DashboardScreens.Dashboard.screenRoute,
            val inclusive: Boolean = false,
        ) : Navigation()
        
        data object Back : Navigation()
        data object Home : Navigation()
    }
    
    data class ShowToast(val message: String) : Effect()
    data class ShareContent(val title: String, val content: String) : Effect()
}

@KoinViewModel
class VerificationViewModel(
    private val verificationRepository: VerificationRepository,
    private val resourceProvider: ResourceProvider
) : MviViewModel<Event, State, Effect>() {
    
    private val userId = "current_user" // In a real app, get from auth system
    
    override fun setInitialState(): State = State(isLoading = true)
    
    override fun handleEvents(event: Event) {
        when (event) {
            is Event.Init -> {
                loadTemplates()
            }
            
            is Event.LoadTemplates -> {
                loadTemplates()
            }
            
            is Event.NavigateBack -> {
                setEffect { Effect.Navigation.Back }
            }
            
            is Event.NavigateHome -> {
                setEffect { Effect.Navigation.Home }
            }
            
            is Event.SelectTemplate -> {
                selectTemplate(event.template)
            }
            
            is Event.AddParameter -> {
                setState { copy(parameterEditMode = true, editingParameter = null) }
            }
            
            is Event.SelectParameterType -> {
                setState { copy(selectedParameterType = event.type) }
            }
            
            is Event.SaveParameter -> {
                saveParameter(event.label, event.description, event.isRequired, event.validationCriteria)
            }
            
            is Event.EditParameter -> {
                setState { 
                    copy(
                        parameterEditMode = true, 
                        editingParameter = event.parameter,
                        selectedParameterType = event.parameter.type
                    )
                }
            }
            
            is Event.DeleteParameter -> {
                deleteParameter(event.parameterId)
            }
            
            is Event.CreateSession -> {
                createSession(event.title, event.description)
            }
            
            is Event.GenerateQrCode -> {
                setState { copy(qrCodeGenerated = true) }
            }
            
            is Event.CopyLink -> {
                val sessionDeepLink = viewState.value.sessionDeepLink
                if (sessionDeepLink != null) {
                    setEffect { Effect.ShowToast(resourceProvider.getString(R.string.verification_sharing_link_copied)) }
                }
            }
            
            is Event.ShareLink -> {
                val session = viewState.value.verificationSession
                val deepLink = viewState.value.sessionDeepLink
                if (session != null && deepLink != null) {
                    val title = resourceProvider.getString(R.string.verification_sharing_title)
                    val content = "${session.title}\n${session.description}\n\n$deepLink"
                    setEffect { Effect.ShareContent(title, content) }
                }
            }
            
            is Event.ShareVia -> {
                // In a real app, this would integrate with the sharing APIs
                // For now, we just show a toast
                setEffect { 
                    Effect.ShowToast(
                        "Shared via ${event.method.name.lowercase()}"
                    ) 
                }
            }
        }
    }
    
    private fun loadTemplates() {
        setState { copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val templates = verificationRepository.getVerificationTemplates()
                setState { 
                    copy(
                        isLoading = false, 
                        templates = templates,
                        error = null
                    ) 
                }
            } catch (e: Exception) {
                setState { 
                    copy(
                        isLoading = false, 
                        error = e.message ?: resourceProvider.genericErrorMessage()
                    ) 
                }
            }
        }
    }
    
    private fun selectTemplate(template: VerificationTemplate) {
        setState { 
            copy(
                selectedTemplate = template,
                currentParameters = template.parameters.toList()
            ) 
        }
        
        if (template.type == VerificationTemplateType.CUSTOM) {
            // For custom template, proceed to custom creation screen where user can add parameters
            navigateToCustomVerification()
        } else {
            // For pre-defined templates, create a session automatically and go directly to sharing
            val title = template.title
            val description = template.description
            createSessionForTemplate(title, description)
        }
    }
    
    private fun createSessionForTemplate(title: String, description: String) {
        setState { copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val session = VerificationSession(
                    title = title,
                    description = description,
                    creatorId = userId,
                    parameters = viewState.value.currentParameters,
                    deepLink = "https://wallet.example.com/verify/${UUID.randomUUID().toString().take(8)}"
                )
                
                verificationRepository.saveVerificationSession(session)
                
                setState { 
                    copy(
                        isLoading = false,
                        verificationSession = session,
                        sessionDeepLink = session.deepLink,
                        success = true
                    ) 
                }
                
                // Navigate to sharing screen
                navigateToSharingScreen()
            } catch (e: Exception) {
                setState { 
                    copy(
                        isLoading = false,
                        error = e.message ?: resourceProvider.genericErrorMessage()
                    ) 
                }
            }
        }
    }
    
    private fun saveParameter(
        label: String,
        description: String,
        isRequired: Boolean,
        validationCriteria: ValidationCriteria?
    ) {
        val selectedType = viewState.value.selectedParameterType ?: return
        val editingParameter = viewState.value.editingParameter
        
        val newParameter = if (editingParameter != null) {
            editingParameter.copy(
                label = label,
                description = description,
                isRequired = isRequired,
                validationCriteria = validationCriteria
            )
        } else {
            VerificationParameter(
                type = selectedType,
                label = label,
                description = description,
                isRequired = isRequired,
                validationCriteria = validationCriteria
            )
        }
        
        // If editing, replace the parameter; otherwise, add it
        val updatedParameters = if (editingParameter != null) {
            viewState.value.currentParameters.map {
                if (it.id == editingParameter.id) newParameter else it
            }
        } else {
            viewState.value.currentParameters + newParameter
        }
        
        setState { 
            copy(
                currentParameters = updatedParameters,
                parameterEditMode = false,
                editingParameter = null,
                selectedParameterType = null
            ) 
        }
    }
    
    private fun deleteParameter(parameterId: String) {
        setState { 
            copy(
                currentParameters = viewState.value.currentParameters.filter { it.id != parameterId }
            ) 
        }
    }
    
    private fun createSession(title: String, description: String) {
        // Reuse the createSessionForTemplate method
        createSessionForTemplate(title, description)
    }
    
    private fun navigateToCustomVerification() {
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = DashboardScreens.VerificationCustomCreation.screenRoute
            )
        }
    }
    
    private fun navigateToParametersScreen() {
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = DashboardScreens.VerificationCustomCreation.screenRoute
            )
        }
    }
    
    private fun navigateToSharingScreen() {
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = DashboardScreens.VerificationSharing.screenRoute
            )
        }
    }
} 