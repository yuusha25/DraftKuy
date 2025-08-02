package com.example.draftkuy.activities

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.draftkuy.R
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.OnUserEarnedRewardListener

class TopUpActivity : AppCompatActivity() {
    private var rewardedAd: RewardedAd? = null
    private var isLoading = false
    private val TAG = "TopUpActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_topup)
        supportActionBar?.hide()

        // Inisialisasi Mobile Ads SDK
        MobileAds.initialize(this) {}

        // Tombol kembali
        findViewById<Button>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // Tombol nonton iklan
        findViewById<Button>(R.id.btnAds).setOnClickListener {
            showRewardedAd()
        }

        // Tampilkan jumlah koin saat ini
        updateCoinUI()

        // Load iklan rewarded
        loadRewardedAd()
    }

    private fun loadRewardedAd() {
        if (isLoading || rewardedAd != null) return
        isLoading = true

        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(this, "ca-app-pub-3940256099942544/5224354917", adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "Rewarded ad berhasil dimuat.")
                    rewardedAd = ad
                    isLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Gagal memuat iklan: ${error.message}")
                    rewardedAd = null
                    isLoading = false
                }
            }
        )
    }

    private fun showRewardedAd() {
        val ad = rewardedAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Iklan ditutup.")
                    rewardedAd = null
                    loadRewardedAd()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e(TAG, "Gagal menampilkan iklan: ${adError.message}")
                    rewardedAd = null
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Iklan ditampilkan.")
                }
            }

            ad.show(this, OnUserEarnedRewardListener {
                // Tambah 3 koin ke akun user
                addCoins(3)
                Toast.makeText(this, "Kamu mendapatkan 3 koin!", Toast.LENGTH_SHORT).show()
            })
        } else {
            Toast.makeText(this, "Iklan belum siap, coba lagi nanti.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getCoins(): Int {
        val prefs = getSharedPreferences("user_data", MODE_PRIVATE)
        return prefs.getInt("coins", 0)
    }

    private fun addCoins(amount: Int) {
        val prefs = getSharedPreferences("user_data", MODE_PRIVATE)
        val currentCoins = getCoins()
        prefs.edit().putInt("coins", currentCoins + amount).apply()
        updateCoinUI()
    }

    private fun updateCoinUI() {
        val txtCoin = findViewById<TextView>(R.id.txtCoin)
        txtCoin.text = "${getCoins()}"
    }


}
