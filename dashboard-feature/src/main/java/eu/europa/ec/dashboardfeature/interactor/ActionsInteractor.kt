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

package eu.europa.ec.dashboardfeature.interactor

import eu.europa.ec.dashboardfeature.ui.actions.model.ActionCategoryUi
import eu.europa.ec.dashboardfeature.ui.actions.model.ActionRequest
import eu.europa.ec.dashboardfeature.ui.actions.model.ActionStatus
import eu.europa.ec.dashboardfeature.ui.actions.model.ActionType
import eu.europa.ec.dashboardfeature.ui.actions.model.ActionUi
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.Instant

sealed class ActionsInteractorPartialState {
    data class Success(
        val pendingCount: Int,
        val allActions: List<ActionUi>,
        val groupedActions: List<Pair<ActionCategoryUi, List<ActionUi>>>
    ) : ActionsInteractorPartialState()

    data class Failure(val error: String) : ActionsInteractorPartialState()
}

interface ActionsInteractor {
    fun getActions(): Flow<ActionsInteractorPartialState>
    suspend fun acceptAction(actionId: String): Result<Unit>
    suspend fun declineAction(actionId: String): Result<Unit>
    fun getPendingActionsCount(): Flow<Int>
}

class ActionsInteractorImpl(
    private val resourceProvider: ResourceProvider,
) : ActionsInteractor {

    // Mock data - will be replaced with real API calls
    private val mockActions = mutableListOf(
        ActionRequest(
            id = "action_1",
            type = ActionType.SIGN_REQUEST,
            title = resourceProvider.getString(R.string.actions_type_sign_request),
            requesterName = "Nordic Bank",
            requesterLogoUrl = null,
            description = "Request to digitally sign your apartment lease agreement for property at Mannerheimintie 1, Helsinki.",
            timestamp = Instant.now().minus(Duration.ofHours(2)),
            expiresAt = Instant.now().plus(Duration.ofDays(7)),
            status = ActionStatus.PENDING
        ),
        ActionRequest(
            id = "action_2",
            type = ActionType.VERIFY_REQUEST,
            title = resourceProvider.getString(R.string.actions_type_verify_request),
            requesterName = "Tax Authority",
            requesterLogoUrl = null,
            description = "Verify your identity for annual tax filing submission.",
            timestamp = Instant.now().minus(Duration.ofHours(5)),
            expiresAt = Instant.now().plus(Duration.ofDays(30)),
            status = ActionStatus.PENDING
        ),
        ActionRequest(
            id = "action_3",
            type = ActionType.DATA_REQUEST,
            title = resourceProvider.getString(R.string.actions_type_data_request),
            requesterName = "Insurance Company",
            requesterLogoUrl = null,
            description = "Share your driving license information for auto insurance quote.",
            timestamp = Instant.now().minus(Duration.ofHours(8)),
            expiresAt = Instant.now().plus(Duration.ofDays(3)),
            status = ActionStatus.PENDING
        ),
        ActionRequest(
            id = "action_4",
            type = ActionType.DATA_REQUEST,
            title = resourceProvider.getString(R.string.actions_type_data_request),
            requesterName = "Online Store",
            requesterLogoUrl = null,
            description = "Age verification for purchase.",
            timestamp = Instant.now().minus(Duration.ofDays(1)),
            expiresAt = null,
            status = ActionStatus.ACCEPTED
        ),
        ActionRequest(
            id = "action_5",
            type = ActionType.VERIFY_REQUEST,
            title = resourceProvider.getString(R.string.actions_type_verify_request),
            requesterName = "Healthcare Portal",
            requesterLogoUrl = null,
            description = "Identity verification for patient portal access.",
            timestamp = Instant.now().minus(Duration.ofDays(2)),
            expiresAt = null,
            status = ActionStatus.ACCEPTED
        ),
        ActionRequest(
            id = "action_6",
            type = ActionType.SIGN_REQUEST,
            title = resourceProvider.getString(R.string.actions_type_sign_request),
            requesterName = "Mobile Operator",
            requesterLogoUrl = null,
            description = "Contract signature request.",
            timestamp = Instant.now().minus(Duration.ofDays(5)),
            expiresAt = null,
            status = ActionStatus.DECLINED
        ),
        ActionRequest(
            id = "action_7",
            type = ActionType.DATA_REQUEST,
            title = resourceProvider.getString(R.string.actions_type_data_request),
            requesterName = "Unknown Service",
            requesterLogoUrl = null,
            description = "Request for personal data.",
            timestamp = Instant.now().minus(Duration.ofDays(10)),
            expiresAt = null,
            status = ActionStatus.EXPIRED
        )
    )

    override fun getActions(): Flow<ActionsInteractorPartialState> = flow {
        try {
            val actionUis = mockActions.map { action ->
                ActionUi(
                    id = action.id,
                    type = action.type,
                    title = action.title,
                    requesterName = action.requesterName,
                    description = action.description,
                    relativeTime = formatRelativeTime(action.timestamp),
                    status = action.status,
                    isActionable = action.status == ActionStatus.PENDING
                )
            }

            // Group by category
            val pendingActions = actionUis.filter { it.status == ActionStatus.PENDING }
            val completedActions = actionUis.filter { it.status != ActionStatus.PENDING }

            val grouped = mutableListOf<Pair<ActionCategoryUi, List<ActionUi>>>()

            if (pendingActions.isNotEmpty()) {
                grouped.add(ActionCategoryUi.Pending to pendingActions)
            }

            // Group completed actions by time
            val today = completedActions.filter { isToday(it) }
            val thisWeek = completedActions.filter { isThisWeek(it) && !isToday(it) }
            val earlier = completedActions.filter { !isThisWeek(it) }

            if (today.isNotEmpty()) {
                grouped.add(ActionCategoryUi.Today to today)
            }
            if (thisWeek.isNotEmpty()) {
                grouped.add(ActionCategoryUi.ThisWeek to thisWeek)
            }
            if (earlier.isNotEmpty()) {
                grouped.add(ActionCategoryUi.Earlier to earlier)
            }

            emit(
                ActionsInteractorPartialState.Success(
                    pendingCount = pendingActions.size,
                    allActions = actionUis,
                    groupedActions = grouped
                )
            )
        } catch (e: Exception) {
            emit(ActionsInteractorPartialState.Failure(e.localizedMessage ?: "Unknown error"))
        }
    }

    override suspend fun acceptAction(actionId: String): Result<Unit> {
        return try {
            val index = mockActions.indexOfFirst { it.id == actionId }
            if (index >= 0) {
                mockActions[index] = mockActions[index].copy(status = ActionStatus.ACCEPTED)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun declineAction(actionId: String): Result<Unit> {
        return try {
            val index = mockActions.indexOfFirst { it.id == actionId }
            if (index >= 0) {
                mockActions[index] = mockActions[index].copy(status = ActionStatus.DECLINED)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getPendingActionsCount(): Flow<Int> = flow {
        emit(mockActions.count { it.status == ActionStatus.PENDING })
    }

    private fun formatRelativeTime(timestamp: Instant): String {
        val now = Instant.now()
        val duration = Duration.between(timestamp, now)

        return when {
            duration.toMinutes() < 1 -> resourceProvider.getString(R.string.actions_time_just_now)
            duration.toMinutes() < 60 -> resourceProvider.getString(
                R.string.actions_time_minutes_ago,
                duration.toMinutes()
            )
            duration.toHours() < 24 -> resourceProvider.getString(
                R.string.actions_time_hours_ago,
                duration.toHours()
            )
            duration.toDays() == 1L -> resourceProvider.getString(R.string.actions_time_yesterday)
            duration.toDays() < 7 -> resourceProvider.getString(
                R.string.actions_time_days_ago,
                duration.toDays()
            )
            else -> resourceProvider.getString(
                R.string.actions_time_weeks_ago,
                duration.toDays() / 7
            )
        }
    }

    private fun isToday(action: ActionUi): Boolean {
        // Simplified check - in real implementation use proper date comparison
        return action.relativeTime.contains("hour") ||
               action.relativeTime.contains("minute") ||
               action.relativeTime.contains("just now")
    }

    private fun isThisWeek(action: ActionUi): Boolean {
        return action.relativeTime.contains("day") || isToday(action)
    }
}
