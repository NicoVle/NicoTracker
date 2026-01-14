package com.example.nicotracker.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow

class SubCategoryViewModel(
    private val dao: SubCategoryDao
) : ViewModel() {

    private val _allSubCategories = MutableStateFlow<List<SubCategory>>(emptyList())
    val allSubCategories = _allSubCategories.asStateFlow()

    init {
        viewModelScope.launch {
            _allSubCategories.value = dao.getAllSync()
        }
    }

    fun getSubCategoriesForCategory(categoryId: Int): Flow<List<SubCategory>> =
        dao.getSubCategoriesForCategory(categoryId)

    suspend fun getSubCategoryName(id: Int, callback: (String?) -> Unit) {
        callback(dao.getNameById(id))
    }

    fun insert(subCategory: SubCategory) = viewModelScope.launch {
        dao.insert(subCategory)
        _allSubCategories.value = dao.getAllSync()
    }

    fun delete(subCategory: SubCategory) = viewModelScope.launch {
        dao.delete(subCategory)
        _allSubCategories.value = dao.getAllSync()
    }

    fun reload() = viewModelScope.launch {
        _allSubCategories.value = dao.getAllSync()
    }

    fun update(subCategory: SubCategory) {
        viewModelScope.launch(Dispatchers.IO) {
            // CORRECTION : on utilise 'dao' et non 'subCategoryDao'
            dao.update(subCategory)

            // On rafraîchit la liste locale après la mise à jour pour que l'interface change tout de suite
            _allSubCategories.value = dao.getAllSync()
        }
    }
}

class SubCategoryViewModelFactory(
    private val dao: SubCategoryDao
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SubCategoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return SubCategoryViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
