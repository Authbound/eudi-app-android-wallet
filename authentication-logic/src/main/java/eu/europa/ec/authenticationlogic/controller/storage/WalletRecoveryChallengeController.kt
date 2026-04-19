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

package eu.europa.ec.authenticationlogic.controller.storage

import eu.europa.ec.authenticationlogic.storage.LocalAuthKeys
import eu.europa.ec.businesslogic.controller.storage.PrefsControllerV2
import eu.europa.ec.networklogic.model.response.AttestationChallengeResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface WalletRecoveryChallengeController {
    suspend fun peekPreparedChallenge(): AttestationChallengeResponse?
    suspend fun cachePreparedChallenge(challenge: AttestationChallengeResponse)
    suspend fun consumePreparedChallenge(): AttestationChallengeResponse?
    suspend fun clearPreparedChallenge()
}

class WalletRecoveryChallengeControllerImpl(
    private val prefsController: PrefsControllerV2
) : WalletRecoveryChallengeController {

    private val json: Json = Json {
        ignoreUnknownKeys = true
    }

    override suspend fun peekPreparedChallenge(): AttestationChallengeResponse? {
        return readPreparedChallenge(consume = false)
    }

    override suspend fun cachePreparedChallenge(challenge: AttestationChallengeResponse) {
        val serializedChallenge: String = json.encodeToString(challenge)
        prefsController.setString(LocalAuthKeys.PREPARED_WALLET_RECOVERY_CHALLENGE, serializedChallenge)
    }

    override suspend fun consumePreparedChallenge(): AttestationChallengeResponse? {
        return readPreparedChallenge(consume = true)
    }

    override suspend fun clearPreparedChallenge() {
        if (prefsController.contains(LocalAuthKeys.PREPARED_WALLET_RECOVERY_CHALLENGE)) {
            prefsController.clear(LocalAuthKeys.PREPARED_WALLET_RECOVERY_CHALLENGE)
        }
    }

    private suspend fun readPreparedChallenge(consume: Boolean): AttestationChallengeResponse? {
        if (!prefsController.contains(LocalAuthKeys.PREPARED_WALLET_RECOVERY_CHALLENGE)) {
            return null
        }
        val serializedChallenge: String = prefsController.getString(
            LocalAuthKeys.PREPARED_WALLET_RECOVERY_CHALLENGE,
            ""
        )
        if (serializedChallenge.isBlank()) {
            clearPreparedChallenge()
            return null
        }
        val challenge: AttestationChallengeResponse = runCatching {
            json.decodeFromString<AttestationChallengeResponse>(serializedChallenge)
        }.getOrElse {
            clearPreparedChallenge()
            return null
        }
        if (isExpired(challenge)) {
            clearPreparedChallenge()
            return null
        }
        if (consume) {
            clearPreparedChallenge()
        }
        return challenge
    }

    private fun isExpired(challenge: AttestationChallengeResponse): Boolean {
        return runCatching {
            java.time.Instant.parse(challenge.expiresAt).isBefore(java.time.Instant.now())
        }.getOrElse {
            true
        }
    }
}
