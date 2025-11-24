package com.example.nicotracker.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class SubCategoryViewModel(private val repository: SubCategoryRepository) : ViewModel() {

    fun getSubCategoriesForCategory(categoryId: Int): Flow<List<SubCategory>> {
        return repository.getSubCategoriesForCategory(categoryId)
    }

    fun insert(subCategory: SubCategory) = viewModelScope.launch {
        repository.insert(subCategory)
    }

    fun delete(subCategory: SubCategory) = viewModelScope.launch {
        repository.delete(subCategory)
    }

    fun getSubCategoryName(id: Int, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            onResult(repository.getSubCategoryName(id))
        }
    }

}

class SubCategoryViewModelFactory(private val repository: SubCategoryRepository)
    : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SubCategoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SubCategoryViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
