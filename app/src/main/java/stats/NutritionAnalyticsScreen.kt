package com.example.nicotracker.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.* // Importe remember, mutableStateOf
import androidx.compose.runtime.getValue // IMPORT ESSENTIEL POUR 'by'
import androidx.compose.runtime.setValue // IMPORT ESSENTIEL POUR 'var'
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.example.nicotracker.CardBackgroundEmpty
import com.example.nicotracker.DarkBackground
import com.example.nicotracker.NeonOrange
import com.example.nicotracker.TextGray
import com.example.nicotracker.data.JournalEntry
import com.example.nicotracker.data.JournalEntryViewModel
import com.example.nicotracker.data.SubCategoryViewModel
import java.util.Calendar

// Définition des couleurs
val NeonGreen = Color(0xFF00E676)
val GhostColor = Color.Gray

// --- ENUMS POUR LE GRAPHE UNIVERSEL ---

enum class GraphTimeScale(val label: String) {
    Daily("Jour"),
    Weekly("Semaine"),
    Monthly("Mois")
}

enum class GraphDataType(val label: String, val color: Color, val unit: String, val step: Int) {
    Calories("Calories", NeonGreen, "kcal", 500), // De 500 en 500
    Proteins("Protéines", NeonGreen, "g", 40),    // De 40 en 40
    Carbs("Glucides", Color(0xFF00E5FF), "g", 50),// De 50 en 50
    Lipids("Lipides", Color(0xFFFF8C00), "g", 20),// De 20 en 20
    Quality("Qualité", Color(0xFFFFD700), "/10", 2)// De 2 en 2
}
@Composable
fun NutritionAnalyticsScreen(
    journalEntryViewModel: JournalEntryViewModel,
    subCategoryViewModel: SubCategoryViewModel,
    onBack: () -> Unit
) {
    val allEntries by journalEntryViewModel.allEntries.collectAsState(initial = emptyList())

    // États de navigation
    var currentWeekOffset by remember { mutableIntStateOf(0) }
    var ghostWeekOffset by remember { mutableIntStateOf(-1) }

    var monthOffset by remember { mutableIntStateOf(0) }

    // Animations
    val animA = remember { Animatable(1f) }
    val animB = remember { Animatable(1f) }
    val animMonth = remember { Animatable(1f) }

    // Déclencheur pour Semaine A
    LaunchedEffect(currentWeekOffset) {
        animA.snapTo(0f)
        animA.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }

    // Déclencheur pour Semaine B
    LaunchedEffect(ghostWeekOffset) {
        animB.snapTo(0f)
        animB.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }

    LaunchedEffect(monthOffset) {
        animMonth.snapTo(0f)
        animMonth.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }

    val factorA = animA.value
    val factorB = animB.value
    val factorMonth = animMonth.value

    // --- DATA COMPARATIVES ---
    val (startCurrent, endCurrent) = getWeekRange(currentWeekOffset)
    val (startGhost, endGhost) = getWeekRange(ghostWeekOffset)

    val currentData = remember(allEntries, currentWeekOffset) {
        allEntries.filter { it.categoryName == "Repas" && it.date.time in startCurrent..endCurrent }
    }
    val ghostData = remember(allEntries, ghostWeekOffset) {
        allEntries.filter { it.categoryName == "Repas" && it.date.time in startGhost..endGhost }
    }

    // --- CALCULS ---
    val curTotal = currentData.sumOf { it.mealCalories ?: 0 }
    val prevTotal = ghostData.sumOf { it.mealCalories ?: 0 }

    // Fonction pour compter les jours écoulés (Lundi -> Aujourd'hui)
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
    // Pour la semaine B (comparaison), on divise par 7 (semaine complète)
    // OU par elapsedDaysA si tu veux comparer "Lundi-Mardi A" vs "Lundi-Mardi B" (plus juste).
    // Ici on garde 7 pour la semaine passée pour voir la moyenne globale de cette semaine-là.
    val divB = 7f

    // Moyenne Journalière Intelligente
    val curAvg = if (elapsedDaysA > 0) curTotal.toFloat() / elapsedDaysA else 0f
    val prevAvg = prevTotal.toFloat() / divB

    // Couleur dynamique moyenne
    val avgColorA = when {
        curAvg > 2600 -> Color(0xFFFF1744) // Rouge
        curAvg > 2100 -> Color(0xFFFFD600) // Jaune
        else -> NeonGreen // Vert
    }

    val scrollState = rememberScrollState()
    val vignetteBrush = Brush.radialGradient(
        colors = listOf(Color(0xFF1B3025), DarkBackground),
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
            Text("ANALYSE NUTRITION", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        // SELECTEURS (Semaines)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WeekSelectorRow("SEMAINE ANALYSÉE (A)", formatWeekLabel(startCurrent, endCurrent), NeonGreen, { currentWeekOffset-- }, { currentWeekOffset++ })
            WeekSelectorRow("COMPARÉE AVEC (B)", formatWeekLabel(startGhost, endGhost), GhostColor, { ghostWeekOffset-- }, { ghostWeekOffset++ })
        }

        Spacer(Modifier.height(4.dp))

        // 1. DUEL MOYENNES
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GaugeSquareCard(
                title = "SEMAINE A",
                value = curAvg,
                maxValue = 3000f,
                label = "Moyenne / jour",
                color = avgColorA,
                animFactor = factorA,
                modifier = Modifier.weight(1f)
            )
            GaugeSquareCard(
                title = "SEMAINE B",
                value = prevAvg,
                maxValue = 3000f,
                label = "Moyenne / jour",
                color = GhostColor,
                animFactor = factorB,
                modifier = Modifier.weight(1f)
            )
        }

        // 2. COURBE CALORIQUE INTERACTIVE
        CalorieLineChartCard(
            currentEntries = currentData,
            ghostEntries = ghostData,
            weekStartCurrent = startCurrent,
            weekStartGhost = startGhost,
            animFactorA = factorA,
            animFactorB = factorB,
            primaryColor = NeonGreen
        )

        // 3. QUALITÉ NUTRITIONNELLE
        QualityDuelRow(
            currentEntries = currentData,
            ghostEntries = ghostData,
            animFactorA = factorA,
            animFactorB = factorB
        )


        MacroRadarChart(
            currentEntries = currentData,
            ghostEntries = ghostData,
            animFactorA = factorA,
            animFactorB = factorB
        )

        NutritionMonthSummaryCard(
            entries = allEntries,
            monthOffset = monthOffset,
            animFactor = factorMonth,
            onPrev = { monthOffset-- },
            onNext = { monthOffset++ }
        )


        UniversalTrendCard(allEntries = allEntries)

        Spacer(Modifier.height(120.dp))
    }
}

// --------------------------------------------------------------------------------
// COMPOSANTS
// --------------------------------------------------------------------------------

@Composable
fun CalorieLineChartCard(
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
                days[dayIndex] += (entry.mealCalories ?: 0).toFloat()
            }
        }
        return days.toList()
    }

    val currentDays = remember(currentEntries) { mapToWeekDays(currentEntries, weekStartCurrent) }
    val ghostDays = remember(ghostEntries) { mapToWeekDays(ghostEntries, weekStartGhost) }

    val maxVal = maxOf(currentDays.maxOrNull() ?: 2500f, ghostDays.maxOrNull() ?: 2500f, 3600f) * 1.1f
    val daysLabels = listOf("L", "M", "M", "J", "V", "S", "D")

    // Calcul du jour limite pour l'affichage (Si c'est la semaine en cours)
    val todayIndex = remember(weekStartCurrent) {
        val now = System.currentTimeMillis()
        // Si la semaine analysée est passée (> 7 jours), on affiche tout (7)
        if (now > weekStartCurrent + 7 * 24 * 3600 * 1000) 7
        // Sinon on calcule l'index du jour actuel
        else ((now - weekStartCurrent) / (24 * 3600 * 1000)).toInt()
    }

    var selectedPoint by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    Card(
        modifier = Modifier.fillMaxWidth().height(260.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, primaryColor.copy(alpha = 0.6f))
    ) {
        Column(Modifier.padding(16.dp)) {
            // HEADER
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("COURBE CALORIQUE", color = TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                if (selectedPoint != null) {
                    val (index, isGhost) = selectedPoint!!
                    val value = if (isGhost) ghostDays[index] else currentDays[index]
                    val color = if (isGhost) GhostColor else primaryColor
                    val label = if (isGhost) "Passé" else "Actuel"
                    Text("$label : ${value.toInt()} kcal", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(primaryColor, androidx.compose.foundation.shape.CircleShape)); Spacer(Modifier.width(4.dp))
                        Text("Actuel", color = TextGray, fontSize = 9.sp); Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(8.dp).background(GhostColor, androidx.compose.foundation.shape.CircleShape)); Spacer(Modifier.width(4.dp))
                        Text("Passé", color = TextGray, fontSize = 9.sp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // CANVAS
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
                                // On limite la sélection aux jours existants pour la courbe A
                                val limitA = if (weekStartCurrent > System.currentTimeMillis() - 7*24*3600*1000) todayIndex else 7

                                val valA = if (index <= limitA) currentDays[index] else null
                                val valB = ghostDays[index]

                                if (valA == null) {
                                    // Si jour futur, on force la sélection sur le Ghost (si on veut) ou on ignore
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

                    // 1. GRILLE
                    val gridSteps = listOf(0, 500, 1000, 1500, 2000, 2500, 3000, 3500)
                    val textPaint = android.graphics.Paint().apply { setColor(android.graphics.Color.LTGRAY); textSize = 9.sp.toPx(); textAlign = android.graphics.Paint.Align.RIGHT }
                    gridSteps.forEach { step ->
                        val y = fullHeight - (step.toFloat() / maxVal * fullHeight)
                        if (y >= 0 && y <= fullHeight) {
                            drawLine(color = Color.White.copy(alpha = 0.1f), start = Offset(paddingStart, y), end = Offset(paddingStart + graphWidth, y), strokeWidth = 1.dp.toPx())
                            drawContext.canvas.nativeCanvas.drawText(step.toString(), paddingStart - 25f, y + 4f, textPaint)
                        }
                    }
                    val goalY = fullHeight - (2100f / maxVal * fullHeight)
                    drawLine(color = Color.White.copy(alpha = 0.5f), start = Offset(paddingStart, goalY), end = Offset(paddingStart + graphWidth, goalY), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))

                    // 3. COURBES
                    fun drawSmoothLine(data: List<Float>, color: Color, isGhost: Boolean, currentAnimFactor: Float) {
                        val path = Path()
                        var firstPoint = true

                        // Si c'est le Ghost, on dessine tout (limite = 7). Si c'est Actuel, on s'arrête à todayIndex
                        val drawLimit = if (isGhost) 7 else todayIndex

                        data.forEachIndexed { index, value ->
                            // CONDITION CLEF : Ne pas dessiner les jours futurs
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

                        // POINTS
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

                    // 4. INTERACTION
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

                        val label = "${value.toInt()}"
                        val paintTooltip = android.graphics.Paint().apply { setColor(android.graphics.Color.WHITE); textSize = 12.sp.toPx(); typeface = android.graphics.Typeface.DEFAULT_BOLD; textAlign = android.graphics.Paint.Align.CENTER }
                        val textBounds = android.graphics.Rect(); paintTooltip.getTextBounds(label, 0, label.length, textBounds)
                        val tooltipW = textBounds.width() + 20f; val tooltipH = textBounds.height() + 15f; val tooltipY = y - 25.dp.toPx()

                        drawRoundRect(color = Color(0xFF151515), topLeft = Offset(x - tooltipW/2, tooltipY - tooltipH), size = Size(tooltipW, tooltipH), cornerRadius = CornerRadius(8f), style = Fill)
                        drawRoundRect(color = color, topLeft = Offset(x - tooltipW/2, tooltipY - tooltipH), size = Size(tooltipW, tooltipH), cornerRadius = CornerRadius(8f), style = Stroke(width = 1.dp.toPx()))
                        drawContext.canvas.nativeCanvas.drawText(label, x, tooltipY - tooltipH/2 + textBounds.height()/3, paintTooltip)
                    }

                    // 5. LABELS
                    val dayLabelPaint = android.graphics.Paint().apply { setColor(android.graphics.Color.GRAY); textSize = 10.sp.toPx(); textAlign = android.graphics.Paint.Align.CENTER; isFakeBoldText = true }
                    daysLabels.forEachIndexed { index, label ->
                        if (selectedPoint?.first == index) dayLabelPaint.color = android.graphics.Color.WHITE
                        else dayLabelPaint.color = android.graphics.Color.GRAY
                        val x = paddingStart + (index * xStep)
                        drawContext.canvas.nativeCanvas.drawText(label, x, size.height, dayLabelPaint)
                    }
                }
            }
        }
    }
}

@Composable
fun QualityDuelRow(
    currentEntries: List<JournalEntry>,
    ghostEntries: List<JournalEntry>,
    animFactorA: Float,
    animFactorB: Float
) {
    val curAvg = remember(currentEntries) {
        val valid = currentEntries.mapNotNull { it.mealQuality }
        if (valid.isNotEmpty()) valid.average().toFloat() else 0f
    }
    val prevAvg = remember(ghostEntries) {
        val valid = ghostEntries.mapNotNull { it.mealQuality }
        if (valid.isNotEmpty()) valid.average().toFloat() else 0f
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        VerticalQualityGaugeCard(
            title = "QUALITÉ A",
            score = curAvg,
            baseColor = NeonGreen,
            isGhost = false,
            animFactor = animFactorA,
            modifier = Modifier.weight(1f)
        )
        VerticalQualityGaugeCard(
            title = "QUALITÉ B",
            score = prevAvg,
            baseColor = GhostColor,
            isGhost = true,
            animFactor = animFactorB,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun VerticalQualityGaugeCard(
    title: String,
    score: Float,
    baseColor: Color,
    isGhost: Boolean,
    animFactor: Float,
    modifier: Modifier
) {
    val finalColor = if (isGhost) baseColor else when {
        score >= 8f -> NeonGreen
        score >= 5f -> Color(0xFFFF8C00)
        else -> Color(0xFFFF1744)
    }

    val shadowElevation = if (isGhost) 0.dp else 15.dp

    Card(
        modifier = modifier
            .height(160.dp)
            .shadow(shadowElevation, RoundedCornerShape(16.dp), spotColor = finalColor, ambientColor = finalColor),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (isGhost) baseColor.copy(alpha = 0.3f) else finalColor.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.Start
            ) {
                Text(title, color = TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Column {
                    Text(
                        text = String.format("%.1f", score),
                        color = if (isGhost) TextGray else Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = "/ 10", color = TextGray.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }

            Box(
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier
                    .width(24.dp)
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape)
                    .border(1.dp, TextGray.copy(alpha = 0.1f), androidx.compose.foundation.shape.CircleShape)
                    .clip(androidx.compose.foundation.shape.CircleShape)
            ) {
                val fillRatio = (score / 10f).coerceIn(0f, 1f) * animFactor
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fillRatio)
                        .background(Brush.verticalGradient(colors = listOf(finalColor, finalColor.copy(alpha = 0.5f))))
                )
            }
        }
    }
}

@Composable
fun UniversalTrendCard(
    allEntries: List<JournalEntry>
) {
    // ÉTATS DE SÉLECTION
    var selectedScale by remember { mutableStateOf(GraphTimeScale.Daily) }
    var selectedType by remember { mutableStateOf(GraphDataType.Calories) }

    // --- PRÉPARATION DES DONNÉES (CORRIGÉ : MOYENNE INTELLIGENTE) ---
    val graphData = remember(allEntries, selectedScale, selectedType) {
        if (allEntries.isEmpty()) return@remember emptyList<Triple<String, Float, Long>>()

        val sortedEntries = allEntries.sortedBy { it.date }
        val points = mutableListOf<Triple<String, Float, Long>>()
        val cal = java.util.Calendar.getInstance(java.util.Locale.ENGLISH)
        cal.firstDayOfWeek = java.util.Calendar.MONDAY

        // Fonction helper pour récupérer la valeur brute
        fun getRawValue(entry: JournalEntry): Float? = when (selectedType) {
            GraphDataType.Calories -> entry.mealCalories?.toFloat()
            GraphDataType.Proteins -> entry.mealProtein?.toFloat()
            GraphDataType.Carbs -> entry.mealCarbs?.toFloat()
            GraphDataType.Lipids -> entry.mealLipids?.toFloat()
            GraphDataType.Quality -> entry.mealQuality?.toFloat()
        }

        // 1. Groupement selon l'échelle de temps
        val grouped = when (selectedScale) {
            GraphTimeScale.Daily -> sortedEntries.groupBy {
                cal.time = it.date
                "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.DAY_OF_YEAR)}"
            }
            GraphTimeScale.Weekly -> sortedEntries.groupBy {
                cal.time = it.date
                "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.WEEK_OF_YEAR)}"
            }
            GraphTimeScale.Monthly -> sortedEntries.groupBy {
                cal.time = it.date
                "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.MONTH)}"
            }
        }

        // 2. Calcul des valeurs pour chaque point
        grouped.forEach { (_, periodEntries) ->
            if (periodEntries.isNotEmpty()) {
                val firstDate = periodEntries.first().date
                cal.time = firstDate

                // Définition du Label
                val (label, timestamp) = when (selectedScale) {
                    GraphTimeScale.Daily -> {
                        val lbl = java.text.SimpleDateFormat("dd", java.util.Locale.ENGLISH).format(firstDate)
                        lbl to firstDate.time
                    }
                    GraphTimeScale.Weekly -> {
                        cal.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.SUNDAY)
                        val lbl = java.text.SimpleDateFormat("dd/MM", java.util.Locale.ENGLISH).format(cal.time)
                        lbl to firstDate.time
                    }
                    GraphTimeScale.Monthly -> {
                        val lbl = java.text.SimpleDateFormat("MMM", java.util.Locale.ENGLISH).format(firstDate).uppercase()
                        lbl to firstDate.time
                    }
                }

                // --- CALCUL DE LA VALEUR ---
                val value = if (selectedType == GraphDataType.Quality) {
                    // CAS QUALITÉ : Moyenne des notes existantes
                    val scores = periodEntries.mapNotNull { getRawValue(it) }
                    if (scores.isNotEmpty()) scores.average().toFloat() else 0f
                } else {
                    // CAS MACROS/CALORIES : Moyenne sur les JOURS ACTIFS
                    val total = periodEntries.mapNotNull { getRawValue(it) }.sum()

                    if (selectedScale == GraphTimeScale.Daily) {
                        total
                    } else {
                        // On compte combien de jours différents on a vraiment loggés dans cette période
                        val activeDays = periodEntries.map {
                            val c = java.util.Calendar.getInstance()
                            c.time = it.date
                            c.get(java.util.Calendar.DAY_OF_YEAR)
                        }.distinct().count()

                        // On divise par les jours actifs (min 1 pour éviter division par 0)
                        total / activeDays.coerceAtLeast(1).toFloat()
                    }
                }

                points.add(Triple(label, value, timestamp))
            }
        }
        points
    }

    // Calcul Max Y et Dimensions
    val stepSize = selectedType.step
    val rawMax = graphData.maxOfOrNull { it.second } ?: (stepSize * 2f)
    val maxVal = (kotlin.math.ceil(rawMax / stepSize) * stepSize).coerceAtLeast(stepSize.toFloat())

    val pointSpacing = when(selectedScale) {
        GraphTimeScale.Daily -> 60f
        GraphTimeScale.Weekly -> 100f
        GraphTimeScale.Monthly -> 120f
    }

    val yAxisWidth = 35.dp
    val paddingStartPx = with(androidx.compose.ui.platform.LocalDensity.current) { 16.dp.toPx() }
    val totalContentWidth = (graphData.size * pointSpacing) + paddingStartPx * 2

    // Scroll State
    val scrollState = rememberScrollState()

    // --- CALCUL DU MOIS VISIBLE (RADAR) ---
    var visibleMonthTitle by remember { mutableStateOf("") }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val containerWidthPx = with(density) { (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp - 32.dp).toPx() } // Largeur approx de la card

    // On met à jour le titre à chaque scroll
    LaunchedEffect(scrollState.value, graphData) {
        if (graphData.isNotEmpty()) {
            // On cherche quel point est au milieu de la zone visible
            // scrollX est la position du début du contenu par rapport au bord gauche
            // Le centre visible est à scrollState.value + moitié de la largeur du conteneur
            val visibleCenter = scrollState.value + (containerWidthPx / 2)

            // On convertit cette position en index
            val index = ((visibleCenter - paddingStartPx) / pointSpacing).toInt().coerceIn(graphData.indices)

            val timestamp = graphData[index].third
            // Formatage Anglais: "JANUARY 2025"
            visibleMonthTitle = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.ENGLISH)
                .format(java.util.Date(timestamp))
                .uppercase()
        }
    }

    LaunchedEffect(graphData.size, selectedScale) {
        if (graphData.isNotEmpty()) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .shadow(15.dp, RoundedCornerShape(24.dp), spotColor = selectedType.color, ambientColor = selectedType.color),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(2.dp, selectedType.color.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp)) {

            // HEADER
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ÉVOLUTION",
                        color = TextGray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    // --- INDICATEUR RADAR ---
                    if (visibleMonthTitle.isNotEmpty() && selectedScale == GraphTimeScale.Daily) {
                        Text(
                            text = visibleMonthTitle, // ex: JANUARY 2025
                            color = NeonGreen, // On met en valeur le mois
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactSelector(
                        currentLabel = selectedType.label,
                        color = selectedType.color,
                        options = GraphDataType.values().map { it.label },
                        onSelect = { label -> selectedType = GraphDataType.values().first { it.label == label } }
                    )

                    CompactSelector(
                        currentLabel = selectedScale.label,
                        color = Color.White,
                        options = GraphTimeScale.values().map { it.label },
                        onSelect = { label -> selectedScale = GraphTimeScale.values().first { it.label == label } }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ZONE GRAPHIQUE
            Box(modifier = Modifier.fillMaxSize()) {

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = yAxisWidth)
                        .horizontalScroll(scrollState)
                ) {
                    Box(
                        modifier = Modifier
                            .width(with(density) { totalContentWidth.toDp() })
                            .fillMaxHeight()
                            // CORRECTION ICI : On remplace Unit par les variables qui changent (graphData et pointSpacing)
                            .pointerInput(graphData, pointSpacing) {
                                detectTapGestures(onPress = { offset ->
                                    val tapX = offset.x

                                    // 1. On retire la marge de départ
                                    val relativeX = tapX - paddingStartPx

                                    // 2. On divise par le pointSpacing ACTUEL (qui est maintenant à jour grâce au pointerInput)
                                    val rawIndex = relativeX / pointSpacing

                                    // 3. Arrondi à l'index le plus proche
                                    val index = kotlin.math.round(rawIndex).toInt()

                                    // 4. Vérification et Tolérance
                                    if (index in graphData.indices) {
                                        val exactPointX = paddingStartPx + (index * pointSpacing)
                                        val distance = kotlin.math.abs(tapX - exactPointX)

                                        // Tolérance de clic (moitié de l'espace)
                                        if (distance < (pointSpacing / 2)) {
                                            selectedIndex = index
                                            tryAwaitRelease()
                                            selectedIndex = null
                                        }
                                    }
                                })
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // ... LE CONTENU DU CANVAS RESTE EXACTEMENT LE MÊME ...
                            // (Copie-colle ton code de dessin Canvas ici, il n'a pas besoin de changer)
                            // ...

                            val paddingTopPx = 40.dp.toPx()
                            val paddingBottomPx = 20.dp.toPx()
                            val graphHeight = size.height - paddingTopPx - paddingBottomPx

                            // Lignes Horizontales
                            var currentYVal = 0
                            while (currentYVal <= maxVal) {
                                val yRatio = currentYVal / maxVal.toFloat()
                                val yPos = paddingTopPx + graphHeight - (yRatio * graphHeight)
                                drawLine(
                                    color = Color.White.copy(alpha = 0.1f),
                                    start = Offset(0f, yPos),
                                    end = Offset(size.width, yPos),
                                    strokeWidth = 1.dp.toPx()
                                )
                                currentYVal += stepSize
                            }

                            val path = Path()
                            val fillPath = Path()

                            graphData.forEachIndexed { i, data ->
                                val x = paddingStartPx + (i * pointSpacing)
                                val y = paddingTopPx + graphHeight - ((data.second / maxVal) * graphHeight)

                                if (i == 0) {
                                    path.moveTo(x, y)
                                    fillPath.moveTo(x, paddingTopPx + graphHeight)
                                    fillPath.lineTo(x, y)
                                } else {
                                    val prevX = paddingStartPx + ((i - 1) * pointSpacing)
                                    val prevY = paddingTopPx + graphHeight - ((graphData[i - 1].second / maxVal) * graphHeight)
                                    val cx = (prevX + x) / 2
                                    path.cubicTo(cx, prevY, cx, y, x, y)
                                    fillPath.cubicTo(cx, prevY, cx, y, x, y)
                                }

                                if (i == graphData.size - 1) {
                                    fillPath.lineTo(x, paddingTopPx + graphHeight)
                                    fillPath.close()
                                }
                            }

                            if (graphData.isNotEmpty()) {
                                drawPath(
                                    path = fillPath,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(selectedType.color.copy(alpha = 0.3f), Color.Transparent),
                                        startY = paddingTopPx,
                                        endY = paddingTopPx + graphHeight
                                    )
                                )
                                drawPath(
                                    path = path,
                                    color = selectedType.color,
                                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }

                            // POINTS ET LABELS
                            val textPaintNormal = android.graphics.Paint().apply {
                                color = android.graphics.Color.DKGRAY
                                textSize = 9.sp.toPx()
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                            // Paint spécial pour le 1er du mois (Orange + Gras)
                            val textPaintFirstOfMonth = android.graphics.Paint().apply {
                                color = android.graphics.Color.parseColor("#FF8C00") // Neon Orange
                                textSize = 9.sp.toPx()
                                isFakeBoldText = true
                                textAlign = android.graphics.Paint.Align.CENTER
                            }

                            graphData.forEachIndexed { i, data ->
                                val x = paddingStartPx + (i * pointSpacing)
                                val y = paddingTopPx + graphHeight - ((data.second / maxVal) * graphHeight)

                                // --- LOGIQUE AXE X INTELLIGENT (ANGLAIS) ---
                                val label = data.first
                                // Si c'est "01" ET qu'on est en vue Daily, on affiche le mois en lettres
                                if (selectedScale == GraphTimeScale.Daily && label == "01") {
                                    val monthName = java.text.SimpleDateFormat("MMM", java.util.Locale.ENGLISH)
                                        .format(java.util.Date(data.third))
                                        .uppercase()

                                    // On dessine une petite ligne verticale pour marquer le mois
                                    drawLine(
                                        color = Color(0xFFFF8C00).copy(alpha = 0.5f),
                                        start = Offset(x, paddingTopPx),
                                        end = Offset(x, size.height - 15f), // On s'arrête avant le texte
                                        strokeWidth = 1.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                    )

                                    drawContext.canvas.nativeCanvas.drawText(monthName, x, size.height, textPaintFirstOfMonth)
                                } else {
                                    drawContext.canvas.nativeCanvas.drawText(label, x, size.height, textPaintNormal)
                                }

                                drawCircle(CardBackgroundEmpty, radius = 4.dp.toPx(), center = Offset(x, y))
                                drawCircle(selectedType.color, radius = 3.dp.toPx(), center = Offset(x, y))

                                if (selectedIndex == i) {
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.5f),
                                        start = Offset(x, y), end = Offset(x, paddingTopPx + graphHeight),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                                    )

                                    val tooltipLabel = if (selectedType == GraphDataType.Quality)
                                        String.format(java.util.Locale.ENGLISH, "%.1f/10", data.second)
                                    else
                                        "${data.second.toInt()} ${selectedType.unit}"

                                    val tpPaint = android.graphics.Paint().apply {
                                        color = android.graphics.Color.WHITE
                                        textSize = 12.sp.toPx()
                                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                                        textAlign = android.graphics.Paint.Align.CENTER
                                    }
                                    val bounds = android.graphics.Rect()
                                    tpPaint.getTextBounds(tooltipLabel, 0, tooltipLabel.length, bounds)

                                    val bgW = bounds.width() + 40f
                                    val bgH = bounds.height() + 30f
                                    val bgY = y - 40.dp.toPx()

                                    drawRoundRect(
                                        color = Color(0xFF151515), topLeft = Offset(x - bgW/2, bgY - bgH),
                                        size = Size(bgW, bgH), cornerRadius = CornerRadius(10f), style = Fill
                                    )
                                    drawRoundRect(
                                        color = selectedType.color, topLeft = Offset(x - bgW/2, bgY - bgH),
                                        size = Size(bgW, bgH), cornerRadius = CornerRadius(10f), style = Stroke(width = 2f)
                                    )
                                    drawContext.canvas.nativeCanvas.drawText(tooltipLabel, x, bgY - bgH/2 + bounds.height()/2, tpPaint)
                                }
                            }
                        }
                    }
                }

                // AXE Y FIXE
                Box(
                    modifier = Modifier
                        .width(yAxisWidth)
                        .fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(CardBackgroundEmpty, CardBackgroundEmpty.copy(alpha=0.9f), Color.Transparent)))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val paddingTopPx = 40.dp.toPx()
                        val paddingBottomPx = 20.dp.toPx()
                        val graphHeight = size.height - paddingTopPx - paddingBottomPx

                        val textPaintY = android.graphics.Paint().apply {
                            color = android.graphics.Color.GRAY
                            textSize = 9.sp.toPx()
                            textAlign = android.graphics.Paint.Align.RIGHT
                        }

                        var currentYVal = 0
                        while (currentYVal <= maxVal) {
                            val yRatio = currentYVal / maxVal.toFloat()
                            val yPos = paddingTopPx + graphHeight - (yRatio * graphHeight)
                            drawContext.canvas.nativeCanvas.drawText(
                                currentYVal.toString(),
                                size.width - 5f,
                                yPos + 4f,
                                textPaintY
                            )
                            currentYVal += stepSize
                        }
                    }
                }
            }
        }
    }
}

// --- PETIT COMPOSANT HELPER POUR LES MENUS DÉROULANTS ---
@Composable
fun CompactSelector(
    currentLabel: String,
    color: Color,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(currentLabel, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF1E1E1E))
        ) {
            options.forEach { label ->
                DropdownMenuItem(
                    text = { Text(label, color = Color.White, fontSize = 12.sp) },
                    onClick = {
                        onSelect(label)
                        expanded = false
                    }
                )
            }
        }
    }
}
@Composable
fun MacroRadarChart(
    currentEntries: List<JournalEntry>,
    ghostEntries: List<JournalEntry>,
    animFactorA: Float,
    animFactorB: Float
) {
    // 1. Calcul des moyennes (Grammes)
    val (curP, curC, curL) = remember(currentEntries) { calculateSmartMacroAverages(currentEntries) }
    val (ghP, ghC, ghL) = remember(ghostEntries) { calculateSmartMacroAverages(ghostEntries) }

    // 2. Calcul des pourcentages pour la SEMAINE ACTUELLE
    val totalCur = curP + curC + curL
    val pctCurP = if (totalCur > 0) curP / totalCur else 0f
    val pctCurC = if (totalCur > 0) curC / totalCur else 0f
    val pctCurL = if (totalCur > 0) curL / totalCur else 0f

    // 3. Calcul des pourcentages pour la SEMAINE FANTÔME (Ghost)
    val totalGh = ghP + ghC + ghL
    val pctGhP = if (totalGh > 0) ghP / totalGh else 0f
    val pctGhC = if (totalGh > 0) ghC / totalGh else 0f
    val pctGhL = if (totalGh > 0) ghL / totalGh else 0f

    // Échelle du radar
    val maxVal = maxOf(curP, curC, curL, ghP, ghC, ghL, 150f) * 1.1f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .shadow(
                elevation = 15.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = NeonGreen,
                ambientColor = NeonGreen
            ),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, NeonGreen.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --- GAUCHE : RADAR (Code inchangé pour le dessin) ---
            Box(
                modifier = Modifier.weight(0.55f).fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(170.dp)) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val radius = size.minDimension / 2 * 0.85f
                    val angleP = -Math.PI / 2; val angleC = -Math.PI / 2 + 2 * Math.PI / 3; val angleL = -Math.PI / 2 + 4 * Math.PI / 3

                    fun getPoint(value: Float, angle: Double): Offset {
                        val ratio = (value / maxVal).coerceIn(0f, 1f)
                        return Offset((center.x + radius * ratio * kotlin.math.cos(angle)).toFloat(), (center.y + radius * ratio * kotlin.math.sin(angle)).toFloat())
                    }

                    // Grille
                    val gridPaint = android.graphics.Paint().apply { color = android.graphics.Color.DKGRAY; style = android.graphics.Paint.Style.STROKE; strokeWidth = 1.dp.toPx(); alpha = 50 }
                    val pathGrid = Path()
                    listOf(0.33f, 0.66f, 1f).forEach { r ->
                        val p1 = Offset((center.x + radius * r * kotlin.math.cos(angleP)).toFloat(), (center.y + radius * r * kotlin.math.sin(angleP)).toFloat())
                        val p2 = Offset((center.x + radius * r * kotlin.math.cos(angleC)).toFloat(), (center.y + radius * r * kotlin.math.sin(angleC)).toFloat())
                        val p3 = Offset((center.x + radius * r * kotlin.math.cos(angleL)).toFloat(), (center.y + radius * r * kotlin.math.sin(angleL)).toFloat())
                        pathGrid.reset(); pathGrid.moveTo(p1.x, p1.y); pathGrid.lineTo(p2.x, p2.y); pathGrid.lineTo(p3.x, p3.y); pathGrid.close()
                        drawContext.canvas.nativeCanvas.drawPath(pathGrid.asAndroidPath(), gridPaint)
                    }
                    // Axes
                    drawLine(Color.DarkGray.copy(alpha=0.3f), center, getPoint(maxVal, angleP))
                    drawLine(Color.DarkGray.copy(alpha=0.3f), center, getPoint(maxVal, angleC))
                    drawLine(Color.DarkGray.copy(alpha=0.3f), center, getPoint(maxVal, angleL))

                    // Formes
                    fun drawRadarShape(p: Float, c: Float, l: Float, color: Color, anim: Float, isGhost: Boolean) {
                        if (p + c + l <= 0) return
                        val pP = getPoint(p * anim, angleP); val pC = getPoint(c * anim, angleC); val pL = getPoint(l * anim, angleL)
                        val path = Path().apply { moveTo(pP.x, pP.y); lineTo(pC.x, pC.y); lineTo(pL.x, pL.y); close() }
                        drawPath(path = path, color = color.copy(alpha = if (isGhost) 0.1f else 0.2f))
                        drawPath(path = path, color = color.copy(alpha = if (isGhost) 0.4f else 0.9f), style = Stroke(width = if(isGhost) 2.dp.toPx() else 3.dp.toPx(), join = StrokeJoin.Round))
                    }
                    drawRadarShape(ghP, ghC, ghL, GhostColor, animFactorB, true)
                    drawRadarShape(curP, curC, curL, NeonGreen, animFactorA, false)

                    // Labels Lettres
                    val textPaint = android.graphics.Paint().apply { color = android.graphics.Color.GRAY; textSize = 10.sp.toPx(); typeface = android.graphics.Typeface.DEFAULT_BOLD; textAlign = android.graphics.Paint.Align.CENTER }
                    val labelRadius = radius * 1.15f
                    val posP = Offset((center.x + labelRadius * kotlin.math.cos(angleP)).toFloat(), (center.y + labelRadius * kotlin.math.sin(angleP)).toFloat() + 4.dp.toPx())
                    val posC = Offset((center.x + labelRadius * kotlin.math.cos(angleC)).toFloat(), (center.y + labelRadius * kotlin.math.sin(angleC)).toFloat() + 4.dp.toPx())
                    val posL = Offset((center.x + labelRadius * kotlin.math.cos(angleL)).toFloat(), (center.y + labelRadius * kotlin.math.sin(angleL)).toFloat() + 4.dp.toPx())
                    drawContext.canvas.nativeCanvas.drawText("Protéines", posP.x, posP.y, textPaint)
                    drawContext.canvas.nativeCanvas.drawText("Glucides", posC.x, posC.y, textPaint)
                    drawContext.canvas.nativeCanvas.drawText("Lipides", posL.x, posL.y, textPaint)
                }
            }

            // Séparateur
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.width(1.dp).fillMaxHeight(0.7f).background(Color.White.copy(alpha=0.1f)))
            Spacer(modifier = Modifier.width(12.dp))

            // --- DROITE : LÉGENDE COMPARATIVE ---
            Column(
                modifier = Modifier.weight(0.45f).fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                // Header légende
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(NeonGreen, androidx.compose.foundation.shape.CircleShape))
                    Spacer(Modifier.width(4.dp))
                    Text("Actuel", color = TextGray, fontSize = 8.sp)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(6.dp).background(GhostColor, androidx.compose.foundation.shape.CircleShape))
                    Spacer(Modifier.width(4.dp))
                    Text("Passé", color = TextGray, fontSize = 8.sp)
                }

                Spacer(Modifier.height(16.dp))

                // Items avec comparaison
                MacroLegendItem(
                    label = "PROTÉINES",
                    curVal = "${curP.toInt()}g", curPct = pctCurP,
                    ghVal = "${ghP.toInt()}g", ghPct = pctGhP,
                    color = NeonGreen, animA = animFactorA, animB = animFactorB
                )
                Spacer(Modifier.height(12.dp))

                MacroLegendItem(
                    label = "GLUCIDES",
                    curVal = "${curC.toInt()}g", curPct = pctCurC,
                    ghVal = "${ghC.toInt()}g", ghPct = pctGhC,
                    color = NeonGreen, animA = animFactorA, animB = animFactorB
                )
                Spacer(Modifier.height(12.dp))

                MacroLegendItem(
                    label = "LIPIDES",
                    curVal = "${curL.toInt()}g", curPct = pctCurL,
                    ghVal = "${ghL.toInt()}g", ghPct = pctGhL,
                    color = NeonGreen, animA = animFactorA, animB = animFactorB
                )
            }
        }
    }
}

// --- NOUVEAU HELPER AVEC DOUBLE BARRE ---
@Composable
fun MacroLegendItem(
    label: String,
    curVal: String, curPct: Float,
    ghVal: String, ghPct: Float,
    color: Color,
    animA: Float, animB: Float
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Ligne de texte : "PROTÉINES ...... 140g (120g)"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(label, color = Color.LightGray, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            Row {
                Text(curVal, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(4.dp))
                // Valeur fantôme en petit et gris
                Text("($ghVal)", color = GhostColor, fontSize = 9.sp)
            }
        }
        Spacer(Modifier.height(4.dp))

        // BARRE 1 : ACTUELLE (Colorée)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp) // Barre principale un peu plus épaisse
                .background(Color.DarkGray.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(curPct * animA)
                    .fillMaxHeight()
                    .background(color, RoundedCornerShape(2.dp))
            )
        }

        Spacer(Modifier.height(2.dp)) // Petit écart entre les deux barres

        // BARRE 2 : FANTÔME (Grise et fine)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp) // Barre fantôme plus fine
                .background(Color.Transparent) // Pas de fond pour ne pas alourdir
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ghPct * animB)
                    .fillMaxHeight()
                    .background(GhostColor.copy(alpha = 0.6f), RoundedCornerShape(1.dp))
            )
        }
    }
}

// Calcule la moyenne des macros par jour, en ignorant les jours vides
fun calculateSmartMacroAverages(entries: List<JournalEntry>): Triple<Float, Float, Float> {
    if (entries.isEmpty()) return Triple(0f, 0f, 0f)

    // 1. On regroupe par jour (ex: jour 345 de l'année)
    val dailyMap = entries.groupBy {
        val c = Calendar.getInstance()
        c.time = it.date
        // On combine Année + Jour pour être sûr
        "${c.get(Calendar.YEAR)}-${c.get(Calendar.DAY_OF_YEAR)}"
    }

    // 2. On additionne les macros pour chaque jour actif
    var sumP = 0f
    var sumC = 0f
    var sumL = 0f

    dailyMap.values.forEach { dayEntries ->
        sumP += dayEntries.sumOf { it.mealProtein ?: 0 }.toFloat()
        sumC += dayEntries.sumOf { it.mealCarbs ?: 0 }.toFloat()
        sumL += dayEntries.sumOf { it.mealLipids ?: 0 }.toFloat()
    }

    // 3. On divise par le nombre de jours ACTIFS (et non pas 7)
    val activeDays = dailyMap.size.coerceAtLeast(1)

    return Triple(sumP / activeDays, sumC / activeDays, sumL / activeDays)
}

@Composable
fun NutritionMonthSummaryCard(
    entries: List<JournalEntry>,
    monthOffset: Int,
    animFactor: Float = 1f, // Valeur par défaut pour éviter les erreurs si l'appel n'est pas encore maj
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    // 1. Calcul de la période
    val calendar = remember(monthOffset) {
        java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.MONTH, monthOffset)
        }
    }
    val currentMonth = calendar.get(java.util.Calendar.MONTH)
    val currentYear = calendar.get(java.util.Calendar.YEAR)

    val monthLabel = remember(monthOffset) {
        java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.FRANCE)
            .format(calendar.time)
            .uppercase()
    }

    // 2. Filtrage et Calculs
    val (stats, avgNote) = remember(entries, monthOffset) {
        val monthData = entries.filter {
            val c = java.util.Calendar.getInstance().apply { time = it.date }
            it.categoryName == "Repas" &&
                    c.get(java.util.Calendar.MONTH) == currentMonth &&
                    c.get(java.util.Calendar.YEAR) == currentYear
        }

        if (monthData.isEmpty()) {
            Pair(null, 0f)
        } else {
            // Jours actifs (uniques)
            val uniqueDays = monthData.map {
                val c = java.util.Calendar.getInstance().apply { time = it.date }
                c.get(java.util.Calendar.DAY_OF_YEAR)
            }.distinct().count()
            val daysDivider = if (uniqueDays > 0) uniqueDays else 1

            // Moyennes Macros / Cal
            val avgKcal = monthData.sumOf { it.mealCalories ?: 0 } / daysDivider
            val avgProt = monthData.sumOf { it.mealProtein ?: 0 } / daysDivider
            val avgCarbs = monthData.sumOf { it.mealCarbs ?: 0 } / daysDivider
            val avgFat = monthData.sumOf { it.mealLipids ?: 0 } / daysDivider

            // Moyenne Note (sur l'ensemble des repas notés)
            val notes = monthData.mapNotNull { it.mealQuality }
            val averageNote = if (notes.isNotEmpty()) notes.average().toFloat() else 0f

            Pair(
                NutritionMonthStats(avgKcal, avgProt, avgCarbs, avgFat, uniqueDays),
                averageNote
            )
        }
    }

    // 3. Affichage
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .shadow(
                elevation = 15.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = NeonGreen, // Lueur Verte
                ambientColor = NeonGreen
            ),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(2.dp, NeonGreen.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // HEADER (Navigation)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrev, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.ChevronLeft, null, tint = TextGray)
                }
                Text(
                    text = "BILAN : $monthLabel",
                    color = TextGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                IconButton(onClick = onNext, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.ChevronRight, null, tint = TextGray)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (stats != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // --- PARTIE GAUCHE : CERCLE CALORIES ---
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(100.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val stroke = 10.dp.toPx()
                            val radius = size.minDimension / 2 - stroke / 2

                            // Fond gris
                            drawCircle(
                                color = Color.DarkGray.copy(alpha = 0.3f),
                                radius = radius,
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )

                            // Arc vert (Objectif 2100)
                            val targetProgress = (stats.avgKcal / 2100f).coerceIn(0f, 1f)
                            val animatedProgress = targetProgress * animFactor

                            if (animatedProgress > 0) {
                                drawArc(
                                    color = NeonGreen,
                                    startAngle = -90f,
                                    sweepAngle = 360f * animatedProgress,
                                    useCenter = false,
                                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${stats.avgKcal}",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Kcal/j",
                                color = TextGray,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    // --- PARTIE DROITE : LISTE MACROS & NOTE ---
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.alpha(animFactor)
                    ) {
                        // Protéines
                        MacroRowCompact(color = NeonGreen, label = "Protéines", value = "${stats.avgProt}g")
                        // Glucides
                        MacroRowCompact(color = Color(0xFF00E5FF), label = "Glucides", value = "${stats.avgCarbs}g") // Cyan
                        // Lipids
                        MacroRowCompact(color = Color(0xFFFF8C00), label = "Lipides", value = "${stats.avgFat}g") // Orange

                        Divider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))

                        // Note Moyenne
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFD700), // Or
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Note moy. : ${String.format("%.1f", avgNote)}/10",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    Text("Aucune donnée ce mois-ci", color = TextGray.copy(alpha = 0.5f), fontSize = 12.sp)
                }
            }
        }
    }
}

// Petit helper pour les lignes de droite
@Composable
fun MacroRowCompact(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, androidx.compose.foundation.shape.CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            color = TextGray,
            fontSize = 11.sp
        )
    }
}

// Petite data class locale pour stocker les résultats
data class NutritionMonthStats(
    val avgKcal: Int,
    val avgProt: Int,
    val avgCarbs: Int,
    val avgFat: Int,
    val activeDays: Int
)

@Composable
fun MacroMonthItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, color = color.copy(alpha = 0.8f), fontSize = 10.sp)
    }
}