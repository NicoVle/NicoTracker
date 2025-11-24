package com.example.nicotracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "journal_entry_table")
data class JournalEntry(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    // Catégorie principale : Sport, Repas, Sommeil, etc.
    val categoryName: String,

    // Sous-catégorie optionnelle (clé vers SubCategory.id)
    val subCategoryId: Int? = null,

    // Date de l’entrée
    val date: Date = Date(),

    // ---------- SPORT ----------
    // Sport : Subcategory / Durée / Intensité (1–10) / Commentaire
    val sportDurationMinutes: Int? = null,
    val sportIntensity: Int? = null,

    // ---------- REPAS ----------
    // Repas : Subcategory / Nombre de kcal / Qualité (1–10) / Commentaire
    val mealCalories: Int? = null,
    val mealQuality: Int? = null,

    // ---------- SOMMEIL ----------
    // Sommeil : Subcategory / Heure de couché / Heure de réveil / Temps de sommeil /
    //           Qualité (1–10) / Commentaire
    val sleepBedTime: String? = null,     // ex : "23:30"
    val sleepWakeTime: String? = null,    // ex : "07:15"
    val sleepDuration: String? = null,    // ex : "7h45"
    val sleepQuality: Int? = null,

    // ---------- ACTION PRODUCTIVE ----------
    // Action productive : Subcategory / Durée / Focus (1–10) / Commentaire
    val productiveDurationMinutes: Int? = null,
    val productiveFocus: Int? = null,

    // ---------- TEMPS D'ÉCRAN ----------
    // Temps d’écran : Subcategory / Durée / Commentaire
    val screenDurationMinutes: Int? = null,

    // ---------- NOMBRE DE PAS ----------
    // Nombre de pas : Quantité / Commentaire
    val stepsCount: Int? = null,

    // ---------- HUMEUR ----------
    // Humeur : Humeur générale (1–10) / Commentaire
    val moodScore: Int? = null,

    // ---------- COMMUN ----------
    val comment: String? = null
)
