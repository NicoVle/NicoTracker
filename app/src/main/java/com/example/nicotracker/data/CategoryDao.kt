package com.example.nicotracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    // Récupérer toutes les catégories
    @Query("SELECT * FROM category_table ORDER BY name ASC")
    fun getAllCategories(): Flow<List<Category>>

    // Ajouter une catégorie
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: Category)

    // Supprimer une catégorie
    @Delete
    suspend fun delete(category: Category)
}