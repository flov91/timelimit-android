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

import io.timelimit.android.R
import io.timelimit.android.data.model.Device
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.ui.model.*
import io.timelimit.android.ui.model.flow.Case
import io.timelimit.android.ui.model.flow.splitConflated
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.*

object ManageDeviceHandling {
    fun processState(
        logic: AppLogic,
        activityCommand: SendChannel<ActivityCommand>,
        authentication: AuthenticationModelApi,
        state: Flow<State.ManageDevice>,
        updateState: ((State.ManageDevice) -> State) -> Unit
    ): Flow<Screen> = state.splitConflated(
        Case.withKey<_, _, State.ManageDevice, _>(
            withKey = { it.deviceId },
            producer = { deviceId, state2 ->
                val state3 = share(state2)
                val deviceLive = logic.database.device().getDeviceByIdFlow(deviceId)

                val hasDeviceLive = deviceLive.map { it != null }.distinctUntilChanged()
                val foundDeviceLive = deviceLive.filterNotNull()

                val baseBackStackLive = state3.map { state ->
                    listOf(
                        BackStackItem(
                            Title.StringResource(R.string.main_tab_overview)
                        ) { updateState { state.previousOverview } }
                    )
                }

                hasDeviceLive.transformLatest { hasDevice ->
                    if (hasDevice) emitAll(state3.splitConflated(
                        Case.simple<_, _, State.ManageDevice.Main> {
                            processMainState(
                                it,
                                baseBackStackLive,
                                foundDeviceLive
                            )
                        },
                        Case.simple<_, _, State.ManageDevice.Sub> {
                            processSubState(
                                logic,
                                activityCommand,
                                authentication,
                                it,
                                baseBackStackLive,
                                share(foundDeviceLive),
                                updateMethod(updateState)
                            )
                        },
                    ))
                    else updateState { it.previousOverview }
                }
            }
        )
    )

    private fun processMainState(
        stateLive: Flow<State.ManageDevice.Main>,
        parentBackStackLive: Flow<List<BackStackItem>>,
        deviceLive: Flow<Device>
    ): Flow<Screen> = combine(stateLive, deviceLive, parentBackStackLive) { state, device, backStack ->
        Screen.ManageDevice(
            state,
            state.toolbarIcons,
            state.toolbarOptions,
            state,
            R.id.fragment_manage_device_main,
            device.name,
            backStack
        )
    }

    private fun processSubState(
        logic: AppLogic,
        activityCommand: SendChannel<ActivityCommand>,
        authentication: AuthenticationModelApi,
        stateLive: Flow<State.ManageDevice.Sub>,
        parentBackStackLive: Flow<List<BackStackItem>>,
        deviceLive: SharedFlow<Device>,
        updateState: ((State.ManageDevice.Sub) -> State) -> Unit
    ): Flow<Screen> {
        val subBackStackLive = combine(parentBackStackLive, deviceLive) { baseBackStack, device ->
            baseBackStack + BackStackItem(Title.Plain(device.name)) { updateState { it.previousManageDeviceMain } }
        }

        return stateLive.splitConflated(
            Case.simple<_, _, State.ManageDevice.User> {
                ManageDeviceUser.processUserState(
                    logic,
                    scope,
                    activityCommand,
                    authentication,
                    share(it),
                    subBackStackLive,
                    deviceLive,
                    updateMethod(updateState)
                )
            },
            Case.simple<_, _, State.ManageDevice.Permissions> {
                processPermissionsState(
                    it,
                    subBackStackLive,
                    deviceLive
                )
            },
            Case.simple<_, _, State.ManageDevice.Features> {
                processFeaturesState(
                    it,
                    subBackStackLive,
                    deviceLive
                )
            },
            Case.simple<_, _, State.ManageDevice.Advanced> {
                processAdvancedState(
                    it,
                    subBackStackLive,
                    deviceLive
                )
            },
        )
    }

    private fun processPermissionsState(
        stateLive: Flow<State.ManageDevice.Permissions>,
        parentBackStackLive: Flow<List<BackStackItem>>,
        deviceLive: Flow<Device>
    ): Flow<Screen> = combine(stateLive, deviceLive, parentBackStackLive) { state, device, backStack ->
        Screen.ManageDevicePermissions(
            state,
            state.toolbarIcons,
            state.toolbarOptions,
            state,
            R.id.fragment_manage_device_permissions,
            backStack
        )
    }

    private fun processFeaturesState(
        stateLive: Flow<State.ManageDevice.Features>,
        parentBackStackLive: Flow<List<BackStackItem>>,
        deviceLive: Flow<Device>
    ): Flow<Screen> = combine(stateLive, deviceLive, parentBackStackLive) { state, device, backStack ->
        Screen.ManageDeviceFeatures(
            state,
            state.toolbarIcons,
            state.toolbarOptions,
            state,
            R.id.fragment_manage_device_features,
            backStack
        )
    }

    private fun processAdvancedState(
        stateLive: Flow<State.ManageDevice.Advanced>,
        parentBackStackLive: Flow<List<BackStackItem>>,
        deviceLive: Flow<Device>
    ): Flow<Screen> = combine(stateLive, deviceLive, parentBackStackLive) { state, device, backStack ->
        Screen.ManageDeviceAdvances(
            state,
            state.toolbarIcons,
            state.toolbarOptions,
            state,
            R.id.fragment_manage_device_advanced,
            backStack
        )
    }
}