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

package eu.europa.ec.commonfeature.ui.pin

import eu.europa.ec.authenticationlogic.usecase.SignOutUseCase
import eu.europa.ec.businesslogic.validator.Form
import eu.europa.ec.businesslogic.validator.FormValidationResult
import eu.europa.ec.commonfeature.interactor.BiometricInteractor
import eu.europa.ec.commonfeature.interactor.QuickPinInteractor
import eu.europa.ec.commonfeature.model.PinFlow
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.serializer.UiSerializer
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

/**
 * Unit tests for [PinViewModel].
 *
 * Tests the three PIN flows:
 * - CREATE: New PIN creation (enter → re-enter → save)
 * - UPDATE: PIN change (validate current → enter new → re-enter new → save)
 * - VERIFY: PIN verification (validate → dashboard)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestPinViewModel {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @get:Rule
    val coroutineRule = CoroutineTestRule(testDispatcher, testScope)

    @Mock
    private lateinit var interactor: QuickPinInteractor

    @Mock
    private lateinit var biometricInteractor: BiometricInteractor

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    @Mock
    private lateinit var uiSerializer: UiSerializer

    @Mock
    private lateinit var signOutUseCase: SignOutUseCase

    private lateinit var closeable: AutoCloseable

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        // Set Main dispatcher for ViewModel's viewModelScope
        Dispatchers.setMain(testDispatcher)
        setupResourceProvider()
        runBlocking {
            whenever(biometricInteractor.getBiometricUserSelection()).thenReturn(false)
            whenever(biometricInteractor.getBiometricsPreferenceDecided()).thenReturn(true)
        }
    }

    @After
    fun after() {
        Dispatchers.resetMain()
        closeable.close()
    }

    //region CREATE Flow - Initial State Tests

    // Case 1: CREATE flow initial state
    @Test
    fun `Given CREATE flow, When initialized, Then state is ENTER`() {
        // Given/When
        val viewModel = createViewModel(PinFlow.CREATE)

        // Then
        assertEquals(PinValidationState.ENTER, viewModel.viewState.value.pinState)
        assertEquals("Create PIN", viewModel.viewState.value.title)
    }

    // Case 2: CREATE flow has NONE action (no cancel button)
    @Test
    fun `Given CREATE flow, When checking action, Then returns NONE`() {
        // Given/When
        val viewModel = createViewModel(PinFlow.CREATE)

        // Then
        assertEquals(ScreenNavigateAction.NONE, viewModel.viewState.value.action)
    }

    // Case 3: CREATE flow ENTER → REENTER transition
    @Test
    fun `Given CREATE ENTER state, When NextButtonPressed, Then transitions to REENTER`() =
        coroutineRule.runTest {
            // Given
            val viewModel = createViewModel(PinFlow.CREATE)
            assertEquals(PinValidationState.ENTER, viewModel.viewState.value.pinState)

            // When
            viewModel.setEvent(Event.NextButtonPressed(pin = "123456"))
            testScope.advanceUntilIdle()

            // Then
            assertEquals(PinValidationState.REENTER, viewModel.viewState.value.pinState)
            assertEquals("123456", viewModel.viewState.value.enteredPin)
        }

    //endregion

    //region UPDATE Flow - Initial State Tests

    // Case 4: UPDATE flow initial state
    @Test
    fun `Given UPDATE flow, When initialized, Then state is VALIDATE`() {
        // Given/When
        val viewModel = createViewModel(PinFlow.UPDATE)

        // Then
        assertEquals(PinValidationState.VALIDATE, viewModel.viewState.value.pinState)
        assertEquals("Change PIN", viewModel.viewState.value.title)
    }

    // Case 5: UPDATE flow has CANCELABLE action
    @Test
    fun `Given UPDATE flow, When checking action, Then returns CANCELABLE`() {
        // Given/When
        val viewModel = createViewModel(PinFlow.UPDATE)

        // Then
        assertEquals(ScreenNavigateAction.CANCELABLE, viewModel.viewState.value.action)
    }

    //endregion

    //region VERIFY Flow - Initial State Tests

    // Case 6: VERIFY flow initial state
    @Test
    fun `Given VERIFY flow, When initialized, Then state is VALIDATE`() {
        // Given/When
        val viewModel = createViewModel(PinFlow.VERIFY)

        // Then
        assertEquals(PinValidationState.VALIDATE, viewModel.viewState.value.pinState)
        assertEquals("Verify PIN", viewModel.viewState.value.title)
    }

    // Case 7: VERIFY flow has NONE action
    @Test
    fun `Given VERIFY flow, When checking action, Then returns NONE`() {
        // Given/When
        val viewModel = createViewModel(PinFlow.VERIFY)

        // Then
        assertEquals(ScreenNavigateAction.NONE, viewModel.viewState.value.action)
    }

    //endregion

    //region Pin Validation Tests

    // Case 8: Valid PIN enables button
    @Test
    fun `Given valid 6-digit PIN, When OnQuickPinEntered, Then button is enabled`() =
        coroutineRule.runTest {
            // Given
            whenever(interactor.validateForm(any<Form>()))
                .thenReturn(FormValidationResult(isValid = true))
            val viewModel = createViewModel(PinFlow.CREATE)

            // When
            viewModel.setEvent(Event.OnQuickPinEntered("123456"))
            testScope.advanceUntilIdle()

            // Then
            assertTrue(viewModel.viewState.value.isButtonEnabled)
        }

    // Case 9: Invalid PIN disables button
    @Test
    fun `Given invalid short PIN, When OnQuickPinEntered, Then button is disabled`() =
        coroutineRule.runTest {
            // Given
            whenever(interactor.validateForm(any<Form>()))
                .thenReturn(FormValidationResult(isValid = false))
            val viewModel = createViewModel(PinFlow.CREATE)

            // When
            viewModel.setEvent(Event.OnQuickPinEntered("12345"))
            testScope.advanceUntilIdle()

            // Then
            assertFalse(viewModel.viewState.value.isButtonEnabled)
        }

    // Case 10: Non-numeric PIN shows validation error
    @Test
    fun `Given non-numeric PIN, When OnQuickPinEntered, Then shows error`() =
        coroutineRule.runTest {
            // Given
            whenever(interactor.validateForm(any<Form>()))
                .thenReturn(FormValidationResult(isValid = false, message = "Only numbers allowed"))
            val viewModel = createViewModel(PinFlow.CREATE)

            // When
            viewModel.setEvent(Event.OnQuickPinEntered("abc123"))
            testScope.advanceUntilIdle()

            // Then
            assertFalse(viewModel.viewState.value.isButtonEnabled)
            assertEquals("Only numbers allowed", viewModel.viewState.value.quickPinError)
        }

    //endregion

    //region State Properties Tests

    // Case 11: quickPinSize is 6
    @Test
    fun `Given any flow, When checking quickPinSize, Then returns 6`() {
        // Given/When
        val viewModel = createViewModel(PinFlow.CREATE)

        // Then
        assertEquals(6, viewModel.viewState.value.quickPinSize)
    }

    // Case 12: Initial isLoading is false
    @Test
    fun `Given new ViewModel, When created, Then isLoading is false`() {
        // Given/When
        val viewModel = createViewModel(PinFlow.CREATE)

        // Then
        assertFalse(viewModel.viewState.value.isLoading)
    }

    // Case 13: Initial isButtonEnabled is false
    @Test
    fun `Given new ViewModel, When created, Then isButtonEnabled is false`() {
        // Given/When
        val viewModel = createViewModel(PinFlow.CREATE)

        // Then
        assertFalse(viewModel.viewState.value.isButtonEnabled)
    }

    // Case 14: Initial pin is empty
    @Test
    fun `Given new ViewModel, When created, Then pin is empty`() {
        // Given/When
        val viewModel = createViewModel(PinFlow.CREATE)

        // Then
        assertEquals("", viewModel.viewState.value.pin)
    }

    // Case 15: Initial resetPin is false
    @Test
    fun `Given new ViewModel, When created, Then resetPin is false`() {
        // Given/When
        val viewModel = createViewModel(PinFlow.CREATE)

        // Then
        assertFalse(viewModel.viewState.value.resetPin)
    }

    //endregion

    //region onBackEvent Tests

    // Case 16: CREATE flow onBackEvent is Finish
    @Test
    fun `Given CREATE flow, When checking onBackEvent, Then returns Finish`() {
        // Given/When
        val viewModel = createViewModel(PinFlow.CREATE)

        // Then
        assertEquals(Event.Finish, viewModel.viewState.value.onBackEvent)
    }

    // Case 17: UPDATE flow onBackEvent is CancelPressed
    @Test
    fun `Given UPDATE flow, When checking onBackEvent, Then returns CancelPressed`() {
        // Given/When
        val viewModel = createViewModel(PinFlow.UPDATE)

        // Then
        assertEquals(Event.CancelPressed, viewModel.viewState.value.onBackEvent)
    }

    // Case 18: VERIFY flow onBackEvent is Finish
    @Test
    fun `Given VERIFY flow, When checking onBackEvent, Then returns Finish`() {
        // Given/When
        val viewModel = createViewModel(PinFlow.VERIFY)

        // Then
        assertEquals(Event.Finish, viewModel.viewState.value.onBackEvent)
    }

    //endregion

    //region Button Text Tests

    // Case 19: ENTER state button text is "Next"
    @Test
    fun `Given ENTER state, When checking buttonText, Then returns Next`() {
        // Given/When
        val viewModel = createViewModel(PinFlow.CREATE)

        // Then
        assertEquals("Next", viewModel.viewState.value.buttonText)
    }

    // Case 20: VERIFY flow button text is "Verify"
    @Test
    fun `Given VERIFY flow, When checking buttonText, Then returns Verify`() {
        // Given/When
        val viewModel = createViewModel(PinFlow.VERIFY)

        // Then
        assertEquals("Verify", viewModel.viewState.value.buttonText)
    }

    // Case 21: REENTER state button text is "Confirm"
    @Test
    fun `Given REENTER state, When checking buttonText, Then returns Confirm`() =
        coroutineRule.runTest {
            // Given
            val viewModel = createViewModel(PinFlow.CREATE)
            viewModel.setEvent(Event.NextButtonPressed(pin = "123456"))
            testScope.advanceUntilIdle()

            // Then
            assertEquals(PinValidationState.REENTER, viewModel.viewState.value.pinState)
            assertEquals("Confirm", viewModel.viewState.value.buttonText)
        }

    //endregion

    //region Helper Methods

    private fun setupResourceProvider() {
        whenever(resourceProvider.getString(R.string.quick_pin_create_title)).thenReturn("Create PIN")
        whenever(resourceProvider.getString(R.string.quick_pin_create_enter_subtitle)).thenReturn("Enter subtitle")
        whenever(resourceProvider.getString(R.string.quick_pin_create_reenter_subtitle)).thenReturn("Re-enter subtitle")
        whenever(resourceProvider.getString(R.string.quick_pin_change_title)).thenReturn("Change PIN")
        whenever(resourceProvider.getString(R.string.quick_pin_change_validate_current_subtitle)).thenReturn("Current PIN subtitle")
        whenever(resourceProvider.getString(R.string.quick_pin_change_enter_new_subtitle)).thenReturn("New PIN subtitle")
        whenever(resourceProvider.getString(R.string.quick_pin_change_reenter_new_subtitle)).thenReturn("Re-enter new subtitle")
        whenever(resourceProvider.getString(R.string.quick_pin_verify_title)).thenReturn("Verify PIN")
        whenever(resourceProvider.getString(R.string.quick_pin_verify_subtitle)).thenReturn("Verify subtitle")
        whenever(resourceProvider.getString(R.string.generic_next_capitalized)).thenReturn("Next")
        whenever(resourceProvider.getString(R.string.generic_confirm_capitalized)).thenReturn("Confirm")
        whenever(resourceProvider.getString(R.string.generic_verify_capitalized)).thenReturn("Verify")
        whenever(resourceProvider.getString(R.string.quick_pin_numerical_rule_invalid_error_message)).thenReturn("Only numbers allowed")
    }

    private fun createViewModel(pinFlow: PinFlow): PinViewModel {
        return PinViewModel(
            interactor = interactor,
            biometricInteractor = biometricInteractor,
            resourceProvider = resourceProvider,
            uiSerializer = uiSerializer,
            signOutUseCase = signOutUseCase,
            pinFlow = pinFlow
        )
    }

    //endregion
}
