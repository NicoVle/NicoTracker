package com.example.nicotracker.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nicotracker.data.JournalEntry
import com.example.nicotracker.data.JournalEntryViewModel
import com.example.nicotracker.data.SubCategoryViewModel
import com.example.nicotracker.DarkBackground
import com.example.nicotracker.CardBackgroundEmpty
import com.example.nicotracker.TextGray
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

// --- UTILITAIRES DATES ---
fun getWeekRange(offsetWeeks: Int): Pair<Long, Long> {
    val cal = Calendar.getInstance(Locale.FRANCE)
    cal.firstDayOfWeek = Calendar.MONDAY
    cal.time = Date()
    // On recule jusqu'au lundi de la semaine en cours
    while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
        cal.add(Calendar.DAY_OF_YEAR, -1)
    }
    // Reset heures
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)

    // Décalage
    cal.add(Calendar.WEEK_OF_YEAR, offsetWeeks)

    val startOfWeek = cal.timeInMillis

    // Fin de semaine (Dimanche 23:59:59)
    cal.add(Calendar.DAY_OF_YEAR, 6)
    cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
    val endOfWeek = cal.timeInMillis

    return Pair(startOfWeek, endOfWeek)
}

fun formatWeekLabel(startMillis: Long, endMillis: Long): String {
    val fmt = SimpleDateFormat("dd MMM", Locale.getDefault())
    return "${fmt.format(Date(startMillis))} - ${fmt.format(Date(endMillis))}"
}

@Composable
fun SportAnalyticsScreen(
    journalEntryViewModel: JournalEntryViewModel,
    subCategoryViewModel: SubCategoryViewModel,
    primaryColor: Color,
    onBack: () -> Unit
) {
    val allEntries by journalEntryViewModel.allEntries.collectAsState(initial = emptyList())

    // États SEMAINE (Haut de page)
    var currentWeekOffset by remember { mutableIntStateOf(0) }
    var ghostWeekOffset by remember { mutableIntStateOf(-1) }

    // État MOIS (Bas de page - Indépendant)
    var monthlySummaryOffset by remember { mutableIntStateOf(0) }

    // --- MODIFICATION : 2 ANIMATIONS DISTINCTES ---
    val animA = remember { Animatable(1f) }
    val animB = remember { Animatable(1f) }
    val animMonth = remember { Animatable(1f) }
    // Déclencheur pour Semaine A (Actuelle)
    LaunchedEffect(currentWeekOffset) {
        animA.snapTo(0f)
        animA.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }

    // Déclencheur pour Semaine B (Ghost)
    LaunchedEffect(ghostWeekOffset) {
        animB.snapTo(0f)
        animB.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }
    // Déclencheur pour le MOIS
    LaunchedEffect(monthlySummaryOffset) {
        animMonth.snapTo(0f) // Remet à 0
        animMonth.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) // Lance l'anim
    }
    val factorA = animA.value
    val factorB = animB.value
    val factorMonth = animMonth.value

    // --- DATA COMPARATIVES (SEMAINES) ---
    val (startCurrent, endCurrent) = getWeekRange(currentWeekOffset)
    val currentData = remember(allEntries, currentWeekOffset) {
        allEntries.filter {
            it.categoryName == "Sport" &&
                    it.date.time in startCurrent..endCurrent &&
                    (it.sportIntensity ?: 0) >= 6
        }
    }

    val (startGhost, endGhost) = getWeekRange(ghostWeekOffset)
    val ghostData = remember(allEntries, ghostWeekOffset) {
        allEntries.filter {
            it.categoryName == "Sport" &&
                    it.date.time in startGhost..endGhost &&
                    (it.sportIntensity ?: 0) >= 6
        }
    }

    // --- DATA MENSUELLE (Indépendante) ---
    val currentMonthEntries = remember(allEntries, monthlySummaryOffset) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, monthlySummaryOffset)
        val targetMonth = cal.get(Calendar.MONTH)
        val targetYear = cal.get(Calendar.YEAR)

        allEntries.filter {
            val c = Calendar.getInstance()
            c.time = it.date
            it.categoryName == "Sport" &&
                    c.get(Calendar.MONTH) == targetMonth &&
                    c.get(Calendar.YEAR) == targetYear &&
                    (it.sportIntensity ?: 0) >= 6
        }
    }

    // Label du mois sélectionné
    val monthLabel = remember(monthlySummaryOffset) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, monthlySummaryOffset)
        val fmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        fmt.format(cal.time).uppercase()
    }

    // --- CALCULS SEMAINE ---
    val curSessions = currentData.size
    val prevSessions = ghostData.size

    val curIntensity = if (curSessions > 0) currentData.mapNotNull { it.sportIntensity }.average().toFloat() else 0f
    val prevIntensity = if (prevSessions > 0) ghostData.mapNotNull { it.sportIntensity }.average().toFloat() else 0f

    val curDuration = if (curSessions > 0) currentData.mapNotNull { it.sportDurationMinutes }.average().toFloat() else 0f
    val prevDuration = if (prevSessions > 0) ghostData.mapNotNull { it.sportDurationMinutes }.average().toFloat() else 0f

    val ghostColor = Color.Gray
    val scrollState = rememberScrollState()

    // --- FOND VIGNETTE ---
    val vignetteBrush = Brush.radialGradient(
        colors = listOf(
            Color(0xFF252525),
            DarkBackground
        ),
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
        // --- HEADER ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Text("ANALYSE SPORT", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        // --- SÉLECTEURS ---
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WeekSelectorRow(
                label = "SEMAINE ANALYSÉE (A)",
                dateRange = formatWeekLabel(startCurrent, endCurrent),
                color = primaryColor,
                onPrev = { currentWeekOffset-- },
                onNext = { currentWeekOffset++ }
            )
            WeekSelectorRow(
                label = "COMPARÉE AVEC (B)",
                dateRange = formatWeekLabel(startGhost, endGhost),
                color = ghostColor,
                onPrev = { ghostWeekOffset-- },
                onNext = { ghostWeekOffset++ }
            )
        }

        Spacer(Modifier.height(4.dp))

        // 1. DUEL SÉANCES
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ComparisonSquareCard(
                title = "SEMAINE A",
                value = "$curSessions",
                label = "Séances",
                borderColor = primaryColor,
                modifier = Modifier.weight(1f)
            )
            ComparisonSquareCard(
                title = "SEMAINE B",
                value = "$prevSessions",
                label = "Séances",
                borderColor = ghostColor,
                modifier = Modifier.weight(1f)
            )
        }

        // 2. RÉPARTITION COMPARÉE
        StackedDistributionCard(
            currentEntries = currentData,
            ghostEntries = ghostData,
            subCategoryViewModel = subCategoryViewModel,
            primaryColor = primaryColor,
            ghostColor = ghostColor,
            animFactorA = factorA,
            animFactorB = factorB
        )

        // 3. DUEL INTENSITÉ
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GaugeSquareCard(
                title = "SEMAINE A",
                value = curIntensity,
                maxValue = 10f,
                label = "Intensité Moy.",
                color = primaryColor,
                animFactor = factorA,
                modifier = Modifier.weight(1f)
            )
            GaugeSquareCard(
                title = "SEMAINE B",
                value = prevIntensity,
                maxValue = 10f,
                label = "Intensité Moy.",
                color = ghostColor,
                animFactor = factorB,
                modifier = Modifier.weight(1f)
            )
        }

        // 4. DUEL DURÉE
        DurationTimelineCard(
            curDuration = curDuration,
            prevDuration = prevDuration,
            primaryColor = primaryColor,
            ghostColor = ghostColor,
            animFactorA = factorA,
            animFactorB = factorB
        )

        // 5. BILAN MENSUEL (Indépendant)
        MonthlySummaryCard(
            entries = currentMonthEntries,
            subCategoryViewModel = subCategoryViewModel,
            primaryColor = primaryColor,
            month = monthLabel,
            animFactor = factorMonth,
            onPrev = { monthlySummaryOffset-- },
            onNext = { monthlySummaryOffset++ }
        )

        Spacer(Modifier.height(30.dp))
    }
}

// -------------------------------------------------------------------------
// COMPOSANTS
// -------------------------------------------------------------------------

@Composable
fun MonthlySummaryCard(
    entries: List<JournalEntry>,
    subCategoryViewModel: SubCategoryViewModel,
    primaryColor: Color,
    month: String,
    animFactor: Float,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val totalSessions = entries.size
    val avgDuration = if (totalSessions > 0) entries.mapNotNull { it.sportDurationMinutes }.average() else 0.0
    val avgIntensity = if (totalSessions > 0) entries.mapNotNull { it.sportIntensity }.average() else 0.0

    val breakdown = remember { mutableStateMapOf<String, Int>() }
    LaunchedEffect(entries) {
        breakdown.clear()
        entries.groupBy { it.subCategoryId }.forEach { (id, list) ->
            if (id != null) {
                subCategoryViewModel.getSubCategoryName(id) { name ->
                    val n = name ?: "Autre"
                    breakdown[n] = (breakdown[n] ?: 0) + list.size
                }
            } else {
                breakdown["Autre"] = (breakdown["Autre"] ?: 0) + list.size
            }
        }
    }

    // AJOUT DU GLOW ET DE LA BORDURE ICI
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 15.dp, // Effet de lueur diffuse
                shape = RoundedCornerShape(24.dp),
                spotColor = primaryColor, // La lueur prend la couleur du thème (Orange)
                ambientColor = primaryColor
            ),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(2.dp, primaryColor.copy(alpha = 0.5f)) // Bordure colorée semi-transparente
    ) {
        Column(Modifier.padding(20.dp)) {
            // HEADER AVEC NAVIGATION
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrev, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Précédent", tint = TextGray)
                }

                Text(
                    text = "BILAN : $month",
                    color = TextGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                IconButton(onClick = onNext, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Suivant", tint = TextGray)
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // PARTIE GAUCHE
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(100.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = 10.dp.toPx()
                        val radius = size.minDimension / 2 - stroke / 2
                        drawCircle(color = Color.DarkGray.copy(alpha = 0.3f), radius = radius, style = Stroke(width = stroke, cap = StrokeCap.Round))
                        val targetProgress = (totalSessions / 30f).coerceIn(0f, 1f)
                        val animatedProgress = targetProgress * animFactor
                        if (animatedProgress > 0) {
                            drawArc(
                                color = primaryColor,
                                startAngle = -90f,
                                sweepAngle = 360f * animatedProgress,
                                useCenter = false,
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "$totalSessions", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Séances", color = TextGray, fontSize = 10.sp)
                    }
                }

                Spacer(Modifier.width(24.dp))

                // PARTIE DROITE
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    // ON APPLIQUE L'ANIMATION SUR TOUTE LA COLONNE ICI
                    modifier = Modifier.alpha(animFactor)
                ) {
                    // Ligne Timer
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timer, null, tint = primaryColor, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(text = "Moy. ${avgDuration.toInt()} min", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }

                    // Ligne Intensité
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bolt, null, tint = primaryColor, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(text = "Intensité ${String.format("%.1f", avgIntensity)}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }

                    // --- AJOUT : MOYENNE SÉANCES / SEMAINE ---
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // On réutilise une icône pertinente, ex: Refresh ou DateRange
                        Icon(Icons.Default.DateRange, null, tint = primaryColor, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))

                        // Calcul : Total séances / 4.33 (moyenne semaines/mois)
                        val weeklyAvg = if (totalSessions > 0) totalSessions / 4.33f else 0f

                        Text(
                            text = "${String.format("%.1f", weeklyAvg)} sé./sem",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Divider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))

                    if (breakdown.isEmpty()) {
                        Text("Aucun détail", color = TextGray, fontSize = 11.sp)
                    } else {
                        breakdown.entries.sortedByDescending { it.value }.take(3).forEach { (name, count) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(4.dp).background(TextGray, CircleShape))
                                Spacer(Modifier.width(8.dp))
                                Text(text = "$count $name", color = TextGray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DurationTimelineCard(
    curDuration: Float,
    prevDuration: Float,
    primaryColor: Color,
    ghostColor: Color,
    animFactorA: Float,
    animFactorB: Float
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(text = "DURÉE MOYENNE / SÉANCE", color = TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(12.dp))
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val maxScale = maxOf(90f, curDuration, prevDuration) * 1.1f
                val gridSteps = (maxScale / 30).toInt()
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 10.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                    alpha = 100
                }
                for (i in 0..gridSteps) {
                    val time = i * 30
                    val x = (time / maxScale) * width
                    drawLine(color = Color.DarkGray.copy(alpha = 0.3f), start = Offset(x, 0f), end = Offset(x, height - 20f), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                    if (time > 0) {
                        val label = if (time % 60 == 0) "${time/60}h" else "${time}m"
                        drawContext.canvas.nativeCanvas.drawText(label, x, height, textPaint)
                    }
                }
                val barHeight = 24.dp.toPx()
                val barSpacing = 20.dp.toPx()
                val startY = 10.dp.toPx()
                val widthA = (curDuration / maxScale) * width * animFactorA
                drawRoundRect(color = primaryColor, topLeft = Offset(0f, startY), size = Size(widthA, barHeight), cornerRadius = CornerRadius(4.dp.toPx()))
                if (curDuration > 0) {
                    drawIntoCanvas {
                        val paint = android.graphics.Paint().apply {
                            color = primaryColor.toArgb()
                            maskFilter = android.graphics.BlurMaskFilter(20f, android.graphics.BlurMaskFilter.Blur.NORMAL)
                            alpha = (80 * animFactorA).toInt()
                        }
                        it.nativeCanvas.drawRect(0f, startY, widthA, startY + barHeight, paint)
                    }
                }
                val labelA = "${curDuration.toInt()} min"
                val valPaintA = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 12.sp.toPx()
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    textAlign = android.graphics.Paint.Align.LEFT
                    alpha = (255 * animFactorB).toInt()
                }
                drawContext.canvas.nativeCanvas.drawText(labelA, widthA + 15f, startY + barHeight/1.5f, valPaintA)

                val yB = startY + barHeight + barSpacing
                val widthB = (prevDuration / maxScale) * width * animFactorB
                drawRoundRect(color = ghostColor, topLeft = Offset(0f, yB), size = Size(widthB, barHeight), cornerRadius = CornerRadius(4.dp.toPx()))
                val labelB = "${prevDuration.toInt()} min"
                val valPaintB = android.graphics.Paint().apply {
                    color = android.graphics.Color.LTGRAY
                    textSize = 12.sp.toPx()
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    textAlign = android.graphics.Paint.Align.LEFT
                    alpha = (255 * animFactorB).toInt()
                }
                drawContext.canvas.nativeCanvas.drawText(labelB, widthB + 15f, yB + barHeight/1.5f, valPaintB)
            }
        }
    }
}

@Composable
fun GaugeSquareCard(
    title: String,
    value: Float,
    maxValue: Float,
    label: String,
    color: Color,
    animFactor: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(130.dp).shadow(20.dp, RoundedCornerShape(16.dp), spotColor = color, ambientColor = color),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, color.copy(alpha = 0.8f))
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Box(
                modifier = Modifier.align(Alignment.TopCenter).background(color.copy(alpha = 0.2f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(text = title, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Canvas(
                modifier = Modifier.fillMaxWidth().height(90.dp).align(Alignment.Center).offset(y = -15.dp)
            ) {
                val strokeWidth = 8.dp.toPx()
                val radius = size.minDimension / 2
                val center = Offset(size.width / 2, size.height)
                drawArc(color = Color.DarkGray.copy(alpha = 0.3f), startAngle = 180f, sweepAngle = 180f, useCenter = false, topLeft = Offset(center.x - radius, center.y - radius), size = Size(radius * 2, radius * 2), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                val animatedValue = value * animFactor
                val progress = (animatedValue / maxValue).coerceIn(0f, 1f)
                drawArc(color = color, startAngle = 180f, sweepAngle = 180f * progress, useCenter = false, topLeft = Offset(center.x - radius, center.y - radius), size = Size(radius * 2, radius * 2), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
            }
            Column(modifier = Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = String.format("%.1f", value), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(text = label, color = TextGray, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun ComparisonSquareCard(
    title: String,
    value: String,
    label: String,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(110.dp).shadow(15.dp, RoundedCornerShape(16.dp), spotColor = borderColor, ambientColor = borderColor),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text(text = label, color = TextGray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier.background(borderColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(text = title, color = borderColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun StackedDistributionCard(
    currentEntries: List<JournalEntry>,
    ghostEntries: List<JournalEntry>,
    subCategoryViewModel: SubCategoryViewModel,
    primaryColor: Color,
    ghostColor: Color,
    animFactorA: Float,
    animFactorB: Float
) {
    val distribution = remember { mutableStateMapOf<String, Pair<Int, Int>>() }

    LaunchedEffect(currentEntries, ghostEntries) {
        distribution.clear()
        val allIds = (currentEntries.map { it.subCategoryId } + ghostEntries.map { it.subCategoryId }).toSet()
        allIds.forEach { id ->
            val countCurrent = currentEntries.count { it.subCategoryId == id }
            val countGhost = ghostEntries.count { it.subCategoryId == id }
            if (id != null) {
                subCategoryViewModel.getSubCategoryName(id) { name ->
                    val finalName = name ?: "Autre"
                    val existing = distribution[finalName] ?: (0 to 0)
                    distribution[finalName] = (existing.first + countCurrent) to (existing.second + countGhost)
                }
            } else {
                val finalName = "Autre"
                val existing = distribution[finalName] ?: (0 to 0)
                distribution[finalName] = (existing.first + countCurrent) to (existing.second + countGhost)
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("RÉPARTITION COMPARÉE", color = TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            if (distribution.isEmpty()) {
                Text("Aucune donnée", color = TextGray.copy(alpha = 0.5f), fontSize = 12.sp)
            } else {
                val maxVal = 7f
                val sortedData = distribution.entries.sortedByDescending { it.value.first }
                sortedData.forEach { (name, counts) ->
                    val (curr, ghost) = counts
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(4.dp))
                        // Actuel
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            val width = (curr / maxVal).coerceIn(0f, 1f) * animFactorA
                            if (width > 0) Box(modifier = Modifier.height(10.dp).fillMaxWidth(width).clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)).background(primaryColor))
                            else Box(Modifier.size(2.dp, 10.dp).background(TextGray.copy(alpha=0.3f)))
                            Spacer(Modifier.width(8.dp))
                            Text("$curr", color = primaryColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(1.dp))
                        // Ghost
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            val width = (ghost / maxVal).coerceIn(0f, 1f) * animFactorB
                            if (width > 0) Box(modifier = Modifier.height(10.dp).fillMaxWidth(width).clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)).background(ghostColor.copy(alpha=0.6f)))
                            else Box(Modifier.size(2.dp, 10.dp).background(TextGray.copy(alpha=0.3f)))
                            Spacer(Modifier.width(8.dp))
                            Text("$ghost", color = ghostColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Divider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
fun WeekSelectorRow(label: String, dateRange: String, color: Color, onPrev: () -> Unit, onNext: () -> Unit) {
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
            IconButton(onClick = onPrev, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.ChevronLeft, contentDescription = "Précédent", tint = TextGray) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = label, color = TextGray.copy(alpha = 0.7f), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(text = dateRange, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onNext, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.ChevronRight, contentDescription = "Suivant", tint = TextGray) }
        }
    }
}