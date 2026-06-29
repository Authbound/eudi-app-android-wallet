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

package eu.europa.ec.presentationfeature.ui.request

import androidx.lifecycle.viewModelScope
import eu.europa.ec.businesslogic.extension.ifEmptyOrNull
import eu.europa.ec.commonfeature.config.PresentationMode
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.commonfeature.ui.request.Event
import eu.europa.ec.commonfeature.ui.request.RequestViewModel
import eu.europa.ec.commonfeature.ui.request.model.RequestDocumentItemUi
import eu.europa.ec.presentationfeature.interactor.PresentationRequestInteractor
import eu.europa.ec.presentationfeature.interactor.PresentationRequestInteractorPartialState
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.RelyingPartyDataUi
import eu.europa.ec.uilogic.component.content.ContentErrorConfig
import eu.europa.ec.uilogic.component.content.ContentHeaderConfig
import eu.europa.ec.uilogic.navigation.PresentationScreens
import eu.europa.ec.uilogic.navigation.helper.IntentAction
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import eu.europa.ec.uilogic.serializer.UiSerializer
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam

@KoinViewModel
class PresentationRequestViewModel(
    private val interactor: PresentationRequestInteractor,
    private val resourceProvider: ResourceProvider,
    private val uiSerializer: UiSerializer,
    @InjectedParam private val requestUriConfigRaw: String
) : RequestViewModel() {

    override fun getHeaderConfig(): ContentHeaderConfig {
        return ContentHeaderConfig(
            description = resourceProvider.getString(R.string.request_header_description),
            mainText = resourceProvider.getString(R.string.request_header_main_text),
            relyingPartyData = getRelyingPartyData(
                name = null,
                isVerified = false,
            ),
        )
    }

    override fun getNextScreen(): String {
        return generateComposableNavigationLink(
            screen = PresentationScreens.PresentationLoading,
            arguments = generateComposableArguments(
                mapOf("scopeId" to viewState.value.presentationScopeId)
            )
        )
    }

    override fun doWork() {
        setState {
            copy(
                isLoading = true,
                error = null
            )
        }

        val requestUriConfig: RequestUriConfig = uiSerializer.fromBase64(
            requestUriConfigRaw,
            RequestUriConfig::class.java,
            RequestUriConfig.Parser
        ) ?: throw RuntimeException("RequestUriConfig:: is Missing or invalid")

        setState {
            copy(presentationScopeId = requestUriConfig.presentationScopeId)
        }

        if (requestUriConfig.mode is PresentationMode.DcApi && viewState.value.intentAction == null) {
            setState {
                copy(
                    isLoading = false,
                    error = ContentErrorConfig(
                        onRetry = { setEvent(Event.DoWork) },
                        errorSubTitle = resourceProvider.genericErrorMessage(),
                        onCancel = { setEvent(Event.Pop) }
                    )
                )
            }
            return
        }

        interactor.setConfig(requestUriConfig, viewState.value.intentAction)

        viewModelJob = viewModelScope.launch {
            interactor.getRequestDocuments().collect { response ->
                when (response) {
                    is PresentationRequestInteractorPartialState.Failure -> {
                        setState {
                            copy(
                                isLoading = false,
                                error = ContentErrorConfig(
                                    onRetry = { setEvent(Event.DoWork) },
                                    errorSubTitle = response.error,
                                    onCancel = { setEvent(Event.Pop) }
                                )
                            )
                        }
                    }

                    is PresentationRequestInteractorPartialState.Success -> {
                        updateData(response.requestDocuments)

                        val updatedHeaderConfig = viewState.value.headerConfig.copy(
                            relyingPartyData = getRelyingPartyData(
                                name = response.verifierName,
                                isVerified = response.verifierIsTrusted,
                            )
                        )

                        setState {
                            copy(
                                isLoading = false,
                                error = null,
                                headerConfig = updatedHeaderConfig,
                                items = response.requestDocuments,
                            )
                        }
                    }

                    is PresentationRequestInteractorPartialState.Disconnect -> {
                        setEvent(Event.Pop)
                    }

                    is PresentationRequestInteractorPartialState.NoData -> {
                        val updatedHeaderConfig = viewState.value.headerConfig.copy(
                            relyingPartyData = getRelyingPartyData(
                                name = response.verifierName,
                                isVerified = response.verifierIsTrusted,
                            )
                        )

                        setState {
                            copy(
                                isLoading = false,
                                error = null,
                                headerConfig = updatedHeaderConfig,
                                noItems = true,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun init(intentAction: IntentAction?) {
        setState {
            copy(intentAction = intentAction)
        }
    }

    override fun updateData(
        updatedItems: List<RequestDocumentItemUi>,
        allowShare: Boolean?
    ) {
        super.updateData(updatedItems, allowShare)
        interactor.updateRequestedDocuments(updatedItems)
    }

    override fun cleanUp() {
        interactor.stopPresentation()
        super.cleanUp()
    }

    private fun getRelyingPartyData(
        name: String?,
        isVerified: Boolean,
    ): RelyingPartyDataUi {
        return RelyingPartyDataUi(
            isVerified = isVerified,
            name = name.ifEmptyOrNull(
                default = resourceProvider.getString(R.string.request_relying_party_default_name)
            ),
            description = resourceProvider.getString(R.string.request_relying_party_description),
        )
    }
}
