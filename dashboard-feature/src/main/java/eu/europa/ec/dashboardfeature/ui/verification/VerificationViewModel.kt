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

import androidx.lifecycle.viewModelScope
import eu.europa.ec.dashboardfeature.model.verification.VerificationAttributeDefinition
import eu.europa.ec.dashboardfeature.model.verification.VerificationDraftAttribute
import eu.europa.ec.dashboardfeature.model.verification.VerificationRecipient
import eu.europa.ec.dashboardfeature.model.verification.VerificationRecipientContactType
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
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

data class State(
    val isLoading: Boolean = false,
    val templates: List<VerificationTemplate> = emptyList(),
    val selectedTemplate: VerificationTemplate? = null,
    val purpose: String = "",
    val attributes: List<VerificationDraftAttribute> = emptyList(),
    val recipients: List<VerificationRecipient> = emptyList(),
    val recipientValue: String = "",
    val recipientContactType: VerificationRecipientContactType = VerificationRecipientContactType.HANDLE,
    val error: String? = null,
) : ViewState

sealed class Event : ViewEvent {
    data object Init : Event()
    data class InitializeTemplate(val type: VerificationTemplateType) : Event()
    data class SelectTemplate(val template: VerificationTemplate) : Event()
    data class UpdatePurpose(val value: String) : Event()
    data class ToggleAttribute(val key: String, val selected: Boolean) : Event()
    data class UpdateExpectedValue(val key: String, val value: String) : Event()
    data class UpdateRecipientValue(val value: String) : Event()
    data class UpdateRecipientContactType(val type: VerificationRecipientContactType) : Event()
    data object AddRecipient : Event()
    data class RemoveRecipient(val index: Int) : Event()
    data object CreateSession : Event()
    data object NavigateBack : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data class SwitchScreen(
            val screenRoute: String,
            val popUpToScreenRoute: String = DashboardScreens.Dashboard.screenRoute,
            val inclusive: Boolean = false,
        ) : Navigation()

        data object Back : Navigation()
    }

    data class ShowToast(val message: String) : Effect()
}

@KoinViewModel
class VerificationViewModel(
    private val verificationRepository: VerificationRepository,
    private val resourceProvider: ResourceProvider
) : MviViewModel<Event, State, Effect>() {

    override fun setInitialState(): State = State(isLoading = true)

    override fun handleEvents(event: Event) {
        when (event) {
            Event.Init -> loadTemplates()
            is Event.InitializeTemplate -> initializeTemplate(event.type, navigate = false)
            is Event.SelectTemplate -> initializeTemplate(event.template.type, navigate = true)
            is Event.UpdatePurpose -> setState { copy(purpose = event.value) }
            is Event.ToggleAttribute -> toggleAttribute(event.key, event.selected)
            is Event.UpdateExpectedValue -> updateExpectedValue(event.key, event.value)
            is Event.UpdateRecipientValue -> setState { copy(recipientValue = event.value) }
            is Event.UpdateRecipientContactType -> setState { copy(recipientContactType = event.type) }
            Event.AddRecipient -> addRecipient()
            is Event.RemoveRecipient -> removeRecipient(event.index)
            Event.CreateSession -> createSession()
            Event.NavigateBack -> setEffect { Effect.Navigation.Back }
        }
    }

    private fun loadTemplates() {
        setState { copy(isLoading = true) }
        viewModelScope.launch {
            val templates = verificationRepository.getVerificationTemplates()
            setState {
                copy(
                    isLoading = false,
                    templates = templates,
                    error = null
                )
            }
        }
    }

    private fun attributeDefinitions(): List<VerificationAttributeDefinition> = listOf(
        VerificationAttributeDefinition(
            key = "age_over_18",
            label = resourceProvider.getString(R.string.verification_parameter_age),
            description = resourceProvider.getString(R.string.verification_parameter_age_description),
            requiresExpectedValue = false
        ),
        VerificationAttributeDefinition(
            key = "full_name",
            label = resourceProvider.getString(R.string.verification_parameter_name),
            description = resourceProvider.getString(R.string.verification_parameter_name_description),
            requiresExpectedValue = true
        ),
        VerificationAttributeDefinition(
            key = "date_of_birth",
            label = resourceProvider.getString(R.string.verification_parameter_date_of_birth),
            description = resourceProvider.getString(R.string.verification_parameter_date_of_birth_description),
            requiresExpectedValue = true
        ),
        VerificationAttributeDefinition(
            key = "nationality",
            label = resourceProvider.getString(R.string.verification_parameter_nationality),
            description = resourceProvider.getString(R.string.verification_parameter_nationality_description),
            requiresExpectedValue = false
        ),
        VerificationAttributeDefinition(
            key = "personal_id",
            label = resourceProvider.getString(R.string.verification_parameter_document_number),
            description = resourceProvider.getString(R.string.verification_parameter_document_number_description),
            requiresExpectedValue = true
        ),
        VerificationAttributeDefinition(
            key = "address",
            label = "Address",
            description = "Residential address from the holder's credential",
            requiresExpectedValue = false
        ),
        VerificationAttributeDefinition(
            key = "phone",
            label = "Phone",
            description = "Phone number from the holder's credential",
            requiresExpectedValue = false
        )
    )

    private fun initializeTemplate(
        templateType: VerificationTemplateType,
        navigate: Boolean,
    ) {
        val template = viewState.value.templates.firstOrNull { it.type == templateType } ?: return
        val selectedKeys = template.attributes.toSet()
        val attributes = attributeDefinitions().map { definition ->
            VerificationDraftAttribute(
                key = definition.key,
                label = definition.label,
                description = definition.description,
                requiresExpectedValue = definition.requiresExpectedValue,
                selected = if (template.type == VerificationTemplateType.CUSTOM) false else definition.key in selectedKeys
            )
        }

        setState {
            copy(
                selectedTemplate = template,
                purpose = template.purpose,
                attributes = attributes,
                recipients = emptyList(),
                recipientValue = "",
                error = null
            )
        }

        if (navigate) {
            setEffect {
                Effect.Navigation.SwitchScreen(
                    screenRoute = generateComposableNavigationLink(
                        screen = DashboardScreens.VerificationCustomCreation,
                        arguments = generateComposableArguments(
                            mapOf("templateType" to template.type.name)
                        )
                    ),
                    popUpToScreenRoute = DashboardScreens.VerificationTemplateSelection.screenRoute,
                    inclusive = false
                )
            }
        }
    }

    private fun toggleAttribute(key: String, selected: Boolean) {
        setState {
            copy(
                attributes = attributes.map { attribute ->
                    if (attribute.key == key) attribute.copy(selected = selected) else attribute
                }
            )
        }
    }

    private fun updateExpectedValue(key: String, value: String) {
        setState {
            copy(
                attributes = attributes.map { attribute ->
                    if (attribute.key == key) attribute.copy(expectedValue = value) else attribute
                }
            )
        }
    }

    private fun addRecipient() {
        val normalizedValue = normalizeRecipientValue(
            type = viewState.value.recipientContactType,
            value = viewState.value.recipientValue
        )

        if (normalizedValue.isBlank()) {
            setEffect { Effect.ShowToast("Enter a recipient value") }
            return
        }

        val newRecipient = VerificationRecipient(
            contactType = viewState.value.recipientContactType,
            value = normalizedValue
        )

        val alreadyExists = viewState.value.recipients.any {
            it.contactType == newRecipient.contactType && it.value == newRecipient.value
        }
        if (alreadyExists) {
            setEffect { Effect.ShowToast("Recipient already added") }
            return
        }

        setState {
            copy(
                recipients = recipients + newRecipient,
                recipientValue = ""
            )
        }
    }

    private fun normalizeRecipientValue(
        type: VerificationRecipientContactType,
        value: String
    ): String {
        val trimmed = value.trim()
        return when (type) {
            VerificationRecipientContactType.HANDLE -> trimmed.removePrefix("@").lowercase()
            VerificationRecipientContactType.EMAIL -> trimmed.lowercase()
            VerificationRecipientContactType.PHONE -> trimmed
        }
    }

    private fun removeRecipient(index: Int) {
        setState {
            copy(
                recipients = recipients.filterIndexed { currentIndex, _ -> currentIndex != index }
            )
        }
    }

    private fun createSession() {
        val state = viewState.value
        val selectedAttributes = state.attributes.filter { it.selected }
        if (selectedAttributes.isEmpty()) {
            setEffect { Effect.ShowToast("Select at least one attribute") }
            return
        }
        if (state.purpose.isBlank()) {
            setEffect { Effect.ShowToast("Enter a purpose for this verification") }
            return
        }

        setState { copy(isLoading = true, error = null) }
        viewModelScope.launch {
            verificationRepository.createVerificationSession(
                purpose = state.purpose,
                attributes = state.attributes,
                recipients = state.recipients
            ).fold(
                onSuccess = { session ->
                    setState { copy(isLoading = false) }
                    setEffect {
                        Effect.Navigation.SwitchScreen(
                            screenRoute = generateComposableNavigationLink(
                                screen = DashboardScreens.VerificationSharing,
                                arguments = generateComposableArguments(
                                    mapOf("sessionId" to session.id)
                                )
                            ),
                            popUpToScreenRoute = DashboardScreens.VerificationTemplateSelection.screenRoute,
                            inclusive = false
                        )
                    }
                },
                onFailure = { error ->
                    setState {
                        copy(
                            isLoading = false,
                            error = error.message ?: resourceProvider.genericErrorMessage()
                        )
                    }
                }
            )
        }
    }
}
