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

package io.authbound.wallet.test

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.runner.AndroidJUnitRunner
import io.authbound.wallet.BuildConfig

class AuthTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, name: String?, context: Context): Application {
        val useAuthTestApplication = shouldUseAuthTestApplication()

        check(!useAuthTestApplication || BuildConfig.DEBUG) {
            "auth_test_application=true is only supported for debug variants"
        }

        val applicationName = if (useAuthTestApplication) {
            AuthTestApplication::class.java.name
        } else {
            name
        }
        return super.newApplication(cl, applicationName, context)
    }

    private fun shouldUseAuthTestApplication(): Boolean {
        val instrumentationContext: Context = this.context
        val applicationInfo = instrumentationContext.packageManager.getApplicationInfo(
            instrumentationContext.packageName,
            PackageManager.GET_META_DATA
        )

        return applicationInfo.metaData
            ?.getBoolean(AUTH_TEST_APPLICATION_ARGUMENT, false) == true
    }

    companion object {
        const val AUTH_TEST_APPLICATION_ARGUMENT: String = "auth_test_application"
    }
}
