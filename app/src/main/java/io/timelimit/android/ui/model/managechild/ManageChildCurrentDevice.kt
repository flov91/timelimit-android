/*
 * TimeLimit Copyright <C> 2019 - 2026 Jonas Lochmann
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

package io.timelimit.android.ui.model.managechild

import androidx.compose.material.SnackbarHostState
import androidx.lifecycle.asFlow
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.data.model.HintsToShow
import io.timelimit.android.data.model.User
import io.timelimit.android.extensions.tryWithLock
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.CurrentDeviceLogic
import io.timelimit.android.sync.actions.SetRelaxPrimaryDeviceAction
import io.timelimit.android.sync.actions.apply.ActionExecutionInfo
import io.timelimit.android.sync.actions.apply.ApplyActionUtil
import io.timelimit.android.sync.network.UpdatePrimaryDeviceRequest
import io.timelimit.android.sync.network.UpdatePrimaryDeviceRequestType
import io.timelimit.android.sync.network.UpdatePrimaryDeviceResponseType
import io.timelimit.android.ui.model.ActivityCommand
import io.timelimit.android.ui.model.AuthenticationModelApi
import io.timelimit.android.ui.model.BackStackItem
import io.timelimit.android.ui.model.Screen
import io.timelimit.android.ui.model.State
import io.timelimit.android.ui.model.intro.IntroHandling
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

object ManageChildCurrentDevice {
    fun processCurrentDeviceState(
        logic: AppLogic,
        activityCommand: SendChannel<ActivityCommand>,
        authentication: AuthenticationModelApi,
        stateLive: Flow<State.ManageChild.AdvancedCurrentDevice>,
        parentBackStackLive: Flow<List<BackStackItem>>,
        userLive: Flow<User>,
        scope: CoroutineScope
    ): Flow<Screen> = flow {
        val snackbarHostState = SnackbarHostState()
        var lastJob: Job? = null
        val transitionLiveMutex = Mutex()
        val transitionLive = MutableStateFlow(null as Content.ActiveUser.Transition?)

        fun launch(action: suspend () -> Unit) {
            lastJob?.cancel()

            lastJob = scope.launch {
                try {
                    action()
                } catch (ex: ErrorToastException) {
                    snackbarHostState.showSnackbar(logic.context.getString(ex.messageId))
                } catch (_: Exception) {
                    snackbarHostState.showSnackbar(logic.context.getString(R.string.error_general))
                }
            }
        }

        val devicesLive = logic.database.device().getAllDevicesFlow()

        val currentDeviceLive = userLive.combine(devicesLive) { user, devices ->
            devices.firstOrNull { it.id == user.currentDevice }
        }

        emitAll(combine(
            combine(
                stateLive,
                parentBackStackLive,
                logic.database.derivedDataDao().getUserAndDeviceRelatedDataLive().asFlow().filterNotNull(),
            ) { a, b, c -> Triple(a, b, c) },
            combine(
                logic.currentDeviceLogic.borrowedCurrentDevice,
                userLive,
                IntroHandling.handle(logic, HintsToShow.CURRENT_DEVICE),
            ) { a, b, c -> Triple(a, b, c) },
            transitionLive,
            currentDeviceLive
        ) { (state, backStack, userAndDeviceRelatedData), (borrowedCurrentDevice, user, intro), transition, currentDevice ->
            val status = CurrentDeviceLogic.handleDeviceAsCurrentDevice(
                userAndDeviceRelatedData,
                borrowedCurrentDevice
            )

            val content = if (status is CurrentDeviceLogic.HandleAsCurrentDevice.Yes.LocalMode) {
                Content.LocalMode
            } else if (user.id == userAndDeviceRelatedData.userRelatedData?.user?.id) {
                Content.ActiveUser(
                    mode = when (status) {
                        is CurrentDeviceLogic.HandleAsCurrentDevice.Yes.PrimaryDevice -> Content.ActiveUser.Mode.PrimaryDevice
                        is CurrentDeviceLogic.HandleAsCurrentDevice.Yes.BorrowedCurrentDevice -> Content.ActiveUser.Mode.SecondaryDevice
                        is CurrentDeviceLogic.HandleAsCurrentDevice.No -> Content.ActiveUser.Mode.OtherDevice
                        CurrentDeviceLogic.HandleAsCurrentDevice.Yes.RelaxedCurrentDevice -> Content.ActiveUser.Mode.RelaxedPrimaryDevice
                        CurrentDeviceLogic.HandleAsCurrentDevice.Yes.LocalMode -> {
                            // case already handled above

                            throw IllegalStateException()
                        }
                    },
                    transition = transition,
                    actions = Content.ActiveUser.Actions(
                        makePrimary = {
                            launch {
                                transitionLiveMutex.tryWithLock {
                                    logic.currentDeviceLogic.cancelBorrowRequest()

                                    val deRelaxAuthentication = if (userAndDeviceRelatedData.userRelatedData.user.relaxPrimaryDevice) {
                                        authentication.doParentAuthentication()?.authentication ?: return@tryWithLock
                                    } else null

                                    transitionLive.value = Content.ActiveUser.Transition.ConvertToPrimary.SendingRequest

                                    try {
                                        val server = logic.serverLogic.getServerConfigCoroutine()

                                        while (true) {
                                            val response = try {
                                                server.api.updatePrimaryDevice(
                                                    UpdatePrimaryDeviceRequest(
                                                        action = UpdatePrimaryDeviceRequestType.SetThisDevice,
                                                        currentUserId = user.id,
                                                        deviceAuthToken = server.deviceAuthToken
                                                    )
                                                )
                                            } catch (_: IOException) {
                                                throw ErrorToastException(R.string.error_network)
                                            }

                                            when (response.status) {
                                                UpdatePrimaryDeviceResponseType.Success -> {
                                                    transitionLive.value = Content.ActiveUser.Transition.ConvertToPrimary.WaitingForSync

                                                    // the server does not trigger a sync in this case, so do it manually
                                                    logic.syncUtil.requestImportantSyncAndWait()

                                                    // check the result
                                                    val updatedUser = logic.database.user().getUserByIdFlow(userAndDeviceRelatedData.userRelatedData.user.id).first()

                                                    if (updatedUser == null || updatedUser.currentDevice != userAndDeviceRelatedData.deviceRelatedData.deviceEntry.id) {
                                                        throw IllegalStateException()
                                                    }

                                                    // disable borrowing
                                                    logic.currentDeviceLogic.dropBorrow()

                                                    // disable relaxing if it was enabled
                                                    deRelaxAuthentication?.let {
                                                        ApplyActionUtil.applyParentAction(
                                                            SetRelaxPrimaryDeviceAction(
                                                                userId = user.id,
                                                                relax = false
                                                            ),
                                                            it,
                                                            logic
                                                        )
                                                    }

                                                    // do not try again
                                                    break
                                                }
                                                UpdatePrimaryDeviceResponseType.AssignedToOtherDevice -> {
                                                    transitionLive.value = Content.ActiveUser.Transition.ConvertToPrimary.SendingSignOutRequest

                                                    val currentDevice = currentDeviceLive.first() ?: throw IllegalStateException()

                                                    server.api.requestSignOutAtPrimaryDevice(server.deviceAuthToken)

                                                    transitionLive.value = Content.ActiveUser.Transition.ConvertToPrimary.WaitingForSignOutAtOtherDevice(currentDevice.name)

                                                    withTimeoutOrNull(1000 * 10) {
                                                        currentDeviceLive.filter { it == null }.first()
                                                    }
                                                }
                                                UpdatePrimaryDeviceResponseType.RequiresFullVersion -> {
                                                    activityCommand.trySend(ActivityCommand.ShowMissingPremiumDialog)

                                                    break
                                                }
                                                UpdatePrimaryDeviceResponseType.UnknownError -> throw IllegalStateException()
                                            }
                                        }
                                    } finally {
                                        transitionLive.value = null
                                    }
                                }
                            }
                        },
                        makeSecondary = {
                            launch {
                                transitionLiveMutex.tryWithLock {
                                    if (logic.fullVersion.shouldProvideFullVersionFunctions()) {
                                        if (currentDevice == null) {
                                            throw ErrorToastException(R.string.current_device_error_missing_primary)
                                        } else if (currentDevice.id == userAndDeviceRelatedData.deviceRelatedData.deviceEntry.id) {
                                            throw ErrorToastException(R.string.current_device_error_is_primary)
                                        } else {
                                            val deRelaxAuthentication = if (userAndDeviceRelatedData.userRelatedData.user.relaxPrimaryDevice) {
                                                authentication.doParentAuthentication()?.authentication ?: return@tryWithLock
                                            } else null

                                            val (request, pingAction) = logic.currentDeviceLogic.requestBorrow(currentDevice.id)

                                            val sequenceNumber =
                                                if (pingAction is ActionExecutionInfo.Enqueued) pingAction.sequenceNumber
                                                else throw IllegalStateException()

                                            transitionLive.value = Content.ActiveUser.Transition.ConvertToSecondary.SendingPing

                                            try {
                                                val isPingPending =
                                                    logic.database.pendingSyncAction()
                                                        .getPendingSyncActionBySequenceNumberFlow(
                                                            sequenceNumber
                                                        ).map { it != null }

                                                isPingPending.combine(logic.currentDeviceLogic.borrowedCurrentDevice) { a, b ->
                                                    Pair(
                                                        a,
                                                        b
                                                    )
                                                }
                                                    .collect { (pingPending, status) ->
                                                        if (!pingPending) {
                                                            // ping was sent
                                                            transitionLive.value =
                                                                Content.ActiveUser.Transition.ConvertToSecondary.WaitingForReply(
                                                                    currentDevice.name
                                                                )
                                                        }

                                                        if (status == request) {
                                                            // waiting
                                                        } else if (status?.deviceId == request.deviceId && status.since != null) {
                                                            // disable relaxing if it was enabled
                                                            deRelaxAuthentication?.let {
                                                                ApplyActionUtil.applyParentAction(
                                                                    SetRelaxPrimaryDeviceAction(
                                                                        userId = user.id,
                                                                        relax = false
                                                                    ),
                                                                    it,
                                                                    logic
                                                                )
                                                            }

                                                            // we are done
                                                            throw DoneException()
                                                        } else {
                                                            // failure
                                                            throw IllegalStateException()
                                                        }
                                                    }
                                            } catch (_ : DoneException) {
                                                // control flow workaround; nothing to do here
                                            } finally {
                                                transitionLive.value = null
                                            }
                                        }
                                    } else {
                                        activityCommand.trySend(ActivityCommand.ShowMissingPremiumDialog)
                                    }
                                }
                            }
                        },
                        makeOther = {
                            launch {
                                transitionLiveMutex.tryWithLock {
                                    logic.currentDeviceLogic.cancelBorrowRequest()

                                    val deRelaxAuthentication = if (userAndDeviceRelatedData.userRelatedData.user.relaxPrimaryDevice) {
                                        authentication.doParentAuthentication()?.authentication ?: return@tryWithLock
                                    } else null

                                    transitionLive.value = Content.ActiveUser.Transition.ConvertToOther.WaitingForSync

                                    try {
                                        // if not the current device, skip some steps
                                        if (user.currentDevice == userAndDeviceRelatedData.deviceRelatedData.deviceEntry.id) {
                                            logic.syncUtil.requestImportantSyncAndWait()

                                            transitionLive.value =
                                                Content.ActiveUser.Transition.ConvertToOther.SendingRequest

                                            // send request
                                            val response = try {
                                                val server =
                                                    logic.serverLogic.getServerConfigCoroutine()

                                                server.api.updatePrimaryDevice(
                                                    UpdatePrimaryDeviceRequest(
                                                        action = UpdatePrimaryDeviceRequestType.UnsetThisDevice,
                                                        currentUserId = user.id,
                                                        deviceAuthToken = server.deviceAuthToken
                                                    )
                                                )
                                            } catch (_: IOException) {
                                                throw ErrorToastException(R.string.error_network)
                                            }

                                            if (response.status != UpdatePrimaryDeviceResponseType.Success) {
                                                throw IllegalStateException()
                                            }

                                            // adjust in database
                                            Threads.database.executeAndWait {
                                                logic.database.runInTransaction {
                                                    logic.database.user().updateUserSync(
                                                        logic.database.user()
                                                            .getUserByIdSync(user.id)!!
                                                            .copy(currentDevice = "")
                                                    )
                                                }
                                            }
                                        }

                                        // disable borrowing
                                        logic.currentDeviceLogic.dropBorrow()

                                        // disable relaxing if it was enabled
                                        deRelaxAuthentication?.let {
                                            ApplyActionUtil.applyParentAction(
                                                SetRelaxPrimaryDeviceAction(
                                                    userId = user.id,
                                                    relax = false
                                                ),
                                                it,
                                                logic
                                            )
                                        }
                                    } finally {
                                        transitionLive.value = null
                                    }
                                }
                            }
                        },
                        makeRelaxed = {
                            launch {
                                if (logic.fullVersion.shouldProvideFullVersionFunctions()) {
                                    authentication.doParentAuthentication()?.let {
                                        ApplyActionUtil.applyParentAction(
                                            SetRelaxPrimaryDeviceAction(
                                                userId = user.id,
                                                relax = true
                                            ),
                                            it.authentication,
                                            logic
                                        )
                                    }
                                } else {
                                    activityCommand.trySend(ActivityCommand.ShowMissingPremiumDialog)
                                }
                            }
                        },
                    )
                )
            } else {
                Content.InactiveUser(
                    relaxed = user.relaxPrimaryDevice,
                    toggle = {
                        launch {
                            authentication.doParentAuthentication()?.let { parent ->
                                ApplyActionUtil.applyParentAction(
                                    SetRelaxPrimaryDeviceAction(
                                        userId = user.id,
                                        relax = it,
                                    ),
                                    parent.authentication,
                                    logic
                                )
                            }
                        }
                    }
                )
            }

            Screen.ManageChildCurrentDeviceScreen(state, content, intro, backStack, snackbarHostState)
        })
    }

    suspend fun unsetCurrentDeviceInBackground(logic: AppLogic) {
        val userAndDeviceRelatedData = Threads.database.executeAndWait {
            logic.database.derivedDataDao().getUserAndDeviceRelatedDataSync()
        }!!

        val user = userAndDeviceRelatedData.userRelatedData!!.user

        if (user.currentDevice != userAndDeviceRelatedData.deviceRelatedData.deviceEntry.id) throw IllegalStateException()

        logic.syncUtil.requestImportantSyncAndWait()

        val server = logic.serverLogic.getServerConfigCoroutine()

        val response = server.api.updatePrimaryDevice(
            UpdatePrimaryDeviceRequest(
                action = UpdatePrimaryDeviceRequestType.UnsetThisDevice,
                currentUserId = user.id,
                deviceAuthToken = server.deviceAuthToken
            )
        )

        if (response.status != UpdatePrimaryDeviceResponseType.Success) {
            throw IllegalStateException()
        }

        // adjust in database
        Threads.database.executeAndWait {
            logic.database.runInTransaction {
                logic.database.user().updateUserSync(
                    logic.database.user()
                        .getUserByIdSync(user.id)!!
                        .copy(currentDevice = "")
                )
            }
        }
    }

    sealed class Content {
        object LocalMode: Content()
        class InactiveUser(
            val relaxed: Boolean,
            val toggle: (Boolean) -> Unit
        ): Content()
        class ActiveUser(
            val mode: Mode,
            val transition: Transition?,
            val actions: Actions
        ): Content() {
            data class Actions(
                val makePrimary: () -> Unit,
                val makeSecondary: () -> Unit,
                val makeOther: () -> Unit,
                val makeRelaxed: () -> Unit
            )

            enum class Mode {
                PrimaryDevice,
                SecondaryDevice,
                OtherDevice,
                RelaxedPrimaryDevice,
            }

            sealed class Transition {
                sealed class ConvertToPrimary: Transition() {
                    object SendingRequest: ConvertToPrimary()
                    object WaitingForSync: ConvertToPrimary()
                    object SendingSignOutRequest: ConvertToPrimary()
                    data class WaitingForSignOutAtOtherDevice(val deviceName: String): ConvertToPrimary()
                }
                sealed class ConvertToSecondary: Transition() {
                    object SendingPing: ConvertToSecondary()
                    data class WaitingForReply(val deviceName: String): ConvertToSecondary()
                }
                sealed class ConvertToOther: Transition() {
                    object WaitingForSync: ConvertToOther()
                    object SendingRequest: ConvertToOther()
                }
            }
        }
    }

    internal class ErrorToastException(val messageId: Int): Exception()
    internal class DoneException: Exception()
}