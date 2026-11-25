//
//  PlayStore.kt
//
//  The real store: Google Play Billing. Google handles the payment; the app
//  only learns what was bought. Copied from Skate's PlayStore.kt (itself from
//  Sevens'), with Restore and Redeem from Mahjong's.
//
//  How Google Play differs from Apple's StoreKit:
//  - A product can be bought again only after it has been "consumed". Tips are
//    consumed straight away so the player can tip more than once.
//  - A purchase that unlocks something must be "acknowledged" within three days
//    or Google refunds it. The full version is acknowledged as soon as it's seen.
//

package com.tuesdaygames.synth.store

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.tuesdaygames.synth.KeyValueStore
import com.tuesdaygames.synth.Prefs

class PlayStore(
    context: Context,
    private val prefs: KeyValueStore,
    /** For screenshots and manual testing on debug builds: pretend the full version is or is not owned. */
    private val debugOverride: Boolean? = null,
) : Store, PurchasesUpdatedListener {

    override var onChange: () -> Unit = {}

    /** What Google Play told us about each product, needed again to start a purchase. */
    private var details: Map<String, ProductDetails> = emptyMap()
    private var hasLoaded = false

    /** Whether Google Play says the full game is owned. Remembered ("fullGameUnlocked",
     *  as on iOS) so menus are right before the store answers. */
    private var owned = prefs.getBoolean(Prefs.FULL_GAME_UNLOCKED)

    /** Detail of the most recent failure, for the diagnostics line. */
    private var lastProblem: String? = null

    /** Who to tell when the purchase now on screen finishes. */
    private var waiting: ((PurchaseOutcome) -> Unit)? = null

    /** Google Play answers on a background thread; everything here is passed back to the main one. */
    private val main = Handler(Looper.getMainLooper())

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        // Allow purchases that finish later, such as paying cash at a shop.
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    override val state: StoreState
        get() {
            val products = details.values.map(::product)
            val parts = mutableListOf("products ${products.size}/${Store.productIDs.size}", "full ${if (owned) "owned" else "not owned"}")
            lastProblem?.let(parts::add)
            return StoreState(
                fullGame = products.firstOrNull { it.id == Store.fullGameID },
                tips = Store.tipIDs.mapNotNull { id -> products.firstOrNull { it.id == id } }
                    .sortedBy { details[it.id]?.oneTimePurchaseOfferDetails?.priceAmountMicros ?: 0 },
                hasLoaded = hasLoaded,
                isUnlocked = debugOverride ?: owned,
                diagnostics = parts.joinToString(" · "),
            )
        }

    private fun product(details: ProductDetails) = StoreProduct(
        id = details.productId,
        name = details.name,
        price = details.oneTimePurchaseOfferDetails?.formattedPrice ?: "",
    )

    // Connecting and loading

    override fun start() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                main.post {
                    if (result.responseCode == BillingResponseCode.OK) {
                        loadProducts()
                        refreshOwnership()
                    } else {
                        // No Play Store on this phone, or no Google account signed in.
                        lastProblem = describe("connect", result)
                        hasLoaded = true
                        onChange()
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                // The connection is set to come back by itself.
            }
        })
    }

    private fun loadProducts() {
        val wanted = Store.productIDs.map { id ->
            QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(ProductType.INAPP).build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(wanted).build()
        client.queryProductDetailsAsync(params) { result, found ->
            main.post {
                if (result.responseCode == BillingResponseCode.OK) {
                    details = found.productDetailsList.associateBy { it.productId }
                } else {
                    lastProblem = describe("products", result)
                }
                hasLoaded = true
                onChange()
            }
        }
    }

    override fun refresh() {
        if (!client.isReady) return
        if (details.isEmpty()) loadProducts()
        refreshOwnership()
    }

    /** Reads what Google Play says is owned. This is the source of truth for the unlock,
     *  and settles any tip that was paid for but never used up (say, the app closed mid-purchase). */
    private fun refreshOwnership(then: () -> Unit = {}) {
        val params = QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            main.post {
                if (result.responseCode == BillingResponseCode.OK) {
                    purchases.forEach(::settle)
                    // A refunded purchase is simply missing from the list, which locks it again.
                    setUnlocked(purchases.any(::ownsFullGame))
                } else {
                    lastProblem = describe("purchases", result)
                }
                onChange()
                then()
            }
        }
    }

    override fun restore(done: (Boolean) -> Unit) {
        if (!client.isReady) {
            done(state.isUnlocked)
            return
        }
        refreshOwnership { done(state.isUnlocked) }
    }

    override fun redeemCode(host: Any?): Boolean {
        if (host !is Activity) return false
        return try {
            host.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/redeem")))
            true
        } catch (error: Exception) {
            lastProblem = "redeem: no Play Store"
            false
        }
    }

    private fun ownsFullGame(purchase: Purchase): Boolean =
        purchase.purchaseState == Purchase.PurchaseState.PURCHASED && Store.fullGameID in purchase.products

    /** Does what Google Play requires after a purchase: acknowledge the unlock, use up a tip. */
    private fun settle(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (Store.fullGameID in purchase.products) {
            if (!purchase.isAcknowledged) {
                val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                client.acknowledgePurchase(params) { result ->
                    if (result.responseCode != BillingResponseCode.OK) main.post { lastProblem = describe("acknowledge", result) }
                }
            }
        } else {
            val params = ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            client.consumeAsync(params) { result, _ ->
                if (result.responseCode != BillingResponseCode.OK) main.post { lastProblem = describe("consume", result) }
            }
        }
    }

    // Buying

    override fun buy(product: StoreProduct, host: Any?, done: (PurchaseOutcome) -> Unit) {
        lastProblem = null
        val details = details[product.id]
        if (host !is Activity || details == null) {
            lastProblem = "nothing to buy"
            done(PurchaseOutcome.Failed)
            return
        }
        val item = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build()
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(item)).build()
        waiting = done
        val result = client.launchBillingFlow(host, params)
        if (result.responseCode != BillingResponseCode.OK) {
            // The payment sheet never opened. Otherwise the answer arrives in onPurchasesUpdated.
            lastProblem = describe("launch", result)
            finish(PurchaseOutcome.Failed)
        }
    }

    /** Google Play calls this when a purchase finishes, including one started outside the app. */
    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        main.post {
            when (result.responseCode) {
                BillingResponseCode.OK -> {
                    val all = purchases.orEmpty()
                    all.forEach(::settle)
                    if (all.any(::ownsFullGame)) setUnlocked(true)
                    if (all.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }) {
                        finish(PurchaseOutcome.Success)
                    } else {
                        finish(PurchaseOutcome.Pending)
                    }
                }
                BillingResponseCode.USER_CANCELED -> finish(PurchaseOutcome.Cancelled)
                else -> {
                    lastProblem = describe("purchase", result)
                    finish(PurchaseOutcome.Failed)
                }
            }
        }
    }

    private fun finish(outcome: PurchaseOutcome) {
        val done = waiting
        waiting = null
        onChange()
        done?.invoke(outcome)
    }

    private fun setUnlocked(value: Boolean) {
        owned = value
        prefs.putBoolean(Prefs.FULL_GAME_UNLOCKED, value)
    }

    private fun describe(what: String, result: BillingResult) = "$what: code ${result.responseCode} ${result.debugMessage}".trim()

}
