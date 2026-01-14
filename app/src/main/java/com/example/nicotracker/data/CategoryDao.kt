package com.example.nicotracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    // Flow pour l’UI (lecture en temps réel)
    @Query("SELECT * FROM category_table")
    fun getAll(): Flow<List<Category>>

    // Version synchrone pour EntryStorage
    @Query("SELECT * FROM category_table")
    suspend fun getAllSync(): List<Category>

    @Query("DELETE FROM category_table")
    suspend fun clearAll()


    @Insert
    suspend fun insert(category: Category)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<Category>)

    @Query("SELECT COUNT(*) FROM category_table")
    suspend fun countCategories(): Int
}
