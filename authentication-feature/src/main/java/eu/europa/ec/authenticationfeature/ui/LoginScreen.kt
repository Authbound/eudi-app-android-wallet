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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.rememberNavController
import eu.europa.ec.authenticationlogic.model.OAuthProvider
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.content.ToolbarConfig
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.WrapButton
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LoginScreen(
    viewModel: AuthenticationViewModel,
    onNavigateToHome: () -> Unit,
    onNavigateToWalletSetup: () -> Unit,
    onNavigateToProfileCompletion: () -> Unit
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

    // Reset ViewModel state when LoginScreen is displayed
    LaunchedEffect(Unit) {
        viewModel.setEvent(Event.ResetViewModel)
    }

    // Handle hardware back button in signup mode
    BackHandler(enabled = state.isSignUpMode) {
        viewModel.setEvent(Event.ToggleMode) // Return to login mode
    }

    LaunchedEffect(viewModel) {
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
                is Effect.Navigation.NavigateToProfileCompletion -> onNavigateToProfileCompletion()

                is Effect.Navigation.PopBackStack -> {
                    // No-op for LoginScreen - this would only apply if LoginScreen had a back stack
                }

                is Effect.Navigation.NavigateToLoginAndClearStack -> {
                    // No-op for LoginScreen as this is the target screen
                }

                is Effect.Navigation.SignOutAndNavigateToLogin -> {
                    // No-op for LoginScreen - user is already being navigated here
                    // The logout has already been handled by the ViewModel
                    Toast.makeText(context, "Signed out successfully", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    ContentScreen(
        isLoading = state.isLoading,
        navigatableAction = ScreenNavigateAction.NONE, // Disable default navigation
        toolBarConfig = null, // Disable default toolbar
        topBar = if (state.isSignUpMode) {
            {
                // Custom transparent toolbar with just back arrow
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(Color.Transparent)
                ) {
                    IconButton(
                        onClick = { viewModel.setEvent(Event.ToggleMode) },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back to Login",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        } else {
            null
        },
    ) { paddingValues ->
        LoginFormContent(
            state = state,
            paddingValues = paddingValues,
            onEvent = viewModel::setEvent
        )
    }
}

@Composable
private fun LoginFormContent(
    state: State,
    paddingValues: PaddingValues,
    onEvent: (Event) -> Unit
) {
    val context = LocalContext.current

    // Animated gradient colors
    val infiniteTransition = rememberInfiniteTransition(label = "gradient")
    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradientOffset"
    )

    // Logo scale animation
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoScale"
    )

    // Use darker navy in dark mode, primary (deep navy) in light mode
    val isDarkTheme = isSystemInDarkTheme()
    val headerBackground = if (isDarkTheme) {
        // Dark mode: Use subtle dark navy gradient that blends with the dark background
        // Avoid bright blues - use muted, sophisticated tones
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF0F2142), // Slightly lighter than background for subtle contrast
                Color(0xFF0A1A36)  // Match background color for seamless blend
            ),
            start = Offset(0f, 0f),
            end = Offset(0f, Float.POSITIVE_INFINITY)
        )
    } else {
        // Light mode: Keep the original deep navy
        Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.primary
            )
        )
    }

    // Dark mode button colors - use softer, less saturated colors
    val primaryButtonColor = if (isDarkTheme) {
        Color(0xFF60A5FA) // Lighter, softer blue for dark mode
    } else {
        MaterialTheme.colorScheme.primary
    }

    val primaryButtonTextColor = if (isDarkTheme) {
        Color(0xFF0A1A36) // Dark text on light button in dark mode
    } else {
        MaterialTheme.colorScheme.onPrimary
    }

    val secondaryButtonBorderColor = if (isDarkTheme) {
        Color(0xFF60A5FA).copy(alpha = 0.5f) // Softer border in dark mode
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    }

    val linkTextColor = if (isDarkTheme) {
        Color(0xFF93C5FD) // Light blue for links in dark mode - high contrast
    } else {
        MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Header background with arc shape
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp)
                .clip(CustomWaveShape())
                .background(headerBackground)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
//            Spacer(Modifier.height(40.dp))

            // Animated Authbound branding
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Main Authbound logo with animation
                Box(
                    modifier = Modifier
                        .scale(logoScale)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f),
                                    Color.Transparent
                                ),
                                radius = 120f
                            ),
                            shape = CircleShape
                        )
                        .padding(16.dp)
                ) {
                    // Use white tint in dark mode for better visibility
                    Icon(
                        painter = painterResource(id = R.drawable.ic_authbound_logo),
                        contentDescription = null,
                        modifier = Modifier.size(148.dp),
                        tint = if (isDarkTheme) Color.White else Color.Unspecified
                    )
                }

//                Spacer(modifier = Modifier.height(4.dp))

                // Animated gradient text for main title
                Text(
                    text = "AUTHBOUND",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.onPrimary,
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                            ),
                            start = Offset(gradientOffset * 200f, 0f),
                            end = Offset(gradientOffset * 200f + 200f, 0f)
                        )
                    ),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimary
                )

//                Spacer(modifier = Modifier.height(6.dp))

                // Subtitle
                Text(
                    text = "Identity Wallet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Security tagline
                Text(
                    text = "Secure • Private • Trusted",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(30.dp))

            // Enhanced login form card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDarkTheme) {
                        Color(0xFF0F2142) // Slightly elevated surface for card in dark mode
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isDarkTheme) 8.dp else 16.dp),
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            secondaryButtonBorderColor,
                            Color.Transparent
                        )
                    )
                )
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Welcome header inside the card
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (state.isSignUpMode) "Create Account" else "Welcome Back",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = if (state.isSignUpMode) "Join the secure identity ecosystem" else "Sign in to your secure wallet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Input fields with dark mode optimized colors
                    val textFieldColors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isDarkTheme) Color(0xFF60A5FA) else MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = if (isDarkTheme) Color(0xFF5F6A85) else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        focusedLabelColor = if (isDarkTheme) Color(0xFF93C5FD) else MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = if (isDarkTheme) Color(0xFF9CA3AF) else MaterialTheme.colorScheme.onSurfaceVariant,
                        cursorColor = if (isDarkTheme) Color(0xFF60A5FA) else MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = state.email,
                        onValueChange = { onEvent(Event.OnEmailChanged(it)) },
                        label = { Text(stringResource(id = R.string.email)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        colors = textFieldColors
                    )

                    OutlinedTextField(
                        value = state.password,
                        onValueChange = { onEvent(Event.OnPasswordChanged(it)) },
                        label = { Text(stringResource(id = R.string.password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        colors = textFieldColors
                    )

                    if (state.isSignUpMode) {
                        OutlinedTextField(
                            value = state.confirmPassword,
                            onValueChange = { onEvent(Event.OnConfirmPasswordChanged(it)) },
                            label = { Text(stringResource(id = R.string.confirm_password)) },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            isError = state.error != null,
                            colors = textFieldColors
                        )
                    }

                    // Primary button
                    WrapButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        buttonConfig = ButtonConfig(
                            type = ButtonType.PRIMARY,
                            onClick = {
                                if (state.isSignUpMode) {
                                    onEvent(Event.SignUpWithEmailAndPassword)
                                } else {
                                    onEvent(Event.SignInWithEmailAndPassword)
                                }
                            },
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (state.isSignUpMode) {
                                Icon(
                                    painter = painterResource(id = R.drawable.baseline_person_24),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = stringResource(id = if (state.isSignUpMode) R.string.sign_up else R.string.sign_in),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    // Divider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                        Text(
                            text = "OR",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp),
                            fontWeight = FontWeight.Medium
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                    }

                    // Google Sign-In button
                    WrapButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        buttonConfig = ButtonConfig(
                            type = ButtonType.SECONDARY,
                            onClick = {
                                onEvent(
                                    Event.SignInWithOAuth(
                                        OAuthProvider.GOOGLE,
                                        context
                                    )
                                )
                            },
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.baseline_person_24),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = linkTextColor
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(id = R.string.login_with_google),
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Toggle button outside the card
            TextButton(
                onClick = { onEvent(Event.ToggleMode) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(
                        id = if (state.isSignUpMode) R.string.already_have_account else R.string.dont_have_account
                    ),
                    color = linkTextColor,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@ThemeModePreviews
@Composable
private fun LoginScreenPreview() {
    // Create a mock state for preview
    val mockState = State(
        email = "",
        password = "",
        confirmPassword = "",
        isSignUpMode = false,
        isLoading = false,
        error = null
    )
    PreviewTheme {
        ContentScreen(
            isLoading = false,
            navigatableAction = ScreenNavigateAction.NONE,
        ) { paddingValues ->
            LoginFormContent(
                state = mockState,
                paddingValues = paddingValues,
                onEvent = { /* No-op for preview */ }
            )
        }
    }
}
