package com.example.nicotracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "avatar_state")
data class AvatarState(
    @PrimaryKey(autoGenerate = false)
    val id: Int = 1, // On aura une seule ligne (ID 1), c'est ton profil unique

    val currentHp: Float = 100f,
    val maxHp: Float = 100f, // Pour l'instant fixe, mais prêt à évoluer

    val currentSp: Float = 100f,
    val maxSp: Float = 100f, // Sera souvent égal à currentHp selon notre "Règle d'Or"

    val lastUpdate: Date = Date(), // Pour savoir si on doit appliquer des malus de temps

    // Bonus de consistance (Série de jours parfaits)
    val currentStreak: Int = 0
)