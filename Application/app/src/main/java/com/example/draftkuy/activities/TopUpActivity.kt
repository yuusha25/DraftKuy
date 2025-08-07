package com.notherix.draftkuy.activities

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.notherix.draftkuy.R
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.android.billingclient.api.*
import java.util.*
import java.text.DateFormat
import java.util.concurrent.TimeUnit

class TopUpActivity : AppCompatActivity(), PurchasesUpdatedListener {

    private var rewardedAd: RewardedAd? = null
    private var isLoading = false
    private val TAG = "TopUpActivity"

    private lateinit var billingClient: BillingClient
    private val skuId = "unlimited_coins_20k"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_topup)
        supportActionBar?.hide()

        MobileAds.initialize(this) {}

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnAds).setOnClickListener { showRewardedAd() }
        findViewById<Button>(R.id.btnTopup20K).setOnClickListener { launchPurchaseFlow() }

        checkSubscriptionStatus()
        updateCoinUI()
        loadRewardedAd()
        setupBillingClient()
    }

    // -- Billing Setup --
    private fun setupBillingClient() {
        billingClient = BillingClient.newBuilder(this)
            .enablePendingPurchases()
            .setListener(this)
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing Client ready")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.e(TAG, "Billing Client disconnected")
            }
        })
    }

    private fun launchPurchaseFlow() {
        val params = SkuDetailsParams.newBuilder()
        params.setSkusList(listOf(skuId)).setType(BillingClient.SkuType.INAPP)

        billingClient.querySkuDetailsAsync(params.build()) { billingResult, skuDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && skuDetailsList != null) {
                val skuDetails = skuDetailsList.firstOrNull() ?: return@querySkuDetailsAsync
                val flowParams = BillingFlowParams.newBuilder()
                    .setSkuDetails(skuDetails)
                    .build()
                billingClient.launchBillingFlow(this, flowParams)
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    if (purchase.skus.contains(skuId)) {
                        handlePurchase(purchase)
                    }
                }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                Toast.makeText(this, "Anda sudah memiliki langganan aktif", Toast.LENGTH_SHORT).show()
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Toast.makeText(this, "Pembelian dibatalkan", Toast.LENGTH_SHORT).show()
            }
            else -> {
                Toast.makeText(this, "Pembelian gagal: ${billingResult.debugMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

                billingClient.acknowledgePurchase(params) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        processSubscription()
                    } else {
                        Toast.makeText(this, "Gagal memverifikasi pembelian", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun processSubscription() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val serverTimeRef = FirebaseDatabase.getInstance()
            .getReference("users/$uid/ServerTime/timestamp")

        // Get server time first
        serverTimeRef.setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
            serverTimeRef.get().addOnSuccessListener { snapshot ->
                val serverTime = snapshot.getValue(Long::class.java) ?: System.currentTimeMillis()

                // Calculate expiration (2 months from server time)
                val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Singapore")).apply {
                    timeInMillis = serverTime
                    add(Calendar.MONTH, 2)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                }
                val expireTime = calendar.timeInMillis

                // Save locally and to Firebase
                getSharedPreferences("user_data", MODE_PRIVATE).edit()
                    .putBoolean("is_subscribed", true)
                    .putLong("sub_expire_time", expireTime)
                    .apply()

                FirebaseDatabase.getInstance("https://draftkuy-3c559-default-rtdb.asia-southeast1.firebasedatabase.app/")
                    .getReference("users/$uid/subscribed_until")
                    .setValue(expireTime)


                val expireDate = DateFormat.getDateInstance(DateFormat.LONG).format(Date(expireTime))
                Toast.makeText(
                    this,
                    "Berhasil! Unlimited coins aktif hingga $expireDate",
                    Toast.LENGTH_LONG
                ).show()

                updateCoinUI()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Gagal memverifikasi waktu server", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkSubscriptionStatus() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val prefs = getSharedPreferences("user_data", MODE_PRIVATE)
        val isSubscribed = prefs.getBoolean("is_subscribed", false)
        val expireAt = prefs.getLong("sub_expire_time", 0L)

        if (isSubscribed) {
            // Verify with server time
            FirebaseDatabase.getInstance()
                .getReference("users/$uid/ServerTime/timestamp")
                .get().addOnSuccessListener { snapshot ->
                    val serverTime = snapshot.getValue(Long::class.java) ?: System.currentTimeMillis()

                    if (serverTime > expireAt) {
                        prefs.edit().putBoolean("is_subscribed", false).apply()
                        FirebaseDatabase.getInstance()
                            .getReference("users/$uid/is_subscribed")
                            .setValue(false)

                        Toast.makeText(this, "Langganan telah berakhir", Toast.LENGTH_SHORT).show()
                        updateCoinUI()
                    }
                }
        }
    }

    // -- Rewarded Ads --
    private fun loadRewardedAd() {
        if (isLoading || rewardedAd != null) return
        isLoading = true

        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(this, "ca-app-pub-3940256099942544/5224354917", adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    isLoading = false
                    Log.e(TAG, "Failed to load ad: ${error.message}")
                }
            })
    }

    private fun showRewardedAd() {
        rewardedAd?.let { ad ->
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    loadRewardedAd()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    rewardedAd = null
                    Log.e(TAG, "Ad failed to show: ${adError.message}")
                }
            }

            ad.show(this, OnUserEarnedRewardListener {
                addCoins(3)
                Toast.makeText(this, "+3 Koin!", Toast.LENGTH_SHORT).show()
            })
        } ?: run {
            Toast.makeText(this, "Iklan belum siap. Coba lagi nanti", Toast.LENGTH_SHORT).show()
            loadRewardedAd()
        }
    }

    // -- Coin Management --
    private fun getCoinsDisplay(): String {
        val prefs = getSharedPreferences("user_data", MODE_PRIVATE)
        val isSubscribed = prefs.getBoolean("is_subscribed", false)
        val expireAt = prefs.getLong("sub_expire_time", 0L)

        if (isSubscribed) {
            val daysLeft = TimeUnit.MILLISECONDS.toDays(expireAt - System.currentTimeMillis())
            return if (daysLeft > 0) "∞ ($daysLeft hari)" else getCoins().toString()
        }
        return getCoins().toString()
    }

    private fun getCoins(): Int {
        return getSharedPreferences("user_data", MODE_PRIVATE).getInt("coins", 0)
    }

    private fun addCoins(amount: Int) {
        val prefs = getSharedPreferences("user_data", MODE_PRIVATE)
        val newTotal = getCoins() + amount
        prefs.edit().putInt("coins", newTotal).apply()

        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            FirebaseDatabase.getInstance()
                .getReference("users/$uid/coins")
                .setValue(newTotal)
        }

        updateCoinUI()
    }

    private fun updateCoinUI() {
        findViewById<TextView>(R.id.txtCoin).text = getCoinsDisplay()
    }
}