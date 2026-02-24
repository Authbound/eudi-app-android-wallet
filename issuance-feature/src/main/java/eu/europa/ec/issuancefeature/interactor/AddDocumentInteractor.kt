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

package eu.europa.ec.issuancefeature.interactor

import android.content.Context
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricsAvailability
import eu.europa.ec.authenticationlogic.controller.authentication.DeviceAuthenticationResult
import eu.europa.ec.authenticationlogic.model.BiometricCrypto
import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.commonfeature.config.IssuanceFlowType
import eu.europa.ec.commonfeature.config.SuccessUIConfig
import eu.europa.ec.commonfeature.interactor.DeviceAuthenticationInteractor
import eu.europa.ec.corelogic.controller.FetchScopedDocumentsPartialState
import eu.europa.ec.corelogic.controller.IssuanceMethod
import eu.europa.ec.corelogic.controller.IssueDocumentPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.model.DocumentCategory
import eu.europa.ec.corelogic.model.FormatType
import eu.europa.ec.corelogic.model.ScopedDocumentDomain
import eu.europa.ec.corelogic.model.toDocumentCategory
import eu.europa.ec.corelogic.model.toDocumentIdentifier
import eu.europa.ec.issuancefeature.ui.add.model.AddDocumentUi
import eu.europa.ec.issuancefeature.ui.add.model.CategoryGroupUi
import eu.europa.ec.issuancefeature.ui.add.model.FeaturedCredentialUi
import eu.europa.ec.issuancefeature.util.toIcon
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ListItemDataUi
import eu.europa.ec.uilogic.component.ListItemMainContentDataUi
import eu.europa.ec.uilogic.component.ListItemTrailingContentDataUi
import eu.europa.ec.uilogic.component.utils.PERCENTAGE_25
import eu.europa.ec.uilogic.config.ConfigNavigation
import eu.europa.ec.uilogic.config.NavigationType
import eu.europa.ec.uilogic.navigation.CommonScreens
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import eu.europa.ec.uilogic.serializer.UiSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed class AddDocumentInteractorPartialState {
    data class Success(
        val featured: List<FeaturedCredentialUi>,
        val categoryGroups: List<CategoryGroupUi>,
    ) : AddDocumentInteractorPartialState()

    data class NoOptions(val errorMsg: String) : AddDocumentInteractorPartialState()
    data class Failure(val error: String) : AddDocumentInteractorPartialState()
}

interface AddDocumentInteractor {
    fun getAddDocumentOption(
        flowType: IssuanceFlowType,
    ): Flow<AddDocumentInteractorPartialState>

    fun issueDocument(
        issuanceMethod: IssuanceMethod,
        configId: String,
        issuerId: String
    ): Flow<IssueDocumentPartialState>

    fun handleUserAuth(
        context: Context,
        crypto: BiometricCrypto,
        notifyOnAuthenticationFailure: Boolean,
        resultHandler: DeviceAuthenticationResult
    )

    fun buildGenericSuccessRouteForDeferred(flowType: IssuanceFlowType): String

    fun resumeOpenId4VciWithAuthorization(uri: String)
}

class AddDocumentInteractorImpl(
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
    private val deviceAuthenticationInteractor: DeviceAuthenticationInteractor,
    private val resourceProvider: ResourceProvider,
    private val uiSerializer: UiSerializer
) : AddDocumentInteractor {

    private val genericErrorMsg
        get() = resourceProvider.genericErrorMessage()

    override fun getAddDocumentOption(
        flowType: IssuanceFlowType,
    ): Flow<AddDocumentInteractorPartialState> =
        flow {
            val state =
                walletCoreDocumentsController.getScopedDocuments(resourceProvider.getLocale())
            when (state) {
                is FetchScopedDocumentsPartialState.Failure -> emit(
                    AddDocumentInteractorPartialState.Failure(
                        error = state.errorMessage
                    )
                )

                is FetchScopedDocumentsPartialState.Success -> {
                    val customFormatType: FormatType? =
                        (flowType as? IssuanceFlowType.ExtraDocument)?.formatType

                    val allCategories = walletCoreDocumentsController.getAllDocumentCategories()

                    val filtered = state.documents.filter { doc ->
                        (customFormatType == null || doc.formatType == customFormatType) &&
                                (flowType !is IssuanceFlowType.NoDocument || doc.isPid)
                    }

                    val deduplicated = deduplicateDocuments(filtered)

                    if (deduplicated.isEmpty()) {
                        emit(
                            AddDocumentInteractorPartialState.NoOptions(
                                errorMsg = resourceProvider.getString(R.string.issuance_add_document_no_options)
                            )
                        )
                        return@flow
                    }

                    val categorizedDocs = deduplicated.map { doc ->
                        val formatType = doc.formatType.orEmpty()
                        val category = formatType.toDocumentIdentifier()
                            .toDocumentCategory(allCategories)
                        doc to category
                    }

                    val featured = buildFeaturedList(categorizedDocs)
                    val featuredKeys = featured.map { it.configurationId }.toSet()

                    val categoryGroups = buildCategoryGroups(
                        categorizedDocs.filter { (doc, _) ->
                            doc.configurationId !in featuredKeys
                        }
                    )

                    emit(
                        AddDocumentInteractorPartialState.Success(
                            featured = featured,
                            categoryGroups = categoryGroups,
                        )
                    )
                }
            }
        }.safeAsync {
            AddDocumentInteractorPartialState.Failure(
                error = it.localizedMessage ?: genericErrorMsg
            )
        }

    private fun normalizeFormatType(formatType: String): String {
        var normalized = formatType.removeSuffix(".deferred_endpoint")
        // Map urn:eu.europa.ec.eudi:X:1 → eu.europa.ec.eudi.X.1
        if (normalized.startsWith("urn:")) {
            normalized = normalized.removePrefix("urn:")
                .replace(':', '.')
        }
        return normalized.lowercase()
    }

    private fun deduplicateDocuments(
        documents: List<ScopedDocumentDomain>
    ): List<ScopedDocumentDomain> {
        return documents
            .groupBy { doc ->
                normalizeFormatType(doc.formatType.orEmpty())
            }
            .values
            .mapNotNull { variants ->
                variants.sortedWith(
                    compareBy<ScopedDocumentDomain> { doc ->
                        // Prefer non-deferred
                        if (doc.formatType.orEmpty().endsWith(".deferred_endpoint")) 1 else 0
                    }.thenBy { doc ->
                        // Prefer Mdoc (non-urn prefix) over SdJwt (urn: prefix)
                        if (doc.formatType.orEmpty().startsWith("urn:")) 1 else 0
                    }
                ).firstOrNull()
            }
    }

    private fun buildFeaturedList(
        categorizedDocs: List<Pair<ScopedDocumentDomain, DocumentCategory>>
    ): List<FeaturedCredentialUi> {
        val docsByNormalized = categorizedDocs.associateBy { (doc, _) ->
            normalizeFormatType(doc.formatType.orEmpty())
        }

        return FEATURED_FORMAT_TYPES.mapNotNull { featuredType ->
            val normalized = normalizeFormatType(featuredType)
            val (doc, category) = docsByNormalized[normalized] ?: return@mapNotNull null
            FeaturedCredentialUi(
                credentialIssuerId = doc.credentialIssuerId,
                configurationId = doc.configurationId,
                name = doc.name,
                description = getFeaturedDescription(normalized),
                category = category,
                categoryIcon = category.toIcon(),
            )
        }
    }

    private fun getFeaturedDescription(normalizedFormatType: String): String {
        val descriptionResId = FEATURED_DESCRIPTIONS[normalizedFormatType] ?: return ""
        return resourceProvider.getString(descriptionResId)
    }

    private fun buildCategoryGroups(
        categorizedDocs: List<Pair<ScopedDocumentDomain, DocumentCategory>>
    ): List<CategoryGroupUi> {
        return categorizedDocs
            .groupBy { (_, category) -> category }
            .entries
            .sortedBy { (category, _) -> category.order }
            .map { (category, docsWithCategory) ->
                CategoryGroupUi(
                    category = category,
                    categoryIcon = category.toIcon(),
                    credentials = docsWithCategory
                        .sortedBy { (doc, _) -> doc.name.lowercase() }
                        .map { (doc, cat) ->
                            AddDocumentUi(
                                credentialIssuerId = doc.credentialIssuerId,
                                configurationId = doc.configurationId,
                                category = cat,
                                itemData = ListItemDataUi(
                                    itemId = doc.configurationId,
                                    mainContentData = ListItemMainContentDataUi.Text(text = doc.name),
                                    trailingContentData = ListItemTrailingContentDataUi.Icon(
                                        iconData = AppIcons.Add
                                    )
                                )
                            )
                        }
                )
            }
            .filter { it.credentials.isNotEmpty() }
    }

    companion object {
        val FEATURED_FORMAT_TYPES = listOf(
            "eu.europa.ec.eudi.pid.1",
            "org.iso.18013.5.1.mDL",
            "eu.europa.ec.eudi.ehic.1",
            "eu.europa.ec.eudi.iban.1",
            "org.iso.23220.2.photoid.1",
            "eu.europa.ec.eudi.pseudonym.age_over_18.1",
        )

        private val FEATURED_DESCRIPTIONS: Map<String, Int> = FEATURED_FORMAT_TYPES.zip(
            listOf(
                R.string.credential_desc_pid,
                R.string.credential_desc_mdl,
                R.string.credential_desc_ehic,
                R.string.credential_desc_iban,
                R.string.credential_desc_photoid,
                R.string.credential_desc_age_verification,
            )
        ).associate { (type, resId) -> normalizeFormatTypeStatic(type) to resId }

        private fun normalizeFormatTypeStatic(formatType: String): String {
            var normalized = formatType.removeSuffix(".deferred_endpoint")
            if (normalized.startsWith("urn:")) {
                normalized = normalized.removePrefix("urn:").replace(':', '.')
            }
            return normalized.lowercase()
        }
    }

    override fun issueDocument(
        issuanceMethod: IssuanceMethod,
        configId: String,
        issuerId: String
    ): Flow<IssueDocumentPartialState> =
        walletCoreDocumentsController.issueDocument(
            issuanceMethod = issuanceMethod,
            configId = configId,
            issuerId = issuerId
        )

    override fun handleUserAuth(
        context: Context,
        crypto: BiometricCrypto,
        notifyOnAuthenticationFailure: Boolean,
        resultHandler: DeviceAuthenticationResult
    ) {
        deviceAuthenticationInteractor.getBiometricsAvailability {
            when (it) {
                is BiometricsAvailability.CanAuthenticate -> {
                    deviceAuthenticationInteractor.authenticateWithBiometrics(
                        context = context,
                        crypto = crypto,
                        notifyOnAuthenticationFailure = notifyOnAuthenticationFailure,
                        resultHandler = resultHandler
                    )
                }

                is BiometricsAvailability.NonEnrolled -> {
                    deviceAuthenticationInteractor.launchBiometricSystemScreen()
                }

                is BiometricsAvailability.Failure -> {
                    resultHandler.onAuthenticationFailure()
                }
            }
        }
    }

    override fun buildGenericSuccessRouteForDeferred(flowType: IssuanceFlowType): String {
        val navigation = when (flowType) {
            is IssuanceFlowType.NoDocument -> ConfigNavigation(
                navigationType = NavigationType.PushRoute(
                    route = DashboardScreens.Dashboard.screenRoute,
                    popUpToRoute = IssuanceScreens.AddDocument.screenRoute
                ),
            )

            is IssuanceFlowType.ExtraDocument -> ConfigNavigation(
                navigationType = NavigationType.PopTo(
                    screen = DashboardScreens.Dashboard
                )
            )
        }
        val successScreenArguments = getSuccessScreenArgumentsForDeferred(navigation)
        return generateComposableNavigationLink(
            screen = CommonScreens.Success,
            arguments = successScreenArguments
        )
    }

    override fun resumeOpenId4VciWithAuthorization(uri: String) {
        walletCoreDocumentsController.resumeOpenId4VciWithAuthorization(uri)
    }

    private fun getSuccessScreenArgumentsForDeferred(
        navigation: ConfigNavigation
    ): String {
        val (textElementsConfig, imageConfig, buttonText) = Triple(
            first = SuccessUIConfig.TextElementsConfig(
                text = resourceProvider.getString(R.string.issuance_add_document_deferred_success_text),
                description = resourceProvider.getString(R.string.issuance_add_document_deferred_success_description),
                color = ThemeColors.pending
            ),
            second = SuccessUIConfig.ImageConfig(
                type = SuccessUIConfig.ImageConfig.Type.Drawable(icon = AppIcons.InProgress),
                tint = ThemeColors.primary,
                screenPercentageSize = PERCENTAGE_25,
            ),
            third = resourceProvider.getString(R.string.issuance_add_document_deferred_success_primary_button_text)
        )

        return generateComposableArguments(
            mapOf(
                SuccessUIConfig.serializedKeyName to uiSerializer.toBase64(
                    SuccessUIConfig(
                        textElementsConfig = textElementsConfig,
                        imageConfig = imageConfig,
                        buttonConfig = listOf(
                            SuccessUIConfig.ButtonConfig(
                                text = buttonText,
                                style = SuccessUIConfig.ButtonConfig.Style.PRIMARY,
                                navigation = navigation
                            )
                        ),
                        onBackScreenToNavigate = navigation,
                    ),
                    SuccessUIConfig.Parser
                ).orEmpty()
            )
        )
    }
}