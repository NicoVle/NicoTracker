package com.example.nicotracker.data

class SubCategoryRepository(private val dao: SubCategoryDao) {

    fun getSubCategoriesForCategory(categoryId: Int) =
        dao.getAllSubCategoriesByCategoryId(categoryId.toLong())

    suspend fun insert(subCategory: SubCategory) =
        dao.insert(subCategory)

    suspend fun delete(subCategory: SubCategory) =
        dao.delete(subCategory)

    suspend fun getSubCategoryName(id: Int): String? {
        return dao.getNameById(id)   // ✔️ FIX ICI
    }
}
