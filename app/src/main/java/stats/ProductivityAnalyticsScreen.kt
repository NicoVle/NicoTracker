package com.example.nicotracker.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontStyle
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
import kotlinx.coroutines.launch
import java.util.Calendar

// COULEUR THÈME PRODUCTIVITÉ
val NeonPurple = Color(0xFFD500F9)
val GhostPurple = Color(0xFFB388FF)

@Composable
fun ProductivityAnalyticsScreen(
    journalEntryViewModel: JournalEntryViewModel,
    subCategoryViewModel: SubCategoryViewModel,
    onBack: () -> Unit
) {
    val allEntries by journalEntryViewModel.allEntries.collectAsState(initial = emptyList())

    // États Navigation
    var currentWeekOffset by remember { mutableIntStateOf(0) }
    var ghostWeekOffset by remember { mutableIntStateOf(-1) }

    // État MOIS (Bas de page)
    var monthlySummaryOffset by remember { mutableIntStateOf(0) }

    // Animations
    val animA = remember { Animatable(1f) }
    val animB = remember { Animatable(1f) }
    val animMonth = remember { Animatable(1f) }

    LaunchedEffect(currentWeekOffset) {
        animA.snapTo(0f)
        animA.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(ghostWeekOffset) {
        animB.snapTo(0f)
        animB.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(monthlySummaryOffset) {
        animMonth.snapTo(0f)
        animMonth.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }

    val factorA = animA.value
    val factorB = animB.value
    val factorMonth = animMonth.value

    // --- PREP DATA SEMAINE ---
    val (startCurrent, endCurrent) = getWeekRange(currentWeekOffset)
    val (startGhost, endGhost) = getWeekRange(ghostWeekOffset)

    val currentData = remember(allEntries, currentWeekOffset) {
        allEntries.filter { it.categoryName == "Action productive" && it.date.time in startCurrent..endCurrent }
    }
    val ghostData = remember(allEntries, ghostWeekOffset) {
        allEntries.filter { it.categoryName == "Action productive" && it.date.time in startGhost..endGhost }
    }

    // --- PREP DATA MOIS ---
    val currentMonthEntries = remember(allEntries, monthlySummaryOffset) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, monthlySummaryOffset)
        val targetMonth = cal.get(Calendar.MONTH)
        val targetYear = cal.get(Calendar.YEAR)
        allEntries.filter {
            val c = Calendar.getInstance(); c.time = it.date
            it.categoryName == "Action productive" && c.get(Calendar.MONTH) == targetMonth && c.get(Calendar.YEAR) == targetYear
        }
    }
    val monthLabel = remember(monthlySummaryOffset) {
        val cal = Calendar.getInstance(); cal.add(Calendar.MONTH, monthlySummaryOffset)
        val fmt = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())
        fmt.format(cal.time).uppercase()
    }

    // --- CALCULS INTELLIGENTS SEMAINE ---
    val curTotalMinutes = currentData.sumOf { it.productiveDurationMinutes ?: 0 }
    val prevTotalMinutes = ghostData.sumOf { it.productiveDurationMinutes ?: 0 }

    // Total Heures (C'est ce qu'on affiche en GROS maintenant)
    val curTotalHours = curTotalMinutes / 60f
    val prevTotalHours = prevTotalMinutes / 60f

    // Fonction jours écoulés
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

    // Moyenne Heures / Jour (C'est ce qu'on affiche en PETIT)
    val curAvgHours = if (elapsedDaysA > 0) curTotalHours / elapsedDaysA else 0f
    val prevAvgHours = prevTotalHours / elapsedDaysB

    // Échelle dynamique pour la jauge (Min 40h ou le max atteint)
    val maxGaugeValue = maxOf(curTotalHours, prevTotalHours, 40f) * 1.1f

    val scrollState = rememberScrollState()
    val vignetteBrush = Brush.radialGradient(
        colors = listOf(Color(0xFF2A0E36), DarkBackground),
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
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Text("ANALYSE PRODUCTIVITÉ", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        // SELECTEURS
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WeekSelectorRow("SEMAINE ANALYSÉE (A)", formatWeekLabel(startCurrent, endCurrent), NeonPurple, { currentWeekOffset-- }, { currentWeekOffset++ })
            WeekSelectorRow("COMPARÉE AVEC (B)", formatWeekLabel(startGhost, endGhost), GhostColor, { ghostWeekOffset-- }, { ghostWeekOffset++ })
        }

        Spacer(Modifier.height(4.dp))

        // 1. DUEL TOTAL HEURES (NOUVEAU COMPOSANT "Proposition 1")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProductivityGaugeCard(
                title = "SEMAINE A",
                totalHours = curTotalHours,
                avgHours = curAvgHours,
                maxValue = maxGaugeValue,
                color = NeonPurple,
                animFactor = factorA,
                modifier = Modifier.weight(1f)
            )
            ProductivityGaugeCard(
                title = "SEMAINE B",
                totalHours = prevTotalHours,
                avgHours = prevAvgHours,
                maxValue = maxGaugeValue,
                color = GhostColor,
                animFactor = factorB,
                modifier = Modifier.weight(1f)
            )
        }

        // 2. COURBE HEURES INTERACTIVE
        ProductivityLineChartCard(
            currentEntries = currentData,
            ghostEntries = ghostData,
            weekStartCurrent = startCurrent,
            weekStartGhost = startGhost,
            animFactorA = factorA,
            animFactorB = factorB,
            primaryColor = NeonPurple
        )

        // 3. FOCUS (Qualité)
        FocusDuelRow(
            currentEntries = currentData,
            ghostEntries = ghostData,
            animFactorA = factorA,
            animFactorB = factorB
        )

        Spacer(Modifier.height(0.dp))

        ProductivityDetailsCard(
            currentEntries = currentData,
            ghostEntries = ghostData,
            subCategoryViewModel = subCategoryViewModel,
            animFactorA = factorA,
            animFactorB = factorB
        )

        Spacer(Modifier.height(0.dp))

        // 4. BILAN MENSUEL
        ProductivityMonthlySummaryCard(
            entries = currentMonthEntries,
            subCategoryViewModel = subCategoryViewModel,
            month = monthLabel,
            animFactor = factorMonth,
            onPrev = { monthlySummaryOffset-- },
            onNext = { monthlySummaryOffset++ }
        )

        Spacer(Modifier.height(120.dp))
    }
}

// --------------------------------------------------------------------------------
// COMPOSANTS PRODUCTIVITÉ
// --------------------------------------------------------------------------------

// --- NOUVEAU COMPOSANT : Jauge Total avec Moyenne en dessous ---
@Composable
fun ProductivityGaugeCard(
    title: String,
    totalHours: Float, // Valeur Principale (Gros)
    avgHours: Float,   // Valeur Secondaire (Petit)
    maxValue: Float,
    color: Color,
    animFactor: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(130.dp)
            .shadow(20.dp, RoundedCornerShape(16.dp), spotColor = color, ambientColor = color),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, color.copy(alpha = 0.8f))
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            // Titre en haut
            Box(
                modifier = Modifier.align(Alignment.TopCenter).background(color.copy(alpha = 0.2f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(text = title, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }

            // Jauge Circulaire
            Canvas(
                modifier = Modifier.fillMaxWidth().height(115.dp).align(Alignment.Center).offset(y = -10.dp)
            ) {
                val strokeWidth = 8.dp.toPx()
                val radius = size.minDimension / 2
                val center = Offset(size.width / 2, size.height)

                // Fond Jauge
                drawArc(
                    color = Color.DarkGray.copy(alpha = 0.3f),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Jauge Animée (Basée sur le TOTAL)
                val animatedValue = totalHours * animFactor
                val progress = (animatedValue / maxValue).coerceIn(0f, 1f)

                drawArc(
                    color = color,
                    startAngle = 180f,
                    sweepAngle = 180f * progress,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Textes Centraux
            Column(
                modifier = Modifier
                    .align(Alignment.Center) // 1. On le centre par rapport à la carte
                    .offset(y = 30.dp),      // 2. On le descend de 10 pixels (ajuste ce chiffre si besoin)
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // TOTAL (Gros)
                Text(
                    text = "${String.format("%.1f", totalHours)}h",
                    color = Color.White,
                    fontSize = 20.sp, // 3. TAILLE RÉDUITE (avant c'était 24.sp)
                    fontWeight = FontWeight.Bold
                )
                // MOYENNE (Petit)
                Text(
                    text = "Moy. ${String.format("%.1f", avgHours)}h/j",
                    color = TextGray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun ProductivityLineChartCard(
    currentEntries: List<JournalEntry>,
    ghostEntries: List<JournalEntry>,
    weekStartCurrent: Long,
    weekStartGhost: Long,
    animFactorA: Float,
    animFactorB: Float,
    primaryColor: Color
) {
    fun mapToWeekDays(entries: List<JournalEntry>, startOfWeek: Long): List<Float> {
        val days = FloatArray(7) { 0f }
        entries.forEach { entry ->
            val diff = entry.date.time - startOfWeek
            val dayIndex = (diff / (1000 * 60 * 60 * 24)).toInt()
            if (dayIndex in 0..6) {
                days[dayIndex] += (entry.productiveDurationMinutes ?: 0) / 60f
            }
        }
        return days.toList()
    }

    val currentDays = remember(currentEntries) { mapToWeekDays(currentEntries, weekStartCurrent) }
    val ghostDays = remember(ghostEntries) { mapToWeekDays(ghostEntries, weekStartGhost) }

    val maxVal = maxOf(currentDays.maxOrNull() ?: 8f, ghostDays.maxOrNull() ?: 8f, 8f) * 1.1f
    val daysLabels = listOf("L", "M", "M", "J", "V", "S", "D")

    var selectedPoint by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    val todayIndex = remember(weekStartCurrent) {
        val now = System.currentTimeMillis()
        if (now < weekStartCurrent) -1
        else if (now > weekStartCurrent + 7 * 24 * 3600 * 1000) 7
        else ((now - weekStartCurrent) / (24 * 3600 * 1000)).toInt()
    }

    Card(
        modifier = Modifier.fillMaxWidth().height(260.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, primaryColor.copy(alpha = 0.6f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("RYTHME HEBDOMADAIRE (H)", color = TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                if (selectedPoint != null) {
                    val (index, isGhost) = selectedPoint!!
                    val value = if (isGhost) ghostDays[index] else currentDays[index]
                    val color = if (isGhost) GhostColor else primaryColor
                    val label = if (isGhost) "Passé" else "Actuel"
                    Text("$label : ${String.format("%.1f", value)}h", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(primaryColor, CircleShape)); Spacer(Modifier.width(4.dp))
                        Text("Actuel", color = TextGray, fontSize = 9.sp); Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(8.dp).background(GhostColor, CircleShape)); Spacer(Modifier.width(4.dp))
                        Text("Passé", color = TextGray, fontSize = 9.sp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier.fillMaxSize()
                    .onSizeChanged { canvasSize = it.toSize() }
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = { offset ->
                            val paddingStart = 35.dp.toPx(); val paddingEnd = 10.dp.toPx()
                            val graphWidth = size.width - paddingStart - paddingEnd
                            val xStep = graphWidth / 6f
                            val relativeX = offset.x - paddingStart
                            val index = (relativeX / xStep).let { kotlin.math.round(it).toInt() }

                            if (index in 0..6) {
                                val limitIndex = if (weekStartCurrent > System.currentTimeMillis() - 7*24*3600*1000) todayIndex else 7
                                val valA = if (index <= limitIndex) currentDays[index] else null
                                val valB = ghostDays[index]

                                if (valA == null) {
                                    selectedPoint = index to true
                                } else {
                                    val fullHeight = size.height - 20.dp.toPx()
                                    val yA = fullHeight - ((valA * animFactorA) / maxVal * fullHeight)
                                    val yB = fullHeight - ((valB * animFactorB) / maxVal * fullHeight)
                                    val distA = kotlin.math.abs(offset.y - yA)
                                    val distB = kotlin.math.abs(offset.y - yB)
                                    selectedPoint = index to (distB < distA)
                                }
                                tryAwaitRelease()
                                selectedPoint = null
                            }
                        })
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val fullWidth = size.width; val fullHeight = size.height - 20.dp.toPx()
                    val paddingStart = 35.dp.toPx(); val paddingEnd = 10.dp.toPx()
                    val graphWidth = fullWidth - paddingStart - paddingEnd
                    val xStep = graphWidth / 6f

                    listOf(0, 2, 4, 6, 8, 10, 12).forEach { step ->
                        val y = fullHeight - (step.toFloat() / maxVal * fullHeight)
                        if (y >= 0) {
                            drawLine(color = Color.White.copy(alpha = 0.1f), start = Offset(paddingStart, y), end = Offset(paddingStart + graphWidth, y), strokeWidth = 1.dp.toPx())
                            drawContext.canvas.nativeCanvas.drawText(step.toString(), paddingStart - 15f, y + 4f, android.graphics.Paint().apply { setColor(android.graphics.Color.LTGRAY); textSize = 9.sp.toPx(); textAlign = android.graphics.Paint.Align.RIGHT })
                        }
                    }
                    val goalY = fullHeight - (6f / maxVal * fullHeight)
                    drawLine(color = Color.White.copy(alpha = 0.5f), start = Offset(paddingStart, goalY), end = Offset(paddingStart + graphWidth, goalY), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))

                    fun drawSmoothLine(data: List<Float>, color: Color, isGhost: Boolean, currentAnimFactor: Float) {
                        val path = Path()
                        var firstPoint = true
                        val drawLimit = if (!isGhost && weekStartCurrent > System.currentTimeMillis() - 604800000L) todayIndex else 6

                        data.forEachIndexed { index, value ->
                            if (index <= drawLimit) {
                                val x = paddingStart + (index * xStep)
                                val y = fullHeight - ((value * currentAnimFactor) / maxVal * fullHeight)
                                if (firstPoint) { path.moveTo(x, y); firstPoint = false }
                                else path.lineTo(x, y)
                            }
                        }

                        if (!path.isEmpty) {
                            drawIntoCanvas {
                                val paint = android.graphics.Paint().apply {
                                    this.color = color.toArgb()
                                    style = android.graphics.Paint.Style.STROKE
                                    strokeWidth = if (isGhost) 4.dp.toPx() else 3.dp.toPx()
                                    strokeJoin = android.graphics.Paint.Join.ROUND
                                    strokeCap = android.graphics.Paint.Cap.ROUND
                                    isAntiAlias = true
                                    pathEffect = android.graphics.CornerPathEffect(30f)
                                    if (isGhost) alpha = 100 else maskFilter = android.graphics.BlurMaskFilter(3f, android.graphics.BlurMaskFilter.Blur.NORMAL)
                                }
                                it.nativeCanvas.drawPath(path.asAndroidPath(), paint)
                            }
                            if (!isGhost) drawPath(path = path, color = color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.cornerPathEffect(30f)))
                        }

                        data.forEachIndexed { index, value ->
                            if (index <= drawLimit) {
                                val isThisPointSelected = selectedPoint != null && selectedPoint!!.first == index && selectedPoint!!.second == isGhost
                                if (!isThisPointSelected) {
                                    val x = paddingStart + (index * xStep)
                                    val y = fullHeight - ((value * currentAnimFactor) / maxVal * fullHeight)
                                    drawCircle(color = CardBackgroundEmpty, radius = 6.dp.toPx(), center = Offset(x, y))
                                    drawCircle(color = color, radius = 4.dp.toPx(), center = Offset(x, y))
                                }
                            }
                        }
                    }

                    if (ghostDays.any { it > 0 }) drawSmoothLine(ghostDays, GhostColor, true, animFactorB)
                    drawSmoothLine(currentDays, primaryColor, false, animFactorA)

                    if (selectedPoint != null) {
                        val (index, isGhostSelection) = selectedPoint!!
                        val value = if (isGhostSelection) ghostDays[index] else currentDays[index]
                        val color = if (isGhostSelection) GhostColor else primaryColor
                        val animFactor = if (isGhostSelection) animFactorB else animFactorA

                        val x = paddingStart + (index * xStep)
                        val y = fullHeight - ((value * animFactor) / maxVal * fullHeight)

                        drawLine(color = Color.White.copy(alpha = 0.5f), start = Offset(x, y), end = Offset(x, fullHeight), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f))
                        drawCircle(color = color, radius = 8.dp.toPx(), center = Offset(x, y))
                        drawCircle(color = Color.White, radius = 5.dp.toPx(), center = Offset(x, y))

                        val label = String.format("%.1f h", value)
                        val paintTooltip = android.graphics.Paint().apply { setColor(android.graphics.Color.WHITE); textSize = 12.sp.toPx(); typeface = android.graphics.Typeface.DEFAULT_BOLD; textAlign = android.graphics.Paint.Align.CENTER }
                        val textBounds = android.graphics.Rect(); paintTooltip.getTextBounds(label, 0, label.length, textBounds)
                        val tooltipW = textBounds.width() + 20f; val tooltipH = textBounds.height() + 15f; val tooltipY = y - 25.dp.toPx()

                        drawRoundRect(color = Color(0xFF151515), topLeft = Offset(x - tooltipW / 2, tooltipY - tooltipH), size = Size(tooltipW, tooltipH), cornerRadius = CornerRadius(8f), style = Fill)
                        drawRoundRect(color = color, topLeft = Offset(x - tooltipW / 2, tooltipY - tooltipH), size = Size(tooltipW, tooltipH), cornerRadius = CornerRadius(8f), style = Stroke(width = 1.dp.toPx()))
                        drawContext.canvas.nativeCanvas.drawText(label, x, tooltipY - tooltipH / 2 + textBounds.height() / 3, paintTooltip)
                    }

                    daysLabels.forEachIndexed { index, label ->
                        val dayLabelPaint = android.graphics.Paint().apply { setColor(android.graphics.Color.GRAY); textSize = 10.sp.toPx(); textAlign = android.graphics.Paint.Align.CENTER; isFakeBoldText = true }
                        if (selectedPoint?.first == index) dayLabelPaint.color = android.graphics.Color.WHITE
                        val x = paddingStart + (index * xStep)
                        drawContext.canvas.nativeCanvas.drawText(label, x, size.height, dayLabelPaint)
                    }
                }
            }
        }
    }
}

@Composable
fun FocusDuelRow(
    currentEntries: List<JournalEntry>,
    ghostEntries: List<JournalEntry>,
    animFactorA: Float,
    animFactorB: Float
) {
    // Calcul des stats pour la Semaine A
    val (flowA, neutralA, struggleA) = calculateQualityDistribution(currentEntries)
    // Calcul des stats pour la Semaine B
    val (flowB, neutralB, struggleB) = calculateQualityDistribution(ghostEntries)

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        QualityStatCard(
            title = "QUALITÉ A",
            flowPercent = flowA,
            neutralPercent = neutralA,
            strugglePercent = struggleA,
            mainColor = NeonPurple, // Couleur du thème Productivité
            animFactor = animFactorA,
            modifier = Modifier.weight(1f)
        )
        QualityStatCard(
            title = "QUALITÉ B",
            flowPercent = flowB,
            neutralPercent = neutralB,
            strugglePercent = struggleB,
            mainColor = GhostColor,
            animFactor = animFactorB,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun QualityStatCard(
    title: String,
    flowPercent: Float,
    neutralPercent: Float,
    strugglePercent: Float,
    mainColor: Color,
    animFactor: Float,
    modifier: Modifier = Modifier
) {
    val displayFlow = (flowPercent * 100).toInt()
    val cardColor = if (mainColor == GhostColor) GhostColor else Color(0xFF00E5FF) // NeonCyan hardcodé si pas dispo

    Card(
        modifier = modifier
            .height(130.dp)
            .shadow(15.dp, RoundedCornerShape(16.dp), spotColor = cardColor, ambientColor = cardColor),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, mainColor.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // En-tête
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, color = TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Default.Bolt, null, tint = cardColor, modifier = Modifier.size(16.dp))
            }

            // Gros Chiffre (% FLOW)
            Column {
                Text(
                    text = "$displayFlow%",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "TAUX DE FLOW",
                    color = cardColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            // Barre de Répartition (Stacked Bar)
            // CORRECTION ICI : On anime la largeur du conteneur, pas les poids internes
            Box(
                modifier = Modifier
                    .fillMaxWidth(animFactor) // <--- L'ANIMATION EST DÉPLACÉE ICI
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.DarkGray.copy(alpha = 0.3f))
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // 1. FLOW (Cyan)
                    if (flowPercent > 0.001f) {
                        Box(
                            modifier = Modifier
                                .weight(flowPercent) // <--- Plus de multiplication ici
                                .fillMaxHeight()
                                .background(Color(0xFF00E5FF)) // NeonCyan
                        )
                    }
                    // 2. NEUTRE (Gris)
                    if (neutralPercent > 0.001f) {
                        Box(
                            modifier = Modifier
                                .weight(neutralPercent)
                                .fillMaxHeight()
                                .background(Color.Gray)
                        )
                    }
                    // 3. LUTTE (Rouge)
                    if (strugglePercent > 0.001f) {
                        Box(
                            modifier = Modifier
                                .weight(strugglePercent)
                                .fillMaxHeight()
                                .background(Color(0xFFFF1744)) // NeonRed
                        )
                    }
                }
            }
        }
    }
}
// Fonction d'aide pour calculer les pourcentages
fun calculateQualityDistribution(entries: List<JournalEntry>): Triple<Float, Float, Float> {
    val validEntries = entries.filter { it.productiveFocus != null }
    if (validEntries.isEmpty()) return Triple(0f, 0f, 0f)

    val total = validEntries.size.toFloat()
    val flowCount = validEntries.count { getFocusState(it.productiveFocus) == 3 }
    val neutralCount = validEntries.count { getFocusState(it.productiveFocus) == 2 }
    val struggleCount = validEntries.count { getFocusState(it.productiveFocus) == 1 }

    return Triple(flowCount / total, neutralCount / total, struggleCount / total)
}
@Composable
fun VerticalFocusGaugeCard(
    title: String,
    score: Float,
    baseColor: Color,
    isGhost: Boolean,
    animFactor: Float,
    modifier: Modifier
) {
    val finalColor = if (isGhost) baseColor else when {
        score >= 8f -> NeonPurple
        score >= 5f -> Color(0xFF00E5FF)
        else -> Color(0xFFFF1744)
    }
    val shadowElevation = if (isGhost) 0.dp else 15.dp

    Card(
        modifier = modifier.height(160.dp).shadow(shadowElevation, RoundedCornerShape(16.dp), spotColor = finalColor, ambientColor = finalColor),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (isGhost) baseColor.copy(alpha = 0.3f) else finalColor.copy(alpha = 0.5f))
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Text(title, color = TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Column {
                    Text(String.format("%.1f", score), color = if (isGhost) TextGray else Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("/ 10", color = TextGray.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
            Box(
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier.width(24.dp).fillMaxHeight().background(Color.Black.copy(alpha = 0.3f), CircleShape).border(1.dp, TextGray.copy(alpha = 0.1f), CircleShape).clip(CircleShape)
            ) {
                val fillRatio = (score / 10f).coerceIn(0f, 1f) * animFactor
                Box(Modifier.fillMaxWidth().fillMaxHeight(fillRatio).background(Brush.verticalGradient(listOf(finalColor, finalColor.copy(alpha = 0.5f)))))
            }
        }
    }
}

@Composable
fun ProductivityMonthlySummaryCard(
    entries: List<JournalEntry>,
    subCategoryViewModel: SubCategoryViewModel,
    month: String,
    animFactor: Float,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val totalMinutes = entries.sumOf { it.productiveDurationMinutes ?: 0 }
    val totalHours = totalMinutes / 60f

    // On récupère le scope pour pouvoir agir sur l'UI depuis les callbacks
    val scope = rememberCoroutineScope()

    val validDaysCount = entries.count { (it.productiveDurationMinutes ?: 0) > 0 }
    val dailyAvg = if (validDaysCount > 0) totalHours / validDaysCount else 0f

    val validFocus = entries.mapNotNull { it.productiveFocus }

    val breakdown = remember { mutableStateMapOf<String, Float>() }

    LaunchedEffect(entries) {
        breakdown.clear()
        entries.groupBy { it.subCategoryId }.forEach { (id, list) ->
            val hours = list.sumOf { it.productiveDurationMinutes ?: 0 } / 60f
            if (id != null) {
                subCategoryViewModel.getSubCategoryName(id) { name ->
                    // IMPORTANT : On revient sur le Thread Principal pour modifier l'état
                    scope.launch {
                        val n = name ?: "Autre"
                        breakdown[n] = (breakdown[n] ?: 0f) + hours
                    }
                }
            } else {
                breakdown["Autre"] = (breakdown["Autre"] ?: 0f) + hours
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(15.dp, RoundedCornerShape(24.dp), spotColor = NeonPurple, ambientColor = NeonPurple),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(2.dp, NeonPurple.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrev, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Précédent", tint = TextGray)
                }
                Text(text = "BILAN : $month", color = TextGray, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                IconButton(onClick = onNext, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Suivant", tint = TextGray)
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(100.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = 10.dp.toPx()
                        val radius = size.minDimension / 2 - stroke / 2
                        drawCircle(color = Color.DarkGray.copy(alpha = 0.3f), radius = radius, style = Stroke(width = stroke, cap = StrokeCap.Round))
                        val targetProgress = (totalHours / 100f).coerceIn(0f, 1f)
                        val animatedProgress = targetProgress * animFactor
                        if (animatedProgress > 0) {
                            drawArc(
                                color = NeonPurple,
                                startAngle = -90f,
                                sweepAngle = 360f * animatedProgress,
                                useCenter = false,
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "${totalHours.toInt()}", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Heures", color = TextGray, fontSize = 10.sp)
                    }
                }

                Spacer(Modifier.width(24.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.alpha(animFactor)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timer, null, tint = NeonPurple, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(text = "Moy. ${String.format("%.1f", dailyAvg)}h / jour", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }

                    // Calcul du % Flow mensuel
                    val flowCountMonth = validFocus.count { getFocusState(it) == 3 }
                    val totalFocusCount = validFocus.size.toFloat()
                    val flowRateMonth = if (totalFocusCount > 0) (flowCountMonth / totalFocusCount * 100).toInt() else 0

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bolt, null, tint = NeonPurple, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        // Affichage du Taux de Flow au lieu de la moyenne
                        Text(text = "Taux Flow : $flowRateMonth%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }

                    Divider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))

                    if (breakdown.isEmpty()) {
                        Text("Aucun détail", color = TextGray, fontSize = 11.sp)
                    } else {
                        breakdown.entries.sortedByDescending { it.value }.take(3).forEach { (name, hours) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(4.dp).background(TextGray, CircleShape))
                                Spacer(Modifier.width(8.dp))
                                Text(text = "${String.format("%.1f", hours)}h $name", color = TextGray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProductivityDetailsCard(
    currentEntries: List<JournalEntry>,
    ghostEntries: List<JournalEntry>,
    subCategoryViewModel: SubCategoryViewModel,
    animFactorA: Float,
    animFactorB: Float
) {
    // Maps pour stocker les sommes par nom d'activité (String -> Heures)
    val breakdownA = remember { mutableStateMapOf<String, Float>() }
    val breakdownB = remember { mutableStateMapOf<String, Float>() }

    // Liste combinée des activités pour l'affichage
    val allActivities = remember { mutableStateListOf<String>() }

    // On récupère le scope pour sécuriser les modifications d'état
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentEntries, ghostEntries) {
        breakdownA.clear()
        breakdownB.clear()
        allActivities.clear()

        // Fonction suspendue locale INTELLIGENTE
        suspend fun fillMap(entries: List<JournalEntry>, map: MutableMap<String, Float>) {
            entries.groupBy { it.subCategoryId }.forEach { (id, list) ->
                val hours = list.sumOf { it.productiveDurationMinutes ?: 0 } / 60f

                if (hours > 0) {
                    if (id != null) {
                        subCategoryViewModel.getSubCategoryName(id) { name ->
                            scope.launch {
                                val categoryName = name ?: "Autre"

                                // 1. On ajoute le temps à la catégorie principale (ex: "Copywriting")
                                map[categoryName] = (map[categoryName] ?: 0f) + hours
                                if (!allActivities.contains(categoryName)) allActivities.add(categoryName)

                                // --- MAGIE : EXTRACTION DES TAGS (Lecture / Vidéo) ---
                                // On regarde si dans ce groupe, il y a du temps caché sous forme de tags

                                // A. Calcul du temps "Lecture" caché
                                val readingMinutes = list
                                    .filter { it.tags?.contains("Lecture", ignoreCase = true) == true }
                                    .sumOf { it.productiveDurationMinutes ?: 0 }

                                if (readingMinutes > 0 && !categoryName.equals("Lecture", ignoreCase = true)) {
                                    val readingHours = readingMinutes / 60f
                                    val label = "Lecture" // Le nom commun pour regrouper
                                    map[label] = (map[label] ?: 0f) + readingHours
                                    if (!allActivities.contains(label)) allActivities.add(label)
                                }

                                // B. Calcul du temps "Vidéo" caché (Bonus si tu veux tracker ça aussi)
                                val videoMinutes = list
                                    .filter { it.tags?.contains("Vidéo", ignoreCase = true) == true }
                                    .sumOf { it.productiveDurationMinutes ?: 0 }

                                if (videoMinutes > 0 && !categoryName.equals("Vidéo", ignoreCase = true)) {
                                    val videoHours = videoMinutes / 60f
                                    val label = "Vidéo"
                                    map[label] = (map[label] ?: 0f) + videoHours
                                    if (!allActivities.contains(label)) allActivities.add(label)
                                }
                                // -----------------------------------------------------
                            }
                        }
                    } else {
                        map["Autre"] = (map["Autre"] ?: 0f) + hours
                        if (!allActivities.contains("Autre")) allActivities.add("Autre")
                    }
                }
            }
        }
        fillMap(currentEntries, breakdownA)
        fillMap(ghostEntries, breakdownB)
    }

    // On trie les activités par le temps le plus élevé (soit A soit B)
    val sortedActivities = remember(breakdownA.toMap(), breakdownB.toMap(), allActivities.toList()) {
        allActivities.sortedByDescending { name ->
            maxOf(breakdownA[name] ?: 0f, breakdownB[name] ?: 0f)
        }
    }

    // Calcul du max global pour l'échelle des barres
    val maxDuration = remember(breakdownA.toMap(), breakdownB.toMap()) {
        val maxA = breakdownA.values.maxOrNull() ?: 0f
        val maxB = breakdownB.values.maxOrNull() ?: 0f
        maxOf(maxA, maxB, 1f) // évite division par 0
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            // --- CHANGEMENT 1 : Ombre Néon ---
            .shadow(15.dp, RoundedCornerShape(16.dp), spotColor = NeonPurple, ambientColor = NeonPurple),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty),
        shape = RoundedCornerShape(16.dp),
        // --- CHANGEMENT 2 : Bordure Néon ---
        border = BorderStroke(2.dp, NeonPurple.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "DÉTAILS ACTIVITÉS (HEURES)",
                color = TextGray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (sortedActivities.isEmpty()) {
                Text(
                    text = "Aucune donnée sur la période",
                    color = TextGray,
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                sortedActivities.forEach { name ->
                    val valA = breakdownA[name] ?: 0f
                    val valB = breakdownB[name] ?: 0f

                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        // Nom de l'activité
                        Text(text = name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))

                        // Barre Semaine A (Actuelle - Reste NeonPurple)
                        if (valA > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.DarkGray.copy(alpha = 0.3f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth((valA / maxDuration) * animFactorA)
                                            .fillMaxHeight()
                                            .background(NeonPurple)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = String.format("%.1f", valA),
                                    color = NeonPurple,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(30.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        // Barre Semaine B (Passé - Devient GRIS)
                        if (valB > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color.DarkGray.copy(alpha = 0.2f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth((valB / maxDuration) * animFactorB)
                                            .fillMaxHeight()
                                            // Utilisation de TextGray avec transparence
                                            .background(TextGray.copy(alpha = 0.5f))
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = String.format("%.1f", valB),
                                    // Utilisation de TextGray solide
                                    color = TextGray,
                                    fontSize = 10.sp,
                                    modifier = Modifier.width(30.dp)
                                )
                            }
                        }
                    }
                    Divider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

// Traduit les scores (Anciens 1-10 et Nouveaux 1-3) en États Standardisés
// 3 = Flow, 2 = Neutre, 1 = Lutte
fun getFocusState(score: Int?): Int {
    if (score == null) return 0
    return when {
        score >= 7 -> 3 // Ancien "Fort" -> Flow
        score == 3 -> 3 // Nouveau Flow
        score == 2 -> 2 // Nouveau Neutre
        score == 1 -> 1 // Nouveau Lutte
        score <= 4 -> 1 // Ancien "Faible" -> Lutte
        else -> 2       // Reste -> Neutre
    }
}