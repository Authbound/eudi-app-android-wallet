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

import eu.europa.ec.authenticationlogic.gate.LocalUnlockTracker
import eu.europa.ec.authenticationlogic.model.LegalAcceptanceSnapshot
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class WalletSetupBehavior {
    PENDING,
    SUCCESS,
    FAILURE,
}

sealed interface WalletSetupAttemptOutcome {
    data object Pending : WalletSetupAttemptOutcome
    data object Success : WalletSetupAttemptOutcome
    data class Failure(val error: Exception) : WalletSetupAttemptOutcome
}

data class AuthScenarioState(
    val authenticated: Boolean = false,
    val profileCompleted: Boolean = false,
    val deviceSecurityReady: Boolean = false,
    val walletActivated: Boolean = false,
    val storedPin: String = "",
    val currentTimeMillis: Long = 1_000L,
    val unlockDeadlineMillis: Long? = null,
    val walletSetupBehavior: WalletSetupBehavior = WalletSetupBehavior.PENDING,
    val walletSetupAttemptOutcomes: List<WalletSetupAttemptOutcome> = emptyList(),
    val handleAvailable: Boolean = true,
    val userId: String = "test-user",
    val userEmail: String = "tester@authbound.io",
    val displayName: String = "Authbound Tester",
    val profileHandle: String = "authbound_tester",
    val migrationCompleted: Boolean = false,
    val biometricsEnabled: Boolean = false,
    val biometricsPreferenceDecided: Boolean = true,
    val legalAcceptanceSnapshot: LegalAcceptanceSnapshot = LegalAcceptanceSnapshot(
        requiredTermsVersion = "terms-2026-04-08",
        acceptedTermsVersion = "terms-2026-04-08",
        acceptedTermsAt = "2026-04-08T00:00:00Z",
        requiredPrivacyVersion = "privacy-2026-04-08",
        acknowledgedPrivacyVersion = "privacy-2026-04-08",
        acknowledgedPrivacyAt = "2026-04-08T00:00:00Z",
    ),
) {
    fun isUnlocked(): Boolean {
        return unlockDeadlineMillis?.let { currentTimeMillis < it } == true
    }

    fun hasPin(): Boolean = storedPin.isNotBlank()
}

object AuthScenarioStore {
    private val lock: Any = Any()
    private var state: AuthScenarioState = AuthScenarioState()
    private var authSessionKey: AuthSessionKey = state.toAuthSessionKey()
    private val authStateFlow: MutableStateFlow<SessionStatus> = MutableStateFlow(state.toSessionStatus())

    fun reset(newState: AuthScenarioState = AuthScenarioState()) {
        synchronized(lock) {
            state = newState
            authSessionKey = newState.toAuthSessionKey()
            authStateFlow.value = newState.toSessionStatus()
        }
    }

    fun snapshot(): AuthScenarioState {
        return synchronized(lock) {
            state.copy()
        }
    }

    fun update(transform: (AuthScenarioState) -> AuthScenarioState) {
        synchronized(lock) {
            val previousAuthSessionKey = authSessionKey
            state = transform(state)
            authSessionKey = state.toAuthSessionKey()
            if (authSessionKey != previousAuthSessionKey) {
                authStateFlow.value = state.toSessionStatus()
            }
        }
    }

    fun observeAuthState(): StateFlow<SessionStatus> = authStateFlow

    fun consumeWalletSetupAttemptOutcome(): WalletSetupAttemptOutcome? {
        return synchronized(lock) {
            val nextOutcome = state.walletSetupAttemptOutcomes.firstOrNull() ?: return@synchronized null
            state = state.copy(walletSetupAttemptOutcomes = state.walletSetupAttemptOutcomes.drop(1))
            nextOutcome
        }
    }
}

object AuthScenarioDriver {
    fun reset(newState: AuthScenarioState = AuthScenarioState()) {
        AuthScenarioStore.reset(newState)
    }

    fun snapshot(): AuthScenarioState = AuthScenarioStore.snapshot()

    fun markUnlocked(ttlMillis: Long = LocalUnlockTracker.DEFAULT_TTL_MS) {
        AuthScenarioStore.update { current ->
            current.copy(unlockDeadlineMillis = current.currentTimeMillis + ttlMillis)
        }
    }

    fun lockNow() {
        AuthScenarioStore.update { current ->
            current.copy(unlockDeadlineMillis = null)
        }
    }

    fun advanceTimeBy(deltaMillis: Long) {
        AuthScenarioStore.update { current ->
            current.copy(currentTimeMillis = current.currentTimeMillis + deltaMillis)
        }
    }

    fun expireUnlockSession() {
        AuthScenarioStore.update { current ->
            current.copy(unlockDeadlineMillis = current.currentTimeMillis)
        }
    }

    fun setDeviceSecurityReady(isReady: Boolean) {
        AuthScenarioStore.update { current ->
            current.copy(deviceSecurityReady = isReady)
        }
    }
}

private data class AuthSessionKey(
    val authenticated: Boolean,
    val userId: String,
    val userEmail: String,
)

private fun AuthScenarioState.toAuthSessionKey(): AuthSessionKey {
    return AuthSessionKey(
        authenticated = authenticated,
        userId = userId,
        userEmail = userEmail,
    )
}

private fun AuthScenarioState.toSessionStatus(): SessionStatus {
    return if (authenticated) {
        SessionStatus.Authenticated(mockk<UserSession>(relaxed = true))
    } else {
        SessionStatus.NotAuthenticated(isSignOut = false)
    }
}
