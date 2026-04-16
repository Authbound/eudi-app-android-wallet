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

package eu.europa.ec.authboundpidfeature.sdk

import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatDelegate
import com.candour.candoursdk.interfaces.CandourExitStatus
import com.candour.candoursdk.interfaces.CandourListener
import com.candour.candoursdk.ui.main.CandourSdkV2
import com.candour.candoursdk.utils.CandourSdkConfig
import com.candour.candoursdk.utils.CandourThemeConfig
import eu.europa.ec.authboundpidfeature.R
import java.util.Locale

private const val TAG: String = "CandourSdkLauncher"

fun createCandourLaunchIntent(
    activity: ComponentActivity,
    candourSessionId: String,
    candourApiEndpoint: String,
    onExit: (String) -> Unit
): Intent {
    val candourTheme: CandourThemeConfig = CandourThemeConfig(
        theme = R.style.Theme_themeForCandourSdk,
        mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
        languageCode = Locale.getDefault().language
    )
    val candourListener: CandourListener = object : CandourListener {
        override fun onExit(status: CandourExitStatus) {
            Log.i(TAG, "CANDOUR LISTENER EXIT $status")
            onExit(status.name)
        }
    }
    Log.i(TAG, "Launching Candour SDK with endpoint=$candourApiEndpoint")

    return CandourSdkV2.createLaunchIntent(
        activity,
        candourSdkConfig = CandourSdkConfig(
            candourApiEndpoint = candourApiEndpoint,
            launchType = "sdk",
            verificationSessionId = candourSessionId,
            sessionSecret = null,
            savedDataSlots = 1,
            allowOcrSkipAfterFailures = -1,
            candourThemeConfig = candourTheme,
            candourListener = candourListener
        ),
        launchType = "sdk"
    )
}
