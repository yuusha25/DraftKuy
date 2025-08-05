package com.example.draftkuy.activities

import android.app.Dialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Paint
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
import com.example.draftkuy.R
import com.example.draftkuy.models.Hero
import com.example.draftkuy.utils.DataHelper
import com.example.draftkuy.utils.JsonMeta
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetSequence
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes



import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import java.text.SimpleDateFormat
import java.util.*

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
    private val LAST_CLAIM_DATE_KEY = "last_claim_date"
    private val IS_LOGGED_IN_KEY = "is_logged_in"
    private val TUTORIAL_SHOWN_KEY = "tutorial_shown"

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

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun showTutorial() {
        val appPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (!appPrefs.getBoolean(TUTORIAL_SHOWN_KEY, false)) {
            sharedPrefs.edit().apply {
                putInt(COINS_KEY, 3) // Changed from 7 to 3 coins
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
                        titleTextSize(18)
                        descriptionTextSize(14)
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
                        titleTextSize(18)
                        descriptionTextSize(14)
                        textColor(R.color.white)
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
                        titleTextSize(18)
                        descriptionTextSize(14)
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
                        titleTextSize(18)
                        descriptionTextSize(14)
                        textColor(R.color.white)
                        dimColor(R.color.black)
                        drawShadow(true)
                        cancelable(false)
                        transparentTarget(true)
                    }
                ).listener(object : TapTargetSequence.Listener {
                    override fun onSequenceFinish() {
                        appPrefs.edit().putBoolean(TUTORIAL_SHOWN_KEY, true).apply()
                    }
                    override fun onSequenceStep(lastTarget: TapTarget?, targetClicked: Boolean) {}
                    override fun onSequenceCanceled(lastTarget: TapTarget?) {}
                }).start()
            }
        } else {
            checkDailyReward()
        }
    }

    private fun checkCoinsAndPromptLogin() {
        if (sharedPrefs.getInt(COINS_KEY, 0) == 0 && !sharedPrefs.getBoolean(IS_LOGGED_IN_KEY, false)) {
            AlertDialog.Builder(this)
                .setTitle("Login untuk Bonus Koin!")
                .setMessage("Dapatkan 10 koin gratis + reward harian 5 koin dengan login akun Google!")
                .setPositiveButton("Login") { _, _ -> signInWithGoogle() }
                .setNegativeButton("Nanti", null)
                .show()
        }
    }

    private fun signInWithGoogle() {
        signInLauncher.launch(googleSignInClient.signInIntent)
    }


    private fun firebaseAuthWithGoogle(idToken: String) {
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
                        editor.putBoolean(IS_LOGGED_IN_KEY, true)

                        if (snapshot.exists()) {
                            val coins = snapshot.child("coins").getValue(Int::class.java)

                            if (coins == null || coins < 0 || coins > 99999) {
                                // 🧹 Data rusak → reset
                                userRef.setValue(
                                    mapOf(
                                        "coins" to 10,
                                        "last_login" to ServerValue.TIMESTAMP
                                    )
                                )
                                editor.putInt(COINS_KEY, 10)
                                Toast.makeText(this, "Data rusak. Direset +10 koin", Toast.LENGTH_SHORT).show()
                            } else {
                                // 🟢 Data valid
                                editor.putInt(COINS_KEY, coins)
                                Toast.makeText(this, "Login berhasil. Sisa koin: $coins", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            // 🆕 User baru
                            editor.putInt(COINS_KEY, 10)
                            Toast.makeText(this, "Login pertama! +10 Koin", Toast.LENGTH_SHORT).show()
                        }

                        editor.apply()

                        // ✅ Simpan/update data user ke Firebase
                        saveUserDataToFirebase()

                        // 💰 Update UI
                        tvCoinAmount.text = getCoinsDisplay()
                    }
                } else {
                    Toast.makeText(this, "Login gagal", Toast.LENGTH_SHORT).show()
                }
            }
    }




    private fun saveUserDataToFirebase() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val userRef = FirebaseDatabase.getInstance("https://draftkuy-3c559-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference("users/$uid")

        userRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                // User baru, beri 10 koin
                userRef.setValue(
                    mapOf(
                        "coins" to 10,
                        "last_login" to ServerValue.TIMESTAMP
                    )
                ).addOnSuccessListener {
                    Log.d("Firebase", "User baru disimpan +10 koin")
                }
            } else {
                // User lama, hanya update waktu login
                userRef.child("last_login").setValue(ServerValue.TIMESTAMP)
                Log.d("Firebase", "User lama login, tidak dapat koin")
            }
        }.addOnFailureListener {
            Log.e("Firebase", "Gagal cek data user: ${it.message}")
        }
    }


    override fun onResume() {
        super.onResume()
        tvCoinAmount.text = getCoinsDisplay()
    }

    private fun getCoinsDisplay(): String {
        val prefs = getSharedPreferences("user_data", MODE_PRIVATE)
        val isSubscribed = prefs.getBoolean("is_subscribed", false)

        return if (isSubscribed) "∞" else getCoins().toString()
    }

    private fun getCoins(): Int {
        val prefs = getSharedPreferences("user_data", MODE_PRIVATE)
        return prefs.getInt("coins", 0)
    }

    private fun initViews() {
        tvHeroName = findViewById(R.id.tvHeroName)
        rvHeroes = findViewById(R.id.rvHeroes)
        roleBar = findViewById(R.id.roleBar)
        tvCoinAmount = findViewById(R.id.txtCoin)
        ivHero = findViewById(R.id.ivHero)

        tvCoinAmount.text = getCoinsDisplay()
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
            if (getCoins() == 0 && !sharedPrefs.getBoolean(IS_LOGGED_IN_KEY, false)) {
                checkCoinsAndPromptLogin() // Prompt login jika koin habis
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
                val prefs = getSharedPreferences("user_data", MODE_PRIVATE)
                val isSubscribed = prefs.getBoolean("is_subscribed", false)
                val currentCoins = getCoins()

                if (currentCoins == 0) {
                    Toast.makeText(this, "Anda tidak memiliki koin.", Toast.LENGTH_SHORT).show()
                    return@setOnItemClickListener // hentikan eksekusi lebih lanjut
                }

                if(!isSubscribed){
                    prefs.edit().putInt("coins", currentCoins - 1).apply()

// Tambahkan ini: update ke Firebase juga
                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                    if (uid != null) {
                        FirebaseDatabase.getInstance("https://draftkuy-3c559-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference("users/$uid/coins")
                            .setValue(currentCoins - 1)
                    }

                }

                tvCoinAmount.text = getCoins().toString()
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
        val prefs = getSharedPreferences("user_data", MODE_PRIVATE)
        val isSubscribed = prefs.getBoolean("is_subscribed", false)
        if (isSubscribed) return

        val ref = FirebaseDatabase.getInstance("https://drafkuy-31f7e-default-rtdb.asia-southeast1.firebasedatabase.app/")
            .getReference("server_time")
        val dummyData = mapOf("timestamp" to ServerValue.TIMESTAMP)

        ref.setValue(dummyData).addOnCompleteListener {
            ref.child("timestamp").get().addOnSuccessListener { snapshot ->
                val serverTimestamp = snapshot.value as? Long
                if (serverTimestamp != null) {
                    val serverDate = getDateOnly(serverTimestamp)
                    val lastClaimDate = prefs.getString(LAST_CLAIM_DATE_KEY, "")
                    Log.d("DailyReward", "Server timestamp: $serverTimestamp")
                    Log.d("DailyReward", "Last claim date: $lastClaimDate, Server date: $serverDate")

                    if (serverDate != lastClaimDate) {
                        showCustomClaimDialog(serverDate)
                    }
                }
            }
        }
    }

    private fun syncCoinsToFirebase() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val coins = getCoins()

        val userRef = FirebaseDatabase
            .getInstance("https://draftkuy-3c559-default-rtdb.asia-southeast1.firebasedatabase.app/")
            .getReference("users/$uid")

        userRef.child("coins").setValue(coins)
        userRef.child("last_login").setValue(ServerValue.TIMESTAMP)
    }
    private fun showCustomClaimDialog(currentDate: String) {
        val dialogView = layoutInflater.inflate(R.layout.daily_coin, null)
        val dialog = Dialog(this)
        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(false)

        val btnClaim = dialogView.findViewById<Button>(R.id.btnClaim)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvMessage)

        tvTitle.text = "Klaim Koin Harian"
        tvMessage.text = "Dapatkan 5 koin gratis hari ini!"

        btnClaim.setOnClickListener {
            val prefs = getSharedPreferences("user_data", MODE_PRIVATE)
            val currentCoins = prefs.getInt("coins", 0)
            val newCoins = currentCoins + 5

            prefs.edit()
                .putInt(COINS_KEY, newCoins)
                .putString(LAST_CLAIM_DATE_KEY, currentDate)
                .apply()

            // 🔁 Simpan ke Firebase dari sharedPrefs yang sudah diupdate
            syncCoinsToFirebase()

            Toast.makeText(this, "5 koin ditambahkan!", Toast.LENGTH_SHORT).show()
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



}