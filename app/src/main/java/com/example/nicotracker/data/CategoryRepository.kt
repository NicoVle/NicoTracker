package com.example.nicotracker.data

import kotlinx.coroutines.flow.Flow

class CategoryRepository(private val categoryDao: CategoryDao) {

    // Récupère la liste depuis le DAO
    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()

    // --- VOICI LES DEUX FONCTIONS QUI MANQUAIENT ---

    suspend fun insert(category: Category) {
        categoryDao.insert(category)
    }

    suspend fun delete(category: Category) {
        categoryDao.delete(category)
    }
}