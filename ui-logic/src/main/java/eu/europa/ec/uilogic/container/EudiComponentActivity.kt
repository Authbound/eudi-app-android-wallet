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

package eu.europa.ec.uilogic.container

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import eu.europa.ec.businesslogic.controller.session.PresentationSessionController
import eu.europa.ec.businesslogic.provider.UuidProvider
import eu.europa.ec.corelogic.di.getOrNullKoinScope
import eu.europa.ec.resourceslogic.theme.ThemeManager
import eu.europa.ec.uilogic.extension.exposeTestTagsAsResourceId
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.RouterHost
import eu.europa.ec.uilogic.navigation.helper.DeepLinkAction
import eu.europa.ec.uilogic.navigation.helper.DeepLinkType
import eu.europa.ec.uilogic.navigation.helper.IntentType
import eu.europa.ec.uilogic.navigation.helper.handleDeepLinkAction
import eu.europa.ec.uilogic.navigation.helper.hasDeepLink
import eu.europa.ec.uilogic.navigation.helper.hasIntentAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.annotation.KoinViewModel

open class EudiComponentActivity : FragmentActivity() {

    private val routerHost: RouterHost by inject()
    private val viewModel: EudiComponentActivityViewModel by viewModel()

    private var flowStarted: Boolean = false

    internal var pendingDeepLink: Uri? = null
    internal var pendingIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.onCreate()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    internal fun cacheDeepLink(intent: Intent?) {
        pendingDeepLink = intent?.data
    }

    internal fun cacheIntent(intent: Intent?) {
        pendingIntent = intent
    }

    protected fun cacheDeepLink(uri: Uri?) {
        pendingDeepLink = uri
    }

    /**
     * Returns the current pending deeplink URI, if any.
     * Used by subclasses to preserve the deeplink across Activity restarts.
     */
    protected fun getPendingDeepLinkUri(): Uri? = pendingDeepLink

    @Composable
    protected fun Content(
        intent: Intent?,
        builder: NavGraphBuilder.(NavController) -> Unit,
    ) {
        ThemeManager.instance.Theme(darkTheme = true) {
            Surface(
                modifier = Modifier
                    .exposeTestTagsAsResourceId()
                    .fillMaxSize(),
                color = MaterialTheme.colorScheme.surface
            ) {
                routerHost.StartFlow {
                    builder(it)
                }
                flowStarted = true
                handleDeepLink(intent, coldBoot = true)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (flowStarted) {
            handleDeepLink(intent)
        } else {
            runPendingDeepLink(intent)
        }
    }

    private fun runPendingDeepLink(intent: Intent?) {
        lifecycleScope.launch {
            var count = 0
            while (!flowStarted && count <= 10) {
                count++
                delay(500)
            }
            if (count <= 10) {
                handleDeepLink(intent)
            }
        }
    }

    private fun handleDeepLink(intent: Intent?, coldBoot: Boolean = false) {
        hasDeepLink(intent?.data)?.let {
            if (it.type == DeepLinkType.ISSUANCE && !coldBoot) {
                handleDeepLinkAction(
                    routerHost.getNavController(),
                    it.link
                )
            } else if (
                it.type == DeepLinkType.CREDENTIAL_OFFER
                && !routerHost.userIsLoggedInWithDocuments()
                && routerHost.userIsLoggedInWithNoDocuments()
            ) {
                cacheDeepLink(intent)
                routerHost.popToIssuanceOnboardingScreen()
            } else if (it.type == DeepLinkType.OPENID4VP
                && routerHost.userIsLoggedInWithDocuments()
                && (routerHost.isScreenOnBackStackOrForeground(IssuanceScreens.AddDocument)
                        || routerHost.isScreenOnBackStackOrForeground(IssuanceScreens.DocumentOffer))
            ) {
                handleDeepLinkAction(
                    routerHost.getNavController(),
                    DeepLinkAction(it.link, DeepLinkType.DYNAMIC_PRESENTATION)
                )
            } else if (it.type != DeepLinkType.ISSUANCE) {
                cacheDeepLink(intent)
                if (routerHost.userIsLoggedInWithDocuments()) {
                    routerHost.popToDashboardScreen()
                }
            }
            setIntent(Intent())
        } ?: hasIntentAction(intent)?.let {
            when (it.type) {
                IntentType.DC_API -> {
                    cacheIntent(it.intent)
                    if (routerHost.userIsLoggedInWithDocuments()) {
                        routerHost.popToDashboardScreen()
                    }
                }
            }
            setIntent(Intent())
        }
    }
}

@KoinViewModel
internal class EudiComponentActivityViewModel(
    private val presentationSessionController: PresentationSessionController,
    uuidProvider: UuidProvider
) : ViewModel() {

    private val sessionId: String = uuidProvider.provideUuid()

    fun onCreate() {
        setSessionId()
    }

    fun onResume() {
        setSessionId()
    }

    override fun onCleared() {
        getOrNullKoinScope(sessionId)?.close()
        presentationSessionController.clearSessionId(sessionId)
        super.onCleared()
    }

    private fun setSessionId() {
        presentationSessionController.setSessionId(sessionId)
    }
}
