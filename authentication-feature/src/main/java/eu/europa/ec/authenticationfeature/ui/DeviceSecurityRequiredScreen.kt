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
package eu.europa.ec.authenticationfeature.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.content.ToolbarConfig
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.WrapButton
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun DeviceSecurityRequiredScreen(
    viewModel: DeviceSecurityRequiredViewModel = koinViewModel(),
    logController: LogController,
    onNavigateToWalletSetup: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val state by viewModel.viewState.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                DeviceSecurityRequiredEffect.NavigateToWalletSetup -> onNavigateToWalletSetup()
                DeviceSecurityRequiredEffect.NavigateToLogin -> onNavigateToLogin()
                DeviceSecurityRequiredEffect.OpenSecuritySettings -> openSecuritySettings(context, logController)
            }
        }
    }
    ContentScreen(
        isLoading = state.isChecking,
        navigatableAction = ScreenNavigateAction.NONE,
        toolBarConfig = ToolbarConfig(title = "Device Security Required")
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_warning),
                contentDescription = null,
                modifier = Modifier.height(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Secure Your Device",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "To create your wallet, set a screen lock or enroll biometrics. This protects your keys and meets security requirements.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!state.infoMessage.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = state.infoMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
            WrapButton(
                modifier = Modifier.fillMaxWidth(),
                buttonConfig = ButtonConfig(
                    type = ButtonType.PRIMARY,
                    enabled = !state.isChecking,
                    onClick = { viewModel.setEvent(DeviceSecurityRequiredEvent.Retry) }
                )
            ) {
                Text(text = if (state.isChecking) "Checking..." else "I've set it up — Retry")
            }
            Spacer(modifier = Modifier.height(16.dp))
            WrapButton(
                modifier = Modifier.fillMaxWidth(),
                buttonConfig = ButtonConfig(
                    type = ButtonType.SECONDARY,
                    enabled = !state.isChecking,
                    onClick = { viewModel.setEvent(DeviceSecurityRequiredEvent.OpenSettings) }
                )
            ) {
                Text(text = "Open Security Settings")
            }
            if (state.isChecking) {
                Spacer(modifier = Modifier.height(24.dp))
                CircularProgressIndicator(modifier = Modifier.height(32.dp))
            }
        }
    }
}

private fun openSecuritySettings(context: Context, logController: LogController) {
    val enrollIntent = Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
        putExtra(
            Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
            BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        )
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val fallbackIntent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(enrollIntent)
    } catch (e: Exception) {
        logController.w("DeviceSecurityRequiredScreen") { "Biometric enroll not available: ${e.message}" }
        try {
            context.startActivity(fallbackIntent)
        } catch (fallbackException: Exception) {
            logController.e("DeviceSecurityRequiredScreen", fallbackException)
        }
    }
}
