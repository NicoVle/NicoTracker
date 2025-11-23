package com.example.nicotracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

// --- IMPORTS CRUCIAUX ---
// Assurez-vous que ces lignes ne sont pas grisées ou soulignées en rouge
import com.example.nicotracker.data.Category
import com.example.nicotracker.data.JournalEntry
import com.example.nicotracker.data.SubCategory  // <--- Celle-ci manquait peut-être à Room
import com.example.nicotracker.data.CategoryDao
import com.example.nicotracker.data.JournalEntryDao
import com.example.nicotracker.data.SubCategoryDao

@TypeConverters(Converters::class)
@Database(
    // On déclare les 3 tables ici. C'est la liste officielle.
    entities = [Category::class, JournalEntry::class, SubCategory::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun journalEntryDao(): JournalEntryDao

    // On déclare le DAO ici pour que Room sache comment l'utiliser
    abstract fun subCategoryDao(): SubCategoryDao

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
                    .fallbackToDestructiveMigration() // On efface et on recrée si la version change
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}