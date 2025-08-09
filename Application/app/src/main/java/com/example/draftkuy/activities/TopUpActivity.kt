package com.notherix.draftkuy.activities

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.notherix.draftkuy.R
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.android.billingclient.api.*
import com.google.firebase.database.DatabaseError
import java.util.*
import java.text.DateFormat
import java.util.concurrent.TimeUnit
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.ValueEventListener

class TopUpActivity : AppCompatActivity(), PurchasesUpdatedListener {

    private var rewardedAd: RewardedAd? = null
    private var isLoading = false
    private val TAG = "TopUpActivity"

    private lateinit var billingClient: BillingClient
    private val skuId = "unlimited_coins_dky1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_topup)
        supportActionBar?.hide()

        MobileAds.initialize(this) {}

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnAds).setOnClickListener { checkAdsAvailabilityAndShow() }
        findViewById<Button>(R.id.btnTopup20K).setOnClickListener {
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) {
                // User belum login
                AlertDialog.Builder(this)
                    .setTitle("Login Diperlukan")
                    .setMessage("Silakan login terlebih dahulu untuk melakukan top-up.")
                    .setPositiveButton("OK") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
                return@setOnClickListener
            }

            // Jika sudah login, lanjutkan ke proses top-up
            launchPurchaseFlow()
        }



        checkSubscriptionStatus()
        updateCoinUI()
        loadRewardedAd()
        setupBillingClient()
    }


    private fun checkAdsAvailabilityAndShow() {
        val btnAds = findViewById<Button>(R.id.btnAds)
        val user = FirebaseAuth.getInstance().currentUser

        if (user == null) {
            // Belum login → nonaktifkan tombol dan ubah warna & teks
            btnAds.isEnabled = true
            btnAds.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#BDBDBD")))
            btnAds.text = "Login untuk lihat iklan"

            AlertDialog.Builder(this)
                .setTitle("Login Diperlukan")
                .setMessage("Silakan login terlebih dahulu untuk dapat melihat iklan dan mendapatkan koin gratis.")
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss() // Menutup dialog saat tombol OK ditekan
                }
                .show()
            return
        }

        val prefs = getSharedPreferences("user_data", MODE_PRIVATE)
        val isSubscribed = prefs.getBoolean("is_subscribed", false)
        val uid = user.uid

        if (isSubscribed) return

        val userRef = FirebaseDatabase
            .getInstance("https://draftkuy-3c559-default-rtdb.asia-southeast1.firebasedatabase.app/")
            .getReference("users/$uid")

        userRef.child("time_ads").get().addOnSuccessListener { snapshot ->
            val lastAdTime = snapshot.getValue(Long::class.java) ?: 0L

            FirebaseDatabase.getInstance("https://draftkuy-3c559-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference(".info/serverTimeOffset")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(offsetSnapshot: DataSnapshot) {
                        val offset = offsetSnapshot.getValue(Long::class.java) ?: 0L
                        val serverNow = System.currentTimeMillis() + offset

                        val elapsedSeconds = (serverNow - lastAdTime) / 1000
                        val remaining = 1200 - elapsedSeconds // sisa waktu dalam detik

                        if (remaining <= 0) {
                            // Iklan bisa diklik
                            btnAds.isEnabled = true
                            showRewardedAd {
                                // Update time_ads setelah iklan ditonton
                                userRef.child("time_ads").setValue(ServerValue.TIMESTAMP)
                            }

                        } else {
                            // Masih cooldown → ubah warna, disable tombol, ganti teks jadi timer
                            val minutes = remaining / 60
                            val seconds = remaining % 60
                            val timeFormatted = String.format("%02d menit : %02d detik", minutes, seconds)

                            btnAds.isEnabled = true
                            btnAds.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#BDBDBD")))
//                            btnAds.text = "Tunggu $timeFormatted lagi"

                            Toast.makeText(
                                this@TopUpActivity,
                                "Tunggu $timeFormatted lagi untuk klik iklan.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("checkAds", "Gagal ambil offset waktu server: ${error.message}")
                    }
                })
        }.addOnFailureListener {
            Log.e("checkAds", "Gagal ambil time_ads: ${it.message}")
        }
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
            billingClient.consumeAsync(
                ConsumeParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
            ) { billingResult, _ ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    processSubscription()
                    Toast.makeText(this, "Pembelian Anda berhasil", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Gagal konsumsi pembelian", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    private fun processSubscription() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val prefs = getSharedPreferences("user_data", MODE_PRIVATE)

        val userRef = FirebaseDatabase.getInstance("https://draftkuy-3c559-default-rtdb.asia-southeast1.firebasedatabase.app/")
            .getReference("users/$uid")

        // Ambil offset waktu server
        FirebaseDatabase.getInstance("https://draftkuy-3c559-default-rtdb.asia-southeast1.firebasedatabase.app/")
            .getReference(".info/serverTimeOffset")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val offset = snapshot.getValue(Long::class.java) ?: 0L
                    val serverTime = System.currentTimeMillis() + offset

                    val oldExpireTime = prefs.getLong("sub_expire_time", 0L)
                    val baseTime = maxOf(serverTime, oldExpireTime)

                    val newExpireCal = Calendar.getInstance().apply {
                        timeInMillis = baseTime
                        add(Calendar.MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                    }
                    val newExpireTime = newExpireCal.timeInMillis

                    // Hitung durasi tambahan: 30 hari
                    val addedDays = 30L

                    // Ambil total sub + 1
                    val totalSub = prefs.getLong("total_sub",0L) + 1L
                    // Ambil sub_duration_days lama dari Firebase
                    userRef.child("sub_duration_days").get().addOnSuccessListener { snapshot ->
                        val currentDuration = snapshot.getValue(Long::class.java) ?: 0L
                        val newDuration = currentDuration + addedDays

                        // Simpan ke SharedPreferences
                        prefs.edit()
                            .putBoolean("is_subscribed", true)
                            .putLong("sub_expire_time", newExpireTime)
                            .putLong("total_sub", totalSub)
                            .apply()

                        // Simpan ke Firebase
                        userRef.child("is_subscribed").setValue(true)
                        userRef.child("sub_expire_time").setValue(newExpireTime)
                        userRef.child("sub_duration_days").setValue(newDuration)
                        userRef.child("total_sub").setValue(totalSub)


                        val expireDate = DateFormat.getDateInstance(DateFormat.LONG).format(Date(newExpireTime))
                        Toast.makeText(
                            this@TopUpActivity,
                            "Berhasil! Unlimited coins aktif hingga $expireDate",
                            Toast.LENGTH_LONG
                        ).show()

                        updateCoinUI()
                    }.addOnFailureListener {
                        Toast.makeText(this@TopUpActivity, "Gagal ambil durasi lama", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@TopUpActivity, "Gagal ambil waktu server", Toast.LENGTH_SHORT).show()
                }
            })
    }


    private fun checkSubscriptionStatus() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val prefs = getSharedPreferences("user_data", MODE_PRIVATE)
        val isSubscribed = prefs.getBoolean("is_subscribed", false)
        val expireAt = prefs.getLong("sub_expire_time", 0L)

        if (isSubscribed) {
            // Verify with server time
            FirebaseDatabase.getInstance("https://draftkuy-3c559-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("users/$uid/ServerTime/timestamp")
                .get().addOnSuccessListener { snapshot ->
                    val serverTime = snapshot.getValue(Long::class.java) ?: System.currentTimeMillis()

                    if (serverTime > expireAt) {
                        prefs.edit().putBoolean("is_subscribed", false).apply()
                        FirebaseDatabase.getInstance("https://draftkuy-3c559-default-rtdb.asia-southeast1.firebasedatabase.app/")
                            .getReference("users/$uid/is_subscribed")
                            .setValue(false)

                        Toast.makeText(this, "Langganan telah berakhir", Toast.LENGTH_SHORT).show()
                        updateCoinUI()
                    }
                    else{
                        val daysLeft = TimeUnit.MILLISECONDS.toDays(expireAt - serverTime)
                        FirebaseDatabase.getInstance("https://draftkuy-3c559-default-rtdb.asia-southeast1.firebasedatabase.app/")
                            .getReference("users/$uid/sub_duration_days")
                            .setValue(daysLeft)
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

    private fun showRewardedAd(onRewardEarned: (() -> Unit)? = null){
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
                onRewardEarned?.invoke() // jalankan callback
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

        val now = System.currentTimeMillis()
        val isStillValid = isSubscribed && now < expireAt

        return if (isStillValid) {
            val daysLeft = TimeUnit.MILLISECONDS.toDays(expireAt - now)
            "∞ ($daysLeft hari)"
        } else {
            // Optional: reset is_subscribed jika kadaluarsa tapi belum diupdate
            if (isSubscribed) {
                prefs.edit().putBoolean("is_subscribed", false).apply()
            }
            getCoins().toString()
        }
    }


    private fun getCoins(): Int {
        return getSharedPreferences("user_data", MODE_PRIVATE).getInt("coins", 0)
    }

    private fun addCoins(amount: Int) {
        val prefs = getSharedPreferences("user_data", MODE_PRIVATE)
        val newTotal = getCoins() + amount
        prefs.edit().putInt("coins", newTotal).apply()

        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            FirebaseDatabase.getInstance("https://draftkuy-3c559-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("users/$uid/coins")
                .setValue(newTotal)
        }

        updateCoinUI()
    }

    private fun updateCoinUI() {
        findViewById<TextView>(R.id.txtCoin).text = getCoinsDisplay()
    }
}