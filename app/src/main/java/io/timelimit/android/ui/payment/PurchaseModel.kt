/*
 * TimeLimit Copyright <C> 2019 - 2022 Jonas Lochmann
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
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.BuildConfig
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.extensions.BillingNotSupportedException
import io.timelimit.android.livedata.castDown
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.apply.ApplyActionChildAddLimitAuthentication
import io.timelimit.android.sync.actions.apply.ApplyActionParentDeviceAuthentication
import io.timelimit.android.sync.actions.apply.ApplyActionParentPasswordAuthentication
import io.timelimit.android.sync.network.CanDoPurchaseStatus
import io.timelimit.android.sync.network.api.NotFoundHttpError
import io.timelimit.android.ui.main.ActivityViewModel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PurchaseModel(application: Application): AndroidViewModel(application) {
    private val logic: AppLogic by lazy { DefaultAppLogic.with(application) }
    private val statusInternal = MutableLiveData<Status>()
    private val lock = Mutex()
    private var activityPurchaseModel: ActivityPurchaseModel? = null
    private var auth: ActivityViewModel? = null

    val status = statusInternal.castDown()

    fun init(activityPurchaseModel: ActivityPurchaseModel, auth: ActivityViewModel) {
        this.activityPurchaseModel = activityPurchaseModel
        this.auth = auth

        retry()
    }

    fun retry() {
        if (
            this.status.value is Status.Error.Recoverable ||
            this.status.value is Status.WaitingForAuth ||
            this.status.value == null
        ) {
            val activityPurchaseModel = activityPurchaseModel
            val auth = auth

            if (activityPurchaseModel != null && auth != null) {
                prepare(activityPurchaseModel, auth)
            }
        }
    }

    private fun prepare(activityPurchaseModel: ActivityPurchaseModel, auth: ActivityViewModel) {
        runAsync {
            lock.withLock {
                try {
                    statusInternal.value = Status.Preparing

                    val server = logic.serverLogic.getServerConfigCoroutine()

                    suspend fun canDoPurchase() = if (server.hasAuthToken) server.api.canDoPurchase(server.deviceAuthToken)
                    else CanDoPurchaseStatus.NoForUnknownReason

                    if (!BuildConfig.storeCompilant) {
                        if (auth.isParentAuthenticated()) {
                            val authData = auth.authenticatedUser.value?.first

                            try {
                                val token = when (authData) {
                                    ApplyActionParentDeviceAuthentication -> server.api.createIdentityToken(
                                        deviceAuthToken = server.deviceAuthToken,
                                        parentUserId = "",
                                        parentPasswordSecondHash = "device"
                                    )
                                    is ApplyActionParentPasswordAuthentication -> server.api.createIdentityToken(
                                        deviceAuthToken = server.deviceAuthToken,
                                        parentUserId = authData.parentUserId,
                                        parentPasswordSecondHash = authData.secondPasswordHash
                                    )
                                    is ApplyActionChildAddLimitAuthentication -> throw RuntimeException(
                                        "child can not do that"
                                    )
                                    null -> throw RuntimeException("missing user")
                                }

                                statusInternal.value = Status.ReadyToken(token)
                            } catch (ex: NotFoundHttpError) {
                                statusInternal.value = Status.Error.Unrecoverable.ServerClientCombinationUnsupported
                            }
                        } else statusInternal.value = Status.WaitingForAuth
                    } else {
                        val canDoPurchase = canDoPurchase()

                        if (canDoPurchase is CanDoPurchaseStatus.Yes) {
                            if (canDoPurchase.publicKey?.contentEquals(Base64.decode(BuildConfig.googlePlayKey, 0)) == false) {
                                statusInternal.value = Status.Error.Unrecoverable.ServerClientCombinationUnsupported
                            } else {
                                val skus = activityPurchaseModel.queryProducts(PurchaseIds.BUY_SKUS)

                                statusInternal.value = Status.ReadyRegular(
                                        monthPrice = skus.find { it.productId == PurchaseIds.SKU_MONTH }?.oneTimePurchaseOfferDetails?.formattedPrice.toString(),
                                        yearPrice = skus.find { it.productId == PurchaseIds.SKU_YEAR }?.oneTimePurchaseOfferDetails?.formattedPrice.toString()
                                )
                            }
                        } else if (canDoPurchase == CanDoPurchaseStatus.NotDueToOldPurchase) {
                            statusInternal.value = Status.Error.Unrecoverable.ExistingPaymentError
                        } else {
                            statusInternal.value = Status.Error.Unrecoverable.ServerRejectedError
                        }
                    }
                } catch (ex: BillingNotSupportedException) {
                    statusInternal.value = Status.Error.Unrecoverable.BillingNotSupportedByDevice
                } catch (ex: Exception) {
                    statusInternal.value = Status.Error.Recoverable.NetworkError(ex)
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
                object BillingNotSupportedByDevice: Unrecoverable()
                object ExistingPaymentError: Unrecoverable()
                object ServerRejectedError: Unrecoverable()
                object ServerClientCombinationUnsupported: Unrecoverable()
            }
        }
        class ReadyRegular(val monthPrice: String, val yearPrice: String): Status()
        class ReadyToken(val token: String): Status()
        object Preparing: Status()
        object WaitingForAuth: Status()
    }
}