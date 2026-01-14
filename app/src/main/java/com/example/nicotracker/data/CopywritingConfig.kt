package com.example.nicotracker.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// Modèle pour un type de mission (ex: "Emailing" avec unité "Mails")
data class CopyAsset(val name: String, val unit: String)

class CopywritingConfigManager(context: Context) {
    private val prefs = context.getSharedPreferences("nico_copy_config", Context.MODE_PRIVATE)
    private val gson = Gson()

    // --- LISTE DES CLIENTS ---
    fun getClients(): List<String> {
        val json = prefs.getString("clients_list", null) ?: return listOf("Projet Perso", "Client A")
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveClients(list: List<String>) {
        prefs.edit().putString("clients_list", gson.toJson(list)).apply()
    }

    // --- LISTE DES MISSIONS (ASSETS) ---
    fun getAssets(): List<CopyAsset> {
        val json = prefs.getString("assets_list", null) ?: return listOf(
            CopyAsset("Emailing", "Mails"),
            CopyAsset("Page de Vente", "Mots"),
            CopyAsset("Publicité (Ads)", "Variations"),
            CopyAsset("Contenu Social", "Posts")
        )
        val type = object : TypeToken<List<CopyAsset>>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveAssets(list: List<CopyAsset>) {
        prefs.edit().putString("assets_list", gson.toJson(list)).apply()
    }

    fun addSuggestion(name: String) {
        if (name.isBlank()) return
        val current = getClients().toMutableList()
        // Si le nom existe déjà, on le supprime pour le remettre en haut de liste (récent)
        current.remove(name)
        current.add(0, name)
        // On ne garde par exemple que les 15 derniers pour ne pas saturer
        val limited = current.take(15)
        saveClients(limited)
    }
}