package com.example.nicotracker.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class JournalEntryViewModel(private val repository: JournalEntryRepository) : ViewModel() {

    // Données exposées à l'interface utilisateur
    val allEntries: Flow<List<JournalEntry>> = repository.allEntries

    // Fonction d'insertion appelée par l'écran 'TodayScreen'
    fun insert(entry: JournalEntry) = viewModelScope.launch {
        repository.insert(entry)
    }
}

// Classe Factory pour créer le ViewModel correctement
class JournalEntryViewModelFactory(private val repository: JournalEntryRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(JournalEntryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return JournalEntryViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}