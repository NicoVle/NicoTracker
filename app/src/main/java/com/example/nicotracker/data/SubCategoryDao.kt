package com.example.nicotracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SubCategoryDao {

    @Query("SELECT * FROM subcategories WHERE parentCategoryId = :categoryId")
    fun getSubCategoriesForCategory(categoryId: Int): Flow<List<SubCategory>>

    @Query("SELECT name FROM subcategories WHERE id = :id LIMIT 1")
    suspend fun getNameById(id: Int): String?

    @Query("SELECT * FROM subcategories")
    suspend fun getAllSync(): List<SubCategory>

    @Query("DELETE FROM subcategories")
    suspend fun clearAll()


    @Insert
    suspend fun insert(sub: SubCategory)

    @Insert
    suspend fun insertAll(subCategories: List<SubCategory>)

    @Delete
    suspend fun delete(sub: SubCategory)

    @Update
    suspend fun update(subCategory: SubCategory)

}
