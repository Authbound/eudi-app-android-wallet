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

package eu.europa.ec.authenticationlogic.usecase

import eu.europa.ec.authenticationlogic.controller.storage.RecoveryCheckpointController
import eu.europa.ec.authenticationlogic.storage.LocalAuthKeys
import eu.europa.ec.businesslogic.controller.storage.PrefKeysV2
import eu.europa.ec.businesslogic.controller.storage.PrefsControllerV2
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import junit.framework.TestCase.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TestFinalizeWalletActivationStateUseCase {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var prefKeys: PrefKeysV2

    @Mock
    private lateinit var prefsController: PrefsControllerV2

    @Mock
    private lateinit var recoveryCheckpointController: RecoveryCheckpointController

    private lateinit var useCase: FinalizeWalletActivationStateUseCase
    private lateinit var closeable: AutoCloseable

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        useCase = FinalizeWalletActivationStateUseCaseImpl(
            prefKeys = prefKeys,
            prefsController = prefsController,
            recoveryCheckpointController = recoveryCheckpointController
        )
    }

    @After
    fun after() {
        closeable.close()
    }

    @Test
    fun `Given finalization succeeds, When invoked, Then local activation state is persisted in order`() =
        coroutineRule.runTest {
            val result = useCase()

            assertTrue(result.isSuccess)
            val inOrder = inOrder(prefKeys, prefsController, recoveryCheckpointController)
            inOrder.verify(prefKeys).setWalletActivated(true)
            inOrder.verify(prefsController).setBool(LocalAuthKeys.ENROLLMENT_REQUIRED, true)
            inOrder.verify(recoveryCheckpointController).clearCheckpoint()
        }

    @Test
    fun `Given wallet activation flag persistence fails, When invoked, Then later state writes are skipped and failure is returned`() =
        coroutineRule.runTest {
            doThrow(SecurityException("persist failed"))
                .whenever(prefKeys)
                .setWalletActivated(true)

            val result = useCase()

            assertTrue(result.isFailure)
            verify(prefsController, never()).setBool(LocalAuthKeys.ENROLLMENT_REQUIRED, true)
            verify(recoveryCheckpointController, never()).clearCheckpoint()
        }
}
