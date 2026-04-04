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

package eu.europa.ec.corelogic.provider

import android.content.Context
import eu.europa.ec.corelogic.config.VciConfig
import eu.europa.ec.eudi.wallet.EudiWallet
import eu.europa.ec.eudi.wallet.issue.openid4vci.OpenId4VciManager

interface IssuerOpenId4VciManagerFactory {
    fun create(eudiWallet: EudiWallet, vciConfig: VciConfig): OpenId4VciManager
}

class IssuerOpenId4VciManagerFactoryImpl(
    private val context: Context,
    private val walletCoreAttestationProviderFactory: WalletCoreAttestationProviderFactory,
) : IssuerOpenId4VciManagerFactory {

    override fun create(eudiWallet: EudiWallet, vciConfig: VciConfig): OpenId4VciManager =
        OpenId4VciManager(context) {
            config(vciConfig.config)
            documentManager(eudiWallet.documentManager)
            walletKeyManager(eudiWallet.walletKeyManager)
            logger(eudiWallet.logger)
            ktorHttpClientFactory { ProvideKtorHttpClient.client() }
            walletAttestationsProvider(
                walletCoreAttestationProviderFactory.create(vciConfig.walletProviderConfig)
            )
        }
}
