package com.example.nicotracker.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class CategoryViewModel(private val repository: CategoryRepository) : ViewModel() {

    // --- C'EST CETTE LIGNE QUI MANQUAIT ---
    // Elle permet de dire : "Hey, donne-moi la liste qui vient de la base de données"
    val allCategories: Flow<List<Category>> = repository.allCategories

    // Fonction pour ajouter une catégorie
    fun insert(category: Category) = viewModelScope.launch {
        repository.insert(category)
    }

    // Fonction pour supprimer une catégorie
    fun delete(category: Category) = viewModelScope.launch {
        repository.delete(category)
    }
}

// Cette partie "Factory" sert juste à créer le ViewModel proprement (ne pas toucher)
