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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import eu.europa.ec.resourceslogic.theme.ThemeManager
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import eu.europa.ec.authenticationlogic.model.OAuthProvider
import eu.europa.ec.businesslogic.extension.toUri
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ImePaddingConfig
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.SIZE_100
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.extension.applyTestTag
import eu.europa.ec.uilogic.extension.openUrl
import eu.europa.ec.uilogic.test.AuthTestTags
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LoginScreen(
    viewModel: AuthenticationViewModel,
    onNavigateToHome: () -> Unit,
    onNavigateToStartup: () -> Unit,
    onNavigateToWalletSetup: () -> Unit,
    onNavigateToProfileCompletion: () -> Unit,
    onNavigateToPinCreate: () -> Unit,
    onNavigateToPinVerify: () -> Unit
) {
    val state by viewModel.viewState.collectAsState()
    val context = LocalContext.current

    // Note: ON_RESUME loading dismissal removed — it was causing the login screen
    // to flash briefly after sign-up by prematurely clearing the loading overlay
    // before navigation to the profile completion screen could occur.
    // Loading is now managed entirely by the auth state observer in the ViewModel.

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

                is Effect.Navigation.NavigateToStartup -> onNavigateToStartup()
                is Effect.Navigation.NavigateToWalletSetup -> onNavigateToWalletSetup()
                is Effect.Navigation.NavigateToProfileCompletion -> onNavigateToProfileCompletion()
                is Effect.Navigation.NavigateToPinCreate -> onNavigateToPinCreate()
                is Effect.Navigation.NavigateToPinVerify -> onNavigateToPinVerify()

                is Effect.Navigation.PopBackStack -> {
                    // No-op for LoginScreen
                }

                is Effect.Navigation.NavigateToLoginAndClearStack -> {
                    // No-op for LoginScreen as this is the target screen
                }

                is Effect.Navigation.SignOutAndNavigateToLogin -> {
                    Toast.makeText(context, "Signed out successfully", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    ContentScreen(
        isLoading = state.isLoading,
        imePaddingConfig = ImePaddingConfig.ONLY_CONTENT,
        navigatableAction = ScreenNavigateAction.NONE,
        toolBarConfig = null,
        topBar = if (state.isSignUpMode) {
            {
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
    val isDarkTheme = ThemeManager.instance.set.isInDarkMode

    // Brand colors matching HomeScreen's navy spectrum
    val navyDeep = Color(0xFF0A1A36)
    val navyMedium = Color(0xFF1E3A5F)
    val accentBlue = MaterialTheme.colorScheme.tertiary

    // Diagonal gradient matching HomeScreen's card pattern
    val headerBackground = remember(isDarkTheme) {
        Brush.linearGradient(
            colors = listOf(navyDeep, navyMedium),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    }

    val cardBorderColor = if (isDarkTheme) {
        accentBlue.copy(alpha = 0.15f)
    } else {
        navyDeep.copy(alpha = 0.08f)
    }
    val termsUrl = stringResource(R.string.legal_terms_alpha_url)
    val privacyPolicyUrl = stringResource(R.string.legal_privacy_policy_url)

    val linkTextColor = if (isDarkTheme) {
        Color(0xFF93C5FD)
    } else {
        MaterialTheme.colorScheme.primary
    }

    // Staggered entrance animation states
    var brandingVisible by remember { mutableStateOf(false) }
    var cardVisible by remember { mutableStateOf(false) }
    var toggleVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(150)
        brandingVisible = true
        delay(250)
        cardVisible = true
        delay(200)
        toggleVisible = true
    }

    val passwordFocusRequester = remember { FocusRequester() }
    val confirmPasswordFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .applyTestTag(AuthTestTags.Login.ROOT)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Header background with wave shape and diagonal gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .clip(remember { CustomWaveShape() })
                .background(headerBackground)
        ) {
            // Decorative circles matching HomeScreen's brand motif
            LoginDecorativeCircles(
                modifier = Modifier.align(Alignment.TopEnd),
                color = Color.White
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Branding section with entrance animation
            AnimatedVisibility(
                visible = brandingVisible,
                enter = fadeIn(tween(400)) + slideInVertically(
                    animationSpec = tween(400),
                    initialOffsetY = { it / 3 }
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(32.dp))

                    Icon(
                        painter = painterResource(id = R.drawable.authbound_logo_bold),
                        contentDescription = null,
                        modifier = Modifier
                            .size(120.dp),
                        tint = Color.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Authbound",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = FontFamily(Font(R.font.lato_black))
                        ),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = Color.White
                    )

                    Text(
                        text = "Identity Wallet",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Secure \u2022 Private \u2022 Trusted",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Form card with entrance animation
            AnimatedVisibility(
                visible = cardVisible,
                enter = fadeIn(tween(400)) + slideInVertically(
                    animationSpec = tween(400),
                    initialOffsetY = { it / 3 }
                )
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDarkTheme) {
                            Color(0xFF0F2142)
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(cardBorderColor, Color.Transparent)
                        )
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Welcome header
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (state.isSignUpMode) "Create Account" else "Welcome Back",
                                style = MaterialTheme.typography.headlineSmall,
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

                        // Text field colors
                        val textFieldColors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (isDarkTheme) Color(0xFF60A5FA) else MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = if (isDarkTheme) Color(0xFF5F6A85) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            focusedLabelColor = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            cursorColor = if (isDarkTheme) Color(0xFF60A5FA) else MaterialTheme.colorScheme.primary,
                            focusedTextColor = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = if (isDarkTheme) Color.White.copy(alpha = 0.87f) else MaterialTheme.colorScheme.onSurface
                        )

                        OutlinedTextField(
                            value = state.email,
                            onValueChange = { onEvent(Event.OnEmailChanged(it)) },
                            label = { Text(stringResource(id = R.string.email)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .applyTestTag(AuthTestTags.Login.EMAIL_FIELD),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            colors = textFieldColors,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { passwordFocusRequester.requestFocus() }
                            )
                        )

                        OutlinedTextField(
                            value = state.password,
                            onValueChange = { onEvent(Event.OnPasswordChanged(it)) },
                            label = { Text(stringResource(id = R.string.password)) },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .applyTestTag(AuthTestTags.Login.PASSWORD_FIELD)
                                .focusRequester(passwordFocusRequester),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            colors = textFieldColors,
                            keyboardOptions = KeyboardOptions(
                                imeAction = if (state.isSignUpMode) {
                                    ImeAction.Next
                                } else {
                                    ImeAction.Done
                                }
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = {
                                    if (state.isSignUpMode) {
                                        confirmPasswordFocusRequester.requestFocus()
                                    }
                                },
                                onDone = {
                                    if (state.isSignUpMode) {
                                        confirmPasswordFocusRequester.requestFocus()
                                    } else {
                                        keyboardController?.hide()
                                    }
                                }
                            )
                        )

                        if (state.isSignUpMode) {
                            OutlinedTextField(
                                value = state.confirmPassword,
                                onValueChange = { onEvent(Event.OnConfirmPasswordChanged(it)) },
                                label = { Text(stringResource(id = R.string.confirm_password)) },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(confirmPasswordFocusRequester),
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true,
                                isError = state.error != null,
                                colors = textFieldColors,
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Done
                                )
                            )
                        }

                        // Primary action button
                        WrapButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .applyTestTag(AuthTestTags.Login.PRIMARY_BUTTON)
                                .height(52.dp),
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

                        // OR divider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                            Text(
                                text = "OR",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                fontWeight = FontWeight.Medium
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        }

                        // Google OAuth button — follows Google branding guidelines:
                        // white/light filled background, multicolor "G" logo, dark text
                        Button(
                            onClick = {
                                onEvent(
                                    Event.SignInWithOAuth(
                                        OAuthProvider.GOOGLE,
                                        context
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .applyTestTag(AuthTestTags.Login.GOOGLE_BUTTON)
                                .height(52.dp),
                            shape = RoundedCornerShape(SIZE_100.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDarkTheme) Color(0xFF1F1F1F) else Color.White,
                                contentColor = if (isDarkTheme) Color(0xFFE3E3E3) else Color(0xFF1F1F1F)
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 1.dp
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isDarkTheme) Color(0xFF8E918F) else Color(0xFF747775)
                            ),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_google_logo),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = Color.Unspecified
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(id = R.string.login_with_google),
                                    fontWeight = FontWeight.Medium,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }
            }

            // Mode toggle with entrance animation
            AnimatedVisibility(
                visible = toggleVisible,
                enter = fadeIn(tween(400)) + slideInVertically(
                    animationSpec = tween(300),
                    initialOffsetY = { it / 2 }
                )
            ) {
                TextButton(
                    onClick = { onEvent(Event.ToggleMode) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .applyTestTag(AuthTestTags.Login.TOGGLE_MODE_BUTTON)
                        .padding(top = 12.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    val questionPart = if (state.isSignUpMode) {
                        "Already have an account? "
                    } else {
                        "Don\u2019t have an account? "
                    }
                    val actionPart = stringResource(
                        id = if (state.isSignUpMode) R.string.sign_in else R.string.sign_up
                    )
                    val questionColor = if (isDarkTheme) Color.White.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    val actionColor = if (isDarkTheme) Color.White else accentBlue
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    color = questionColor,
                                    fontWeight = FontWeight.Normal
                                )
                            ) {
                                append(questionPart)
                            }
                            withStyle(
                                SpanStyle(
                                    color = actionColor,
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append(actionPart)
                            }
                        }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.login_legal_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            context.openUrl(
                                termsUrl.toUri()
                            )
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.login_terms_link),
                            color = linkTextColor
                        )
                    }
                    Text(
                        text = stringResource(R.string.login_legal_separator),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = {
                            context.openUrl(
                                privacyPolicyUrl.toUri()
                            )
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.login_privacy_link),
                            color = linkTextColor
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Decorative filled circles creating a soft ambient depth effect
 * in the header. All circles are filled (no outlines) with varying
 * sizes and opacities for a modern layered look.
 */
@Composable
private fun LoginDecorativeCircles(
    modifier: Modifier = Modifier,
    color: Color
) {
    Canvas(
        modifier = modifier
            .size(200.dp)
            .padding(8.dp)
    ) {
        // Large soft circle — ambient glow
        drawCircle(
            color = color.copy(alpha = 0.06f),
            radius = 70.dp.toPx(),
            center = Offset(size.width * 0.55f, size.height * 0.25f)
        )
        // Medium circle — primary accent
        drawCircle(
            color = color.copy(alpha = 0.10f),
            radius = 32.dp.toPx(),
            center = Offset(size.width * 0.30f, size.height * 0.50f)
        )
        // Small circle — detail
        drawCircle(
            color = color.copy(alpha = 0.14f),
            radius = 14.dp.toPx(),
            center = Offset(size.width * 0.75f, size.height * 0.65f)
        )
        // Tiny circle — sparkle
        drawCircle(
            color = color.copy(alpha = 0.18f),
            radius = 6.dp.toPx(),
            center = Offset(size.width * 0.50f, size.height * 0.78f)
        )
    }
}

@ThemeModePreviews
@Composable
private fun LoginScreenPreview() {
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
