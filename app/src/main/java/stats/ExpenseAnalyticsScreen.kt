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
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EuroSymbol
import androidx.compose.material.icons.filled.ShoppingCart
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
import com.example.nicotracker.data.SubCategoryViewModel
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

// --- COULEURS THÈME DÉPENSES ---
val NeonExpense = Color(0xFF76FF03) // Vert électrique
val GhostExpense = Color.Gray

@Composable
fun ExpenseAnalyticsScreen(
    journalEntryViewModel: JournalEntryViewModel,
    subCategoryViewModel: SubCategoryViewModel,
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

    // On ne garde QUE les dépenses
    val currentData = remember(allEntries, currentWeekOffset) {
        allEntries.filter { it.categoryName == "Dépense" && it.date.time in startCurrent..endCurrent }
    }
    val ghostData = remember(allEntries, ghostWeekOffset) {
        allEntries.filter { it.categoryName == "Dépense" && it.date.time in startGhost..endGhost }
    }

    // --- CALCULS SEMAINE ---
    val curTotal = currentData.sumOf { it.depensePrice ?: 0.0 }.toFloat()
    val prevTotal = ghostData.sumOf { it.depensePrice ?: 0.0 }.toFloat()

    // Moyenne par jour (sur 7 jours pour lisser)
    val curDailyAvg = curTotal / 7f
    val prevDailyAvg = prevTotal / 7f

    // Max pour les jauges (au moins 200€ ou le max atteint)
    val maxGaugeValue = max(max(curTotal, prevTotal), 200f) * 1.1f

    // --- DATA MOIS ---
    val currentMonthEntries = remember(allEntries, monthlySummaryOffset) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, monthlySummaryOffset)
        val tM = cal.get(Calendar.MONTH); val tY = cal.get(Calendar.YEAR)
        allEntries.filter {
            val c = Calendar.getInstance(); c.time = it.date
            it.categoryName == "Dépense" && c.get(Calendar.MONTH) == tM && c.get(Calendar.YEAR) == tY
        }
    }
    val monthLabel = remember(monthlySummaryOffset) {
        val cal = Calendar.getInstance(); cal.add(Calendar.MONTH, monthlySummaryOffset)
        java.text.SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time).uppercase()
    }

    // --- VISUEL ---
    val scrollState = rememberScrollState()
    val vignetteBrush = Brush.radialGradient(
        colors = listOf(Color(0xFF0F260C), DarkBackground), // Fond vert très sombre
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
            Text("ANALYSE DÉPENSES", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        // SÉLECTEURS
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ExpenseWeekSelectorRow("SEMAINE ANALYSÉE (A)", formatWeekLabelLocal(startCurrent, endCurrent), NeonExpense, { currentWeekOffset-- }, { currentWeekOffset++ })
            ExpenseWeekSelectorRow("COMPARÉE AVEC (B)", formatWeekLabelLocal(startGhost, endGhost), GhostExpense, { ghostWeekOffset-- }, { ghostWeekOffset++ })
        }

        // 1. DUEL TOTAL
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ExpenseStatCard(
                title = "TOTAL A",
                value = curTotal,
                subLabel = "Moy. ${curDailyAvg.toInt()}€ /j",
                maxValue = maxGaugeValue,
                color = NeonExpense,
                animFactor = factorA,
                modifier = Modifier.weight(1f)
            )
            ExpenseStatCard(
                title = "TOTAL B",
                value = prevTotal,
                subLabel = "Moy. ${prevDailyAvg.toInt()}€ /j",
                maxValue = maxGaugeValue,
                color = GhostExpense,
                animFactor = factorB,
                modifier = Modifier.weight(1f)
            )
        }

        // 2. GRAPHIQUE BARRES
        ExpenseBarChart(
            currentEntries = currentData,
            ghostEntries = ghostData,
            weekStartCurrent = startCurrent,
            weekStartGhost = startGhost,
            animFactorA = factorA,
            animFactorB = factorB
        )

        // 3. DÉTAILS PAR CATÉGORIE
        ExpenseBreakdownCard(
            currentEntries = currentData,
            ghostEntries = ghostData,
            subCategoryViewModel = subCategoryViewModel,
            animFactorA = factorA,
            animFactorB = factorB
        )

        // 4. BILAN MENSUEL
        ExpenseMonthlySummaryCard(
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
// COMPOSANTS UI
// --------------------------------------------------------------------------------

@Composable
fun ExpenseStatCard(
    title: String,
    value: Float,
    subLabel: String,
    maxValue: Float,
    color: Color,
    animFactor: Float,
    modifier: Modifier
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
            // Label en haut
            Box(modifier = Modifier.align(Alignment.TopCenter).background(color.copy(alpha = 0.2f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(text = title, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            // Jauge
            Canvas(modifier = Modifier.fillMaxWidth().height(90.dp).align(Alignment.Center).offset(y = -10.dp)) {
                val strokeWidth = 8.dp.toPx()
                val radius = size.minDimension / 2
                val center = Offset(size.width / 2, size.height)
                drawArc(color = Color.DarkGray.copy(alpha = 0.3f), startAngle = 180f, sweepAngle = 180f, useCenter = false, topLeft = Offset(center.x - radius, center.y - radius), size = Size(radius * 2, radius * 2), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                val progress = (value / maxValue).coerceIn(0f, 1f) * animFactor
                drawArc(color = color, startAngle = 180f, sweepAngle = 180f * progress, useCenter = false, topLeft = Offset(center.x - radius, center.y - radius), size = Size(radius * 2, radius * 2), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
            }
            // Valeurs
            Column(modifier = Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "${value.toInt()} €", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(text = subLabel, color = TextGray, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun ExpenseBarChart(
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
            if (idx in 0..6) days[idx] += (e.depensePrice ?: 0.0).toFloat()
        }
        return days.toList()
    }

    val curDays = remember(currentEntries) { mapToDays(currentEntries, weekStartCurrent) }
    val ghostDays = remember(ghostEntries) { mapToDays(ghostEntries, weekStartGhost) }
    val maxVal = max(curDays.maxOrNull() ?: 10f, ghostDays.maxOrNull() ?: 10f).coerceAtLeast(50f) * 1.1f
    val labels = listOf("L", "M", "M", "J", "V", "S", "D")

    var selectedPoint by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth().height(240.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, NeonExpense.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp)) {
            // LÉGENDE DYNAMIQUE
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("DÉPENSES JOURNALIÈRES (€)", color = TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                if (selectedPoint != null) {
                    val (idx, isGhost) = selectedPoint!!
                    val value = if(isGhost) ghostDays[idx] else curDays[idx]
                    val color = if(isGhost) GhostExpense else NeonExpense
                    val label = if(isGhost) "Passé" else "Actuel"
                    Text("$label : ${value.toInt()}€", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(NeonExpense, CircleShape)); Spacer(Modifier.width(4.dp))
                        Text("Actuel", color = TextGray, fontSize = 9.sp); Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(8.dp).background(GhostExpense, CircleShape)); Spacer(Modifier.width(4.dp))
                        Text("Passé", color = TextGray, fontSize = 9.sp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // ZONE TACTILE
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = { offset ->
                            val barSpace = size.width / 7
                            val index = (offset.x / barSpace).toInt().coerceIn(0, 6)

                            val chartHeight = size.height - 20.dp.toPx()

                            // Hauteur des barres
                            val hA = (curDays[index] / maxVal * chartHeight)
                            val hB = (ghostDays[index] / maxVal * chartHeight)

                            // Position Y du haut des barres
                            val yA = chartHeight - hA
                            val yB = chartHeight - hB

                            // Distance entre le doigt et le haut de chaque barre
                            val distA = kotlin.math.abs(offset.y - yA)
                            val distB = kotlin.math.abs(offset.y - yB)

                            // Logique de sélection intelligente :
                            // Si les deux existent, on prend la plus proche du doigt.
                            // Sinon on prend celle qui existe.
                            if (curDays[index] > 0 && ghostDays[index] > 0) {
                                selectedPoint = index to (distB < distA)
                            } else if (curDays[index] > 0) {
                                selectedPoint = index to false
                            } else if (ghostDays[index] > 0) {
                                selectedPoint = index to true
                            }

                            tryAwaitRelease()
                            selectedPoint = null
                        })
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val barSpace = size.width / 7
                    val barWidth = 12.dp.toPx()
                    val chartHeight = size.height - 20.dp.toPx()

                    listOf(0f, 0.5f, 1f).forEach { r ->
                        val y = chartHeight * (1 - r)
                        drawLine(Color.White.copy(alpha = 0.05f), Offset(0f, y), Offset(size.width, y))
                    }

                    for (i in 0..6) {
                        val x = i * barSpace + (barSpace / 2)

                        // Ghost (Gris)
                        val hB = (ghostDays[i] / maxVal * chartHeight) * animFactorB
                        if (hB > 0) {
                            drawRoundRect(
                                color = GhostExpense.copy(alpha = 0.4f),
                                topLeft = Offset(x + 4.dp.toPx(), chartHeight - hB),
                                size = Size(barWidth, hB),
                                cornerRadius = CornerRadius(4.dp.toPx())
                            )
                        }

                        // Actuel (Vert)
                        val hA = (curDays[i] / maxVal * chartHeight) * animFactorA
                        if (hA > 0) {
                            drawRoundRect(
                                color = NeonExpense,
                                topLeft = Offset(x - 4.dp.toPx(), chartHeight - hA),
                                size = Size(barWidth, hA),
                                cornerRadius = CornerRadius(4.dp.toPx())
                            )
                        }

                        // Labels
                        drawContext.canvas.nativeCanvas.drawText(
                            labels[i], x, size.height,
                            android.graphics.Paint().apply {
                                color = android.graphics.Color.GRAY; textSize = 10.sp.toPx(); textAlign = android.graphics.Paint.Align.CENTER
                            }
                        )
                    }

                    // TOOLTIP
                    if (selectedPoint != null) {
                        val (idx, isGhost) = selectedPoint!!
                        val value = if(isGhost) ghostDays[idx] else curDays[idx]
                        val color = if(isGhost) GhostExpense else NeonExpense

                        // On positionne la tooltip au dessus de la barre concernée
                        val offsetX = if(isGhost) 4.dp.toPx() else -4.dp.toPx()
                        val x = idx * barSpace + (barSpace / 2) + offsetX
                        val h = (value / maxVal * chartHeight) * (if(isGhost) animFactorB else animFactorA)
                        val y = chartHeight - h

                        if (value > 0) {
                            drawCircle(Color.White, 3.dp.toPx(), Offset(x + barWidth/2, y))

                            val label = "${value.toInt()}€"
                            val paintTooltip = android.graphics.Paint().apply {
                                setColor(android.graphics.Color.WHITE)
                                textSize = 12.sp.toPx()
                                typeface = android.graphics.Typeface.DEFAULT_BOLD
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                            val textBounds = android.graphics.Rect()
                            paintTooltip.getTextBounds(label, 0, label.length, textBounds)
                            val tooltipW = textBounds.width() + 20f
                            val tooltipH = textBounds.height() + 15f
                            val tooltipY = y - 25.dp.toPx()

                            drawRoundRect(Color(0xFF151515), topLeft = Offset(x + barWidth/2 - tooltipW/2, tooltipY - tooltipH), size = Size(tooltipW, tooltipH), cornerRadius = CornerRadius(5f))
                            drawRoundRect(color, topLeft = Offset(x + barWidth/2 - tooltipW/2, tooltipY - tooltipH), size = Size(tooltipW, tooltipH), cornerRadius = CornerRadius(5f), style = Stroke(1.dp.toPx()))
                            drawContext.canvas.nativeCanvas.drawText(label, x + barWidth/2, tooltipY - tooltipH/2 + textBounds.height()/3, paintTooltip)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpenseBreakdownCard(
    currentEntries: List<JournalEntry>,
    ghostEntries: List<JournalEntry>,
    subCategoryViewModel: SubCategoryViewModel,
    animFactorA: Float,
    animFactorB: Float
) {
    val breakdownA = remember { mutableStateMapOf<String, Float>() }
    val breakdownB = remember { mutableStateMapOf<String, Float>() }
    val allCats = remember { mutableStateListOf<String>() }

    LaunchedEffect(currentEntries, ghostEntries) {
        breakdownA.clear(); breakdownB.clear(); allCats.clear()
        suspend fun fill(entries: List<JournalEntry>, map: MutableMap<String, Float>) {
            entries.groupBy { it.subCategoryId }.forEach { (id, list) ->
                val sum = list.sumOf { it.depensePrice ?: 0.0 }.toFloat()
                if (sum > 0) {
                    if (id != null) subCategoryViewModel.getSubCategoryName(id) { n -> val name = n ?: "Autre"; map[name] = (map[name]?:0f)+sum; if(!allCats.contains(name)) allCats.add(name) }
                    else { map["Autre"] = (map["Autre"]?:0f)+sum; if(!allCats.contains("Autre")) allCats.add("Autre") }
                }
            }
        }
        fill(currentEntries, breakdownA)
        fill(ghostEntries, breakdownB)
    }

    val sortedCats = allCats.sortedByDescending { max(breakdownA[it]?:0f, breakdownB[it]?:0f) }.take(5)
    val maxRowVal = sortedCats.maxOfOrNull { max(breakdownA[it]?:0f, breakdownB[it]?:0f) } ?: 1f

    Card(
        modifier = Modifier.fillMaxWidth().shadow(15.dp, RoundedCornerShape(16.dp), spotColor = NeonExpense, ambientColor = NeonExpense),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, NeonExpense.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("DÉTAIL PAR CATÉGORIE", color = TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(16.dp))

            if (sortedCats.isEmpty()) {
                Text("Aucune dépense", color = TextGray, fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            } else {
                sortedCats.forEach { name ->
                    val valA = breakdownA[name] ?: 0f
                    val valB = breakdownB[name] ?: 0f

                    Column(Modifier.padding(vertical = 6.dp)) {
                        // EN-TÊTE : Nom + Les deux montants
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                            Text(name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

                            // Affichage des deux valeurs
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${valA.toInt()}€", color = NeonExpense, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                if (valB > 0) {
                                    Spacer(Modifier.width(6.dp))
                                    Text("vs", color = TextGray, fontSize = 10.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text("${valB.toInt()}€", color = GhostExpense, fontSize = 12.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))

                        // Barre Actuelle (Verte)
                        if(valA > 0) {
                            Box(
                                Modifier.height(8.dp).fillMaxWidth().clip(RoundedCornerShape(4.dp))
                                    .background(Color.DarkGray.copy(alpha=0.3f))
                            ) {
                                Box(Modifier.fillMaxHeight().fillMaxWidth((valA/maxRowVal).coerceIn(0f, 1f) * animFactorA).background(NeonExpense))
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        // Barre Passée (Grise)
                        if(valB > 0) {
                            Box(Modifier.height(6.dp).fillMaxWidth().clip(RoundedCornerShape(3.dp))) {
                                Box(Modifier.fillMaxHeight().fillMaxWidth((valB/maxRowVal).coerceIn(0f, 1f) * animFactorB).background(GhostExpense))
                            }
                        }
                    }
                    Divider(color = Color.White.copy(alpha=0.05f), modifier = Modifier.padding(vertical=4.dp))
                }
            }
        }
    }
}

@Composable
fun ExpenseMonthlySummaryCard(
    entries: List<JournalEntry>,
    subCategoryViewModel: SubCategoryViewModel,
    month: String,
    animFactor: Float,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val total = entries.sumOf { it.depensePrice ?: 0.0 }.toFloat()

    // Calcul de la répartition par sous-catégorie -> MODIFICATION ICI (Ajout logique)
    val breakdown = remember { mutableStateMapOf<String, Float>() }
    LaunchedEffect(entries) {
        breakdown.clear()
        entries.groupBy { it.subCategoryId }.forEach { (id, list) ->
            val sum = list.sumOf { it.depensePrice ?: 0.0 }.toFloat()
            if (id != null) {
                subCategoryViewModel.getSubCategoryName(id) { name ->
                    val n = name ?: "Autre"
                    breakdown[n] = (breakdown[n] ?: 0f) + sum
                }
            } else {
                breakdown["Autre"] = (breakdown["Autre"] ?: 0f) + sum
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().shadow(15.dp, RoundedCornerShape(24.dp), spotColor = NeonExpense, ambientColor = NeonExpense),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(2.dp, NeonExpense.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(20.dp)) {
            // Navigation mois
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.ChevronLeft, null, tint = TextGray) }
                Text("BILAN : $month", color = TextGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onNext, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.ChevronRight, null, tint = TextGray) }
            }
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Jauge Circulaire (Gauche)
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(Color.DarkGray.copy(alpha=0.3f), style = Stroke(10.dp.toPx()))
                        // Cercle "budget" fictif (disons 1000€ pour l'exemple visuel)
                        val progress = (total / 1000f).coerceIn(0f, 1f) * animFactor
                        if (progress > 0) drawArc(NeonExpense, -90f, 360f*progress, false, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${total.toInt()}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Euros", color = TextGray, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.width(24.dp))

                // Infos Texte (Droite)
                Column(modifier = Modifier.alpha(animFactor)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EuroSymbol, null, tint = NeonExpense, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Total : ${total.toInt()}€", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ShoppingCart, null, tint = NeonExpense, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("${entries.size} transactions", color = TextGray, fontSize = 12.sp)
                    }

                    Divider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))

                    // Liste des sous-catégories (Top 3) -> MODIFICATION ICI
                    if (breakdown.isEmpty()) {
                        Text("Aucun détail", color = TextGray, fontSize = 11.sp)
                    } else {
                        breakdown.entries.sortedByDescending { it.value }.take(3).forEach { (name, amount) ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
                                Box(Modifier.size(4.dp).background(TextGray, CircleShape))
                                Spacer(Modifier.width(8.dp))
                                Text(text = "${amount.toInt()}€ $name", color = TextGray, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- UTILITAIRES LOCAUX ---
@Composable
fun ExpenseWeekSelectorRow(label: String, dateRange: String, color: Color, onPrev: () -> Unit, onNext: () -> Unit) {
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

fun getWeekRangeLocal(offsetWeeks: Int): Pair<Long, Long> {
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

fun formatWeekLabelLocal(start: Long, end: Long): String {
    val fmt = java.text.SimpleDateFormat("dd MMM", Locale.getDefault())
    return "${fmt.format(java.util.Date(start))} - ${fmt.format(java.util.Date(end))}"
}