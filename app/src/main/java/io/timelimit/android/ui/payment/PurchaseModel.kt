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
package io.timelimit.android.ui.payment

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.livedata.castDown
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.apply.ApplyDirectCallAuthentication
import io.timelimit.android.sync.network.CanDoPurchaseStatus
import io.timelimit.android.sync.network.api.NotFoundHttpError
import io.timelimit.android.ui.main.ActivityViewModel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PurchaseModel(application: Application): AndroidViewModel(application) {
    private val logic: AppLogic by lazy { DefaultAppLogic.with(application) }
    private val statusInternal = MutableLiveData<Status>()
    private val lock = Mutex()
    private var auth: ActivityViewModel? = null

    val status = statusInternal.castDown()

    fun init(auth: ActivityViewModel) {
        this.auth = auth

        retry()
    }

    fun retry() {
        if (
            this.status.value is Status.Error.Recoverable ||
            this.status.value is Status.WaitingForAuth ||
            this.status.value == null
        ) {
            val auth = auth

            if (auth != null) {
                prepare(auth)
            }
        }
    }

    private fun prepare(auth: ActivityViewModel) {
        runAsync {
            lock.withLock {
                try {
                    statusInternal.value = Status.Preparing

                    val server = logic.serverLogic.getServerConfigCoroutine()

                    suspend fun canDoPurchase() = if (server.hasAuthToken) server.api.canDoPurchase(server.deviceAuthToken)
                    else CanDoPurchaseStatus.NoForUnknownReason

                    statusInternal.value = when (canDoPurchase()) {
                        is CanDoPurchaseStatus.Yes -> if (auth.isParentAuthenticated()) {
                            try {
                                val authData = ApplyDirectCallAuthentication.from(
                                    auth.authenticatedUser.value?.first!!
                                )

                                val token = server.api.createIdentityToken(
                                    deviceAuthToken = server.deviceAuthToken,
                                    parentUserId = authData.parentUserId,
                                    parentPasswordSecondHash = authData.parentPasswordSecondHash
                                )

                                Status.ReadyToken(token)
                            } catch (ex: NotFoundHttpError) {
                                Status.Error.Unrecoverable.ServerClientCombinationUnsupported
                            }
                        } else Status.WaitingForAuth
                        CanDoPurchaseStatus.NotDueToOldPurchase -> Status.Error.Unrecoverable.ExistingPaymentError
                        CanDoPurchaseStatus.NoForUnknownReason -> Status.Error.Unrecoverable.ServerRejectedError
                    }
                } catch (ex: Exception) {
                    Status.Error.Recoverable.NetworkError(ex)
                }
            }
        }
    }

    sealed class Status {
        sealed class Error: Status() {
            sealed class Recoverable: Error() {
                data class NetworkError(val exception: Exception): Recoverable()
            }
            sealed class Unrecoverable: Error() {
                object ExistingPaymentError: Unrecoverable()
                object ServerRejectedError: Unrecoverable()
                object ServerClientCombinationUnsupported: Unrecoverable()
            }
        }
        class ReadyToken(val token: String): Status()
        object Preparing: Status()
        object WaitingForAuth: Status()
    }
}