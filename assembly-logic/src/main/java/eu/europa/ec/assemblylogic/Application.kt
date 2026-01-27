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

package eu.europa.ec.assemblylogic

import android.app.Application
import android.util.Log
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.amplifyframework.AmplifyException
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.core.Amplify
import eu.europa.ec.analyticslogic.controller.AnalyticsController
import eu.europa.ec.assemblylogic.di.setupKoin
import eu.europa.ec.authenticationlogic.gate.AppLockLifecycleObserver
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.corelogic.config.WalletCoreConfig
import eu.europa.ec.corelogic.worker.RevocationWorkManager
import eu.europa.ec.eudi.rqesui.infrastructure.EudiRQESUi
import eu.europa.ec.quickidlogic.repository.QuickIdRepository
import org.koin.android.ext.android.inject
import org.koin.core.KoinApplication

class Application : Application() {

    private val analyticsController: AnalyticsController by inject()
    private val configLogic: ConfigLogic by inject()
    private val walletCoreConfig: WalletCoreConfig by inject()
    private val quickIdRepository: QuickIdRepository by inject()
    private val appLockLifecycleObserver: AppLockLifecycleObserver by inject()

    override fun onCreate() {
        super.onCreate()
        initializeKoin().initializeRqes()
        initializeReporting()
        initializeRevocationWorkManager()
        initializeQuickIdLifecycleObserver()
        initializeAppLockLifecycleObserver()
        initializeAmplify()
    }

    /**
     * Registers the QuickID repository as a lifecycle observer to clear
     * sensitive session data when the app goes to background.
     */
    private fun initializeQuickIdLifecycleObserver() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(quickIdRepository)
    }

    /**
     * Registers the app lock lifecycle observer to record when the app goes to background.
     * This enables the background timeout check in KeyGateV2Impl.isUnlocked.
     */
    private fun initializeAppLockLifecycleObserver() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLockLifecycleObserver)
    }

    /**
     * Initializes AWS Amplify for Face Liveness verification.
     *
     * Note: Update amplifyconfiguration.json in quickid-feature/src/main/res/raw/
     * with your Cognito Identity Pool ID before using Face Liveness.
     */
    private fun initializeAmplify() {
        try {
            Amplify.addPlugin(AWSCognitoAuthPlugin())
            Amplify.configure(this)
            _isAmplifyInitialized = true
            Log.i(TAG, "Amplify initialized successfully")
        } catch (e: AmplifyException) {
            _isAmplifyInitialized = false
            _amplifyInitError = e.message
            Log.e(TAG, "Failed to initialize Amplify: ${e.message}")
        } catch (e: Exception) {
            _isAmplifyInitialized = false
            _amplifyInitError = e.message
            Log.e(TAG, "Amplify initialization error: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "AuthboundApp"

        /**
         * Indicates whether AWS Amplify was successfully initialized.
         * Check this before using Face Liveness features.
         */
        private var _isAmplifyInitialized: Boolean = false
        val isAmplifyInitialized: Boolean
            get() = _isAmplifyInitialized

        /**
         * Error message if Amplify initialization failed, null otherwise.
         */
        private var _amplifyInitError: String? = null
        val amplifyInitError: String?
            get() = _amplifyInitError
    }

    private fun KoinApplication.initializeRqes() {
        EudiRQESUi.setup(
            application = this@Application,
            config = configLogic.rqesConfig,
            koinApplication = this@initializeRqes
        )
    }

    private fun initializeKoin(): KoinApplication {
        return setupKoin()
    }

    private fun initializeReporting() {
        analyticsController.initialize(this)
    }

    private fun initializeRevocationWorkManager() {

        val periodicWorkRequest = PeriodicWorkRequest.Builder(
            workerClass = RevocationWorkManager::class.java,
            repeatInterval = walletCoreConfig.revocationInterval,
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            RevocationWorkManager.REVOCATION_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWorkRequest
        )
    }
}