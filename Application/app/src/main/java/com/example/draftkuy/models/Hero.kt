package com.example.draftkuy.models

data class Hero(
    val name: String,
    val recommendation: Recommendation
)

data class Recommendation(
    val roam: List<String>,
    val jungler: List<String>,
    val midlane: List<String>,
    val goldlane: List<String>,
    val xplane: List<String>
)