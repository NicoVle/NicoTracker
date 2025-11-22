package com.example.nicotracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

// IMPORTANT : Vérifiez que [Category::class] est bien dans la liste des entities
@TypeConverters(Converters::class)
@Database(entities = [Category::class, JournalEntry::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    // IMPORTANT : Cette ligne permet de rendre le DAO accessible
    abstract fun categoryDao(): CategoryDao

    abstract fun journalEntryDao(): JournalEntryDao

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
                    // Cette option permet d'éviter des crashs si on modifie la BDD plus tard
                    // (destructif, mais pratique pour le développement)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}