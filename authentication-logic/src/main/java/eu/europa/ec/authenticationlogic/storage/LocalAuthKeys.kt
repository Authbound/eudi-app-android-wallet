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

object LocalAuthKeys {
    const val AUTH_STATE = "LocalAuthMaterialV2"
    const val ENROLLMENT_REQUIRED = "local_auth_enrollment_required"
    const val PREPARED_WALLET_RECOVERY_CHALLENGE = "prepared_wallet_recovery_challenge"
    const val RECOVERY_CHECKPOINT = "wallet_recovery_checkpoint"
    const val LEGACY_PIN_ENC = "PinEnc"
    const val LEGACY_PIN_IV = "PinIv"

    const val PIN_PEPPER_ALIAS = "wallet_pin_pepper_v1"
    const val AUTH_STATE_MAC_ALIAS = "wallet_auth_state_mac_v1"
    const val RECOVERY_KEY_ALIAS = "wallet_recovery_v1"

    const val PBKDF2_ITERATIONS = 310_000
    const val MAX_FAILED_ATTEMPTS = 10
    const val VAULT_UNLOCK_KEY_SIZE_BYTES = 32
    const val PIN_SALT_SIZE_BYTES = 16
}
