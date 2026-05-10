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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.divider
import eu.europa.ec.resourceslogic.theme.values.warning
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconDataUi
import eu.europa.ec.uilogic.component.ModalOptionUi
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.ALPHA_DISABLED
import eu.europa.ec.uilogic.component.utils.ALPHA_ENABLED
import eu.europa.ec.uilogic.component.utils.DEFAULT_ICON_SIZE
import eu.europa.ec.uilogic.component.utils.HSpacer
import eu.europa.ec.uilogic.component.utils.SIZE_SMALL
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_SMALL
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.extension.exposeTestTagsAsResourceId
import eu.europa.ec.uilogic.extension.optionalTestTag
import eu.europa.ec.uilogic.extension.throttledClickable
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.util.TestTag

private val defaultBottomSheetPadding: PaddingValues = PaddingValues(
    start = SPACING_LARGE.dp,
    end = SPACING_LARGE.dp,
    top = 0.dp,
    bottom = SPACING_LARGE.dp
)

private val bottomSheetDefaultBackgroundColor: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerLowest

private val bottomSheetDefaultTextColor: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface

/**
 * Data class representing the text content for a bottom sheet.
 *
 * This class holds the title, message, and button texts for a bottom sheet.
 * It also includes flags to indicate if a button should be styled as a warning.
 *
 * @property title The title of the bottom sheet.
 * @property message The message displayed in the bottom sheet.
 * @property positiveButtonText The text for the positive button (e.g., "OK", "Confirm"). Can be null if no positive button is needed.
 * @property isPositiveButtonWarning A flag indicating if the positive button should be styled as a warning (e.g., red color). Defaults to false.
 * @property negativeButtonText The text for the negative button (e.g., "Cancel", "Dismiss"). Can be null if no negative button is needed.
 * @property isNegativeButtonWarning A flag indicating if the negative button should be styled as a warning (e.g., red color). Defaults to false.
 */
data class BottomSheetTextDataUi(
    val title: String,
    val message: String,
    val positiveButtonText: String? = null,
    val isPositiveButtonWarning: Boolean = false,
    val negativeButtonText: String? = null,
    val isNegativeButtonWarning: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WrapModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState,
    shape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    dragHandle: @Composable (() -> Unit) = { BottomSheetDefaultHandle() },
    sheetContent: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .exposeTestTagsAsResourceId()
            .then(modifier),
        sheetState = sheetState,
        shape = shape,
        dragHandle = dragHandle,
        content = sheetContent,
    )
}

/**
 * A generic composable function for creating a bottom sheet.
 *
 * This function provides a basic structure for a bottom sheet, including a title and body section.
 * You can customize the content of the title and body by providing composable functions.
 *
 * The bottom sheet is displayed with a default background color and padding.
 *
 * @param titleContent A composable function that provides the content for the title section of the bottom sheet.
 * This content is displayed at the top of the bottom sheet.
 * @param bodyContent A composable function that provides the content for the body section of the bottom sheet.
 * This content is displayed below the title, separated by a medium vertical spacer.
 */
@Composable
fun GenericBottomSheet(
    titleContent: @Composable () -> Unit,
    bodyContent: @Composable () -> Unit,
    sheetBackgroundColor: Color = bottomSheetDefaultBackgroundColor,
    sheetPadding: PaddingValues = defaultBottomSheetPadding,
) {
    Column(
        modifier = Modifier
            .wrapContentHeight()
            .background(color = sheetBackgroundColor)
            .fillMaxWidth()
            .padding(sheetPadding)
    ) {
        titleContent()
        VSpacer.Medium()
        bodyContent()
    }
}

/**
 * A composable function that displays a dialog-style bottom sheet.
 *
 * This bottom sheet presents information to the user with optional icons,
 * title, message, and two buttons for positive and negative actions.
 *
 * @param textData Data class containing the text content for the bottom sheet. This includes
 *                 title, message, positive button text, and negative button text.
 * @param leadingIcon An optional icon to be displayed at the beginning of the title.
 * @param leadingIconTint An optional tint color for the leading icon.
 * @param onPositiveClick A lambda function to be executed when the positive button is clicked.
 * @param onNegativeClick A lambda function to be executed when the negative button is clicked.
 */
@Composable
fun DialogBottomSheet(
    textData: BottomSheetTextDataUi,
    leadingIcon: IconDataUi? = null,
    leadingIconTint: Color? = null,
    onPositiveClick: () -> Unit = {},
    positiveButtonTestTag: String? = null,
    onNegativeClick: () -> Unit = {},
    negativeButtonTestTag: String? = null,
) {
    BaseBottomSheet(
        textData = textData,
        leadingIcon = leadingIcon,
        leadingIconTint = leadingIconTint,
        bodyContent = {
            Row(
                modifier = Modifier.padding(vertical = SPACING_EXTRA_SMALL.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                textData.negativeButtonText?.let { safeNegativeButtonText ->
                    WrapButton(
                        modifier = Modifier
                            .optionalTestTag(negativeButtonTestTag)
                            .weight(1f),
                        buttonConfig = ButtonConfig(
                            type = ButtonType.SECONDARY,
                            onClick = onNegativeClick,
                            isWarning = textData.isNegativeButtonWarning,
                        )
                    ) {
                        Text(
                            text = safeNegativeButtonText,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                HSpacer.Small()

                textData.positiveButtonText?.let { safePositiveButtonText ->
                    WrapButton(
                        modifier = Modifier
                            .optionalTestTag(positiveButtonTestTag)
                            .weight(1f),
                        buttonConfig = ButtonConfig(
                            type = ButtonType.PRIMARY,
                            onClick = onPositiveClick,
                            isWarning = textData.isPositiveButtonWarning,
                        )
                    ) {
                        Text(
                            text = safePositiveButtonText,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    )
}

/**
 * A simple bottom sheet composable function.
 *
 * This function displays a basic bottom sheet with a title and message.
 * It can optionally include a leading icon with a custom tint.
 * It utilizes the `BaseBottomSheet` composable for its core functionality, providing a
 * standardized structure for bottom sheets.
 *
 * @param textData An object of type `BottomSheetTextData` containing the title and message
 * to be displayed in the bottom sheet.
 * @param leadingIcon An optional `IconData` object representing the icon to be displayed
 * at the leading edge of the bottom sheet.
 * @param leadingIconTint An optional `Color` to apply as a tint to the leading icon. If null,
 * the default icon color will be used.
 */
@Composable
fun SimpleBottomSheet(
    textData: BottomSheetTextDataUi,
    leadingIcon: IconDataUi? = null,
    leadingIconTint: Color? = null,
) {
    BaseBottomSheet(
        textData = textData,
        leadingIcon = leadingIcon,
        leadingIconTint = leadingIconTint,
    )
}

@Composable
private fun BaseBottomSheet(
    textData: BottomSheetTextDataUi,
    leadingIcon: IconDataUi? = null,
    leadingIconTint: Color? = null,
    bodyContent: @Composable (() -> Unit)? = null,
    sheetBackgroundColor: Color = bottomSheetDefaultBackgroundColor,
    sheetPadding: PaddingValues = defaultBottomSheetPadding,
) {
    Column(
        modifier = Modifier
            .wrapContentHeight()
            .background(color = sheetBackgroundColor)
            .fillMaxWidth()
            .padding(sheetPadding),
        verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp, Alignment.Start),
                verticalAlignment = Alignment.CenterVertically
            ) {
                leadingIcon?.let { safeLeadingIcon ->
                    WrapIcon(
                        modifier = Modifier.size(DEFAULT_ICON_SIZE.dp),
                        iconData = safeLeadingIcon,
                        customTint = leadingIconTint
                    )
                }
                Text(
                    text = textData.title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color = bottomSheetDefaultTextColor,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.3).sp,
                    )
                )
            }

            Text(
                text = textData.message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = bottomSheetDefaultTextColor.copy(alpha = 0.6f),
                    lineHeight = 20.sp,
                )
            )
        }

        bodyContent?.let { safeBodyContent ->
            safeBodyContent()
        }
    }
}

@Composable
fun <T : ViewEvent> BottomSheetWithTwoBigIcons(
    textData: BottomSheetTextDataUi,
    options: List<ModalOptionUi<T>>,
    onEventSent: (T) -> Unit,
    hostTab: String? = null,
) {
    if (options.isNotEmpty()) {
        BaseBottomSheet(
            textData = textData,
            sheetPadding = defaultBottomSheetPadding,
            bodyContent = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    options.chunked(2).forEachIndexed { rowIndex, rowOptions ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            rowOptions.forEachIndexed { columnIndex, item ->
                                val optionIndex = rowIndex * 2 + columnIndex
                                BottomSheetOptionCard(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .optionalTestTag(
                                            hostTab?.let { safeHostTab ->
                                                TestTag.buttonInBottomSheetWithTwoBigIcons(
                                                    hostTab = safeHostTab,
                                                    index = optionIndex
                                                )
                                            }
                                        ),
                                    title = item.title,
                                    icon = item.leadingIcon,
                                    iconTint = item.leadingIconTint,
                                    accentColor = item.accentColor,
                                    enabled = item.enabled,
                                    onClick = { onEventSent(item.event) }
                                )
                            }
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun BottomSheetOptionCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: IconDataUi? = null,
    iconTint: Color? = null,
    accentColor: Color? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "cardScale"
    )
    val contentAlpha = if (enabled) ALPHA_ENABLED else ALPHA_DISABLED
    val resolvedAccent = accentColor ?: MaterialTheme.colorScheme.tertiary
    val cardBackground = MaterialTheme.colorScheme.surfaceContainerHigh
    val view = LocalView.current

    Box(
        modifier = modifier
            .scale(scale)
            .alpha(contentAlpha)
            .clip(RoundedCornerShape(20.dp))
            .background(cardBackground)
            .throttledClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
            ) {
                @Suppress("DEPRECATION")
                view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                onClick()
            }
            .padding(SPACING_MEDIUM.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Accent line at the top
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                resolvedAccent.copy(alpha = 0.6f),
                                resolvedAccent,
                                resolvedAccent.copy(alpha = 0.6f),
                            )
                        )
                    )
            )

            // Icon in a glowing circle
            icon?.let { safeIcon ->
                Box(
                    contentAlignment = Alignment.Center,
                ) {
                    // Outer glow ring
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(resolvedAccent.copy(alpha = 0.08f))
                    )
                    // Inner icon circle
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        resolvedAccent.copy(alpha = 0.18f),
                                        resolvedAccent.copy(alpha = 0.06f),
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        WrapIcon(
                            iconData = safeIcon,
                            customTint = iconTint ?: resolvedAccent,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // Title
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 18.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // Arrow chip
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(resolvedAccent.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                WrapIcon(
                    iconData = AppIcons.KeyboardArrowRight,
                    customTint = resolvedAccent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun <T : ViewEvent> BottomSheetWithOptionsList(
    textData: BottomSheetTextDataUi,
    options: List<ModalOptionUi<T>>,
    onEventSent: (T) -> Unit,
) {
    if (options.isNotEmpty()) {
        BaseBottomSheet(
            textData = textData,
            bodyContent = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.Start
                ) {
                    OptionsList(
                        optionItems = options,
                        itemSelected = onEventSent
                    )
                }
            }
        )
    }
}

@Composable
private fun <T : ViewEvent> OptionsList(
    optionItems: List<ModalOptionUi<T>>,
    itemSelected: (T) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)
    ) {
        itemsIndexed(optionItems) { index, item ->

            OptionListItem(
                item = item,
                itemSelected = itemSelected
            )

            if (index < optionItems.lastIndex) {
                HorizontalDivider(
                    thickness = 1.dp,
                )
            }
        }
    }
}

@Composable
private fun <T : ViewEvent> OptionListItem(
    item: ModalOptionUi<T>,
    itemSelected: (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SIZE_SMALL.dp))
            .background(bottomSheetDefaultBackgroundColor)
            .throttledClickable {
                itemSelected(item.event)
            }
            .padding(
                vertical = SPACING_MEDIUM.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp, Alignment.Start),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item.leadingIcon?.let { safeLeadingIcon ->
            WrapIcon(
                modifier = Modifier.size(DEFAULT_ICON_SIZE.dp),
                iconData = safeLeadingIcon,
                customTint = item.leadingIconTint,
            )
        }

        Text(
            modifier = Modifier.weight(1f),
            text = item.title,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = bottomSheetDefaultTextColor
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        item.trailingIcon?.let { safeTrailingIcon ->
            WrapIcon(
                modifier = Modifier.size(DEFAULT_ICON_SIZE.dp),
                iconData = safeTrailingIcon,
                customTint = item.trailingIconTint,
            )
        }
    }
}

@Composable
private fun BottomSheetDefaultHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bottomSheetDefaultBackgroundColor)
            .padding(top = 12.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
        )
    }
}

@ThemeModePreviews
@Composable
private fun BottomSheetDefaultHandlePreview() {
    PreviewTheme {
        BottomSheetDefaultHandle()
    }
}

@ThemeModePreviews
@Composable
private fun SimpleBottomSheetPreview() {
    PreviewTheme {
        SimpleBottomSheet(
            textData = BottomSheetTextDataUi(
                title = "Title",
                message = "Message",
            )
        )
    }
}

@ThemeModePreviews
@Composable
private fun SimpleBottomSheetWithLeadingIconPreview() {
    PreviewTheme {
        SimpleBottomSheet(
            textData = BottomSheetTextDataUi(
                title = "Title",
                message = "Message",
            ),
            leadingIcon = AppIcons.Warning,
            leadingIconTint = MaterialTheme.colorScheme.warning,
        )
    }
}

@ThemeModePreviews
@Composable
private fun DialogBottomSheetPreview() {
    PreviewTheme {
        DialogBottomSheet(
            textData = BottomSheetTextDataUi(
                title = "Title",
                message = "Message",
                positiveButtonText = "OK",
                negativeButtonText = "Cancel"
            )
        )
    }
}

private data object DummyEventForPreview : ViewEvent

@ThemeModePreviews
@Composable
private fun BottomSheetWithOptionsListPreview() {
    PreviewTheme {
        BottomSheetWithOptionsList(
            textData = BottomSheetTextDataUi(
                title = "Title",
                message = "Message"
            ),
            options = buildList {
                addAll(
                    listOf(
                        ModalOptionUi(
                            title = "Option with no icons",
                            event = DummyEventForPreview,
                        ),
                        ModalOptionUi(
                            title = "Option with leading icon",
                            leadingIcon = AppIcons.Verified,
                            leadingIconTint = MaterialTheme.colorScheme.primary,
                            event = DummyEventForPreview,
                        ),
                        ModalOptionUi(
                            title = "Option with leading icon",
                            trailingIcon = AppIcons.Edit,
                            trailingIconTint = MaterialTheme.colorScheme.primary,
                            event = DummyEventForPreview,
                        ),
                        ModalOptionUi(
                            title = "Option with leading and trailing icon",
                            leadingIcon = AppIcons.Add,
                            leadingIconTint = MaterialTheme.colorScheme.primary,
                            trailingIcon = AppIcons.ClockTimer,
                            trailingIconTint = MaterialTheme.colorScheme.primary,
                            event = DummyEventForPreview,
                        ),
                        ModalOptionUi(
                            title = "Option with leading and trailing icon and really really really really really long text",
                            leadingIcon = AppIcons.Add,
                            leadingIconTint = MaterialTheme.colorScheme.primary,
                            trailingIcon = AppIcons.ClockTimer,
                            trailingIconTint = MaterialTheme.colorScheme.primary,
                            event = DummyEventForPreview,
                        ),
                    )
                )
            },
            onEventSent = {}
        )
    }
}

@ThemeModePreviews
@Composable
private fun BottomSheetWithTwoBigIconsEvenTextPreview() {
    PreviewTheme {
        BottomSheetWithTwoBigIcons(
            textData = BottomSheetTextDataUi(
                title = "Authenticate",
                message = "Choose how you want to authenticate"
            ),
            options = listOf(
                ModalOptionUi(
                    title = "In Person",
                    leadingIcon = AppIcons.PresentDocumentInPerson,
                    accentColor = Color(0xFF3B82F6),
                    event = DummyEventForPreview,
                    enabled = true,
                ),
                ModalOptionUi(
                    title = "Online",
                    leadingIcon = AppIcons.PresentDocumentOnline,
                    accentColor = Color(0xFF7C3AED),
                    event = DummyEventForPreview,
                    enabled = false,
                ),
            ),
            onEventSent = {}
        )
    }
}

@ThemeModePreviews
@Composable
private fun BottomSheetWithTwoBigIconsUnevenTextPreview() {
    PreviewTheme {
        BottomSheetWithTwoBigIcons(
            textData = BottomSheetTextDataUi(
                title = "Sign a document",
                message = "Choose where your document is located"
            ),
            options = listOf(
                ModalOptionUi(
                    title = "From Device",
                    leadingIcon = AppIcons.PresentDocumentInPerson,
                    accentColor = Color(0xFF059669),
                    event = DummyEventForPreview,
                    enabled = true,
                ),
                ModalOptionUi(
                    title = "Scan QR",
                    leadingIcon = AppIcons.PresentDocumentOnline,
                    accentColor = Color(0xFFD97706),
                    event = DummyEventForPreview,
                    enabled = true,
                ),
            ),
            onEventSent = {}
        )
    }
}

@ThemeModePreviews
@Composable
private fun BottomSheetWithThreeBigIconsPreview() {
    PreviewTheme {
        BottomSheetWithTwoBigIcons(
            textData = BottomSheetTextDataUi(
                title = "Add credential",
                message = "Choose how to add a credential"
            ),
            options = listOf(
                ModalOptionUi(
                    title = "From list",
                    leadingIcon = AppIcons.AddDocumentFromList,
                    accentColor = Color(0xFFD97706),
                    event = DummyEventForPreview,
                    enabled = true,
                ),
                ModalOptionUi(
                    title = "Scan QR",
                    leadingIcon = AppIcons.AddDocumentFromQr,
                    accentColor = Color(0xFFF59E0B),
                    event = DummyEventForPreview,
                    enabled = true,
                ),
                ModalOptionUi(
                    title = "Get Authbound ID",
                    leadingIcon = AppIcons.Verified,
                    accentColor = Color(0xFF047857),
                    event = DummyEventForPreview,
                    enabled = true,
                ),
            ),
            onEventSent = {}
        )
    }
}
