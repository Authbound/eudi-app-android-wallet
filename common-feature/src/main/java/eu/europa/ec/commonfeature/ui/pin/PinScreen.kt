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

package eu.europa.ec.commonfeature.ui.pin

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.commonfeature.model.PinFlow
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIconAndText
import eu.europa.ec.uilogic.component.AppIconAndTextData
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ImePaddingConfig
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.OneTimeLaunchedEffect
import eu.europa.ec.uilogic.component.wrap.BottomSheetTextDataUi
import eu.europa.ec.uilogic.component.wrap.DialogBottomSheet
import eu.europa.ec.uilogic.component.wrap.WrapModalBottomSheet
import eu.europa.ec.uilogic.component.wrap.PinIndicator
import eu.europa.ec.uilogic.component.wrap.WrapPinKeypad
import eu.europa.ec.uilogic.extension.applyTestTag
import eu.europa.ec.uilogic.extension.exposeTestTagsAsResourceId
import eu.europa.ec.uilogic.extension.finish
import eu.europa.ec.uilogic.navigation.AuthenticationScreens
import eu.europa.ec.uilogic.navigation.CommonScreens
import eu.europa.ec.uilogic.test.AuthTestTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinScreen(
    navController: NavController,
    viewModel: PinViewModel,
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val isBottomSheetOpen = state.isBottomSheetOpen
    val scope = rememberCoroutineScope()
    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ContentScreen(
        isLoading = state.isLoading,
        navigatableAction = state.action,
        onBack = { viewModel.setEvent(state.onBackEvent) },
        imePaddingConfig = ImePaddingConfig.ONLY_CONTENT,
    ) { paddingValues ->
        Content(
            state = state,
            effectFlow = viewModel.effect,
            onEventSend = { event -> viewModel.setEvent(event) },
            onNavigationRequested = { navigationEffect ->
                handleNavigationEffect(
                    context,
                    navigationEffect,
                    navController
                )
            },
            paddingValues = paddingValues,
            coroutineScope = scope,
            modalBottomSheetState = bottomSheetState,
        )

        if (isBottomSheetOpen) {
            WrapModalBottomSheet(
                onDismissRequest = {
                    viewModel.setEvent(
                        Event.BottomSheet.UpdateBottomSheetState(
                            isOpen = false
                        )
                    )
                },
                sheetState = bottomSheetState
            ) {
                SheetContent(
                    onEventSent = {
                        viewModel.setEvent(it)
                    }
                )
            }
        }

        if (state.showResetConfirmation) {
            WrapModalBottomSheet(
                onDismissRequest = {
                    viewModel.setEvent(Event.BottomSheet.Reset.CancelPressed)
                },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                ResetConfirmationSheetContent(
                    onEventSent = { viewModel.setEvent(it) }
                )
            }
        }

        if (state.showBiometricsPreferencePrompt) {
            AlertDialog(
                modifier = Modifier
                    .exposeTestTagsAsResourceId()
                    .applyTestTag(AuthTestTags.QuickPin.BIOMETRIC_DIALOG),
                onDismissRequest = { },
                title = { Text(text = stringResource(R.string.quick_pin_biometrics_prompt_title)) },
                text = { Text(text = stringResource(R.string.quick_pin_biometrics_prompt_description)) },
                confirmButton = {
                    TextButton(
                        modifier = Modifier.applyTestTag(
                            AuthTestTags.QuickPin.BIOMETRIC_ENABLE_BUTTON
                        ),
                        onClick = {
                            viewModel.setEvent(
                                Event.OnBiometricsPreferenceSelected(shouldUseBiometrics = true)
                            )
                        }
                    ) {
                        Text(text = stringResource(R.string.quick_pin_biometrics_prompt_enable))
                    }
                },
                dismissButton = {
                    TextButton(
                        modifier = Modifier.applyTestTag(
                            AuthTestTags.QuickPin.BIOMETRIC_SKIP_BUTTON
                        ),
                        onClick = {
                            viewModel.setEvent(
                                Event.OnBiometricsPreferenceSelected(shouldUseBiometrics = false)
                            )
                        }
                    ) {
                        Text(text = stringResource(R.string.quick_pin_biometrics_prompt_disable))
                    }
                }
            )
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
            navController.navigate(navigationEffect.screen) {
                popUpTo(CommonScreens.QuickPin.screenRoute) {
                    inclusive = true
                }
            }
        }

        is Effect.Navigation.SwitchModule -> navController.navigate(navigationEffect.moduleRoute.route)

        is Effect.Navigation.Pop -> navController.popBackStack()
        is Effect.Navigation.Finish -> context.finish()

        is Effect.Navigation.GoToLogin -> {
            navController.navigate(AuthenticationScreens.Login.screenRoute) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }

        is Effect.Navigation.GoToWalletSetup -> {
            navController.navigate(AuthenticationScreens.WalletSetup.screenRoute) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Content(
    state: State,
    effectFlow: Flow<Effect>,
    onEventSend: (Event) -> Unit,
    onNavigationRequested: (Effect.Navigation) -> Unit,
    paddingValues: PaddingValues,
    coroutineScope: CoroutineScope,
    modalBottomSheetState: SheetState,
) {
    val localContext = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // This screen uses an in-app keypad. Ensure the system keyboard stays hidden.
    OneTimeLaunchedEffect {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        if (state.shouldShowBiometricLoginButton) {
            onEventSend(
                Event.OnBiometricLoginPressed(context = localContext)
            )
        }
    }

    // Responsive PIN screen: sizes derived from available screen dimensions.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        val screenWidth = maxWidth

        // Scale logo proportionally — cap width fraction so it doesn't bloat on tablets.
        val logoWidthFraction = 0.50f
        val logoHeight = (screenWidth * logoWidthFraction) * (35.61f / 120f)
        val logoTopPadding = paddingValues.calculateTopPadding() + (screenHeight * 0.02f)

        // Keypad key size: pick the smaller of width-based and height-based limits
        // so the keypad fits comfortably on any screen.
        val keyFromWidth = (screenWidth - 32.dp) / 3   // 3 keys + spacing
        val keyFromHeight = screenHeight * 0.10f         // ~10% of screen height
        val keySpacing = (screenHeight * 0.014f).coerceIn(10.dp, 18.dp)
        val adaptiveKeySize = minOf(keyFromWidth, keyFromHeight, 80.dp)

        // Spacing between sections, proportional to screen height.
        val pinToKeypadSpacing = (screenHeight * 0.03f).coerceIn(16.dp, 40.dp)
        val titleToSubtitleSpacing = (screenHeight * 0.008f).coerceIn(4.dp, 12.dp)

        // Main screen content respects ContentScreen paddings (toolbar, insets, etc.).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .applyTestTag(AuthTestTags.QuickPin.ROOT)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Top section: logo space + title + subtitle — flexes to fill remaining space.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Reserve space for the overlaid logo.
                    Spacer(modifier = Modifier.height(logoHeight + (screenHeight * 0.03f)))

                    // Title — e.g., "Create Passcode" or "Enter Passcode"
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .applyTestTag(AuthTestTags.QuickPin.TITLE)
                            .padding(bottom = titleToSubtitleSpacing)
                    )

                    // Subtitle — e.g., "Enter a 6-digit passcode"
                    Text(
                        text = state.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .applyTestTag(AuthTestTags.QuickPin.SUBTITLE)
                            .padding(horizontal = 32.dp, vertical = 4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                // PIN indicators — directly above the keypad.
                PinFieldLayout(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = pinToKeypadSpacing),
                    state = state
                )

                // Keypad — pinned to the bottom, sized proportionally.
                WrapPinKeypad(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    maxKeySize = adaptiveKeySize,
                    keySpacing = keySpacing,
                    enabled = !state.pinSuccess && !state.isTransitioning,
                    leadingIconData = if (state.shouldShowBiometricLoginButton) AppIcons.TouchId else null,
                    onLeadingPressed = if (state.shouldShowBiometricLoginButton) {
                        {
                            onEventSend(
                                Event.OnBiometricLoginPressed(context = localContext)
                            )
                        }
                    } else null,
                    keypadTestTag = AuthTestTags.QuickPin.KEYPAD,
                    digitTestTagProvider = AuthTestTags.QuickPin::digit,
                    leadingButtonTestTag = AuthTestTags.QuickPin.BIOMETRIC_CTA,
                    backspaceTestTag = AuthTestTags.QuickPin.BACKSPACE,
                    onDigitPressed = { digit ->
                        onEventSend(Event.OnQuickPinDigitPressed(digit))
                    },
                    onBackspacePressed = {
                        onEventSend(Event.OnQuickPinBackspacePressed)
                    }
                )
            } // end main Column
        }

        // Logo overlay — floats above the scroll content.
        AppIconAndText(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = logoTopPadding)
                .fillMaxWidth(logoWidthFraction),
            iconModifier = Modifier
                .fillMaxWidth()
                .aspectRatio(120f / 35.61f),
            appIconAndTextData = AppIconAndTextData(),
        )

        // Overflow menu overlay — only visible during VERIFY flow.
        if (state.isVerifyFlow) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = paddingValues.calculateTopPadding(), end = 4.dp)
                    .offset(y = (-6).dp)
            ) {
                IconButton(
                    modifier = Modifier.applyTestTag(AuthTestTags.QuickPin.OVERFLOW_BUTTON),
                    onClick = { onEventSend(Event.OverflowMenuToggled(true)) }
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(id = R.string.quick_pin_forgot_menu_item),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = state.showOverflowMenu,
                    onDismissRequest = { onEventSend(Event.OverflowMenuToggled(false)) }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(text = stringResource(id = R.string.quick_pin_forgot_menu_item))
                        },
                        modifier = Modifier.applyTestTag(AuthTestTags.QuickPin.FORGOT_PIN_ACTION),
                        onClick = {
                            onEventSend(Event.OverflowMenuToggled(false))
                            onEventSend(Event.ForgotPinPressed)
                        }
                    )
                }
            }
        }

        // Fade-to-background overlay: a flat rectangle that fades in on top of everything.
        // Much cheaper than animating alpha on the content tree.
        if (state.isTransitioning) {
            val overlayAlpha by animateFloatAsState(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 300),
                label = "fadeOverlay"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = overlayAlpha }
                    .background(MaterialTheme.colorScheme.background)
            )
        }
    }

    LaunchedEffect(Unit) {
        effectFlow.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> onNavigationRequested(effect)

                is Effect.CloseBottomSheet -> {
                    coroutineScope.launch {
                        modalBottomSheetState.hide()
                    }.invokeOnCompletion {
                        if (!modalBottomSheetState.isVisible) {
                            onEventSend(Event.BottomSheet.UpdateBottomSheetState(isOpen = false))
                        }
                    }
                }

                is Effect.ShowBottomSheet -> {
                    onEventSend(Event.BottomSheet.UpdateBottomSheetState(isOpen = true))
                }

                is Effect.TriggerBiometricAuth -> {
                    onEventSend(Event.OnBiometricLoginPressed(context = localContext))
                }
            }
        }.collect()
    }
}

@Composable
private fun SheetContent(
    onEventSent: (event: Event) -> Unit
) {
    DialogBottomSheet(
        textData = BottomSheetTextDataUi(
            title = stringResource(id = R.string.quick_pin_bottom_sheet_cancel_title),
            message = stringResource(id = R.string.quick_pin_bottom_sheet_cancel_subtitle),
            positiveButtonText = stringResource(id = R.string.quick_pin_bottom_sheet_cancel_primary_button_text),
            negativeButtonText = stringResource(id = R.string.quick_pin_bottom_sheet_cancel_secondary_button_text),
        ),
        onPositiveClick = { onEventSent(Event.BottomSheet.Cancel.PrimaryButtonPressed) },
        onNegativeClick = { onEventSent(Event.BottomSheet.Cancel.SecondaryButtonPressed) }
    )
}

@Composable
private fun ResetConfirmationSheetContent(
    onEventSent: (event: Event) -> Unit
) {
    val context = LocalContext.current
    DialogBottomSheet(
        textData = BottomSheetTextDataUi(
            title = stringResource(id = R.string.quick_pin_reset_title),
            message = stringResource(id = R.string.quick_pin_reset_subtitle),
            positiveButtonText = stringResource(id = R.string.quick_pin_reset_primary_button),
            negativeButtonText = stringResource(id = R.string.quick_pin_reset_secondary_button),
        ),
        onPositiveClick = { onEventSent(Event.BottomSheet.Reset.ConfirmPressed(context)) },
        onNegativeClick = { onEventSent(Event.BottomSheet.Reset.CancelPressed) }
    )
}

@Composable
private fun PinFieldLayout(
    modifier: Modifier = Modifier,
    state: State,
) {
    PinIndicator(
        modifier = modifier,
        pinLength = state.quickPinSize,
        filledCount = state.pinLength,
        hasError = !state.quickPinError.isNullOrEmpty(),
        hasSuccess = state.pinSuccess,
        errorMessage = state.quickPinError,
        errorMessageTestTag = AuthTestTags.QuickPin.ERROR_MESSAGE,
        indicatorTestTagProvider = AuthTestTags.QuickPin::indicator,
        circleSize = 16.dp,
        circleSpacing = 20.dp
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@ThemeModePreviews
@Composable
private fun PinScreenEmptyPreview() {
    PreviewTheme {
        Content(
            state = State(
                pinFlow = PinFlow.CREATE_WITH_ACTIVATION,
                pinState = PinValidationState.ENTER
            ),
            effectFlow = Channel<Effect>().receiveAsFlow(),
            onEventSend = {},
            onNavigationRequested = {},
            paddingValues = PaddingValues(10.dp),
            coroutineScope = rememberCoroutineScope(),
            modalBottomSheetState = rememberModalBottomSheetState(),
        )
    }
}

@ThemeModePreviews
@Composable
private fun SheetContentCancelPreview() {
    PreviewTheme {
        SheetContent(
            onEventSent = {}
        )
    }
}
