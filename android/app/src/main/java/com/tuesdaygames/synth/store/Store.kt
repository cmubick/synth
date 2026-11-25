//
//  Store.kt
//
//  In-app purchases: a one-time unlock of the full version, and optional tips.
//  This file is the part the app talks to and has no Android in it, so the
//  tests can stand a pretend store in its place. PlayStore.kt is the real one.
//  Same shape as Skate's and Mahjong's Android stores; ported from
//  iOS/Synth/UI/Store.swift.
//

package com.tuesdaygames.synth.store

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Something that can be bought, as Google Play describes it. `price` is already formatted, such as "$2.99". */
data class StoreProduct(val id: String, val name: String, val price: String)

enum class PurchaseOutcome {
    Success,

    /** Waiting on something outside the app, such as a parent's approval or a cash payment. */
    Pending,
    Cancelled,
    Failed,
}

/** Everything the screens need to know about the store at one moment. */
data class StoreState(
    val fullGame: StoreProduct? = null,
    /** Tip products, cheapest first. */
    val tips: List<StoreProduct> = emptyList(),
    /** True once the product list has been requested, whether or not anything came back. */
    val hasLoaded: Boolean = false,
    /** Whether Google Play says the full version is owned. */
    val isUnlocked: Boolean = false,
    /** One line describing what the store currently knows, for debug builds. */
    val diagnostics: String = "",
)

interface Store {
    val state: StoreState

    /** Called whenever products or the unlock change, so the screen can redraw. */
    var onChange: () -> Unit

    fun start()
    fun refresh()

    /** Buys a product. `host` is what the store shows its payment sheet on: the Activity. */
    fun buy(product: StoreProduct, host: Any?, done: (PurchaseOutcome) -> Unit)

    /** Reads purchases back from Google Play; `done(true)` if the full version is owned. */
    fun restore(done: (Boolean) -> Unit)

    /** Opens the Play Store's page for typing in a promo code. False if it could not be opened. */
    fun redeemCode(host: Any?): Boolean

    companion object {
        /** The same product IDs as on the App Store. */
        const val fullGameID = "synth.fullgame"
        val tipIDs = listOf("synth.tip.small", "synth.tip.medium", "synth.tip.large")

        /** When off, the full version is never loaded or sold (as Skate does). On: it's ready. */
        const val fullGameOnSale = true

        val productIDs: List<String> get() = (if (fullGameOnSale) listOf(fullGameID) else emptyList()) + tipIDs
    }
}

/** A store with nothing for sale. Used by tests and previews. */
class NoStore(unlocked: Boolean = false) : Store {
    override val state = StoreState(hasLoaded = true, isUnlocked = unlocked)
    override var onChange: () -> Unit = {}
    override fun start() {}
    override fun refresh() {}
    override fun buy(product: StoreProduct, host: Any?, done: (PurchaseOutcome) -> Unit) = done(PurchaseOutcome.Failed)
    override fun restore(done: (Boolean) -> Unit) = done(state.isUnlocked)
    override fun redeemCode(host: Any?) = false
}

/** The store as Compose state: screens read `state` and redraw when it changes. */
class StoreModel(private val store: Store) {
    var state by mutableStateOf(store.state)
        private set

    val isUnlocked: Boolean get() = state.isUnlocked

    init {
        store.onChange = { state = store.state }
    }

    fun start() = store.start()
    fun refresh() = store.refresh()

    fun buy(product: StoreProduct, host: Any?, done: (PurchaseOutcome) -> Unit) =
        store.buy(product, host) { outcome ->
            state = store.state
            done(outcome)
        }

    fun restore(done: (Boolean) -> Unit) = store.restore { found ->
        state = store.state
        done(found)
    }

    fun redeemCode(host: Any?) = store.redeemCode(host)
}
