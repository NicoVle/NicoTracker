package com.example.nicotracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

// --- IMPORTS CRUCIAUX ---
import com.example.nicotracker.data.Category
import com.example.nicotracker.data.JournalEntry
import com.example.nicotracker.data.SubCategory
import com.example.nicotracker.data.AvatarState // <--- NOUVEAU

import com.example.nicotracker.data.CategoryDao
import com.example.nicotracker.data.JournalEntryDao
import com.example.nicotracker.data.SubCategoryDao
import com.example.nicotracker.data.AvatarDao // <--- NOUVEAU

@TypeConverters(Converters::class)
@Database(
    // On ajoute AvatarState à la liste des tables
    entities = [
        Category::class,
        JournalEntry::class,
        SubCategory::class,
        AvatarState::class // <--- AJOUTÉ ICI
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun journalEntryDao(): JournalEntryDao
    abstract fun subCategoryDao(): SubCategoryDao

    // On déclare le nouveau DAO
    abstract fun avatarDao(): AvatarDao // <--- AJOUTÉ ICI

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nicotracker_database"
                )
                    .fallbackToDestructiveMigration() // Va recréer la DB proprement avec la nouvelle table
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}