package com.example.nicotracker.data

import kotlinx.coroutines.flow.Flow

class JournalEntryRepository(private val journalEntryDao: JournalEntryDao) {

    // Pour lire toutes les entrées (Flow = données mises à jour en temps réel)
    val allEntries: Flow<List<JournalEntry>> = journalEntryDao.getAllEntries()

    // Pour insérer une nouvelle entrée dans la base de données
    suspend fun insert(entry: JournalEntry) {
        journalEntryDao.insert(entry)
    }
}