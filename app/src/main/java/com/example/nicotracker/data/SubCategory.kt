package com.example.nicotracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index // <--- C'EST CET IMPORT QUI MANQUAIT SUREMENT !
import androidx.room.PrimaryKey

@Entity(
    tableName = "subcategories",
    // L'index est nécessaire pour la performance et éviter l'avertissement de Room
    indices = [Index(value = ["parentCategoryId"])],
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["parentCategoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SubCategory(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val parentCategoryId: Int
)