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

package eu.europa.ec.businesslogic.config

import eu.europa.ec.businesslogic.BuildConfig

object E2eRuntimeConfig {

    private const val DEV_FLAVOR: String = "dev"

    const val SYNTHETIC_USER_ID: String = "e2e-wallet-user"

    val isEnabled: Boolean
        get() = isEnabled(
            isDebug = BuildConfig.DEBUG,
            flavor = BuildConfig.FLAVOR,
            requested = BuildConfig.E2E_MODE
        )

    val issuerBaseUrl: String
        get() = if (isEnabled) BuildConfig.E2E_ISSUER_BASE_URL else ""

    val verifierApiUrl: String
        get() = if (isEnabled) BuildConfig.E2E_VERIFIER_API_URL else ""

    val verifierUiUrl: String
        get() = if (isEnabled) BuildConfig.E2E_VERIFIER_UI_URL else ""

    internal fun isEnabled(
        isDebug: Boolean,
        flavor: String,
        requested: Boolean
    ): Boolean {
        return isDebug && flavor == DEV_FLAVOR && requested
    }
}
