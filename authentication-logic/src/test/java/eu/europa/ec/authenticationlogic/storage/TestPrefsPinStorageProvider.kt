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

package eu.europa.ec.authenticationlogic.storage

import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import eu.europa.ec.businesslogic.controller.storage.PrefsControllerV2
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import java.security.KeyStore
import java.security.KeyStoreException
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TestPrefsPinStorageProvider {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var prefsController: PrefsControllerV2

    @Mock
    private lateinit var cryptoController: CryptoController

    @Mock
    private lateinit var keyStore: KeyStore

    private lateinit var provider: PrefsPinStorageProvider
    private lateinit var closeable: AutoCloseable

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        whenever(keyStore.containsAlias(any())).thenReturn(false)
        provider = PrefsPinStorageProvider(
            prefsController = prefsController,
            cryptoController = cryptoController,
            keyStoreProvider = { keyStore }
        )
    }

    @After
    fun after() {
        closeable.close()
    }

    @Test
    fun `Given no captured user id, When clearPinData is called, Then no session-scoped pref clear is attempted`() =
        coroutineRule.runTest {
            provider.clearPinData(null)

            verify(prefsController, never()).clear(any())
        }

    @Test
    fun `Given local auth alias deletion fails, When clearPinData is called, Then a security error is surfaced`() =
        coroutineRule.runTest {
            whenever(keyStore.containsAlias(any())).thenReturn(true)
            doThrow(KeyStoreException("delete failed")).whenever(keyStore).deleteEntry(any())

            assertThrows(SecurityException::class.java) {
                kotlinx.coroutines.runBlocking {
                    provider.clearPinData(null)
                }
            }
        }
}
