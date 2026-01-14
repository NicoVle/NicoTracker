package com.example.nicotracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalEntryDao {

    // Flow pour l’UI
    @Query("SELECT * FROM journal_entry_table ORDER BY date DESC")
    fun getAll(): Flow<List<JournalEntry>>

    // Version synchrone pour EntryStorage
    @Query("SELECT * FROM journal_entry_table ORDER BY date DESC")
    suspend fun getAllSync(): List<JournalEntry>

    @Query("SELECT COUNT(*) FROM journal_entry_table")
    suspend fun countEntries(): Int


    @Query("DELETE FROM journal_entry_table")
    suspend fun clearAll()


    @Insert
    suspend fun insert(entry: JournalEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<JournalEntry>)

    @Delete
    suspend fun delete(entry: JournalEntry)

    @Update
    suspend fun update(entry: JournalEntry)

}

