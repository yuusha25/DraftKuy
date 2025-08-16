package com.notherix.draftkuy.activities

import android.app.Dialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.notherix.draftkuy.R
import com.notherix.draftkuy.models.Hero
import com.notherix.draftkuy.utils.DataHelper
import com.notherix.draftkuy.utils.JsonMeta
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetSequence
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes

import android.os.Handler
import android.os.Looper


import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var tvHeroName: TextView
    private lateinit var rvHeroes: RecyclerView
    private lateinit var roleBar: ViewGroup
    private lateinit var tvCoinAmount: TextView
    private lateinit var ivHero: ImageView
    private lateinit var allHeroNames: List<String>
    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var googleSignInClient: GoogleSignInClient
    private var searchDialog: AlertDialog? = null
    private var currentHero: Hero? = null
    private var selectedRoleView: TextView? = null

    private val COINS_KEY = "coins"
    private val LAST_LOGIN = "last_login"
    private val LAST_CLAIM_DATE_KEY = "last_claim"
    private val IS_LOGGED_IN_KEY = "is_logged_in"
    private val TUTORIAL_SHOWN_KEY = "tutorial_shown"
    private val IS_SUBSCRIBED = "is_subscribed"
    private val TIME_ADS = "time_ads"
    private val SUB_DURATION_DAYS= "sub_duration_days"
    private val TOTAL_SUB = "total_sub"
    private val SUB_EXPIRE_TIME= "sub_expire_time"

    // Tambahkan di sini:
    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
            account?.idToken?.let { firebaseAuthWithGoogle(it) }
        } catch (e: ApiException) {
            val errorMsg = when (e.statusCode) {
                GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> "Login dibatalkan"
                GoogleSignInStatusCodes.SIGN_IN_FAILED -> "Gagal login. Coba lagi"
                else -> "Error: ${e.message}"
            }
            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val tvLogin = findViewById<TextView>(R.id.tvLogin)
        updateLoginText()

        tvLogin.setOnClickListener {
            handleLoginLogout()
        }

        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        sharedPrefs = getSharedPreferences("user_data", MODE_PRIVATE)
        setupGoogleSignIn()

        val btnTopUp = findViewById<ImageButton>(R.id.btnTopUp)
        btnTopUp.setOnClickListener {
            startActivity(Intent(this, TopUpActivity::class.java))
        }

        initViews()
        setupRecyclerView()
        setupClickListeners()
        loadAllHeroNames()
        supportActionBar?.hide()

        showTutorial()
    }

    private fun initViews() {
        tvHeroName = findViewById(R.id.tvHeroName)
        rvHeroes = findViewById(R.id.rvHeroes)
        roleBar = findViewById(R.id.roleBar)
        tvCoinAmount = findViewById(R.id.txtCoin)
        ivHero = findViewById(R.id.ivHero)
        tvCoinAmount.text = getCoinsDisplay()
        checkUser()

    }

    private fun checkUser(){
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            // Misal ambil data dari Realtime Database
            val uid = user.uid
            val databaseRef = FirebaseDatabase.getInstance().getReference("users").child(uid)

            databaseRef.get().addOnSuccessListener { snapshot ->
                val lastClaim = snapshot.child(LAST_CLAIM_DATE_KEY).getValue(String::class.java) ?: ""

                // simpan ke SharedPreferences
                val editor = sharedPrefs.edit()
                editor.putString(LAST_CLAIM_DATE_KEY, lastClaim)
                editor.apply()

                // cek daily reward
                checkDailyReward()
            }.addOnFailureListener {
                // handle error
                Log.e("MainActivity", "Gagal ambil data lastClaim", it)
            }
        }
    }





    private fun updateLoginText() {
        val tvLogin = findViewById<TextView>(R.id.tvLogin)
        val isLoggedIn = FirebaseAuth.getInstance().currentUser != null
        tvLogin.text = if (isLoggedIn) "Logout" else "Login"
    }
    private fun handleLoginLogout() {
        showLoading()
        val tvLogin = findViewById<TextView>(R.id.tvLogin)
        val user = FirebaseAuth.getInstance().currentUser

        if (user != null) {
            // Logout
            FirebaseAuth.getInstance().signOut()
            googleSignInClient.signOut()

            sharedPrefs.edit()
                .putBoolean(IS_LOGGED_IN_KEY, false)
                .putInt(COINS_KEY, 0)
                .putBoolean(IS_SUBSCRIBED, false)
                .apply()

            tvCoinAmount.text = "0"
            tvLogin.text = "Login"
            Toast.makeText(this, "Logout berhasil", Toast.LENGTH_SHORT).show()

            // ⏳ Beri delay agar loading muncul dulu
            Handler(Looper.getMainLooper()).postDelayed({
                hideLoading()
            }, 300)

        } else {
            // Saat login, jangan hide dulu — biar ditutup saat login selesai
            signInWithGoogle()
        }
    }


    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }



    private fun checkCoinsAndPromptLogin() {
        if (sharedPrefs.getInt(COINS_KEY, 0) == 0 && !sharedPrefs.getBoolean(IS_LOGGED_IN_KEY, false)) {
            AlertDialog.Builder(this)
                .setTitle("Login untuk Bonus Koin!")
                .setMessage("Dapatkan 7 koin gratis + reward harian 3 koin dengan login akun Google!")
                .setPositiveButton("Login") { _, _ ->
                    showLoading() // ⬅️ tambahkan ini biar ada animasi loading
                    signInWithGoogle()
                }
                .setNegativeButton("Nanti", null)
                .show()
        }
    }

    private fun signInWithGoogle() {
        signInLauncher.launch(googleSignInClient.signInIntent)
    }

    private fun validateSubscriptionStatus() {
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
                            .getReference("users/$uid/$IS_SUBSCRIBED")
                            .setValue(false)


                        Toast.makeText(this, "Langganan telah berakhir", Toast.LENGTH_SHORT).show()
                        getCoinsDisplay()
                    }
                    else{
                        val daysLeft = TimeUnit.MILLISECONDS.toDays(expireAt - serverTime)
                        FirebaseDatabase.getInstance("https://draftkuy-3c559-default-rtdb.asia-southeast1.firebasedatabase.app/")
                            .getReference("users/$uid/$SUB_DURATION_DAYS")
                            .setValue(daysLeft)
                        getCoinsDisplay()
                    }
                }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        showLoading()

        val credential = GoogleAuthProvider.getCredential(idToken, null)
        FirebaseAuth.getInstance().signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = FirebaseAuth.getInstance().currentUser
                    val userId = user?.uid ?: return@addOnCompleteListener
                    val userRef = FirebaseDatabase.getInstance("https://draftkuy-3c559-default-rtdb.asia-southeast1.firebasedatabase.app/")
                        .getReference("users/$userId")

                    userRef.get().addOnSuccessListener { snapshot ->
                        val editor = sharedPrefs.edit()
                        val localCoins = sharedPrefs.getInt(COINS_KEY, 0)
                        editor.putBoolean(IS_LOGGED_IN_KEY, true)

                        if (snapshot.exists()) {
                            // 🔄 Ambil data dari Firebase
                            val coins = snapshot.child(COINS_KEY).getValue(Int::class.java)
                            val isSubscribed = snapshot.child(IS_SUBSCRIBED).getValue(Boolean::class.java) ?: false
                            val lastClaim = snapshot.child(LAST_CLAIM_DATE_KEY).getValue(String::class.java) ?: ""
                            val subExpireTime = snapshot.child("sub_expire_time").getValue(Long::class.java) ?: 0L
                            val subDuration = snapshot.child("sub_duration_days").getValue(Long::class.java) ?: 0L
                            val totalSub = snapshot.child("total_Sub").getValue(Long::class.java) ?: 0L

                            Log.d("DailyReward", "Saved last claim date: $lastClaim")

                            if (coins == null || coins < 0 || coins > 99999) {
                                // ⚠️ Data rusak, reset
                                val resetCoins = 10
                                userRef.updateChildren(
                                    mapOf(
                                        "coins" to resetCoins,
                                        "last_login" to System.currentTimeMillis()
                                    )
                                )
                                editor.putInt(COINS_KEY, resetCoins)
                                Toast.makeText(this, "Data rusak. Direset ke 10 koin", Toast.LENGTH_SHORT).show()
                            } else {
                                // ✅ Data valid, simpan ke SharedPreferences
                                editor.putInt(COINS_KEY, coins)
                                editor.putBoolean(IS_SUBSCRIBED, isSubscribed)
                                editor.putString(LAST_CLAIM_DATE_KEY, lastClaim)
                                editor.putLong("sub_expire_time", subExpireTime)
                                editor.putLong(SUB_DURATION_DAYS, subDuration)
                                editor.putLong(TOTAL_SUB, totalSub)
                                validateSubscriptionStatus()
                                Toast.makeText(this, "Login berhasil. Sisa koin: $coins", Toast.LENGTH_SHORT).show()
                            }

                        } else {
                            // 🆕 User baru
                            val startingCoins = localCoins + 7
                            editor.putInt(COINS_KEY, startingCoins)
                            editor.putBoolean(IS_SUBSCRIBED, false)
                            editor.putString(LAST_CLAIM_DATE_KEY, "")
                            editor.putLong("sub_expire_time", 0L)
                            editor.putLong("sub_duration_days", 0L)
                            editor.putLong(TOTAL_SUB, 0L)

                            // Simpan data baru ke Firebase
                            userRef.setValue(
                                mapOf(
                                    "coins" to startingCoins,
                                    "is_subscribed" to false,
                                    "sub_expire_time" to 0L,
                                    "sub_duration_days" to 0L,
                                    TOTAL_SUB to 0L,
                                    "last_login" to System.currentTimeMillis()
                                )
                            )

                            Toast.makeText(this, "Login pertama! +7 Koin", Toast.LENGTH_SHORT).show()
                        }

                        editor.apply()
                        // 💾 Simpan ulang ke Firebase
                        saveUserDataToFirebase()

                        // 🔄 Update UI
                        tvCoinAmount.text = getCoinsDisplay()
                        updateLoginText()
                        hideLoading()
                    }
                } else {
                    hideLoading()
                    Toast.makeText(this, "Login gagal", Toast.LENGTH_SHORT).show()
                }
            }
    }


    private var loadingDialog: AlertDialog? = null

    private fun showLoading() {
        if (loadingDialog == null) {
            val view = layoutInflater.inflate(R.layout.dialog_loading, null)
            loadingDialog = AlertDialog.Builder(this, R.style.TransparentDialog)
                .setView(view)
                .setCancelable(false)
                .create()
        }
        if (loadingDialog?.isShowing != true) {
            loadingDialog?.show()
        }

    }

    private fun hideLoading() {
        loadingDialog?.dismiss()
    }


    private fun saveUserDataToFirebase() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val userRef = FirebaseDatabase.getInstance("https://draftkuy-3c559-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference("users/$uid")

        userRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                // User baru, beri 10 koin
                userRef.setValue(
                    mapOf(
                        "coins" to sharedPrefs.getInt(COINS_KEY,0),
                        "last_login" to ServerValue.TIMESTAMP,
                        "last_claim" to "",
                        "is_subscribed" to false
                    )
                ).addOnSuccessListener {
                    Log.d("Firebase", "User baru disimpan +10 koin")
                }
                checkDailyReward()
            } else {
                // User lama, hanya update waktu login
                userRef.child("last_login").setValue(ServerValue.TIMESTAMP)
                checkDailyReward()
                Log.d("Firebase", "User lama login, tidak dapat koin")
            }
        }.addOnFailureListener {
            Log.e("Firebase", "Gagal cek data user: ${it.message}")
        }
    }


    override fun onResume() {
        super.onResume()
        validateSubscriptionStatus()
        tvCoinAmount.text = getCoinsDisplay()
        hideLoading()
    }

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
        val coins = sharedPrefs.getInt(COINS_KEY,0)
        return coins
    }


    private fun loadAllHeroNames() {
        if (!DataHelper.isInitialized) {
            DataHelper.initialize(this)
        }
        allHeroNames = DataHelper.allHeroes.map { it.name }
        Log.d("HeroNames", "Loaded ${allHeroNames.size} hero names")
    }

    private fun setupRecyclerView() {
        rvHeroes.layoutManager = LinearLayoutManager(this)
        rvHeroes.adapter = HeroAdapter(emptyList())
    }

    private fun setupClickListeners() {
        for (i in 0 until roleBar.childCount) {
            val roleView = roleBar.getChildAt(i) as? TextView ?: continue
            roleView.setOnClickListener {
                setSelectedRole(roleView)
                handleRoleClick(roleView.text.toString())
            }
        }

        findViewById<View>(R.id.btnSearch).setOnClickListener {
            val isSubscribed = sharedPrefs.getBoolean(IS_SUBSCRIBED, false)
            if (!isSubscribed && getCoins() == 0 && !sharedPrefs.getBoolean(IS_LOGGED_IN_KEY, false)) {
                checkCoinsAndPromptLogin()
            } else {
                showSearchDialog()
            }
        }

    }

    private fun setSelectedRole(newSelected: TextView) {
        selectedRoleView?.let {
            it.paintFlags = it.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
            it.setTextColor(ContextCompat.getColor(this, R.color.yellow))
        }

        newSelected.paintFlags = newSelected.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        newSelected.setTextColor(ContextCompat.getColor(this, R.color.orange))

        selectedRoleView = newSelected
    }

    private fun handleRoleClick(role: String) {
        when (role.uppercase()) {
            "META" -> showMetaHeroes()
            else -> showHeroesForRole(role.lowercase())
        }
    }

    private fun showHeroesForRole(role: String) {
        currentHero?.let { hero ->
            val heroes = when (role.uppercase()) {
                "ROAM" -> hero.recommendation.roam
                "JUNGLER" -> hero.recommendation.jungler
                "MIDLANE" -> hero.recommendation.midlane
                "GOLDLANE" -> hero.recommendation.goldlane
                "XPLANE" -> hero.recommendation.xplane
                else -> emptyList()
            }
            rvHeroes.adapter = HeroAdapter(heroes)
        } ?: run {
            Toast.makeText(this, "Please search for a hero first", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMetaHeroes() {
        val metaHeroes = JsonMeta.loadMetaFromJson(this)
        rvHeroes.adapter = HeroAdapter(metaHeroes)
    }

    private fun showSearchDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_search, null)
        val input = dialogView.findViewById<AutoCompleteTextView>(R.id.etSearch).apply {
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
            setHintTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            threshold = 1
            setDropDownBackgroundResource(R.drawable.bg_dropdown)
            setDropDownVerticalOffset(resources.getDimensionPixelSize(R.dimen.dropdown_offset))
        }

        val adapter = ArrayAdapter(
            this,
            R.layout.item_dropdown,
            R.id.dropdown_item,
            allHeroNames
        )

        input.setAdapter(adapter)

        input.setOnItemClickListener { _, _, position, _ ->
            val selectedHero = adapter.getItem(position)
            searchDialog?.dismiss()

            if (selectedHero != null) {
                val isSubscribed = sharedPrefs.getBoolean(IS_SUBSCRIBED,false)
                val currentCoins = getCoins()
                validateSubscriptionStatus()
                if (currentCoins == 0 && !isSubscribed) {
                    Toast.makeText(this, "Anda tidak memiliki koin.", Toast.LENGTH_SHORT).show()
                    return@setOnItemClickListener // hentikan eksekusi lebih lanjut
                }

                if(!isSubscribed){
                    sharedPrefs.edit().putInt(COINS_KEY, currentCoins - 1).apply()
                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                    if (uid != null) {
                        FirebaseDatabase.getInstance("https://draftkuy-3c559-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference("users/$uid/coins")
                            .setValue(currentCoins - 1)
                    }

                    tvCoinAmount.text = getCoins().toString()
// Tambahkan ini: update ke Firebase juga

                }

                searchHero(selectedHero)
            }
        }

        searchDialog = AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setTitle("Search Hero")
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .create()

        searchDialog?.show()
    }

    // FUNGSI BARU: Ambil gambar hero berdasarkan nama
    private fun getHeroImageResource(heroName: String): Int {
        val resourceName = heroName.lowercase()
            .replace(" ", "")
            .replace("'", "")
            .replace("-", "")
            .replace("&", "")
            .replace(".", "")
        return resources.getIdentifier(resourceName, "drawable", packageName)
    }

    private fun searchHero(heroName: String) {
        Thread {
            try {
                val hero = DataHelper.getHeroRecommendations(heroName)
                runOnUiThread {
                    if (hero != null) {
                        currentHero = hero
                        tvHeroName.text = hero.name

                        // TAMBAHKAN INI: Set gambar hero utama
                        val imageRes = getHeroImageResource(hero.name)
                        if (imageRes != 0) {
                            ivHero.setImageResource(imageRes)
                        } else {
                            ivHero.setImageResource(R.drawable.default_hero)
                        }

                        showHeroesForRole("roam")
                        val defaultRoleView = findTextViewByText("ROAM")
                        defaultRoleView?.let { setSelectedRole(it) }
                    } else {
                        Toast.makeText(this, "Hero '$heroName' not found", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Search failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun findTextViewByText(text: String): TextView? {
        for (i in 0 until roleBar.childCount) {
            val tv = roleBar.getChildAt(i) as? TextView
            if (tv?.text?.toString()?.equals(text, ignoreCase = true) == true) return tv
        }
        return null
    }

    private inner class HeroAdapter(private val heroes: List<String>) :
        RecyclerView.Adapter<HeroAdapter.HeroViewHolder>() {

        inner class HeroViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val heroName: TextView = itemView.findViewById(R.id.heroName)
            val heroImage: ImageView = itemView.findViewById(R.id.ivHero) // TAMBAHKAN INI
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeroViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_hero, parent, false)
            return HeroViewHolder(view)
        }

        override fun onBindViewHolder(holder: HeroViewHolder, position: Int) {
            val heroName = heroes[position]
            holder.heroName.text = heroName

            // Set gambar untuk setiap hero
            val imageRes = getHeroImageResource(heroName)
            if (imageRes != 0) {
                holder.heroImage.setImageResource(imageRes)
                holder.heroImage.scaleType = ImageView.ScaleType.CENTER_CROP
            } else {
                holder.heroImage.setImageResource(R.drawable.default_hero)
                holder.heroImage.scaleType = ImageView.ScaleType.CENTER_CROP
            }
        }


        override fun getItemCount() = heroes.size
    }



    private fun checkDailyReward() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val isSubscribed = sharedPrefs.getBoolean(IS_SUBSCRIBED, false)
        if (isSubscribed) return

        val ref = FirebaseDatabase.getInstance("https://draftkuy-3c559-default-rtdb.asia-southeast1.firebasedatabase.app/")
            .getReference("users/$uid/ServerTime")
        val dummyData = mapOf("timestamp" to ServerValue.TIMESTAMP)

        ref.setValue(dummyData).addOnCompleteListener {
            ref.child("timestamp").get().addOnSuccessListener { snapshot ->
                val serverTimestamp = snapshot.value as? Long
                if (serverTimestamp != null) {
                    val serverDate = getDateOnly(serverTimestamp)
                    val lastClaimDate = sharedPrefs.getString(LAST_CLAIM_DATE_KEY, "")
                    Log.d("DailyReward", "Server timestamp: $serverTimestamp")
                    Log.d("DailyReward", "Last claim date: $lastClaimDate, Server date: $serverDate")

                    if(lastClaimDate == ""){
                        sharedPrefs.edit().putString(LAST_CLAIM_DATE_KEY,serverDate).apply()
                        syncCoinsToFirebase()
                    }
                    else if (serverDate != lastClaimDate) {
                        showCustomClaimDialog(serverDate)
                    }
                }
            }
        }
    }

    private fun syncCoinsToFirebase() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val coins = getCoins().coerceAtLeast(0)

        val userRef = FirebaseDatabase
            .getInstance("https://draftkuy-3c559-default-rtdb.asia-southeast1.firebasedatabase.app/")
            .getReference("users/$uid")

        val lastLogin = ServerValue.TIMESTAMP
        val lastClaim = sharedPrefs.getString(LAST_CLAIM_DATE_KEY,"")

        userRef.child(COINS_KEY).setValue(coins)
        userRef.child(LAST_LOGIN).setValue(lastLogin)
        userRef.child(LAST_CLAIM_DATE_KEY).setValue(lastClaim)

        Log.d("DailyReward", "Saved last claim date: $lastClaim")

    }
    private fun isUserSubscribed(callback: (Boolean) -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return callback(false)

        FirebaseDatabase.getInstance()
            .getReference("users/$uid/subscribed_until")
            .get()
            .addOnSuccessListener { snapshot ->
                val timestamp = snapshot.value as? Long ?: 0L
                callback(System.currentTimeMillis() < timestamp)
            }
            .addOnFailureListener {
                callback(false)
            }
    }

    private fun showCustomClaimDialog(currentDate: String) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(this, "Login dulu untuk klaim koin harian.", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.daily_coin, null)
        val dialog = Dialog(this)
        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(false)

        val btnClaim = dialogView.findViewById<Button>(R.id.btnClaim)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvMessage)

        tvTitle.text = "Klaim Koin Harian"
        tvMessage.text = "Dapatkan 3 koin gratis hari ini!"

        btnClaim.setOnClickListener {
            val currentCoins = sharedPrefs.getInt(COINS_KEY, 0)
            val newCoins = currentCoins + 3

            sharedPrefs.edit()
                .putInt(COINS_KEY, newCoins)
                .putString(LAST_CLAIM_DATE_KEY, currentDate)
                .apply()

            Log.d("DailyReward", "Saved last claim date: $currentDate")
            syncCoinsToFirebase()

            Toast.makeText(this, "3 koin ditambahkan!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            tvCoinAmount.text = getCoinsDisplay()
        }

        dialog.show()
    }



    private fun getDateOnly(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(timestamp))
    }



    // TUTORIAL AWAL DOWNLOAD APLIKASI //
    private fun showTutorial() {
        val appPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (!appPrefs.getBoolean(TUTORIAL_SHOWN_KEY, false)) {
            sharedPrefs.edit().apply {
                putInt(COINS_KEY, 1) // Changed from 7 to 1 coins
                apply()
            }
            tvCoinAmount.text = getCoinsDisplay()

            findViewById<View>(R.id.ivCoin).post {
                TapTargetSequence(this).targets(
                    TapTarget.forView(
                        findViewById(R.id.ivCoin),
                        "Koin Pencarian",
                        "Setiap pencarian hero akan mengurangi 1 koin Anda"
                    ).apply {
                        outerCircleColor(R.color.orange)
                        targetCircleColor(R.color.transparent)
                        titleTextSize(28)
                        descriptionTextSize(24)
                        descriptionTypeface(Typeface.DEFAULT_BOLD)
                        textColor(R.color.white)
                        dimColor(R.color.black)
                        drawShadow(true)
                        cancelable(false)
                        transparentTarget(true)
                        targetRadius(35)
                    },
                    TapTarget.forView(
                        findViewById(R.id.btnSearch),
                        "Pencarian Hero",
                        "Gunakan tombol ini untuk mencari hero yang ingin Anda counter"
                    ).apply {
                        outerCircleColor(R.color.blue)
                        targetCircleColor(R.color.transparent)
                        titleTextSize(28)
                        descriptionTextSize(24)
                        textColor(R.color.white)
                        descriptionTypeface(Typeface.DEFAULT_BOLD)
                        dimColor(R.color.black)
                        drawShadow(true)
                        cancelable(false)
                        transparentTarget(true)
                    },
                    TapTarget.forView(
                        findViewById(R.id.ivHero),
                        "Hero Target",
                        "Nama hero yang Anda pilih akan muncul di sini"
                    ).apply {
                        outerCircleColor(R.color.purple_500)
                        targetCircleColor(R.color.transparent)
                        titleTextSize(28)
                        descriptionTextSize(24)
                        descriptionTypeface(Typeface.DEFAULT_BOLD)
                        textColor(R.color.white)
                        dimColor(R.color.black)
                        drawShadow(true)
                        cancelable(false)
                        transparentTarget(true)
                    },
                    TapTarget.forView(
                        findViewById(R.id.rvHeroes),
                        "Rekomendasi Hero",
                        "Daftar hero terbaik untuk counter akan muncul di sini"
                    ).apply {
                        outerCircleColor(R.color.teal_700)
                        targetCircleColor(R.color.transparent)
                        titleTextSize(28)
                        descriptionTextSize(24)
                        descriptionTypeface(Typeface.DEFAULT_BOLD)
                        textColor(R.color.white)
                        dimColor(R.color.black)
                        drawShadow(true)
                        cancelable(false)
                        transparentTarget(true)
                    }
                ).listener(object : TapTargetSequence.Listener {
                    override fun onSequenceFinish() {
                        appPrefs.edit().putBoolean(TUTORIAL_SHOWN_KEY, true).apply()

                        val container = findViewById<FrameLayout>(R.id.tutorialContainer)
                        val videoView = findViewById<VideoView>(R.id.videoView)
                        val closeBtn = findViewById<Button>(R.id.btnCloseVideo)

                        container.visibility = View.VISIBLE
                        closeBtn.visibility = View.GONE


                        val videoUri = Uri.parse("android.resource://${packageName}/${R.raw.tutorial_video}")
                        videoView.setVideoURI(videoUri)

                        videoView.setOnPreparedListener { mp ->
                            mp.isLooping = false
                            videoView.start()
                        }
                        videoView.setOnCompletionListener {
                            closeBtn.visibility = View.VISIBLE
                        }

                        closeBtn.setOnClickListener {
                            videoView.stopPlayback()
                            container.visibility = View.GONE
                        }
                    }


                    override fun onSequenceStep(lastTarget: TapTarget?, targetClicked: Boolean) {}
                    override fun onSequenceCanceled(lastTarget: TapTarget?) {}
                }).start()
            }
        }
    }



}