package com.example.nicotracker.avatar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.nicotracker.AvatarConstants
import com.example.nicotracker.data.AvatarDao
import com.example.nicotracker.data.AvatarState
import com.example.nicotracker.data.JournalEntry
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.max
import kotlin.math.min

class AvatarViewModel(private val avatarDao: AvatarDao) : ViewModel() {

    // L'état exposé à l'UI. Si null (premier lancement), on en crée un par défaut.
    val avatarState: StateFlow<AvatarState> = avatarDao.getAvatarState()
        .map { it ?: AvatarState() } // Si null, on retourne un state par défaut
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AvatarState())

    init {
        // Initialisation : Si la DB est vide, on crée le profil
        viewModelScope.launch {
            if (avatarDao.count() == 0) {
                avatarDao.insertOrUpdate(AvatarState())
            }
        }
    }

    /**
     * C'est ici que la MAGIE opère.
     * Cette fonction est appelée chaque fois qu'une entrée est ajoutée ou modifiée.
     */
    fun processEntry(entry: JournalEntry) {
        viewModelScope.launch {
            val currentState = avatarDao.getAvatarStateOneShot() ?: AvatarState()

            // On calcule les deltas (changements)
            var hpDelta = 0f
            var spDelta = 0f

            // 1. ANALYSE SELON LE CODEX (AvatarEngine)
            when (entry.categoryName) {
                "Repas" -> {
                    val score = entry.mealQuality ?: 5
                    hpDelta += when {
                        score > 8 -> AvatarConstants.HP_GAIN_NUTRITION_EXCELLENT
                        score in 6..8 -> AvatarConstants.HP_GAIN_NUTRITION_GOOD
                        score in 4..5 -> AvatarConstants.HP_LOSS_NUTRITION_MEDIOCRE
                        else -> AvatarConstants.HP_LOSS_NUTRITION_CRITICAL
                    }
                }
                "Sport" -> {
                    // Le sport coûte de l'énergie (SP) mais soigne (HP) si intense
                    spDelta -= 20f // Coût d'effort standard (à affiner selon durée)

                    val intensity = entry.sportIntensity ?: 5
                    if (intensity >= 6) {
                        hpDelta += 2f // Petit soin "Boost métabolique"
                    }
                }
                "Sommeil" -> {
                    // Calcul basé sur la durée (simplifié ici, à affiner avec ton parsing d'heures)
                    // Supposons que tu aies converti la durée en heures avant ou dans l'objet
                    // Ici on applique juste une logique générique pour l'exemple :
                    val quality = entry.sleepQuality ?: 5
                    spDelta += when {
                        quality >= 8 -> AvatarConstants.SP_GAIN_SLEEP_DEEP
                        quality >= 6 -> AvatarConstants.SP_GAIN_SLEEP_STANDARD
                        else -> AvatarConstants.SP_GAIN_SLEEP_DEBT
                    }
                }
                "Action productive" -> {
                    // Logique FLOW / LUTTE
                    val durationHours = (entry.productiveDurationMinutes ?: 0) / 60f
                    val costPerHour = when(entry.productiveFocus) {
                        3 -> AvatarConstants.SP_COST_FLOW
                        1 -> AvatarConstants.SP_COST_STRUGGLE
                        else -> AvatarConstants.SP_COST_NEUTRAL
                    }
                    spDelta -= (costPerHour * durationHours)
                }
                "Nombre de pas" -> {
                    val steps = entry.stepsCount ?: 0
                    hpDelta += when {
                        steps > 12000 -> AvatarConstants.HP_GAIN_STEPS_ATHLETE
                        steps > 10000 -> AvatarConstants.HP_GAIN_STEPS_OBJECTIVE
                        steps < 4000 -> AvatarConstants.HP_LOSS_STEPS_CRITICAL
                        steps < 7000 -> AvatarConstants.HP_LOSS_STEPS_SEDENTARY
                        else -> 0f
                    }
                }
            }

            // 2. CALCUL DES NOUVELLES VALEURS
            // On clamp (limite) entre 0 et 100
            val newHp = (currentState.currentHp + hpDelta).coerceIn(0f, 100f)

            // LA RÈGLE D'OR : La Stamina Max est limitée par les HP actuels
            val staminaCap = newHp
            // La SP ne peut pas dépasser le Cap, ni 100, ni descendre sous 0
            val rawNewSp = currentState.currentSp + spDelta
            val newSp = rawNewSp.coerceIn(0f, staminaCap)

            // 3. SAUVEGARDE
            val newState = currentState.copy(
                currentHp = newHp,
                currentSp = newSp,
                lastUpdate = Date()
            )
            avatarDao.insertOrUpdate(newState)
        }
    }

    // Fonction de Debug pour réinitialiser si besoin
    fun resetAvatar() {
        viewModelScope.launch {
            avatarDao.insertOrUpdate(AvatarState(currentHp = 100f, currentSp = 100f))
        }
    }
}

// Factory pour créer le ViewModel (nécessaire car il prend un DAO en paramètre)
class AvatarViewModelFactory(private val avatarDao: AvatarDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AvatarViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AvatarViewModel(avatarDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}