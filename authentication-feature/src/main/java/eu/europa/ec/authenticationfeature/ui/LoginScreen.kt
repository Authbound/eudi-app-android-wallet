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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.rememberNavController
import eu.europa.ec.authenticationlogic.model.OAuthProvider
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.WrapButton
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.StartupScreens

@Composable
fun LoginScreen(
    viewModel: AuthenticationViewModel,
    onNavigateToHome: () -> Unit,
    onNavigateToWalletSetup: () -> Unit,
) {
    val state by viewModel.viewState.collectAsState()
    val context = LocalContext.current

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (state.isLoading) {
                    viewModel.setEvent(Event.DismissLoading)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is Effect.NavigateToHome -> onNavigateToHome()
                is Effect.ShowError -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }

                is Effect.ShowInfo -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_LONG).show()
                }
                
                is Effect.Navigation.NavigateToWalletSetup -> onNavigateToWalletSetup()
                
                is Effect.Navigation.NavigateBackToLogin -> {
                    // No-op for LoginScreen as this is the target screen
                }
            }
        }
    }

    ContentScreen(
        isLoading = state.isLoading || state.isActivating,
        navigatableAction = ScreenNavigateAction.NONE,
    ) { paddingValues ->
        if (state.isActivating) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Securing wallet...",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            TextField(
                value = state.email,
                onValueChange = { viewModel.setEvent(Event.OnEmailChanged(it)) },
                label = { Text(stringResource(id = R.string.email)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = state.password,
                onValueChange = { viewModel.setEvent(Event.OnPasswordChanged(it)) },
                label = { Text(stringResource(id = R.string.password)) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
                if (state.isSignUpMode) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = state.confirmPassword,
                        onValueChange = { viewModel.setEvent(Event.OnConfirmPasswordChanged(it)) },
                        label = { Text(stringResource(id = R.string.confirm_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        isError = state.error != null
                    )
                }
            Spacer(modifier = Modifier.height(16.dp))
            WrapButton(
                modifier = Modifier.fillMaxWidth(),
                buttonConfig = ButtonConfig(
                    type = ButtonType.PRIMARY,
                        onClick = {
                            if (state.isSignUpMode) {
                                viewModel.setEvent(Event.SignUpWithEmailAndPassword)
                            } else {
                                viewModel.setEvent(Event.SignInWithEmailAndPassword)
                            }
                        },
                )
            ) {
                    Text(text = stringResource(id = if (state.isSignUpMode) R.string.sign_up else R.string.sign_in))
            }
            Spacer(modifier = Modifier.height(16.dp))
            WrapButton(
                modifier = Modifier.fillMaxWidth(),
                buttonConfig = ButtonConfig(
                    type = ButtonType.SECONDARY,
                    onClick = {
                        viewModel.setEvent(
                            Event.SignInWithOAuth(
                                OAuthProvider.GOOGLE,
                                context
                            )
                        )
                    },
                )
            ) {
                Text(text = stringResource(id = R.string.login_with_google))
            }
            Spacer(modifier = Modifier.height(8.dp))
            WrapButton(
                modifier = Modifier.fillMaxWidth(),
                buttonConfig = ButtonConfig(
                    type = ButtonType.SECONDARY,
                    onClick = {
                        viewModel.setEvent(
                            Event.SignInWithOAuth(
                                OAuthProvider.MICROSOFT,
                                context
                            )
                        )
                    },
                )
            ) {
                Text(text = stringResource(id = R.string.login_with_microsoft))
            }
            Spacer(modifier = Modifier.height(8.dp))
            WrapButton(
                modifier = Modifier.fillMaxWidth(),
                buttonConfig = ButtonConfig(
                    type = ButtonType.SECONDARY,
                    onClick = {
                        viewModel.setEvent(
                            Event.SignInWithOAuth(
                                OAuthProvider.META,
                                context
                            )
                        )
                    },
                )
            ) {
                Text(text = stringResource(id = R.string.login_with_meta))
            }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { viewModel.setEvent(Event.ToggleMode) }) {
                    Text(
                        text = stringResource(
                            id = if (state.isSignUpMode) R.string.already_have_account else R.string.dont_have_account
                        )
                    )
                }
            }
        }
    }
}


@ThemeModePreviews
@Composable
private fun LoginScreenPreview() {
    PreviewTheme {
        LoginScreen(viewModel = koinViewModel(), onNavigateToHome = {}, onNavigateToWalletSetup = {})
    }
} 