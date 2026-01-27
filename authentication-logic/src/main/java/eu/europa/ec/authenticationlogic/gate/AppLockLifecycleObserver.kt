/*
 * Copyright (c) 2025 European Commission
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

package eu.europa.ec.authenticationlogic.gate

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Observes the application process lifecycle to record when the app goes to background.
 *
 * Registered on [androidx.lifecycle.ProcessLifecycleOwner] so that [onStop] fires only
 * when the entire app is backgrounded (not on configuration changes).
 *
 * The recorded timestamp is used by [KeyGateV2Impl.isUnlocked] to enforce
 * [KeyGateV2Impl.BACKGROUND_TIMEOUT_MS].
 */
class AppLockLifecycleObserver(
    private val keyGateImpl: KeyGateV2Impl
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        Log.d(TAG, ">>> ProcessLifecycle onStart (app foregrounded)")
    }

    override fun onStop(owner: LifecycleOwner) {
        Log.d(TAG, ">>> ProcessLifecycle onStop (app backgrounded) — calling onAppBackgrounded()")
        keyGateImpl.onAppBackgrounded()
    }

    companion object {
        private const val TAG = "AppLockLifecycle"
    }
}
