package com.example.nicotracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// Assurez-vous d'importer la nouvelle entité SubCategory
// import com.votrepackage.data.SubCategory

@Dao
interface SubCategoryDao {

    /**
     * Insère une nouvelle sous-catégorie.
     * En cas de conflit (même ID), l'ancienne entrée est remplacée.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subCategory: SubCategory)

    /**
     * Supprime une sous-catégorie.
     */
    @Delete
    suspend fun delete(subCategory: SubCategory)

    /**
     * Récupère toutes les sous-catégories associées à un ID de catégorie spécifique.
     * Le résultat est un Flow pour observer les changements en temps réel.
     */
    @Query("SELECT * FROM subcategories WHERE parentcategoryId = :categoryId ORDER BY name ASC")
    fun getAllSubCategoriesByCategoryId(categoryId: Long): Flow<List<SubCategory>>
}