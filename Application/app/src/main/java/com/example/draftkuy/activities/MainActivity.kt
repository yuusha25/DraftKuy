package com.example.draftkuy.activities

import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.content.pm.ActivityInfo
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.draftkuy.R
import com.example.draftkuy.models.Hero
import com.example.draftkuy.utils.DataHelper
import com.example.draftkuy.utils.JsonMeta
import android.content.Intent

import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetSequence

import com.example.draftkuy.activities.TopUpActivity


class MainActivity : AppCompatActivity() {

    private lateinit var tvHeroName: TextView
    private lateinit var rvHeroes: RecyclerView
    private lateinit var roleBar: ViewGroup
    private lateinit var tvCoinAmount: TextView
    private lateinit var ivHero: ImageView // TAMBAHKAN INI
    private lateinit var allHeroNames: List<String>
    private var searchDialog: AlertDialog? = null
    private var currentHero: Hero? = null
    private var selectedRoleView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // [TAMBAHKAN INI] - Pengecekan intro sebelum lanjut
//        val introPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
//        if (!introPrefs.getBoolean("intro_shown", false)) {
//            startActivity(Intent(this, IntroActivity::class.java))
//            finish()
//            return  // Keluar dari onCreate jika intro perlu ditampilkan
//        }

        // [KODE YANG SUDAH ADA] - Lanjut ke MainActivity
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnTopUp = findViewById<ImageButton>(R.id.btnTopUp)
        btnTopUp.setOnClickListener {
            val intent = Intent(this, TopUpActivity::class.java)
            startActivity(intent)
        }
        initViews()
        setupRecyclerView()
        setupClickListeners()
        loadAllHeroNames()
        supportActionBar?.hide()

        showTutorial()

    }

    private fun showTutorial() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("tutorial_shown", false)) {
            findViewById<View>(R.id.ivCoin).post {
                TapTargetSequence(this)
                    .targets(
                        // Target 1: Icon Koin
                        TapTarget.forView(
                            findViewById(R.id.ivCoin),
                            "Koin Pencarian",
                            "Setiap pencarian hero akan mengurangi 1 koin Anda"
                        )
                            .outerCircleColor(R.color.orange)
                            .targetCircleColor(R.color.transparent) // Menggunakan resource warna
                            .titleTextSize(18)
                            .descriptionTextSize(14)
                            .textColor(R.color.white) // Menggunakan resource warna
                            .dimColor(R.color.black)
                            .drawShadow(true)
                            .cancelable(false)
                            .transparentTarget(true)
                            .targetRadius(35),

                        // Target 2: Tombol Search
                        TapTarget.forView(
                            findViewById(R.id.btnSearch),
                            "Pencarian Hero",
                            "Gunakan tombol ini untuk mencari hero yang ingin Anda counter"
                        )
                            .outerCircleColor(R.color.blue)
                            .targetCircleColor(R.color.transparent)
                            .titleTextSize(18)
                            .descriptionTextSize(14)
                            .textColor(R.color.white)
                            .dimColor(R.color.black)
                            .drawShadow(true)
                            .cancelable(false)
                            .transparentTarget(true),

                        // Target 3: Nama Hero
                        TapTarget.forView(
                            findViewById(R.id.ivHero),
                            "Hero Target",
                            "Nama hero yang Anda pilih akan muncul di sini"
                        )
                            .outerCircleColor(R.color.purple_500)
                            .targetCircleColor(R.color.transparent)
                            .titleTextSize(18)
                            .descriptionTextSize(14)
                            .textColor(R.color.white)
                            .dimColor(R.color.black)
                            .drawShadow(true)
                            .cancelable(false)
                            .transparentTarget(true),

                        // Target 4: Daftar Rekomendasi
                        TapTarget.forView(
                            findViewById(R.id.rvHeroes),
                            "Rekomendasi Hero",
                            "Daftar hero terbaik untuk counter akan muncul di sini"
                        )
                            .outerCircleColor(R.color.teal_700)
                            .targetCircleColor(R.color.transparent)
                            .titleTextSize(18)
                            .descriptionTextSize(14)
                            .textColor(R.color.white)
                            .dimColor(R.color.black)
                            .drawShadow(true)
                            .cancelable(false)
                            .transparentTarget(true)
                    )
                    .listener(object : TapTargetSequence.Listener {
                        override fun onSequenceFinish() {
                            prefs.edit().putBoolean("tutorial_shown", true).apply()
                        }

                        override fun onSequenceStep(lastTarget: TapTarget?, targetClicked: Boolean) {}
                        override fun onSequenceCanceled(lastTarget: TapTarget?) {}
                    })
                    .start()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tvCoinAmount.text = getCoins().toString()
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

        tvCoinAmount.text = getCoins().toString()
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
            showSearchDialog()
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


}