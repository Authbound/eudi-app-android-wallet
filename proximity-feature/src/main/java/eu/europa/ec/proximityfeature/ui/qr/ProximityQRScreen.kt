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

package eu.europa.ec.proximityfeature.ui.qr

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import eu.europa.ec.proximityfeature.interactor.ProximityPresentingDocumentUi
import eu.europa.ec.uilogic.component.qr.rememberQrBitmapPainter
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.brandNavyDeep
import eu.europa.ec.resourceslogic.theme.values.brandNavyMedium
import eu.europa.ec.resourceslogic.theme.values.glowAccent
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.loader.SkeletonBox
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.LifecycleEffect
import eu.europa.ec.uilogic.component.utils.OneTimeLaunchedEffect
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.utils.rememberAnimationsEnabled
import eu.europa.ec.uilogic.component.wrap.IdentityHairline
import eu.europa.ec.uilogic.component.wrap.IdentityHolderName
import eu.europa.ec.uilogic.component.wrap.IdentityLabeledField
import eu.europa.ec.uilogic.component.wrap.IdentityPortraitFrame
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapImage
import eu.europa.ec.uilogic.component.wrap.WrapModalBottomSheet
import eu.europa.ec.uilogic.extension.paddingFrom
import eu.europa.ec.uilogic.navigation.ProximityScreens
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow

@Composable
fun ProximityQRScreen(
    navController: NavController,
    viewModel: ProximityQRViewModel
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hasBlePermissions = rememberBlePermissionsGranted()

    ContentScreen(
        isLoading = state.isLoading,
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack = { viewModel.setEvent(Event.GoBack) },
        contentErrorConfig = state.error,
    ) { paddingValues ->
        Content(
            state = state,
            effectFlow = viewModel.effect,
            onGoBack = { viewModel.setEvent(Event.GoBack) },
            onNavigationRequested = { navigationEffect ->
                when (navigationEffect) {
                    is Effect.Navigation.SwitchScreen -> {
                        navController.navigate(navigationEffect.screenRoute) {
                            popUpTo(ProximityScreens.QR.screenRoute) {
                                inclusive = true
                            }
                        }
                    }

                    is Effect.Navigation.Pop -> {
                        navController.popBackStack()
                    }
                }
            },
            paddingValues = paddingValues
        )
    }

    if (hasBlePermissions) {
        OneTimeLaunchedEffect {
            viewModel.setEvent(Event.Init)
        }
    }

    LifecycleEffect(
        lifecycleOwner = LocalLifecycleOwner.current,
        lifecycleEvent = Lifecycle.Event.ON_RESUME
    ) {
        if (hasBlePermissions) {
            viewModel.setEvent(
                Event.NfcEngagement(
                    componentActivity = context as ComponentActivity,
                    enable = true
                )
            )
        }
    }

    LifecycleEffect(
        lifecycleOwner = LocalLifecycleOwner.current,
        lifecycleEvent = Lifecycle.Event.ON_PAUSE
    ) {
        if (hasBlePermissions) {
            viewModel.setEvent(
                Event.NfcEngagement(
                    componentActivity = context as ComponentActivity,
                    enable = false
                )
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun rememberBlePermissionsGranted(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true

    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    )

    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        }
    }

    return permissionsState.allPermissionsGranted
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Content(
    state: State,
    effectFlow: Flow<Effect>,
    onGoBack: () -> Unit,
    onNavigationRequested: (navigationEffect: Effect.Navigation) -> Unit,
    paddingValues: PaddingValues,
) {
    var isQrExpanded by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()
    val contentTopPadding = maxOf(
        SPACING_SMALL.dp,
        paddingValues.calculateTopPadding() - 36.dp
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .paddingFrom(
                pv = paddingValues,
                top = false,
                start = false,
                end = false,
                bottom = false
            )
            .verticalScroll(scrollState)
            .padding(top = contentTopPadding)
            .padding(horizontal = SPACING_LARGE.dp)
            .padding(bottom = paddingValues.calculateBottomPadding() + SPACING_SMALL.dp),
        verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)
    ) {
        PresentHeader()
        PresentationPass(
            presentingDocument = state.presentingDocument,
            isLoading = state.isLoadingPresentingDocument,
            qrCode = state.qrCode,
            onExpandQr = { isQrExpanded = true }
        )
        CancelAction(onClick = onGoBack)
    }

    if (isQrExpanded) {
        WrapModalBottomSheet(
            onDismissRequest = { isQrExpanded = false },
            sheetState = sheetState
        ) {
            ExpandedQrSheet(
                qrCode = state.qrCode,
                onClose = { isQrExpanded = false }
            )
        }
    }

    LaunchedEffect(Unit) {
        effectFlow.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> onNavigationRequested(effect)
            }
        }.collect()
    }
}

@Composable
private fun PresentHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(id = R.string.proximity_qr_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * The presentation pass: one artifact holding the identity zone and the machine-readable
 * zone (QR + NFC), joined by a perforated tear line. The QR is rendered at a directly
 * scannable size — enlarging it is an optional extra, never a required step.
 */
@Composable
private fun PresentationPass(
    presentingDocument: ProximityPresentingDocumentUi?,
    isLoading: Boolean,
    qrCode: String,
    onExpandQr: () -> Unit
) {
    val cardShape = RoundedCornerShape(22.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.glowAccent.copy(alpha = 0.55f),
                shape = cardShape
            ),
        shape = cardShape,
        color = Color.Transparent,
        shadowElevation = 10.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.brandNavyDeep,
                            MaterialTheme.colorScheme.brandNavyMedium
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    )
                )
        ) {
            PidSecurityPattern(modifier = Modifier.matchParentSize())
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 18.dp, bottom = 14.dp)
                ) {
                    if (isLoading) {
                        PassIdentitySkeleton()
                    } else if (presentingDocument != null) {
                        PassIdentityZone(presentingDocument = presentingDocument)
                    }
                }
                PassPerforation()
                PassMachineReadableZone(
                    qrCode = qrCode,
                    onExpandQr = onExpandQr
                )
            }
        }
    }
}

@Composable
private fun PassIdentityZone(presentingDocument: ProximityPresentingDocumentUi) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = presentingDocument.documentName.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.6.sp,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            presentingDocument.countryCode?.let { countryCode ->
                Surface(
                    color = Color.White.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = countryCode,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Identity documents carry the photo window (real portrait or ghost
            // silhouette); attribute-only credentials use the full width instead.
            if (presentingDocument.isIdentityDocument) {
                IdentityPortraitFrame(portraitBase64 = presentingDocument.portraitBase64)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                IdentityHolderName(
                    name = presentingDocument.holderName ?: presentingDocument.documentName
                )
                IdentityHairline()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    IdentityLabeledField(
                        modifier = Modifier.weight(1.2f),
                        label = stringResource(id = R.string.proximity_qr_field_birth_date),
                        value = presentingDocument.birthDate
                    )
                    IdentityLabeledField(
                        modifier = Modifier.weight(0.8f),
                        label = stringResource(id = R.string.proximity_qr_field_sex),
                        value = presentingDocument.sex
                    )
                    IdentityLabeledField(
                        modifier = Modifier.weight(1.2f),
                        label = stringResource(id = R.string.proximity_qr_field_valid_until),
                        value = presentingDocument.validUntil
                    )
                }
            }
        }
    }
}

/** Notched, dashed tear line joining the identity zone to the machine-readable zone. */
@Composable
private fun PassPerforation() {
    val notchColor = MaterialTheme.colorScheme.background
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
    ) {
        val notchRadius = 11.dp.toPx()
        val centerY = size.height / 2f
        val dash = 5.dp.toPx()
        val gap = 6.dp.toPx()
        val lineColor = Color.White.copy(alpha = 0.20f)
        var x = notchRadius + gap
        while (x < size.width - notchRadius - gap) {
            drawLine(
                color = lineColor,
                start = Offset(x, centerY),
                end = Offset(minOf(x + dash, size.width - notchRadius - gap), centerY),
                strokeWidth = 1.dp.toPx()
            )
            x += dash + gap
        }
        drawCircle(color = notchColor, radius = notchRadius, center = Offset(0f, centerY))
        drawCircle(color = notchColor, radius = notchRadius, center = Offset(size.width, centerY))
    }
}

@Composable
private fun PassMachineReadableZone(
    qrCode: String,
    onExpandQr: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 14.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
    ) {
        QrPreview(
            qrCode = qrCode,
            size = 224.dp,
            onClick = onExpandQr
        )
        NfcReadyRow(modifier = Modifier.widthIn(max = 240.dp))
    }
}

@Composable
private fun PassIdentitySkeleton() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
    ) {
        SkeletonBox(
            modifier = Modifier.fillMaxWidth(0.6f),
            height = 16.dp,
            cornerRadius = 8.dp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SkeletonBox(
                modifier = Modifier.width(72.dp),
                height = 92.dp,
                cornerRadius = 8.dp
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)
            ) {
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth(),
                    height = 22.dp,
                    cornerRadius = 8.dp
                )
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth(),
                    height = 30.dp,
                    cornerRadius = 8.dp
                )
            }
        }
    }
}

@Composable
private fun QrPreview(
    qrCode: String,
    size: Dp,
    onClick: () -> Unit
) {
    val enlargeQrDescription = stringResource(id = R.string.proximity_qr_enlarge_qr)
    Surface(
        modifier = Modifier
            .size(size)
            .semantics {
                role = Role.Button
                contentDescription = enlargeQrDescription
            }
            .clickable(
                onClickLabel = enlargeQrDescription,
                onClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier.padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (qrCode.isNotEmpty()) {
                WrapImage(
                    modifier = Modifier.fillMaxSize(),
                    painter = rememberQrBitmapPainter(
                        content = qrCode,
                        size = size - 16.dp
                    ),
                    contentDescription = stringResource(
                        id = R.string.content_description_qr_code_icon
                    )
                )
            } else {
                SkeletonBox(
                    modifier = Modifier.fillMaxSize(),
                    height = size - 16.dp,
                    cornerRadius = 10.dp
                )
            }
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.brandNavyDeep.copy(alpha = 0.88f)
            ) {
                WrapIcon(
                    iconData = AppIcons.OpenInBrowser,
                    customTint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(5.dp).size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun NfcReadyRow(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PulsingNfcIcon()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = stringResource(id = R.string.proximity_qr_nfc_ready),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(id = R.string.proximity_qr_hold_near_reader),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CancelAction(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(id = R.string.generic_cancel),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ExpandedQrSheet(
    qrCode: String,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SPACING_LARGE.dp),
        verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
    ) {
        ExpandedQrHeader(onClose = onClose)
        ExpandedQrCode(qrCode = qrCode)
        ExpandedQrDivider()
        CenteredNfcReadyStatus()
        Spacer(modifier = Modifier.height(SPACING_LARGE.dp))
    }
}

@Composable
private fun ExpandedQrHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(id = R.string.proximity_qr_scan_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Surface(
            modifier = Modifier.size(48.dp).clickable(onClick = onClose),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            WrapIcon(
                iconData = AppIcons.Close,
                customTint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(14.dp)
            )
        }
    }
}

@Composable
private fun ExpandedQrCode(qrCode: String) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        QrPreview(
            qrCode = qrCode,
            size = 286.dp,
            onClick = {}
        )
    }
}

@Composable
private fun ExpandedQrDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = SPACING_MEDIUM.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    )
}

@Composable
private fun CenteredNfcReadyStatus() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        NfcReadyRow(modifier = Modifier.widthIn(max = 228.dp))
    }
}

@Composable
private fun PulsingNfcIcon() {
    val animationsEnabled = rememberAnimationsEnabled()
    val haloScale: Float
    val haloAlpha: Float
    if (animationsEnabled) {
        val pulseTransition = rememberInfiniteTransition(label = "nfc_pulse")
        val animatedScale by pulseTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.22f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200),
                repeatMode = RepeatMode.Reverse
            ),
            label = "nfc_pulse_scale"
        )
        val animatedAlpha by pulseTransition.animateFloat(
            initialValue = 0.22f,
            targetValue = 0.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200),
                repeatMode = RepeatMode.Reverse
            ),
            label = "nfc_pulse_alpha"
        )
        haloScale = animatedScale
        haloAlpha = animatedAlpha
    } else {
        haloScale = 1f
        haloAlpha = 0.16f
    }
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .scale(haloScale)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = haloAlpha))
        )
        WrapImage(
            iconData = AppIcons.NFC,
            modifier = Modifier.size(34.dp)
        )
    }
}

@Composable
private fun PidSecurityPattern(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val accent = Color(0xFF38BDF8)
        val spacing = 14.dp.toPx()
        val stroke = 0.7.dp.toPx()
        var y = spacing
        while (y < size.height) {
            drawLine(
                color = accent.copy(alpha = 0.035f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = stroke
            )
            y += spacing
        }
        listOf(98.dp.toPx(), 142.dp.toPx(), 186.dp.toPx()).forEachIndexed { index, radius ->
            drawCircle(
                color = accent.copy(alpha = 0.07f - index * 0.014f),
                radius = radius,
                center = Offset(size.width * 0.92f, size.height * 0.08f),
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}

@ThemeModePreviews
@Composable
private fun ContentPreview() {
    PreviewTheme {
        Content(
            state = State(
                isLoading = false,
                error = null,
                qrCode = "some qr code",
                presentingDocument = ProximityPresentingDocumentUi(
                    holderName = "Lassi Palojärvi",
                    documentName = "Authbound Digital ID",
                    documentCode = "PID",
                    countryCode = "FIN",
                    birthDate = "12/08/1985",
                    sex = "M",
                    validUntil = "11/07/2026",
                    portraitBase64 = null
                )
            ),
            effectFlow = Channel<Effect>().receiveAsFlow(),
            onGoBack = {},
            onNavigationRequested = {},
            paddingValues = PaddingValues(SPACING_MEDIUM.dp)
        )
    }
}

@ThemeModePreviews
@Composable
private fun ContentLoadingPreview() {
    PreviewTheme {
        Content(
            state = State(
                isLoading = false,
                error = null,
                qrCode = ""
            ),
            effectFlow = Channel<Effect>().receiveAsFlow(),
            onGoBack = {},
            onNavigationRequested = {},
            paddingValues = PaddingValues(SPACING_MEDIUM.dp)
        )
    }
}
