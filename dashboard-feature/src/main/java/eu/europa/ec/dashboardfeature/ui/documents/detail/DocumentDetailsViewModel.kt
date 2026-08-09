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

package eu.europa.ec.dashboardfeature.ui.documents.detail

import androidx.activity.ComponentActivity
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import eu.europa.ec.commonfeature.config.IssuanceFlowType
import eu.europa.ec.commonfeature.config.IssuanceUiConfig
import eu.europa.ec.commonfeature.config.PresentationMode
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.corelogic.model.FormatType
import eu.europa.ec.dashboardfeature.interactor.DocumentDetailsInteractor
import eu.europa.ec.dashboardfeature.interactor.HomeInteractorPresentIdPartialState
import eu.europa.ec.dashboardfeature.interactor.DocumentDetailsInteractorDeleteBookmarkPartialState
import eu.europa.ec.dashboardfeature.interactor.DocumentDetailsInteractorDeleteDocumentPartialState
import eu.europa.ec.dashboardfeature.interactor.DocumentDetailsInteractorPartialState
import eu.europa.ec.dashboardfeature.interactor.DocumentDetailsInteractorStoreBookmarkPartialState
import eu.europa.ec.dashboardfeature.ui.documents.detail.model.DocumentDetailsUi
import eu.europa.ec.dashboardfeature.ui.documents.detail.model.DocumentIssuanceStateUi
import eu.europa.ec.dashboardfeature.ui.documents.detail.transformer.DocumentDetailsTransformer.transformToDocumentDetailsUi
import eu.europa.ec.commonfeature.util.IdentityCardData
import eu.europa.ec.dashboardfeature.ui.documents.model.DocumentCredentialsInfoUi
import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.content.ContentErrorConfig
import eu.europa.ec.uilogic.component.wrap.BottomSheetTextDataUi
import eu.europa.ec.uilogic.extension.toggleExpansionState
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.ProximityScreens
import eu.europa.ec.uilogic.navigation.StartupScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import eu.europa.ec.uilogic.serializer.UiSerializer
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam
import java.net.URI

data class State(
    val isLoading: Boolean = true,
    val error: ContentErrorConfig? = null,
    val isBottomSheetOpen: Boolean = false,
    val isRevoked: Boolean = false,

    val documentDetailsUi: DocumentDetailsUi? = null,
    val identityCardData: IdentityCardData? = null,
    val title: String? = null,
    val issuerName: String? = null,
    val issuerLogo: URI? = null,
    val documentCredentialsInfoUi: DocumentCredentialsInfoUi? = null,
    val documentDetailsSectionTitle: String,
    val documentIssuerSectionTitle: String,

    val isDocumentBookmarked: Boolean = false,
    /** Full claim list is advanced/technical data; collapsed by default. */
    val areDocumentClaimsExpanded: Boolean = false,
    val presentIdQrCode: String = "",
    val presentIdPresentationScopeId: String = "",
    val isPresentIdHandoffInProgress: Boolean = false,

    val sheetContent: DocumentDetailsBottomSheetContent = DocumentDetailsBottomSheetContent.DeleteDocumentConfirmation,
) : ViewState

sealed class Event : ViewEvent {
    data object Init : Event()
    data object Pop : Event()
    data class ClaimClicked(val itemId: String) : Event()
    data object SecondaryButtonPressed : Event()

    data object DismissError : Event()

    sealed class BottomSheet : Event() {
        data class UpdateBottomSheetState(val isOpen: Boolean) : BottomSheet()

        sealed class Delete : BottomSheet() {
            data object PrimaryButtonPressed : Delete()
            data object SecondaryButtonPressed : Delete()
        }
    }

    data object ToggleDocumentClaimsExpanded : Event()
    data object BookmarkPressed : Event()
    data object OnBookmarkStored : Event()
    data object OnBookmarkRemoved : Event()
    data object IssuerCardPressed : Event()
    data class OnRevocationStatusChanged(val revokedIds: List<String>) : Event()
    data class OnReIssuanceStatusChanged(val reIssuedIds: List<String>) : Event()
    data class OnReIssuanceFailureStatusChanged(val failedStatusChangedIds: List<String>) : Event()
    data object ToggleExpansionStateOfDocumentCredentialsSection : Event()
    data object DocumentCredentialsSectionPrimaryButtonPressed : Event()
    data object PresentIdPressed : Event()
    data class PresentIdNfcEngagement(
        val componentActivity: ComponentActivity,
        val enable: Boolean
    ) : Event()
    data object PresentIdLifecycleStopped : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data object Pop : Navigation()
        data class PopWithResult(val resultKey: String, val resultValue: Boolean) : Navigation()
        data class SwitchScreen(
            val screenRoute: String,
            val popUpToScreenRoute: String?,
            val inclusive: Boolean?
        ) : Navigation()
    }

    data object ShowBottomSheet : Effect()
    data object CloseBottomSheet : Effect()

    data object BookmarkStored : Effect()
    data object BookmarkRemoved : Effect()
}

sealed class DocumentDetailsBottomSheetContent {
    data object DeleteDocumentConfirmation : DocumentDetailsBottomSheetContent()
    data object PresentId : DocumentDetailsBottomSheetContent()

    data class BookmarkStoredInfo(
        val bottomSheetTextData: BottomSheetTextDataUi
    ) : DocumentDetailsBottomSheetContent()

    data class BookmarkRemovedInfo(
        val bottomSheetTextData: BottomSheetTextDataUi
    ) : DocumentDetailsBottomSheetContent()

    data class TrustedRelyingPartyInfo(
        val bottomSheetTextData: BottomSheetTextDataUi
    ) : DocumentDetailsBottomSheetContent()
}

@KoinViewModel
class DocumentDetailsViewModel(
    private val documentDetailsInteractor: DocumentDetailsInteractor,
    private val uiSerializer: UiSerializer,
    private val resourceProvider: ResourceProvider,
    @InjectedParam private val documentId: DocumentId,
) : MviViewModel<Event, State, Effect>() {
    private var presentIdJob: Job? = null

    override fun setInitialState(): State = State(
        documentDetailsSectionTitle = resourceProvider.getString(R.string.document_details_main_section_text),
        documentIssuerSectionTitle = resourceProvider.getString(R.string.document_details_issuer_section_text),
    )

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.Init -> getDocumentDetails(event)

            is Event.Pop -> {
                setState { copy(error = null) }
                setEffect { Effect.Navigation.Pop }
            }

            is Event.ClaimClicked -> onClaimClicked(event.itemId)

            is Event.SecondaryButtonPressed -> {
                showBottomSheet(sheetContent = DocumentDetailsBottomSheetContent.DeleteDocumentConfirmation)
            }

            is Event.BottomSheet.UpdateBottomSheetState -> {
                val shouldCancelPresentIdPresentation: Boolean = event.isOpen.not()
                        && viewState.value.sheetContent is DocumentDetailsBottomSheetContent.PresentId
                        && viewState.value.isPresentIdHandoffInProgress.not()
                setState {
                    copy(isBottomSheetOpen = event.isOpen)
                }
                if (shouldCancelPresentIdPresentation) {
                    cancelPresentIdShare()
                }
            }

            is Event.BottomSheet.Delete.PrimaryButtonPressed -> {
                hideBottomSheet()
                deleteDocument(event)
            }

            is Event.BottomSheet.Delete.SecondaryButtonPressed -> {
                hideBottomSheet()
            }

            is Event.DismissError -> setState { copy(error = null) }

            is Event.ToggleDocumentClaimsExpanded -> setState {
                copy(areDocumentClaimsExpanded = !areDocumentClaimsExpanded)
            }

            is Event.BookmarkPressed -> {
                if (!viewState.value.isDocumentBookmarked) {
                    storeBookmark()
                } else {
                    deleteBookmark()
                }
            }

            is Event.OnBookmarkStored -> {
                showBottomSheet(
                    sheetContent = DocumentDetailsBottomSheetContent.BookmarkStoredInfo(
                        bottomSheetTextData = getBookmarkStoredBottomSheetTextData()
                    )
                )
            }

            is Event.OnBookmarkRemoved -> {
                showBottomSheet(
                    sheetContent = DocumentDetailsBottomSheetContent.BookmarkRemovedInfo(
                        bottomSheetTextData = getBookmarkRemovedBottomSheetTextData()
                    )
                )
            }

            is Event.IssuerCardPressed -> {
                showBottomSheet(
                    sheetContent = DocumentDetailsBottomSheetContent.TrustedRelyingPartyInfo(
                        bottomSheetTextData = getTrustedRelyingPartyBottomSheetTextData()
                    )
                )
            }

            is Event.OnRevocationStatusChanged -> {
                setState {
                    copy(
                        isRevoked = event.revokedIds.contains(documentId)
                    )
                }
            }

            is Event.OnReIssuanceStatusChanged -> {
                if (event.reIssuedIds.contains(documentId)) {
                    setEvent(Event.Pop)
                }
            }

            is Event.OnReIssuanceFailureStatusChanged -> {
                if (event.failedStatusChangedIds.contains(documentId)) {
                    getDocumentDetails(event)
                }
            }

            is Event.ToggleExpansionStateOfDocumentCredentialsSection -> toggleExpansionStateOfDocumentCredentialsSection()

            is Event.DocumentCredentialsSectionPrimaryButtonPressed -> {
                viewState.value.documentDetailsUi?.let { safeDocumentDetailsUi ->
                    goToAddDocumentScreen(documentFormatType = safeDocumentDetailsUi.documentIdentifier.formatType)
                }
            }

            is Event.PresentIdPressed -> startPresentIdSheet()

            is Event.PresentIdNfcEngagement -> handlePresentIdNfcEngagement(event)

            is Event.PresentIdLifecycleStopped -> stopPresentIdShareFromLifecycle()
        }
    }

    private fun getDocumentDetails(event: Event) {
        setState {
            copy(
                isLoading = documentDetailsUi == null,
                error = null
            )
        }

        viewModelScope.launch {
            documentDetailsInteractor.getDocumentDetails(
                documentId = documentId,
            ).collect { response ->
                when (response) {
                    is DocumentDetailsInteractorPartialState.Success -> {
                        val documentDetailsUi: DocumentDetailsUi = response.documentDetailsDomain
                            .transformToDocumentDetailsUi()
                            .let { documentDetailsUi ->
                                if (response.isReIssuanceFailed) {
                                    documentDetailsUi.copy(
                                        documentIssuanceStateUi = DocumentIssuanceStateUi.Failed
                                    )
                                } else {
                                    documentDetailsUi
                                }
                            }

                        setState {
                            copy(
                                isLoading = false,
                                error = null,
                                documentDetailsUi = documentDetailsUi,
                                identityCardData = response.identityCardData,
                                documentCredentialsInfoUi = response.documentCredentialsInfoUi,
                                title = documentDetailsUi.documentName,
                                isDocumentBookmarked = response.documentIsBookmarked,
                                isRevoked = response.isRevoked,
                                issuerName = response.issuerName,
                                issuerLogo = response.issuerLogo
                            )
                        }
                    }

                    is DocumentDetailsInteractorPartialState.Failure -> {
                        setState {
                            copy(
                                isLoading = false,
                                error = ContentErrorConfig(
                                    onRetry = { setEvent(event) },
                                    errorSubTitle = response.error,
                                    onCancel = { setEvent(Event.Pop) }
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun onClaimClicked(itemId: String) {
        val currentItem = viewState.value.documentDetailsUi
        if (currentItem != null) {
            val updatedDocumentClaims = currentItem.documentClaims.toggleExpansionState(itemId)

            setState {
                copy(
                    documentDetailsUi = currentItem.copy(
                        documentClaims = updatedDocumentClaims
                    )
                )
            }
        }
    }

    private fun deleteDocument(event: Event) {
        setState {
            copy(
                isLoading = true,
                error = null
            )
        }

        viewModelScope.launch {
            documentDetailsInteractor.deleteDocument(
                documentId = documentId
            ).collect { response ->
                when (response) {
                    is DocumentDetailsInteractorDeleteDocumentPartialState.AllDocumentsDeleted -> {
                        setState {
                            copy(
                                isLoading = false,
                                error = null
                            )
                        }

                        setEffect {
                            Effect.Navigation.SwitchScreen(
                                screenRoute = StartupScreens.Splash.screenRoute,
                                popUpToScreenRoute = DashboardScreens.Dashboard.screenRoute,
                                inclusive = true
                            )
                        }
                    }

                    is DocumentDetailsInteractorDeleteDocumentPartialState.SingleDocumentDeleted -> {
                        setState {
                            copy(
                                isLoading = false,
                                error = null
                            )
                        }

                        setEffect {
                            Effect.Navigation.PopWithResult(
                                resultKey = DOCUMENT_DELETED_RESULT_KEY,
                                resultValue = true
                            )
                        }
                    }

                    is DocumentDetailsInteractorDeleteDocumentPartialState.Failure -> {
                        setState {
                            copy(
                                isLoading = false,
                                error = ContentErrorConfig(
                                    onRetry = { setEvent(event) },
                                    errorSubTitle = response.errorMessage,
                                    onCancel = { setEvent(Event.DismissError) }
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun storeBookmark() {
        viewModelScope.launch {
            documentDetailsInteractor.storeBookmark(documentId).collect {
                if (it is DocumentDetailsInteractorStoreBookmarkPartialState.Success) {
                    setState {
                        copy(
                            isDocumentBookmarked = true
                        )
                    }

                    setEffect {
                        Effect.BookmarkStored
                    }
                }
            }
        }
    }

    private fun deleteBookmark() {
        viewModelScope.launch {
            documentDetailsInteractor.deleteBookmark(documentId).collect {
                if (it is DocumentDetailsInteractorDeleteBookmarkPartialState.Success) {
                    setState {
                        copy(
                            isDocumentBookmarked = false
                        )
                    }

                    setEffect {
                        Effect.BookmarkRemoved
                    }
                }
            }
        }
    }

    private fun showBottomSheet(sheetContent: DocumentDetailsBottomSheetContent) {
        setState {
            copy(
                sheetContent = sheetContent,
                isPresentIdHandoffInProgress = false
            )
        }
        setEffect {
            Effect.ShowBottomSheet
        }
    }

    private fun hideBottomSheet() {
        setEffect {
            Effect.CloseBottomSheet
        }
    }

    private fun getBookmarkStoredBottomSheetTextData(): BottomSheetTextDataUi {
        return BottomSheetTextDataUi(
            title = resourceProvider.getString(R.string.document_details_bottom_sheet_bookmark_info_title),
            message = resourceProvider.getString(R.string.document_details_bottom_sheet_bookmark_info_message)
        )
    }

    private fun getBookmarkRemovedBottomSheetTextData(): BottomSheetTextDataUi {
        return BottomSheetTextDataUi(
            title = resourceProvider.getString(R.string.document_details_bottom_sheet_bookmark_removed_info_title),
            message = resourceProvider.getString(R.string.document_details_bottom_sheet_bookmark_removed_info_message)
        )
    }

    private fun getTrustedRelyingPartyBottomSheetTextData(): BottomSheetTextDataUi {
        return BottomSheetTextDataUi(
            title = resourceProvider.getString(R.string.document_details_bottom_sheet_badge_title),
            message = resourceProvider.getString(R.string.document_details_bottom_sheet_badge_subtitle)
        )
    }

    private fun toggleExpansionStateOfDocumentCredentialsSection() {
        setState {
            copy(
                documentCredentialsInfoUi = documentCredentialsInfoUi?.copy(
                    isExpanded = !documentCredentialsInfoUi.isExpanded
                )
            )
        }
    }

    private fun goToAddDocumentScreen(documentFormatType: FormatType) {
        val addDocumentScreenRoute = generateComposableNavigationLink(
            screen = IssuanceScreens.AddDocument,
            arguments = generateComposableArguments(
                mapOf(
                    IssuanceUiConfig.serializedKeyName to uiSerializer.toBase64(
                        model = IssuanceUiConfig(
                            flowType = IssuanceFlowType.ExtraDocument(
                                formatType = documentFormatType
                            )
                        ),
                        parser = IssuanceUiConfig.Parser
                    )
                )
            )
        )

        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = addDocumentScreenRoute,
                popUpToScreenRoute = null,
                inclusive = null
            )
        }
    }

    private fun startPresentIdSheet() {
        val requestUriConfig = RequestUriConfig(
            mode = PresentationMode.Ble(DashboardScreens.Dashboard.screenRoute),
            presentingDocumentId = documentId
        )
        documentDetailsInteractor.setPresentIdConfig(requestUriConfig)
        presentIdJob?.cancel()
        setState {
            copy(
                presentIdQrCode = "",
                presentIdPresentationScopeId = requestUriConfig.presentationScopeId,
                isPresentIdHandoffInProgress = false,
                sheetContent = DocumentDetailsBottomSheetContent.PresentId
            )
        }
        setEffect { Effect.ShowBottomSheet }
        presentIdJob = viewModelScope.launch {
            documentDetailsInteractor.startPresentIdEngagement().collect { response ->
                when (response) {
                    is HomeInteractorPresentIdPartialState.QrReady -> {
                        setState { copy(presentIdQrCode = response.qrCode) }
                    }

                    is HomeInteractorPresentIdPartialState.Connected -> {
                        navigateFromPresentIdSheetToRequest()
                    }

                    is HomeInteractorPresentIdPartialState.Disconnected -> {
                        cancelPresentIdShare()
                        hideBottomSheet()
                    }

                    is HomeInteractorPresentIdPartialState.Error -> {
                        cancelPresentIdShare()
                        hideBottomSheet()
                    }
                }
            }
        }
    }

    private fun navigateFromPresentIdSheetToRequest() {
        val presentationScopeId: String = viewState.value.presentIdPresentationScopeId
        val arguments: Map<String, String> = buildMap {
            put("scopeId", presentationScopeId)
            put("presentingDocumentId", documentId)
        }
        unsubscribePresentIdShare()
        documentDetailsInteractor.releasePresentIdPresentationController()
        setState {
            copy(
                isBottomSheetOpen = false,
                isPresentIdHandoffInProgress = true,
                presentIdQrCode = ""
            )
        }
        setEffect { Effect.CloseBottomSheet }
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = generateComposableNavigationLink(
                    screen = ProximityScreens.Request,
                    arguments = generateComposableArguments(arguments)
                ),
                popUpToScreenRoute = null,
                inclusive = null
            )
        }
    }

    private fun handlePresentIdNfcEngagement(event: Event.PresentIdNfcEngagement) {
        val canToggleNfc: Boolean =
            viewState.value.sheetContent is DocumentDetailsBottomSheetContent.PresentId
                    && viewState.value.presentIdPresentationScopeId.isNotBlank()
        if (canToggleNfc) {
            documentDetailsInteractor.togglePresentIdNfcEngagement(
                componentActivity = event.componentActivity,
                toggle = event.enable
            )
        }
    }

    private fun unsubscribePresentIdShare() {
        presentIdJob?.cancel()
        presentIdJob = null
    }

    private fun cancelPresentIdShare() {
        val hasActivePresentation: Boolean = viewState.value.presentIdPresentationScopeId.isNotBlank()
        unsubscribePresentIdShare()
        if (hasActivePresentation) {
            documentDetailsInteractor.cancelPresentIdPresentation()
        }
        setState {
            copy(
                presentIdQrCode = "",
                presentIdPresentationScopeId = "",
                isPresentIdHandoffInProgress = false
            )
        }
    }

    private fun stopPresentIdShareFromLifecycle() {
        val shouldStopPresentIdPresentation: Boolean =
            viewState.value.sheetContent is DocumentDetailsBottomSheetContent.PresentId
                    && viewState.value.isPresentIdHandoffInProgress.not()
        if (shouldStopPresentIdPresentation.not()) return
        cancelPresentIdShare()
        setState { copy(isBottomSheetOpen = false) }
    }


    companion object {
        const val DOCUMENT_DELETED_RESULT_KEY = "documentDeleted"
    }
}
