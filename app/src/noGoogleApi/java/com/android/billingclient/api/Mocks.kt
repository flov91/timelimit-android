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
package com.android.billingclient.api

import android.app.Activity
import android.app.Application
import io.timelimit.android.async.Threads

object BillingClient {
    fun newBuilder(application: Application) = Builder

    fun startConnection(listener: BillingClientStateListener) {
        Threads.mainThreadHandler.post { listener.onBillingSetupFinished(BillingResult) }
    }

    fun endConnection() {}

    fun queryProductDetails(param: QueryProductDetailsParams) = QueryProductDetailsResult.instance
    fun launchBillingFlow(activity: Activity, params: BillingFlowParams) = BillingResult
    fun acknowledgePurchase(params: AcknowledgePurchaseParams) = BillingResult
    fun consumePurchase(params: ConsumeParams) = BillingResult
    suspend fun queryPurchasesAsync(request: QueryPurchasesParams) = QueryPurchasesResult

    object BillingResponseCode {
        const val OK = 0
        const val ERR = 1
    }

    enum class ProductType { INAPP }

    object Builder {
        fun enablePendingPurchases() = this
        fun setListener(listener: PurchasesUpdatedListener) = this
        fun build() = BillingClient
    }
}

object BillingResult {
    const val responseCode = BillingClient.BillingResponseCode.ERR
    const val debugMessage = "only mock linked"
}

object ProductDetails {
    const val productId = ""
    const val description = ""
    val oneTimePurchaseOfferDetails: OfferDetails? = OfferDetails

    object OfferDetails {
        const val formattedPrice = ""
    }
}

object Purchase {
    const val purchaseState = PurchaseState.PURCHASED
    const val isAcknowledged = true
    val products = emptyList<String>()
    const val purchaseToken = ""
    const val originalJson = ""
    const val signature = ""

    object PurchaseState {
        const val PURCHASED = 0
    }
}

object AcknowledgePurchaseParams {
    fun newBuilder() = Builder

    object Builder {
        fun setPurchaseToken(token: String) = this
        fun build() = AcknowledgePurchaseParams
    }
}

object ConsumeParams {
    fun newBuilder() = Builder

    object Builder {
        fun setPurchaseToken(token: String) = this
        fun build() = ConsumeParams
    }
}

object BillingFlowParams {
    fun newBuilder() = this
    fun setProductDetailsParamsList(details: List<ProductDetailsParams>) = this
    fun build() = this

    object ProductDetailsParams {
        fun newBuilder() = this
        fun setProductDetails(details: ProductDetails) = this
        fun build() = this
    }
}

object QueryPurchasesResult {
    val billingResult = BillingResult
    val purchasesList: List<Purchase> = emptyList()
}

data class QueryProductDetailsResult(val billingResult: BillingResult, val details: List<ProductDetails>?) {
    companion object {
        val instance = QueryProductDetailsResult(BillingResult, emptyList())
    }
}

interface BillingClientStateListener {
    fun onBillingSetupFinished(billingResult: BillingResult)
    fun onBillingServiceDisconnected()
}

interface PurchasesUpdatedListener {
    fun onPurchasesUpdated(p0: BillingResult, p1: MutableList<Purchase>?)
}

object QueryProductDetailsParams {
    fun newBuilder() = this
    fun setProductList(list: List<Product>) = this
    fun build() = this

    object Product {
        fun newBuilder() = this
        fun setProductId(id: String) = this
        fun setProductType(type: BillingClient.ProductType) = this
        fun build() = this
    }
}

object QueryPurchasesParams {
    fun newBuilder() = this
    fun setProductType(type: BillingClient.ProductType) = this
    fun build() = this
}