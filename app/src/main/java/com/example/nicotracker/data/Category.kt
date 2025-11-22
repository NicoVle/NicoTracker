package com.example.nicotracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// C'est ici qu'on dit à la base de données : "Crée une table pour ça"
@Entity(tableName = "category_table")
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val name: String
)