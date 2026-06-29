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

package eu.europa.ec.corelogic.service

import eu.europa.ec.businesslogic.controller.session.PresentationSessionController
import eu.europa.ec.corelogic.di.PRESENTATION_WALLET_QUALIFIER
import eu.europa.ec.corelogic.di.WalletCoreScope
import eu.europa.ec.corelogic.di.getOrCreateKoinScope
import eu.europa.ec.eudi.iso18013.transfer.TransferManager
import eu.europa.ec.eudi.wallet.EudiWallet
import org.koin.android.ext.android.inject
import org.koin.core.qualifier.named
import eu.europa.ec.eudi.iso18013.transfer.engagement.NfcEngagementService as BaseService

class NfcEngagementService : BaseService() {

    private val presentationSessionController: PresentationSessionController by inject()

    override val transferManager: TransferManager
        get() {
            val sessionId = presentationSessionController.getSessionId()
            if (sessionId.isEmpty()) {
                throw IllegalStateException("Missing presentation session id")
            }
            return getOrCreateKoinScope<WalletCoreScope>(sessionId)
                .get<EudiWallet>(qualifier = named(PRESENTATION_WALLET_QUALIFIER))
                .transferManager
        }
}
