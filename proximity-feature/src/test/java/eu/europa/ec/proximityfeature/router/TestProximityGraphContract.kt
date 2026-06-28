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

package eu.europa.ec.proximityfeature.router

import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test
import java.io.File

class TestProximityGraphContract {

    @Test
    fun `proximity QR route uses request URI config argument`() {
        val graph = readWalletSource(
            "proximity-feature/src/main/java/eu/europa/ec/proximityfeature/router/Graph.kt"
        )
        val routerContract = readWalletSource(
            "ui-logic/src/main/java/eu/europa/ec/uilogic/navigation/RouterContract.kt"
        )
        val viewModel = readWalletSource(
            "proximity-feature/src/main/java/eu/europa/ec/proximityfeature/ui/qr/ProximityQRViewModel.kt"
        )

        assertTrue(routerContract.contains("PROXIMITY_QR"))
        assertTrue(routerContract.contains("requestUriConfig={requestUriConfig}"))
        assertTrue(viewModel.contains("requestUriConfigRaw"))
        assertTrue(graph.contains("navArgument(RequestUriConfig.serializedKeyName)"))
        assertTrue(graph.contains("getString(RequestUriConfig.serializedKeyName)"))
        assertFalse(graph.contains("navArgument(\"scopeId\") {\n                    type = NavType.StringType\n                },\n            ) {\n            ProximityQRScreen"))
        assertFalse(graph.contains("ProximityQRScreen(\n                navController,\n                koinViewModel(\n                    parameters = {\n                        parametersOf(\n                            it.arguments?.getString(\"scopeId\")"))
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
