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

package eu.europa.ec.uilogic.component.wrap

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.text.isDigitsOnly
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.EmptyTextToolbar
import eu.europa.ec.uilogic.component.utils.HSpacer
import eu.europa.ec.uilogic.component.utils.OneTimeLaunchedEffect
import eu.europa.ec.uilogic.component.utils.SIZE_SMALL
import androidx.compose.ui.platform.testTag
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.extension.optionalTestTag

@Composable
fun WrapPinTextField(
    modifier: Modifier = Modifier.fillMaxWidth(),
    displayCode: String? = null,
    controlledCode: String? = null,
    onPinUpdate: (code: String) -> Unit,
    length: Int,
    hasError: Boolean = false,
    errorMessage: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    pinWidth: Dp? = null,
    clearCode: Boolean = false,
    focusOnCreate: Boolean = false,
    shouldHideKeyboardOnCompletion: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
        disabledTextColor = MaterialTheme.colorScheme.onSurface,
        disabledContainerColor = Color.Transparent,
    )
) {

    fun List<FocusRequester>.requestFocus(index: Int) {
        this.elementAtOrNull(index)?.requestFocus()
    }

    // Text field range.
    val fieldsRange = 0 until length

    // Get keyboard controller.
    val keyboardController = LocalSoftwareKeyboardController.current

    // Get Focus Manager
    val focusManager = LocalFocusManager.current

    // Init list of all digits.
    val textFieldStateList = rememberSaveable {
        fieldsRange.map {
            mutableStateOf("")
        }
    }

    // Init focus requesters.
    val focusRequesters: List<FocusRequester> = remember {
        fieldsRange.map { FocusRequester() }
    }

    displayCode?.let { otpCode ->
        // Assign each charter from otpCode to the corresponding TextField
        textFieldStateList.forEachIndexed { index, mutableState ->
            mutableState.value = otpCode[index].toString()
        }
        onPinUpdate.invoke(otpCode)
    }

    controlledCode?.let { code ->
        textFieldStateList.forEachIndexed { index, mutableState ->
            mutableState.value = code.getOrNull(index)?.toString() ?: ""
        }
    }

    if (clearCode) {
        textFieldStateList.forEach {
            it.value = ""
            onPinUpdate.invoke("")
        }
        focusRequesters.requestFocus(0)
    }

    CompositionLocalProvider(
        LocalTextToolbar provides EmptyTextToolbar,
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = Color.Transparent,
            backgroundColor = Color.Transparent
        )
    ) {
        Column(modifier = modifier) {
            Row(
                modifier = Modifier.fillMaxWidth().wrapContentWidth(align = Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (currentTextField in fieldsRange) {
                    DisableSelection {
                        OutlinedTextField(
                            modifier = Modifier
                                .focusRequester(focusRequesters[currentTextField])
                                .then(pinWidth?.let { dp ->
                                    Modifier
                                        .width(dp)
                                        .padding(vertical = SPACING_SMALL.dp)
                                } ?: Modifier
                                    .weight(1f)
                                    .wrapContentSize())
                                .then(
                                    Modifier.onKeyEvent { keyEvent ->
                                        if (keyEvent.key == Key.Backspace) {
                                            if (textFieldStateList[currentTextField].value.isNotEmpty()) {
                                                textFieldStateList[currentTextField].value = ""
                                                // Notify listener.
                                                onPinUpdate.invoke(
                                                    textFieldStateList.joinToString(
                                                        separator = "",
                                                        transform = { textField ->
                                                            textField.value
                                                        }
                                                    )
                                                )
                                            }
                                            focusRequesters.requestFocus(currentTextField - 1)
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                ),
                            shape = RoundedCornerShape(SIZE_SMALL.dp),
                            value = textFieldStateList[currentTextField].value,
                            textStyle = LocalTextStyle.current.copy(
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            ),
                            colors = colors.copy(
                                cursorColor = Color.Transparent,
                                errorCursorColor = Color.Transparent
                            ),
                            visualTransformation = visualTransformation,
                            isError = hasError,
                            enabled = enabled,
                            readOnly = readOnly || controlledCode != null,
                            singleLine = true,
                            onValueChange = { newText: String ->
                                if (controlledCode != null) {
                                    return@OutlinedTextField
                                }

                                if (
                                    !newText.isDigitsOnly()
                                    || ((textFieldStateList.all { textField -> textField.value.isEmpty() }
                                            || textFieldStateList.all { textField -> textField.value.isNotEmpty() })
                                            && currentTextField == fieldsRange.last
                                            && newText.isNotEmpty())
                                ) {
                                    return@OutlinedTextField
                                }

                                if (newText != textFieldStateList[currentTextField].value) {
                                    textFieldStateList[currentTextField].value =
                                        newText.replaceFirst(
                                            textFieldStateList[currentTextField].value,
                                            ""
                                        )

                                    // Check if all fields are valid.
                                    if (
                                        !textFieldStateList.any { textField -> textField.value.isEmpty() }
                                        && shouldHideKeyboardOnCompletion
                                    ) {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                    } else if (currentTextField < fieldsRange.last && newText.isNotEmpty()) {
                                        focusRequesters.requestFocus(currentTextField + 1)
                                    }
                                    // Notify listener.
                                    onPinUpdate.invoke(
                                        textFieldStateList.joinToString(
                                            separator = "",
                                            transform = { textField ->
                                                textField.value
                                            }
                                        )
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = when (currentTextField < fieldsRange.last) {
                                    true -> ImeAction.Next
                                    false -> ImeAction.Done
                                }
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = {
                                    focusRequesters.requestFocus(currentTextField + 1)
                                }, onDone = {
                                    keyboardController?.hide()
                                }
                            )
                        )
                    }

                    if (currentTextField != fieldsRange.last) {
                        HSpacer.Small()
                    }
                }
            }
            errorMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            OneTimeLaunchedEffect {
                if (focusOnCreate) {
                    focusRequesters.requestFocus(0)
                }
            }
        }
    }
}

/**
 * Preview composable of [WrapPinTextField].
 */
@ThemeModePreviews
@Composable
private fun PreviewWrapPinTextField() {
    PreviewTheme {
        WrapPinTextField(
            modifier = Modifier.wrapContentSize(),
            onPinUpdate = {},
            length = 6,
            visualTransformation = PasswordVisualTransformation(),
            pinWidth = 42.dp,
        )
    }
}

@ThemeModePreviews
@Composable
private fun PreviewWrapPinTextFieldFilled() {
    PreviewTheme {
        WrapPinTextField(
            modifier = Modifier.wrapContentSize(),
            controlledCode = "123",
            onPinUpdate = {},
            length = 6,
            visualTransformation = PasswordVisualTransformation(),
            pinWidth = 42.dp,
        )
    }
}

@ThemeModePreviews
@Composable
private fun PreviewWrapPinTextFieldError() {
    PreviewTheme {
        WrapPinTextField(
            modifier = Modifier.wrapContentSize(),
            controlledCode = "123456",
            onPinUpdate = {},
            length = 6,
            hasError = true,
            errorMessage = "Incorrect PIN",
            visualTransformation = PasswordVisualTransformation(),
            pinWidth = 42.dp,
        )
    }
}

/**
 * Modern circle-based PIN indicator component.
 * Shows empty circles for unfilled positions and filled circles for entered digits.
 * Includes smooth scale animations when digits are entered.
 */
@Composable
fun PinIndicator(
    modifier: Modifier = Modifier,
    pinLength: Int,
    filledCount: Int,
    hasError: Boolean = false,
    hasSuccess: Boolean = false,
    errorMessage: String? = null,
    errorMessageTestTag: String? = null,
    indicatorTestTagProvider: ((Int) -> String)? = null,
    circleSize: Dp = 16.dp,
    circleSpacing: Dp = 16.dp,
    filledColor: Color = MaterialTheme.colorScheme.primary,
    emptyColor: Color = MaterialTheme.colorScheme.outlineVariant,
    errorColor: Color = MaterialTheme.colorScheme.error,
    successColor: Color = Color(0xFF4CAF50),
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .wrapContentWidth()
                .height(circleSize),
            horizontalArrangement = Arrangement.spacedBy(circleSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (index in 0 until pinLength) {
                val isFilled = index < filledCount

                // Animate scale: pop on fill, extra pop on success/error feedback.
                val scale by animateFloatAsState(
                    targetValue = when {
                        hasSuccess || hasError -> 1.15f
                        isFilled -> 1f
                        else -> 0.85f
                    },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "pinIndicatorScale"
                )

                // Target color: success > error > filled > empty.
                val targetColor = when {
                    hasSuccess -> successColor
                    hasError -> errorColor
                    isFilled -> filledColor
                    else -> emptyColor
                }

                // Smooth color transition (150ms for snappy feedback).
                val animatedColor by animateColorAsState(
                    targetValue = targetColor,
                    animationSpec = tween(durationMillis = 150),
                    label = "pinIndicatorColor"
                )

                Box(
                    modifier = Modifier
                        .size(circleSize)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .then(
                            if (isFilled || hasSuccess || hasError) {
                                Modifier.background(animatedColor, CircleShape)
                            } else {
                                Modifier.background(
                                    color = animatedColor.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                            }
                        )
                        .testTag(indicatorTestTagProvider?.invoke(index) ?: "pin_indicator_$index")
                )
            }
        }

        AnimatedVisibility(
            visible = !errorMessage.isNullOrEmpty(),
            enter = fadeIn(animationSpec = tween(200)) + expandVertically(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)) + shrinkVertically(animationSpec = tween(200))
        ) {
            errorMessage?.let {
                Text(
                    modifier = Modifier
                        .padding(top = SPACING_SMALL.dp)
                        .optionalTestTag(errorMessageTestTag),
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@ThemeModePreviews
@Composable
private fun PreviewPinIndicatorEmpty() {
    PreviewTheme {
        PinIndicator(
            pinLength = 6,
            filledCount = 0
        )
    }
}

@ThemeModePreviews
@Composable
private fun PreviewPinIndicatorPartiallyFilled() {
    PreviewTheme {
        PinIndicator(
            pinLength = 6,
            filledCount = 3
        )
    }
}

@ThemeModePreviews
@Composable
private fun PreviewPinIndicatorFilled() {
    PreviewTheme {
        PinIndicator(
            pinLength = 6,
            filledCount = 6
        )
    }
}

@ThemeModePreviews
@Composable
private fun PreviewPinIndicatorSuccess() {
    PreviewTheme {
        PinIndicator(
            pinLength = 6,
            filledCount = 6,
            hasSuccess = true
        )
    }
}

@ThemeModePreviews
@Composable
private fun PreviewPinIndicatorError() {
    PreviewTheme {
        PinIndicator(
            pinLength = 6,
            filledCount = 6,
            hasError = true,
            errorMessage = "Incorrect PIN"
        )
    }
}
