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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestE2eRuntimeConfig {

    @Test
    fun `Given non debug build When gating is evaluated Then E2E mode is disabled`() {
        val isEnabled = E2eRuntimeConfig.isEnabled(
            isDebug = false,
            flavor = "dev",
            requested = true
        )

        assertFalse(isEnabled)
    }

    @Test
    fun `Given non dev flavor When gating is evaluated Then E2E mode is disabled`() {
        val isEnabled = E2eRuntimeConfig.isEnabled(
            isDebug = true,
            flavor = "demo",
            requested = true
        )

        assertFalse(isEnabled)
    }

    @Test
    fun `Given E2E not requested When gating is evaluated Then E2E mode is disabled`() {
        val isEnabled = E2eRuntimeConfig.isEnabled(
            isDebug = true,
            flavor = "dev",
            requested = false
        )

        assertFalse(isEnabled)
    }

    @Test
    fun `Given debug dev build with E2E requested When gating is evaluated Then E2E mode is enabled`() {
        val isEnabled = E2eRuntimeConfig.isEnabled(
            isDebug = true,
            flavor = "dev",
            requested = true
        )

        assertTrue(isEnabled)
    }
}
