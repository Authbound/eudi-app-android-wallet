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

package eu.europa.ec.corelogic.controller

import com.nimbusds.jose.JWSAlgorithm
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.wallet.UserDocumentOwnershipController
import eu.europa.ec.corelogic.config.AuthboundWalletProviderConfig
import eu.europa.ec.corelogic.config.DocumentIssuanceConfig
import eu.europa.ec.corelogic.config.DocumentIssuanceRule
import eu.europa.ec.corelogic.config.EuReferenceWalletProviderConfig
import eu.europa.ec.corelogic.config.VciConfig
import eu.europa.ec.corelogic.config.WalletCoreConfig
import eu.europa.ec.corelogic.provider.IssuerOpenId4VciManagerFactory
import eu.europa.ec.corelogic.provider.WuaProofUserAuthRequiredException
import eu.europa.ec.eudi.openid4vci.CIAuthorizationServerMetadata
import eu.europa.ec.eudi.openid4vci.CredentialConfigurationIdentifier
import eu.europa.ec.eudi.openid4vci.CredentialIssuerEndpoint
import eu.europa.ec.eudi.openid4vci.CredentialIssuerId
import eu.europa.ec.eudi.openid4vci.CredentialIssuerMetadata
import eu.europa.ec.eudi.openid4vci.CredentialMetadata
import eu.europa.ec.eudi.openid4vci.CredentialOffer
import eu.europa.ec.eudi.openid4vci.Grants
import eu.europa.ec.eudi.openid4vci.KeyAttestationRequirement
import eu.europa.ec.eudi.openid4vci.ProofTypeMeta
import eu.europa.ec.eudi.openid4vci.ProofTypesSupported
import eu.europa.ec.eudi.openid4vci.SdJwtVcCredential
import eu.europa.ec.eudi.wallet.EudiWallet
import eu.europa.ec.eudi.wallet.EudiWalletConfig
import eu.europa.ec.eudi.wallet.document.CreateDocumentSettings.CredentialPolicy
import eu.europa.ec.eudi.wallet.issue.openid4vci.IssueEvent
import eu.europa.ec.eudi.wallet.issue.openid4vci.Offer
import eu.europa.ec.eudi.wallet.issue.openid4vci.OpenId4VciManager
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.storagelogic.dao.BookmarkDao
import eu.europa.ec.storagelogic.dao.RevokedDocumentDao
import eu.europa.ec.storagelogic.dao.TransactionLogDao
import eu.europa.ec.testlogic.extension.runFlowTest
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

class TestWalletCoreDocumentsControllerAuthboundIssuanceAuth {

    @get:Rule
    val coroutineRule: CoroutineTestRule = CoroutineTestRule()

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    @Mock
    private lateinit var eudiWallet: EudiWallet

    @Mock
    private lateinit var walletCoreConfig: WalletCoreConfig

    @Mock
    private lateinit var bookmarkDao: BookmarkDao

    @Mock
    private lateinit var issuerOpenId4VciManagerFactory: IssuerOpenId4VciManagerFactory

    @Mock
    private lateinit var transactionLogDao: TransactionLogDao

    @Mock
    private lateinit var revokedDocumentDao: RevokedDocumentDao

    @Mock
    private lateinit var ownershipController: UserDocumentOwnershipController

    @Mock
    private lateinit var logController: LogController

    @Mock
    private lateinit var manager: OpenId4VciManager

    private lateinit var closeable: AutoCloseable
    private lateinit var controller: WalletCoreDocumentsControllerImpl
    private lateinit var authboundVciConfig: VciConfig

    @Before
    fun setup() {
        closeable = MockitoAnnotations.openMocks(this)
        authboundVciConfig = VciConfig(
            config = OpenId4VciManager.Config.Builder()
                .withIssuerUrl(ISSUER_URL)
                .withClientAuthenticationType(OpenId4VciManager.ClientAuthenticationType.AttestationBased)
                .withAuthFlowRedirectionURI("authbound://issue")
                .build(),
            order = 0,
            walletProviderConfig = AuthboundWalletProviderConfig(
                baseUrl = "https://mobile-backend.authbound.io/api/mobile/wallet-provider"
            )
        )
        whenever(resourceProvider.getString(any())).thenReturn("Issuance failed")
        whenever(resourceProvider.genericErrorMessage()).thenReturn("Generic error")
        val eudiWalletConfig: EudiWalletConfig = eudiWalletConfig()
        whenever(eudiWallet.config).thenReturn(eudiWalletConfig)
        whenever(walletCoreConfig.config).thenReturn(eudiWalletConfig)
        whenever(walletCoreConfig.issuersConfig).thenReturn(listOf(authboundVciConfig))
        whenever(walletCoreConfig.documentIssuanceConfig).thenReturn(
            DocumentIssuanceConfig(
                defaultRule = DocumentIssuanceRule(
                    policy = CredentialPolicy.RotateUse,
                    numberOfCredentials = 1
                ),
                documentSpecificRules = emptyMap()
            )
        )
        whenever(issuerOpenId4VciManagerFactory.create(eudiWallet, authboundVciConfig))
            .thenReturn(manager)
        controller = WalletCoreDocumentsControllerImpl(
            resourceProvider = resourceProvider,
            eudiWallet = eudiWallet,
            walletCoreConfig = walletCoreConfig,
            issuerOpenId4VciManagerFactory = issuerOpenId4VciManagerFactory,
            bookmarkDao = bookmarkDao,
            transactionLogDao = transactionLogDao,
            revokedDocumentDao = revokedDocumentDao,
            ownershipController = ownershipController,
            logController = logController
        )
    }

    @Test
    fun `Given Authbound offer, When issuance starts, Then platform auth is requested before consuming offer`() =
        coroutineRule.runTest {
            val offer: Offer = authboundOffer()

            controller.issueDocumentsByOffer(offer).runFlowTest {
                val result: IssueDocumentsPartialState = awaitItem()

                assertTrue(result is IssueDocumentsPartialState.UserAuthRequired)
                assertNull((result as IssueDocumentsPartialState.UserAuthRequired).crypto.cryptoObject)
                verify(manager, never()).issueDocumentByOffer(
                    offer = eq(offer),
                    txCode = anyOrNull(),
                    executor = anyOrNull(),
                    onIssueEvent = any()
                )

                result.resultHandler.onAuthenticationSuccess()

                verify(manager).issueDocumentByOffer(
                    offer = eq(offer),
                    txCode = anyOrNull(),
                    executor = anyOrNull(),
                    onIssueEvent = any()
                )
            }
        }

    @Test
    fun `Given Authbound issuance started, When WUA auth is requested, Then issuance is not restarted`() =
        coroutineRule.runTest {
            val offer: Offer = authboundOffer()
            doAnswer { invocation ->
                val listener: OpenId4VciManager.OnIssueEvent =
                    invocation.getArgument<OpenId4VciManager.OnIssueEvent>(3)
                listener.onResult(IssueEvent.Started(total = 1))
                listener.onResult(IssueEvent.Failure(WuaProofUserAuthRequiredException()))
                Unit
            }.whenever(manager).issueDocumentByOffer(
                offer = eq(offer),
                txCode = anyOrNull(),
                executor = anyOrNull(),
                onIssueEvent = any()
            )

            controller.issueDocumentsByOffer(offer).runFlowTest {
                val authRequired: IssueDocumentsPartialState.UserAuthRequired =
                    awaitItem() as IssueDocumentsPartialState.UserAuthRequired
                authRequired.resultHandler.onAuthenticationSuccess()

                val failure: IssueDocumentsPartialState = awaitItem()

                assertTrue(failure is IssueDocumentsPartialState.Failure)
                verify(manager, times(1)).issueDocumentByOffer(
                    offer = eq(offer),
                    txCode = anyOrNull(),
                    executor = anyOrNull(),
                    onIssueEvent = any()
                )
            }
        }

    @Test
    fun `Given non Authbound issuance started, When WUA auth is requested, Then issuance can retry`() =
        coroutineRule.runTest {
            val offer: Offer = authboundOffer()
            val euReferenceVciConfig: VciConfig = euReferenceVciConfig()
            val invocationCount: AtomicInteger = AtomicInteger(0)
            whenever(walletCoreConfig.issuersConfig).thenReturn(listOf(euReferenceVciConfig))
            whenever(issuerOpenId4VciManagerFactory.create(eudiWallet, euReferenceVciConfig))
                .thenReturn(manager)
            doAnswer { invocation ->
                if (invocationCount.incrementAndGet() == 1) {
                    val listener: OpenId4VciManager.OnIssueEvent =
                        invocation.getArgument<OpenId4VciManager.OnIssueEvent>(3)
                    listener.onResult(IssueEvent.Started(total = 1))
                    listener.onResult(IssueEvent.Failure(WuaProofUserAuthRequiredException()))
                }
                Unit
            }.whenever(manager).issueDocumentByOffer(
                offer = eq(offer),
                txCode = anyOrNull(),
                executor = anyOrNull(),
                onIssueEvent = any()
            )

            controller.issueDocumentsByOffer(offer).runFlowTest {
                val authRequired: IssueDocumentsPartialState.UserAuthRequired =
                    awaitItem() as IssueDocumentsPartialState.UserAuthRequired
                authRequired.resultHandler.onAuthenticationSuccess()

                verify(manager, times(2)).issueDocumentByOffer(
                    offer = eq(offer),
                    txCode = anyOrNull(),
                    executor = anyOrNull(),
                    onIssueEvent = any()
                )
            }
        }

    private fun eudiWalletConfig(): EudiWalletConfig {
        return EudiWalletConfig {
            configureDocumentKeyCreation(
                userAuthenticationRequired = true,
                userAuthenticationTimeout = 30.seconds,
                useStrongBoxForKeys = false
            )
        }
    }

    private fun euReferenceVciConfig(): VciConfig {
        return VciConfig(
            config = OpenId4VciManager.Config.Builder()
                .withIssuerUrl(ISSUER_URL)
                .withClientAuthenticationType(OpenId4VciManager.ClientAuthenticationType.AttestationBased)
                .withAuthFlowRedirectionURI("eudi-openid4ci://authorize")
                .build(),
            order = 0,
            walletProviderConfig = EuReferenceWalletProviderConfig()
        )
    }

    private fun authboundOffer(): Offer {
        val issuerId: CredentialIssuerId = CredentialIssuerId(ISSUER_URL).getOrThrow()
        val configurationId: CredentialConfigurationIdentifier =
            CredentialConfigurationIdentifier("authbound-pid")
        val metadata: CredentialIssuerMetadata = CredentialIssuerMetadata(
            credentialIssuerIdentifier = issuerId,
            credentialEndpoint = CredentialIssuerEndpoint("$ISSUER_URL/credential").getOrThrow(),
            credentialConfigurationsSupported = mapOf(
                configurationId to SdJwtVcCredential(
                    credentialMetadata = CredentialMetadata(),
                    type = "eu.europa.ec.eudi.pid.1",
                    proofTypesSupported = ProofTypesSupported(
                        setOf(
                            ProofTypeMeta.Attestation(
                                algorithms = listOf(JWSAlgorithm.ES256),
                                keyAttestationRequirement = KeyAttestationRequirement.RequiredNoConstraints
                            )
                        )
                    )
                )
            ),
            display = emptyList()
        )
        val credentialOffer: CredentialOffer = CredentialOffer(
            credentialIssuerIdentifier = issuerId,
            credentialIssuerMetadata = metadata,
            authorizationServerMetadata = mock<CIAuthorizationServerMetadata>(),
            credentialConfigurationIdentifiers = listOf(configurationId),
            grants = Grants.PreAuthorizedCode(preAuthorizedCode = "pre-authorized-code")
        )
        return Offer(credentialOffer)
    }

    private companion object {
        const val ISSUER_URL: String = "https://issuer.authbound.io/api/v1/openid4vci"
    }
}
