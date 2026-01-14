package com.example.nicotracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "journal_entry_table")
data class JournalEntry(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    // Catégorie principale
    val categoryName: String,

    // Sous-catégorie optionnelle
    val subCategoryId: Int? = null,

    // Date de l’entrée
    val date: Date = Date(),

    // ---------- SPORT ----------
    val sportDurationMinutes: Int? = null,
    val sportIntensity: Int? = null,

    // ---------- REPAS ----------
    val mealCalories: Int? = null,
    // --- NOUVEAUX CHAMPS MACROS ---
    val mealProtein: Int? = null, // Protéines (P)
    val mealCarbs: Int? = null,   // Glucides (G)
    val mealLipids: Int? = null,  // Lipides (L)
    // ------------------------------
    val mealQuality: Int? = null,

    // --- LES CHAMPS INVISIBLES (Pour l'IA uniquement) ---
    val mealSugar: Double? = null,        // Stocké mais pas affiché
    val mealSaturatedFat: Double? = null, // Stocké mais pas affiché
    val mealSodium: Double? = null,       // Stocké mais pas affiché
    val mealFibers: Double? = null,       // Stocké mais pas affiché

    // ---------- SOMMEIL ----------
    val sleepBedTime: String? = null,
    val sleepWakeTime: String? = null,
    val sleepDuration: String? = null,
    val sleepQuality: Int? = null,
    val sleepWokeUpWithAlarm: Boolean? = null,

    // ---------- ACTION PRODUCTIVE ----------
    val productiveDurationMinutes: Int? = null,
    val productiveFocus: Int? = null,

    // ---------- TEMPS D'ÉCRAN ----------
    val screenDurationMinutes: Int? = null,

    // ---------- NOMBRE DE PAS ----------
    val stepsCount: Int? = null,

    // ---------- HUMEUR ----------
    val moodScore: Int? = null,

    // ---------- DÉPENSE ----------
    val depensePrice: Double? = null,

    // ---------- REVENUS ----------
    val incomeAmount: Double? = null,

    // ---------- DÉFIS  ----------
    val challengeTitle: String? = null,
    val challengeDurationMinutes: Int? = null,
    val challengeQuantity: Int? = null,
    val challengeSuccess: Int? = null,
    val challengeDifficulty: Int? = null,
    val challengeState: Int? = null,

    // ---------- CHAMPS POLYVALENTS (Système Smart Tags) ----------
    // Ces champs servent à stocker des données complexes (ex: Copywriting)
    // sans avoir à créer une colonne pour chaque petit détail.

    // Sert à stocker le PILIER (ex: "Production", "Formation", "Prospection")
    val complexity: String? = null,

    // Sert à stocker les TAGS concaténés (ex: "Email, Client A, Ads")
    val tags: String? = null,

    // Sert à stocker une QUANTITÉ variable (ex: Nombre de mots, Nombre de DM envoyés)
    val volume: Double? = null,

    // ---------- COMMUN ----------
    val comment: String? = null
)

// ==========================================
// FONCTION DE NETTOYAGE (VERSION CORRIGÉE)
// ==========================================
fun JournalEntry.sanitize(): JournalEntry {
    var cleaned = this

    // 1. Si ce n'est pas du SPORT
    if (categoryName != "Sport") {
        cleaned = cleaned.copy(
            sportDurationMinutes = null,
            sportIntensity = null
        )
    }

    // 2. Si ce n'est pas un REPAS
    if (categoryName != "Repas") {
        cleaned = cleaned.copy(
            mealCalories = null,
            mealProtein = null,
            mealCarbs = null,
            mealLipids = null,
            mealQuality = null,
            mealSugar = null,
            mealSaturatedFat = null,
            mealSodium = null
        )
    }

    // 3. Si ce n'est pas le SOMMEIL (C'est ici qu'on a ajouté les champs !)
    if (categoryName != "Sommeil") {
        cleaned = cleaned.copy(
            sleepDuration = null,
            sleepQuality = null,
            sleepWokeUpWithAlarm = null,
            sleepBedTime = null, // <--- AJOUTÉ
            sleepWakeTime = null // <--- AJOUTÉ
        )
    }

    // 4. Si ce n'est pas une ACTION PRODUCTIVE
    if (categoryName != "Action productive") {
        cleaned = cleaned.copy(
            productiveDurationMinutes = null,
            productiveFocus = null
        )
    }

    // 5. Si ce n'est pas du TEMPS D'ÉCRAN
    if (categoryName != "Temps d'écran") {
        cleaned = cleaned.copy(
            screenDurationMinutes = null
        )
    }

    // 6. Si ce n'est pas le NOMBRE DE PAS
    if (categoryName != "Nombre de pas") {
        cleaned = cleaned.copy(
            stepsCount = null
        )
    }

    // 7. Si ce n'est pas l'HUMEUR
    if (categoryName != "Humeur") {
        cleaned = cleaned.copy(
            moodScore = null
        )
    }

    // 8. Si ce n'est pas une DÉPENSE
    if (categoryName != "Dépense") {
        cleaned = cleaned.copy(
            depensePrice = null
        )
    }

    // 9. Si ce n'est pas des REVENUS
    if (categoryName != "Revenus") {
        cleaned = cleaned.copy(
            incomeAmount = null
        )
    }

    // 10. Si ce n'est pas un DÉFI
    if (categoryName != "Défis") {
        cleaned = cleaned.copy(
            challengeTitle = null,
            challengeDurationMinutes = null,
            challengeQuantity = null,
            challengeSuccess = null,
            challengeDifficulty = null,
            challengeState = null
        )
    }

    return cleaned
}
