/*
 * TimeLimit Copyright <C> 2019 - 2023 Jonas Lochmann
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.timelimit.android.ui.model.main

import androidx.compose.material.SnackbarHostState
import androidx.lifecycle.asFlow
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.data.extensions.getTimezone
import io.timelimit.android.data.model.Device
import io.timelimit.android.data.model.HintsToShow
import io.timelimit.android.data.model.UserType
import io.timelimit.android.data.model.derived.FullChildTask
import io.timelimit.android.date.DateInTimezone
import io.timelimit.android.extensions.tryWithLock
import io.timelimit.android.extensions.whileTrue
import io.timelimit.android.integration.platform.RuntimePermissionStatus
import io.timelimit.android.livedata.map
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.sync.actions.ReviewChildTaskAction
import io.timelimit.android.sync.actions.apply.ApplyActionUtil
import io.timelimit.android.ui.model.ActivityCommand
import io.timelimit.android.ui.model.AuthenticationModelApi
import io.timelimit.android.ui.model.Screen
import io.timelimit.android.ui.model.State
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import java.util.*
import kotlin.coroutines.CoroutineContext

object OverviewHandling {
    fun processState(
        logic: AppLogic,
        scope: CoroutineScope,
        activityCommand: SendChannel<ActivityCommand>,
        authentication: AuthenticationModelApi,
        stateLive: MutableStateFlow<State>
    ): Flow<Screen> {
        val snackbarHostState = SnackbarHostState()
        val actions: Actions = getActions(logic, scope, activityCommand, authentication, stateLive, snackbarHostState)
        val overviewStateLive: Flow<OverviewState> = stateLive.transform { if (it is State.Overview) emit(it.state) }
        val overviewState2Live: Flow<State.Overview> = stateLive.transform { if (it is State.Overview) emit(it) }
        val overviewScreenLive: Flow<OverviewScreen> = getScreen(logic, actions, overviewStateLive)
        val hasMatchingStateLive = stateLive.map { it is State.Overview }.distinctUntilChanged()

        return hasMatchingStateLive.whileTrue {
            overviewState2Live.combine(overviewScreenLive) { state, overviewScreen ->
                Screen.OverviewScreen(state, overviewScreen, snackbarHostState)
            }
        }
    }

    private fun getActions(
        logic: AppLogic,
        scope: CoroutineScope,
        activityCommand: SendChannel<ActivityCommand>,
        authentication: AuthenticationModelApi,
        stateLive: MutableStateFlow<State>,
        snackbarHostState: SnackbarHostState
    ): Actions {
        val lock = Mutex()

        fun launch(action: suspend () -> Unit) {
            scope.launch {
                try {
                    action()
                } catch (ex: Exception) {
                    snackbarHostState.showSnackbar(logic.context.getString(R.string.error_general))
                }
            }
        }

        return Actions(
            hideIntro = {
                launch {
                    Threads.database.executeAndWait {
                        logic.database.config().setHintsShownSync(HintsToShow.OVERVIEW_INTRODUCTION)
                    }
                }
            },
            addDevice = {
                launch {
                    lock.tryWithLock {
                        val isLocalMode = Threads.database.executeAndWait {
                            logic.database.config().getDeviceAuthTokenSync().isEmpty()
                        }

                        if (isLocalMode) activityCommand.send(ActivityCommand.ShowCanNotAddDevicesInLocalModeDialogFragment)
                        else if (authentication.doParentAuthentication() != null) activityCommand.send(ActivityCommand.ShowAddDeviceFragment)
                    }
                }
            },
            skipTaskReview = { task ->
                launch {
                    lock.tryWithLock {
                        if (authentication.doParentAuthentication() != null) {
                            stateLive.update { oldState ->
                                if (oldState is State.Overview) oldState.copy(
                                    state = oldState.state.copy(
                                        hiddenTaskIds = oldState.state.hiddenTaskIds + task.task.childTask.taskId
                                    )
                                )
                                else oldState
                            }
                        }
                    }
                }
            },
            reviewReject = { task ->
                launch {
                    lock.tryWithLock {
                        authentication.doParentAuthentication()?.let { parent ->
                            ApplyActionUtil.applyParentAction(
                                ReviewChildTaskAction(
                                    taskId = task.task.childTask.taskId,
                                    ok = false,
                                    time = logic.timeApi.getCurrentTimeInMillis(),
                                    day = null
                                ),
                                parent.authentication,
                                logic
                            )
                        }
                    }
                }
            },
            reviewAccept = { task ->
                launch {
                    val parent = authentication.authenticatedParentOnly.first()
                    val hasPremium = logic.fullVersion.shouldProvideFullVersionFunctions()

                    if (parent == null) authentication.doParentAuthentication()
                    else if (!hasPremium) activityCommand.send(ActivityCommand.ShowMissingPremiumDialog)
                    else {
                        val serverApiLevel = logic.serverApiLevelLogic.getCoroutine()
                        val child = logic.database.user().getChildUserByIdCoroutine(task.task.childId)
                        val time = logic.timeApi.getCurrentTimeInMillis()
                        val day = DateInTimezone.newInstance(time, child.getTimezone()).dayOfEpoch

                        ApplyActionUtil.applyParentAction(
                            ReviewChildTaskAction(
                                taskId = task.task.childTask.taskId,
                                ok = true,
                                time = time,
                                day = if (serverApiLevel.hasLevelOrIsOffline(2)) day else null
                            ),
                            parent.authentication,
                            logic
                        )
                    }
                }
            }
        )
    }

    private fun getScreen(logic: AppLogic, actions: Actions, state: Flow<OverviewState>): Flow<OverviewScreen> {
        val introLive = getIntroFlags(logic)
        val taskToReviewLive = getTaskToReview(logic, state.map { it.hiddenTaskIds })
        val usersLive = getUserList(logic, state.map { it.showAllUsers })
        val devicesLive = getDeviceList(logic, state.map { it.visibleDevices })

        return combine(
            introLive, taskToReviewLive, usersLive, devicesLive
        ) { intro, taskToReview, users, devices ->
            OverviewScreen(
                intro = intro,
                taskToReview = taskToReview,
                users = users,
                devices = devices,
                actions = actions
            )
        }
    }

    private fun getUsers(logic: AppLogic): Flow<List<UserItem>> {
        val userFlow = logic.database.user().getAllUsersFlow()

        val timeFlow = flow {
            while (true) {
                emit(logic.realTimeLogic.getCurrentTimeInMillis())
                delay(5000)
            }
        }

        return userFlow.combine(timeFlow) { users, time ->
            users.map { user ->
                UserItem(
                    id = user.id,
                    name = user.name,
                    type = user.type,
                    areLimitsTemporarilyDisabled = user.disableLimitsUntil >= time,
                    viewingNeedsAuthentication = user.restrictViewingToParents
                )
            }
        }.distinctUntilChanged()
    }

    private fun getUserList(logic: AppLogic, showAllUsersLive: Flow<Boolean>): Flow<UserList> {
        val usersLive = getUsers(logic)

        return usersLive.combine(showAllUsersLive) { users, showAllUsers ->
            if (showAllUsers || users.none { it.type != UserType.Parent }) UserList(
                list = users,
                canAdd = true,
                canShowMore = false
            )
            else UserList(
                list = users.filter { it.type == UserType.Child },
                canAdd = false,
                canShowMore = true
            )
        }
    }

    private fun getDevices(logic: AppLogic): Flow<List<DeviceItem>> {
        val ownDeviceIdLive = logic.database.config().getOwnDeviceIdFlow()
        val devicesWithUserNameLive = logic.database.device().getAllDevicesWithUserInfoFlow()
        val connectedDevicesLive = logic.websocket.connectedDevices.asFlow()

        return combine (
            ownDeviceIdLive, devicesWithUserNameLive, connectedDevicesLive
        ) { ownDeviceId, devicesWithUserName, connectedDevices ->
            devicesWithUserName.map { deviceWithUserName ->
                DeviceItem(
                    device = deviceWithUserName.device,
                    userName = deviceWithUserName.currentUserName,
                    userType = deviceWithUserName.currentUserType,
                    isCurrentDevice = deviceWithUserName.device.id == ownDeviceId,
                    isConnected = connectedDevices.contains(deviceWithUserName.device.id)
                )
            }
        }
    }

    private fun getDeviceList(logic: AppLogic, deviceListLive: Flow<OverviewState.DeviceList>): Flow<DeviceList> {
        val devicesLive = getDevices(logic)

        return devicesLive.combine(deviceListLive) { allDevices, deviceList ->
            val bareMinimum = allDevices.filter { it.isCurrentDevice || it.device.isImportant }
            val allChildDevices = allDevices.filter { it.isCurrentDevice || it.device.isImportant || it.userType == UserType.Child }

            val list = when (deviceList) {
                OverviewState.DeviceList.BareMinimum -> bareMinimum
                OverviewState.DeviceList.AllChildDevices -> allChildDevices
                OverviewState.DeviceList.AllDevices -> allDevices
            }

            val canShowMore = when (deviceList) {
                OverviewState.DeviceList.BareMinimum ->
                    if (bareMinimum.size != allChildDevices.size) OverviewState.DeviceList.AllChildDevices
                    else if (bareMinimum.size != allDevices.size) OverviewState.DeviceList.AllDevices
                    else null
                OverviewState.DeviceList.AllChildDevices ->
                    if (allChildDevices.size != allDevices.size) OverviewState.DeviceList.AllDevices
                    else null
                OverviewState.DeviceList.AllDevices -> null
            }

            val canAdd = list.size == allDevices.size

            DeviceList(
                list = list,
                canAdd = canAdd,
                canShowMore = canShowMore
            )
        }
    }

    private fun getIntroFlags(logic: AppLogic): Flow<IntroFlags> {
        val showSetupOptionLive = logic.deviceUserEntry.map { it == null }.asFlow()

        val serverVersionLive = logic.serverApiLevelLogic.infoLive.asFlow()
        val showOutdatedServerLive = serverVersionLive.map { serverVersion ->
            !serverVersion.hasLevelOrIsOffline(BuildConfig.minimumRecommendServerVersion)
        }

        val showServerMessageLive = logic.database.config().getServerMessageFlow()

        val hasShownIntroductionLive = logic.database.config().wereHintsShown(HintsToShow.OVERVIEW_INTRODUCTION).asFlow()
        val showIntroLive = hasShownIntroductionLive.map { !it }

        return combine(
            showSetupOptionLive, showOutdatedServerLive, showServerMessageLive, showIntroLive
        ) { showSetupOption, showOutdatedServer, showServerMessage, showIntro ->
            IntroFlags(
                showSetupOption = showSetupOption,
                showOutdatedServer = showOutdatedServer,
                showServerMessage = showServerMessage,
                showIntro = showIntro
            )
        }
    }

    private fun getTaskToReview(logic: AppLogic, hiddenTaskIdsLive: Flow<Set<String>>): Flow<TaskToReview?> =
        logic.database.childTasks().getPendingTasksFlow().combine(hiddenTaskIdsLive) { pendingTasks, hiddenTaskIds ->
            pendingTasks
                .filterNot { hiddenTaskIds.contains(it.childTask.taskId) }
                .firstOrNull()
                ?.let { TaskToReview(it) }
        }

    data class OverviewState(
        val hiddenTaskIds: Set<String>,
        val visibleDevices: DeviceList,
        val showAllUsers: Boolean
    ): java.io.Serializable {
        companion object {
            val empty = OverviewState(
                hiddenTaskIds = emptySet(),
                visibleDevices = DeviceList.BareMinimum,
                showAllUsers = false
            )
        }

        enum class DeviceList {
            BareMinimum,    // current device + devices with warnings
            AllChildDevices,
            AllDevices
        }
    }

    data class OverviewScreen(
        val intro: IntroFlags,
        val taskToReview: TaskToReview?,
        val users: UserList,
        val devices: DeviceList,
        val actions: Actions
    )
    data class Actions(
        val hideIntro: () -> Unit,
        val addDevice: () -> Unit,
        val skipTaskReview: (TaskToReview) -> Unit,
        val reviewReject: (TaskToReview) -> Unit,
        val reviewAccept: (TaskToReview) -> Unit
    )
    data class IntroFlags(
        val showSetupOption: Boolean,
        val showOutdatedServer: Boolean,
        val showServerMessage: String?,
        val showIntro: Boolean
    )
    data class TaskToReview(val task: FullChildTask)
    data class UserItem(
        val id: String,
        val name: String,
        val type: UserType,
        val areLimitsTemporarilyDisabled: Boolean,
        val viewingNeedsAuthentication: Boolean
    )
    data class UserList(val list: List<UserItem>, val canAdd: Boolean, val canShowMore: Boolean)
    data class DeviceItem(val device: Device, val userName: String?, val userType: UserType?, val isCurrentDevice: Boolean, val isConnected: Boolean) {
        val isMissingRequiredPermission = userType == UserType.Child && (
                device.currentUsageStatsPermission == RuntimePermissionStatus.NotGranted || device.missingPermissionAtQOrLater)
    }
    data class DeviceList(val list: List<DeviceItem>, val canAdd: Boolean, val canShowMore: OverviewState.DeviceList?)
}