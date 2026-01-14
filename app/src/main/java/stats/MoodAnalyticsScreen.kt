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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.SentimentNeutral
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.SentimentVeryDissatisfied
import androidx.compose.material.icons.filled.SentimentVerySatisfied
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
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.pow

// --- COULEURS DYNAMIQUES HUMEUR ---
val MoodGreen = Color(0xFF00E676)   // 8-10 (Top)
val MoodOrange = Color(0xFFFF9100)  // 5-7 (Moyen)
val MoodRed = Color(0xFFFF1744)     // 1-4 (Mauvais)
val MoodGhost = Color.Gray

// Fonction utilitaire pour choisir la couleur
fun getMoodColor(score: Float): Color {
    return when {
        score >= 8f -> MoodGreen
        score >= 5f -> MoodOrange
        else -> MoodRed
    }
}

// Fonction utilitaire pour l'icône
fun getMoodIcon(score: Float): androidx.compose.ui.graphics.vector.ImageVector {
    return when {
        score >= 8f -> Icons.Default.SentimentVerySatisfied
        score >= 5f -> Icons.Default.SentimentNeutral
        else -> Icons.Default.SentimentVeryDissatisfied
    }
}

@Composable
fun MoodAnalyticsScreen(
    journalEntryViewModel: JournalEntryViewModel,
    onBack: () -> Unit
) {
    val allEntries by journalEntryViewModel.allEntries.collectAsState(initial = emptyList())

    // --- NAVIGATION TEMPORELLE ---
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
    val (startCurrent, endCurrent) = getWeekRangeLocal(currentWeekOffset)
    val (startGhost, endGhost) = getWeekRangeLocal(ghostWeekOffset)

    val currentData = remember(allEntries, currentWeekOffset) {
        allEntries.filter { it.categoryName == "Humeur" && it.date.time in startCurrent..endCurrent }
    }
    val ghostData = remember(allEntries, ghostWeekOffset) {
        allEntries.filter { it.categoryName == "Humeur" && it.date.time in startGhost..endGhost }
    }

    // --- CALCULS SEMAINE ---
    val curScores = currentData.mapNotNull { it.moodScore }
    val curAvg = if (curScores.isNotEmpty()) curScores.average().toFloat() else 0f

    val mainNeonColor = getMoodColor(curAvg)

    val prevScores = ghostData.mapNotNull { it.moodScore }
    val prevAvg = if (prevScores.isNotEmpty()) prevScores.average().toFloat() else 0f

    // --- DATA MOIS ---
    val currentMonthEntries = remember(allEntries, monthlySummaryOffset) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, monthlySummaryOffset)
        val tM = cal.get(Calendar.MONTH); val tY = cal.get(Calendar.YEAR)
        allEntries.filter {
            val c = Calendar.getInstance(); c.time = it.date
            it.categoryName == "Humeur" && c.get(Calendar.MONTH) == tM && c.get(Calendar.YEAR) == tY
        }
    }
    val monthLabel = remember(monthlySummaryOffset) {
        val cal = Calendar.getInstance(); cal.add(Calendar.MONTH, monthlySummaryOffset)
        java.text.SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time).uppercase()
    }

    val scrollState = rememberScrollState()

    // Fond Radial subtil (Teinte dynamique selon la moyenne actuelle)
    val vignetteColor = getMoodColor(curAvg).copy(alpha = 0.15f)
    val vignetteBrush = Brush.radialGradient(
        colors = listOf(vignetteColor, DarkBackground),
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
            Text("ANALYSE HUMEUR", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        // SÉLECTEURS
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MoodWeekSelectorRow("SEMAINE ANALYSÉE (A)", formatWeekLabelLocal(startCurrent, endCurrent), getMoodColor(curAvg), { currentWeekOffset-- }, { currentWeekOffset++ })
            MoodWeekSelectorRow("COMPARÉE AVEC (B)", formatWeekLabelLocal(startGhost, endGhost), MoodGhost, { ghostWeekOffset-- }, { ghostWeekOffset++ })
        }

        // 1. DUEL ÉTAT D'ESPRIT (Moyennes)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MoodScoreCard(
                title = "MOYENNE A",
                score = curAvg,
                isGhost = false,
                animFactor = factorA,
                modifier = Modifier.weight(1f)
            )
            MoodScoreCard(
                title = "MOYENNE B",
                score = prevAvg,
                isGhost = true,
                animFactor = factorB,
                modifier = Modifier.weight(1f)
            )
        }

        // 2. COURBE ÉLECTROCARDIOGRAMME
        MoodLineChart(
            currentEntries = currentData,
            ghostEntries = ghostData,
            weekStartCurrent = startCurrent,
            weekStartGhost = startGhost,
            animFactorA = factorA,
            animFactorB = factorB,
            neonColor = mainNeonColor
        )

        // 3. ZONES (Top / Neutre / Down)
        MoodZonesCard(
            currentEntries = currentData,
            ghostEntries = ghostData,
            animFactorA = factorA,
            animFactorB = factorB,
            neonColor = mainNeonColor
        )

        // 4. BILAN MENSUEL
        MoodMonthlySummaryCard(
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
// COMPOSANTS
// --------------------------------------------------------------------------------

@Composable
fun MoodScoreCard(
    title: String,
    score: Float,
    isGhost: Boolean,
    animFactor: Float,
    modifier: Modifier
) {
    val color = if (isGhost) MoodGhost else getMoodColor(score)
    val icon = getMoodIcon(score)
    val displayScore = if (score > 0) String.format("%.1f", score) else "--"

    Card(
        modifier = modifier
            .height(140.dp)
            .shadow(if(isGhost) 0.dp else 20.dp, RoundedCornerShape(16.dp), spotColor = color, ambientColor = color),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, color.copy(alpha = if(isGhost) 0.5f else 0.8f))
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            // Label
            Box(modifier = Modifier.align(Alignment.TopCenter).background(color.copy(alpha = 0.2f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(text = title, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }

            // Smiley Central avec Animation
            Box(modifier = Modifier.align(Alignment.Center).alpha(animFactor)) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(50.dp)
                )
            }

            // Note en bas
            Column(modifier = Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "$displayScore / 10", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MoodLineChart(
    currentEntries: List<JournalEntry>,
    ghostEntries: List<JournalEntry>,
    weekStartCurrent: Long,
    weekStartGhost: Long,
    animFactorA: Float,
    animFactorB: Float,
    neonColor: Color // <--- NOUVEAU PARAMÈTRE
) {
    fun mapToDays(entries: List<JournalEntry>, start: Long): List<Float> {
        val daysTotal = FloatArray(7) { 0f }
        val daysCount = IntArray(7) { 0 }

        entries.forEach { e ->
            val diff = e.date.time - start
            val idx = (diff / (1000 * 60 * 60 * 24)).toInt()
            if (idx in 0..6 && e.moodScore != null) {
                daysTotal[idx] += e.moodScore.toFloat()
                daysCount[idx]++
            }
        }

        // On retourne la moyenne pour chaque jour, ou 0 si vide
        return daysTotal.mapIndexed { index, total ->
            if (daysCount[index] > 0) total / daysCount[index] else 0f
        }
    }

    val curDays = remember(currentEntries) { mapToDays(currentEntries, weekStartCurrent) }
    val ghostDays = remember(ghostEntries) { mapToDays(ghostEntries, weekStartGhost) }
    val labels = listOf("L", "M", "M", "J", "V", "S", "D")
    var selectedPoint by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }

    val gradientBrush = Brush.verticalGradient(colors = listOf(MoodGreen, MoodOrange, MoodRed))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            // AJOUT DU GLOW :
            .shadow(15.dp, RoundedCornerShape(16.dp), spotColor = neonColor, ambientColor = neonColor),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty),
        shape = RoundedCornerShape(16.dp),
        // CHANGEMENT DE LA BORDURE :
        border = BorderStroke(2.dp, neonColor.copy(alpha = 0.5f))
    ) {
        // ... Le contenu du Column reste exactement le même ...
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ÉVOLUTION", color = TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                if (selectedPoint != null) {
                    val (idx, isGhost) = selectedPoint!!
                    val value = if(isGhost) ghostDays[idx] else curDays[idx]
                    val color = if(isGhost) MoodGhost else getMoodColor(value)
                    val label = if(isGhost) "Passé" else "Actuel"
                    Text("$label : ${value.toInt()}/10", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(onPress = { offset ->
                    val xStep = size.width / 6f
                    val idx = (offset.x / xStep).let { kotlin.math.round(it).toInt() }.coerceIn(0, 6)
                    if (curDays[idx] > 0) selectedPoint = idx to false
                    else if (ghostDays[idx] > 0) selectedPoint = idx to true
                    tryAwaitRelease()
                    selectedPoint = null
                })
            }) {
                Canvas(Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height - 20.dp.toPx()
                    val xStep = width / 6f

                    // 1. DÉFINITION DU STYLE DU TEXTE (Petit, Gris clair)
                    val textPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#70FFFFFF") // Blanc transparent (Gris clair)
                        textSize = 9.sp.toPx() // Taille petite
                        textAlign = android.graphics.Paint.Align.LEFT // Alignement à gauche
                    }

                    // 2. BOUCLE LIGNES + CHIFFRES
                    val gridSteps = listOf(2, 4, 6, 8, 10)

                    gridSteps.forEach { step ->
                        val y = height - (step.toFloat() / 10f * height)

                        // La ligne (inchangée)
                        drawLine(
                            color = Color.White.copy(alpha = 0.1f),
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1.dp.toPx()
                        )

                        // Le numéro (Juste au-dessus de la ligne, décalé de 4px vers la droite)
                        drawContext.canvas.nativeCanvas.drawText(
                            step.toString(),
                            10f,       // X : Légèrement décalé du bord gauche
                            y - 6f,    // Y : Juste au-dessus de la ligne
                            textPaint
                        )
                    }

                    fun drawCurve(data: List<Float>, isGhost: Boolean, anim: Float) {
                        val path = Path()
                        var first = true
                        data.forEachIndexed { i, v ->
                            if (v > 0) {
                                val x = i * xStep
                                val y = height - (v / 10f * height)
                                val animatedY = height - ((height - y) * anim)
                                if (first) { path.moveTo(x, animatedY); first = false }
                                else {
                                    val prevIdx = data.subList(0, i).indexOfLast { it > 0 }
                                    if (prevIdx >= 0) {
                                        val prevX = prevIdx * xStep
                                        val prevY = height - ((height - (height - (data[prevIdx] / 10f * height))) * anim)
                                        val cx = (prevX + x) / 2
                                        path.cubicTo(cx, prevY, cx, animatedY, x, animatedY)
                                    } else { path.lineTo(x, animatedY) }
                                }
                                val pointColor = if (isGhost) MoodGhost else getMoodColor(v)
                                drawCircle(pointColor, 4.dp.toPx(), Offset(x, animatedY))
                                if (selectedPoint?.first == i && selectedPoint?.second == isGhost) {
                                    drawCircle(Color.White, 6.dp.toPx(), Offset(x, animatedY), style = Stroke(2.dp.toPx()))
                                }
                            }
                        }
                        if (!first) {
                            if (isGhost) drawPath(path, MoodGhost.copy(alpha=0.4f), style = Stroke(width=2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
                            else drawPath(path, gradientBrush, style = Stroke(width=4.dp.toPx(), cap = StrokeCap.Round))
                        }
                    }
                    drawCurve(ghostDays, true, animFactorB)
                    drawCurve(curDays, false, animFactorA)
                    labels.forEachIndexed { i, txt ->
                        drawContext.canvas.nativeCanvas.drawText(txt, i * xStep, size.height, android.graphics.Paint().apply { color = android.graphics.Color.GRAY; textSize = 10.sp.toPx(); textAlign = android.graphics.Paint.Align.CENTER })
                    }
                }
            }
        }
    }
}

@Composable
fun MoodZonesCard(
    currentEntries: List<JournalEntry>,
    ghostEntries: List<JournalEntry>,
    animFactorA: Float,
    animFactorB: Float,
    neonColor: Color // <--- NOUVEAU PARAMÈTRE
) {
    fun countZones(entries: List<JournalEntry>): Triple<Int, Int, Int> {
        var top = 0; var mid = 0; var bad = 0

        // 1. On groupe les entrées par jour (Numéro du jour dans l'année)
        val groupedByDay = entries.groupBy {
            val c = Calendar.getInstance(); c.time = it.date; "${c.get(Calendar.DAY_OF_YEAR)}"
        }

        // 2. Pour chaque jour, on calcule la moyenne
        groupedByDay.values.forEach { dailyEntries ->
            val scores = dailyEntries.mapNotNull { it.moodScore }
            if (scores.isNotEmpty()) {
                val dailyAvg = scores.average() // Moyenne du jour
                when {
                    dailyAvg >= 8 -> top++
                    dailyAvg >= 5 -> mid++
                    else -> bad++
                }
            }
        }
        return Triple(top, mid, bad)
    }

    val (topA, midA, badA) = remember(currentEntries) { countZones(currentEntries) }
    val (topB, midB, badB) = remember(ghostEntries) { countZones(ghostEntries) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            // AJOUT DU GLOW :
            .shadow(15.dp, RoundedCornerShape(16.dp), spotColor = neonColor, ambientColor = neonColor),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty),
        shape = RoundedCornerShape(16.dp),
        // CHANGEMENT DE LA BORDURE (passage à 2.dp aussi pour l'uniformité) :
        border = BorderStroke(2.dp, neonColor.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("RÉPARTITION (Jours)", color = TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(16.dp))

            ZoneRow("TOP (8-10)", MoodGreen, topA, topB, animFactorA, animFactorB)
            Spacer(Modifier.height(12.dp))
            ZoneRow("MOYEN (5-7)", MoodOrange, midA, midB, animFactorA, animFactorB)
            Spacer(Modifier.height(12.dp))
            ZoneRow("DIFFICILE (1-4)", MoodRed, badA, badB, animFactorA, animFactorB)
        }
    }
}

@Composable
fun ZoneRow(label: String, color: Color, valA: Int, valB: Int, animA: Float, animB: Float) {
    val maxVal = 7f // Max 7 jours dans une semaine

    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row {
                if (valA > 0) Text("$valA j", color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                if (valB > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text("vs $valB j", color = MoodGhost, fontSize = 10.sp)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.height(8.dp).fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Color.DarkGray.copy(alpha=0.3f))) {
            // Ghost en dessous
            if (valB > 0) {
                Box(Modifier.fillMaxHeight().fillMaxWidth((valB / maxVal) * animB).background(MoodGhost.copy(alpha=0.5f)))
            }
            // Actuel au dessus
            if (valA > 0) {
                Box(Modifier.fillMaxHeight().fillMaxWidth((valA / maxVal) * animA).background(color))
            }
        }
    }
}

@Composable
fun MoodMonthlySummaryCard(
    entries: List<JournalEntry>,
    month: String,
    animFactor: Float,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val scores = entries.mapNotNull { it.moodScore }
    val avg = if (scores.isNotEmpty()) scores.average().toFloat() else 0f

    // Tendance (Ecart type simplifié pour déterminer la stabilité)
    val trendText = if (scores.isEmpty()) "Aucune donnée" else {
        // CORRECTION ICI : Utilisation de la syntaxe d'extension .pow(2.0)
        // Ou plus simplement : diff * diff (ce qui évite les soucis d'import)
        val variance = scores.map {
            val diff = (it - avg).toDouble()
            diff * diff
        }.average()

        val stdDev = kotlin.math.sqrt(variance)
        when {
            stdDev < 1.5 -> "Mois Stable"
            stdDev < 2.5 -> "Mois Variable"
            else -> "Montagnes Russes"
        }
    }

    val mainColor = getMoodColor(avg)

    Card(
        modifier = Modifier.fillMaxWidth().shadow(15.dp, RoundedCornerShape(24.dp), spotColor = mainColor, ambientColor = mainColor),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(2.dp, mainColor.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.ChevronLeft, null, tint = TextGray) }
                Text("BILAN : $month", color = TextGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onNext, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.ChevronRight, null, tint = TextGray) }
            }
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Jauge
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(Color.DarkGray.copy(alpha=0.3f), style = Stroke(10.dp.toPx()))
                        val progress = (avg / 10f).coerceIn(0f, 1f) * animFactor
                        if (progress > 0) drawArc(mainColor, -90f, 360f*progress, false, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(String.format("%.1f", avg), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("/ 10", color = TextGray, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.width(24.dp))

                // Infos
                Column(modifier = Modifier.alpha(animFactor)) {
                    Text(trendText, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("${scores.size} entrées ce mois", color = TextGray, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))

                    // Répartition rapide (mini barres)
                    val (top, mid, bad) = scores.fold(Triple(0,0,0)) { acc, s ->
                        Triple(acc.first + (if(s>=8) 1 else 0), acc.second + (if(s in 5..7) 1 else 0), acc.third + (if(s<5) 1 else 0))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(MoodGreen, CircleShape)); Spacer(Modifier.width(4.dp))
                        Text("$top", color = TextGray, fontSize = 10.sp)
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(8.dp).background(MoodOrange, CircleShape)); Spacer(Modifier.width(4.dp))
                        Text("$mid", color = TextGray, fontSize = 10.sp)
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(8.dp).background(MoodRed, CircleShape)); Spacer(Modifier.width(4.dp))
                        Text("$bad", color = TextGray, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

// Helpers locaux pour éviter les imports croisés
@Composable
fun MoodWeekSelectorRow(label: String, dateRange: String, color: Color, onPrev: () -> Unit, onNext: () -> Unit) {
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