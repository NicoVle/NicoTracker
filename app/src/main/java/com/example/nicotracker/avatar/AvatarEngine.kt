package com.example.nicotracker

import androidx.compose.ui.graphics.Color

object AvatarConstants {
    // --- POINTS DE VIE (HP) ---
    const val HP_MAX = 100f

    // Nutrition (Moyenne journalière)
    const val HP_GAIN_NUTRITION_EXCELLENT = 4f   // Score > 8
    const val HP_GAIN_NUTRITION_GOOD = 1f        // Score 6-8
    const val HP_LOSS_NUTRITION_MEDIOCRE = -5f   // Score 4-6
    const val HP_LOSS_NUTRITION_CRITICAL = -12f  // Score < 4

    // Mouvement (Pas)
    const val HP_GAIN_STEPS_ATHLETE = 3f         // > 12k
    const val HP_GAIN_STEPS_OBJECTIVE = 1f       // 10k-12k
    const val HP_LOSS_STEPS_SEDENTARY = -5f      // 4k-7k
    const val HP_LOSS_STEPS_CRITICAL = -15f      // < 4k

    const val HP_BONUS_CONSISTENCY = 10f         // 3 jours consécutifs

    // --- STAMINA (SP) ---
    // Recharge (Sommeil)
    const val SP_GAIN_SLEEP_DEEP = 80f           // > 8h30
    const val SP_GAIN_SLEEP_STANDARD = 60f       // 7h-8h30
    const val SP_GAIN_SLEEP_DEBT = 25f           // 5h-7h
    const val SP_GAIN_SLEEP_SURVIVAL = 5f        // < 5h

    // Drain (Écran)
    const val SP_LOSS_SCREEN_MODERATE = -10f     // 1.5h-3h
    const val SP_LOSS_SCREEN_SEVERE = -30f       // 3h-5h
    const val SP_LOSS_SCREEN_CRITICAL = -50f     // > 5h

    // Consommation (Productivité / heure)
    const val SP_COST_FLOW = 8f
    const val SP_COST_NEUTRAL = 15f
    const val SP_COST_STRUGGLE = 25f
}