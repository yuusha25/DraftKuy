package com.example.draftkuy.utils

import android.content.Context
import android.util.Log
import com.example.draftkuy.R
import com.example.draftkuy.models.Hero
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object DataHelper {
    // Ubah menjadi public lateinit var agar bisa diakses dari luar
    lateinit var allHeroes: List<Hero>
    var isInitialized = false
        private set

    fun initialize(context: Context) {
        try {
            val inputStream = context.resources.openRawResource(R.raw.heroes)
            val json = inputStream.bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<Hero>>() {}.type
            allHeroes = Gson().fromJson(json, type)
            isInitialized = true

            // Log initialization details
            Log.d("DataHelper", "Data initialized with ${allHeroes.size} heroes")
            Log.d("DataHelper", "Sample heroes: ${allHeroes.take(3).map { it.name }}")

            // Check if Akai exists
            val akaiExists = allHeroes.any { it.name.equals("Akai", ignoreCase = true) }
            Log.d("DataHelper", "Akai exists: $akaiExists")
        } catch (e: Exception) {
            Log.e("DataHelper", "Error initializing data", e)
            throw RuntimeException("Failed to initialize hero data", e)
        }
    }

    fun getHeroRecommendations(heroName: String): Hero? {
        if (!isInitialized) {
            Log.w("DataHelper", "Data not initialized when getting recommendations")
            return null
        }

        // Normalize the hero name: remove spaces and convert to lower case
        val normalizedInput = heroName.replace(" ", "").lowercase()

        return allHeroes.firstOrNull { hero ->
            hero.name.replace(" ", "").lowercase() == normalizedInput
        }?.also {
            Log.d("DataHelper", "Found hero: ${it.name}")
        }
    }
}