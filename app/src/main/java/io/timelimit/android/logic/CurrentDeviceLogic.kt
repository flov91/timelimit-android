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
package io.timelimit.android.logic

import android.util.Log
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import io.timelimit.android.BuildConfig
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.coroutines.runAsyncExpectForever
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.data.model.Device
import io.timelimit.android.data.model.derived.DeviceAndUserRelatedData
import io.timelimit.android.livedata.*
import io.timelimit.android.logic.CurrentDeviceLogic.HandleAsCurrentDevice.No
import io.timelimit.android.sync.actions.PingAction
import io.timelimit.android.sync.actions.apply.ActionExecutionInfo
import io.timelimit.android.sync.actions.apply.ApplyActionUtil
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

class CurrentDeviceLogic(private val appLogic: AppLogic) {
    companion object {
        private const val LOG_TAG = "CurrentDeviceLogic"

        private const val BORROW_REPLY_TIMEOUT = 1000 * 60 // 1 minute
        private val BORROW_REFRESH_INTERVAL = 1000 * 60 * 2 .. 1000 * 60 * 4 // 2 to 4 minutes
        private const val BORROW_EXPIRE_TIMEOUT = 1000 * 60 * 5 // 5 minutes

        fun handleDeviceAsCurrentDevice(deviceAndUserRelatedData: DeviceAndUserRelatedData, borrowedPrimaryDevice: BorrowedCurrentDevice?): HandleAsCurrentDevice {
            if (deviceAndUserRelatedData.deviceRelatedData.isLocalMode) {
                return HandleAsCurrentDevice.Yes.LocalMode
            }

            val user = deviceAndUserRelatedData.userRelatedData?.user ?: return No.CurrentDeviceImpossible.NoUserAssigned

            if (user.id != deviceAndUserRelatedData.deviceRelatedData.deviceEntry.currentUserId) {
                return No.CurrentDeviceImpossible.WrongUserAssigned
            }

            if (deviceAndUserRelatedData.deviceRelatedData.isConnectedAndHasPremium && user.relaxPrimaryDevice) {
                return HandleAsCurrentDevice.Yes.RelaxedCurrentDevice
            }

            if (user.currentDevice == deviceAndUserRelatedData.deviceRelatedData.deviceEntry.id) {
                return HandleAsCurrentDevice.Yes.PrimaryDevice
            }

            if (
                deviceAndUserRelatedData.deviceRelatedData.isConnectedAndHasPremium &&
                borrowedPrimaryDevice != null &&
                borrowedPrimaryDevice.since != null &&
                user.currentDevice == borrowedPrimaryDevice.deviceId
            ) {
                return HandleAsCurrentDevice.Yes.BorrowedCurrentDevice
            }

            return No.NotPrimaryDevice
        }
    }

    sealed class HandleAsCurrentDevice {
        sealed class No: HandleAsCurrentDevice() {
            sealed class CurrentDeviceImpossible: No() {
                object NoUserAssigned: CurrentDeviceImpossible()
                object WrongUserAssigned: CurrentDeviceImpossible()
            }
            object NotPrimaryDevice: No()
        }
        sealed class Yes: HandleAsCurrentDevice() {
            object LocalMode: Yes()
            object PrimaryDevice: Yes()
            object RelaxedCurrentDevice: Yes()
            object BorrowedCurrentDevice: Yes()
        }
    }

    data class BorrowedCurrentDevice(
        val deviceId: String,
        val since: Long?,
        val tokenRequest: TokenRequest
    )

    sealed class TokenRequest {
        data class Scheduled(val at: Long): TokenRequest()
        data class Ongoing(val since: Long, val token: String): TokenRequest()
    }

    private val borrowedCurrentDeviceState = MutableStateFlow(null as BorrowedCurrentDevice?)
    val borrowedCurrentDevice = borrowedCurrentDeviceState.asStateFlow()

    private val userDeviceEntries = appLogic.deviceUserId.switchMap { deviceUserId ->
        if (deviceUserId == "") {
            liveDataFromNonNullValue(emptyList())
        } else {
            appLogic.database.device().getDevicesByUserId(deviceUserId)
        }
    }

    private val otherUserDeviceEntries = appLogic.deviceEntry.switchMap { ownDeviceEntry ->
        userDeviceEntries.map { devices ->
            devices.filterNot { device -> device.id == ownDeviceEntry?.id }
        }
    }

    val otherAssignedDevice = appLogic.deviceUserEntry.switchMap { userEntry ->
        if (userEntry?.currentDevice == null) {
            liveDataFromNullableValue(null as Device?)
        } else {
            otherUserDeviceEntries.map { otherDeviceEntries ->
                otherDeviceEntries.find { it.id == userEntry.currentDevice }
            }
        }
    }

    suspend fun requestBorrow(): String {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "request borrow")
        }

        val currentDeviceId = Threads.database.executeAndWait {
            appLogic.database.runInTransaction {
                val ownDeviceId = appLogic.database.config().getOwnDeviceIdSync()
                    ?: throw IllegalStateException()

                val ownDevice = appLogic.database.device().getDeviceByIdSync(ownDeviceId)
                    ?: throw IllegalStateException()

                val ownUser = appLogic.database.user().getUserByIdSync(ownDevice.currentUserId)
                    ?: throw IllegalStateException()

                if (ownUser.currentDevice == "") throw IllegalStateException()

                if (ownUser.currentDevice == ownDeviceId) throw IllegalStateException()

                val currentDevice =
                    appLogic.database.device().getDeviceByIdSync(ownUser.currentDevice)

                if (currentDevice == null || currentDevice.currentUserId != ownUser.id) throw IllegalStateException()

                currentDevice.id
            }
        }

        requestBorrow(currentDeviceId)

        return currentDeviceId
    }

    suspend fun requestBorrow(currentDeviceId: String): Pair<BorrowedCurrentDevice, ActionExecutionInfo> {
        val token = IdGenerator.generateId()

        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "ping $currentDeviceId with token $token")
        }

        val result = BorrowedCurrentDevice(
            deviceId = currentDeviceId,
            since = null,
            tokenRequest = TokenRequest.Ongoing(
                since = appLogic.timeApi.getCurrentUptimeInMillis(),
                token = token
            )
        )

        borrowedCurrentDeviceState.value = result

        val actionId = ApplyActionUtil.applyAppLogicAction(
            PingAction(
                deviceId = currentDeviceId,
                event = PingAction.Event.Ping,
                token = token
            ),
            appLogic,
            ignoreIfDeviceIsNotConfigured = false
        )

        return Pair(result, actionId)
    }

    fun handlePong(senderDeviceId: String, token: String) {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "got pong from $senderDeviceId with token $token")
        }

        val d = borrowedCurrentDeviceState.value
        val now = appLogic.timeApi.getCurrentUptimeInMillis()

        if (
            d?.deviceId == senderDeviceId &&
            d.tokenRequest is TokenRequest.Ongoing &&
            d.tokenRequest.token == token &&
            d.tokenRequest.since + BORROW_REPLY_TIMEOUT > now
        ) {
            val dNew = d.copy(
                since = d.tokenRequest.since,
                tokenRequest = TokenRequest.Scheduled(
                    d.tokenRequest.since + BORROW_REFRESH_INTERVAL.random()
                )
            )

            val updated = borrowedCurrentDeviceState.compareAndSet(d, dNew)

            if (!updated) {
                // concurrently updated

                return
            }

            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "validated pong; current device is $dNew")
            }
        }
    }

    fun eventuallyRefresh() {
        val d = borrowedCurrentDeviceState.value
        val now = appLogic.timeApi.getCurrentUptimeInMillis()

        // nothing to extend
        if (d?.since == null) return

        // already expired
        if (d.since + BORROW_EXPIRE_TIMEOUT <= now) return

        if (d.tokenRequest is TokenRequest.Scheduled && d.tokenRequest.at <= now) {
            val token = IdGenerator.generateId()

            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "refresh with token $token")
            }

            val updated = borrowedCurrentDeviceState.compareAndSet(
                d,
                d.copy(tokenRequest = TokenRequest.Ongoing(since = now, token = token))
            )

            if (!updated) {
                // concurrent modification in between

                return
            }

            runAsync {
                try {
                    ApplyActionUtil.applyAppLogicAction(
                        PingAction(
                            deviceId = d.deviceId,
                            event = PingAction.Event.Ping,
                            token = token
                        ),
                        appLogic,
                        ignoreIfDeviceIsNotConfigured = false
                    )
                } catch (_: Exception) {
                    // ignore the exception; the lease will expire soon
                }
            }
        }
    }

    fun cancelBorrowRequest() {
        borrowedCurrentDeviceState.update {
            if (it?.since == null) null
            else it
        }
    }

    fun dropBorrow() {
        borrowedCurrentDeviceState.value = null
    }

    init {
        val channel = Channel<Unit>(Channel.CONFLATED)

        runAsyncExpectForever {
            // generate change notifications
            borrowedCurrentDeviceState.collect { channel.send(Unit) }
        }

        runAsyncExpectForever {
            while (true) {
                val d = borrowedCurrentDeviceState.value

                val now = appLogic.timeApi.getCurrentUptimeInMillis()

                val requestTimeout = if (d?.tokenRequest is TokenRequest.Ongoing) {
                    val valid = d.tokenRequest.since <= now && d.tokenRequest.since + BORROW_REPLY_TIMEOUT > now

                    if (valid) d.tokenRequest.since + BORROW_REPLY_TIMEOUT - now
                    else 0
                } else null

                val responseTimeout = if (d?.since != null) {
                    val valid = d.since <= now && d.since + BORROW_EXPIRE_TIMEOUT > now

                    if (valid) d.since + BORROW_EXPIRE_TIMEOUT - now
                    else 0
                } else null

                val minTimeout = listOf(requestTimeout, responseTimeout).filterNotNull().minOrNull()

                if (minTimeout == null) {
                    // wait for the next value

                    channel.receive()
                } else if (minTimeout == 0L) {
                    // wipe the value, if it is still current
                    borrowedCurrentDeviceState.compareAndSet(d, null)

                    // and consume the change notification
                    channel.receive()
                } else {
                    // wait for the timeout or the next change notification
                    select {
                        channel.onReceive {/* value changed */}
                        launch { appLogic.timeApi.sleep(minTimeout) }.onJoin {/* timeout reached */}
                    }
                }
            }
        }
    }
}
