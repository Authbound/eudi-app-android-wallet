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

package eu.europa.ec.authboundpidfeature.ui.intro

import org.junit.Assert.assertEquals
import org.junit.Test

class TestCandourActivityResultResolution {

    @Test
    fun `Given listener already handled the result, When activity result arrives, Then it is ignored`() {
        val resolution: CandourActivityResultResolution = resolveCandourActivityResult(
            status = "SUCCESS_SESSION",
            didReceiveCandourListenerResult = true
        )

        assertEquals(CandourActivityResultResolution.Ignore, resolution)
    }

    @Test
    fun `Given activity result includes a status, When listener has not already handled it, Then the status is dispatched`() {
        val resolution: CandourActivityResultResolution = resolveCandourActivityResult(
            status = "SUCCESS_SESSION",
            didReceiveCandourListenerResult = false
        )

        assertEquals(
            CandourActivityResultResolution.Dispatch(status = "SUCCESS_SESSION"),
            resolution
        )
    }

    @Test
    fun `Given activity result is missing a status, When listener has not yet handled it, Then the UI waits for the listener`() {
        val resolution: CandourActivityResultResolution = resolveCandourActivityResult(
            status = null,
            didReceiveCandourListenerResult = false
        )

        assertEquals(CandourActivityResultResolution.AwaitListener, resolution)
    }
}
