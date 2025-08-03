package com.example.draftkuy.activities

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.draftkuy.databinding.ActivityIntroBinding

class IntroActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIntroBinding
    private lateinit var sharedPref: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inisialisasi View Binding
        binding = ActivityIntroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup SharedPreferences
        sharedPref = getSharedPreferences("app_prefs", MODE_PRIVATE)

        // Cek apakah intro sudah pernah ditampilkan
        if (sharedPref.getBoolean("intro_shown", false)) {
            startMainActivity()
            return
        }

        // Handle tombol mulai
        binding.btnStart.setOnClickListener {
            sharedPref.edit().putBoolean("intro_shown", true).apply()
            startMainActivity()
        }
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}