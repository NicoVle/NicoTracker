package com.example.nicotracker.stats

import com.example.nicotracker.data.JournalEntry
import java.util.*

class StatsEngine {

    fun filterByPeriod(entries: List<JournalEntry>, period: StatsPeriod): List<JournalEntry> {
        if (entries.isEmpty()) return emptyList()

        val now = Calendar.getInstance()

        return when (period) {

            StatsPeriod.TODAY -> {
                entries.filter { isSameDay(it.date, now.time) }
            }

            StatsPeriod.SEVEN_DAYS -> {
                val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
                entries.filter { it.date.after(cal.time) }
            }

            StatsPeriod.THIRTY_DAYS -> {
                val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }
                entries.filter { it.date.after(cal.time) }
            }

            StatsPeriod.CUSTOM -> entries // sera remplacé plus tard
        }
    }

    fun filterByCategory(entries: List<JournalEntry>, category: String): List<JournalEntry> {
        return entries.filter { it.categoryName == category }
    }

    private fun isSameDay(d1: Date, d2: Date): Boolean {
        val c1 = Calendar.getInstance().apply { time = d1 }
        val c2 = Calendar.getInstance().apply { time = d2 }
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }
}
