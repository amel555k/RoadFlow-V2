package com.amko.roadflow.data.local

import android.app.Activity
import android.content.Context
import com.amko.roadflow.domain.model.SubscriptionResult
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BillingRepository(context: Context) {

    companion object {
        const val PREMIUM_SUBSCRIPTION_PRODUCT_ID = "roadflow_premium_monthly"
    }

    private var pendingPurchaseCallback: ((SubscriptionResult) -> Unit)? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val purchasedItem = purchases?.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }

                if (purchasedItem != null) {
                    pendingPurchaseCallback?.invoke(SubscriptionResult.Success)
                } else {
                    val hasPending = purchases?.any { it.purchaseState == Purchase.PurchaseState.PENDING } == true
                    if (hasPending) {
                        pendingPurchaseCallback?.invoke(SubscriptionResult.Error("Uplata je na čekanju, nije još potvrđena"))
                    } else {
                        pendingPurchaseCallback?.invoke(SubscriptionResult.Error("Kupovina nije potvrđena"))
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                pendingPurchaseCallback?.invoke(SubscriptionResult.UserCancelled)
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                pendingPurchaseCallback?.invoke(SubscriptionResult.AlreadyOwned)
            }
            else -> {
                pendingPurchaseCallback?.invoke(SubscriptionResult.Error(billingResult.debugMessage))
            }
        }
        pendingPurchaseCallback = null
    }

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    private var isConnected = false

    private suspend fun ensureConnectedAsync(): Boolean = withContext(Dispatchers.Main) {
        if (isConnected && billingClient.isReady) return@withContext true

        val deferred = CompletableDeferred<Boolean>()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                isConnected = billingResult.responseCode == BillingClient.BillingResponseCode.OK
                deferred.complete(isConnected)
            }

            override fun onBillingServiceDisconnected() {
                isConnected = false
            }
        })

        deferred.await()
    }

    suspend fun getProductDetailsAsync(): ProductDetails? = withContext(Dispatchers.IO) {
        val connected = ensureConnectedAsync()
        if (!connected) return@withContext null

        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PREMIUM_SUBSCRIPTION_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        val deferred = CompletableDeferred<ProductDetails?>()

        billingClient.queryProductDetailsAsync(params) { billingResult, queryProductDetailsResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                deferred.complete(queryProductDetailsResult.productDetailsList.firstOrNull())
            } else {
                deferred.complete(null)
            }
        }

        deferred.await()
    }

    suspend fun launchPurchaseFlowAsync(activity: Activity): SubscriptionResult = withContext(Dispatchers.Main) {
        val connected = ensureConnectedAsync()
        if (!connected) return@withContext SubscriptionResult.Error("Nije moguce povezati se sa Google Play Billing")

        val productDetails = getProductDetailsAsync()
        if (productDetails == null) {
            return@withContext SubscriptionResult.ProductNotAvailable
        }

        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (offerToken == null) {
            return@withContext SubscriptionResult.ProductNotAvailable
        }

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        val deferred = CompletableDeferred<SubscriptionResult>()
        pendingPurchaseCallback = { result -> deferred.complete(result) }

        val launchResult = billingClient.launchBillingFlow(activity, flowParams)
        if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
            pendingPurchaseCallback = null
            return@withContext SubscriptionResult.Error(launchResult.debugMessage)
        }

        deferred.await()
    }

    suspend fun hasActiveSubscriptionAsync(): Boolean = withContext(Dispatchers.IO) {
        val connected = ensureConnectedAsync()
        if (!connected) return@withContext false

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        val deferred = CompletableDeferred<Boolean>()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val hasPurchased = purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                deferred.complete(hasPurchased)
            } else {
                deferred.complete(false)
            }
        }

        deferred.await()
    }
}