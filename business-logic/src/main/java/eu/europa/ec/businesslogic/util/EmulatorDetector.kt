/*
 * Copyright (c) 2023 European Commission
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

package eu.europa.ec.businesslogic.util

import android.os.Build

/**
 * Detects if the app is running on an Android emulator.
 * Used to select the correct localhost address for development.
 */
object EmulatorDetector {

    /**
     * Checks multiple Build properties to determine if running on an emulator.
     * Covers stock Android emulators, Genymotion, and other common emulator types.
     */
    fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.PRODUCT == "google_sdk"
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu"))
    }

    /**
     * Returns the appropriate localhost address for accessing the dev server.
     * - Emulator: 10.0.2.2 (Android's special alias for host machine loopback)
     * - Real device: 127.0.0.1 (requires `adb reverse tcp:PORT tcp:PORT`)
     */
    fun getLocalhostAddress(): String {
        return if (isEmulator()) "10.0.2.2" else "127.0.0.1"
    }
}
