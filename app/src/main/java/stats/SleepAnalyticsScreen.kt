package com.example.nicotracker.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.example.nicotracker.CardBackgroundEmpty
import com.example.nicotracker.DarkBackground
import com.example.nicotracker.TextGray
import com.example.nicotracker.data.JournalEntry
import com.example.nicotracker.data.JournalEntryViewModel
import com.example.nicotracker.data.SubCategoryViewModel
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

// COULEUR THÈME SOMMEIL
val NeonBlue = Color(0xFF448AFF)
val GhostBlue = Color(0xFF536DFE)
val NeonRed = Color(0xFFFF1744)   // Pour les mauvaises nuits (< 5)
val NeonAmber = Color(0xFFFFC400) // Pour les nuits moyennes (5-7)

@Composable
fun SleepAnalyticsScreen(
    journalEntryViewModel: JournalEntryViewModel,
    subCategoryViewModel: SubCategoryViewModel,
    onBack: () -> Unit
) {
    val allEntries by journalEntryViewModel.allEntries.collectAsState(initial = emptyList())

    // États Navigation
    var currentWeekOffset by remember { mutableIntStateOf(0) }
    var ghostWeekOffset by remember { mutableIntStateOf(-1) }
    var monthlySummaryOffset by remember { mutableIntStateOf(0) }

    // Animations
    val animA = remember { Animatable(1f) }
    val animB = remember { Animatable(1f) }
    val animMonth = remember { Animatable(1f) }

    LaunchedEffect(currentWeekOffset) { animA.snapTo(0f); animA.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
    LaunchedEffect(ghostWeekOffset) { animB.snapTo(0f); animB.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
    LaunchedEffect(monthlySummaryOffset) { animMonth.snapTo(0f); animMonth.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }

    val factorA = animA.value
    val factorB = animB.value
    val factorMonth = animMonth.value

    // --- PREP DATA ---
    val (startCurrent, endCurrent) = getWeekRange(currentWeekOffset)
    val (startGhost, endGhost) = getWeekRange(ghostWeekOffset)

    val currentData = remember(allEntries, currentWeekOffset) {
        allEntries.filter { it.categoryName == "Sommeil" && it.date.time in startCurrent..endCurrent }
    }
    val ghostData = remember(allEntries, ghostWeekOffset) {
        allEntries.filter { it.categoryName == "Sommeil" && it.date.time in startGhost..endGhost }
    }

    // --- PREP DATA MENSUELLE ---
    val currentMonthEntries = remember(allEntries, monthlySummaryOffset) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, monthlySummaryOffset)
        val tM = cal.get(Calendar.MONTH); val tY = cal.get(Calendar.YEAR)
        allEntries.filter { val c = Calendar.getInstance(); c.time = it.date; it.categoryName == "Sommeil" && c.get(Calendar.MONTH) == tM && c.get(Calendar.YEAR) == tY }
    }
    val monthLabel = remember(monthlySummaryOffset) {
        val cal = Calendar.getInstance(); cal.add(Calendar.MONTH, monthlySummaryOffset)
        java.text.SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time).uppercase()
    }

    // --- CALCULS STATS (Helper functions plus bas) ---
    val curAvgDurationMin = calculateAvgDuration(currentData)
    val prevAvgDurationMin = calculateAvgDuration(ghostData)

    val curAvgQuality = calculateAvgQuality(currentData)
    val prevAvgQuality = calculateAvgQuality(ghostData)

    val curBedTime = calculateAverageTime(currentData.mapNotNull { it.sleepBedTime }, isBedTime = true)
    val curWakeTime = calculateAverageTime(currentData.mapNotNull { it.sleepWakeTime }, isBedTime = false)
    val prevBedTime = calculateAverageTime(ghostData.mapNotNull { it.sleepBedTime }, isBedTime = true)
    val prevWakeTime = calculateAverageTime(ghostData.mapNotNull { it.sleepWakeTime }, isBedTime = false)

    // Couleur dynamique Durée (8h = Top)
    val avgColorA = when {
        curAvgDurationMin >= 450 -> NeonBlue // > 7h30
        curAvgDurationMin >= 360 -> Color(0xFF00E5FF) // > 6h
        else -> Color(0xFFFF1744) // < 6h
    }

    val scrollState = rememberScrollState()
    val vignetteBrush = Brush.radialGradient(
        colors = listOf(Color(0xFF0D1826), DarkBackground),
        center = Offset.Unspecified,
        radius = 1500f
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(vignetteBrush)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // HEADER
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Color.White) }
            Spacer(Modifier.width(8.dp))
            Text("ANALYSE SOMMEIL", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        // SELECTEURS
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WeekSelectorRow("SEMAINE ANALYSÉE (A)", formatWeekLabel(startCurrent, endCurrent), NeonBlue, { currentWeekOffset-- }, { currentWeekOffset++ })
            WeekSelectorRow("COMPARÉE AVEC (B)", formatWeekLabel(startGhost, endGhost), GhostColor, { ghostWeekOffset-- }, { ghostWeekOffset++ })
        }

        Spacer(Modifier.height(4.dp))

        // 1. DUEL DURÉE + QUALITÉ
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SleepStatCard(
                title = "SEMAINE A",
                durationMin = curAvgDurationMin,
                quality = curAvgQuality,
                maxValue = 600f, // 10h max
                color = avgColorA,
                animFactor = factorA,
                modifier = Modifier.weight(1f)
            )
            SleepStatCard(
                title = "SEMAINE B",
                durationMin = prevAvgDurationMin,
                quality = prevAvgQuality,
                maxValue = 600f,
                color = GhostColor,
                animFactor = factorB,
                modifier = Modifier.weight(1f)
            )
        }

        // 2. HORAIRES
        SleepScheduleComparisonCard(
            bedA = curBedTime, wakeA = curWakeTime,
            bedB = prevBedTime, wakeB = prevWakeTime,
            animFactorA = factorA, animFactorB = factorB
        )

        // 3. & 4. FUSION : GRAPHIQUE RYTHME DE SOMMEIL (GANTT)
        SleepGanttChart(
            currentEntries = currentData,
            ghostEntries = ghostData,
            weekStartCurrent = startCurrent,
            weekStartGhost = startGhost,
            animFactorA = factorA,
            animFactorB = factorB,
            primaryColor = NeonBlue
        )

        // 4. bis GRAPHIQUE DURÉE (Montagne / Area Chart)
        SleepDurationAreaChart(
            currentEntries = currentData,
            ghostEntries = ghostData,
            weekStartCurrent = startCurrent,
            weekStartGhost = startGhost,
            animFactorA = factorA,
            animFactorB = factorB,
            primaryColor = NeonBlue
        )

        // 5. BILAN MENSUEL
        SleepMonthlySummaryCard(
            entries = currentMonthEntries,
            month = monthLabel,
            animFactor = factorMonth,
            onPrev = { monthlySummaryOffset-- },
            onNext = { monthlySummaryOffset++ }
        )

        Spacer(Modifier.height(120.dp))
    }
}

// --------------------------------------------------------------------------------
// HELPER FUNCTIONS
// --------------------------------------------------------------------------------

fun parseTime(time: String?): Int {
    if (time.isNullOrBlank()) return 0
    return try {
        val p = time.split(":")
        p[0].toInt() * 60 + p[1].toInt()
    } catch (e: Exception) { 0 }
}

fun calculateAvgDuration(entries: List<JournalEntry>): Int {
    if (entries.isEmpty()) return 0
    val totalMin = entries.sumOf { parseTime(it.sleepDuration) }
    return if (entries.isNotEmpty()) totalMin / entries.size else 0
}

fun calculateAvgQuality(entries: List<JournalEntry>): Float {
    val valid = entries.mapNotNull { it.sleepQuality }
    return if (valid.isNotEmpty()) valid.average().toFloat() else 0f
}

fun calculateAverageTime(times: List<String>, isBedTime: Boolean): String {
    if (times.isEmpty()) return "--:--"
    val minutesList = times.map {
        val m = parseTime(it)
        if (isBedTime && m > 720) m - 1440 else m
    }
    var avg = minutesList.average().toInt()
    if (avg < 0) avg += 1440
    val h = avg / 60
    val m = avg % 60
    return "%02d:%02d".format(h, m)
}

// --------------------------------------------------------------------------------
// COMPOSANTS UI
// --------------------------------------------------------------------------------

@Composable
fun SleepStatCard(
    title: String,
    durationMin: Int,
    quality: Float,
    maxValue: Float,
    color: Color,
    animFactor: Float,
    modifier: Modifier
) {
    val h = durationMin / 60
    val m = durationMin % 60

    Card(
        modifier = modifier.height(130.dp).shadow(20.dp, RoundedCornerShape(16.dp), spotColor = color, ambientColor = color),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, color.copy(alpha = 0.8f))
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Box(modifier = Modifier.align(Alignment.TopCenter).background(color.copy(alpha = 0.2f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(text = title, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Canvas(modifier = Modifier.fillMaxWidth().height(90.dp).align(Alignment.Center).offset(y = -10.dp)) {
                val strokeWidth = 8.dp.toPx()
                val radius = size.minDimension / 2
                val center = Offset(size.width / 2, size.height)
                drawArc(color = Color.DarkGray.copy(alpha = 0.3f), startAngle = 180f, sweepAngle = 180f, useCenter = false, topLeft = Offset(center.x - radius, center.y - radius), size = Size(radius * 2, radius * 2), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                val progress = (durationMin.toFloat() / maxValue).coerceIn(0f, 1f) * animFactor
                drawArc(color = color, startAngle = 180f, sweepAngle = 180f * progress, useCenter = false, topLeft = Offset(center.x - radius, center.y - radius), size = Size(radius * 2, radius * 2), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
            }
            Column(modifier = Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "${h}h ${m}m", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(text = "Qual. ${String.format("%.1f", quality)}/10", color = TextGray, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun SleepScheduleComparisonCard(
    bedA: String, wakeA: String,
    bedB: String, wakeB: String,
    animFactorA: Float, animFactorB: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(15.dp, RoundedCornerShape(16.dp), spotColor = NeonBlue, ambientColor = NeonBlue),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, NeonBlue.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("RYTHME DE SOMMEIL (Moyennes)", color = TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(16.dp))
            ScheduleRow(label = "Sem A", bed = bedA, wake = wakeA, color = NeonBlue, anim = animFactorA)
            Spacer(Modifier.height(12.dp))
            Divider(color = Color.White.copy(alpha=0.1f))
            Spacer(Modifier.height(12.dp))
            ScheduleRow(label = "Sem B", bed = bedB, wake = wakeB, color = GhostColor, anim = animFactorB)
        }
    }
}

@Composable
fun ScheduleRow(label: String, bed: String, wake: String, color: Color, anim: Float) {
    Row(
        modifier = Modifier.fillMaxWidth().alpha(0.5f + (0.5f * anim)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(4.dp, 24.dp).background(color, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(8.dp))
            Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Bedtime, null, tint = TextGray, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(bed, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WbSunny, null, tint = TextGray, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(wake, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun SleepMonthlySummaryCard(
    entries: List<JournalEntry>,
    month: String,
    animFactor: Float,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val totalMin = entries.sumOf { parseTime(it.sleepDuration) }
    val totalHours = totalMin / 60f
    val validNightsCount = entries.count { parseTime(it.sleepDuration) > 0 }
    val dailyAvgH = if (validNightsCount > 0) totalHours / validNightsCount else 0f
    val avgQual = calculateAvgQuality(entries)

    Card(
        modifier = Modifier.fillMaxWidth().shadow(15.dp, RoundedCornerShape(24.dp), spotColor = NeonBlue, ambientColor = NeonBlue),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(2.dp, NeonBlue.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.ChevronLeft, contentDescription = "Précédent", tint = TextGray) }
                Text(text = "BILAN : $month", color = TextGray, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                IconButton(onClick = onNext, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.ChevronRight, contentDescription = "Suivant", tint = TextGray) }
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = 10.dp.toPx(); val radius = size.minDimension / 2 - stroke / 2
                        drawCircle(color = Color.DarkGray.copy(alpha = 0.3f), radius = radius, style = Stroke(width = stroke, cap = StrokeCap.Round))
                        val targetProgress = (dailyAvgH / 9f).coerceIn(0f, 1f)
                        val animatedProgress = targetProgress * animFactor
                        if (animatedProgress > 0) {
                            drawArc(color = NeonBlue, startAngle = -90f, sweepAngle = 360f * animatedProgress, useCenter = false, style = Stroke(width = stroke, cap = StrokeCap.Round))
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "${String.format("%.1f", dailyAvgH)}h", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Moy/Nuit", color = TextGray, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.width(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.alpha(animFactor)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bedtime, null, tint = NeonBlue, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(text = "Total Mois : ${totalHours.toInt()}h", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WbSunny, null, tint = NeonBlue, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(text = "Qualité moy. ${String.format("%.1f", avgQual)}/10", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// Graphe pour visualiser la CONSISTANCE des horaires
// Graphe pour visualiser la CONSISTANCE des horaires (Supporte plusieurs siestes/nuits par jour)
@Composable
fun SleepGanttChart(
    currentEntries: List<JournalEntry>,
    ghostEntries: List<JournalEntry>,
    weekStartCurrent: Long,
    weekStartGhost: Long,
    animFactorA: Float,
    animFactorB: Float,
    primaryColor: Color
) {
    val startHour = 18
    val totalHours = 19
    val minMinutes = startHour * 60
    val maxMinutes = minMinutes + (totalHours * 60)

    data class SleepSession(
        val bedTimeMins: Int,
        val wakeTimeMins: Int,
        val durationLabel: String,
        val bedLabel: String,
        val wakeLabel: String,
        val quality: Float
    )

    fun mapToSessions(entries: List<JournalEntry>, startOfWeek: Long): Map<Int, List<SleepSession>> {
        val map = mutableMapOf<Int, MutableList<SleepSession>>()
        entries.forEach { entry ->
            val bed = parseTime(entry.sleepBedTime)
            val wake = parseTime(entry.sleepWakeTime)
            if (bed > 0 && wake > 0) {
                val diff = entry.date.time - startOfWeek
                val dayIndex = (diff / (1000 * 60 * 60 * 24)).toInt()
                if (dayIndex in 0..6) {
                    val normBed = if (bed < 1080) bed + 1440 else bed
                    val normWake = if (wake < 1080) wake + 1440 else wake
                    val safeWake = if (normWake <= normBed) normBed + 60 else normWake
                    val quality = entry.sleepQuality?.toFloat() ?: 0f

                    val session = SleepSession(
                        normBed, safeWake,
                        entry.sleepDuration ?: "?",
                        entry.sleepBedTime ?: "",
                        entry.sleepWakeTime ?: "",
                        quality
                    )
                    map.getOrPut(dayIndex) { mutableListOf() }.add(session)
                }
            }
        }
        return map
    }

    val currentSessionsMap = remember(currentEntries) { mapToSessions(currentEntries, weekStartCurrent) }
    val ghostSessionsMap = remember(ghostEntries) { mapToSessions(ghostEntries, weekStartGhost) }
    val daysLabels = listOf("L", "M", "M", "J", "V", "S", "D")

    var selection by remember { mutableStateOf<DataSelection?>(null) }

    val todayIndex = remember(weekStartCurrent) {
        val now = System.currentTimeMillis()
        if (now < weekStartCurrent) -1 else if (now > weekStartCurrent + 7 * 24 * 3600 * 1000) 7 else ((now - weekStartCurrent) / (24 * 3600 * 1000)).toInt()
    }

    Card(
        modifier = Modifier.fillMaxWidth().height(280.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, primaryColor.copy(alpha = 0.6f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("RYTHME & DURÉE", color = TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                if (selection != null) {
                    val sel = selection!!
                    val sessionsList = if (sel.isGhost) ghostSessionsMap[sel.dayIndex] else currentSessionsMap[sel.dayIndex]
                    val session = sessionsList?.getOrNull(sel.sessionIndex)

                    val headerColor = if (sel.isGhost) GhostColor else {
                        val q = session?.quality ?: 0f
                        when {
                            q < 5f -> NeonRed
                            q < 8f -> NeonAmber
                            else -> primaryColor
                        }
                    }

                    if (session != null) {
                        val text = when(sel.clickType) {
                            1 -> "Réveil : ${session.wakeLabel}"
                            0 -> "Coucher : ${session.bedLabel}"
                            else -> "Durée : ${session.durationLabel}"
                        }
                        Text(text, color = headerColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).background(NeonRed, CircleShape)); Spacer(Modifier.width(2.dp))
                        Box(Modifier.size(6.dp).background(NeonAmber, CircleShape)); Spacer(Modifier.width(2.dp))
                        Box(Modifier.size(6.dp).background(primaryColor, CircleShape)); Spacer(Modifier.width(4.dp))
                        Text("Qualité", color = TextGray, fontSize = 9.sp)
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.size(8.dp).background(GhostColor, CircleShape)); Spacer(Modifier.width(4.dp))
                        Text("Passé", color = TextGray, fontSize = 9.sp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures(onPress = { offset ->
                        val paddingStart = 25.dp.toPx(); val paddingEnd = 10.dp.toPx()
                        val graphWidth = size.width - paddingStart - paddingEnd
                        val xStep = graphWidth / 6f
                        val relativeX = offset.x - paddingStart
                        val rawIndex = (relativeX / xStep).roundToInt()

                        if (rawIndex in 0..6) {
                            val range = (maxMinutes - minMinutes).toFloat()
                            val fullHeight = size.height - 20.dp.toPx()

                            fun findSessionHit(isGhost: Boolean): DataSelection? {
                                val list = if(isGhost) ghostSessionsMap[rawIndex] else currentSessionsMap[rawIndex]
                                list?.forEachIndexed { idx, s ->
                                    // --- INVERSION AXE Y (CLICK) ---
                                    // fullHeight - (...) pour partir du bas
                                    val yBed = fullHeight - ((s.bedTimeMins - minMinutes) / range * fullHeight)
                                    val yWake = fullHeight - ((s.wakeTimeMins - minMinutes) / range * fullHeight)

                                    // Note : avec l'inversion, yWake est plus petit (plus haut) que yBed
                                    val top = yWake
                                    val bottom = yBed
                                    val height = bottom - top

                                    if (offset.y >= top - 20f && offset.y <= bottom + 20f) {
                                        val type = when {
                                            // Réveil est en HAUT maintenant (top)
                                            kotlin.math.abs(offset.y - top) < 40f || (offset.y < top + height * 0.25) -> 1
                                            // Coucher est en BAS maintenant (bottom)
                                            kotlin.math.abs(offset.y - bottom) < 40f || (offset.y > bottom - height * 0.25) -> 0
                                            else -> 2
                                        }
                                        return DataSelection(rawIndex, isGhost, type, idx)
                                    }
                                }
                                return null
                            }

                            var result = findSessionHit(false)
                            if (result == null) result = findSessionHit(true)

                            selection = result
                            tryAwaitRelease()
                            selection = null
                        }
                    })
                }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val fullWidth = size.width; val fullHeight = size.height - 20.dp.toPx()
                    val paddingStart = 25.dp.toPx(); val paddingEnd = 10.dp.toPx()
                    val graphWidth = fullWidth - paddingStart - paddingEnd
                    val xStep = graphWidth / 6f; val barWidth = 6.dp.toPx()
                    val gridHours = listOf(20, 24, 28, 32, 36); val range = (maxMinutes - minMinutes).toFloat()

                    // GRILLE
                    gridHours.forEach { h ->
                        val mins = h * 60
                        if (mins in minMinutes..maxMinutes) {
                            val ratio = (mins - minMinutes) / range
                            // --- INVERSION AXE Y (GRILLE) ---
                            val y = fullHeight - (ratio * fullHeight)

                            drawLine(color = Color.White.copy(alpha = 0.1f), start = Offset(paddingStart, y), end = Offset(paddingStart + graphWidth, y), strokeWidth = 1.dp.toPx())
                            val label = "%02d:00".format(h % 24)
                            drawContext.canvas.nativeCanvas.drawText(label, paddingStart - 30f, y + 4f, android.graphics.Paint().apply { setColor(android.graphics.Color.GRAY); textSize = 9.sp.toPx(); textAlign = android.graphics.Paint.Align.RIGHT })
                        }
                    }

                    for (i in 0..6) {
                        val centerX = paddingStart + (i * xStep)

                        // --- GHOST ---
                        val ghostList = ghostSessionsMap[i]
                        ghostList?.forEachIndexed { sIdx, ghost ->
                            // --- INVERSION AXE Y (DESSIN) ---
                            val yBed = fullHeight - ((ghost.bedTimeMins - minMinutes) / range * fullHeight)
                            val yWake = fullHeight - ((ghost.wakeTimeMins - minMinutes) / range * fullHeight)

                            // yWake est en haut (petit Y), yBed en bas (grand Y)
                            // Donc la hauteur est yBed - yWake
                            val height = (yBed - yWake) * animFactorB
                            val midY = yWake + (yBed - yWake) / 2 // Milieu inchangé
                            val animTop = midY - height/2

                            val isSelected = selection?.dayIndex == i && selection?.isGhost == true && selection?.sessionIndex == sIdx

                            drawRoundRect(
                                color = if (isSelected) GhostColor else GhostColor.copy(alpha = 0.3f),
                                topLeft = Offset(centerX - barWidth - 2.dp.toPx(), animTop),
                                size = Size(barWidth, height),
                                cornerRadius = CornerRadius(barWidth/2)
                            )
                        }

                        // --- CURRENT ---
                        val currentList = currentSessionsMap[i]
                        val showCurrent = weekStartCurrent < System.currentTimeMillis() - 604800000L || i <= todayIndex

                        if (showCurrent) {
                            currentList?.forEachIndexed { sIdx, current ->
                                // --- INVERSION AXE Y (DESSIN) ---
                                val yBed = fullHeight - ((current.bedTimeMins - minMinutes) / range * fullHeight)
                                val yWake = fullHeight - ((current.wakeTimeMins - minMinutes) / range * fullHeight)

                                val height = (yBed - yWake) * animFactorA
                                val midY = yWake + (yBed - yWake) / 2
                                val animTop = midY - height/2
                                val animBottom = midY + height/2

                                val isSelected = selection?.dayIndex == i && selection?.isGhost == false && selection?.sessionIndex == sIdx

                                val barColor = when {
                                    current.quality < 5f -> NeonRed
                                    current.quality < 8f -> NeonAmber
                                    else -> primaryColor
                                }

                                drawRoundRect(
                                    color = if (isSelected) barColor else barColor.copy(alpha = 0.9f),
                                    topLeft = Offset(centerX + 2.dp.toPx(), animTop),
                                    size = Size(barWidth, height),
                                    cornerRadius = CornerRadius(barWidth/2)
                                )

                                drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(centerX + 2.dp.toPx() + barWidth/2, animTop))
                                drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(centerX + 2.dp.toPx() + barWidth/2, animBottom))

                                if (isSelected) {
                                    val type = selection!!.clickType
                                    // 0 = Coucher (Bas/Bottom maintenant), 1 = Réveil (Haut/Top maintenant)
                                    // animTop correspond visuellement au Haut (Réveil)
                                    // animBottom correspond visuellement au Bas (Coucher)
                                    if (type == 1) drawCircle(Color.White, 4.dp.toPx(), Offset(centerX + 2.dp.toPx() + barWidth/2, animTop))
                                    if (type == 0) drawCircle(Color.White, 4.dp.toPx(), Offset(centerX + 2.dp.toPx() + barWidth/2, animBottom))
                                }
                            }
                        }

                        // Label jour
                        val dayLabelPaint = android.graphics.Paint().apply { setColor(android.graphics.Color.GRAY); textSize = 10.sp.toPx(); textAlign = android.graphics.Paint.Align.CENTER; isFakeBoldText = true }
                        if (selection?.dayIndex == i) dayLabelPaint.color = android.graphics.Color.WHITE
                        drawContext.canvas.nativeCanvas.drawText(daysLabels[i], centerX, size.height, dayLabelPaint)
                    }

                    // --- TOOLTIP ---
                    if (selection != null) {
                        val sel = selection!!
                        val sessionsList = if (sel.isGhost) ghostSessionsMap[sel.dayIndex] else currentSessionsMap[sel.dayIndex]
                        val session = sessionsList?.getOrNull(sel.sessionIndex)

                        val tooltipColor = if (sel.isGhost) GhostColor else {
                            val q = session?.quality ?: 0f
                            when {
                                q < 5f -> NeonRed
                                q < 8f -> NeonAmber
                                else -> primaryColor
                            }
                        }

                        if (session != null) {
                            val idx = sel.dayIndex
                            // --- INVERSION AXE Y (TOOLTIP) ---
                            val yBed = fullHeight - ((session.bedTimeMins - minMinutes) / range * fullHeight)
                            val yWake = fullHeight - ((session.wakeTimeMins - minMinutes) / range * fullHeight)

                            val height = (yBed - yWake) * (if(sel.isGhost) animFactorB else animFactorA)
                            val midY = yWake + (yBed - yWake) / 2
                            val animTop = midY - height/2 // Réveil (Haut)
                            val animBottom = midY + height/2 // Coucher (Bas)

                            val (label, targetY) = when (sel.clickType) {
                                0 -> session.bedLabel to animBottom // Coucher en bas
                                1 -> session.wakeLabel to animTop   // Réveil en haut
                                else -> "Durée: ${session.durationLabel}" to midY
                            }

                            val centerX = paddingStart + (idx * xStep) + (if (!sel.isGhost) 2.dp.toPx() + barWidth/2 else -barWidth - 2.dp.toPx() + barWidth/2)
                            val paintTooltip = android.graphics.Paint().apply { setColor(android.graphics.Color.WHITE); textSize = 12.sp.toPx(); typeface = android.graphics.Typeface.DEFAULT_BOLD; textAlign = android.graphics.Paint.Align.CENTER }
                            val textBounds = android.graphics.Rect(); paintTooltip.getTextBounds(label, 0, label.length, textBounds)
                            val tooltipW = textBounds.width() + 20f; val tooltipH = textBounds.height() + 15f; val tooltipY = targetY - 25.dp.toPx()

                            drawRoundRect(color = Color(0xFF151515), topLeft = Offset(centerX - tooltipW / 2, tooltipY - tooltipH), size = Size(tooltipW, tooltipH), cornerRadius = CornerRadius(8f), style = Fill)
                            drawRoundRect(color = tooltipColor, topLeft = Offset(centerX - tooltipW / 2, tooltipY - tooltipH), size = Size(tooltipW, tooltipH), cornerRadius = CornerRadius(8f), style = Stroke(width = 1.dp.toPx()))
                            drawContext.canvas.nativeCanvas.drawText(label, centerX, tooltipY - tooltipH / 2 + textBounds.height() / 3, paintTooltip)
                        }
                    }
                }
            }
        }
    }
}

// Classe de donnée helper pour gérer la sélection précise
data class DataSelection(
    val dayIndex: Int,
    val isGhost: Boolean,
    val clickType: Int, // 0=bed, 1=wake, 2=middle
    val sessionIndex: Int // NOUVEAU : Pour savoir sur quelle barre on a cliqué
)

@Composable
fun SleepDurationAreaChart(
    currentEntries: List<JournalEntry>,
    ghostEntries: List<JournalEntry>,
    weekStartCurrent: Long,
    weekStartGhost: Long,
    animFactorA: Float,
    animFactorB: Float,
    primaryColor: Color
) {
    // --- MODIFICATION ICI : On additionne (+=) les durées ---
    fun mapToDuration(entries: List<JournalEntry>, startOfWeek: Long): List<Float> {
        val days = FloatArray(7) { 0f }
        entries.forEach { entry ->
            val diff = entry.date.time - startOfWeek
            val dayIndex = (diff / (1000 * 60 * 60 * 24)).toInt()
            if (dayIndex in 0..6) {
                // AVANT : days[dayIndex] = ... (Écrasait la valeur précédente)
                // APRÈS : days[dayIndex] += ... (Additionne pour le total cumulé)
                days[dayIndex] += parseTime(entry.sleepDuration) / 60f
            }
        }
        return days.toList()
    }

    val currentDurations = remember(currentEntries) { mapToDuration(currentEntries, weekStartCurrent) }
    val ghostDurations = remember(ghostEntries) { mapToDuration(ghostEntries, weekStartGhost) }

    val daysLabels = listOf("L", "M", "M", "J", "V", "S", "D")
    val maxVal = 12f // Échelle max de 12h pour le graphique

    var selectedPoint by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    val todayIndex = remember(weekStartCurrent) {
        val now = System.currentTimeMillis()
        if (now < weekStartCurrent) -1 else if (now > weekStartCurrent + 7 * 24 * 3600 * 1000) 7 else ((now - weekStartCurrent) / (24 * 3600 * 1000)).toInt()
    }

    Card(
        modifier = Modifier.fillMaxWidth().height(240.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, primaryColor.copy(alpha = 0.6f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("DURÉE TOTALE (Siestes incluses)", color = TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                if (selectedPoint != null) {
                    val (index, isGhost) = selectedPoint!!
                    val value = if (isGhost) ghostDurations[index] else currentDurations[index]
                    val color = if (isGhost) GhostColor else primaryColor
                    val h = value.toInt()
                    val m = ((value - h) * 60).toInt()
                    Text(text = "${if (isGhost) "Passé" else "Actuel"} : ${h}h ${m}m", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier.fillMaxSize().onSizeChanged { canvasSize = it.toSize() }.pointerInput(Unit) {
                    detectTapGestures(onPress = { offset ->
                        val paddingStart = 20.dp.toPx(); val paddingEnd = 10.dp.toPx()
                        val graphWidth = size.width - paddingStart - paddingEnd
                        val xStep = graphWidth / 6f
                        val relativeX = offset.x - paddingStart
                        val rawIndex = (relativeX / xStep).roundToInt()
                        if (rawIndex in 0..6) {
                            val valA = currentDurations[rawIndex]; val valB = ghostDurations[rawIndex]
                            val hasA = valA > 0 && (weekStartCurrent < System.currentTimeMillis() - 604800000L || rawIndex <= todayIndex)
                            val hasB = valB > 0
                            val fullHeight = size.height - 20.dp.toPx()

                            // On clamp les valeurs pour éviter les crashs si > 12h
                            val safeValA = valA.coerceAtMost(maxVal)
                            val safeValB = valB.coerceAtMost(maxVal)

                            val yA = fullHeight - (safeValA / maxVal * fullHeight); val yB = fullHeight - (safeValB / maxVal * fullHeight)
                            val distA = kotlin.math.abs(offset.y - yA); val distB = kotlin.math.abs(offset.y - yB)

                            if (hasA && (!hasB || distA < distB)) selectedPoint = rawIndex to false
                            else if (hasB) selectedPoint = rawIndex to true
                            else selectedPoint = null
                            tryAwaitRelease()
                            selectedPoint = null
                        }
                    })
                }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val fullWidth = size.width; val fullHeight = size.height - 20.dp.toPx()
                    val paddingStart = 20.dp.toPx(); val paddingEnd = 10.dp.toPx()
                    val graphWidth = fullWidth - paddingStart - paddingEnd
                    val xStep = graphWidth / 6f

                    // Lignes repères (4h, 6h, 8h, 10h)
                    listOf(4, 6, 8, 10).forEach { h ->
                        val y = fullHeight - (h.toFloat() / maxVal * fullHeight)
                        if (y >= 0) {
                            drawLine(color = Color.White.copy(alpha = 0.05f), start = Offset(paddingStart, y), end = Offset(paddingStart + graphWidth, y), strokeWidth = 1.dp.toPx())
                            drawContext.canvas.nativeCanvas.drawText("${h}h", paddingStart - 10f, y + 4f, android.graphics.Paint().apply { setColor(android.graphics.Color.GRAY); textSize = 9.sp.toPx(); textAlign = android.graphics.Paint.Align.RIGHT })
                        }
                    }

                    fun createSmoothPath(data: List<Float>, animFactor: Float, limitIndex: Int): Path {
                        val path = Path(); val points = mutableListOf<Offset>()
                        for (i in 0..6) {
                            if (i <= limitIndex) {
                                val valH = data[i].coerceAtMost(maxVal) // Protection visuelle max 12h
                                val x = paddingStart + (i * xStep)
                                val y = fullHeight - ((valH * animFactor) / maxVal * fullHeight)
                                points.add(Offset(x, y))
                            }
                        }
                        if (points.isEmpty()) return path
                        path.moveTo(points.first().x, points.first().y)
                        for (i in 0 until points.size - 1) {
                            val p1 = points[i]; val p2 = points[i + 1]
                            val cp1X = p1.x + (p2.x - p1.x) / 2; val cp1Y = p1.y
                            val cp2X = p1.x + (p2.x - p1.x) / 2; val cp2Y = p2.y
                            path.cubicTo(cp1X, cp1Y, cp2X, cp2Y, p2.x, p2.y)
                        }
                        return path
                    }

                    if (ghostDurations.any { it > 0 }) {
                        val ghostPath = createSmoothPath(ghostDurations, animFactorB, 6)
                        drawPath(path = ghostPath, color = GhostColor.copy(alpha = 0.5f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)))
                        ghostDurations.forEachIndexed { i, v ->
                            if (v > 0) {
                                val safeV = v.coerceAtMost(maxVal)
                                val x = paddingStart + (i * xStep)
                                val y = fullHeight - ((safeV * animFactorB) / maxVal * fullHeight)
                                drawCircle(GhostColor.copy(alpha=0.5f), 3.dp.toPx(), Offset(x, y))
                            }
                        }
                    }

                    val limitIndex = if (weekStartCurrent > System.currentTimeMillis() - 604800000L) todayIndex else 6
                    val currentPathStr = createSmoothPath(currentDurations, animFactorA, limitIndex)

                    if (!currentPathStr.isEmpty) {
                        val fillPath = Path(); fillPath.addPath(currentPathStr)
                        val lastX = paddingStart + (kotlin.math.min(limitIndex, currentDurations.size-1) * xStep)
                        fillPath.lineTo(lastX, fullHeight); fillPath.lineTo(paddingStart, fullHeight); fillPath.close()
                        val brush = Brush.verticalGradient(colors = listOf(primaryColor.copy(alpha = 0.6f), primaryColor.copy(alpha = 0.0f)), startY = fullHeight - (10f / maxVal * fullHeight), endY = fullHeight)
                        drawPath(path = fillPath, brush = brush)
                        drawPath(path = currentPathStr, color = primaryColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }

                    for (i in 0..limitIndex) {
                        val valA = currentDurations[i]
                        if (valA > 0) {
                            val safeValA = valA.coerceAtMost(maxVal)
                            val x = paddingStart + (i * xStep)
                            val y = fullHeight - ((safeValA * animFactorA) / maxVal * fullHeight)
                            val isSelected = selectedPoint?.first == i && selectedPoint?.second == false
                            drawCircle(Color.White, if(isSelected) 6.dp.toPx() else 3.dp.toPx(), Offset(x, y))
                            if (isSelected) {
                                drawCircle(primaryColor, 8.dp.toPx(), Offset(x, y), style = Stroke(width = 2.dp.toPx()))
                                drawLine(color = Color.White.copy(alpha=0.3f), start = Offset(x, y), end = Offset(x, fullHeight), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f))
                            }
                        }
                    }

                    daysLabels.forEachIndexed { index, label ->
                        val x = paddingStart + (index * xStep)
                        val paint = android.graphics.Paint().apply { setColor(android.graphics.Color.GRAY); textSize = 10.sp.toPx(); textAlign = android.graphics.Paint.Align.CENTER; isFakeBoldText = true }
                        if (selectedPoint?.first == index) paint.color = android.graphics.Color.WHITE
                        drawContext.canvas.nativeCanvas.drawText(label, x, size.height, paint)
                    }

                    // --- TOOLTIP ---
                    if (selectedPoint != null) {
                        val (index, isGhostSelection) = selectedPoint!!
                        val value = if (isGhostSelection) ghostDurations[index] else currentDurations[index]
                        val color = if (isGhostSelection) GhostColor else primaryColor
                        val animFactor = if (isGhostSelection) animFactorB else animFactorA

                        // Protection visuelle pour position Y
                        val safeValue = value.coerceAtMost(maxVal)

                        val x = paddingStart + (index * xStep)
                        val y = fullHeight - ((safeValue * animFactor) / maxVal * fullHeight)
                        val h = value.toInt(); val m = ((value - h) * 60).toInt()
                        val label = "${h}h ${if(m>0) "${m}m" else ""}"

                        val paintTooltip = android.graphics.Paint().apply { setColor(android.graphics.Color.WHITE); textSize = 12.sp.toPx(); typeface = android.graphics.Typeface.DEFAULT_BOLD; textAlign = android.graphics.Paint.Align.CENTER }
                        val textBounds = android.graphics.Rect(); paintTooltip.getTextBounds(label, 0, label.length, textBounds)
                        val tooltipW = textBounds.width() + 20f; val tooltipH = textBounds.height() + 15f; val tooltipY = y - 25.dp.toPx()

                        drawRoundRect(color = Color(0xFF151515), topLeft = Offset(x - tooltipW / 2, tooltipY - tooltipH), size = Size(tooltipW, tooltipH), cornerRadius = CornerRadius(8f), style = Fill)
                        drawRoundRect(color = color, topLeft = Offset(x - tooltipW / 2, tooltipY - tooltipH), size = Size(tooltipW, tooltipH), cornerRadius = CornerRadius(8f), style = Stroke(width = 1.dp.toPx()))
                        drawContext.canvas.nativeCanvas.drawText(label, x, tooltipY - tooltipH / 2 + textBounds.height() / 3, paintTooltip)
                    }
                }
            }
        }
    }
}