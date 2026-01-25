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

package eu.europa.ec.quickidfeature.ui.mrzscan

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.LocalLifecycleOwner
import eu.europa.ec.resourceslogic.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.utils.LifecycleEffect
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.navigation.QuickIdScreens
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

private const val TAG = "MrzScanScreen"

/**
 * MRZ scanning screen using CameraX for passport OCR.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MrzScanScreen(
    navController: NavController,
    viewModel: MrzScanViewModel
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            !cameraPermissionState.status.isGranted -> {
                CameraPermissionContent(
                    onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
                )
            }
            else -> {
                CameraContent(
                    state = state,
                    onEventSend = { viewModel.setEvent(it) },
                    onNavigationRequested = { navigationEffect ->
                        when (navigationEffect) {
                            is Effect.Navigation.Pop -> navController.popBackStack()
                            is Effect.Navigation.SwitchScreen -> {
                                navController.navigate(navigationEffect.screenRoute) {
                                    popUpTo(QuickIdScreens.MrzScan.screenRoute) {
                                        inclusive = true
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.onEach { effect ->
            when (effect) {
                is Effect.Navigation.Pop -> navController.popBackStack()
                is Effect.Navigation.SwitchScreen -> {
                    navController.navigate(effect.screenRoute) {
                        popUpTo(QuickIdScreens.MrzScan.screenRoute) {
                            inclusive = true
                        }
                    }
                }
            }
        }.collect()
    }

    LifecycleEffect(
        lifecycleOwner = LocalLifecycleOwner.current,
        lifecycleEvent = Lifecycle.Event.ON_RESUME
    ) {
        viewModel.setEvent(Event.Init)
    }
}

@Composable
private fun CameraPermissionContent(
    onRequestPermission: () -> Unit
) {
    val gradientStart = Color(0xFF047857)
    val gradientEnd = Color(0xFF0D9488)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(listOf(gradientStart, gradientEnd))
                ),
            contentAlignment = Alignment.Center
        ) {
            WrapIcon(
                iconData = AppIcons.QrScanner,
                customTint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.quickid_camera_access_required_title),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.quickid_camera_access_required_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        WrapButton(
            modifier = Modifier.fillMaxWidth(),
            buttonConfig = ButtonConfig(
                type = ButtonType.PRIMARY,
                onClick = onRequestPermission
            )
        ) {
            Text(stringResource(R.string.quickid_camera_enable_button))
        }
    }
}

@Composable
private fun CameraContent(
    state: State,
    onEventSend: (Event) -> Unit,
    onNavigationRequested: (Effect.Navigation) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { previewView ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build()

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Camera binding failed", e)
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        )

        // Overlay with passport frame guide
        PassportFrameOverlay(
            modifier = Modifier.fillMaxSize()
        )

        // Top bar with back button
        TopBar(
            onBackClick = { onEventSend(Event.GoBack) },
            flashEnabled = state.flashEnabled,
            onFlashToggle = { onEventSend(Event.ToggleFlash) },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        )

        // Bottom controls
        BottomControls(
            state = state,
            onCapture = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                imageCapture?.takePicture(
                    cameraExecutor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bytes = imageProxyToJpegBytes(image)
                            image.close()
                            onEventSend(Event.ImageCaptured(bytes))
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e(TAG, "Capture failed", exception)
                        }
                    }
                )
            },
            onRetry = { onEventSend(Event.Retry) },
            onContinue = { onEventSend(Event.Continue) },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        )

        // Processing overlay
        AnimatedVisibility(
            visible = state.scanState is MrzScanState.Processing,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            ProcessingOverlay()
        }
    }
}

@Composable
private fun PassportFrameOverlay(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        // Semi-transparent overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
        )

        // Passport frame cutout area
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Instruction text
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Text(
                    text = stringResource(R.string.quickid_passport_position_instruction),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Passport frame (ID-3 aspect ratio: 125mm × 88mm ≈ 1.42:1)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.42f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Transparent)
                    .border(
                        width = 3.dp,
                        color = Color.White,
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                // Corner indicators
                CornerIndicators()
            }

            Spacer(modifier = Modifier.height(16.dp))

            // MRZ zone indicator
            Text(
                text = stringResource(R.string.quickid_mrz_visible_instruction),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun CornerIndicators() {
    val cornerColor = Color(0xFF10B981) // Green accent
    val cornerLength = 24.dp
    val cornerWidth = 4.dp

    Box(modifier = Modifier.fillMaxSize()) {
        // Top-left corner
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(cornerLength)
                    .height(cornerWidth)
                    .background(cornerColor)
            )
            Box(
                modifier = Modifier
                    .width(cornerWidth)
                    .height(cornerLength)
                    .background(cornerColor)
            )
        }

        // Top-right corner
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(cornerLength)
                    .height(cornerWidth)
                    .background(cornerColor)
                    .align(Alignment.TopEnd)
            )
            Box(
                modifier = Modifier
                    .width(cornerWidth)
                    .height(cornerLength)
                    .background(cornerColor)
                    .align(Alignment.TopEnd)
            )
        }

        // Bottom-left corner
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(cornerLength)
                    .height(cornerWidth)
                    .background(cornerColor)
                    .align(Alignment.BottomStart)
            )
            Box(
                modifier = Modifier
                    .width(cornerWidth)
                    .height(cornerLength)
                    .background(cornerColor)
                    .align(Alignment.BottomStart)
            )
        }

        // Bottom-right corner
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(cornerLength)
                    .height(cornerWidth)
                    .background(cornerColor)
                    .align(Alignment.BottomEnd)
            )
            Box(
                modifier = Modifier
                    .width(cornerWidth)
                    .height(cornerLength)
                    .background(cornerColor)
                    .align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
private fun TopBar(
    onBackClick: () -> Unit,
    flashEnabled: Boolean,
    onFlashToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(16.dp)
            .padding(top = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
        ) {
            WrapIcon(
                iconData = AppIcons.ArrowBack,
                customTint = Color.White
            )
        }

        // Flash toggle
        IconButton(
            onClick = onFlashToggle,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (flashEnabled) Color(0xFF10B981) else Color.Black.copy(alpha = 0.5f)
                )
        ) {
            WrapIcon(
                iconData = AppIcons.Visibility,
                customTint = Color.White
            )
        }
    }
}

@Composable
private fun BottomControls(
    state: State,
    onCapture: () -> Unit,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.7f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val scanState = state.scanState) {
                is MrzScanState.CameraPreview, is MrzScanState.Capturing -> {
                    // Capture button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onCapture()
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(4.dp, Color(0xFF10B981), CircleShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.quickid_tap_to_scan),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

                is MrzScanState.Processing -> {
                    CircularProgressIndicator(
                        color = Color(0xFF10B981),
                        modifier = Modifier.size(48.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.quickid_reading_passport),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }

                is MrzScanState.Success -> {
                    // Success message
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WrapIcon(
                            iconData = AppIcons.Check,
                            customTint = Color(0xFF10B981),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(R.string.quickid_passport_scanned),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color.White
                        )
                    }

                    // Show extracted name if available
                    scanState.mrzData.getFullName()?.let { name ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    WrapButton(
                        modifier = Modifier.fillMaxWidth(),
                        buttonConfig = ButtonConfig(
                            type = ButtonType.PRIMARY,
                            onClick = onContinue
                        )
                    ) {
                        Text(stringResource(R.string.quickid_continue_to_nfc))
                    }
                }

                is MrzScanState.Error -> {
                    // Error message
                    Text(
                        text = scanState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFEF4444),
                        textAlign = TextAlign.Center
                    )

                    if (scanState.isRetryable) {
                        Spacer(modifier = Modifier.height(16.dp))

                        WrapButton(
                            modifier = Modifier.fillMaxWidth(),
                            buttonConfig = ButtonConfig(
                                type = ButtonType.PRIMARY,
                                onClick = onRetry
                            )
                        ) {
                            Text(stringResource(R.string.generic_error_button_retry))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                color = Color(0xFF10B981),
                modifier = Modifier.size(56.dp)
            )
            Text(
                text = stringResource(R.string.quickid_analyzing_passport),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
        }
    }
}

/**
 * Converts ImageProxy to JPEG bytes for API upload.
 */
private fun imageProxyToJpegBytes(image: ImageProxy): ByteArray {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)

    // Decode and rotate if needed
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    val rotatedBitmap = if (image.imageInfo.rotationDegrees != 0) {
        val matrix = Matrix().apply {
            postRotate(image.imageInfo.rotationDegrees.toFloat())
        }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } else {
        bitmap
    }

    // Compress to JPEG
    val outputStream = ByteArrayOutputStream()
    rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)

    return outputStream.toByteArray()
}
