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

package io.authbound.wallet.test

import android.content.Context
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricAuthenticationController
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricPromptData
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricsAuthenticate
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricsAvailability
import eu.europa.ec.authenticationlogic.controller.storage.PinStorageController
import eu.europa.ec.authenticationlogic.gate.LocalUnlockTracker
import eu.europa.ec.authenticationlogic.model.BiometricCrypto
import eu.europa.ec.authenticationlogic.model.EmailPasswordRequest
import eu.europa.ec.authenticationlogic.model.LegalAcceptanceSnapshot
import eu.europa.ec.authenticationlogic.model.OAuthProvider
import eu.europa.ec.authenticationlogic.model.Profile
import eu.europa.ec.authenticationlogic.repository.SupabaseAuthRepository
import eu.europa.ec.authenticationlogic.usecase.CheckHandleAvailabilityUseCase
import eu.europa.ec.authenticationlogic.usecase.CompleteProfileUseCase
import eu.europa.ec.authenticationlogic.usecase.GetCurrentUserUseCase
import eu.europa.ec.authenticationlogic.usecase.IsProfileCompletedUseCase
import eu.europa.ec.authenticationlogic.usecase.IsWalletActivatedUseCase
import eu.europa.ec.authenticationlogic.usecase.ObserveAuthStateUseCase
import eu.europa.ec.authenticationlogic.usecase.SignInWithEmailPasswordUseCase
import eu.europa.ec.authenticationlogic.usecase.SignInWithOAuthUseCase
import eu.europa.ec.authenticationlogic.usecase.SignOutMode
import eu.europa.ec.authenticationlogic.usecase.SignOutUseCase
import eu.europa.ec.authenticationlogic.usecase.SignUpWithEmailPasswordUseCase
import eu.europa.ec.authenticationlogic.usecase.WalletActivationStatus
import eu.europa.ec.businesslogic.controller.device.DeviceController
import eu.europa.ec.businesslogic.controller.device.DeviceSecurityState
import eu.europa.ec.businesslogic.controller.storage.PrefKeysV2
import eu.europa.ec.businesslogic.controller.storage.PrefsControllerV2
import eu.europa.ec.businesslogic.controller.wallet.UserDocumentOwnershipController
import eu.europa.ec.businesslogic.model.DeviceInfo
import eu.europa.ec.businesslogic.validator.Form
import eu.europa.ec.businesslogic.validator.FormValidationResult
import eu.europa.ec.businesslogic.validator.FormsValidationResult
import eu.europa.ec.businesslogic.validator.model.FilterableList
import eu.europa.ec.businesslogic.validator.model.Filters
import eu.europa.ec.businesslogic.validator.model.SortOrder
import eu.europa.ec.commonfeature.interactor.BiometricInteractor
import eu.europa.ec.commonfeature.interactor.QuickPinInteractor
import eu.europa.ec.commonfeature.interactor.QuickPinInteractorPinValidPartialState
import eu.europa.ec.commonfeature.interactor.QuickPinInteractorSetPinPartialState
import eu.europa.ec.dashboardfeature.interactor.ActionsInteractor
import eu.europa.ec.dashboardfeature.interactor.ActionsInteractorPartialState
import eu.europa.ec.dashboardfeature.interactor.DashboardInteractor
import eu.europa.ec.dashboardfeature.interactor.DeviceLinkPartialState
import eu.europa.ec.dashboardfeature.interactor.DocumentInteractorDeleteDocumentPartialState
import eu.europa.ec.dashboardfeature.interactor.DocumentInteractorFilterPartialState
import eu.europa.ec.dashboardfeature.interactor.DocumentInteractorGetDocumentsPartialState
import eu.europa.ec.dashboardfeature.interactor.DocumentInteractorRetryIssuingDeferredDocumentsPartialState
import eu.europa.ec.dashboardfeature.interactor.DocumentsInteractor
import eu.europa.ec.dashboardfeature.interactor.HealthConnectionPartialState
import eu.europa.ec.dashboardfeature.interactor.HealthInteractor
import eu.europa.ec.dashboardfeature.interactor.HealthInteractorPartialState
import eu.europa.ec.dashboardfeature.interactor.HomeInteractor
import eu.europa.ec.dashboardfeature.interactor.HomeInteractorGetCredentialsPartialState
import eu.europa.ec.dashboardfeature.interactor.HomeInteractorGetHeroCredentialPartialState
import eu.europa.ec.dashboardfeature.interactor.HomeInteractorGetUserNameViaMainPidDocumentPartialState
import eu.europa.ec.dashboardfeature.interactor.SettingsInteractor
import eu.europa.ec.dashboardfeature.interactor.CredentialSummaryUi
import eu.europa.ec.dashboardfeature.model.verification.VerificationDraftAttribute
import eu.europa.ec.dashboardfeature.model.verification.VerificationRecipient
import eu.europa.ec.dashboardfeature.model.verification.VerificationRecipientSession
import eu.europa.ec.dashboardfeature.model.verification.VerificationRequestedAttribute
import eu.europa.ec.dashboardfeature.model.verification.VerificationRequester
import eu.europa.ec.dashboardfeature.model.verification.VerificationSession
import eu.europa.ec.dashboardfeature.model.verification.VerificationTemplate
import eu.europa.ec.dashboardfeature.repository.VerificationRepository
import eu.europa.ec.dashboardfeature.ui.actions.model.ActionRequest
import eu.europa.ec.dashboardfeature.ui.actions.model.DeviceLinkStatus
import eu.europa.ec.dashboardfeature.ui.actions.model.LinkedDeviceInfo
import eu.europa.ec.dashboardfeature.ui.dashboard.model.SideMenuItemUi
import eu.europa.ec.dashboardfeature.ui.health.ConnectionStatus
import eu.europa.ec.dashboardfeature.ui.health.HealthRecordUi
import eu.europa.ec.dashboardfeature.ui.health.HealthService
import eu.europa.ec.dashboardfeature.ui.health.MedicationUi
import eu.europa.ec.dashboardfeature.ui.health.PrescriptionUi
import eu.europa.ec.dashboardfeature.ui.health.VaccinationUi
import eu.europa.ec.dashboardfeature.ui.home.model.HeroCredentialUi
import eu.europa.ec.dashboardfeature.ui.settings.model.SettingsItemUi
import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.networklogic.model.request.CompleteProfileRequest
import eu.europa.ec.networklogic.model.response.WalletActivationResponse
import eu.europa.ec.notificationlogic.controller.ActionNotification
import eu.europa.ec.notificationlogic.controller.PushNotificationController
import eu.europa.ec.notificationlogic.controller.UserScopedPushNotificationController
import eu.europa.ec.notificationlogic.controller.WalletNotification
import eu.europa.ec.walletactivationlogic.usecase.CreateWalletAttestationUseCase
import eu.europa.ec.walletactivationlogic.usecase.DeleteWalletActivationUseCase
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.koin.dsl.module
import java.time.Instant

val authTestModule = module {
    single<SupabaseAuthRepository> { FakeSupabaseAuthRepository() }
    single<SignInWithEmailPasswordUseCase> { FakeSignInWithEmailPasswordUseCase() }
    single<SignUpWithEmailPasswordUseCase> { FakeSignUpWithEmailPasswordUseCase() }
    single<SignInWithOAuthUseCase> { FakeSignInWithOAuthUseCase() }
    single<SignOutUseCase> { FakeSignOutUseCase() }
    single<ObserveAuthStateUseCase> { FakeObserveAuthStateUseCase() }
    single<IsProfileCompletedUseCase> { FakeIsProfileCompletedUseCase() }
    single<IsWalletActivatedUseCase> { FakeIsWalletActivatedUseCase() }
    single<GetCurrentUserUseCase> { FakeGetCurrentUserUseCase() }
    single<CheckHandleAvailabilityUseCase> { FakeCheckHandleAvailabilityUseCase() }
    single<CompleteProfileUseCase> { FakeCompleteProfileUseCase() }

    single<CreateWalletAttestationUseCase> { FakeCreateWalletAttestationUseCase() }
    single<DeleteWalletActivationUseCase> { FakeDeleteWalletActivationUseCase() }

    single<DeviceController> { FakeDeviceController() }
    single<PushNotificationController> { FakePushNotificationController() }
    single<UserScopedPushNotificationController> { FakeUserScopedPushNotificationController() }
    single<PrefKeysV2> { FakePrefKeysV2() }
    single<PrefsControllerV2> { FakePrefsControllerV2() }
    single<UserDocumentOwnershipController> { FakeUserDocumentOwnershipController() }
    single<PinStorageController> { FakePinStorageController() }
    single<LocalUnlockTracker> { FakeLocalUnlockTracker() }
    single<QuickPinInteractor> { FakeQuickPinInteractor(get(), get()) }
    single<BiometricInteractor> { FakeBiometricInteractor() }
    single<BiometricAuthenticationController> { FakeBiometricAuthenticationController() }

    single<DashboardInteractor> { FakeDashboardInteractor() }
    single<HomeInteractor> { FakeHomeInteractor() }
    single<DocumentsInteractor> { FakeDocumentsInteractor() }
    single<ActionsInteractor> { FakeActionsInteractor() }
    single<HealthInteractor> { FakeHealthInteractor() }
    single<SettingsInteractor> { FakeSettingsInteractor() }
    single<VerificationRepository> { FakeVerificationRepository() }
}

private class FakeVerificationRepository : VerificationRepository {
    override suspend fun getVerificationTemplates(): List<VerificationTemplate> = emptyList()

    override suspend fun createVerificationSession(
        purpose: String,
        attributes: List<VerificationDraftAttribute>,
        recipients: List<VerificationRecipient>
    ): Result<VerificationSession> {
        return Result.failure(UnsupportedOperationException("Verification creation not used in auth smoke tests"))
    }

    override suspend fun getPublicVerificationSession(
        sessionId: String,
        accessToken: String
    ): Result<VerificationRecipientSession> {
        return Result.success(
            VerificationRecipientSession(
                id = sessionId,
                status = "pending",
                purpose = "Verify employment eligibility",
                createdAt = Instant.parse("2026-04-14T10:00:00Z").toEpochMilli(),
                expiresAt = Instant.parse("2026-04-14T12:00:00Z").toEpochMilli(),
                requester = VerificationRequester(
                    id = "requester-1",
                    displayName = "Alice Example",
                    handle = "alice"
                ),
                requestedAttributes = listOf(
                    VerificationRequestedAttribute(
                        key = "full_name",
                        label = "Full Name",
                        description = "The person's full legal name",
                        expectedValue = null
                    )
                ),
                publicUrl = "https://app.authbound.io/verify/$sessionId?token=$accessToken",
                gatewaySessionId = "gateway-session-1",
                requestUri = "openid4vp://verify?request_uri=https%3A%2F%2Fapi.authbound.io%2Frequest.jwt",
                requestUriExpiresAt = Instant.parse("2026-04-14T12:00:00Z").toEpochMilli(),
                sseToken = "test-sse-token",
                statusStreamUrl = "https://api.authbound.io/v1/sessions/gateway-session-1/status/sse?token=test-sse-token"
            )
        )
    }

    override fun observePublicVerificationSessionStatus(statusStreamUrl: String): Flow<String> = emptyFlow()

    override suspend fun getVerificationSession(sessionId: String): Result<VerificationSession> {
        return Result.failure(UnsupportedOperationException("Authenticated verification session not used in auth smoke tests"))
    }

    override suspend fun getVerificationSessions(): Flow<List<VerificationSession>> = flowOf(emptyList())

    override suspend fun refreshVerificationSessions(): Result<Unit> = Result.success(Unit)
}

private class FakeSupabaseAuthRepository : SupabaseAuthRepository {
    override suspend fun isUserAuthenticated(): Boolean = AuthScenarioStore.snapshot().authenticated

    override suspend fun getCurrentUser(): UserInfo? = mockUserInfoOrNull()

    override suspend fun getCurrentUserId(): String? = AuthScenarioStore.snapshot().takeIf { it.authenticated }?.userId

    override suspend fun getAccessToken(): String? = AuthScenarioStore.snapshot().takeIf { it.authenticated }?.let { "test-access-token" }

    override fun observeAuthState(): Flow<SessionStatus> = AuthScenarioStore.observeAuthState()

    override suspend fun signInWithEmailPassword(request: EmailPasswordRequest) {
        AuthScenarioStore.update { current ->
            current.copy(authenticated = true)
        }
    }

    override suspend fun signUpWithEmailPassword(request: EmailPasswordRequest) {
        AuthScenarioStore.update { current ->
            current.copy(authenticated = true)
        }
    }

    override suspend fun signInWithOAuth(provider: OAuthProvider, context: Context) {
        AuthScenarioStore.update { current ->
            current.copy(authenticated = true)
        }
    }

    override suspend fun signOut() {
        AuthScenarioStore.update { current ->
            current.copy(authenticated = false, unlockDeadlineMillis = null)
        }
    }
}

private class FakeSignInWithEmailPasswordUseCase : SignInWithEmailPasswordUseCase {
    override suspend fun invoke(request: EmailPasswordRequest) {
        AuthScenarioStore.update { current ->
            current.copy(authenticated = true)
        }
    }
}

private class FakeSignUpWithEmailPasswordUseCase : SignUpWithEmailPasswordUseCase {
    override suspend fun invoke(request: EmailPasswordRequest) {
        AuthScenarioStore.update { current ->
            current.copy(authenticated = true)
        }
    }
}

private class FakeSignInWithOAuthUseCase : SignInWithOAuthUseCase {
    override suspend fun invoke(provider: OAuthProvider, context: Context) {
        AuthScenarioStore.update { current ->
            current.copy(authenticated = true)
        }
    }
}

private class FakeSignOutUseCase : SignOutUseCase {
    override suspend fun invoke(mode: SignOutMode) {
        AuthScenarioStore.update { current ->
            current.copy(authenticated = false, unlockDeadlineMillis = null)
        }
    }
}

private class FakeObserveAuthStateUseCase : ObserveAuthStateUseCase {
    override fun invoke(): Flow<SessionStatus> = AuthScenarioStore.observeAuthState()
}

private class FakeIsProfileCompletedUseCase : IsProfileCompletedUseCase {
    override suspend fun invoke(): Boolean = AuthScenarioStore.snapshot().profileCompleted
}

private class FakeIsWalletActivatedUseCase : IsWalletActivatedUseCase {
    override suspend fun invoke(): WalletActivationStatus {
        return if (AuthScenarioStore.snapshot().walletActivated) {
            WalletActivationStatus.Activated
        } else {
            WalletActivationStatus.NotActivated(
                reason = "Auth smoke scenario wallet activation required",
                privateKeyExists = false,
                localFlagSet = false,
            )
        }
    }
}

private class FakeGetCurrentUserUseCase : GetCurrentUserUseCase {
    override suspend fun invoke(): UserInfo? = mockUserInfoOrNull()
}

private class FakeCheckHandleAvailabilityUseCase : CheckHandleAvailabilityUseCase {
    override suspend fun invoke(handle: String): Result<Boolean> {
        return Result.success(AuthScenarioStore.snapshot().handleAvailable)
    }
}

private class FakeCompleteProfileUseCase : CompleteProfileUseCase {
    override suspend fun invoke(request: CompleteProfileRequest): Result<Unit> {
        AuthScenarioStore.update { current ->
            current.copy(profileCompleted = true)
        }
        return Result.success(Unit)
    }
}

private class FakeCreateWalletAttestationUseCase : CreateWalletAttestationUseCase {
    override suspend fun invoke(
        deviceInfo: DeviceInfo,
        pushToken: String,
    ): Result<WalletActivationResponse> {
        return when (val outcome = AuthScenarioStore.consumeWalletSetupAttemptOutcome().orDefault()) {
            WalletSetupAttemptOutcome.Pending -> {
                awaitCancellation()
            }

            WalletSetupAttemptOutcome.Success -> {
                AuthScenarioStore.update { current ->
                    current.copy(walletActivated = true)
                }
                Result.success(mockk(relaxed = true))
            }

            is WalletSetupAttemptOutcome.Failure -> {
                Result.failure(outcome.error)
            }
        }
    }
}

private class FakeDeleteWalletActivationUseCase : DeleteWalletActivationUseCase {
    override suspend fun invoke(): Result<Unit> {
        AuthScenarioStore.update { current ->
            current.copy(
                authenticated = false,
                walletActivated = false,
                storedPin = "",
                unlockDeadlineMillis = null,
            )
        }
        return Result.success(Unit)
    }
}

private fun WalletSetupAttemptOutcome?.orDefault(): WalletSetupAttemptOutcome {
    return this ?: when (AuthScenarioStore.snapshot().walletSetupBehavior) {
        WalletSetupBehavior.PENDING -> WalletSetupAttemptOutcome.Pending
        WalletSetupBehavior.SUCCESS -> WalletSetupAttemptOutcome.Success
        WalletSetupBehavior.FAILURE -> WalletSetupAttemptOutcome.Failure(
            IllegalStateException("Auth smoke wallet activation failure")
        )
    }
}

private class FakeDeviceController : DeviceController {
    override fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            deviceId = "test-device",
            deviceName = "Auth Smoke Device",
            deviceModel = "Pixel Smoke",
            deviceOs = "Android Test",
            deviceOsVersion = "34",
            securityPatchLevel = "2026-01-01",
            hasSecureElement = true,
            hasHardwareKeystore = true,
            hasStrongBox = true,
            attestationSupported = true,
            deviceVerifiedBoot = true,
            playProtectVerified = true,
            hasBiometricHardware = false
        )
    }

    override fun getDeviceSecurityState(): DeviceSecurityState {
        val isReady = AuthScenarioStore.snapshot().deviceSecurityReady
        return DeviceSecurityState(
            isDeviceSecure = isReady,
            canAuthenticateWithDeviceCredential = isReady,
            canUseStrongBiometrics = false
        )
    }
}

private class FakePushNotificationController : PushNotificationController {
    override suspend fun registerForPushNotifications(): Result<String> = Result.success("push-token")
}

private class FakeUserScopedPushNotificationController : UserScopedPushNotificationController {
    override suspend fun registerForPushNotifications(userId: String): Result<String> = Result.success("user-push-token")

    override suspend fun unregisterPushNotifications(userId: String): Result<Unit> = Result.success(Unit)

    override fun observeCredentialClaims(): Flow<eu.europa.ec.businesslogic.model.CredentialClaim> = emptyFlow()

    override fun observeVerificationRequests(): Flow<eu.europa.ec.businesslogic.model.VerificationRequest> = emptyFlow()

    override fun observeActionRequests(): Flow<ActionNotification> = emptyFlow()

    override fun observeGeneralNotifications(): Flow<WalletNotification> = emptyFlow()

    override fun handleIncomingNotification(data: Map<String, String>) = Unit

    override fun clearUserNotifications(userId: String) = Unit
}

private class FakePrefKeysV2 : PrefKeysV2 {
    private var cryptoAlias: String = ""
    private var biometricAlias: String = ""
    private var showBatchIssuanceCounter: Boolean = false
    private var requiredTermsVersion: String = ""
    private var acceptedTermsVersion: String = ""
    private var acceptedTermsAt: String = ""
    private var requiredPrivacyVersion: String = ""
    private var acknowledgedPrivacyVersion: String = ""
    private var acknowledgedPrivacyAt: String = ""

    override suspend fun getCryptoAlias(): String = cryptoAlias

    override suspend fun setCryptoAlias(value: String) {
        cryptoAlias = value
    }

    override suspend fun getBiometricAlias(): String = biometricAlias

    override suspend fun setBiometricAlias(value: String) {
        biometricAlias = value
    }

    override suspend fun getShowBatchIssuanceCounter(): Boolean = showBatchIssuanceCounter

    override suspend fun setShowBatchIssuanceCounter(value: Boolean) {
        showBatchIssuanceCounter = value
    }

    override suspend fun isWalletActivated(): Boolean = AuthScenarioStore.snapshot().walletActivated

    override suspend fun setWalletActivated(isActivated: Boolean) {
        AuthScenarioStore.update { current ->
            current.copy(walletActivated = isActivated)
        }
    }

    override suspend fun isProfileCompleted(): Boolean = AuthScenarioStore.snapshot().profileCompleted

    override suspend fun setProfileCompleted(value: Boolean) {
        AuthScenarioStore.update { current ->
            current.copy(profileCompleted = value)
        }
    }

    override suspend fun getRequiredTermsVersion(): String = requiredTermsVersion

    override suspend fun setRequiredTermsVersion(value: String) {
        requiredTermsVersion = value
    }

    override suspend fun getAcceptedTermsVersion(): String = acceptedTermsVersion

    override suspend fun setAcceptedTermsVersion(value: String) {
        acceptedTermsVersion = value
    }

    override suspend fun getAcceptedTermsAt(): String = acceptedTermsAt

    override suspend fun setAcceptedTermsAt(value: String) {
        acceptedTermsAt = value
    }

    override suspend fun getRequiredPrivacyVersion(): String = requiredPrivacyVersion

    override suspend fun setRequiredPrivacyVersion(value: String) {
        requiredPrivacyVersion = value
    }

    override suspend fun getAcknowledgedPrivacyVersion(): String = acknowledgedPrivacyVersion

    override suspend fun setAcknowledgedPrivacyVersion(value: String) {
        acknowledgedPrivacyVersion = value
    }

    override suspend fun getAcknowledgedPrivacyAt(): String = acknowledgedPrivacyAt

    override suspend fun setAcknowledgedPrivacyAt(value: String) {
        acknowledgedPrivacyAt = value
    }

    override fun isWalletActivatedSafe(): Boolean = AuthScenarioStore.snapshot().walletActivated

    override fun getBiometricAliasSafe(): String = biometricAlias

    override fun isProfileCompletedSafe(): Boolean = AuthScenarioStore.snapshot().profileCompleted

    override fun getRequiredTermsVersionSafe(): String = requiredTermsVersion

    override fun getAcceptedTermsVersionSafe(): String = acceptedTermsVersion

    override fun getAcceptedTermsAtSafe(): String = acceptedTermsAt

    override fun getRequiredPrivacyVersionSafe(): String = requiredPrivacyVersion

    override fun getAcknowledgedPrivacyVersionSafe(): String = acknowledgedPrivacyVersion

    override fun getAcknowledgedPrivacyAtSafe(): String = acknowledgedPrivacyAt
}

private class FakePrefsControllerV2 : PrefsControllerV2 {
    private val values: MutableMap<String, Any> = mutableMapOf()

    override fun hasAuthenticatedUser(): Boolean = AuthScenarioStore.snapshot().authenticated

    override suspend fun clearCurrentUserData() {
        values.clear()
    }

    override suspend fun clearUserData(userId: String) {
        values.clear()
    }

    override suspend fun getString(key: String, defaultValue: String): String {
        return values[key] as? String ?: defaultValue
    }

    override suspend fun setString(key: String, value: String) {
        values[key] = value
    }

    override suspend fun getBool(key: String, defaultValue: Boolean): Boolean {
        return values[key] as? Boolean ?: defaultValue
    }

    override suspend fun setBool(key: String, value: Boolean) {
        values[key] = value
    }

    override suspend fun getLong(key: String, defaultValue: Long): Long {
        return values[key] as? Long ?: defaultValue
    }

    override suspend fun setLong(key: String, value: Long) {
        values[key] = value
    }

    override suspend fun getInt(key: String, defaultValue: Int): Int {
        return values[key] as? Int ?: defaultValue
    }

    override suspend fun setInt(key: String, value: Int) {
        values[key] = value
    }

    override suspend fun contains(key: String): Boolean = values.containsKey(key)

    override suspend fun clear(key: String) {
        values.remove(key)
    }

    override suspend fun clearAll() {
        values.clear()
    }

    override fun safeBool(key: String, defaultValue: Boolean): Boolean {
        return values[key] as? Boolean ?: defaultValue
    }

    override fun safeString(key: String, defaultValue: String): String {
        return values[key] as? String ?: defaultValue
    }

    override fun safeLong(key: String, defaultValue: Long): Long {
        return values[key] as? Long ?: defaultValue
    }

    override suspend fun invalidateCache() = Unit
}

private class FakePinStorageController : PinStorageController {
    override suspend fun retrievePin(): String = AuthScenarioStore.snapshot().storedPin

    override suspend fun setPin(pin: String) {
        AuthScenarioStore.update { current ->
            current.copy(storedPin = pin)
        }
    }

    override suspend fun isPinValid(pin: String): Boolean = AuthScenarioStore.snapshot().storedPin == pin
}

private class FakeUserDocumentOwnershipController : UserDocumentOwnershipController {
    override suspend fun getCurrentUserId(): String? = AuthScenarioStore.snapshot().takeIf { it.authenticated }?.userId

    override suspend fun requireCurrentUserId(): String {
        return getCurrentUserId() ?: throw SecurityException("Auth smoke scenario user is not authenticated")
    }

    override suspend fun bindDocumentToCurrentUser(documentId: String) = Unit

    override suspend fun bindDocumentsToCurrentUser(documentIds: List<String>) = Unit

    override suspend fun isDocumentOwnedByCurrentUser(documentId: String): Boolean = true

    override suspend fun getCurrentUserDocumentIds(): Set<String> = emptySet()

    override suspend fun unbindDocument(documentId: String) = Unit

    override suspend fun unbindAllDocumentsForCurrentUser() = Unit

    override suspend fun migrateOrphanedDocumentsToCurrentUser(): Int = 0

    override suspend fun isMigrationCompleted(): Boolean = AuthScenarioStore.snapshot().migrationCompleted

    override suspend fun setMigrationCompleted() {
        AuthScenarioStore.update { current ->
            current.copy(migrationCompleted = true)
        }
    }
}

private class FakeLocalUnlockTracker : LocalUnlockTracker {
    override suspend fun markUnlocked(ttlMillis: Long) {
        AuthScenarioDriver.markUnlocked(ttlMillis)
    }

    override suspend fun lockNow() {
        AuthScenarioDriver.lockNow()
    }

    override fun isUnlocked(): Boolean = AuthScenarioStore.snapshot().isUnlocked()
}

private class FakeQuickPinInteractor(
    private val pinStorageController: PinStorageController,
    private val localUnlockTracker: LocalUnlockTracker,
) : QuickPinInteractor {
    override suspend fun validateForm(form: Form): FormValidationResult {
        val value = form.inputs.values.firstOrNull().orEmpty()
        return when {
            value.length != 6 -> FormValidationResult(isValid = false, message = "")
            value.any { !it.isDigit() } -> FormValidationResult(isValid = false, message = "Only numbers allowed")
            else -> FormValidationResult(isValid = true)
        }
    }

    override suspend fun validateForms(forms: List<Form>): FormsValidationResult {
        val validationResults = forms.map { validateForm(it) }
        return FormsValidationResult(
            isValid = validationResults.all { it.isValid },
            messages = validationResults.mapNotNull { it.message.takeIf(String::isNotBlank) }
        )
    }

    override fun setPin(newPin: String, initialPin: String): Flow<QuickPinInteractorSetPinPartialState> {
        return flowOf(
            if (newPin == initialPin) {
                AuthScenarioStore.update { current ->
                    current.copy(storedPin = newPin)
                }
                QuickPinInteractorSetPinPartialState.Success
            } else {
                QuickPinInteractorSetPinPartialState.Failed("PINs do not match")
            }
        )
    }

    override fun changePin(newPin: String): Flow<QuickPinInteractorSetPinPartialState> {
        return flowOf(
            QuickPinInteractorSetPinPartialState.Success.also {
                AuthScenarioStore.update { current ->
                    current.copy(storedPin = newPin)
                }
            }
        )
    }

    override fun isCurrentPinValid(pin: String): Flow<QuickPinInteractorPinValidPartialState> {
        return flowOf(
            if (AuthScenarioStore.snapshot().storedPin == pin) {
                QuickPinInteractorPinValidPartialState.Success
            } else {
                QuickPinInteractorPinValidPartialState.Failed("Incorrect PIN")
            }
        )
    }

    override fun isPinMatched(
        currentPin: String,
        newPin: String,
    ): Flow<QuickPinInteractorPinValidPartialState> {
        return flowOf(
            if (currentPin == newPin) {
                QuickPinInteractorPinValidPartialState.Success
            } else {
                QuickPinInteractorPinValidPartialState.Failed("PINs do not match")
            }
        )
    }

    override suspend fun hasPin(): Boolean = pinStorageController.retrievePin().isNotBlank()

    override suspend fun resetPin() {
        pinStorageController.setPin("")
        localUnlockTracker.lockNow()
    }
}

private class FakeBiometricInteractor : BiometricInteractor {
    override fun getBiometricsAvailability(listener: (BiometricsAvailability) -> Unit) {
        listener(BiometricsAvailability.Failure("Biometrics unavailable in auth smoke tests"))
    }

    override suspend fun getBiometricUserSelection(): Boolean = AuthScenarioStore.snapshot().biometricsEnabled

    override suspend fun storeBiometricsUsageDecision(shouldUseBiometrics: Boolean) {
        AuthScenarioStore.update { current ->
            current.copy(biometricsEnabled = shouldUseBiometrics)
        }
    }

    override suspend fun getBiometricsPreferenceDecided(): Boolean = AuthScenarioStore.snapshot().biometricsPreferenceDecided

    override suspend fun storeBiometricsPreferenceDecided(value: Boolean) {
        AuthScenarioStore.update { current ->
            current.copy(biometricsPreferenceDecided = value)
        }
    }

    override fun authenticateWithBiometrics(
        context: Context,
        notifyOnAuthenticationFailure: Boolean,
        listener: (BiometricsAuthenticate) -> Unit,
    ) {
        listener(BiometricsAuthenticate.Cancelled)
    }

    override fun launchBiometricSystemScreen() = Unit

    override fun isPinValid(pin: String): Flow<QuickPinInteractorPinValidPartialState> {
        return flowOf(QuickPinInteractorPinValidPartialState.Failed("Biometrics disabled"))
    }
}

private class FakeBiometricAuthenticationController : BiometricAuthenticationController {
    override fun deviceSupportsBiometrics(listener: (BiometricsAvailability) -> Unit) {
        listener(BiometricsAvailability.Failure("Biometrics unavailable in auth smoke tests"))
    }

    override fun authenticate(
        context: Context,
        notifyOnAuthenticationFailure: Boolean,
        listener: (BiometricsAuthenticate) -> Unit,
    ) {
        listener(BiometricsAuthenticate.Cancelled)
    }

    override suspend fun authenticate(
        activity: FragmentActivity,
        biometryCrypto: BiometricCrypto,
        promptInfo: BiometricPrompt.PromptInfo,
        notifyOnAuthenticationFailure: Boolean,
    ): BiometricPromptData = BiometricPromptData(authenticationResult = null)

    override fun launchBiometricSystemScreen() = Unit

    override fun hasBiometricHardware(): Boolean = false
}

private class FakeDashboardInteractor : DashboardInteractor {
    override fun getSideMenuOptions(): List<SideMenuItemUi> = emptyList()

    override fun getAppVersion(): String = "test"

    override suspend fun getUserProfile(): eu.europa.ec.dashboardfeature.ui.dashboard.model.UserProfileUi? = null
}

private class FakeHomeInteractor : HomeInteractor {
    override fun isBleAvailable(): Boolean = false

    override fun isBleCentralClientModeEnabled(): Boolean = false

    override fun getUserNameViaMainPidDocument(): Flow<HomeInteractorGetUserNameViaMainPidDocumentPartialState> {
        return flowOf(HomeInteractorGetUserNameViaMainPidDocumentPartialState.Success("Authbound"))
    }

    override fun getCredentials(): Flow<HomeInteractorGetCredentialsPartialState> {
        return flowOf(HomeInteractorGetCredentialsPartialState.Success(emptyList()))
    }

    override fun getHeroCredential(): Flow<HomeInteractorGetHeroCredentialPartialState> {
        return flowOf(HomeInteractorGetHeroCredentialPartialState.Success(emptyList<HeroCredentialUi>()))
    }
}

private class FakeDocumentsInteractor : DocumentsInteractor {
    override fun getDocuments(): Flow<DocumentInteractorGetDocumentsPartialState> {
        return flowOf(
            DocumentInteractorGetDocumentsPartialState.Success(
                allDocuments = FilterableList(emptyList()),
                shouldAllowUserInteraction = true,
            )
        )
    }

    override fun tryIssuingDeferredDocumentsFlow(
        deferredDocuments: Map<DocumentId, eu.europa.ec.corelogic.model.FormatType>,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ): Flow<DocumentInteractorRetryIssuingDeferredDocumentsPartialState> {
        return flowOf(
            DocumentInteractorRetryIssuingDeferredDocumentsPartialState.Result(
                successfullyIssuedDeferredDocuments = emptyList(),
                failedIssuedDeferredDocuments = emptyList(),
            )
        )
    }

    override fun deleteDocument(documentId: String): Flow<DocumentInteractorDeleteDocumentPartialState> {
        return flowOf(DocumentInteractorDeleteDocumentPartialState.SingleDocumentDeleted)
    }

    override fun onFilterStateChange(): Flow<DocumentInteractorFilterPartialState> = emptyFlow()

    override fun initializeFilters(filterableList: FilterableList) = Unit

    override fun updateLists(filterableList: FilterableList) = Unit

    override fun applyFilters() = Unit

    override fun applySearch(query: String) = Unit

    override fun resetFilters() = Unit

    override fun revertFilters() = Unit

    override fun updateFilter(filterGroupId: String, filterId: String) = Unit

    override fun updateSortOrder(sortOrder: SortOrder) = Unit

    override fun addDynamicFilters(
        documents: FilterableList,
        filters: Filters,
    ): Filters = filters

    override fun getFilters(): Filters = Filters.emptyFilters()
}

private class FakeActionsInteractor : ActionsInteractor {
    override fun getActions(): Flow<ActionsInteractorPartialState> {
        return flowOf(
            ActionsInteractorPartialState.Success(
                pendingCount = 0,
                allActions = emptyList(),
                groupedActions = emptyList(),
            )
        )
    }

    override suspend fun acceptAction(actionId: String): Result<Unit> = Result.success(Unit)

    override suspend fun declineAction(actionId: String): Result<Unit> = Result.success(Unit)

    override fun getPendingActionsCount(): Flow<Int> = flowOf(0)

    override fun getDeviceLinkStatus(): Flow<DeviceLinkPartialState> {
        return flowOf(DeviceLinkPartialState.Success(DeviceLinkStatus.NOT_LINKED, null))
    }

    override suspend fun linkDevice(pairingPayload: String): Result<LinkedDeviceInfo> {
        return Result.success(
            LinkedDeviceInfo(
                deviceId = "device-link",
                deviceName = "Portal",
                deviceModel = "Browser",
                linkedAt = Instant.EPOCH,
            )
        )
    }

    override suspend fun unlinkDevice(): Result<Unit> = Result.success(Unit)

    override fun getActionById(actionId: String): ActionRequest? = null
}

private class FakeHealthInteractor : HealthInteractor {
    override fun getHealthData(): Flow<HealthInteractorPartialState> {
        return flowOf(
            HealthInteractorPartialState.Success(
                medications = emptyList<MedicationUi>(),
                prescriptions = emptyList<PrescriptionUi>(),
                healthRecords = emptyList<HealthRecordUi>(),
                vaccinations = emptyList<VaccinationUi>(),
            )
        )
    }

    override fun getConnectionStatus(): Flow<HealthConnectionPartialState> {
        return flowOf(
            HealthConnectionPartialState.Success(
                kantaStatus = ConnectionStatus.NOT_CONNECTED,
                maisaStatus = ConnectionStatus.NOT_CONNECTED,
                lastSyncTime = null,
            )
        )
    }

    override suspend fun connectToService(service: HealthService): Result<Unit> = Result.success(Unit)

    override suspend fun startMaisaAuth(): Result<String> = Result.success("https://example.invalid")

    override suspend fun handleMaisaCallback(code: String, state: String, redirectUri: String): Result<String> {
        return Result.success("credential-offer")
    }

    override suspend fun disconnectFromService(service: HealthService): Result<Unit> = Result.success(Unit)

    override suspend fun refreshHealthData(): Result<Unit> = Result.success(Unit)

    override suspend fun hasHealthData(): Boolean = false
}

private class FakeSettingsInteractor : SettingsInteractor {
    override fun getAppVersion(): String = "test"

    override fun getChangelogUrl(): String? = null

    override fun getTermsUrl(): String = "https://example.invalid/terms"

    override fun getPrivacyPolicyUrl(): String = "https://example.invalid/privacy"

    override fun getAccountDeletionUrl(): String = "https://example.invalid/delete"

    override fun getCachedLegalAcceptance(): LegalAcceptanceSnapshot = LegalAcceptanceSnapshot()

    override fun retrieveLogFileUris(): ArrayList<android.net.Uri> = arrayListOf()

    override fun getSettingsItemsUi(
        changelogUrl: String?,
        isBiometricsEnabled: Boolean,
    ): List<SettingsItemUi> = emptyList()

    override fun getShowBatchIssuanceCounter(): Boolean = false

    override fun toggleShowBatchIssuanceCounter() = Unit

    override suspend fun getBiometricsEnabled(): Boolean = false

    override suspend fun setBiometricsEnabled(enabled: Boolean) = Unit

    override suspend fun setBiometricsPreferenceDecided(value: Boolean) = Unit

    override fun getUserEmail(): Flow<String?> = flowOf(AuthScenarioStore.snapshot().userEmail)

    override suspend fun logout() {
        AuthScenarioStore.update { current ->
            current.copy(authenticated = false, unlockDeadlineMillis = null)
        }
    }

    override suspend fun isUserAuthenticated(): Boolean = AuthScenarioStore.snapshot().authenticated

    override suspend fun getCurrentUser(): UserInfo? = mockUserInfoOrNull()

    override suspend fun getMyProfile(): Result<Profile> {
        val state = AuthScenarioStore.snapshot()
        return Result.success(
            Profile(
                id = state.userId,
                handle = state.profileHandle,
                displayName = state.displayName,
            )
        )
    }

    override suspend fun resetHealthData(): Result<Unit> = Result.success(Unit)

    override suspend fun getCredentialCount(): Int = 0

    override suspend fun getCredentialSummaries(): List<CredentialSummaryUi> = emptyList()

    override suspend fun getMainPidPortraitBase64(): String? = null
}

private fun mockUserInfoOrNull(): UserInfo? {
    val state = AuthScenarioStore.snapshot()
    if (!state.authenticated) {
        return null
    }
    return mockk(relaxed = true) {
        every { id } returns state.userId
        every { email } returns state.userEmail
    }
}
