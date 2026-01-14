package com.example.nicotracker.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import com.example.nicotracker.stats.NutritionScoringEngine

class JournalEntryViewModel(
    private val storage: EntryStorage
) : ViewModel() {

    // Flow interne contenant les entrées
    private val _allEntries = MutableStateFlow<List<JournalEntry>>(emptyList())
    val allEntries = _allEntries.asStateFlow()

    init {
        // Charge les entrées au démarrage
        viewModelScope.launch {
            _allEntries.value = storage.getAllEntries()
        }
    }

    // 🔁 Appelé après import JSON pour tout recharger
    fun reload() = viewModelScope.launch {
        _allEntries.value = storage.getAllEntries()
    }

    fun insert(entry: JournalEntry) = viewModelScope.launch {
        storage.addEntry(entry.sanitize()) // Le nettoyage se fait ici automatiquement
        _allEntries.value = storage.getAllEntries()
    }

    fun delete(entry: JournalEntry) = viewModelScope.launch {
        storage.deleteEntry(entry)
        _allEntries.value = storage.getAllEntries()
    }

    fun update(entry: JournalEntry) = viewModelScope.launch {
        storage.updateEntry(entry.sanitize()) // Et ici aussi
        _allEntries.value = storage.getAllEntries()
    }

    // --- CORRECTION ICI ---
    fun syncStepsForToday(steps: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. On cherche la date d'aujourd'hui (00:00:00)
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // 2. On récupère la liste (Correction : utilisation de 'storage' et 'getAllEntries')
            val entries = storage.getAllEntries()

            val existingEntry = entries.find { entry ->
                val c = Calendar.getInstance().apply { time = entry.date }
                val entryDate = c.apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                // Condition : C'est aujourd'hui ET c'est la catégorie "Nombre de pas"
                entryDate.timeInMillis == today.timeInMillis && entry.categoryName == "Nombre de pas"
            }

            if (existingEntry != null) {
                // CAS 1 : Ça existe déjà -> On met à jour
                if (existingEntry.stepsCount != steps) { // On update seulement si ça a changé
                    update(existingEntry.copy(stepsCount = steps))
                    println("SYNC PAS : Mise à jour de l'entrée existante ($steps pas)")
                }
            } else {
                // CAS 2 : Ça n'existe pas -> On crée
                if (steps > 0) { // On ne crée pas d'entrée vide pour 0 pas
                    insert(
                        JournalEntry(
                            categoryName = "Nombre de pas",
                            date = Date(), // Date de maintenant
                            stepsCount = steps
                        )
                    )
                    println("SYNC PAS : Création nouvelle entrée ($steps pas)")
                }
            }
        }
    }

    // --- LA NOUVELLE FONCTION (À placer ICI, AVANT la dernière accolade de la classe) ---
    fun syncStepsForDate(steps: Int, date: Date) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. On prépare la date cible (Minuit pile du jour demandé)
            val targetDate = Calendar.getInstance().apply {
                time = date
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // 2. On récupère tout pour chercher
            val entries = storage.getAllEntries()

            val existingEntry = entries.find { entry ->
                val c = Calendar.getInstance().apply { time = entry.date }
                // On remet la date de l'entrée à minuit pour comparer
                c.set(Calendar.HOUR_OF_DAY, 0)
                c.set(Calendar.MINUTE, 0)
                c.set(Calendar.SECOND, 0)
                c.set(Calendar.MILLISECOND, 0)

                // On compare les temps en millisecondes pour être sûr
                c.timeInMillis == targetDate.timeInMillis && entry.categoryName == "Nombre de pas"
            }

            if (existingEntry != null) {
                // CAS 1 : Mise à jour si le nombre de pas a changé
                if (existingEntry.stepsCount != steps) {
                    update(existingEntry.copy(stepsCount = steps))
                    println("SYNC PAS (${targetDate.time}): Update à $steps")
                }
            } else {
                // CAS 2 : Création si inexistant (et si > 0)
                if (steps > 0) {
                    insert(
                        JournalEntry(
                            categoryName = "Nombre de pas",
                            date = targetDate.time, // IMPORTANT : On force la date passée en paramètre
                            stepsCount = steps
                        )
                    )
                    println("SYNC PAS (${targetDate.time}): Création à $steps")
                }
            }
        }
    }


    fun syncScreenTime(date: Date, subCategoryId: Int, minutes: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val targetDate = Calendar.getInstance().apply {
                time = date
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }

            val entries = storage.getAllEntries()

            // On cherche l'entrée existante (Temps d'écran -> Réseaux Sociaux)
            val existingEntry = entries.find { entry ->
                val c = Calendar.getInstance().apply { time = entry.date }
                c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
                c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)

                c.timeInMillis == targetDate.timeInMillis &&
                        entry.subCategoryId == subCategoryId
            }

            if (existingEntry != null) {
                // MISE À JOUR : On ne touche que si le temps a changé
                if (existingEntry.screenDurationMinutes != minutes) {
                    update(existingEntry.copy(
                        screenDurationMinutes = minutes
                    ))
                    println("SYNC SCREEN: Update ID $subCategoryId -> $minutes min")
                }
            } else {
                // CRÉATION : On ne crée que si > 0 minute
                if (minutes > 0) {
                    insert(
                        JournalEntry(
                            categoryName = "Temps d'écran",
                            subCategoryId = subCategoryId,
                            date = targetDate.time,
                            screenDurationMinutes = minutes
                        )
                    )
                    println("SYNC SCREEN: Création ID $subCategoryId -> $minutes min")
                }
            }
        }
    }
    // Récupérer le défi actif (s'il existe)
    fun getActiveChallenge(): JournalEntry? {
        // On cherche une entrée qui est "Défis" et dont l'état est 1 (Actif)
        return _allEntries.value.find {
            it.categoryName == "Défis" && it.challengeState == 1
        }
    }

    // Activer un défi (Le sort de la banque et le met en "En cours")
    fun activateChallenge(entry: JournalEntry) = viewModelScope.launch {
        // 1. D'abord, on vérifie s'il y a déjà un défi actif. Si oui, on le met en pause ou en échec ?
        // Pour faire simple ici : on remet l'ancien actif en banque (état 0)
        val currentActive = getActiveChallenge()
        if (currentActive != null) {
            update(currentActive.copy(challengeState = 0))
        }

        // 2. On active le nouveau (date mise à jour à aujourd'hui pour qu'il apparaisse en haut si on trie par date)
        update(entry.copy(challengeState = 1, date = Date()))
    }

    // Terminer le défi actif (Victoire ou Défaite)
    fun completeActiveChallenge(entry: JournalEntry, successScore: Int) = viewModelScope.launch {
        update(entry.copy(
            challengeState = 2, // Terminé
            challengeSuccess = successScore,
            date = Date() // On valide à la date de fin
        ))
    }

    // Ajouter un défi directement dans la Banque
    fun addToBank(entry: JournalEntry) = viewModelScope.launch {
        // On force l'état à 0
        insert(entry.copy(challengeState = 0))
    }
}



class JournalEntryViewModelFactory(
    private val storage: EntryStorage
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(JournalEntryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return JournalEntryViewModel(storage) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}