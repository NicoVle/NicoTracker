package com.example.nicotracker.stats

import kotlin.math.ceil
import kotlin.math.max

object NutritionScoringEngine {

    /**
     * Calcule la note /10 selon le "Protein-First Flex Score".
     */
    fun computeScore(calories: Int, protein: Int, fibers: Int = 0): Int {
        if (calories == 0) return 5 // Note neutre si vide

        // 1. SCORE CALORIES (Sécurité)
        // Base 5 pts. Si > 900kcal, -1 pt par 100kcal excédentaires.
        var calorieScore = 5
        if (calories > 900) {
            val excess = (calories - 900).toDouble()
            val penalty = ceil(excess / 100.0).toInt()
            calorieScore = max(0, 5 - penalty)
        }

        // 2. SCORE PROTÉINES (Densité)
        // Ratio : (Grammes Prot * 4) / Kcal Totales
        val proteinKcal = protein * 4.0
        val ratio = if (calories > 0) proteinKcal / calories else 0.0

        val proteinScore = when {
            ratio >= 0.30 -> 5  // Excellence (>30%)
            ratio >= 0.25 -> 4  // Très bon (25-30%)
            ratio >= 0.20 -> 3  // Bon (20-25%)
            ratio >= 0.15 -> 2  // Passable (15-20%)
            else -> 1           // Insuffisant (<15%)
        }

        var totalScore = calorieScore + proteinScore

        // 3. PÉNALITÉ "Calories Vides" (< 10% prot)
        if (ratio < 0.10) {
            totalScore -= 3
        }

        return totalScore.coerceIn(0, 10)
    }
}