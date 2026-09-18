package com.pixel.oceanfacts.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.AcknowledgePurchaseParams
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
import com.pixel.oceanfacts.core.OceanPrefs

/**
 * Google Play Billing for the single non-consumable "remove ads" entitlement.
 *
 * The entitlement is mirrored into [OceanPrefs] so [isAdFree] is correct synchronously on the
 * next cold start, before Play has answered — an ad-free user never sees a flash of ads while
 * the billing connection is still coming up. Play stays the source of truth: every successful
 * ownership query overwrites the cached value, so a refund revokes the entitlement on the
 * following launch.
 */
object BillingManager {

    const val REMOVE_ADS_PRODUCT = "ocean_remove_ads"

    enum class Outcome { PURCHASED, CANCELLED, UNAVAILABLE, ERROR }

    var isAdFree by mutableStateOf(false)
        private set

    /** Play's localised price for the product, e.g. "$4.99" — null until queried. */
    var price by mutableStateOf<String?>(null)
        private set

    private const val TAG = "OceanBilling"
    private const val MAX_RETRIES = 4

    private var client: BillingClient? = null
    private var productDetails: ProductDetails? = null
    private var retries = 0
    private var pendingResult: ((Outcome) -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())

    private val purchasesUpdated = PurchasesUpdatedListener { result, purchases ->
        when {
            result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null -> {
                purchases.forEach { record(it) }
                finish(Outcome.PURCHASED)
            }
            result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED ->
                finish(Outcome.CANCELLED)
            result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                refreshPurchases()
                finish(Outcome.PURCHASED)
            }
            else -> {
                Log.w(TAG, "Purchase update failed: ${result.responseCode} ${result.debugMessage}")
                finish(Outcome.ERROR)
            }
        }
    }

    /** Idempotent — safe to call from every Activity.onCreate. */
    fun init(context: Context) {
        isAdFree = OceanPrefs.adFree
        if (client != null) return
        client = BillingClient.newBuilder(context.applicationContext)
            .setListener(purchasesUpdated)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
            )
            .build()
        connect()
    }

    private fun connect() {
        val c = client ?: return
        if (c.isReady) return
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    retries = 0
                    queryProduct()
                    refreshPurchases()
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.responseCode} ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                if (retries >= MAX_RETRIES) return
                retries += 1
                handler.postDelayed({ connect() }, 2_000L * retries)
            }
        })
    }

    private fun queryProduct() {
        val c = client ?: return
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(REMOVE_ADS_PRODUCT)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        c.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "Product query failed: ${result.responseCode} ${result.debugMessage}")
                return@queryProductDetailsAsync
            }
            val product = details.productDetailsList.firstOrNull { it.productId == REMOVE_ADS_PRODUCT }
            productDetails = product
            price = product?.oneTimePurchaseOfferDetails?.formattedPrice
        }
    }

    /**
     * Asks Play what this account owns and reconciles the cached entitlement with the answer.
     * [onResult] reports whether the product came back as owned; on a failed or unavailable
     * query it reports the cached value instead, so a restore that could not reach Play never
     * reads as "you own nothing".
     */
    fun refreshPurchases(onResult: ((Boolean) -> Unit)? = null) {
        val c = client
        if (c == null || !c.isReady) {
            connect()
            onResult?.invoke(isAdFree)
            return
        }
        c.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
        ) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "Purchase query failed: ${result.responseCode} ${result.debugMessage}")
                onResult?.invoke(isAdFree)
                return@queryPurchasesAsync
            }
            purchases.forEach { record(it) }
            val owned = purchases.any {
                it.products.contains(REMOVE_ADS_PRODUCT) &&
                    it.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            applyEntitlement(owned)
            onResult?.invoke(owned)
        }
    }

    fun launchPurchase(activity: Activity, onResult: (Outcome) -> Unit) {
        val c = client
        val product = productDetails
        if (c == null || !c.isReady || product == null) {
            connect()
            onResult(Outcome.UNAVAILABLE)
            return
        }
        pendingResult = onResult
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .build(),
                ),
            )
            .build()
        val result = c.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "Could not start billing flow: ${result.responseCode} ${result.debugMessage}")
            finish(Outcome.ERROR)
        }
    }

    /**
     * Grants the entitlement and acknowledges the purchase. Play auto-refunds anything left
     * unacknowledged for three days, so this has to run for every PURCHASED token — including
     * one first seen through a restore rather than a fresh buy.
     */
    private fun record(purchase: Purchase) {
        if (!purchase.products.contains(REMOVE_ADS_PRODUCT)) return
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        applyEntitlement(true)
        if (purchase.isAcknowledged) return
        client?.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build(),
        ) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "Acknowledge failed: ${result.responseCode} ${result.debugMessage}")
            }
        }
    }

    private fun applyEntitlement(value: Boolean) {
        isAdFree = value
        OceanPrefs.adFree = value
    }

    private fun finish(outcome: Outcome) {
        val callback = pendingResult ?: return
        pendingResult = null
        callback(outcome)
    }
}
