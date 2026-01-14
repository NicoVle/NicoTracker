package com.example.nicotracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AvatarDao {
    // On observe l'état en temps réel pour l'UI
    @Query("SELECT * FROM avatar_state WHERE id = 1")
    fun getAvatarState(): Flow<AvatarState?>

    // Pour récupérer l'état une seule fois (pour les calculs)
    @Query("SELECT * FROM avatar_state WHERE id = 1")
    suspend fun getAvatarStateOneShot(): AvatarState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(state: AvatarState)

    // Une fonction utile pour initialiser si vide
    @Query("SELECT COUNT(*) FROM avatar_state")
    suspend fun count(): Int
}