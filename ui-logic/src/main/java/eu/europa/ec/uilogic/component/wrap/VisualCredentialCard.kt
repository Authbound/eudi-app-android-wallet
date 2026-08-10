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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.brandNavyDeep
import eu.europa.ec.resourceslogic.theme.values.brandNavyMedium
import eu.europa.ec.resourceslogic.theme.values.success
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ImageOrPlaceholder
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import java.util.Locale
import kotlinx.coroutines.delay

/** Visual type for credential cards - determines styling. */
enum class CredentialVisualType {
        /** Personal Identification Data - EU Blue/Gold */
        PID,
        /** Mobile Driving License - Green */
        MDL,
        /** Education/Diploma credentials - Purple */
        DIPLOMA,
        /** Health-related credentials - Red */
        HEALTH,
        /** Generic/Other credentials - Gray */
        GENERIC,
        /** Authbound-issued identity credentials - Navy/Blue */
        AUTHBOUND
}

/** Status of a credential. */
enum class CredentialStatus {
        ISSUED,
        PENDING,
        EXPIRED,
        REVOKED
}

/** Layout variant for credential cards. */
enum class CredentialCardLayout {
        /** Standard wallet-card layout used across document lists. */
        COMPACT,
        /** Passport-style layout: portrait frame and identity fields for hero credentials. */
        PASSPORT
}

/**
 * How many identity fields the passport face shows.
 * [FULL] is for home hero; [SUMMARY] is for document details (facts live in ClaimPropertyList).
 */
enum class PassportFieldDensity {
        FULL,
        SUMMARY
}

/** Configuration for visual credential card. */
data class VisualCredentialConfig(
        val id: String,
        val visualType: CredentialVisualType,
        val title: String,
        val subtitle: String?,
        val holderName: String?,
        val issuerName: String?,
        val primaryField: String?,
        val secondaryField: String?,
        val status: CredentialStatus,
        val expiryDate: String?,
        val hasPhoto: Boolean = false,
        val portraitBase64: String? = null,
        val nationality: String? = null,
        val birthDate: String? = null,
        val layout: CredentialCardLayout = CredentialCardLayout.COMPACT,
        val passportFieldDensity: PassportFieldDensity = PassportFieldDensity.FULL
)

/** Color scheme for credential types. */
data class CredentialColorScheme(
        val gradientStart: Color,
        val gradientEnd: Color,
        val accent: Color,
        val textPrimary: Color,
        val textSecondary: Color
)

/**
 * Get color scheme for credential type. Updated to use Authbound brand colors for premium, cohesive
 * look.
 *
 * Brand Palette:
 * - Navy Deep: #0A1A36 (Primary backgrounds)
 * - Navy Medium: #1E3A5F (Gradient endpoints)
 * - Navy Light: #2A4A6F (Tertiary backgrounds)
 * - Blue Accent: #3B82F6 (Primary interactive elements)
 * - Teal Accent: #2A8A9A (Secondary highlights)
 */
@Composable
private fun getCredentialColors(type: CredentialVisualType): CredentialColorScheme {
        val navyDeep = MaterialTheme.colorScheme.brandNavyDeep
        val navyMedium = MaterialTheme.colorScheme.brandNavyMedium
        return when (type) {
                // PID: Premium navy with gold accent (EU identity + Authbound brand)
                CredentialVisualType.PID ->
                        CredentialColorScheme(
                                gradientStart = navyDeep,
                                gradientEnd = navyMedium,
                                accent = Color(0xFFD4A84B), // Refined gold (premium feel)
                                textPrimary = Color.White,
                                textSecondary = Color.White.copy(alpha = 0.75f)
                        )
                // MDL: Navy base with teal accent (driving = go = teal/green undertone)
                CredentialVisualType.MDL ->
                        CredentialColorScheme(
                                gradientStart = navyDeep,
                                gradientEnd = Color(0xFF14294F), // Navy variant
                                accent = Color(0xFF2A8A9A), // Teal accent (brand)
                                textPrimary = Color.White,
                                textSecondary = Color.White.copy(alpha = 0.75f)
                        )
                // DIPLOMA: Navy with purple accent (education = wisdom)
                CredentialVisualType.DIPLOMA ->
                        CredentialColorScheme(
                                gradientStart = Color(0xFF0F1A2E), // Deep navy-purple
                                gradientEnd = Color(0xFF1A2847), // Medium navy-purple
                                accent = Color(0xFF8B5CF6), // Violet accent
                                textPrimary = Color.White,
                                textSecondary = Color.White.copy(alpha = 0.75f)
                        )
                // HEALTH: Navy with rose accent (medical cross feel)
                CredentialVisualType.HEALTH ->
                        CredentialColorScheme(
                                gradientStart = Color(0xFF1A1520), // Deep navy-rose
                                gradientEnd = Color(0xFF2A2035), // Medium navy-rose
                                accent = Color(0xFFF472B6), // Rose accent
                                textPrimary = Color.White,
                                textSecondary = Color.White.copy(alpha = 0.75f)
                        )
                // GENERIC: Standard navy with blue accent
                CredentialVisualType.GENERIC ->
                        CredentialColorScheme(
                                gradientStart = navyDeep,
                                gradientEnd = navyMedium,
                                accent = Color(0xFF3B82F6), // Blue accent (brand)
                                textPrimary = Color.White,
                                textSecondary = Color.White.copy(alpha = 0.75f)
                        )
                // AUTHBOUND: Navy base with Authbound brand blue accent (distinct from gold PID)
                CredentialVisualType.AUTHBOUND ->
                        CredentialColorScheme(
                                gradientStart = navyDeep,
                                gradientEnd = Color(0xFF1A3060), // Slightly deeper navy
                                accent = Color(0xFF3B82F6), // Authbound blue accent
                                textPrimary = Color.White,
                                textSecondary = Color.White.copy(alpha = 0.75f)
                        )
        }
}

/**
 * A premium visual credential card inspired by Apple Wallet. Displays documents with beautiful
 * gradients and visual hierarchy.
 */
@Composable
fun VisualCredentialCard(
        config: VisualCredentialConfig,
        modifier: Modifier = Modifier,
        animationDelay: Int = 0,
        enableAnimations: Boolean = true,
        showAuthboundBadge: Boolean = false,
        onClick: (() -> Unit)? = {}
) {
        val view = LocalView.current
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val colors = getCredentialColors(config.visualType)
        val shouldShowPhoto: Boolean = config.hasPhoto || !config.portraitBase64.isNullOrBlank()
        val scale: Float =
                if (enableAnimations) {
                        val animatedScale by
                                animateFloatAsState(
                                        targetValue = if (isPressed) 0.97f else 1f,
                                        animationSpec = tween(durationMillis = 100),
                                        label = "credential_card_scale"
                                )
                        animatedScale
                } else {
                        1f
                }
        val cardShape = RoundedCornerShape(20.dp)
        val content: @Composable () -> Unit = {
                Surface(
                        modifier =
                                modifier.fillMaxWidth()
                                        .heightIn(min = 200.dp) // Minimum height for breathing room
                                        .border(
                                                width = 1.dp,
                                                color = Color.White.copy(alpha = 0.1f),
                                                shape = cardShape
                                        )
                                        .scale(scale)
                                        .then(
                                                if (onClick != null) {
                                                        Modifier.clickable(
                                                                interactionSource = interactionSource,
                                                                indication = ripple(bounded = true),
                                                                onClick = {
                                                                        view.performHapticFeedback(
                                                                                HapticFeedbackConstants.VIRTUAL_KEY
                                                                        )
                                                                        onClick()
                                                                }
                                                        )
                                                } else {
                                                        Modifier
                                                }
                                        ),
                        shape = cardShape,
                        shadowElevation = 12.dp
                ) {
                        Box(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .background(
                                                        // Diagonal gradient (135°) for premium look
                                                        Brush.linearGradient(
                                                                colors =
                                                                        listOf(
                                                                                colors.gradientStart,
                                                                                colors.gradientEnd
                                                                        ),
                                                                start = Offset(0f, 0f),
                                                                end =
                                                                        Offset(
                                                                                Float.POSITIVE_INFINITY,
                                                                                Float.POSITIVE_INFINITY
                                                                        )
                                                        )
                                                )
                        ) {
                                // Security pattern overlay (subtle guilloche-like effect)
                                CredentialSecurityPattern(
                                        modifier = Modifier.fillMaxSize(),
                                        color = colors.accent
                                )

                                if (config.layout == CredentialCardLayout.PASSPORT) {
                                        PassportCardContent(config = config, colors = colors)
                                } else {

                                // Decorative circles (Authbound brand motif)
                                CredentialDecorativeCircles(
                                        modifier = Modifier.align(Alignment.TopEnd),
                                        color = colors.accent
                                )

                                // Main content
                                Column(
                                        modifier =
                                                Modifier.fillMaxSize()
                                                        .padding(
                                                                24.dp
                                                        ), // Increased padding for better breathing
                                        // room
                                        verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                        // Top section: Type badge + Status
                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.Top
                                        ) {
                                                // Type badge with EU flag for PID
                                                Row(
                                                        verticalAlignment =
                                                                Alignment.CenterVertically,
                                                        horizontalArrangement =
                                                                Arrangement.spacedBy(8.dp)
                                                ) {
                                                        if (config.visualType ==
                                                                        CredentialVisualType.PID
                                                        ) {
                                                                // Premium EU badge with gold border
                                                                Box(
                                                                        modifier =
                                                                                Modifier.size(28.dp)
                                                                                        .clip(
                                                                                                CircleShape
                                                                                        )
                                                                                        .background(
                                                                                                colors.accent
                                                                                        )
                                                                                        .border(
                                                                                                width =
                                                                                                        1.5.dp,
                                                                                                color =
                                                                                                        colors.accent
                                                                                                                .copy(
                                                                                                                        alpha =
                                                                                                                                0.5f
                                                                                                                ),
                                                                                                shape =
                                                                                                        CircleShape
                                                                                        ),
                                                                        contentAlignment =
                                                                                Alignment.Center
                                                                ) {
                                                                        Text(
                                                                                text = "EU",
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .labelSmall,
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Black,
                                                                                color =
                                                                                        colors.gradientStart,
                                                                                fontSize = 10.sp
                                                                        )
                                                                }
                                                        }

                                                        if (config.visualType ==
                                                                        CredentialVisualType.AUTHBOUND
                                                        ) {
                                                                // Authbound brand badge
                                                                Box(
                                                                        modifier =
                                                                                Modifier.size(28.dp)
                                                                                        .clip(CircleShape)
                                                                                        .background(
                                                                                                colors.accent.copy(alpha = 0.15f)
                                                                                        )
                                                                                        .border(
                                                                                                width = 1.5.dp,
                                                                                                color = colors.accent.copy(alpha = 0.4f),
                                                                                                shape = CircleShape
                                                                                        ),
                                                                        contentAlignment =
                                                                                Alignment.Center
                                                                ) {
                                                                        Text(
                                                                                text = "AB",
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .labelSmall,
                                                                                fontWeight =
                                                                                        FontWeight.Black,
                                                                                color = colors.accent,
                                                                                fontSize = 10.sp
                                                                        )
                                                                }
                                                        }

                                                        Text(
                                                                text =
                                                                        getTypeLabel(
                                                                                config.visualType
                                                                        ),
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelSmall,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = colors.textSecondary,
                                                                letterSpacing = 1.2.sp
                                                        )
                                                }

                                                // Status badges (stacked)
                                                Column(
                                                        horizontalAlignment = Alignment.End,
                                                        verticalArrangement =
                                                                Arrangement.spacedBy(6.dp)
                                                ) {
                                                        if (showAuthboundBadge) {
                                                                // Authbound verification badge
                                                                Surface(
                                                                        color =
                                                                                colors.accent.copy(
                                                                                        alpha = 0.2f
                                                                                ),
                                                                        shape =
                                                                                RoundedCornerShape(
                                                                                        100.dp
                                                                                )
                                                                ) {
                                                                        Row(
                                                                                modifier =
                                                                                        Modifier.padding(
                                                                                                horizontal =
                                                                                                        10.dp,
                                                                                                vertical =
                                                                                                        4.dp
                                                                                        ),
                                                                                verticalAlignment =
                                                                                        Alignment
                                                                                                .CenterVertically,
                                                                                horizontalArrangement =
                                                                                        Arrangement
                                                                                                .spacedBy(
                                                                                                        4.dp
                                                                                                )
                                                                        ) {
                                                                                WrapIcon(
                                                                                        iconData =
                                                                                                AppIcons.Verified,
                                                                                        customTint =
                                                                                                colors.accent,
                                                                                        modifier =
                                                                                                Modifier.size(
                                                                                                        12.dp
                                                                                                )
                                                                                )
                                                                                Text(
                                                                                        text =
                                                                                                "Authbound",
                                                                                        style =
                                                                                                MaterialTheme
                                                                                                        .typography
                                                                                                        .labelSmall,
                                                                                        fontWeight =
                                                                                                FontWeight
                                                                                                        .SemiBold,
                                                                                        color =
                                                                                                colors.textPrimary
                                                                                )
                                                                        }
                                                                }
                                                        }
                                                        if (config.status != CredentialStatus.ISSUED) {
                                                                CredentialStatusBadge(
                                                                        status = config.status,
                                                                        colors = colors
                                                                )
                                                        }
                                                }
                                        }

                                        // Middle section: Title + Subtitle with improved spacing
                                        Column(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(
                                                                        vertical = 8.dp
                                                                ), // Add vertical padding for
                                                // breathing room
                                                verticalArrangement =
                                                        Arrangement.spacedBy(
                                                                4.dp
                                                        ) // Slightly increased spacing
                                        ) {
                                                Text(
                                                        text = config.title,
                                                        style =
                                                                MaterialTheme.typography.titleLarge
                                                                        .copy(
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold,
                                                                                letterSpacing =
                                                                                        (-0.5).sp
                                                                        ),
                                                        color = colors.textPrimary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                )

                                                if (!config.subtitle.isNullOrBlank()) {
                                                        Text(
                                                                text = config.subtitle,
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodyMedium,
                                                                color = colors.textSecondary,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                        )
                                                }
                                        }

                                        // Bottom section: Photo + Details
                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                verticalAlignment = Alignment.Bottom
                                        ) {
                                                // Photo with premium ID card-style frame
                                                if (shouldShowPhoto) {
                                                        Box(
                                                                modifier =
                                                                        Modifier.size(
                                                                                        64.dp
                                                                                ) // Slightly larger
                                                                                // for better
                                                                                // visibility
                                                                                .clip(
                                                                                        RoundedCornerShape(
                                                                                                8.dp
                                                                                        )
                                                                                ) // Slightly
                                                                                // smaller radius
                                                                                // for ID card
                                                                                // feel
                                                                                .background(
                                                                                        Brush.verticalGradient(
                                                                                                colors =
                                                                                                        listOf(
                                                                                                                colors.textPrimary
                                                                                                                        .copy(
                                                                                                                                alpha =
                                                                                                                                        0.12f
                                                                                                                        ),
                                                                                                                colors.textPrimary
                                                                                                                        .copy(
                                                                                                                                alpha =
                                                                                                                                        0.06f
                                                                                                                        )
                                                                                                        )
                                                                                        )
                                                                                )
                                                                                .border(
                                                                                        width =
                                                                                                2.dp, // Slightly thicker border
                                                                                        brush =
                                                                                                Brush.linearGradient(
                                                                                                        colors =
                                                                                                                listOf(
                                                                                                                        colors.accent
                                                                                                                                .copy(
                                                                                                                                        alpha =
                                                                                                                                                0.5f
                                                                                                                                ),
                                                                                                                        colors.accent
                                                                                                                                .copy(
                                                                                                                                        alpha =
                                                                                                                                                0.2f
                                                                                                                                )
                                                                                                                )
                                                                                                ),
                                                                                        shape =
                                                                                                RoundedCornerShape(
                                                                                                        8.dp
                                                                                                )
                                                                                ),
                                                                contentAlignment = Alignment.Center
                                                        ) {
                                                                // Inner shadow effect for photo
                                                                // frame depth
                                                                Box(
                                                                        modifier =
                                                                                Modifier.fillMaxSize()
                                                                                        .padding(
                                                                                                2.dp
                                                                                        )
                                                                                        .clip(
                                                                                                RoundedCornerShape(
                                                                                                        6.dp
                                                                                                )
                                                                                        )
                                                                                        .background(
                                                                                                Brush.radialGradient(
                                                                                                        colors =
                                                                                                                listOf(
                                                                                                                        Color.Transparent,
                                                                                                                        colors.gradientStart
                                                                                                                                .copy(
                                                                                                                                        alpha =
                                                                                                                                                0.3f
                                                                                                                                )
                                                                                                                )
                                                                                                )
                                                                                        )
                                                                )
                                                                ImageOrPlaceholder(
                                                                        modifier =
                                                                                Modifier.fillMaxSize()
                                                                                        .padding(
                                                                                                2.dp
                                                                                        )
                                                                                        .clip(
                                                                                                RoundedCornerShape(
                                                                                                        6.dp
                                                                                                )
                                                                                        ),
                                                                        base64Image =
                                                                                config.portraitBase64
                                                                                        .orEmpty(),
                                                                        contentScale =
                                                                                ContentScale.Crop,
                                                                        fallbackIcon = AppIcons.User
                                                                )
                                                        }
                                                }

                                                // Holder details with improved typography
                                                Column(
                                                        modifier = Modifier.weight(1f),
                                                        verticalArrangement =
                                                                Arrangement.spacedBy(
                                                                        4.dp
                                                                ) // Increased spacing
                                                ) {
                                                        if (!config.holderName.isNullOrBlank()) {
                                                                Text(
                                                                        text = config.holderName,
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .titleMedium
                                                                                        .copy(
                                                                                                fontWeight =
                                                                                                        FontWeight
                                                                                                                .SemiBold,
                                                                                                fontSize =
                                                                                                        17.sp // Slightly larger for holder
                                                                                                // name prominence
                                                                                                ),
                                                                        color = colors.textPrimary,
                                                                        maxLines = 1,
                                                                        overflow =
                                                                                TextOverflow
                                                                                        .Ellipsis
                                                                )
                                                        }

                                                        if (!config.expiryDate.isNullOrBlank()) {
                                                                Text(
                                                                        text =
                                                                                "Valid until: ${config.expiryDate}",
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .bodySmall,
                                                                        color =
                                                                                colors.textSecondary,
                                                                        letterSpacing =
                                                                                0.3.sp // Subtle
                                                                        // letter
                                                                        // spacing
                                                                        // for
                                                                        // readability
                                                                        )
                                                        }
                                                }
                                        }
                                }
                                }
                        }
                }
        }
        if (enableAnimations) {
                var isVisible by remember { mutableStateOf(animationDelay == 0) }
                LaunchedEffect(Unit) {
                        if (animationDelay > 0) {
                                delay(animationDelay.toLong())
                                isVisible = true
                        }
                }
                AnimatedVisibility(
                        visible = isVisible,
                        enter =
                                fadeIn(tween(300)) +
                                        slideInVertically(
                                                animationSpec = tween(300),
                                                initialOffsetY = { it / 4 }
                                        )
                ) { content() }
        } else {
                content()
        }
}

/**
 * Passport-style card content for hero credentials: restrained physical-ID cues without the
 * machine-readable strip, which is too noisy for the home screen.
 */
@Composable
private fun PassportCardContent(
        config: VisualCredentialConfig,
        colors: CredentialColorScheme
) {
        Box(modifier = Modifier.fillMaxSize()) {
                PassportMicroLines(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        color = colors.accent
                )
                Column(
                        modifier =
                                Modifier.fillMaxSize()
                                        .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                ) {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                        ) {
                                Text(
                                        text = getTypeLabel(config.visualType),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 1.8.sp,
                                        color = colors.textSecondary
                                )
                                // Healthy issued credentials need no badge — "Issued" is the
                                // default state of every card and just reads as UI noise.
                                // Only surface exceptional lifecycle states.
                                if (config.status != CredentialStatus.ISSUED) {
                                        CredentialStatusBadge(
                                                status = config.status,
                                                colors = colors
                                        )
                                }
                        }
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                // Same rule as the presentation pass: a photo window
                                // belongs to any credential that carries a portrait or is
                                // an identity document type; attribute-only credentials
                                // use the full width for their fields instead.
                                if (config.visualType.isIdentityDocumentType() ||
                                        !config.portraitBase64.isNullOrBlank()
                                ) {
                                        IdentityPortraitFrame(
                                                portraitBase64 = config.portraitBase64,
                                                frameTint = colors.textPrimary
                                        )
                                }
                                Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(9.dp)
                                ) {
                                        val displayName =
                                                config.holderName?.takeIf { it.isNotBlank() }
                                                        ?: config.title
                                        IdentityHolderName(
                                                name = displayName,
                                                color = colors.textPrimary
                                        )
                                        IdentityHairline(
                                                color = colors.textPrimary.copy(alpha = 0.14f)
                                        )
                                        when (config.passportFieldDensity) {
                                                PassportFieldDensity.SUMMARY -> {
                                                        // Details screen: one key fact on the face;
                                                        // full claims live in ClaimPropertyList.
                                                        IdentityLabeledField(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                label =
                                                                        stringResource(
                                                                                R.string
                                                                                        .credential_card_label_valid_until
                                                                        ),
                                                                value = config.expiryDate,
                                                                labelColor = colors.textSecondary,
                                                                valueColor = colors.textPrimary
                                                        )
                                                }
                                                PassportFieldDensity.FULL -> {
                                                        // Home hero: compact identity strip.
                                                        Column(
                                                                verticalArrangement =
                                                                        Arrangement.spacedBy(8.dp)
                                                        ) {
                                                                Row(
                                                                        modifier =
                                                                                Modifier.fillMaxWidth(),
                                                                        horizontalArrangement =
                                                                                Arrangement
                                                                                        .spacedBy(
                                                                                                16.dp
                                                                                        )
                                                                ) {
                                                                        IdentityLabeledField(
                                                                                modifier =
                                                                                        Modifier
                                                                                                .weight(
                                                                                                        1f
                                                                                                ),
                                                                                label =
                                                                                        stringResource(
                                                                                                R
                                                                                                        .string
                                                                                                        .credential_card_label_nationality
                                                                                        ),
                                                                                value =
                                                                                        config
                                                                                                .nationality,
                                                                                labelColor =
                                                                                        colors
                                                                                                .textSecondary,
                                                                                valueColor =
                                                                                        colors
                                                                                                .textPrimary
                                                                        )
                                                                        IdentityLabeledField(
                                                                                modifier =
                                                                                        Modifier
                                                                                                .weight(
                                                                                                        1.25f
                                                                                                ),
                                                                                label =
                                                                                        stringResource(
                                                                                                R
                                                                                                        .string
                                                                                                        .credential_card_label_date_of_birth
                                                                                        ),
                                                                                value =
                                                                                        config
                                                                                                .birthDate,
                                                                                labelColor =
                                                                                        colors
                                                                                                .textSecondary,
                                                                                valueColor =
                                                                                        colors
                                                                                                .textPrimary
                                                                        )
                                                                }
                                                                IdentityLabeledField(
                                                                        modifier =
                                                                                Modifier
                                                                                        .fillMaxWidth(),
                                                                        label =
                                                                                stringResource(
                                                                                        R.string
                                                                                                .credential_card_label_valid_until
                                                                                ),
                                                                        value = config.expiryDate,
                                                                        labelColor =
                                                                                colors
                                                                                        .textSecondary,
                                                                        valueColor =
                                                                                colors.textPrimary
                                                                )
                                                        }
                                                }
                                        }
                                }
                        }
                        if (config.passportFieldDensity == PassportFieldDensity.FULL) {
                                PassportFooterRule(
                                        issuerName = config.issuerName,
                                        colors = colors
                                )
                        } else {
                                // SUMMARY (details): issuer is shown once below the card.
                                Spacer(modifier = Modifier.height(4.dp))
                        }
                }
        }
}

@Composable
private fun PassportFooterRule(
        issuerName: String?,
        colors: CredentialColorScheme
) {
        val footerLabel: String? = issuerName
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.uppercase(Locale.ROOT)
        Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
                Box(
                        modifier =
                                Modifier.weight(1f)
                                        .height(1.dp)
                                        .background(colors.textPrimary.copy(alpha = 0.12f))
                )
                if (footerLabel != null) {
                        Text(
                                text = footerLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.4.sp,
                                color = colors.textSecondary.copy(alpha = 0.58f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                        )
                }
        }
}

/** Fine horizontal security lines near the bottom of the card. */
@Composable
private fun PassportMicroLines(
        modifier: Modifier = Modifier,
        color: Color
) {
        Box(
                modifier =
                        modifier.fillMaxWidth()
                                .height(48.dp)
        ) {
                Canvas(
                        modifier = Modifier.matchParentSize(),
                ) {
                        val spacing = 4.dp.toPx()
                        val stroke = 0.8.dp.toPx()
                        var y = 0f
                        while (y < size.height) {
                                drawLine(
                                        color = color.copy(alpha = 0.035f),
                                        start = Offset(0f, y),
                                        end = Offset(size.width, y),
                                        strokeWidth = stroke
                                )
                                y += spacing
                        }
                }
        }
}

/**
 * Premium status badge for credential cards. Uses gradient backgrounds and icons for visual
 * distinction.
 */
@Composable
private fun CredentialStatusBadge(
        status: CredentialStatus,
        colors: CredentialColorScheme,
        modifier: Modifier = Modifier
) {
        val (backgroundColor, dotColor, label, icon) =
                when (status) {
                        CredentialStatus.ISSUED ->
                                Quadruple(
                                        colors.textPrimary.copy(alpha = 0.10f),
                                        MaterialTheme.colorScheme.success,
                                        "Issued",
                                        AppIcons.Verified
                                )
                        CredentialStatus.PENDING ->
                                Quadruple(
                                        Color(0xFFFFA726).copy(alpha = 0.25f),
                                        Color(0xFFFFA726),
                                        "Pending",
                                        null
                                )
                        CredentialStatus.EXPIRED ->
                                Quadruple(
                                        Color(0xFFEF5350).copy(alpha = 0.25f),
                                        Color(0xFFEF5350),
                                        "Expired",
                                        null
                                )
                        CredentialStatus.REVOKED ->
                                Quadruple(
                                        Color(0xFFEF5350).copy(alpha = 0.25f),
                                        Color(0xFFEF5350),
                                        "Revoked",
                                        null
                                )
                }

        Surface(modifier = modifier, shape = RoundedCornerShape(100.dp), color = backgroundColor) {
                Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                        IdentityStatusDot(color = dotColor)
                        Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = colors.textPrimary
                        )
                }
        }
}

/** Helper data class for status badge configuration. */
private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/** Get display label for credential type. */
private fun getTypeLabel(type: CredentialVisualType): String {
        return when (type) {
                CredentialVisualType.PID -> "PERSONAL ID"
                CredentialVisualType.MDL -> "DRIVING LICENSE"
                CredentialVisualType.DIPLOMA -> "EDUCATION"
                CredentialVisualType.HEALTH -> "HEALTH"
                CredentialVisualType.GENERIC -> "CREDENTIAL"
                CredentialVisualType.AUTHBOUND -> "AUTHBOUND ID"
        }
}

/**
 * Premium decorative circles for credential card backgrounds. Creates subtle brand-aligned visual
 * interest inspired by Authbound design system. Positioned in top-right corner with varying sizes
 * and opacities.
 */
@Composable
private fun CredentialDecorativeCircles(modifier: Modifier = Modifier, color: Color) {
        Canvas(modifier = modifier.size(120.dp).padding(8.dp)) {
                // Large circle (outline) - creates depth
                drawCircle(
                        color = color.copy(alpha = 0.06f),
                        radius = 48.dp.toPx(),
                        center = Offset(size.width * 0.65f, size.height * 0.35f),
                        style = Stroke(width = 1.5.dp.toPx())
                )
                // Medium circle (filled) - adds visual weight
                drawCircle(
                        color = color.copy(alpha = 0.08f),
                        radius = 18.dp.toPx(),
                        center = Offset(size.width * 0.35f, size.height * 0.55f)
                )
                // Small circle (filled) - detail accent
                drawCircle(
                        color = color.copy(alpha = 0.1f),
                        radius = 7.dp.toPx(),
                        center = Offset(size.width * 0.8f, size.height * 0.7f)
                )
        }
}

/**
 * Security pattern overlay for credential cards. Creates a subtle guilloche-like pattern similar to
 * real ID cards and passports. Uses very low opacity arcs to create a sophisticated security feel.
 */
/**
 * Security pattern overlay for credential cards. Creates a subtle guilloche-like pattern similar to
 * real ID cards and passports. Uses very low opacity arcs to create a sophisticated security feel.
 */
@Composable
private fun CredentialSecurityPattern(modifier: Modifier = Modifier, color: Color) {
        Canvas(modifier = modifier) {
                val patternColor =
                        color.copy(alpha = 0.045f) // Slightly increased for more ID card feel
                val strokeWidth = 1.dp.toPx() // Slightly thicker strokes

                // Create subtle curved lines (guilloche-inspired)
                // Arc 1 - Large sweeping curve from bottom-left
                drawArc(
                        color = patternColor,
                        startAngle = 180f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(-size.width * 0.3f, size.height * 0.4f),
                        size =
                                androidx.compose.ui.geometry.Size(
                                        size.width * 0.8f,
                                        size.height * 0.8f
                                ),
                        style = Stroke(width = strokeWidth)
                )

                // Arc 2 - Medium curve
                drawArc(
                        color = patternColor,
                        startAngle = 200f,
                        sweepAngle = 70f,
                        useCenter = false,
                        topLeft = Offset(-size.width * 0.2f, size.height * 0.5f),
                        size =
                                androidx.compose.ui.geometry.Size(
                                        size.width * 0.6f,
                                        size.height * 0.6f
                                ),
                        style = Stroke(width = strokeWidth)
                )

                // Arc 3 - Top-right curve
                drawArc(
                        color = patternColor,
                        startAngle = -20f,
                        sweepAngle = 60f,
                        useCenter = false,
                        topLeft = Offset(size.width * 0.5f, -size.height * 0.2f),
                        size =
                                androidx.compose.ui.geometry.Size(
                                        size.width * 0.7f,
                                        size.height * 0.5f
                                ),
                        style = Stroke(width = strokeWidth)
                )

                // Subtle horizontal lines (microprint effect)
                for (i in 0..3) {
                        val yPosition = size.height * (0.25f + i * 0.18f)
                        drawLine(
                                color = color.copy(alpha = 0.025f), // Slightly increased
                                start = Offset(0f, yPosition),
                                end = Offset(size.width * 0.3f, yPosition),
                                strokeWidth = 0.6.dp.toPx()
                        )
                }
        }
}

@ThemeModePreviews
@Composable
private fun PassportCredentialCardPreview() {
        PreviewTheme {
                Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                        // PID hero card with passport-style identity fields
                        VisualCredentialCard(
                                config =
                                        VisualCredentialConfig(
                                                id = "1",
                                                visualType = CredentialVisualType.AUTHBOUND,
                                                title = "Authbound Digital ID",
                                                subtitle = "Digital ID",
                                                holderName = "Lassi Palojärvi",
                                                issuerName = "Authbound",
                                                primaryField = null,
                                                secondaryField = null,
                                                status = CredentialStatus.ISSUED,
                                                expiryDate = "11/07/2026",
                                                hasPhoto = false,
                                                nationality = "FIN",
                                                birthDate = "15/03/1990",
                                                layout = CredentialCardLayout.PASSPORT
                                        )
                        )

                        VisualCredentialCard(
                                config =
                                        VisualCredentialConfig(
                                                id = "2",
                                                visualType = CredentialVisualType.MDL,
                                                title = "Driving License",
                                                subtitle = "Mobile Driving License",
                                                holderName = "Lassi Palojärvi",
                                                issuerName = "Transport Agency",
                                                primaryField = null,
                                                secondaryField = null,
                                                status = CredentialStatus.ISSUED,
                                                expiryDate = "15/06/2028",
                                                hasPhoto = false,
                                                layout = CredentialCardLayout.PASSPORT
                                        )
                        )
                }
        }
}

@ThemeModePreviews
@Composable
private fun VisualCredentialCardPreview() {
        PreviewTheme {
                Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                        VisualCredentialCard(
                                config =
                                        VisualCredentialConfig(
                                                id = "1",
                                                visualType = CredentialVisualType.PID,
                                                title = "National ID Card",
                                                subtitle = "Personal Identification Data",
                                                holderName = "John Michael Doe",
                                                issuerName = "Finland",
                                                primaryField = "Born: 15.03.1990",
                                                secondaryField = null,
                                                status = CredentialStatus.ISSUED,
                                                expiryDate = "01.01.2030",
                                                hasPhoto = true
                                        )
                        )

                        VisualCredentialCard(
                                config =
                                        VisualCredentialConfig(
                                                id = "2",
                                                visualType = CredentialVisualType.MDL,
                                                title = "Driving License",
                                                subtitle = "Mobile Driving License (mDL)",
                                                holderName = "John Doe",
                                                issuerName = "Transport Agency",
                                                primaryField = "Categories: B, BE",
                                                secondaryField = null,
                                                status = CredentialStatus.PENDING,
                                                expiryDate = "15.06.2028",
                                                hasPhoto = true
                                        )
                        )

                        VisualCredentialCard(
                                config =
                                        VisualCredentialConfig(
                                                id = "3",
                                                visualType = CredentialVisualType.DIPLOMA,
                                                title = "Master's Degree",
                                                subtitle = "Computer Science",
                                                holderName = "John Doe",
                                                issuerName = "University of Helsinki",
                                                primaryField = "Graduated: 2020",
                                                secondaryField = null,
                                                status = CredentialStatus.ISSUED,
                                                expiryDate = null,
                                                hasPhoto = false
                                        )
                        )
                }
        }
}
