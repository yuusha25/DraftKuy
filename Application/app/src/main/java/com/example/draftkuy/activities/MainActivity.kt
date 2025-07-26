package com.example.draftkuy.activities

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.draftkuy.R
import com.example.draftkuy.models.Hero
import com.example.draftkuy.utils.DataHelper

class MainActivity : AppCompatActivity() {

    private lateinit var heroTable: TableLayout
    private lateinit var btnSearch: Button
    private lateinit var btnMeta: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        supportActionBar?.hide()
        initializeViews()
        initializeData()
        setupButtonListeners()
    }

    private fun initializeViews() {
        heroTable = findViewById(R.id.heroTable)
        btnSearch = findViewById(R.id.btnSearch)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun initializeData() {
        showLoading(true)
        Thread {
            try {
                DataHelper.initialize(this@MainActivity)
                runOnUiThread {
                    showLoading(false)
                    Toast.makeText(
                        this@MainActivity,
                        "Hero data loaded successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showLoading(false)
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to load hero data: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e("MainActivity", "Data initialization failed", e)
                }
            }
        }.start()
    }

    private fun setupButtonListeners() {
        btnSearch.setOnClickListener { showSearchDialog() }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showSearchDialog() {
        val input = AutoCompleteTextView(this).apply {
            hint = "Enter hero name (e.g., Akai)"
            threshold = 1 // Mulai menampilkan saran setelah 1 karakter diketik
        }

        AlertDialog.Builder(this)
            .setTitle("Search Hero")
            .setView(input)
            .setPositiveButton("Search") { dialog, _ ->
                val heroName = input.text.toString().trim()
                if (heroName.isNotEmpty()) {
                    searchHero(heroName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()

        // Setup adapter dan filter untuk autocomplete
        setupAutoCompleteAdapter(input)
    }

    private fun setupAutoCompleteAdapter(input: AutoCompleteTextView) {
        val adapter = object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_dropdown_item_1line,
            mutableListOf()
        ) {
            override fun getFilter(): Filter {
                return object : Filter() {
                    override fun performFiltering(constraint: CharSequence?): FilterResults {
                        val results = FilterResults()
                        val suggestions = mutableListOf<String>()

                        if (DataHelper.isInitialized && !constraint.isNullOrEmpty()) {
                            val filterPattern = constraint.toString().lowercase().trim()
                            DataHelper.allHeroes.forEach { hero ->
                                if (hero.name.lowercase().contains(filterPattern)) {
                                    suggestions.add(hero.name)
                                }
                            }
                        }

                        results.values = suggestions
                        results.count = suggestions.size
                        return results
                    }

                    @Suppress("UNCHECKED_CAST")
                    override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                        clear()
                        results?.values?.let {
                            addAll(it as List<String>)
                            notifyDataSetChanged()
                        }
                    }
                }
            }
        }

        input.setAdapter(adapter)

        // Load semua nama hero saat data siap
        if (DataHelper.isInitialized) {
            adapter.addAll(DataHelper.allHeroes.map { it.name })
        }
    }

    private fun searchHero(heroName: String) {
        if (!DataHelper.isInitialized) {
            Toast.makeText(this, "Data not ready, please try again in a few seconds", Toast.LENGTH_SHORT).show()
            return
        }

        showLoading(true)
        Thread {
            val hero = DataHelper.getHeroRecommendations(heroName)
            runOnUiThread {
                showLoading(false)
                if (hero != null) {
                    displayHeroRecommendations(hero)
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Hero '$heroName' not found. Try another name.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun displayHeroRecommendations(hero: Hero) {
        // Clear existing rows except header
        while (heroTable.childCount > 1) {
            heroTable.removeViewAt(1)
        }

        // Log the received data for debugging
        Log.d("MainActivity", "Displaying recommendations for: ${hero.name}")
        Log.d("MainActivity", "Roam: ${hero.recommendation.roam}")
        Log.d("MainActivity", "Jungler: ${hero.recommendation.jungler}")
        Log.d("MainActivity", "Midlane: ${hero.recommendation.midlane}")
        Log.d("MainActivity", "Goldlane: ${hero.recommendation.goldlane}")
        Log.d("MainActivity", "Xplane: ${hero.recommendation.xplane}")

        // Add recommendations for each role
        for (i in 0 until 5) {
            TableRow(this).apply {
                // Create list of hero names for this row
                val heroes = listOf(
                    hero.recommendation.roam.getOrNull(i) ?: "-",
                    hero.recommendation.jungler.getOrNull(i) ?: "-",
                    hero.recommendation.midlane.getOrNull(i) ?: "-",
                    hero.recommendation.goldlane.getOrNull(i) ?: "-",
                    hero.recommendation.xplane.getOrNull(i) ?: "-"
                )

                // Create TextViews for each hero name
                heroes.forEach { heroName ->
                    addView(TextView(context).apply {
                        text = heroName
                        setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER
                        setPadding(8, 16, 8, 16)
                    })
                }
                heroTable.addView(this)
            }
        }
    }
}