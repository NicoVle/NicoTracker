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
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.example.nicotracker.data.SubCategoryViewModel
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

// --- COULEURS ---
private val ScreenNeonPink = Color(0xFFFF007F) // Rose Bonbon (Actuel)
private val ScreenGhostGrey = Color(0xFF757575) // Gris (Comparaison/Passé)

@Composable
fun ScreenTimeAnalyticsScreen(
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
    val (startCurrent, endCurrent) = getScreenWeekRange(currentWeekOffset)
    val (startGhost, endGhost) = getScreenWeekRange(ghostWeekOffset)

    // FILTRE SUR "Temps d'écran"
    val currentData = remember(allEntries, currentWeekOffset) {
        allEntries.filter { it.categoryName == "Temps d'écran" && it.date.time in startCurrent..endCurrent }
    }
    val ghostData = remember(allEntries, ghostWeekOffset) {
        allEntries.filter { it.categoryName == "Temps d'écran" && it.date.time in startGhost..endGhost }
    }

    // --- PREP DATA MOIS ---
    val currentMonthEntries = remember(allEntries, monthlySummaryOffset) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, monthlySummaryOffset)
        val targetMonth = cal.get(Calendar.MONTH)
        val targetYear = cal.get(Calendar.YEAR)
        allEntries.filter {
            val c = Calendar.getInstance(); c.time = it.date
            it.categoryName == "Temps d'écran" && c.get(Calendar.MONTH) == targetMonth && c.get(Calendar.YEAR) == targetYear
        }
    }
    val monthLabel = remember(monthlySummaryOffset) {
        val cal = Calendar.getInstance(); cal.add(Calendar.MONTH, monthlySummaryOffset)
        val fmt = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())
        fmt.format(cal.time).uppercase()
    }

    // --- CALCULS INTELLIGENTS SEMAINE ---
    val curTotalMinutes = currentData.sumOf { it.screenDurationMinutes ?: 0 }
    val prevTotalMinutes = ghostData.sumOf { it.screenDurationMinutes ?: 0 }

    val curTotalHours = curTotalMinutes / 60f
    val prevTotalHours = prevTotalMinutes / 60f

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

    val curAvgHours = if (elapsedDaysA > 0) curTotalHours / elapsedDaysA else 0f
    val prevAvgHours = prevTotalHours / elapsedDaysB

    val maxGaugeValue = maxOf(curTotalHours, prevTotalHours, 20f) * 1.1f // Base 20h pour l'écran

    val scrollState = rememberScrollState()

    // FOND : Rose nuit sombre
    val vignetteBrush = Brush.radialGradient(
        colors = listOf(Color(0xFF330019), DarkBackground),
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
            Text("ANALYSE ÉCRAN", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        // SELECTEURS : A en Rose, B en Gris
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ScreenWeekSelectorRow("SEMAINE ANALYSÉE (A)", formatScreenWeekLabel(startCurrent, endCurrent), ScreenNeonPink, { currentWeekOffset-- }, { currentWeekOffset++ })
            ScreenWeekSelectorRow("COMPARÉE AVEC (B)", formatScreenWeekLabel(startGhost, endGhost), ScreenGhostGrey, { ghostWeekOffset-- }, { ghostWeekOffset++ })
        }

        Spacer(Modifier.height(4.dp))

        // 1. DUEL TOTAL HEURES : A en Rose, B en Gris
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScreenGaugeCard(
                title = "SEMAINE A",
                totalHours = curTotalHours,
                avgHours = curAvgHours,
                maxValue = maxGaugeValue,
                color = ScreenNeonPink,
                animFactor = factorA,
                modifier = Modifier.weight(1f)
            )
            ScreenGaugeCard(
                title = "SEMAINE B",
                totalHours = prevTotalHours,
                avgHours = prevAvgHours,
                maxValue = maxGaugeValue,
                color = ScreenGhostGrey,
                animFactor = factorB,
                modifier = Modifier.weight(1f)
            )
        }

        // 2. COURBE HEURES INTERACTIVE
        ScreenLineChartCard(
            currentEntries = currentData,
            ghostEntries = ghostData,
            weekStartCurrent = startCurrent,
            weekStartGhost = startGhost,
            animFactorA = factorA,
            animFactorB = factorB,
            primaryColor = ScreenNeonPink
        )

        Spacer(Modifier.height(0.dp))

        // 2.5 COMPARATIF SOUS-CATÉGORIES (FANTÔME)
        ScreenSubCategoryComparisonCard(
            currentEntries = currentData,
            ghostEntries = ghostData,
            subCategoryViewModel = subCategoryViewModel,
            animFactor = factorA
        )

        Spacer(Modifier.height(0.dp))

        // 3. BILAN MENSUEL
        ScreenMonthlySummaryCard(
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
// COMPOSANTS
// --------------------------------------------------------------------------------

data class SubCatComparisonData(
    val name: String,
    val hoursA: Float,
    val hoursB: Float
)

@Composable
fun ScreenSubCategoryComparisonCard(
    currentEntries: List<JournalEntry>,
    ghostEntries: List<JournalEntry>,
    subCategoryViewModel: SubCategoryViewModel,
    animFactor: Float
) {
    val comparisonList = remember { mutableStateListOf<SubCatComparisonData>() }

    LaunchedEffect(currentEntries, ghostEntries) {
        comparisonList.clear()
        val mapA = currentEntries.groupBy { it.subCategoryId }
            .mapValues { it.value.sumOf { entry -> entry.screenDurationMinutes ?: 0 } / 60f }
        val mapB = ghostEntries.groupBy { it.subCategoryId }
            .mapValues { it.value.sumOf { entry -> entry.screenDurationMinutes ?: 0 } / 60f }

        val allIds = (mapA.keys + mapB.keys).filterNotNull().toSet()
        val otherA = mapA[null] ?: 0f
        val otherB = mapB[null] ?: 0f

        allIds.forEach { id ->
            subCategoryViewModel.getSubCategoryName(id) { name ->
                val catName = name ?: "Inconnu"
                val hA = mapA[id] ?: 0f
                val hB = mapB[id] ?: 0f
                if (hA > 0 || hB > 0) {
                    comparisonList.add(SubCatComparisonData(catName, hA, hB))
                }
            }
        }
        if (otherA > 0 || otherB > 0) {
            comparisonList.add(SubCatComparisonData("Autre", otherA, otherB))
        }
    }

    if (comparisonList.isNotEmpty()) {
        val sortedList = comparisonList.sortedByDescending { max(it.hoursA, it.hoursB) }
        val maxGlobal = sortedList.maxOfOrNull { max(it.hoursA, it.hoursB) } ?: 1f
        val scaleMax = if (maxGlobal == 0f) 1f else maxGlobal

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(16.dp), spotColor = ScreenNeonPink, ambientColor = ScreenNeonPink),
            colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty),
            shape = RoundedCornerShape(16.dp),
            // MODIFICATION 1 : BORDURE ROSE
            border = BorderStroke(2.dp, ScreenNeonPink.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "DÉTAIL PAR ACTIVITÉ",
                    color = TextGray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                sortedList.forEach { item ->
                    ComparisonRowGhostOverlay(
                        item = item,
                        maxScale = scaleMax,
                        animFactor = animFactor
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
fun ComparisonRowGhostOverlay(
    item: SubCatComparisonData,
    maxScale: Float,
    animFactor: Float
) {
    val fillRatioA = (item.hoursA / maxScale).coerceIn(0f, 1f) * animFactor
    val fillRatioB = (item.hoursB / maxScale).coerceIn(0f, 1f) * animFactor

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = item.name,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${String.format("%.1f", item.hoursA)}h",
                    color = ScreenNeonPink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "vs",
                    color = TextGray,
                    fontSize = 10.sp
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${String.format("%.1f", item.hoursB)}h",
                    color = ScreenGhostGrey,
                    fontSize = 11.sp
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.DarkGray.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fillRatioB)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, ScreenGhostGrey.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(fillRatioA)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(ScreenNeonPink)
            )
        }
    }
}

@Composable
fun ScreenGaugeCard(
    title: String,
    totalHours: Float,
    avgHours: Float,
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
            Box(
                modifier = Modifier.align(Alignment.TopCenter).background(color.copy(alpha = 0.2f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(text = title, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }

            Canvas(
                modifier = Modifier.fillMaxWidth().height(115.dp).align(Alignment.Center).offset(y = -10.dp)
            ) {
                val strokeWidth = 8.dp.toPx()
                val radius = size.minDimension / 2
                val center = Offset(size.width / 2, size.height)

                drawArc(
                    color = Color.DarkGray.copy(alpha = 0.3f),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

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

            Column(
                modifier = Modifier.align(Alignment.Center).offset(y = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${String.format("%.1f", totalHours)}h",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
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
fun ScreenLineChartCard(
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
                days[dayIndex] += (entry.screenDurationMinutes ?: 0) / 60f
            }
        }
        return days.toList()
    }

    val currentDays = remember(currentEntries) { mapToWeekDays(currentEntries, weekStartCurrent) }
    val ghostDays = remember(ghostEntries) { mapToWeekDays(ghostEntries, weekStartGhost) }

    val maxVal = maxOf(currentDays.maxOrNull() ?: 4f, ghostDays.maxOrNull() ?: 4f, 4f) * 1.1f
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
                    val color = if (isGhost) ScreenGhostGrey else primaryColor
                    val label = if (isGhost) "Passé" else "Actuel"
                    Text("$label : ${String.format("%.1f", value)}h", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(primaryColor, CircleShape)); Spacer(Modifier.width(4.dp))
                        Text("Actuel", color = TextGray, fontSize = 9.sp); Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(8.dp).background(ScreenGhostGrey, CircleShape)); Spacer(Modifier.width(4.dp))
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
                            // MODIFICATION 2 : PADDING RÉDUIT (24.dp)
                            val paddingStart = 24.dp.toPx()
                            val paddingEnd = 10.dp.toPx()
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
                    // MODIFICATION 2 (SUITE) : PADDING RÉDUIT DANS LE DESSIN
                    val paddingStart = 24.dp.toPx()
                    val paddingEnd = 10.dp.toPx()
                    val graphWidth = fullWidth - paddingStart - paddingEnd
                    val xStep = graphWidth / 6f

                    // Grille
                    listOf(0, 2, 4, 6, 8, 10, 12).forEach { step ->
                        val y = fullHeight - (step.toFloat() / maxVal * fullHeight)
                        if (y >= 0) {
                            drawLine(color = Color.White.copy(alpha = 0.1f), start = Offset(paddingStart, y), end = Offset(paddingStart + graphWidth, y), strokeWidth = 1.dp.toPx())
                            drawContext.canvas.nativeCanvas.drawText(step.toString(), paddingStart - 15f, y + 4f, android.graphics.Paint().apply { setColor(android.graphics.Color.LTGRAY); textSize = 9.sp.toPx(); textAlign = android.graphics.Paint.Align.RIGHT })
                        }
                    }

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

                    if (ghostDays.any { it > 0 }) drawSmoothLine(ghostDays, ScreenGhostGrey, true, animFactorB)
                    drawSmoothLine(currentDays, primaryColor, false, animFactorA)

                    // Point sélectionné
                    if (selectedPoint != null) {
                        val (index, isGhostSelection) = selectedPoint!!
                        val value = if (isGhostSelection) ghostDays[index] else currentDays[index]
                        val color = if (isGhostSelection) ScreenGhostGrey else primaryColor
                        val animFactor = if (isGhostSelection) animFactorB else animFactorA
                        val x = paddingStart + (index * xStep)
                        val y = fullHeight - ((value * animFactor) / maxVal * fullHeight)

                        drawLine(color = Color.White.copy(alpha = 0.5f), start = Offset(x, y), end = Offset(x, fullHeight), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f))
                        drawCircle(color = color, radius = 8.dp.toPx(), center = Offset(x, y))

                        // Tooltip simplifié
                        val label = String.format("%.1f h", value)
                        val paintTooltip = android.graphics.Paint().apply { setColor(android.graphics.Color.WHITE); textSize = 12.sp.toPx(); typeface = android.graphics.Typeface.DEFAULT_BOLD; textAlign = android.graphics.Paint.Align.CENTER }
                        drawContext.canvas.nativeCanvas.drawText(label, x, y - 25.dp.toPx(), paintTooltip)
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
fun ScreenMonthlySummaryCard(
    entries: List<JournalEntry>,
    subCategoryViewModel: SubCategoryViewModel,
    month: String,
    animFactor: Float,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    // Calcul sur screenDurationMinutes
    val totalMinutes = entries.sumOf { it.screenDurationMinutes ?: 0 }
    val totalHours = totalMinutes / 60f

    val validDaysCount = entries.count { (it.screenDurationMinutes ?: 0) > 0 }
    val dailyAvg = if (validDaysCount > 0) totalHours / validDaysCount else 0f

    // Répartition par sous-catégories (Instagram, TikTok, etc.)
    val breakdown = remember { mutableStateMapOf<String, Float>() }
    LaunchedEffect(entries) {
        breakdown.clear()
        entries.groupBy { it.subCategoryId }.forEach { (id, list) ->
            val hours = list.sumOf { it.screenDurationMinutes ?: 0 } / 60f
            if (id != null) {
                subCategoryViewModel.getSubCategoryName(id) { name ->
                    val n = name ?: "Autre"
                    breakdown[n] = (breakdown[n] ?: 0f) + hours
                }
            } else {
                breakdown["Autre"] = (breakdown["Autre"] ?: 0f) + hours
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(15.dp, RoundedCornerShape(24.dp), spotColor = ScreenNeonPink, ambientColor = ScreenNeonPink),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(2.dp, ScreenNeonPink.copy(alpha = 0.5f))
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
                // ROND PRINCIPAL (HEURES TOTALES)
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
                                color = ScreenNeonPink,
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

                // DÉTAILS À DROITE
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.alpha(animFactor)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timer, null, tint = ScreenNeonPink, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(text = "Moy. ${String.format("%.1f", dailyAvg)}h / jour", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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

// --------------------------------------------------------------------------------
// FONCTIONS UTILITAIRES PRIVÉES (POUR ÉVITER LES CONFLITS AVEC LES AUTRES ÉCRANS)
// --------------------------------------------------------------------------------

@Composable
private fun ScreenWeekSelectorRow(
    label: String,
    dateRange: String,
    color: Color,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackgroundEmpty, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(text = label, color = TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(text = dateRange, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
        Row {
            IconButton(onClick = onPrev, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.ChevronLeft, null, tint = TextGray)
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onNext, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.ChevronRight, null, tint = TextGray)
            }
        }
    }
}

private fun getScreenWeekRange(offset: Int): Pair<Long, Long> {
    val cal = Calendar.getInstance()
    cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)

    cal.add(Calendar.WEEK_OF_YEAR, offset)

    val start = cal.timeInMillis
    cal.add(Calendar.DAY_OF_YEAR, 6)
    cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
    val end = cal.timeInMillis

    return start to end
}

private fun formatScreenWeekLabel(start: Long, end: Long): String {
    val fmt = java.text.SimpleDateFormat("dd MMM", Locale.getDefault())
    return "${fmt.format(java.util.Date(start))} - ${fmt.format(java.util.Date(end))}"
}