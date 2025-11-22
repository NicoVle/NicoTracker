package com.example.nicotracker.data

import androidx.room.TypeConverter
import java.util.Date

class Converters {

    // Convertit un Long (nombre stocké dans la BDD) en objet Date pour Kotlin
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    // Convertit l'objet Date de Kotlin en Long (nombre) pour la BDD
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}