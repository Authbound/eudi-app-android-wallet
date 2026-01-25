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

package eu.europa.ec.quickidlogic.interactor

import eu.europa.ec.quickidlogic.model.PassportData
import eu.europa.ec.quickidlogic.model.VerificationResult
import eu.europa.ec.quickidlogic.repository.QuickIdRepository
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.toList
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

/**
 * Tests for VerificationInteractor state management and data handling.
 *
 * These tests verify:
 * 1. Passport data storage/retrieval maintains state correctly (singleton behavior)
 * 2. Verification flow emits correct states
 * 3. Error handling produces appropriate failure states
 */
class TestVerificationInteractor {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var quickIdRepository: QuickIdRepository

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    private lateinit var interactor: VerificationInteractor

    private lateinit var closeable: AutoCloseable

    private val testPassportData = PassportData(
        faceImageBytes = byteArrayOf(1, 2, 3),
        mrzRaw = "dGVzdCBtcnogZGF0YQ==",
        passportExpiry = "2035-06-15",
        firstName = "John",
        lastName = "Doe",
        dateOfBirth = "1990-05-20",
        documentNumber = "AB123456",
        nationality = "FIN"
    )

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        interactor = VerificationInteractorImpl(
            quickIdRepository = quickIdRepository,
            resourceProvider = resourceProvider
        )
        whenever(resourceProvider.genericErrorMessage()).thenReturn("Generic error")
    }

    @After
    fun after() {
        closeable.close()
    }

    // ==================== Passport Data Storage Tests ====================
    // Critical: Interactor is @Single scoped, so state must persist correctly

    @Test
    fun `stored passport data can be retrieved`() {
        interactor.storePassportData(testPassportData)

        val retrieved = interactor.getStoredPassportData()

        assertNotNull(retrieved)
        assertEquals("John", retrieved?.firstName)
        assertEquals("Doe", retrieved?.lastName)
        assertEquals("AB123456", retrieved?.documentNumber)
    }

    @Test
    fun `getStoredPassportData returns null when nothing stored`() {
        val retrieved = interactor.getStoredPassportData()

        assertNull(retrieved)
    }

    @Test
    fun `clearStoredPassportData removes stored data`() {
        interactor.storePassportData(testPassportData)
        assertNotNull(interactor.getStoredPassportData())

        interactor.clearStoredPassportData()

        assertNull(interactor.getStoredPassportData())
    }

    @Test
    fun `storing new passport data replaces previous data`() {
        val firstData = testPassportData.copy(firstName = "Alice")
        val secondData = testPassportData.copy(firstName = "Bob")

        interactor.storePassportData(firstData)
        assertEquals("Alice", interactor.getStoredPassportData()?.firstName)

        interactor.storePassportData(secondData)
        assertEquals("Bob", interactor.getStoredPassportData()?.firstName)
    }

    // ==================== Verification State Flow Tests ====================

    @Test
    fun `submitVerification emits InProgress first`() = coroutineRule.runTest {
        whenever(quickIdRepository.submitVerification(any(), any()))
            .thenReturn(Result.success(
                VerificationResult.Verified(
                    sessionId = "session-123",
                    firstName = "John",
                    lastName = "Doe",
                    dateOfBirth = "1990-05-20",
                    nationality = "FIN",
                    documentNumber = "AB123456",
                    expiryDate = "2035-06-15"
                )
            ))

        val states = interactor.submitVerification(testPassportData, "liveness-456").toList()

        assertTrue(states.isNotEmpty())
        assertTrue(states.first() is VerificationPartialState.InProgress)
        assertEquals("Verifying your identity...", (states.first() as VerificationPartialState.InProgress).message)
    }

    @Test
    fun `submitVerification emits Verified on success`() = coroutineRule.runTest {
        val expectedResult = VerificationResult.Verified(
            sessionId = "session-123",
            firstName = "John",
            lastName = "Doe",
            dateOfBirth = "1990-05-20",
            nationality = "FIN",
            documentNumber = "AB123456",
            expiryDate = "2035-06-15"
        )
        whenever(quickIdRepository.submitVerification(any(), any()))
            .thenReturn(Result.success(expectedResult))

        val states = interactor.submitVerification(testPassportData, "liveness-456").toList()

        assertEquals(2, states.size)
        val verifiedState = states[1] as VerificationPartialState.Verified
        assertEquals("session-123", verifiedState.result.sessionId)
        assertEquals("John", verifiedState.result.firstName)
    }

    @Test
    fun `submitVerification emits Rejected when verification rejected`() = coroutineRule.runTest {
        val rejectedResult = VerificationResult.Rejected(
            sessionId = "session-123",
            errorCode = "FACE_MISMATCH",
            errorMessage = "Face does not match passport photo",
            isRecoverable = true
        )
        whenever(quickIdRepository.submitVerification(any(), any()))
            .thenReturn(Result.success(rejectedResult))

        val states = interactor.submitVerification(testPassportData, "liveness-456").toList()

        assertEquals(2, states.size)
        val rejectedState = states[1] as VerificationPartialState.Rejected
        assertEquals("FACE_MISMATCH", rejectedState.result.errorCode)
        assertTrue(rejectedState.result.isRecoverable)
    }

    @Test
    fun `submitVerification emits Failure on repository error`() = coroutineRule.runTest {
        whenever(quickIdRepository.submitVerification(any(), any()))
            .thenReturn(Result.failure(Exception("Network timeout")))

        val states = interactor.submitVerification(testPassportData, "liveness-456").toList()

        assertEquals(2, states.size)
        val failureState = states[1] as VerificationPartialState.Failure
        assertEquals("Network timeout", failureState.errorMessage)
    }

    @Test
    fun `submitVerification uses generic error when exception has no message`() = coroutineRule.runTest {
        whenever(quickIdRepository.submitVerification(any(), any()))
            .thenReturn(Result.failure(Exception()))

        val states = interactor.submitVerification(testPassportData, "liveness-456").toList()

        val failureState = states[1] as VerificationPartialState.Failure
        assertEquals("Generic error", failureState.errorMessage)
    }

    // ==================== Credential Issuance State Flow Tests ====================

    @Test
    fun `issueCredential emits InProgress first`() = coroutineRule.runTest {
        whenever(quickIdRepository.issueCredential())
            .thenReturn(Result.success("openid-credential-offer://..."))

        val states = interactor.issueCredential().toList()

        assertTrue(states.isNotEmpty())
        assertTrue(states.first() is CredentialIssuancePartialState.InProgress)
    }

    @Test
    fun `issueCredential emits CredentialOfferReceived on success`() = coroutineRule.runTest {
        val expectedUri = "openid-credential-offer://issuer.example.com?credential_offer=xyz"
        whenever(quickIdRepository.issueCredential())
            .thenReturn(Result.success(expectedUri))

        val states = interactor.issueCredential().toList()

        assertEquals(2, states.size)
        val successState = states[1] as CredentialIssuancePartialState.CredentialOfferReceived
        assertEquals(expectedUri, successState.credentialOfferUri)
    }

    @Test
    fun `issueCredential emits Failure on repository error`() = coroutineRule.runTest {
        whenever(quickIdRepository.issueCredential())
            .thenReturn(Result.failure(Exception("Session expired")))

        val states = interactor.issueCredential().toList()

        assertEquals(2, states.size)
        val failureState = states[1] as CredentialIssuancePartialState.Failure
        assertEquals("Session expired", failureState.errorMessage)
    }
}
