package org.jellyfin.androidtv.jellycinema

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Google Play build: optional tips paid through Google Play (Google doesn't allow other payment links in
 * Play apps). Tips unlock nothing. Each tip is used up right away, so people can tip again.
 * The tips are one-time products made in Play Console with these IDs.
 */
object TipJar {
	const val usesStore = true
	val PRODUCT_IDS = listOf("tip_small", "tip_medium", "tip_large")

	class Tip(val id: String, val name: String, val price: String, internal val details: ProductDetails)

	enum class Result { Thanks, Pending, Cancelled, Failed }

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
	private var client: BillingClient? = null
	private var waiting: ((Result) -> Unit)? = null

	private fun client(context: Context): BillingClient = client ?: BillingClient.newBuilder(context.applicationContext)
		.setListener { result, purchases -> onPurchases(result, purchases) }
		.enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
		.enableAutoServiceReconnection()
		.build()
		.also { client = it }

	private suspend fun connected(context: Context): BillingClient? {
		val c = client(context)
		if (c.isReady) return c
		val done = CompletableDeferred<Boolean>()
		c.startConnection(object : BillingClientStateListener {
			override fun onBillingSetupFinished(result: BillingResult) { done.complete(result.responseCode == BillingClient.BillingResponseCode.OK) }
			override fun onBillingServiceDisconnected() { done.complete(false) }
		})
		return if (withTimeoutOrNull(10_000) { done.await() } == true) c else null
	}

	/** The tips with their prices in the person's own currency, cheapest first. Empty when Play can't be reached. */
	suspend fun tips(context: Context): List<Tip> {
		val c = connected(context) ?: return emptyList()
		finishOld(c)
		val params = QueryProductDetailsParams.newBuilder().setProductList(PRODUCT_IDS.map {
			QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.INAPP).build()
		}).build()
		val r = c.queryProductDetails(params)
		return r.productDetailsList.orEmpty()
			.mapNotNull { d -> d.oneTimePurchaseOfferDetails?.let { Tip(d.productId, d.name, it.formattedPrice, d) to it.priceAmountMicros } }
			.sortedBy { it.second }.map { it.first }
	}

	fun buy(activity: Activity, tip: Tip, onResult: (Result) -> Unit) {
		val c = client ?: return onResult(Result.Failed)
		waiting = onResult
		val flow = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(
			BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(tip.details).build()
		)).build()
		val r = c.launchBillingFlow(activity, flow)
		if (r.responseCode != BillingClient.BillingResponseCode.OK) { waiting = null; onResult(Result.Failed) }
	}

	private fun onPurchases(result: BillingResult, purchases: List<Purchase>?) {
		val done = waiting.also { waiting = null }
		val r = when (result.responseCode) {
			BillingClient.BillingResponseCode.OK -> when {
				purchases.orEmpty().any { it.purchaseState == Purchase.PurchaseState.PURCHASED } -> Result.Thanks
				purchases.orEmpty().any { it.purchaseState == Purchase.PurchaseState.PENDING } -> Result.Pending
				else -> Result.Failed
			}
			BillingClient.BillingResponseCode.USER_CANCELED -> Result.Cancelled
			else -> Result.Failed
		}
		scope.launch {
			client?.let { c -> purchases.orEmpty().forEach { consume(c, it) } }
			done?.invoke(r)
		}
	}

	/** Tips that went through while the app was closed (or were pending) are finished here, so Google doesn't refund them. */
	private suspend fun finishOld(c: BillingClient) {
		val r = c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build())
		r.purchasesList.forEach { consume(c, it) }
	}

	private suspend fun consume(c: BillingClient, p: Purchase) {
		if (p.purchaseState != Purchase.PurchaseState.PURCHASED) return
		val r = c.consumePurchase(ConsumeParams.newBuilder().setPurchaseToken(p.purchaseToken).build())
		Timber.i("Tip ${p.products} finished: ${r.billingResult.responseCode}")
	}
}
