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
package io.timelimit.android.ui.model.managedevice

import androidx.compose.material.SnackbarHostState
import androidx.compose.material.SnackbarResult
import androidx.lifecycle.asFlow
import io.timelimit.android.R
import io.timelimit.android.data.model.Device
import io.timelimit.android.data.model.UserType
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.sync.actions.SetDeviceDefaultUserAction
import io.timelimit.android.sync.actions.SetDeviceDefaultUserTimeoutAction
import io.timelimit.android.sync.actions.SetDeviceUserAction
import io.timelimit.android.sync.actions.SignOutAtDeviceAction
import io.timelimit.android.sync.actions.apply.ApplyActionUtil
import io.timelimit.android.ui.model.*
import io.timelimit.android.ui.model.flow.Case
import io.timelimit.android.ui.model.flow.splitConflated
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

object ManageDeviceUser {
    data class UserItem(
        val id: String,
        val type: UserType,
        val name: String,
        val selected: Boolean,
        val defaultUser: DefaultUser
    ) {
        sealed class DefaultUser {
            object No: DefaultUser()
            data class Yes(val timeout: Int): DefaultUser()
        }
    }

    data class Actions(
        val select: (UserItem) -> Unit,
        val makeDefaultUser: (UserItem) -> Unit,
        val disableDefaultUser: () -> Unit,
        val configureAutoSwitching: () -> Unit
    )

    sealed class Overlay {
        data class EnableDefaultUser(
            val userTitle: String,
            val confirm: () -> Unit,
            val cancel: () -> Unit
        ): Overlay()

        data class ConfigureTimeout(
            val currentValue: Int,
            val confirm: (Int) -> Unit,
            val cancel: () -> Unit
        ): Overlay()
    }

    fun processUserState(
        logic: AppLogic,
        scope: CoroutineScope,
        activityCommand: SendChannel<ActivityCommand>,
        authentication: AuthenticationModelApi,
        stateLive: SharedFlow<State.ManageDevice.User>,
        parentBackStackLive: Flow<List<BackStackItem>>,
        deviceLive: Flow<Device>,
        updateState: ((State.ManageDevice.User) -> State) -> Unit
    ): Flow<Screen> {
        val snackbar = SnackbarHostState()

        fun launch(action: suspend () -> Unit) {
            scope.launch {
                try {
                    action()
                } catch (ex: Exception) {
                    snackbar.showSnackbar(logic.context.getString(R.string.error_general))
                }
            }
        }

        val actions = Actions(
            select = { user -> launch {
                if (user.selected) {
                    val result = snackbar.showSnackbar(
                        logic.context.getString(R.string.manage_device_user_already_selected_text),
                        logic.context.getString(R.string.manage_device_user_already_selected_action)
                    )

                    if (result == SnackbarResult.ActionPerformed) {
                        val userEntry = logic.database.user().getUserByIdFlow(user.id).first()!!

                        when (userEntry.type) {
                            UserType.Child -> {
                                if (userEntry.restrictViewingToParents) {
                                    val currentUserId = logic.deviceEntry.asFlow().first()?.currentUserId

                                    if (currentUserId != userEntry.id) {
                                        authentication.doParentAuthentication() ?: return@launch
                                    }
                                }

                                updateState { state ->
                                    State.ManageChild.Main(state.previousOverview, userEntry.id)
                                }
                            }
                            UserType.Parent -> updateState { state ->
                                State.ManageParent.Main(state.previousOverview, userEntry.id)
                            }
                        }
                    }
                } else if (
                    user.defaultUser is UserItem.DefaultUser.Yes &&
                    logic.fullVersion.shouldProvideFullVersionFunctions() &&
                    deviceLive.first().id == logic.database.config().getOwnDeviceIdFlow().first()
                ) {
                    ApplyActionUtil.applyAppLogicAction(
                        SignOutAtDeviceAction,
                        logic,
                        false
                    )
                } else {
                    val parent = authentication.authenticatedParentOnly.firstOrNull()

                    if (parent != null) {
                        ApplyActionUtil.applyParentAction(
                            SetDeviceUserAction(
                                deviceId = deviceLive.first().id,
                                userId = user.id
                            ),
                            parent.authentication,
                            logic
                        )
                    } else authentication.triggerAuthenticationScreen()
                }
            } },
            makeDefaultUser = { user -> launch {
                if (authentication.doParentAuthentication() != null) {
                    updateState { it.copy(overlay = State.ManageDevice.User.Overlay.EnableDefaultUserDialog(user.id)) }
                }
            } },
            disableDefaultUser = { launch {
                val parent = authentication.authenticatedParentOnly.firstOrNull()

                if (parent != null) {
                    val currentDefaultUser = deviceLive.first().defaultUser

                    ApplyActionUtil.applyParentAction(
                        SetDeviceDefaultUserAction(
                            deviceId = deviceLive.first().id,
                            defaultUserId = ""
                        ),
                        parent.authentication,
                        logic
                    )

                    val result = snackbar.showSnackbar(
                        logic.context.getString(R.string.manage_device_user_disable_default_user_toast),
                        logic.context.getString(R.string.generic_undo)
                    )

                    if (result == SnackbarResult.ActionPerformed) {
                        ApplyActionUtil.applyParentAction(
                            SetDeviceDefaultUserAction(
                                deviceId = deviceLive.first().id,
                                defaultUserId = currentDefaultUser
                            ),
                            parent.authentication,
                            logic
                        )
                    }
                } else authentication.triggerAuthenticationScreen()
            } },
            configureAutoSwitching = { launch {
                if (authentication.doParentAuthentication() != null) {
                    updateState { it.copy(overlay = State.ManageDevice.User.Overlay.AdjustDefaultUserTimeout) }
                }
            } }
        )

        val usersLive = listUsers(logic, deviceLive)

        val overlayLive: Flow<Overlay?> = stateLive
            .map { it.overlay }
            .splitConflated(
                Case.withKey<_, _, State.ManageDevice.User.Overlay.EnableDefaultUserDialog, _>(
                    withKey = { it.userId },
                    producer = { userId, _ ->
                        val userLive = logic.database.user().getUserByIdFlow(userId)
                        val closeOverlay = { updateState {
                            if (it.overlay is State.ManageDevice.User.Overlay.EnableDefaultUserDialog) it.copy(overlay = null)
                            else it
                        } }

                        userLive.transformLatest { user ->
                            if (user == null) closeOverlay()
                            else emit(Overlay.EnableDefaultUser(
                                userTitle = user.name,
                                cancel = closeOverlay,
                                confirm = { launch {
                                    closeOverlay()

                                    if (logic.fullVersion.shouldProvideFullVersionFunctions()) authentication.authenticatedParentOnly.first()!!.let { parent ->
                                        ApplyActionUtil.applyParentAction(
                                            SetDeviceDefaultUserAction(
                                                deviceId = deviceLive.first().id,
                                                defaultUserId = userId
                                            ),
                                            parent.authentication,
                                            logic
                                        )

                                        snackbar.showSnackbar(logic.context.getString(R.string.manage_device_user_make_default_user_toast))
                                    }
                                    else activityCommand.send(ActivityCommand.ShowMissingPremiumDialog)
                                } }
                            ))
                        }
                    }
                ),
                Case.simple<_, _, State.ManageDevice.User.Overlay.AdjustDefaultUserTimeout> {
                    val closeOverlay = { updateState {
                        if (it.overlay is State.ManageDevice.User.Overlay.AdjustDefaultUserTimeout) it.copy(overlay = null)
                        else it
                    } }

                    deviceLive.transform { device ->
                        if (device.defaultUser == "") closeOverlay()
                        else emit(Overlay.ConfigureTimeout(
                            currentValue = device.defaultUserTimeout,
                            cancel = closeOverlay,
                            confirm = { newValue -> launch {
                                closeOverlay()

                                if (logic.fullVersion.shouldProvideFullVersionFunctions()) authentication.authenticatedParentOnly.first()!!.let { parent ->
                                    ApplyActionUtil.applyParentAction(
                                        SetDeviceDefaultUserTimeoutAction(
                                            deviceId = deviceLive.first().id,
                                            timeout = newValue
                                        ),
                                        parent.authentication,
                                        logic
                                    )
                                }
                                else activityCommand.send(ActivityCommand.ShowMissingPremiumDialog)
                            } }
                        ))
                    }
                },
                Case.nil { flowOf(null) }
            )

        return combine(stateLive, parentBackStackLive, usersLive, overlayLive) { state, parentBackStack, users, overlay ->
            Screen.ManageDeviceUserScreen(
                state,
                parentBackStack,
                snackbar,
                users,
                actions,
                overlay
            )
        }
    }

    private fun listUsers(
        logic: AppLogic,
        deviceLive: Flow<Device>
    ): Flow<List<UserItem>> {
        val usersLive = logic.database.user().getAllUsersFlow()

        return combine(usersLive, deviceLive) { users, device ->
            users.map { user ->
                val selected = device.currentUserId == user.id
                val defaultUser =
                    if (device.defaultUser == user.id) UserItem.DefaultUser.Yes(
                        timeout = device.defaultUserTimeout
                    )
                    else UserItem.DefaultUser.No

                UserItem(
                    id = user.id,
                    type = user.type,
                    name = user.name,
                    selected = selected,
                    defaultUser = defaultUser
                )
            }.sortedBy { when (it.defaultUser) {
                is UserItem.DefaultUser.Yes -> 0
                UserItem.DefaultUser.No -> 1
            } }
        }
    }
}