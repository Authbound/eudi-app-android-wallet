/*
 * Copyright (c) 2024 European Commission
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

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ImePaddingConfig
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.extension.applyTestTag
import eu.europa.ec.uilogic.test.AuthTestTags
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ProfileCompletionScreen(
    viewModel: ProfileCompletionViewModel,
    onNavigateToWalletSetup: () -> Unit,
    onNavigateToLogin: () -> Unit = {}
) {
    val state by viewModel.viewState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is ProfileCompletionEffect.NavigateToWalletSetup -> onNavigateToWalletSetup()
                is ProfileCompletionEffect.NavigateToLogin -> {
                    onNavigateToLogin()
                }
                is ProfileCompletionEffect.ShowError -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    ContentScreen(
        isLoading = state.isLoading,
        imePaddingConfig = ImePaddingConfig.ONLY_CONTENT,
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack = { viewModel.setEvent(ProfileCompletionEvent.SignOut) },
    ) { paddingValues ->
        ProfileCompletionContent(
            state = state,
            paddingValues = paddingValues,
            onEvent = viewModel::setEvent
        )
    }
}

@Composable
private fun ProfileCompletionContent(
    state: ProfileCompletionState,
    paddingValues: PaddingValues,
    onEvent: (ProfileCompletionEvent) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .applyTestTag(AuthTestTags.ProfileCompletion.ROOT)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Complete Your Profile",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Choose a unique handle and display name to get started.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = state.handle,
            onValueChange = { onEvent(ProfileCompletionEvent.OnHandleChanged(it)) },
            label = { Text("Handle") },
            leadingIcon = { Text("@") },
            modifier = Modifier
                .fillMaxWidth()
                .applyTestTag(AuthTestTags.ProfileCompletion.HANDLE_FIELD),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            isError = state.isHandleAvailable == false,
            supportingText = {
                when (state.isHandleAvailable) {
                    true -> Text("Handle is available!", color = MaterialTheme.colorScheme.primary)
                    false -> Text("Handle is not available or too short.", color = MaterialTheme.colorScheme.error)
                    null -> Text("Your unique identifier on the platform.")
                }
            }
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = state.displayName,
            onValueChange = { onEvent(ProfileCompletionEvent.OnDisplayNameChanged(it)) },
            label = { Text("Display Name") },
            modifier = Modifier
                .fillMaxWidth()
                .applyTestTag(AuthTestTags.ProfileCompletion.DISPLAY_NAME_FIELD),
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(32.dp))

        // Show WUA creation progress if in progress
        if (state.isCreatingWUA || state.wuaCreationStep.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (state.isCreatingWUA) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(
                        text = state.wuaCreationStep,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Show error message if present
        if (state.error != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Main action button - handles both initial activation and retry
        Button(
            onClick = { 
                if (state.lastError != null && state.canRetry) {
                    onEvent(ProfileCompletionEvent.RetryWalletActivation)
                } else {
                    onEvent(ProfileCompletionEvent.CompleteProfile)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .applyTestTag(AuthTestTags.ProfileCompletion.ACTIVATE_BUTTON)
                .height(50.dp),
            enabled = (state.isHandleAvailable == true && state.displayName.isNotBlank() && !state.isLoading) || (state.canRetry && state.lastError != null && !state.isLoading)
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    if (state.lastError != null && state.canRetry) {
                        "Try Again"
                    } else {
                        "Activate Wallet"
                    }
                )
            }
        }
        
        // Always show sign out button as escape route
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(
            onClick = { onEvent(ProfileCompletionEvent.SignOut) },
            modifier = Modifier
                .fillMaxWidth()
                .applyTestTag(AuthTestTags.ProfileCompletion.SIGN_OUT_BUTTON),
            enabled = !state.isLoading,
        ) {
            Text(
                text = "Sign Out",
                color = MaterialTheme.colorScheme.error
            )
        }
    }
} 
