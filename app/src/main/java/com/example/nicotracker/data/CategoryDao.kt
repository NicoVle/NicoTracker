package com.example.nicotracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    // Récupère toutes les catégories en flux continu (pratique pour Jetpack Compose)
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<Category>>

    // Insère une nouvelle catégorie
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category)

    // Supprime une catégorie
    @Delete
    suspend fun deleteCategory(category: Category)

    // Renomme une catégorie
    @Query("UPDATE categories SET name = :newName WHERE id = :categoryId")
    suspend fun renameCategory(categoryId: Int, newName: String)
}
