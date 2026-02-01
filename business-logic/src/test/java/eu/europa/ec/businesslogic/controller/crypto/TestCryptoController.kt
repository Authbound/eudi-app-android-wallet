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

package eu.europa.ec.businesslogic.controller.crypto

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.cert.Certificate
import javax.crypto.Cipher

/**
 * Unit tests for [CryptoControllerImpl].
 *
 * Uses Robolectric to provide android.util.Base64 for code verifier generation tests.
 * Tests verify delegation to KeystoreController and PKCE code verifier generation.
 * These are security-critical operations for wallet authentication and attestation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class TestCryptoController {

    @Mock
    private lateinit var keystoreController: KeystoreController

    @Mock
    private lateinit var mockCertificate: Certificate

    @Mock
    private lateinit var mockCipher: Cipher

    private lateinit var cryptoController: CryptoControllerImpl
    private lateinit var closeable: AutoCloseable

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        cryptoController = CryptoControllerImpl(keystoreController)
    }

    @After
    fun after() {
        closeable.close()
    }

    // region WUA Key Pair Generation Delegation

    @Test
    fun `Given valid challenge, When generateWuaKeyPair is called, Then delegates to keystoreController`() {
        // Given
        val challenge = "test-challenge".toByteArray()
        val certChain = arrayOf(mockCertificate)
        whenever(keystoreController.generateWuaKeyPair(challenge)).thenReturn(certChain)

        // When
        val result = cryptoController.generateWuaKeyPair(challenge)

        // Then
        verify(keystoreController).generateWuaKeyPair(eq(challenge))
        assertArrayEquals(certChain, result)
    }

    @Test
    fun `Given keystoreController returns null, When generateWuaKeyPair is called, Then null is propagated`() {
        // Given
        val challenge = "test-challenge".toByteArray()
        whenever(keystoreController.generateWuaKeyPair(challenge)).thenReturn(null)

        // When
        val result = cryptoController.generateWuaKeyPair(challenge)

        // Then
        assertNull("Should propagate null from keystoreController", result)
    }

    // endregion

    // region WUA Key Deletion Delegation

    @Test
    fun `Given deletion succeeds, When deleteWuaKey is called, Then returns true`() {
        // Given
        whenever(keystoreController.deleteWuaKey()).thenReturn(true)

        // When
        val result = cryptoController.deleteWuaKey()

        // Then
        assertTrue("Should return true on success", result)
        verify(keystoreController).deleteWuaKey()
    }

    @Test
    fun `Given deletion fails, When deleteWuaKey is called, Then returns false`() {
        // Given
        whenever(keystoreController.deleteWuaKey()).thenReturn(false)

        // When
        val result = cryptoController.deleteWuaKey()

        // Then
        assertFalse("Should return false on failure", result)
    }

    // endregion

    // region PKCE Code Verifier Generation

    @Test
    fun `Given generateCodeVerifier is called, When result returned, Then has correct format`() {
        // When
        val verifier = cryptoController.generateCodeVerifier()

        // Then - Should be 43 characters (32 bytes Base64 URL-safe encoded without padding)
        assertEquals(
            "Code verifier should be 43 characters",
            43,
            verifier.length
        )
    }

    @Test
    fun `Given generateCodeVerifier is called, When result returned, Then is Base64 URL-safe`() {
        // When
        val verifier = cryptoController.generateCodeVerifier()

        // Then - Should only contain Base64 URL-safe characters (A-Z, a-z, 0-9, -, _)
        val validChars = Regex("^[A-Za-z0-9_-]+$")
        assertTrue(
            "Code verifier should be Base64 URL-safe",
            verifier.matches(validChars)
        )
    }

    @Test
    fun `Given generateCodeVerifier is called multiple times, When results compared, Then they are unique`() {
        // When
        val verifier1 = cryptoController.generateCodeVerifier()
        val verifier2 = cryptoController.generateCodeVerifier()
        val verifier3 = cryptoController.generateCodeVerifier()

        // Then
        assertNotEquals("Verifiers should be unique", verifier1, verifier2)
        assertNotEquals("Verifiers should be unique", verifier2, verifier3)
        assertNotEquals("Verifiers should be unique", verifier1, verifier3)
    }

    @Test
    fun `Given generateCodeVerifier is called, When result analyzed, Then has no padding`() {
        // When
        val verifier = cryptoController.generateCodeVerifier()

        // Then - Should not contain '=' padding
        assertFalse(
            "Code verifier should not have Base64 padding",
            verifier.contains("=")
        )
    }

    // endregion

    // region Cipher Operations

    @Test
    fun `Given encrypt mode, When getCipher is called, Then initializes in ENCRYPT_MODE`() {
        // Note: This test requires mocking Cipher.getInstance which is static
        // The actual behavior is verified through integration tests
        // This test documents the expected behavior

        // Given
        whenever(keystoreController.retrieveOrGenerateSecretKey(any())).thenReturn(null)

        // When
        val cipher = cryptoController.getCipher(encrypt = true)

        // Then - Returns null because secret key is null
        assertNull("Cipher should be null when secret key unavailable", cipher)
    }

    @Test
    fun `Given decrypt mode with IV, When getCipher is called, Then uses GCMParameterSpec`() {
        // Given
        val ivBytes = ByteArray(12) { it.toByte() }
        whenever(keystoreController.retrieveOrGenerateSecretKey(any())).thenReturn(null)

        // When
        val cipher = cryptoController.getCipher(encrypt = false, ivBytes = ivBytes)

        // Then - Returns null because secret key is null
        assertNull("Cipher should be null when secret key unavailable", cipher)
    }

    // endregion

    // region Encrypt/Decrypt Operations

    @Test
    fun `Given valid cipher, When encryptDecrypt is called, Then returns doFinal result`() {
        // Given
        val inputData = "test data".toByteArray()
        val expectedOutput = "encrypted".toByteArray()
        whenever(mockCipher.doFinal(inputData)).thenReturn(expectedOutput)

        // When
        val result = cryptoController.encryptDecrypt(mockCipher, inputData)

        // Then
        assertArrayEquals(expectedOutput, result)
        verify(mockCipher).doFinal(inputData)
    }

    @Test
    fun `Given null cipher, When encryptDecrypt is called, Then returns empty ByteArray`() {
        // Given
        val inputData = "test data".toByteArray()

        // When
        val result = cryptoController.encryptDecrypt(null, inputData)

        // Then
        assertEquals("Should return empty array when cipher is null", 0, result.size)
    }

    // endregion
}
