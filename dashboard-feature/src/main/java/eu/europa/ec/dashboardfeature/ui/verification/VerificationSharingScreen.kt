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

package eu.europa.ec.dashboardfeature.ui.verification

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.utils.OneTimeLaunchedEffect
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach

@Composable
fun VerificationSharingScreen(
    navController: NavController,
    viewModel: VerificationViewModel
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    ContentScreen(
        isLoading = state.isLoading,
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack = { navController.popBackStack() },
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 16.dp,
                        vertical = 12.dp
                    )
            ) {
                Text(
                    modifier = Modifier.align(Alignment.Center),
                    text = stringResource(R.string.verification_sharing_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    ) { paddingValues ->
        SharingContent(
            state = state,
            effectFlow = viewModel.effect,
            onEventSent = { event -> viewModel.setEvent(event) },
            onNavigationRequested = { navigationEffect ->
                handleNavigationEffect(context, navigationEffect, navController)
            },
            paddingValues = paddingValues
        )
    }
}

@Composable
private fun SharingContent(
    state: State,
    effectFlow: Flow<Effect>,
    onEventSent: (Event) -> Unit,
    onNavigationRequested: (Effect.Navigation) -> Unit,
    paddingValues: PaddingValues
) {
    LaunchedEffect(effectFlow) {
        effectFlow.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> onNavigationRequested(effect)
                is Effect.ShareContent -> {
                    // For sharing content, you could implement platform-specific share functionality
                }
                is Effect.ShowToast -> {
                    // Toast message (could be implemented with a Snackbar)
                }
            }
        }.collect()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.isLoading) {
            CircularProgressIndicator()
        } else if (state.verificationSession != null) {
            // Session details card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = state.verificationSession.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        text = state.verificationSession.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    Text(
                        text = "Parameters: ${state.verificationSession.parameters.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // QR Code (placeholder)
            if (state.qrCodeGenerated) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "QR Code",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Button(
                    onClick = { onEventSent(Event.GenerateQrCode) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    WrapIcon(iconData = AppIcons.QR)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate QR Code")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Sharing options
            Text(
                text = "Share via",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(
                    onClick = { onEventSent(Event.ShareVia(SharingMethod.WHATSAPP)) }
                ) {
                    Text("WhatsApp")
                }
                
                OutlinedButton(
                    onClick = { onEventSent(Event.ShareVia(SharingMethod.EMAIL)) }
                ) {
                    Text("Email")
                }
                
                OutlinedButton(
                    onClick = { onEventSent(Event.ShareVia(SharingMethod.MESSAGES)) }
                ) {
                    Text("Messages")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Copy/Share link button
            Button(
                onClick = { onEventSent(Event.ShareLink) },
                modifier = Modifier.fillMaxWidth()
            ) {
                WrapIcon(iconData = AppIcons.Info)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share Link")
            }
            
            OutlinedButton(
                onClick = { onEventSent(Event.CopyLink) },
                modifier = Modifier.fillMaxWidth()
            ) {
                WrapIcon(iconData = AppIcons.Contract)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copy Link")
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Done button to return home
            Button(
                onClick = { onEventSent(Event.NavigateHome) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Done")
            }
        } else {
            // Error or no session state
            Text(
                text = state.error ?: "No verification session available",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            
            Button(
                onClick = { onEventSent(Event.NavigateBack) }
            ) {
                Text("Go Back")
            }
        }
    }
}

private fun handleNavigationEffect(
    context: Context,
    navigationEffect: Effect.Navigation,
    navController: NavController
) {
    when (navigationEffect) {
        is Effect.Navigation.SwitchScreen -> {
            navController.navigate(navigationEffect.screenRoute) {
                popUpTo(navigationEffect.popUpToScreenRoute) {
                    inclusive = navigationEffect.inclusive
                }
            }
        }
        
        is Effect.Navigation.Back -> {
            navController.popBackStack()
        }
        
        is Effect.Navigation.Home -> {
            navController.popBackStack()
        }
    }
} 