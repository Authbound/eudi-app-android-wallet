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
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager

import eu.europa.ec.analyticslogic.controller.AnalyticsController
import eu.europa.ec.assemblylogic.di.setupKoin
import eu.europa.ec.authenticationlogic.gate.AppLockLifecycleObserver
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.corelogic.config.WalletCoreConfig
import eu.europa.ec.corelogic.worker.ReIssuanceWorkManager
import eu.europa.ec.corelogic.worker.RevocationWorkManager
import eu.europa.ec.eudi.rqesui.infrastructure.EudiRQESUi

import org.koin.android.ext.android.inject
import org.koin.core.KoinApplication
import org.koin.core.module.Module

open class Application : Application() {

    private val analyticsController: AnalyticsController by inject()
    private val configLogic: ConfigLogic by inject()
    private val walletCoreConfig: WalletCoreConfig by inject()
    private val appLockLifecycleObserver: AppLockLifecycleObserver by inject()

    override fun onCreate() {
        super.onCreate()
        val koinApplication = initializeKoin()
        if (shouldInitializeRqes()) {
            koinApplication.initializeRqes()
        }
        if (shouldInitializeReporting()) {
            initializeReporting()
        }
        if (shouldInitializeRevocationWorkManager()) {
            initializeRevocationWorkManager()
        }
        if (shouldInitializeReIssuanceWorkManager()) {
            initializeReIssuanceWorkManager()
        }
        if (shouldInitializeAppLockLifecycleObserver()) {
            initializeAppLockLifecycleObserver()
        }
    }

    /**
     * Registers the app lock lifecycle observer to record when the app goes to background.
     * This enables the background timeout check in KeyGateV2Impl.isUnlocked.
     */
    private fun initializeAppLockLifecycleObserver() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLockLifecycleObserver)
    }

    private fun KoinApplication.initializeRqes() {
        EudiRQESUi.setup(
            application = this@Application,
            config = configLogic.rqesConfig,
            koinApplication = this@initializeRqes
        )
    }

    protected open fun initializeKoin(): KoinApplication {
        return setupKoin(
            additionalModules = additionalKoinModules(),
            allowOverride = allowKoinOverride()
        )
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

    private fun initializeReIssuanceWorkManager() {
        val periodicWorkRequest = PeriodicWorkRequest.Builder(
            workerClass = ReIssuanceWorkManager::class.java,
            repeatInterval = walletCoreConfig.documentIssuanceConfig.reissuanceRule.backgroundInterval,
        ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ReIssuanceWorkManager.RE_ISSUANCE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWorkRequest
        )
    }

    protected open fun additionalKoinModules(): List<Module> = emptyList()

    protected open fun allowKoinOverride(): Boolean? = null

    protected open fun shouldInitializeRqes(): Boolean = true

    protected open fun shouldInitializeReporting(): Boolean = true

    protected open fun shouldInitializeRevocationWorkManager(): Boolean = true

    protected open fun shouldInitializeReIssuanceWorkManager(): Boolean = true

    protected open fun shouldInitializeAppLockLifecycleObserver(): Boolean = true
}
