package com.example.nicotracker.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CategoryViewModel(
    private val dao: CategoryDao
) : ViewModel() {

    private val _allCategories = MutableStateFlow<List<Category>>(emptyList())
    val allCategories = _allCategories.asStateFlow()

    init {
        viewModelScope.launch {
            _allCategories.value = dao.getAllSync()
        }
    }

    fun insert(category: Category) = viewModelScope.launch {
        dao.insert(category)
        _allCategories.value = dao.getAllSync()
    }

    fun insertAllIfEmpty(defaultCategories: List<Category>) = viewModelScope.launch {
        val count = dao.countCategories()
        if (count == 0) {
            dao.insertAll(defaultCategories)
            _allCategories.value = dao.getAllSync()
        }
    }

    fun reload() = viewModelScope.launch {
        _allCategories.value = dao.getAllSync()
    }
}

class CategoryViewModelFactory(
    private val dao: CategoryDao
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CategoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return CategoryViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
