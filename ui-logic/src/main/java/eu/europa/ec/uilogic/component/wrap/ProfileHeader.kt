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

package eu.europa.ec.uilogic.component.wrap

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ImageOrPlaceholder
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import androidx.compose.ui.unit.sp
import eu.europa.ec.resourceslogic.R

/**
 * Configuration data for the profile header.
 */
data class ProfileHeaderConfig(
    val displayName: String,
    val email: String?,
    val avatarUrl: String? = null,
    val portraitBase64: String? = null,
    val onEditClick: (() -> Unit)? = null
)

/**
 * A premium profile header for the navigation drawer/hamburger menu.
 * Features a gradient background, avatar with initials fallback, and edit button.
 */
@Composable
fun ProfileHeader(
    config: ProfileHeaderConfig,
    modifier: Modifier = Modifier,
    onProfileClick: (() -> Unit)? = null
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "profile_header_scale"
    )

    // Navy gradient background
    val gradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF0A1A36), // Navy Primary
            Color(0xFF1E3A5F)  // Navy Medium
        )
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(gradient)
            .then(
                if (onProfileClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = ripple(bounded = true, color = Color.White),
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onProfileClick()
                        }
                    )
                } else {
                    Modifier
                }
            )
            .padding(24.dp)
            .padding(top = 24.dp) // Extra top padding for close button space
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with initials
            ProfileAvatar(
                displayName = config.displayName,
                avatarUrl = config.avatarUrl,
                portraitBase64 = config.portraitBase64
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Name and email
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = config.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!config.email.isNullOrBlank()) {
                    Text(
                        text = config.email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Edit Profile link
                if (config.onEditClick != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    EditProfileButton(onClick = config.onEditClick)
                }
            }
        }
    }
}

/**
 * Avatar component with initials fallback.
 */
@Composable
fun ProfileAvatar(
    displayName: String,
    avatarUrl: String?,
    portraitBase64: String?,
    modifier: Modifier = Modifier,
    size: Int = 72
) {
    // Get initials from display name
    val initials = remember(displayName) {
        displayName.split(" ")
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifEmpty { displayName.take(1).uppercase() }
    }

    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        val safePortraitBase64: String = portraitBase64.orEmpty()

        // PID portrait if available; otherwise fall back to the app's existing mascot placeholder.
        if (safePortraitBase64.isNotBlank()) {
            ImageOrPlaceholder(
                modifier = Modifier
                    .size((size - 8).dp)
                    .clip(CircleShape),
                base64Image = safePortraitBase64,
            )
        } else {
            Box(
                modifier = Modifier
                    .size((size - 8).dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3B82F6)), // Blue Accent
            ) {
                Image(
                    painter = painterResource(id = R.drawable.authbound_avatar_placeholder_mascot),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

/**
 * Edit profile ghost button with underline.
 */
@Composable
private fun EditProfileButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        color = Color.White.copy(alpha = 0.15f),
        shape = RoundedCornerShape(100.dp),
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onClick()
                }
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            WrapIcon(
                iconData = AppIcons.Edit,
                customTint = Color.White,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = "Edit Profile",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Section header for menu groups.
 */
@Composable
fun MenuSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.5.sp,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

/**
 * A grouped menu item card for navigation drawer actions.
 */
@Composable
fun MenuItemCard(
    title: String,
    icon: eu.europa.ec.uilogic.component.IconDataUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    badge: String? = null,
    showDivider: Boolean = true
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.99f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "menu_item_scale"
    )

    Column(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true),
                    enabled = isEnabled,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onClick()
                    }
                ),
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
                WrapIcon(
                    iconData = icon,
                    customTint = if (isEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isEnabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                    modifier = Modifier.weight(1f)
                )

                // Badge or chevron
                if (badge != null) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    WrapIcon(
                        iconData = AppIcons.KeyboardArrowRight,
                        customTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            )
        }
    }
}

/**
 * Footer for the navigation drawer with version and copyright.
 */
@Composable
fun MenuFooter(
    appVersion: String,
    copyrightText: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "App Version $appVersion",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Text(
            text = copyrightText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@ThemeModePreviews
@Composable
private fun ProfileHeaderPreview() {
    PreviewTheme {
        Column {
            ProfileHeader(
                config = ProfileHeaderConfig(
                    displayName = "John Doe",
                    email = "john.doe@email.com",
                    onEditClick = {}
                ),
                onProfileClick = {}
            )

            Spacer(modifier = Modifier.height(24.dp))

            MenuSectionHeader(title = "Quick Actions")

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Column {
                    MenuItemCard(
                        title = "Authenticate",
                        icon = AppIcons.Key,
                        onClick = {},
                        showDivider = true
                    )
                    MenuItemCard(
                        title = "Add Document",
                        icon = AppIcons.Add,
                        onClick = {},
                        showDivider = true
                    )
                    MenuItemCard(
                        title = "Sign Document",
                        icon = AppIcons.Sign,
                        onClick = {},
                        badge = "Coming",
                        isEnabled = false,
                        showDivider = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            MenuFooter(
                appVersion = "1.0.0",
                copyrightText = "2025 Authbound"
            )
        }
    }
}
