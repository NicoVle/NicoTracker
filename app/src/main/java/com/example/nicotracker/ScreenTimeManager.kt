package com.example.nicotracker

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.util.Calendar

class ScreenTimeManager(private val context: Context) {

    private val targetPackage = "com.instagram.android"

    fun hasPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun requestPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    // --- NOUVELLE LOGIQUE : EVENTS ---
    // On ne fait plus confiance aux stats agrégées (buckets).
    // On récupère le flux brut des ouvertures/fermetures et on calcule nous-mêmes.
    private fun getInstagramTimeForRange(startTime: Long, endTime: Long): Int {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        // On demande les événements bruts (Ouverture, Fermeture...) sur la période
        val events = usageStatsManager.queryEvents(startTime, endTime)

        var totalTimeMillis = 0L
        var lastStartTime = 0L

        val event = UsageEvents.Event() // Objet réutilisable pour lire les événements

        while (events.hasNextEvent()) {
            events.getNextEvent(event)

            // On ne regarde que Instagram
            if (event.packageName == targetPackage) {

                // Si l'appli passe au premier plan (Ouverture)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastStartTime = event.timeStamp
                }
                // Si l'appli passe en arrière-plan (Fermeture)
                else if (event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                    // Si on avait un début d'ouverture enregistré
                    if (lastStartTime != 0L) {
                        totalTimeMillis += (event.timeStamp - lastStartTime)
                        lastStartTime = 0L // On reset pour la prochaine session
                    }
                }
            }
        }

        // CAS LIMITE : L'application est encore ouverte à la fin de la période (ex: à 23h59m59s)
        // Ou l'utilisateur est dessus en ce moment même.
        if (lastStartTime != 0L) {
            totalTimeMillis += (endTime - lastStartTime)
        }

        return (totalTimeMillis / 1000 / 60).toInt()
    }

    // Calcul pour AUJOURD'HUI (00h00 -> Maintenant)
    fun getInstagramTimeToday(): Int {
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis // Maintenant

        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis

        return getInstagramTimeForRange(startTime, endTime)
    }

    // Calcul pour HIER (Hier 00h00 -> Hier 23h59)
    fun getInstagramTimeYesterday(): Int {
        val calendar = Calendar.getInstance()

        // On recule d'un jour pour tomber sur hier
        calendar.add(Calendar.DAY_OF_YEAR, -1)

        // Fin de journée hier
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endTime = calendar.timeInMillis

        // Début de journée hier
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis

        return getInstagramTimeForRange(startTime, endTime)
    }
}