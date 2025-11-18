package com.example.nicotracker.data

import kotlinx.coroutines.flow.Flow

class CategoryRepository(private val dao: CategoryDao) {

    // Flux de toutes les catégories (automatique et mis à jour en temps réel)
    val allCategories: Flow<List<Category>> = dao.getAllCategories()

    // Ajouter une catégorie
    suspend fun addCategory(name: String) {
        dao.insertCategory(Category(name = name))
    }

    // Supprimer une catégorie
    suspend fun deleteCategory(category: Category) {
        dao.deleteCategory(category)
    }

    // Renommer une catégorie
    suspend fun renameCategory(categoryId: Int, newName: String) {
        dao.renameCategory(categoryId, newName)
    }
}
