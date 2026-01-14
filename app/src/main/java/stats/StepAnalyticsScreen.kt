package com.example.nicotracker.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nicotracker.CardBackgroundEmpty
import com.example.nicotracker.DarkBackground
import com.example.nicotracker.TextGray
import com.example.nicotracker.data.JournalEntry
import com.example.nicotracker.data.JournalEntryViewModel
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

// --- COULEURS THÈME PAS ---
val NeonCyan = Color(0xFF00E5FF)
val GhostCyan = Color.Gray

// --- FACTEURS DE CONVERSION ---
const val STEPS_TO_KM = 0.000762
const val STEPS_TO_KCAL = 0.04

@Composable
fun StepAnalyticsScreen(
    journalEntryViewModel: JournalEntryViewModel,
    onBack: () -> Unit
) {
    val allEntries by journalEntryViewModel.allEntries.collectAsState(initial = emptyList())

    // --- NAVIGATION ---
    var currentWeekOffset by remember { mutableIntStateOf(0) }
    var ghostWeekOffset by remember { mutableIntStateOf(-1) }
    var monthlySummaryOffset by remember { mutableIntStateOf(0) }

    // --- ANIMATIONS ---
    val animA = remember { Animatable(1f) }
    val animB = remember { Animatable(1f) }
    val animMonth = remember { Animatable(1f) }

    LaunchedEffect(currentWeekOffset) { animA.snapTo(0f); animA.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
    LaunchedEffect(ghostWeekOffset) { animB.snapTo(0f); animB.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
    LaunchedEffect(monthlySummaryOffset) { animMonth.snapTo(0f); animMonth.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }

    val factorA = animA.value
    val factorB = animB.value
    val factorMonth = animMonth.value

    // --- DATA SEMAINE ---
    val (startCurrent, endCurrent) = getStepWeekRange(currentWeekOffset)
    val (startGhost, endGhost) = getStepWeekRange(ghostWeekOffset)

    val currentData = remember(allEntries, currentWeekOffset) {
        allEntries.filter { it.categoryName == "Nombre de pas" && it.date.time in startCurrent..endCurrent }
    }
    val ghostData = remember(allEntries, ghostWeekOffset) {
        allEntries.filter { it.categoryName == "Nombre de pas" && it.date.time in startGhost..endGhost }
    }

    // --- DATA MOIS ---
    val currentMonthEntries = remember(allEntries, monthlySummaryOffset) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, monthlySummaryOffset)
        val tM = cal.get(Calendar.MONTH); val tY = cal.get(Calendar.YEAR)
        allEntries.filter {
            val c = Calendar.getInstance(); c.time = it.date
            it.categoryName == "Nombre de pas" && c.get(Calendar.MONTH) == tM && c.get(Calendar.YEAR) == tY
        }
    }
    val monthLabel = remember(monthlySummaryOffset) {
        val cal = Calendar.getInstance(); cal.add(Calendar.MONTH, monthlySummaryOffset)
        java.text.SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time).uppercase()
    }

    // --- CALCULS MOYENNE MENSUELLE (INTELLIGENT) ---
    val totalMonthSteps = currentMonthEntries.sumOf { it.stepsCount ?: 0 }

    // Calcul du diviseur pour le mois (Jours écoulés vs Jours totaux)
    val daysInMonthDivider = remember(monthlySummaryOffset) {
        val cal = Calendar.getInstance()
        val now = Calendar.getInstance() // Aujourd'hui

        cal.add(Calendar.MONTH, monthlySummaryOffset) // Le mois qu'on regarde

        if (cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) && cal.get(Calendar.MONTH) == now.get(Calendar.MONTH)) {
            // C'est le mois en cours -> on divise par le jour d'aujourd'hui (ex: le 12)
            now.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
        } else if (cal.before(now)) {
            // C'est un mois passé -> on divise par le nombre de jours total du mois (28, 30, 31)
            cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        } else {
            // Futur -> 1 (pour éviter div/0)
            1
        }
    }

    val monthlyDailyAvg = if (daysInMonthDivider > 0) totalMonthSteps / daysInMonthDivider else 0

    // --- CALCULS SEMAINE ---
    val curTotalSteps = currentData.sumOf { it.stepsCount ?: 0 }
    val prevTotalSteps = ghostData.sumOf { it.stepsCount ?: 0 }

    fun getElapsedDays(startWeek: Long, endWeek: Long): Int {
        val now = Calendar.getInstance()

        // 1. Futur -> 0
        if (now.timeInMillis < startWeek) return 0

        // 2. Passé (Semaine finie) -> 7
        if (now.timeInMillis > endWeek) return 7

        // 3. Présent (Semaine en cours) - Calcul en millisecondes pour éviter le bug de l'an nouveau
        val diffMillis = now.timeInMillis - startWeek
        val daysPassed = (diffMillis / (1000 * 60 * 60 * 24)).toInt()

        // On ajoute +1 pour compter le jour courant
        return (daysPassed + 1).coerceIn(1, 7)
    }

    val elapsedDaysA = getElapsedDays(startCurrent, endCurrent)
    val elapsedDaysB = 7

    val curDailyAvg = if (elapsedDaysA > 0) curTotalSteps / elapsedDaysA else 0
    val prevDailyAvg = if (prevTotalSteps > 0) prevTotalSteps / elapsedDaysB else 0

    val maxGaugeValue = max(max(curDailyAvg, prevDailyAvg).toFloat(), 12000f) * 1.1f

    val scrollState = rememberScrollState()
    val vignetteBrush = Brush.radialGradient(
        colors = listOf(Color(0xFF003333), DarkBackground),
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Color.White) }
            Spacer(Modifier.width(8.dp))
            Text("ANALYSE ACTIVITÉ", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StepWeekSelectorRow("SEMAINE ANALYSÉE (A)", formatStepWeekLabel(startCurrent, endCurrent), NeonCyan, { currentWeekOffset-- }, { currentWeekOffset++ })
            StepWeekSelectorRow("COMPARÉE AVEC (B)", formatStepWeekLabel(startGhost, endGhost), GhostCyan, { ghostWeekOffset-- }, { ghostWeekOffset++ })
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TurbineCard("MOYENNE A", curDailyAvg, curTotalSteps, maxGaugeValue, NeonCyan, factorA, Modifier.weight(1f))
            TurbineCard("MOYENNE B", prevDailyAvg, prevTotalSteps, maxGaugeValue, GhostCyan, factorB, Modifier.weight(1f))
        }

        CapsuleBarChart(currentData, ghostData, startCurrent, startGhost, factorA, factorB)
        RealImpactCard(curTotalSteps, prevTotalSteps, factorA, factorB)
        VictoryStreakCard(currentData, ghostData, startCurrent, startGhost, factorA, factorB)

        // On passe maintenant la moyenne calculée (monthlyDailyAvg)
        StepMonthlySummaryCard(
            entries = currentMonthEntries,
            month = monthLabel,
            dailyAverage = monthlyDailyAvg,
            animFactor = factorMonth,
            onPrev = { monthlySummaryOffset-- },
            onNext = { monthlySummaryOffset++ }
        )

        Spacer(Modifier.height(120.dp))
    }
}

// --- COMPOSANTS ---

@Composable
fun TurbineCard(
    title: String,
    avg: Int,
    total: Int,
    maxValue: Float,
    color: Color,
    animFactor: Float,
    modifier: Modifier
) {
    Card(
        modifier = modifier.height(150.dp).shadow(20.dp, RoundedCornerShape(16.dp), spotColor = color, ambientColor = color),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, color.copy(alpha = 0.8f))
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Box(modifier = Modifier.align(Alignment.TopCenter).background(color.copy(alpha = 0.2f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(text = title, color = color, fontSize = 7.sp, fontWeight = FontWeight.Bold)
            }
            Canvas(modifier = Modifier.fillMaxWidth().height(100.dp).align(Alignment.Center).offset(y = 10.dp)) {
                val radius = size.minDimension / 2 -15f
                val center = Offset(size.width / 2, size.height / 2 + 10f)
                val totalSegments = 30
                val angleStep = 240f / totalSegments
                for(i in 0..totalSegments) {
                    val angle = 150f + (i * angleStep)
                    val rad = Math.toRadians(angle.toDouble())
                    val startX = center.x + (radius - 15f) * Math.cos(rad).toFloat()
                    val startY = center.y + (radius - 15f) * Math.sin(rad).toFloat()
                    val endX = center.x + radius * Math.cos(rad).toFloat()
                    val endY = center.y + radius * Math.sin(rad).toFloat()
                    drawLine(Color.DarkGray.copy(alpha=0.5f), Offset(startX, startY), Offset(endX, endY), strokeWidth = 4f, cap = StrokeCap.Round)
                }
                val progress = (avg.toFloat() / maxValue).coerceIn(0f, 1f) * animFactor
                val activeSegments = (totalSegments * progress).toInt()
                for(i in 0..activeSegments) {
                    val angle = 150f + (i * angleStep)
                    val rad = Math.toRadians(angle.toDouble())
                    val startX = center.x + (radius - 15f) * Math.cos(rad).toFloat()
                    val startY = center.y + (radius - 15f) * Math.sin(rad).toFloat()
                    val endX = center.x + radius * Math.cos(rad).toFloat()
                    val endY = center.y + radius * Math.sin(rad).toFloat()
                    drawLine(color, Offset(startX, startY), Offset(endX, endY), strokeWidth = 5f, cap = StrokeCap.Round)
                }
            }
            Column(modifier = Modifier.align(Alignment.Center).offset(y = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = String.format(Locale.getDefault(), "%,d", avg), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(text = "pas /jour", color = TextGray, fontSize = 10.sp)
            }
            Text(text = "Total ${String.format(Locale.getDefault(), "%,d", total)}", color = TextGray.copy(alpha=0.7f), fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
fun CapsuleBarChart(
    currentEntries: List<JournalEntry>,
    ghostEntries: List<JournalEntry>,
    weekStartCurrent: Long,
    weekStartGhost: Long,
    animFactorA: Float,
    animFactorB: Float
) {
    fun mapToDays(entries: List<JournalEntry>, start: Long): List<Float> {
        val days = FloatArray(7) { 0f }
        entries.forEach { e ->
            val diff = e.date.time - start
            val idx = (diff / (1000 * 60 * 60 * 24)).toInt()
            if (idx in 0..6) days[idx] += (e.stepsCount ?: 0).toFloat()
        }
        return days.toList()
    }

    val curDays = remember(currentEntries, weekStartCurrent) { mapToDays(currentEntries, weekStartCurrent) }
    val ghostDays = remember(ghostEntries) { mapToDays(ghostEntries, weekStartGhost) }
    val maxVal = max(curDays.maxOrNull() ?: 10000f, ghostDays.maxOrNull() ?: 10000f).coerceAtLeast(10000f) * 1.1f
    val labels = listOf("L", "M", "M", "J", "V", "S", "D")
    var selectedPoint by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth().height(260.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, NeonCyan.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("HISTORIQUE ACTIVITÉ", color = TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                if (selectedPoint != null) {
                    val (idx, isGhost) = selectedPoint!!
                    val value = if(isGhost) ghostDays[idx] else curDays[idx]
                    val color = if(isGhost) GhostCyan else NeonCyan
                    val label = if(isGhost) "Passé" else "Actuel"
                    Text("$label : ${value.toInt()} pas", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))

            Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(onPress = { offset ->
                    val barSpace = size.width / 7
                    val index = (offset.x / barSpace).toInt().coerceIn(0, 6)
                    val chartHeight = size.height - 20.dp.toPx()
                    val hA = (curDays[index] / maxVal * chartHeight)
                    val hB = (ghostDays[index] / maxVal * chartHeight)
                    val yA = chartHeight - hA
                    val yB = chartHeight - hB
                    val distA = kotlin.math.abs(offset.y - yA)
                    val distB = kotlin.math.abs(offset.y - yB)

                    if (curDays[index] > 0 && ghostDays[index] > 0) selectedPoint = index to (distB < distA)
                    else if (curDays[index] > 0) selectedPoint = index to false
                    else if (ghostDays[index] > 0) selectedPoint = index to true

                    tryAwaitRelease()
                    selectedPoint = null
                })
            }) {
                Canvas(Modifier.fillMaxSize()) {
                    val barSpace = size.width / 7
                    val barWidth = 8.dp.toPx()
                    val chartHeight = size.height - 20.dp.toPx()

                    // Grille 2000 pas
                    val stepSize = 2000
                    val numberOfLines = (maxVal / stepSize).toInt()
                    val textPaint = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#70FFFFFF"); textSize = 9.sp.toPx(); textAlign = android.graphics.Paint.Align.LEFT }

                    for (i in 1..numberOfLines) {
                        val stepValue = i * stepSize
                        val y = chartHeight - (stepValue / maxVal * chartHeight)
                        if (y > 0) {
                            drawLine(color = Color.White.copy(alpha=0.1f), start = Offset(0f, y), end = Offset(size.width, y), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                            val label = "${stepValue / 1000}k"
                            drawContext.canvas.nativeCanvas.drawText(label, 0f, y - 6f, textPaint)
                        }
                    }

                    for (i in 0..6) {
                        val x = i * barSpace + (barSpace / 2)

                        val hB = (ghostDays[i] / maxVal * chartHeight) * animFactorB
                        if (hB > 0) {
                            val rect = androidx.compose.ui.geometry.Rect(offset = Offset(x + 2.dp.toPx(), chartHeight - hB), size = Size(barWidth, hB))
                            drawRoundRect(color = GhostCyan.copy(alpha=0.5f), topLeft = rect.topLeft, size = rect.size, cornerRadius = CornerRadius(barWidth/2))
                        }

                        val hA = (curDays[i] / maxVal * chartHeight) * animFactorA
                        if (hA > 0) {
                            drawRoundRect(color = NeonCyan, topLeft = Offset(x - barWidth - 2.dp.toPx(), chartHeight - hA), size = Size(barWidth, hA), cornerRadius = CornerRadius(barWidth/2))
                        }

                        drawContext.canvas.nativeCanvas.drawText(labels[i], x, size.height, android.graphics.Paint().apply { color = android.graphics.Color.GRAY; textSize = 10.sp.toPx(); textAlign = android.graphics.Paint.Align.CENTER })
                    }

                    if (selectedPoint != null) {
                        val (idx, isGhost) = selectedPoint!!
                        val value = if(isGhost) ghostDays[idx] else curDays[idx]
                        val color = if(isGhost) GhostCyan else NeonCyan
                        val offsetX = if(isGhost) 2.dp.toPx() + barWidth/2 else -2.dp.toPx() - barWidth/2
                        val x = idx * barSpace + (barSpace / 2) + offsetX
                        val h = (value / maxVal * chartHeight) * (if(isGhost) animFactorB else animFactorA)
                        val y = chartHeight - h

                        if (value > 0) {
                            drawCircle(Color.White, 3.dp.toPx(), Offset(x, y))
                            val kCal = (value * STEPS_TO_KCAL).toInt()
                            val label = "${value.toInt()} | ${kCal}kcal"
                            val paintTooltip = android.graphics.Paint().apply { setColor(android.graphics.Color.WHITE); textSize = 11.sp.toPx(); typeface = android.graphics.Typeface.DEFAULT_BOLD; textAlign = android.graphics.Paint.Align.CENTER }
                            val textBounds = android.graphics.Rect(); paintTooltip.getTextBounds(label, 0, label.length, textBounds)
                            val tooltipW = textBounds.width() + 20f; val tooltipH = textBounds.height() + 15f; val tooltipY = y - 25.dp.toPx()

                            drawRoundRect(Color(0xFF151515), topLeft = Offset(x - tooltipW/2, tooltipY - tooltipH), size = Size(tooltipW, tooltipH), cornerRadius = CornerRadius(5f))
                            drawRoundRect(color, topLeft = Offset(x - tooltipW/2, tooltipY - tooltipH), size = Size(tooltipW, tooltipH), cornerRadius = CornerRadius(5f), style = Stroke(1.dp.toPx()))
                            drawContext.canvas.nativeCanvas.drawText(label, x, tooltipY - tooltipH/2 + textBounds.height()/3, paintTooltip)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RealImpactCard(curSteps: Int, prevSteps: Int, animFactorA: Float, animFactorB: Float) {
    val curKm = (curSteps * STEPS_TO_KM).toFloat()
    val prevKm = (prevSteps * STEPS_TO_KM).toFloat()
    val curKcal = (curSteps * STEPS_TO_KCAL).toFloat()
    val prevKcal = (prevSteps * STEPS_TO_KCAL).toFloat()
    val maxKm = max(curKm, prevKm).coerceAtLeast(1f) * 1.1f
    val maxKcal = max(curKcal, prevKcal).coerceAtLeast(1f) * 1.1f

    Card(
        modifier = Modifier.fillMaxWidth().shadow(15.dp, RoundedCornerShape(16.dp), spotColor = NeonCyan, ambientColor = NeonCyan),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, NeonCyan.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("IMPACT RÉEL", color = TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(16.dp))
            ImpactRow(Icons.Default.Map, "Distance", curKm, prevKm, "km", maxKm, animFactorA, animFactorB)
            Divider(color = Color.White.copy(alpha=0.05f), modifier = Modifier.padding(vertical=12.dp))
            ImpactRow(Icons.Default.LocalFireDepartment, "Énergie", curKcal, prevKcal, "kcal", maxKcal, animFactorA, animFactorB)
        }
    }
}

@Composable
fun ImpactRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, valA: Float, valB: Float, unit: String, maxVal: Float, animA: Float, animB: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(Color.White.copy(alpha=0.05f), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = NeonCyan, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row {
                    Text("${valA.toInt()} $unit", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text("vs ${valB.toInt()}", color = GhostCyan, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.height(6.dp).fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(Color.DarkGray.copy(alpha=0.3f))) {
                if (valB > 0) Box(Modifier.fillMaxHeight().fillMaxWidth((valB / maxVal) * animB).background(GhostCyan.copy(alpha=0.5f)))
                if (valA > 0) Box(Modifier.fillMaxHeight().fillMaxWidth((valA / maxVal) * animA).background(NeonCyan))
            }
        }
    }
}

@Composable
fun VictoryStreakCard(
    currentEntries: List<JournalEntry>,
    ghostEntries: List<JournalEntry>,
    weekStartCurrent: Long,
    weekStartGhost: Long,
    animFactorA: Float,
    animFactorB: Float
) {
    // ... (Code de calcul des streaks inchangé) ...
    fun getStreaks(entries: List<JournalEntry>, start: Long): List<Boolean> {
        val streaks = BooleanArray(7) { false }
        val cal = Calendar.getInstance(Locale.FRANCE)
        entries.forEach { e ->
            cal.time = e.date
            var dayIdx = cal.get(Calendar.DAY_OF_WEEK) - 2
            if (dayIdx == -1) dayIdx = 6
            if (dayIdx in 0..6 && (e.stepsCount ?: 0) >= 10000) {
                streaks[dayIdx] = true
            }
        }
        return streaks.toList()
    }

    val curStreaks = remember(currentEntries, weekStartCurrent) { getStreaks(currentEntries, weekStartCurrent) }
    val ghostStreaks = remember(ghostEntries, weekStartGhost) { getStreaks(ghostEntries, weekStartGhost) }
    val days = listOf("L", "M", "M", "J", "V", "S", "D")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(15.dp, RoundedCornerShape(16.dp), spotColor = NeonCyan, ambientColor = NeonCyan),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, NeonCyan.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("OBJECTIF 10K (Victoires)", color = TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("${curStreaks.count { it }} / 7", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (i in 0..6) {
                    val isVictory = curStreaks[i]
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {

                        // --- CORRECTION ALIGNEMENT & GLOW ---

                        // 1. Conteneur FIXE de 40dp pour TOUS les jours.
                        // Cela garantit que le centre (le point) est toujours au même endroit.
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // 2. Le GLOW (Plus intense et plus large)
                            if (isVictory) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize() // Prend les 40dp entiers
                                        .graphicsLayer { alpha = animFactorA }
                                        .background(
                                            brush = Brush.radialGradient(
                                                // SYNTAXE CORRIGÉE : on utilise "position to couleur"
                                                0.0f to Color.White.copy(alpha = 0.4f),  // Cœur blanc (intensité)
                                                0.4f to NeonCyan.copy(alpha = 0.8f),     // Cyan fort
                                                0.7f to NeonCyan.copy(alpha = 0.2f),     // Cyan diffus
                                                1.0f to Color.Transparent                // Fin transparente
                                            ),
                                            shape = CircleShape
                                        )
                                )
                            }

                            // 3. Le POINT PHYSIQUE (Toujours 24dp)
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer {
                                        alpha = animFactorA
                                        shape = CircleShape
                                        clip = true
                                    }
                                    .background(if (isVictory) NeonCyan else Color.DarkGray.copy(alpha = 0.3f), CircleShape)
                                    // Bordure plus blanche si victoire pour effet "tube néon"
                                    .border(if(isVictory) 2.dp else 1.dp, if (isVictory) Color.White.copy(alpha=0.9f) else Color.White.copy(alpha = 0.1f), CircleShape)
                            )
                        }

                        // --- FIN CORRECTION ---

                        Spacer(Modifier.height(4.dp)) // Réduit un peu car le conteneur est plus grand (40 vs 24)

                        // Point Ghost
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(if (ghostStreaks[i]) GhostCyan else Color.Transparent, CircleShape)
                                .border(1.dp, if (ghostStreaks[i]) GhostCyan else Color.DarkGray, CircleShape)
                                .alpha(animFactorB)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(days[i], color = TextGray, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

// MISE A JOUR : PARAMETRE DAILY AVERAGE AJOUTÉ + FORMATAGE
@Composable
fun StepMonthlySummaryCard(
    entries: List<JournalEntry>,
    month: String,
    dailyAverage: Int, // <-- Nouveau paramètre
    animFactor: Float,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val totalSteps = entries.sumOf { it.stepsCount ?: 0 }
    val totalKm = (totalSteps * STEPS_TO_KM).toInt()
    val funFact = when {
        totalKm > 400 -> "Wow, Paris -> Londres !"
        totalKm > 200 -> "Traversée de la Suisse !"
        totalKm > 100 -> "2 marathons et demi !"
        totalKm > 50 -> "Belle promenade !"
        else -> "Début du voyage..."
    }

    Card(modifier = Modifier.fillMaxWidth().shadow(15.dp, RoundedCornerShape(24.dp), spotColor = NeonCyan, ambientColor = NeonCyan), colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty), shape = RoundedCornerShape(24.dp), border = BorderStroke(2.dp, NeonCyan.copy(alpha = 0.5f))) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.ChevronLeft, null, tint = TextGray) }
                Text("BILAN : $month", color = TextGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onNext, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.ChevronRight, null, tint = TextGray) }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(Color.DarkGray.copy(alpha=0.3f), style = Stroke(10.dp.toPx()))
                        val progress = (totalSteps / 300000f).coerceIn(0f, 1f) * animFactor
                        if (progress > 0) drawArc(NeonCyan, -90f, 360f*progress, false, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // FORMATAGE PRECIS ICI (Plus de 'k')
                        Text(
                            text = String.format(Locale.getDefault(), "%,d", totalSteps),
                            color = Color.White,
                            fontSize = 18.sp, // Un peu plus petit pour que ça rentre si le chiffre est grand
                            fontWeight = FontWeight.Bold
                        )
                        Text("Pas", color = TextGray, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.width(24.dp))
                Column(modifier = Modifier.alpha(animFactor)) {
                    Text("$totalKm km parcourus", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(funFact, color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    // FORMATAGE PRECIS ICI
                    Text(
                        text = "Moyenne : ${String.format(Locale.getDefault(), "%,d", dailyAverage)} / jour",
                        color = TextGray,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

// FONCTIONS RENOMMÉES ET PRIVÉES POUR ÉVITER LES CONFLITS AVEC D'AUTRES FICHIERS
private fun getStepWeekRange(offsetWeeks: Int): Pair<Long, Long> {
    val cal = Calendar.getInstance(Locale.FRANCE)
    cal.firstDayOfWeek = Calendar.MONDAY
    cal.time = java.util.Date()
    while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) cal.add(Calendar.DAY_OF_YEAR, -1)
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    cal.add(Calendar.WEEK_OF_YEAR, offsetWeeks)
    val start = cal.timeInMillis
    cal.add(Calendar.DAY_OF_YEAR, 6)
    cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
    return Pair(start, cal.timeInMillis)
}

private fun formatStepWeekLabel(start: Long, end: Long): String {
    val fmt = java.text.SimpleDateFormat("dd MMM", Locale.getDefault())
    return "${fmt.format(java.util.Date(start))} - ${fmt.format(java.util.Date(end))}"
}

@Composable
fun StepWeekSelectorRow(label: String, dateRange: String, color: Color, onPrev: () -> Unit, onNext: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPrev, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.ChevronLeft, null, tint = TextGray) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, color = TextGray.copy(alpha = 0.7f), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(dateRange, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onNext, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.ChevronRight, null, tint = TextGray) }
        }
    }
}