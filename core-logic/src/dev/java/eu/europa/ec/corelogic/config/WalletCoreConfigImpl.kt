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

package eu.europa.ec.corelogic.config

import android.content.Context
import android.util.Log
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.businesslogic.util.EmulatorDetector
import eu.europa.ec.corelogic.BuildConfig
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.eudi.wallet.EudiWalletConfig
import eu.europa.ec.eudi.wallet.document.CreateDocumentSettings.CredentialPolicy
import eu.europa.ec.eudi.wallet.issue.openid4vci.OpenId4VciManager
import eu.europa.ec.eudi.wallet.issue.openid4vci.dpop.DPopConfig
import eu.europa.ec.eudi.wallet.transfer.openId4vp.ClientIdScheme
import eu.europa.ec.eudi.wallet.transfer.openId4vp.Format
import eu.europa.ec.resourceslogic.R
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlin.time.Duration.Companion.seconds

internal class WalletCoreConfigImpl(
    private val context: Context,
    private val configLogic: ConfigLogic,
    private val httpClient: HttpClient,
) : WalletCoreConfig {

    companion object {
        private const val TAG = "WalletCoreConfig"
        private const val MOBILE_BACKEND_PORT = 3009
    }

    // Trust API client using EmulatorDetector for correct localhost address
    private val trustApiClient by lazy {
        val host = EmulatorDetector.getLocalhostAddress()
        TrustApiClient(
            httpClient = httpClient,
            baseUrl = "http://$host:$MOBILE_BACKEND_PORT/api/trust",
        )
    }

    // Dynamic trust data fetched once at config init time
    private val dynamicTrustData: DynamicTrustData by lazy {
        runBlocking {
            try {
                val issuers = trustApiClient.fetchIssuerUrls()
                val certs = trustApiClient.fetchTrustedCertificates()
                DynamicTrustData(issuerUrls = issuers, certificates = certs)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch dynamic trust data: ${e.message}")
                DynamicTrustData(issuerUrls = null, certificates = null)
            }
        }
    }

    private var _config: EudiWalletConfig? = null

    override val config: EudiWalletConfig
        get() {
            if (_config == null) {
                val dynamicCerts = parseDynamicCertificates(dynamicTrustData.certificates)

                _config = EudiWalletConfig {
                    configureDocumentKeyCreation(
                        userAuthenticationRequired = false,
                        userAuthenticationTimeout = 30.seconds,
                        useStrongBoxForKeys = true
                    )
                    configureOpenId4Vp {
                        withClientIdSchemes(
                            listOf(
                                ClientIdScheme.X509SanDns,
                                ClientIdScheme.X509Hash
                            )
                        )
                        withSchemes(
                            listOf(
                                BuildConfig.OPENID4VP_SCHEME,
                                BuildConfig.EUDI_OPENID4VP_SCHEME,
                                BuildConfig.MDOC_OPENID4VP_SCHEME,
                                BuildConfig.HAIP_OPENID4VP_SCHEME
                            )
                        )
                        withFormats(
                            Format.MsoMdoc.ES256, Format.SdJwtVc.ES256
                        )
                    }

                    configureReaderTrustStore(
                        context,
                        R.raw.authbound_verifier_root_ca,
                        R.raw.pidissuerca02_cz,
                        R.raw.pidissuerca02_ee,
                        R.raw.pidissuerca02_eu,
                        R.raw.pidissuerca02_lu,
                        R.raw.pidissuerca02_nl,
                        R.raw.pidissuerca02_pt,
                        R.raw.pidissuerca02_ut,
                        R.raw.dc4eu,
                        R.raw.r45_staging
                    )

                    if (dynamicCerts.isNotEmpty()) {
                        Log.i(TAG, "Adding ${dynamicCerts.size} dynamic CA certificates to trust store")
                        configureReaderTrustStore(dynamicCerts)
                    }
                }
            }
            return _config!!
        }

    private val defaultIssuers: List<VciConfig> = listOf(
        VciConfig(
            config = OpenId4VciManager.Config.Builder()
                .withIssuerUrl(issuerUrl = "https://ec.dev.issuer.eudiw.dev")
                .withClientAuthenticationType(OpenId4VciManager.ClientAuthenticationType.AttestationBased)
                .withAuthFlowRedirectionURI(BuildConfig.ISSUE_AUTHORIZATION_DEEPLINK)
                .withParUsage(OpenId4VciManager.Config.ParUsage.IF_SUPPORTED)
                .withDPopConfig(DPopConfig.Default)
                .build(),
            order = 0
        ),
        VciConfig(
            config = OpenId4VciManager.Config.Builder()
                .withIssuerUrl(issuerUrl = "https://dev.issuer-backend.eudiw.dev")
                .withClientAuthenticationType(OpenId4VciManager.ClientAuthenticationType.AttestationBased)
                .withAuthFlowRedirectionURI(BuildConfig.ISSUE_AUTHORIZATION_DEEPLINK)
                .withParUsage(OpenId4VciManager.Config.ParUsage.IF_SUPPORTED)
                .withDPopConfig(DPopConfig.Default)
                .build(),
            order = 1
        ),
        VciConfig(
            config = OpenId4VciManager.Config.Builder()
                .withIssuerUrl(issuerUrl = "https://issuer.authbound.io/api/v1/openid4vci")
                .withClientAuthenticationType(OpenId4VciManager.ClientAuthenticationType.AttestationBased)
                .withAuthFlowRedirectionURI(BuildConfig.ISSUE_AUTHORIZATION_DEEPLINK)
                .withParUsage(OpenId4VciManager.Config.ParUsage.IF_SUPPORTED)
                .withDPopConfig(DPopConfig.Default)
                .build(),
            order = 2
        ),
    )

    override val issuersConfig: List<VciConfig> by lazy {
        val dynamicUrls = dynamicTrustData.issuerUrls
        if (dynamicUrls != null && dynamicUrls.isNotEmpty()) {
            val dynamicIssuers = dynamicUrls.mapIndexed { index, issuerUrl ->
                VciConfig(
                    config = OpenId4VciManager.Config.Builder()
                        .withIssuerUrl(issuerUrl = issuerUrl)
                        .withClientAuthenticationType(OpenId4VciManager.ClientAuthenticationType.AttestationBased)
                        .withAuthFlowRedirectionURI(BuildConfig.ISSUE_AUTHORIZATION_DEEPLINK)
                        .withParUsage(OpenId4VciManager.Config.ParUsage.IF_SUPPORTED)
                        .withDPopConfig(DPopConfig.Default)
                        .build(),
                    order = index
                )
            }
            dynamicIssuers + defaultIssuers.mapIndexed { index, vci ->
                vci.copy(order = dynamicUrls.size + index)
            }
        } else {
            defaultIssuers
        }
    }

    override val documentIssuanceConfig: DocumentIssuanceConfig
        get() = DocumentIssuanceConfig(
            defaultRule = DocumentIssuanceRule(
                policy = CredentialPolicy.RotateUse,
                numberOfCredentials = 1
            ),
            documentSpecificRules = mapOf(
                DocumentIdentifier.MdocPid to DocumentIssuanceRule(
                    policy = CredentialPolicy.OneTimeUse,
                    numberOfCredentials = 60
                ),
                DocumentIdentifier.SdJwtPid to DocumentIssuanceRule(
                    policy = CredentialPolicy.OneTimeUse,
                    numberOfCredentials = 60
                ),
            )
        )

    override val walletProviderHost: String
        get() = "${configLogic.environmentConfig.getServerHost()}/api/mobile/wallet-provider"
}

private fun parseDynamicCertificates(pems: List<String>?): List<X509Certificate> {
    if (pems.isNullOrEmpty()) return emptyList()

    val factory = CertificateFactory.getInstance("X.509")
    return pems.mapNotNull { pem ->
        try {
            factory.generateCertificate(
                ByteArrayInputStream(pem.toByteArray())
            ) as X509Certificate
        } catch (e: Exception) {
            Log.w("WalletCoreConfig", "Failed to parse dynamic certificate: ${e.message}")
            null
        }
    }
}

private data class DynamicTrustData(
    val issuerUrls: List<String>?,
    val certificates: List<String>?,
)
