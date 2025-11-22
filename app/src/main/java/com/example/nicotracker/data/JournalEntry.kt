package com.example.nicotracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

// IMPORTANT : On crée une table pour les entrées du journal
@Entity(tableName = "journal_entry_table")
data class JournalEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    // On relie l'entrée à la catégorie choisie
    val categoryName: String,

    val date: Date = Date(),

    // Champs génériques pour l'instant
    val description: String,
    val value: Float? = null // Exemple : durée, quantité, etc.
)