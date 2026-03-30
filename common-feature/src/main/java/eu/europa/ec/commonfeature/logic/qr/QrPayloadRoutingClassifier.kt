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

package eu.europa.ec.commonfeature.logic.qr

import android.net.Uri
import eu.europa.ec.commonfeature.config.IssuanceFlowType
import eu.europa.ec.uilogic.navigation.helper.DeepLinkType
import eu.europa.ec.uilogic.navigation.helper.hasDeepLink
import java.util.Locale

/**
 * Routes a scanned payload for the universal (main FAB) QR entry: OpenID4VCI issuance vs
 * OpenID4VP presentation, aligned with [DeepLinkType] and common OIDC query patterns.
 *
 * Order of checks: explicit credential-offer signals first, then VP signals, then safe defaults.
 */
object QrPayloadRoutingClassifier {

    /**
     * Issuance navigation after universal scan should match "add credential from QR" when the user
     * did not open the scanner from the issuance wizard — [IssuanceFlowType.ExtraDocument] yields
     * PopTo(Dashboard) on success, same as dashboard credential-offer deep links.
     */
    private val universalIssuanceFlowType: IssuanceFlowType =
        IssuanceFlowType.ExtraDocument(formatType = null)

    fun classify(scannedPayload: String): UniversalScanRoute? {
        val trimmed = scannedPayload.trim()
        if (trimmed.isEmpty()) return null

        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return null
        if (scheme.isEmpty()) return null

        val action = hasDeepLink(uri) ?: return classifyHttpOrHttps(uri)

        return when (action.type) {
            DeepLinkType.CREDENTIAL_OFFER ->
                UniversalScanRoute.Issuance(universalIssuanceFlowType)
            DeepLinkType.OPENID4VP ->
                UniversalScanRoute.Presentation
            DeepLinkType.ISSUANCE ->
                UniversalScanRoute.VciResume
            DeepLinkType.RQES, DeepLinkType.RQES_DOC_RETRIEVAL ->
                UniversalScanRoute.Rqes
            DeepLinkType.AUTHBOUNDPID_CALLBACK ->
                null
            DeepLinkType.EXTERNAL ->
                classifyHttpOrHttps(uri)
            DeepLinkType.DYNAMIC_PRESENTATION ->
                UniversalScanRoute.Presentation
        }
    }

    private fun classifyHttpOrHttps(uri: Uri): UniversalScanRoute? {
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return null
        if (scheme != "https" && scheme != "http") return null

        if (hasCredentialOfferQueryParams(uri)) {
            return UniversalScanRoute.Issuance(universalIssuanceFlowType)
        }
        if (hasOpenId4VpQueryParams(uri)) {
            return UniversalScanRoute.Presentation
        }
        // Valid https(s) URL without strong signals: keep prior FAB behaviour (presentation).
        return UniversalScanRoute.Presentation
    }

    private fun hasCredentialOfferQueryParams(uri: Uri): Boolean {
        val offer = uri.getQueryParameter("credential_offer")
        val offerUri = uri.getQueryParameter("credential_offer_uri")
        return !offer.isNullOrBlank() || !offerUri.isNullOrBlank()
    }

    private fun hasOpenId4VpQueryParams(uri: Uri): Boolean {
        if (!uri.getQueryParameter("request_uri").isNullOrBlank()) return true
        if (!uri.getQueryParameter("presentation_definition").isNullOrBlank()) return true
        if (!uri.getQueryParameter("presentation_definition_uri").isNullOrBlank()) return true
        val responseType = uri.getQueryParameter("response_type")?.lowercase(Locale.US).orEmpty()
        if (responseType.contains("vp_token")) return true
        return false
    }
}

sealed interface UniversalScanRoute {
    data object Presentation : UniversalScanRoute
    data class Issuance(val flowType: IssuanceFlowType) : UniversalScanRoute
    data object VciResume : UniversalScanRoute
    data object Rqes : UniversalScanRoute
}
