package com.example.nicotracker

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.ZoneId

data class RawNutritionItem(
    val id: String,
    val name: String,
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val sugar: Double = 0.0,
    val sodium: Double = 0.0,
    val saturatedFat: Double = 0.0,
    val fibers: Double = 0.0
)


class HealthConnectManager(private val context: Context) {

    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    fun isHealthConnectAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    suspend fun hasPermissions(): Boolean {
        val permissions = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getWritePermission(StepsRecord::class),
            HealthPermission.getReadPermission(NutritionRecord::class) // Ajouté par sécurité
        )
        return healthConnectClient.permissionController.getGrantedPermissions()
            .containsAll(permissions)
    }

    suspend fun getTodaySteps(): Int {
        return try {
            val zoneId = ZoneId.systemDefault()
            val now = java.time.LocalDate.now(zoneId)
            val startTime = now.atStartOfDay(zoneId).toInstant()
            val endTime = now.plusDays(1).atStartOfDay(zoneId).toInstant()

            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response[StepsRecord.COUNT_TOTAL]?.toInt() ?: 0
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    suspend fun getStepsForDate(date: java.util.Date): Int {
        return try {
            val zoneId = ZoneId.systemDefault()
            val localDate = date.toInstant().atZone(zoneId).toLocalDate()
            val startTime = localDate.atStartOfDay(zoneId).toInstant()
            val endTime = localDate.plusDays(1).atStartOfDay(zoneId).toInstant()

            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response[StepsRecord.COUNT_TOTAL]?.toInt() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    // 2. La nouvelle fonction qui renvoie une LISTE
    suspend fun getRawDailyNutrition(date: java.util.Date): List<RawNutritionItem> {
        return try {
            val zoneId = ZoneId.systemDefault()
            val localDate = date.toInstant().atZone(zoneId).toLocalDate()
            val startTime = localDate.atStartOfDay(zoneId).toInstant()
            val endTime = localDate.plusDays(1).atStartOfDay(zoneId).toInstant()

            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = NutritionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )

            response.records.map { record ->
                RawNutritionItem(
                    id = record.metadata.id,
                    name = record.name ?: "Aliment inconnu",
                    calories = record.energy?.inKilocalories?.toInt() ?: 0,
                    protein = record.protein?.inGrams?.toInt() ?: 0,
                    carbs = record.totalCarbohydrate?.inGrams?.toInt() ?: 0,
                    fat = record.totalFat?.inGrams?.toInt() ?: 0,
                    sugar = record.sugar?.inGrams ?: 0.0,
                    sodium = record.sodium?.inGrams ?: 0.0,
                    saturatedFat = record.saturatedFat?.inGrams ?: 0.0,
                    fibers = record.dietaryFiber?.inGrams ?: 0.0
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList() // <--- CORRECTION ICI (Renvoie une liste vide, pas 0)
        }
    }
}