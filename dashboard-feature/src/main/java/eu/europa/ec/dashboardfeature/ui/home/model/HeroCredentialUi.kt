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

package eu.europa.ec.dashboardfeature.ui.home.model

import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.dashboardfeature.ui.common.resolveCredentialVisualType
import eu.europa.ec.dashboardfeature.ui.documents.detail.model.DocumentIssuanceStateUi
import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.uilogic.component.wrap.CredentialCardLayout
import eu.europa.ec.uilogic.component.wrap.CredentialStatus
import eu.europa.ec.uilogic.component.wrap.VisualCredentialConfig

/**
 * UI model for the hero credential card displayed at the top of the home screen.
 * Shows the user's primary credential (PID or mDL) in a premium visual style.
 */
data class HeroCredentialUi(
    val documentId: DocumentId,
    val documentIdentifier: DocumentIdentifier,
    val title: String,
    val subtitle: String?,
    val holderName: String?,
    val issuerName: String?,
    val expiryDate: String?,
    val status: DocumentIssuanceStateUi,
    val hasPhoto: Boolean,
    val portraitBase64: String?,
    val nationality: String? = null
) {
    /**
     * Convert to VisualCredentialConfig for the visual credential card component.
     */
    fun toVisualConfig(): VisualCredentialConfig {
        return VisualCredentialConfig(
            id = documentId,
            visualType = resolveCredentialVisualType(
                documentIdentifier = documentIdentifier,
                issuerName = issuerName
            ),
            title = title,
            subtitle = subtitle,
            holderName = holderName,
            issuerName = issuerName,
            primaryField = null,
            secondaryField = null,
            status = status.toCredentialStatus(),
            expiryDate = expiryDate,
            hasPhoto = hasPhoto,
            portraitBase64 = portraitBase64,
            nationality = nationality,
            layout = CredentialCardLayout.PASSPORT
        )
    }
}

/**
 * Map DocumentIssuanceStateUi to CredentialStatus.
 */
fun DocumentIssuanceStateUi.toCredentialStatus(): CredentialStatus {
    return when (this) {
        DocumentIssuanceStateUi.Issued -> CredentialStatus.ISSUED
        DocumentIssuanceStateUi.Pending -> CredentialStatus.PENDING
        DocumentIssuanceStateUi.Failed -> CredentialStatus.REVOKED
        DocumentIssuanceStateUi.Expired -> CredentialStatus.EXPIRED
        DocumentIssuanceStateUi.Revoked -> CredentialStatus.REVOKED
    }
}
