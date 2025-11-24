package com.example.nicotracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalEntryDao {

    // Récupérer toutes les entrées, triées par date
    @Query("SELECT * FROM journal_entry_table ORDER BY date DESC")
    fun getAllEntries(): Flow<List<JournalEntry>>

    // Insérer une nouvelle entrée
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: JournalEntry)

    @Delete
    suspend fun delete(entry: JournalEntry)

}