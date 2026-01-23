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

package eu.europa.ec.dashboardfeature.ui.actions.model

import java.time.Instant

/**
 * Types of actionable requests that can be received via Authbound handle.
 */
enum class ActionType {
    /** Request to digitally sign a document */
    SIGN_REQUEST,
    /** Request to verify identity */
    VERIFY_REQUEST,
    /** Request to share specific data attributes */
    DATA_REQUEST
}

/**
 * Status of an action request.
 */
enum class ActionStatus {
    /** Action is waiting for user response */
    PENDING,
    /** User accepted the action */
    ACCEPTED,
    /** User declined the action */
    DECLINED,
    /** Action expired without response */
    EXPIRED
}

/**
 * Domain model for an action request.
 */
data class ActionRequest(
    val id: String,
    val type: ActionType,
    val title: String,
    val requesterName: String,
    val requesterLogoUrl: String?,
    val description: String?,
    val timestamp: Instant,
    val expiresAt: Instant?,
    val status: ActionStatus,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * UI model for displaying action requests.
 */
data class ActionUi(
    val id: String,
    val type: ActionType,
    val title: String,
    val requesterName: String,
    val description: String?,
    val relativeTime: String,
    val status: ActionStatus,
    val isActionable: Boolean
)

/**
 * Category for grouping actions in the UI.
 */
sealed class ActionCategoryUi(val displayName: String) {
    data object Pending : ActionCategoryUi("Pending")
    data object Today : ActionCategoryUi("Today")
    data object ThisWeek : ActionCategoryUi("This Week")
    data object Earlier : ActionCategoryUi("Earlier")
}

/**
 * Sort order options for actions list.
 */
enum class ActionSortOrder {
    NEWEST_FIRST,
    OLDEST_FIRST,
    BY_TYPE,
    BY_REQUESTER
}

/**
 * UI model for filter chips in the management bar.
 */
data class ActionFilterChipUi(
    val type: ActionType?,
    val label: String,
    val count: Int,
    val isSelected: Boolean
)

/**
 * Device linking status for Authbound portal connection.
 */
enum class DeviceLinkStatus {
    /** No device has been linked */
    NOT_LINKED,
    /** Device is linked and connected */
    LINKED,
    /** Checking device status */
    CHECKING
}

/**
 * Information about a linked device.
 */
data class LinkedDeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val deviceModel: String,
    val linkedAt: Instant,
    val isCurrentDevice: Boolean = true
)
