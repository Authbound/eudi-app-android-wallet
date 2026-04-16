/*
 * Copyright (c) 2026 European Commission
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

package eu.europa.ec.dashboardfeature.ui.common

import eu.europa.ec.corelogic.model.DocumentCategory
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.uilogic.component.wrap.CredentialVisualType
import org.junit.Assert.assertEquals
import org.junit.Test

class TestCredentialVisualTypeResolver {

    @Test
    fun `Given an Authbound-issued pid, When resolving the visual type, Then the Authbound variant is used`() {
        val visualType: CredentialVisualType = resolveCredentialVisualType(
            documentIdentifier = DocumentIdentifier.SdJwtPid,
            documentCategory = DocumentCategory.Government,
            issuerName = "Authbound Issuer"
        )

        assertEquals(CredentialVisualType.AUTHBOUND, visualType)
    }

    @Test
    fun `Given a driving credential format, When resolving the visual type, Then the mdl variant is used`() {
        val visualType: CredentialVisualType = resolveCredentialVisualType(
            documentIdentifier = DocumentIdentifier.OTHER(formatType = "org.iso.18013.5.1.mDL"),
            documentCategory = DocumentCategory.Other,
            issuerName = null
        )

        assertEquals(CredentialVisualType.MDL, visualType)
    }
}
