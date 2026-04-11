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

package eu.europa.ec.euidi.test

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import eu.europa.ec.assemblylogic.ui.MainActivity
import eu.europa.ec.businesslogic.model.error.WalletActivationError
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.navigation.RouterHost
import eu.europa.ec.uilogic.test.AuthTestTags
import eu.europa.ec.uilogic.test.DashboardTestTags
import eu.europa.ec.uilogic.test.StartupTestTags
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.mp.KoinPlatform
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class AuthGateSmokeTest {

    @get:Rule
    val composeRule: ComposeTestRule = createEmptyComposeRule()

    private val appContext: Context
        get() = ApplicationProvider.getApplicationContext()

    private var activityScenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        runCatching {
            activityScenario?.close()
        }
        activityScenario = null
        AuthScenarioDriver.reset()
    }

    @Test
    fun coldStart_unauthenticated_showsLoginScreen() {
        launchApp(state = AuthScenarioState())

        waitForTag(AuthTestTags.Login.ROOT)
    }

    @Test
    fun login_withValidCredentials_routesThroughAuthObserverToProfileCompletion() {
        launchApp(state = AuthScenarioState())

        waitForTag(AuthTestTags.Login.ROOT)
        composeRule.onNodeWithTag(resolvedTag(AuthTestTags.Login.EMAIL_FIELD), useUnmergedTree = true)
            .performTextInput("tester@authbound.io")
        composeRule.onNodeWithTag(resolvedTag(AuthTestTags.Login.PASSWORD_FIELD), useUnmergedTree = true)
            .performTextInput("Password123!")
        composeRule.onNodeWithTag(resolvedTag(AuthTestTags.Login.PRIMARY_BUTTON), useUnmergedTree = true)
            .performClick()

        waitForTag(AuthTestTags.ProfileCompletion.ROOT)
    }

    @Test
    fun coldStart_profileIncomplete_showsProfileCompletion() {
        launchApp(
            state = AuthScenarioState(
                authenticated = true,
            )
        )

        waitForTag(AuthTestTags.ProfileCompletion.ROOT)
    }

    @Test
    fun coldStart_deviceSecurityMissing_showsDeviceSecurityRequired() {
        launchApp(
            state = AuthScenarioState(
                authenticated = true,
                profileCompleted = true,
                deviceSecurityReady = false,
            )
        )

        waitForTag(AuthTestTags.DeviceSecurityRequired.ROOT)
    }

    @Test
    fun coldStart_walletNotActivated_routesToWalletSetup() {
        launchApp(
            state = walletSetupState()
        )

        waitForTag(AuthTestTags.WalletSetup.ROOT)
    }

    @Test
    fun coldStart_deviceSecurityRecovered_retryRoutesToWalletSetup() {
        launchApp(
            state = walletSetupState(
                deviceSecurityReady = false,
            )
        )

        waitForTag(AuthTestTags.DeviceSecurityRequired.ROOT)
        AuthScenarioDriver.setDeviceSecurityReady(true)
        composeRule.onNodeWithTag(
            resolvedTag(AuthTestTags.DeviceSecurityRequired.RETRY_BUTTON),
            useUnmergedTree = true,
        ).performClick()

        waitForTag(AuthTestTags.WalletSetup.ROOT)
        waitForTag(AuthTestTags.WalletSetup.LOADING_STATE)
    }

    @Test
    fun coldStart_walletSetupPending_showsLoadingStateAndBlocksForwardNavigation() {
        launchApp(state = walletSetupState())

        waitForTag(AuthTestTags.WalletSetup.ROOT)
        waitForTag(AuthTestTags.WalletSetup.LOADING_STATE)
        composeRule.onAllNodesWithTag(resolvedTag(AuthTestTags.QuickPin.ROOT), useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithTag(resolvedTag(DashboardTestTags.Screen.ROOT), useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun coldStart_walletSetupSuccess_routesToPinCreate() {
        launchApp(
            state = walletSetupState(
                walletSetupBehavior = WalletSetupBehavior.SUCCESS,
            )
        )

        waitForTag(AuthTestTags.QuickPin.ROOT)
        assertThat(AuthScenarioDriver.snapshot().walletActivated).isTrue()
    }

    @Test
    fun coldStart_walletSetupRetryableFailure_tryAgain_recoversToPinCreate() {
        launchApp(
            state = walletSetupState(
                walletSetupAttemptOutcomes = listOf(
                    WalletSetupAttemptOutcome.Failure(
                        WalletActivationError.NetworkFailure(
                            httpCode = 503,
                            cause = IOException("wallet setup smoke network failure"),
                        )
                    ),
                    WalletSetupAttemptOutcome.Success,
                )
            )
        )

        waitForTag(AuthTestTags.WalletSetup.ERROR_STATE)
        composeRule.onNodeWithTag(resolvedTag(AuthTestTags.WalletSetup.RETRY_BUTTON), useUnmergedTree = true)
            .performClick()

        waitForTag(AuthTestTags.QuickPin.ROOT)
        assertThat(AuthScenarioDriver.snapshot().walletActivated).isTrue()
    }

    @Test
    fun coldStart_walletSetupPermanentFailure_deleteWallet_returnsToLogin() {
        launchApp(
            state = walletSetupState(
                walletSetupAttemptOutcomes = listOf(
                    WalletSetupAttemptOutcome.Failure(
                        WalletActivationError.AttestationRejected(
                            reason = "device attestation rejected",
                            isDeviceIssue = true,
                        )
                    )
                )
            )
        )

        waitForTag(AuthTestTags.WalletSetup.ERROR_STATE)
        composeRule.onNodeWithTag(
            resolvedTag(AuthTestTags.WalletSetup.DELETE_WALLET_BUTTON),
            useUnmergedTree = true,
        ).performClick()
        waitForTag(AuthTestTags.WalletSetup.DELETE_CONFIRMATION_DIALOG)
        composeRule.onNodeWithTag(
            resolvedTag(AuthTestTags.WalletSetup.DELETE_CONFIRM_BUTTON),
            useUnmergedTree = true,
        ).performClick()

        waitForTag(AuthTestTags.Login.ROOT)
        assertThat(AuthScenarioDriver.snapshot().authenticated).isFalse()
    }

    @Test
    fun coldStart_walletSetupPending_backDismiss_keepsSetupInProgress() {
        launchApp(state = walletSetupState())

        waitForTag(AuthTestTags.WalletSetup.LOADING_STATE)
        pressSystemBack()
        waitForTag(AuthTestTags.WalletSetup.CANCEL_CONFIRMATION_DIALOG)
        composeRule.onNodeWithTag(
            resolvedTag(AuthTestTags.WalletSetup.CANCEL_DISMISS_BUTTON),
            useUnmergedTree = true,
        ).performClick()

        waitForTag(AuthTestTags.WalletSetup.ROOT)
        waitForTag(AuthTestTags.WalletSetup.LOADING_STATE)
    }

    @Test
    fun coldStart_walletSetupPending_backConfirm_returnsToLogin() {
        launchApp(state = walletSetupState())

        waitForTag(AuthTestTags.WalletSetup.LOADING_STATE)
        pressSystemBack()
        waitForTag(AuthTestTags.WalletSetup.CANCEL_CONFIRMATION_DIALOG)
        composeRule.onNodeWithTag(
            resolvedTag(AuthTestTags.WalletSetup.CANCEL_CONFIRM_BUTTON),
            useUnmergedTree = true,
        ).performClick()

        waitForTag(AuthTestTags.Login.ROOT)
        assertThat(AuthScenarioDriver.snapshot().authenticated).isFalse()
    }

    @Test
    fun coldStart_walletSetupPending_activityRecreate_staysBlockedOnSetup() {
        launchApp(state = walletSetupState())

        waitForTag(AuthTestTags.WalletSetup.LOADING_STATE)
        activityScenario?.recreate()
        composeRule.waitForIdle()

        waitForTag(AuthTestTags.WalletSetup.ROOT)
        waitForTag(AuthTestTags.WalletSetup.LOADING_STATE)
        composeRule.onAllNodesWithTag(resolvedTag(DashboardTestTags.Screen.ROOT), useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun coldStart_pinNotSet_createPinAndContinue_landsOnDashboard() {
        launchApp(
            state = AuthScenarioState(
                authenticated = true,
                profileCompleted = true,
                deviceSecurityReady = true,
                walletActivated = true,
                biometricsPreferenceDecided = true,
            )
        )

        waitForTag(AuthTestTags.QuickPin.ROOT)
        waitForText(appContext.getString(R.string.quick_pin_create_enter_subtitle))
        enterPin("123456")
        waitForText(appContext.getString(R.string.quick_pin_create_reenter_subtitle))
        enterPin("123456")

        waitForTag(DashboardTestTags.Screen.ROOT)
    }

    @Test
    fun returningLockedUser_correctPin_landsOnDashboard() {
        launchApp(state = lockedReturningUserState())

        waitForTag(AuthTestTags.QuickPin.ROOT)
        enterPin("123456")

        waitForTag(DashboardTestTags.Screen.ROOT)
    }

    @Test
    fun returningLockedUser_wrongPin_showsErrorAndStaysOnPinGate() {
        launchApp(state = lockedReturningUserState())

        waitForTag(AuthTestTags.QuickPin.ROOT)
        enterPin("654321")

        waitForTag(AuthTestTags.QuickPin.ERROR_MESSAGE)
        composeRule.onNodeWithTag(resolvedTag(AuthTestTags.QuickPin.ROOT), useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onAllNodesWithTag(resolvedTag(DashboardTestTags.Screen.ROOT), useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun backgroundResume_afterLocalUnlockExpiry_requiresPinAgain() {
        launchApp(
            state = lockedReturningUserState().copy(
                unlockDeadlineMillis = 61_000L,
            )
        )

        waitForTag(DashboardTestTags.Screen.ROOT)

        val activeScenario = requireNotNull(activityScenario)
        activeScenario.moveToState(Lifecycle.State.CREATED)
        AuthScenarioDriver.expireUnlockSession()
        val lifecycleFailure = assertThrows(AssertionError::class.java) {
            activeScenario.moveToState(Lifecycle.State.STARTED)
        }

        assertThat(lifecycleFailure).hasMessageThat().contains("last lifecycle transition = \"DESTROYED\"")

        waitForTag(AuthTestTags.QuickPin.ROOT)
    }

    @Test
    fun coldStart_withCredentialOfferDeepLink_doesNotBypassLogin() {
        launchApp(
            state = AuthScenarioState(),
            deepLink = Uri.parse("openid-credential-offer://test-offer")
        )

        waitForTag(AuthTestTags.Login.ROOT)
        composeRule.onAllNodesWithTag(resolvedTag(DashboardTestTags.Screen.ROOT), useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun coldStart_withMalformedDeepLink_doesNotBypassLogin() {
        launchApp(
            state = AuthScenarioState(),
            deepLink = Uri.parse("authbound://unexpected-host/callback?status=success")
        )

        waitForTag(AuthTestTags.Login.ROOT)
        composeRule.onAllNodesWithTag(resolvedTag(DashboardTestTags.Screen.ROOT), useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun returningLockedUser_withCredentialOfferDeepLink_stillRequiresPin() {
        launchApp(
            state = lockedReturningUserState(),
            deepLink = Uri.parse("openid-credential-offer://test-offer")
        )

        waitForTag(AuthTestTags.QuickPin.ROOT)
        composeRule.onAllNodesWithTag(resolvedTag(DashboardTestTags.Screen.ROOT), useUnmergedTree = true)
            .assertCountEquals(0)
    }

    private fun launchApp(
        state: AuthScenarioState,
        deepLink: Uri? = null,
    ) {
        activityScenario?.close()
        AuthScenarioDriver.reset(state)

        val launchIntent = Intent(
            if (deepLink == null) Intent.ACTION_MAIN else Intent.ACTION_VIEW,
            deepLink,
            appContext,
            MainActivity::class.java,
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage(appContext.packageName)
        }

        activityScenario = ActivityScenario.launch(launchIntent)
        activityScenario?.onActivity { activity ->
            if (activity.application !is AuthTestApplication) {
                throw AssertionError(
                    "AuthGateSmokeTest requires -Pandroid.testInstrumentationRunnerArguments.${AuthTestRunner.AUTH_TEST_APPLICATION_ARGUMENT}=true"
                )
            }
            assertThat(activity.application).isInstanceOf(AuthTestApplication::class.java)
        }
        composeRule.waitForIdle()
        waitForAnyAuthGateRoot()
    }

    private fun waitForTag(
        tag: String,
        timeoutMillis: Long = 10_000L,
    ) {
        try {
            composeRule.waitUntil(timeoutMillis) {
                hasVisibleNodesForTag(tag)
            }
        } catch (exception: ComposeTimeoutException) {
            throw AssertionError(
                "Timed out waiting for tag=$tag route=${currentRouteOrNull()} visibleRoots=${visibleAuthGateRoots()}",
                exception,
            )
        }

        composeRule.onNodeWithTag(resolvedTag(tag), useUnmergedTree = true).assertIsDisplayed()
    }

    private fun waitForText(
        text: String,
        timeoutMillis: Long = 10_000L,
    ) {
        composeRule.waitUntil(timeoutMillis) {
            hasVisibleNodesForText(text)
        }

        composeRule.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
    }

    private fun hasVisibleNodesForTag(tag: String): Boolean {
        return try {
            composeRule.onAllNodesWithTag(resolvedTag(tag), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        } catch (exception: IllegalStateException) {
            if (exception.message?.contains("No compose hierarchies found") == true) {
                false
            } else {
                throw exception
            }
        }
    }

    private fun hasVisibleNodesForText(text: String): Boolean {
        return try {
            composeRule.onAllNodesWithText(text, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        } catch (exception: IllegalStateException) {
            if (exception.message?.contains("No compose hierarchies found") == true) {
                false
            } else {
                throw exception
            }
        }
    }

    private fun enterPin(pin: String) {
        pin.forEach { char ->
            composeRule.onNodeWithTag(
                resolvedTag(AuthTestTags.QuickPin.digit(char.digitToInt())),
                useUnmergedTree = true,
            ).performClick()
        }
    }

    private fun pressSystemBack() {
        activityScenario?.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        } ?: throw AssertionError("Activity scenario is not active")
        composeRule.waitForIdle()
    }

    private fun lockedReturningUserState(): AuthScenarioState {
        return AuthScenarioState(
            authenticated = true,
            profileCompleted = true,
            deviceSecurityReady = true,
            walletActivated = true,
            storedPin = "123456",
            biometricsPreferenceDecided = true,
        )
    }

    private fun walletSetupState(
        deviceSecurityReady: Boolean = true,
        walletSetupBehavior: WalletSetupBehavior = WalletSetupBehavior.PENDING,
        walletSetupAttemptOutcomes: List<WalletSetupAttemptOutcome> = emptyList(),
    ): AuthScenarioState {
        return AuthScenarioState(
            authenticated = true,
            profileCompleted = true,
            deviceSecurityReady = deviceSecurityReady,
            walletActivated = false,
            walletSetupBehavior = walletSetupBehavior,
            walletSetupAttemptOutcomes = walletSetupAttemptOutcomes,
            biometricsPreferenceDecided = true,
        )
    }

    private fun waitForAnyAuthGateRoot(timeoutMillis: Long = 10_000L) {
        val tags = listOf(
            StartupTestTags.Splash.ROOT,
            AuthTestTags.Login.ROOT,
            AuthTestTags.ProfileCompletion.ROOT,
            AuthTestTags.DeviceSecurityRequired.ROOT,
            AuthTestTags.WalletSetup.ROOT,
            AuthTestTags.QuickPin.ROOT,
            DashboardTestTags.Screen.ROOT,
        )

        try {
            composeRule.waitUntil(timeoutMillis) {
                composeRule.onAllNodes(isRoot(), useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty() &&
                    tags.any { tag ->
                        composeRule.onAllNodesWithTag(resolvedTag(tag), useUnmergedTree = true)
                            .fetchSemanticsNodes().isNotEmpty()
                    }
            }
        } catch (exception: ComposeTimeoutException) {
            throw AssertionError(
                "Timed out waiting for any auth-gate root route=${currentRouteOrNull()} visibleRoots=${visibleAuthGateRoots()}",
                exception,
            )
        }
    }

    private fun resolvedTag(tag: String): String {
        return "${appContext.packageName}:id/$tag"
    }

    private fun currentRouteOrNull(): String? {
        return runCatching {
            KoinPlatform.getKoin().get<RouterHost>().getNavController().currentDestination?.route
        }.getOrNull()
    }

    private fun visibleAuthGateRoots(): List<String> {
        val tags = listOf(
            StartupTestTags.Splash.ROOT,
            AuthTestTags.Login.ROOT,
            AuthTestTags.ProfileCompletion.ROOT,
            AuthTestTags.DeviceSecurityRequired.ROOT,
            AuthTestTags.WalletSetup.ROOT,
            AuthTestTags.QuickPin.ROOT,
            DashboardTestTags.Screen.ROOT,
        )

        return tags.filter { tag ->
            composeRule.onAllNodesWithTag(resolvedTag(tag), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
