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

import eu.europa.ec.dashboardfeature.repository.ActionsRepository
import eu.europa.ec.dashboardfeature.ui.actions.model.ActionCategoryUi
import eu.europa.ec.dashboardfeature.ui.actions.model.ActionRequest
import eu.europa.ec.dashboardfeature.ui.actions.model.ActionStatus
import eu.europa.ec.dashboardfeature.ui.actions.model.ActionUi
import eu.europa.ec.dashboardfeature.ui.actions.model.DeviceLinkStatus
import eu.europa.ec.dashboardfeature.ui.actions.model.LinkedDeviceInfo
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

sealed class DeviceLinkPartialState {
    data class Success(
        val status: DeviceLinkStatus,
        val deviceInfo: LinkedDeviceInfo?
    ) : DeviceLinkPartialState()

    data class Failure(val error: String) : DeviceLinkPartialState()
}

interface ActionsInteractor {
    fun getActions(): Flow<ActionsInteractorPartialState>
    suspend fun acceptAction(actionId: String): Result<Unit>
    suspend fun declineAction(actionId: String): Result<Unit>
    fun getPendingActionsCount(): Flow<Int>

    // Device linking
    fun getDeviceLinkStatus(): Flow<DeviceLinkPartialState>
    suspend fun linkDevice(linkingCode: String, fcmToken: String?): Result<LinkedDeviceInfo>
    suspend fun unlinkDevice(): Result<Unit>

    // Get action by ID for VP flow
    fun getActionById(actionId: String): ActionRequest?
}

class ActionsInteractorImpl(
    private val resourceProvider: ResourceProvider,
    private val actionsRepository: ActionsRepository
) : ActionsInteractor {

    companion object {
        private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes
    }

    // Cache of current actions for quick lookup with TTL
    private var cachedActions: List<ActionRequest> = emptyList()
    private var actionsCacheTimestamp: Long = 0L
    private var cachedDeviceInfo: LinkedDeviceInfo? = null

    /**
     * Checks if the actions cache is still valid (not expired).
     */
    private fun isActionsCacheValid(): Boolean =
        cachedActions.isNotEmpty() &&
                (System.currentTimeMillis() - actionsCacheTimestamp) < CACHE_TTL_MS

    /**
     * Invalidates the actions cache, forcing a refresh on next fetch.
     */
    fun invalidateActionsCache() {
        actionsCacheTimestamp = 0L
    }

    override fun getActions(): Flow<ActionsInteractorPartialState> = flow {
        try {
            val result = actionsRepository.fetchActions()

            result.fold(
                onSuccess = { actions ->
                    cachedActions = actions
                    actionsCacheTimestamp = System.currentTimeMillis()

                    val actionUis = actions.map { action ->
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

                    val grouped = groupActionsByCategory(actionUis)
                    val pendingCount = actionUis.count { it.status == ActionStatus.PENDING }

                    emit(
                        ActionsInteractorPartialState.Success(
                            pendingCount = pendingCount,
                            allActions = actionUis,
                            groupedActions = grouped
                        )
                    )
                },
                onFailure = { error ->
                    emit(ActionsInteractorPartialState.Failure(error.message ?: "Unknown error"))
                }
            )
        } catch (e: Exception) {
            emit(ActionsInteractorPartialState.Failure(e.localizedMessage ?: "Unknown error"))
        }
    }

    override suspend fun acceptAction(actionId: String): Result<Unit> {
        val action = getActionById(actionId)
            ?: return Result.failure(IllegalArgumentException("Action not found: $actionId"))

        // For VERIFY_REQUEST, the ViewModel should handle VP token generation
        // and call the repository directly with the VP token
        // For now, we just forward to repository without VP token
        return actionsRepository.acceptAction(actionId)
    }

    override suspend fun declineAction(actionId: String): Result<Unit> {
        return actionsRepository.declineAction(actionId)
    }

    override fun getPendingActionsCount(): Flow<Int> = flow {
        val result = actionsRepository.fetchActions(status = ActionStatus.PENDING)
        result.fold(
            onSuccess = { actions ->
                emit(actions.size)
            },
            onFailure = {
                emit(0)
            }
        )
    }

    override fun getDeviceLinkStatus(): Flow<DeviceLinkPartialState> = flow {
        try {
            val result = actionsRepository.getDeviceStatus()

            result.fold(
                onSuccess = { statusResult ->
                    cachedDeviceInfo = statusResult.deviceInfo
                    emit(DeviceLinkPartialState.Success(statusResult.status, statusResult.deviceInfo))
                },
                onFailure = { error ->
                    emit(DeviceLinkPartialState.Failure(error.message ?: "Unknown error"))
                }
            )
        } catch (e: Exception) {
            emit(DeviceLinkPartialState.Failure(e.localizedMessage ?: "Unknown error"))
        }
    }

    override suspend fun linkDevice(linkingCode: String, fcmToken: String?): Result<LinkedDeviceInfo> {
        val result = actionsRepository.linkDevice(linkingCode, fcmToken)
        result.onSuccess { deviceInfo ->
            cachedDeviceInfo = deviceInfo
        }
        return result
    }

    override suspend fun unlinkDevice(): Result<Unit> {
        val deviceId = cachedDeviceInfo?.deviceId
            ?: return Result.failure(IllegalStateException("No device linked"))

        // Store previous state for rollback on failure (optimistic update pattern)
        val previousDeviceInfo = cachedDeviceInfo

        // Clear cache optimistically
        cachedDeviceInfo = null

        val result = actionsRepository.unlinkDevice(deviceId)
        result.onFailure {
            // Restore cache on failure
            cachedDeviceInfo = previousDeviceInfo
        }
        return result
    }

    override fun getActionById(actionId: String): ActionRequest? {
        return cachedActions.find { it.id == actionId }
    }

    private fun groupActionsByCategory(actions: List<ActionUi>): List<Pair<ActionCategoryUi, List<ActionUi>>> {
        val pendingActions = actions.filter { it.status == ActionStatus.PENDING }
        val completedActions = actions.filter { it.status != ActionStatus.PENDING }

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

        return grouped
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
        return action.relativeTime.contains("hour") ||
               action.relativeTime.contains("minute") ||
               action.relativeTime.contains("just now")
    }

    private fun isThisWeek(action: ActionUi): Boolean {
        return action.relativeTime.contains("day") || isToday(action)
    }
}
