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
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.commonfeature.model.PinFlow
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIconAndText
import eu.europa.ec.uilogic.component.AppIconAndTextData
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ImePaddingConfig
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.OneTimeLaunchedEffect
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.BottomSheetTextDataUi
import eu.europa.ec.uilogic.component.wrap.DialogBottomSheet
import eu.europa.ec.uilogic.component.wrap.WrapModalBottomSheet
import eu.europa.ec.uilogic.component.wrap.PinIndicator
import eu.europa.ec.uilogic.component.wrap.WrapPinKeypad
import eu.europa.ec.uilogic.extension.finish
import eu.europa.ec.uilogic.navigation.AuthenticationScreens
import eu.europa.ec.uilogic.navigation.CommonScreens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
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
        stickyBottom = { paddingValues ->
            ModernVerifyButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                text = state.buttonText,
                enabled = state.isButtonEnabled,
                onClick = {
                    viewModel.setEvent(Event.NextButtonPressed(pin = state.pin))
                }
            )
        }
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
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // This screen uses an in-app keypad. Ensure the system keyboard stays hidden.
    OneTimeLaunchedEffect {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    // Standard PIN screen layout: Top branding, Middle content (flexible), Bottom keypad (fixed)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val logoWidthFraction = 0.62f
        val logoHeight = (maxWidth * logoWidthFraction) * (35.61f / 120f)
        val logoReservedHeight = logoHeight + 24.dp

        // Main screen content respects ContentScreen paddings (toolbar, insets, etc.).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Top section with instructions - takes remaining space
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 24.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Reserve space so the title/subtitle do not move when the logo is overlaid.
                    Spacer(modifier = Modifier.height(logoReservedHeight))
                    Spacer(modifier = Modifier.height(32.dp))

                    // Title - e.g., "Create Passcode" or "Enter Passcode"
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Subtitle - e.g., "Enter a 6-digit passcode" or "Re-enter to confirm"
                    Text(
                        text = state.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                // PIN indicators - positioned directly above keypad
                PinFieldLayout(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 40.dp),
                    state = state
                )

                WrapPinKeypad(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    onDigitPressed = { digit ->
                        val current = state.pin
                        val next = if (!state.quickPinError.isNullOrEmpty()) {
                            digit.toString()
                        } else {
                            (current + digit.toString())
                        }.take(state.quickPinSize)
                        onEventSend(Event.OnQuickPinEntered(next))
                    },
                    onBackspacePressed = {
                        val current = state.pin
                        val next = if (current.isNotEmpty()) current.dropLast(1) else current
                        onEventSend(Event.OnQuickPinEntered(next))
                    }
                )
            } // end main Column
        }

        // Logo overlay (not inside the scroll container, so it will not be clipped).
        AppIconAndText(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = paddingValues.calculateTopPadding() + 24.dp)
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
                        onClick = {
                            onEventSend(Event.OverflowMenuToggled(false))
                            onEventSend(Event.ForgotPinPressed)
                        }
                    )
                }
            }
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
    DialogBottomSheet(
        textData = BottomSheetTextDataUi(
            title = stringResource(id = R.string.quick_pin_reset_title),
            message = stringResource(id = R.string.quick_pin_reset_subtitle),
            positiveButtonText = stringResource(id = R.string.quick_pin_reset_primary_button),
            negativeButtonText = stringResource(id = R.string.quick_pin_reset_secondary_button),
        ),
        onPositiveClick = { onEventSent(Event.BottomSheet.Reset.ConfirmPressed) },
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
        filledCount = state.pin.length,
        hasError = !state.quickPinError.isNullOrEmpty(),
        errorMessage = state.quickPinError,
        circleSize = 16.dp,
        circleSpacing = 20.dp
    )
}

/**
 * Modern verify button with clean solid background and subtle press animation.
 * Designed to feel premium and distinctive without the divider line.
 */
@Composable
private fun ModernVerifyButton(
    modifier: Modifier = Modifier,
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Smooth scale animation on press
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "verifyButtonScale"
    )

    val backgroundColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onClick()
                }
            )
            .height(56.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            ),
            color = if (enabled) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@ThemeModePreviews
@Composable
private fun PinScreenEmptyPreview() {
    PreviewTheme {
        Content(
            state = State(
                pinFlow = PinFlow.CREATE,
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

@ThemeModePreviews
@Composable
private fun ModernVerifyButtonEnabledPreview() {
    PreviewTheme {
        ModernVerifyButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            text = "Verify",
            enabled = true,
            onClick = {}
        )
    }
}

@ThemeModePreviews
@Composable
private fun ModernVerifyButtonDisabledPreview() {
    PreviewTheme {
        ModernVerifyButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            text = "Verify",
            enabled = false,
            onClick = {}
        )
    }
}