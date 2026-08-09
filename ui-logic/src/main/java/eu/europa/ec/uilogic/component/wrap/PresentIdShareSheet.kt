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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.brandNavyMedium
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.qr.rememberQrBitmapPainter
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL

/**
 * Bottom-sheet content for Present ID: large QR + NFC ready row only.
 * Identity already lives on the home/details card — this sheet is the share channel.
 */
@Composable
fun PresentIdShareSheet(
    qrCode: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val closeLabel: String = stringResource(id = R.string.content_description_close_icon)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SPACING_LARGE.dp)
            .padding(bottom = SPACING_LARGE.dp),
        verticalArrangement = Arrangement.spacedBy(SPACING_LARGE.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.proximity_qr_scan_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.brandNavyMedium.copy(alpha = 0.52f))
                    .clickable(
                        onClickLabel = closeLabel,
                        role = Role.Button,
                        onClick = onClose
                    )
                    .semantics {
                        contentDescription = closeLabel
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center
            ) {
                WrapIcon(
                    iconData = AppIcons.Close,
                    customTint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val availableWidth: Dp = maxWidth - (SPACING_LARGE * 2).dp
            val qrSize: Dp = if (availableWidth < 312.dp) availableWidth else 312.dp
            PresentIdQrPreview(
                qrCode = qrCode,
                size = qrSize
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = SPACING_SMALL.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
        )
        PresentIdNfcReadyStatus(
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PresentIdQrPreview(
    qrCode: String,
    size: Dp
) {
    Surface(
        modifier = Modifier.size(size),
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier.padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            if (qrCode.isNotEmpty()) {
                WrapImage(
                    modifier = Modifier.fillMaxSize(),
                    painter = rememberQrBitmapPainter(
                        content = qrCode,
                        size = size - 20.dp
                    ),
                    contentDescription = stringResource(
                        id = R.string.content_description_qr_code_icon
                    )
                )
            } else {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
private fun PresentIdNfcReadyStatus(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.brandNavyMedium.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            WrapImage(
                iconData = AppIcons.NFC,
                modifier = Modifier.size(42.dp)
            )
        }
        Spacer(modifier = Modifier.width(SPACING_MEDIUM.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = stringResource(R.string.proximity_qr_nfc_ready),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.proximity_qr_hold_near_reader),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
