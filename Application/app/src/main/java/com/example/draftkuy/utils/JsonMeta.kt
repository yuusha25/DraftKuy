package com.example.draftkuy.utils

import android.content.Context
import com.example.draftkuy.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class MetaData(
    val rec: String,
    val hero: List<String>
)

class JsonMeta {
    companion object {
        fun loadMetaFromJson(context: Context): List<String> {
            val inputStream = context.resources.openRawResource(R.raw.meta)
            val jsonText = inputStream.bufferedReader().use { it.readText() }
            val metaData = Gson().fromJson<List<MetaData>>(jsonText, object : TypeToken<List<MetaData>>() {}.type)
            return metaData.firstOrNull()?.hero ?: emptyList()
        }
    }
}
