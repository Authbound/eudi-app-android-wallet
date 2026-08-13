/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 */

package eu.europa.ec.businesslogic.config

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

class TestMobileBackendRouting {

    @Test
    fun `Given the dev flavor, When resolving backend routing, Then staging hosts are used`(): Unit {
        val config: ConfigLogic = ConfigLogicImpl(mock(Context::class.java))

        assertEquals(AppFlavor.DEV, config.appFlavor)
        assertEquals("https://staging-mobile.authbound.io", config.environmentConfig.getServerHost())
        assertEquals("https://staging-api.authbound.io", config.environmentConfig.getGatewayHost())
    }
}
