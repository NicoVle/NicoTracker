package com.example.nicotracker.data

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Gère les entrées et les exports/imports JSON / CSV
 */
class EntryStorage(private val db: AppDatabase) {

    private val entryDao = db.journalEntryDao()
    private val categoryDao = db.categoryDao()
    private val subCategoryDao = db.subCategoryDao()

    // ---------------------------------------------------------
    //  CRUD de base
    // ---------------------------------------------------------

    suspend fun addEntry(entry: JournalEntry) {
        entryDao.insert(entry)
    }

    suspend fun deleteEntry(entry: JournalEntry) {
        entryDao.delete(entry)
    }

    suspend fun getAllEntries(): List<JournalEntry> {
        return entryDao.getAllSync()
    }

    suspend fun updateEntry(entry: JournalEntry) = entryDao.update(entry)

    // ---------------------------------------------------------
    //  EXPORT JSON COMPLET (catégories + sous-cat + entrées)
    // ---------------------------------------------------------

    suspend fun exportAsJson(): String = withContext(Dispatchers.IO) {
        val backup = FullBackup(
            categories = categoryDao.getAllSync(),
            subCategories = subCategoryDao.getAllSync(),
            entries = entryDao.getAllSync()
        )

        // MODIFICATION ICI : On utilise GsonBuilder pour activer le "Pretty Print"
        val gson = com.google.gson.GsonBuilder()
            .setPrettyPrinting() // <--- C'est cette ligne qui rend le JSON lisible
            .create()

        gson.toJson(backup)
    }

    // ---------------------------------------------------------
    //  IMPORT JSON (restauration complète)
    // ---------------------------------------------------------

    private fun cleanEntryPollution(entry: JournalEntry): JournalEntry {
        return entry.copy(
            // --- Famille SPORT (Uniquement si "Sport") ---
            sportDurationMinutes = if (entry.categoryName == "Sport") entry.sportDurationMinutes else null,
            sportIntensity = if (entry.categoryName == "Sport") entry.sportIntensity else null,

            // --- Famille REPAS (Uniquement si "Repas") ---
            mealCalories = if (entry.categoryName == "Repas") entry.mealCalories else null,
            mealProtein = if (entry.categoryName == "Repas") entry.mealProtein else null,
            mealCarbs = if (entry.categoryName == "Repas") entry.mealCarbs else null,
            mealLipids = if (entry.categoryName == "Repas") entry.mealLipids else null,
            mealQuality = if (entry.categoryName == "Repas") entry.mealQuality else null,
            mealSugar = if (entry.categoryName == "Repas") entry.mealSugar else null,
            mealSaturatedFat = if (entry.categoryName == "Repas") entry.mealSaturatedFat else null,
            mealSodium = if (entry.categoryName == "Repas") entry.mealSodium else null,
            mealFibers = if (entry.categoryName == "Repas") entry.mealFibers else null,

            // --- Famille SOMMEIL (Uniquement si "Sommeil") ---
            sleepBedTime = if (entry.categoryName == "Sommeil") entry.sleepBedTime else null,
            sleepWakeTime = if (entry.categoryName == "Sommeil") entry.sleepWakeTime else null,
            sleepDuration = if (entry.categoryName == "Sommeil") entry.sleepDuration else null,
            sleepQuality = if (entry.categoryName == "Sommeil") entry.sleepQuality else null,
            sleepWokeUpWithAlarm = if (entry.categoryName == "Sommeil") entry.sleepWokeUpWithAlarm else null,

            // --- Famille PRODUCTIVITÉ (Uniquement si "Action productive") ---
            productiveDurationMinutes = if (entry.categoryName == "Action productive") entry.productiveDurationMinutes else null,
            productiveFocus = if (entry.categoryName == "Action productive") entry.productiveFocus else null,
            complexity = if (entry.categoryName == "Action productive") entry.complexity else null,
            volume = if (entry.categoryName == "Action productive") entry.volume else null,

            // --- Famille DÉFIS (Uniquement si "Défis") ---
            challengeTitle = if (entry.categoryName == "Défis") entry.challengeTitle else null,
            challengeDurationMinutes = if (entry.categoryName == "Défis") entry.challengeDurationMinutes else null,
            challengeQuantity = if (entry.categoryName == "Défis") entry.challengeQuantity else null,
            challengeSuccess = if (entry.categoryName == "Défis") entry.challengeSuccess else null,
            challengeDifficulty = if (entry.categoryName == "Défis") entry.challengeDifficulty else null,
            challengeState = if (entry.categoryName == "Défis") entry.challengeState else null,

            // --- Champs isolés ---
            screenDurationMinutes = if (entry.categoryName == "Temps d'écran") entry.screenDurationMinutes else null,
            stepsCount = if (entry.categoryName == "Nombre de pas") entry.stepsCount else null,
            moodScore = if (entry.categoryName == "Humeur") entry.moodScore else null,
            depensePrice = if (entry.categoryName == "Dépense") entry.depensePrice else null,
            incomeAmount = if (entry.categoryName == "Revenus") entry.incomeAmount else null

            // On ne touche pas à : id, categoryName, subCategoryId, date, tags, comment.
        )
    }
    suspend fun importFromJson(json: String) {
        withContext(Dispatchers.IO) {
            val gson = Gson()
            val backup = gson.fromJson(json, FullBackup::class.java)

            // On nettoie chaque entrée AVANT de l'insérer en base
            val cleanedEntries = backup.entries.map { cleanEntryPollution(it) }

            entryDao.clearAll()
            subCategoryDao.clearAll()

            subCategoryDao.insertAll(backup.subCategories)
            entryDao.insertAll(cleanedEntries) // On insère les versions propres
        }
    }

    suspend fun update(entry: JournalEntry) {
        db.journalEntryDao().update(entry)
    }


    // ---------------------------------------------------------
    //  EXPORT CSV COMPLET (Tous les champs de JournalEntry)
    // ---------------------------------------------------------

    suspend fun exportAsCsv(): String = withContext(Dispatchers.IO) {
        val entries = entryDao.getAllSync()
        val subCategories = subCategoryDao.getAllSync()

        // Création d'un dictionnaire ID -> Nom pour afficher "Running" au lieu de "12"
        val subCatMap = subCategories.associate { it.id to it.name }

        val builder = StringBuilder()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        // 1. EN-TÊTE COMPLET
        // Chaque champ de JournalEntry a sa propre colonne
        builder.append(
            "id,date,category,subcategory," +

                    // Sport
                    "sport_duration_min,sport_intensity," +

                    // Nutrition
                    "meal_kcal,meal_prot,meal_carbs,meal_fat,meal_quality," +
                    "meal_sugar,meal_sat_fat,meal_sodium,meal_fibers," +

                    // Sommeil
                    "sleep_bed_time,sleep_wake_time,sleep_duration,sleep_quality," +
                    "sleep_bed_time,sleep_wake_time,sleep_duration,sleep_quality,sleep_alarm," +

                    // Productivité & Écran & Pas & Humeur
                    "prod_duration_min,prod_focus,screen_duration_min,steps_count,mood_score," +

                    // Finance
                    "expense_amount,income_amount," +

                    // Défis
                    "challenge_title,challenge_duration_min,challenge_qty,challenge_success,challenge_diff,challenge_state," +

                    // Commun
                    "comment\n"
        )

        // 2. REMPLISSAGE DES DONNÉES
        for (e in entries) {
            val dateString = sdf.format(e.date)

            // Récupération du nom de la sous-catégorie (ou vide si null)
            val subCatName = e.subCategoryId?.let { subCatMap[it] } ?: ""

            // Fonction utilitaire pour éviter d'écrire "null" dans le CSV (on laisse vide)
            fun v(value: Any?): String = value?.toString() ?: ""

            // Fonction pour sécuriser les textes (échapper les guillemets et les virgules)
            fun t(text: String?): String {
                if (text == null) return ""
                // Si le texte contient une virgule ou un guillemet, on l'encadre de guillemets
                val escaped = text.replace("\"", "\"\"")
                return "\"$escaped\""
            }

            builder.append(
                "${e.id}," +
                        "$dateString," +
                        "${t(e.categoryName)}," +
                        "${t(subCatName)}," +

                        // Sport
                        "${v(e.sportDurationMinutes)}," +
                        "${v(e.sportIntensity)}," +

                        // Nutrition (Visible + Ghost)
                        "${v(e.mealCalories)}," +
                        "${v(e.mealProtein)}," +
                        "${v(e.mealCarbs)}," +
                        "${v(e.mealLipids)}," +
                        "${v(e.mealQuality)}," +
                        "${v(e.mealSugar)}," +
                        "${v(e.mealSaturatedFat)}," +
                        "${v(e.mealSodium)}," +
                        "${v(e.mealFibers)}," +

                        // Sommeil
                        "${v(e.sleepBedTime)}," +
                        "${v(e.sleepWakeTime)}," +
                        "${v(e.sleepDuration)}," +
                        "${v(e.sleepQuality)}," +

                        // Productivité & Écran
                        "${v(e.productiveDurationMinutes)}," +
                        "${v(e.productiveFocus)}," +
                        "${v(e.screenDurationMinutes)}," +

                        // Pas & Humeur
                        "${v(e.stepsCount)}," +
                        "${v(e.moodScore)}," +

                        // Finance
                        "${v(e.depensePrice)}," +
                        "${v(e.incomeAmount)}," +

                        // Défis
                        "${t(e.challengeTitle)}," +
                        "${v(e.challengeDurationMinutes)}," +
                        "${v(e.challengeQuantity)}," +
                        "${v(e.challengeSuccess)}," +
                        "${v(e.challengeDifficulty)}," +
                        "${v(e.challengeState)}," +

                        // Commentaire (à la fin)
                        "${t(e.comment)}\n"
            )
        }
        return@withContext builder.toString()
    }
}

// ---------------------------------------------------------
//  CLASSE POUR LE BACKUP COMPLET
// ---------------------------------------------------------

data class FullBackup(
    val categories: List<Category>,
    val subCategories: List<SubCategory>,
    val entries: List<JournalEntry>
)