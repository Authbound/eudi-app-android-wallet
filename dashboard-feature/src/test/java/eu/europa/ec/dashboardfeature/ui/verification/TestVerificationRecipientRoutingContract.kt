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

package eu.europa.ec.dashboardfeature.ui.verification

import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test
import java.io.File

class TestVerificationRecipientRoutingContract {

    @Test
    fun `verification recipient route carries only opaque payload key`() {
        val routerContract = readWalletSource(
            "ui-logic/src/main/java/eu/europa/ec/uilogic/navigation/RouterContract.kt"
        )
        val dashboardViewModel = readWalletSource(
            "dashboard-feature/src/main/java/eu/europa/ec/dashboardfeature/ui/dashboard/DashboardViewModel.kt"
        )
        val graph = readWalletSource(
            "dashboard-feature/src/main/java/eu/europa/ec/dashboardfeature/router/Graph.kt"
        )
        val recipientViewModel = readWalletSource(
            "dashboard-feature/src/main/java/eu/europa/ec/dashboardfeature/ui/verification/VerificationRecipientViewModel.kt"
        )
        val deepLinkHelper = readWalletSource(
            "ui-logic/src/main/java/eu/europa/ec/uilogic/navigation/helper/DeepLinkHelper.kt"
        )

        assertTrue(routerContract.contains("payloadKey={payloadKey}"))
        assertFalse(routerContract.contains("accessToken={accessToken}"))
        assertFalse(routerContract.contains("verificationUrl={verificationUrl}"))

        assertFalse(dashboardViewModel.contains("\"accessToken\" to"))
        assertFalse(dashboardViewModel.contains("\"verificationUrl\" to"))

        assertFalse(graph.contains("navArgument(\"accessToken\")"))
        assertFalse(graph.contains("navArgument(\"verificationUrl\")"))
        assertFalse(graph.contains("getString(\"accessToken\")"))
        assertFalse(graph.contains("getString(\"verificationUrl\")"))

        assertFalse(recipientViewModel.contains("\"accessToken\" to accessToken"))
        assertFalse(recipientViewModel.contains("arguments[\"verificationUrl\"]"))

        assertTrue(deepLinkHelper.contains("if (arguments == null) return"))
    }

    private fun readWalletSource(pathFromWalletRoot: String): String {
        val cwd = File(checkNotNull(System.getProperty("user.dir"))).absoluteFile
        val candidates = generateSequence(cwd) { it.parentFile }.flatMap { directory ->
            sequenceOf(
                File(directory, pathFromWalletRoot),
                File(directory, "apps/android-wallet/$pathFromWalletRoot")
            )
        }

        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate $pathFromWalletRoot from $cwd")
    }
}
