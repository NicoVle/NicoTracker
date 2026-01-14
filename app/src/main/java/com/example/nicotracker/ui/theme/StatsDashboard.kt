package com.example.nicotracker

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.example.nicotracker.data.JournalEntry
import com.example.nicotracker.data.JournalEntryViewModel
import com.example.nicotracker.data.SubCategoryViewModel
import java.util.Calendar
import java.util.Date
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.ui.graphics.StrokeCap
import com.example.nicotracker.data.CategoryViewModel
import com.example.nicotracker.data.SubCategory
import java.util.Locale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// --- COULEURS PERSONNALISÉES (Style Néon) ---
val DarkBackground = Color(0xFF121212)
val CardBackgroundEmpty = Color(0xFF1E1E1E)
val TextGray = Color(0xFFAAAAAA)
val NeonOrange = Color(0xFFFF8C00)
val DeepOrange = Color(0xFFFF4500)
val GlowColor = Color(0xFFFFB74D)
val NeonGreen = Color(0xFF00E676)
val NeonRed = Color(0xFFFF1744)
val NeonCyan = Color(0xFF00E5FF)
val DeepBlue = Color(0xFF0D47A1)
val NeonPink = Color(0xFFFF007F) // Rose Bonbon
val NeonDollar = Color(0xFF76FF03) // Vert Électrique "Argent"

// --- COULEURS PRODUCTIVITÉ ---
val ProdViolet = Color(0xFFD500F9)      // App Nico Tracker
val ProdTurquoise = Color(0xFF00E5FF)   // Copywriting
val ProdBlue = Color(0xFF2979FF)        // Work
val ProdYellow = Color(0xFFFFD600)      // Formation (Jaune Néon)
val ProdWhite = Color(0xFFE0E0E0)       // Lecture (Blanc/Argent)
val ProdDefault = Color(0xFFAA00FF)

// --- COULEURS DÉFIS ---
val RankBronze = Color(0xFFCD7F32)
val RankSilver = Color(0xFFC0C0C0)
val RankGold = Color(0xFFFFD700)
val RankDiamond = Color(0xFFB9F2FF)


// Mapping des couleurs avec nettoyage des noms (.trim)
fun getProductivityColor(subCategoryName: String): Color {
    return when (subCategoryName.trim()) {
        "Application Nico Tracker", "Application NicoTracker" -> ProdViolet
        "Copywriting" -> ProdTurquoise
        "Work" -> ProdBlue
        "Formation" -> ProdYellow
        "Lecture" -> ProdWhite
        else -> ProdDefault
    }
}

// Fonction utilitaire pour parser "HH:MM" en minutes
fun parseDurationStringToMinutes(duration: String?): Int {
    if (duration.isNullOrBlank()) return 0
    return try {
        val parts = duration.split(":")
        (parts[0].toInt() * 60) + parts[1].toInt()
    } catch (e: Exception) { 0 }
}

// --- ÉCRAN PRINCIPAL DU DASHBOARD (ANIMATION PAGE TURN) ---
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun StatsDashboardScreen(
    journalEntryViewModel: JournalEntryViewModel,
    subCategoryViewModel: SubCategoryViewModel,
    categoryViewModel: CategoryViewModel,
    onNavigateToSport: () -> Unit,
    onNavigateToNutrition: () -> Unit,
    onNavigateToProductivity: () -> Unit,
    onNavigateToSleep: () -> Unit,
    onNavigateToScreenTime: () -> Unit,
    onNavigateToExpense: () -> Unit,
    onNavigateToMood: () -> Unit,
    onNavigateToSteps: () -> Unit,
    onToggleFullScreen: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val entries by journalEntryViewModel.allEntries.collectAsState(initial = emptyList())
    val categories by categoryViewModel.allCategories.collectAsState(initial = emptyList())
    var entryToEditFromArmory by remember { mutableStateOf<JournalEntry?>(null) }
    val allSubCategories by subCategoryViewModel.allSubCategories.collectAsState(initial = emptyList())


    // --- LOGIQUE DÉFIS (Nouveau) ---
    var showChallengeDialog by remember { mutableStateOf(false) }
    var showArmory by remember { mutableStateOf(false) }
    BackHandler(enabled = showArmory) {
        // 1. On ferme l'armurerie
        showArmory = false
        // 2. IMPORTANT : On force la réapparition de la barre de navigation
        onToggleFullScreen(false)
    }

    val bankEntries = remember(entries) {
        entries.filter {
            it.categoryName == "Défis" &&
                    it.challengeState == 0
        }
    }

    // --- GESTION DATE ---
    var selectedDate by remember { mutableStateOf(Date()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate.time)

    // Gestion Swipe & Animation
    var offsetX by remember { mutableFloatStateOf(0f) }
    var slideDirection by remember { mutableIntStateOf(0) }

    // --- PREP DONNÉES ---
    val targetCalendar = remember(selectedDate) {
        Calendar.getInstance().apply { time = selectedDate; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    }
    val realToday = remember { Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) } }
    val isToday = targetCalendar.get(Calendar.YEAR) == realToday.get(Calendar.YEAR) && targetCalendar.get(Calendar.DAY_OF_YEAR) == realToday.get(Calendar.DAY_OF_YEAR)

    // Titre de la date
    val titleText = if (isToday) "Aujourd'hui" else android.text.format.DateFormat.format("dd/MM/yyyy", selectedDate).toString()

    // --- FILTRES ET CALCULS ---
    val dailySportEntries = remember(entries, targetCalendar) {
        entries.filter { entry ->
            val c = Calendar.getInstance().apply { time = entry.date }
            c.get(Calendar.YEAR) == targetCalendar.get(Calendar.YEAR) &&
                    c.get(Calendar.DAY_OF_YEAR) == targetCalendar.get(Calendar.DAY_OF_YEAR) &&
                    entry.categoryName == "Sport"
        }
    }

    // Variable pour la carte sport
    val targetSportEntry = dailySportEntries.firstOrNull()

    // 1. On récupère l'entrée précise de la dernière séance (COMMENTAIRE CORRIGÉ)
    val lastValidSportEntry = remember(entries, targetCalendar) {
        entries.filter {
            it.categoryName == "Sport" &&
                    (it.sportIntensity ?: 0) >= 6 &&
                    it.date.time < targetCalendar.timeInMillis
        }.maxByOrNull { it.date }
    }

    // 2. Calcul du nombre de jours
    val daysSinceLastSession = remember(lastValidSportEntry, targetCalendar) {
        if (lastValidSportEntry != null) {
            val lastDate = Calendar.getInstance().apply {
                time = lastValidSportEntry.date
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val diffMillis = targetCalendar.timeInMillis - lastDate.timeInMillis
            (diffMillis / (1000 * 60 * 60 * 24)).toInt()
        } else null
    }

    // 3. Calcul du NOM du jour (AVEC LOCALE CORRIGÉE)
    val lastSessionDayName = remember(lastValidSportEntry) {
        if (lastValidSportEntry != null) {
            val fmt = java.text.SimpleDateFormat("EEEE", Locale.FRANCE) // Locale.FRANCE sans parenthèses
            fmt.format(lastValidSportEntry.date).replaceFirstChar { it.uppercase() }
        } else null
    }

    val subCategoryNameSport = remember { mutableStateOf("") }
    LaunchedEffect(targetSportEntry) {
        if (targetSportEntry?.subCategoryId != null) {
            subCategoryViewModel.getSubCategoryName(targetSportEntry.subCategoryId!!) { name -> subCategoryNameSport.value = name ?: "" }
        } else {
            subCategoryNameSport.value = ""
        }
    }

    val targetEntriesFull = remember(entries, targetCalendar) { entries.filter { entry -> val c = Calendar.getInstance().apply { time = entry.date }; c.get(Calendar.YEAR) == targetCalendar.get(Calendar.YEAR) && c.get(Calendar.DAY_OF_YEAR) == targetCalendar.get(Calendar.DAY_OF_YEAR) } }
    val dailySleepEntries = remember(entries, targetCalendar) { entries.filter { entry -> val c = Calendar.getInstance().apply { time = entry.date }; c.get(Calendar.YEAR) == targetCalendar.get(Calendar.YEAR) && c.get(Calendar.DAY_OF_YEAR) == targetCalendar.get(Calendar.DAY_OF_YEAR) && entry.categoryName == "Sommeil" } }
    val totalSleepMinutes = remember(dailySleepEntries) { dailySleepEntries.sumOf { parseDurationStringToMinutes(it.sleepDuration) } }
    val mainSleepSession = remember(dailySleepEntries) { dailySleepEntries.maxByOrNull { parseDurationStringToMinutes(it.sleepDuration) } }
    val displayBedTime = mainSleepSession?.sleepBedTime ?: "--:--"
    val displayWakeTime = mainSleepSession?.sleepWakeTime ?: "--:--"



    // --- UI ---
    val scrollState = rememberScrollState()

    // --- BOÎTE RACINE POUR PERMETTRE LES OVERLAYS (Armurerie) ---
    Box(modifier = modifier.fillMaxSize().background(DarkBackground)) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val c = Calendar.getInstance()
                            c.time = selectedDate
                            if (offsetX > 50) { // Swipe Droite -> Jour Précédent
                                slideDirection = 1
                                c.add(Calendar.DAY_OF_YEAR, -1)
                                selectedDate = c.time
                            } else if (offsetX < -50) { // Swipe Gauche -> Jour Suivant
                                slideDirection = -1
                                c.add(Calendar.DAY_OF_YEAR, 1)
                                selectedDate = c.time
                            }
                            offsetX = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount
                        }
                    )
                }
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // HEADER
            Row(
                modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = titleText, style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Icon(imageVector = Icons.Default.DateRange, contentDescription = null, tint = NeonOrange, modifier = Modifier.size(28.dp))
            }

            // --- CONTENU ANIMÉ ---
            AnimatedContent(
                targetState = selectedDate,
                transitionSpec = {
                    if (slideDirection < 0) {
                        slideInHorizontally { width: Int -> width } + fadeIn() togetherWith
                                slideOutHorizontally { width: Int -> -width } + fadeOut()
                    } else {
                        slideInHorizontally { width: Int -> -width } + fadeIn() togetherWith
                                slideOutHorizontally { width: Int -> width } + fadeOut()
                    }
                },
                label = "DateAnimation"
            ) { targetDate ->
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                    // 1. SPORT
                    SportCard(
                        entry = targetSportEntry,
                        subCategoryName = subCategoryNameSport.value,
                        daysSinceLastSession = daysSinceLastSession,
                        lastSessionDayName = lastSessionDayName,
                        onClick = onNavigateToSport
                    )
                    // 2. NUTRITION + PRODUCTIVITÉ
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        NutritionCard(
                            entries = targetEntriesFull,
                            goalKcal = 2100,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onNavigateToNutrition() }
                        )
                        ProductivityTowerCard(
                            entries = targetEntriesFull,
                            subCategoryViewModel = subCategoryViewModel,
                            goalHours = 6,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onNavigateToProductivity() }
                        )
                    }

                    // 3. SOMMEIL + ECRAN
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        SleepCardDirect(
                            totalMinutes = totalSleepMinutes,
                            bedTime = displayBedTime,
                            wakeTime = displayWakeTime,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onNavigateToSleep() }
                        )

                        ScreenTimeRadarCard(
                            entries = targetEntriesFull,
                            subCategoryViewModel = subCategoryViewModel,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onNavigateToScreenTime() }
                        )
                    }

                    // 4. DÉPENSES
                    ExpensesCard(
                        entries = entries,
                        selectedDate = targetDate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToExpense() }
                    )

                    // 5. HUMEUR + PAS
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MoodCard(
                            entries = entries,
                            selectedDate = targetDate,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onNavigateToMood() }
                        )
                        StepsCard(
                            entries = targetEntriesFull,
                            goalSteps = 10000,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onNavigateToSteps() }
                        )
                    }

                    // 6. DÉFIS (MODIFIÉ POUR LE CLIC)
                    ChallengeCard(
                        entries = entries,
                        subCategories = allSubCategories, // <--- AJOUTEZ CETTE LIGNE
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showChallengeDialog = true }
                    )

                    Spacer(Modifier.height(50.dp))
                }
            }
        }

        // --- OVERLAYS ET DIALOGUES (DANS LA BOX) ---

        // Le Sélecteur Tactique
        if (showChallengeDialog) {
            ChallengeSelectionDialog(
                onDismiss = { showChallengeDialog = false },
                onHistoryClick = {
                    showChallengeDialog = false
                    // Rien pour l'instant
                },
                onBankClick = {
                    showArmory = true          // L'animation commence (avec le faux menu visible)
                    showChallengeDialog = false // Le vrai menu disparaît instantanément
                    onToggleFullScreen(true)   // On cache la barre
                }
            )
        }

        // --- GESTION DE L'ARMURERIE AVEC ANIMATION ---

        // On enveloppe le tout dans le HolographicReveal
        // Note : On retire le "if (showArmory)" classique car l'anim gère sa propre visibilité/disparition
        HolographicReveal(
            isVisible = showArmory,
            onDismiss = {
                showArmory = false
                onToggleFullScreen(false)
            }
        ) {
            // Contenu de l'Overlay
            Box(modifier = Modifier.zIndex(20f).fillMaxSize()) {

                ChallengeArmoryScreen(
                    bankEntries = bankEntries,
                    subCategories = allSubCategories,

                    // --- 1. ACTIVATION SIMPLE (Pas de copie) ---
                    onActivate = { entry ->
                        // On prend l'entrée existante et on modifie juste son état et sa date
                        val activatedEntry = entry.copy(
                            date = java.util.Date(), // On la ramène à "maintenant" pour qu'elle soit en haut de liste
                            challengeState = 1       // On la passe en état "Actif" (Sort de la banque)
                        )
                        journalEntryViewModel.update(activatedEntry) // On met à jour la base de données

                        showArmory = false
                        onToggleFullScreen(false)
                    },

                    onCardClick = { entry ->
                        entryToEditFromArmory = entry
                    },

                    // --- 2. SUPPRESSION DÉFINITIVE (Pour ton nettoyage) ---
                    onDeleteConfirm = { entry ->
                        journalEntryViewModel.delete(entry)
                    },

                    onClose = {
                        showArmory = false
                        onToggleFullScreen(false)
                    }
                )
            }

            // Le Dialog d'édition doit être AU-DESSUS de l'animation holographique
            // (Mais techniquement il est dans le composant content(), donc ça va)
            if (entryToEditFromArmory != null) {
                EditEntryDialog(
                    entry = entryToEditFromArmory!!,
                    categories = categories,
                    subCategoryViewModel = subCategoryViewModel,
                    onConfirm = { editedEntry ->
                        journalEntryViewModel.update(editedEntry)
                        entryToEditFromArmory = null
                    },
                    onDismiss = { entryToEditFromArmory = null }
                )
            }
        }

            // AJOUT 4 : Si une entrée est sélectionnée pour édition, on ouvre le dialogue PAR DESSUS l'armurerie
            if (entryToEditFromArmory != null) {
                EditEntryDialog(
                    entry = entryToEditFromArmory!!,
                    categories = categories,
                    subCategoryViewModel = subCategoryViewModel,
                    onConfirm = { editedEntry ->
                        journalEntryViewModel.update(editedEntry)
                        entryToEditFromArmory = null
                    },
                    onDismiss = { entryToEditFromArmory = null }
                )
            }
        }

        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { selectedDate = Date(it) }; showDatePicker = false }) { Text("OK") } },
                dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Annuler") } }
            ) { DatePicker(state = datePickerState) }
        }
    } // Fin de la Box Racine


// -----------------------------------------------------------
//   NOUVEAUX COMPOSANTS UI POUR LES DÉFIS (STYLE HUD NÉON)
// -----------------------------------------------------------
private val StatsDialogBg = Color(0xEB121212)
private val StatsBorderGradient = Brush.horizontalGradient(listOf(NeonCyan, ProdViolet))
@Composable
fun ChallengeSelectionDialog(
    onDismiss: () -> Unit,
    onHistoryClick: () -> Unit,
    onBankClick: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .border(2.dp, StatsBorderGradient, RoundedCornerShape(24.dp)) // Bordure dégradée
                .shadow(15.dp, RoundedCornerShape(24.dp), spotColor = NeonCyan, ambientColor = ProdViolet),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = StatsDialogBg)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // TITRE STYLE "AJOUTER : SPORT"
                Text(
                    "INTERFACE DÉFIS",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )

                // Ligne de séparation dégradée sous le titre
                Box(
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .fillMaxWidth(0.4f)
                        .height(2.dp)
                        .background(StatsBorderGradient)
                )

                Spacer(Modifier.height(16.dp))

                // Bouton BANQUE (Cyan)
                BigTacticalButton(
                    text = "ARMURERIE (BANQUE)",
                    icon = Icons.Default.Inventory2,
                    mainColor = NeonCyan,
                    onClick = onBankClick
                )

                Spacer(Modifier.height(16.dp))

                // Bouton HISTORIQUE (Orange)
                BigTacticalButton(
                    text = "HISTORIQUE & STATS",
                    icon = Icons.Default.History,
                    mainColor = NeonOrange,
                    onClick = onHistoryClick
                )

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun BigTacticalButton(
    text: String,
    icon: ImageVector,
    mainColor: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(65.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = mainColor.copy(alpha = 0.10f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, mainColor.copy(alpha = 0.6f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            // Icône encerclée
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(mainColor.copy(alpha = 0.2f), androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = mainColor, modifier = Modifier.size(20.dp))
            }

            Spacer(Modifier.width(16.dp))

            Text(
                text = text,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.weight(1f))

            Icon(Icons.Default.ChevronRight, null, tint = mainColor.copy(alpha = 0.5f))
        }
    }
}


// --- STRUCTURE DE DONNÉES INTERNE POUR LES BARRES ---
data class TowerBarData(
    val color: Color,
    val fillPercentage: Float
)

// ... [LE RESTE DES FONCTIONS EXISTANTES : ProductivityTowerCard, SportCard, NutritionCard, etc. RESTENT EXACTEMENT PAREILLES QU'AVANT]
// Pour éviter de rendre le message trop long, je n'ai pas recopié toutes les fonctions utilitaires du bas (ProductivityTowerCard, SportCard, etc.)
// car elles ne changent pas. Assure-toi de les garder dans ton fichier après le bloc BigTacticalButton !

// --- COMPOSANT : CARTE PRODUCTIVITÉ ---
@Composable
fun ProductivityTowerCard(
    entries: List<JournalEntry>,
    subCategoryViewModel: SubCategoryViewModel,
    goalHours: Int = 6,
    modifier: Modifier = Modifier
) {
    // ... (Ton code existant pour ProductivityTowerCard)
    // Recopie le contenu de ton ancien fichier à partir d'ici jusqu'à la fin
    // J'inclus juste le début pour que tu voies où coller le reste si besoin.
    val totalMinutesGlobal = remember(entries) {
        entries.filter { it.categoryName == "Action productive" }
            .mapNotNull { it.productiveDurationMinutes }.sum()
    }
    // ... suite du code ...
    val hours = totalMinutesGlobal / 60
    val mins = totalMinutesGlobal % 60
    val timeText = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"

    val isEmpty = totalMinutesGlobal == 0
    val cardBorderColor = if (isEmpty) Color.DarkGray else ProdViolet
    val shadowColor = if (isEmpty) Color.Transparent else ProdViolet
    val shadowElevation = if (isEmpty) 0.dp else 25.dp

    val barsData = remember { mutableStateListOf<TowerBarData>() }

    LaunchedEffect(entries) {
        barsData.clear()
        val grouped = entries
            .filter { it.categoryName == "Action productive" }
            .groupBy { it.subCategoryId }

        grouped.forEach { (subCatId, list) ->
            var duration = list.mapNotNull { it.productiveDurationMinutes }.sum()
            if (subCatId != null) {
                subCategoryViewModel.getSubCategoryName(subCatId) { name ->
                    if (name != null) {
                        val color = getProductivityColor(name)
                        var tempDuration = duration
                        while (tempDuration > 0) {
                            val amountForThisBar = minOf(tempDuration, 60)
                            val percentage = amountForThisBar / 60f
                            barsData.add(TowerBarData(color, percentage))
                            tempDuration -= amountForThisBar
                        }
                    }
                }
            } else {
                var tempDuration = duration
                while (tempDuration > 0) {
                    val amountForThisBar = minOf(tempDuration, 60)
                    val percentage = amountForThisBar / 60f
                    barsData.add(TowerBarData(ProdDefault, percentage))
                    tempDuration -= amountForThisBar
                }
            }
        }
    }

    Card(
        modifier = modifier
            .aspectRatio(1f)
            .shadow(shadowElevation, RoundedCornerShape(24.dp), spotColor = shadowColor, ambientColor = shadowColor),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(3.dp, cardBorderColor.copy(alpha = if (isEmpty) 1f else 1f)),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Column(
                modifier = Modifier.align(Alignment.TopStart).padding(top = 4.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(timeText, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 45.dp, bottom = 25.dp, end = 20.dp)
            ) {
                val maxSlots = maxOf(goalHours, barsData.size)
                val gap = 6.dp.toPx()
                val segmentHeight = size.height / maxSlots.toFloat()
                val barHeight = segmentHeight - gap
                val cornerRadius = CornerRadius.Zero

                repeat(maxSlots) { i ->
                    val topY = size.height - ((i + 1) * segmentHeight) + gap/2
                    drawRoundRect(
                        color = Color.DarkGray.copy(alpha = 0.4f),
                        topLeft = Offset(0f, topY),
                        size = Size(size.width, barHeight),
                        cornerRadius = cornerRadius,
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.3f),
                        topLeft = Offset(0f, topY),
                        size = Size(size.width, barHeight),
                        cornerRadius = cornerRadius,
                        style = Fill
                    )
                }

                if (barsData.isNotEmpty()) {
                    barsData.forEachIndexed { index, data ->
                        val topY = size.height - ((index + 1) * segmentHeight) + gap/2
                        val drawWidth = size.width * data.fillPercentage

                        drawIntoCanvas { canvas ->
                            val paint = android.graphics.Paint().apply {
                                color = data.color.toArgb()
                                maskFilter = android.graphics.BlurMaskFilter(25f, android.graphics.BlurMaskFilter.Blur.NORMAL)
                                alpha = 150
                            }
                            canvas.nativeCanvas.drawRect(0f, topY - 4f, drawWidth, topY + barHeight + 4f, paint)
                        }

                        val gradientBrush = Brush.horizontalGradient(
                            colors = listOf(
                                data.color.copy(alpha = 0.9f),
                                data.color,
                                data.color.copy(alpha = 0.9f)
                            ),
                            startX = 0f,
                            endX = drawWidth
                        )

                        drawRoundRect(brush = gradientBrush, topLeft = Offset(0f, topY), size = Size(drawWidth, barHeight), cornerRadius = cornerRadius, style = Fill)
                        drawRoundRect(color = Color.White.copy(alpha = 0.3f), topLeft = Offset(0f, topY), size = Size(drawWidth, barHeight), cornerRadius = cornerRadius, style = Stroke(width = 1.dp.toPx()))
                    }
                }
            }

            Text(
                text = "PRODUCTIVITÉ",
                color = TextGray,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.align(Alignment.BottomStart)
            )

            Text(
                text = "Obj. ${goalHours}h",
                color = TextGray,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
fun SportCard(
    entry: JournalEntry?,
    subCategoryName: String,
    daysSinceLastSession: Int?,
    lastSessionDayName: String? = null,
    onClick: () -> Unit
) {
    val intensity = entry?.sportIntensity ?: 0
    val duration = entry?.sportDurationMinutes ?: 0
    val isDone = entry != null && intensity >= 6

    // --- ANIMATION DE PULSATION (Pour l'alerte) ---
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f, // Descend jusqu'à 30% d'opacité
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing), // Vitesse du battement
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    // --- LOGIQUE DES BORDURES ET STYLES ---
    val days = daysSinceLastSession ?: 0

    // 1. Définition de la Bordure
    val borderStroke = when {
        isDone -> BorderStroke(3.dp, GlowColor.copy(alpha = 1f)) // Succès (Prioritaire)
        days >= 4 -> BorderStroke(3.dp, NeonRed.copy(alpha = pulseAlpha)) // Alerte Critique (Pulse)
        days == 3 -> BorderStroke(3.dp, NeonRed.copy(alpha = pulseAlpha)) // Alerte Haute (Pulse)
        days == 2 -> BorderStroke(3.dp, NeonRed) // Avertissement (Fixe)
        else -> null // < 2 jours (Rien)
    }

    // 2. Définition du Style du Texte "Il y a X jours"
    val subtitleColor = when {
        !isDone && days >= 4 -> NeonRed.copy(alpha = pulseAlpha) // Rouge Pulsant
        !isDone && days >= 2 -> NeonRed // Rouge Fixe dès 2 jours pour être cohérent avec la bordure
        else -> NeonOrange.copy(alpha = 0.8f) // Orange par défaut
    }

    val subtitleSize = if (!isDone && days >= 4) 20.sp else 15.sp // Texte plus gros si critique
    val subtitleWeight = if (!isDone && days >= 4) FontWeight.ExtraBold else FontWeight.Medium

    // 3. Fond et Ombre
    val backgroundBrush = if (isDone) {
        Brush.radialGradient(colors = listOf(NeonOrange, DeepOrange), radius = 600f)
    } else {
        SolidColor(CardBackgroundEmpty)
    }

    // Le glow (ombre portée) s'active aussi en rouge si critique
    val shadowColor = if (isDone) GlowColor else if (days >= 2) NeonRed else Color.Transparent
    val glowElevation = if (isDone) 40.dp else if (days >= 2) 15.dp else 0.dp

    val icon = when (subCategoryName) {
        "Crossfit" -> Icons.Filled.FitnessCenter
        "Marche" -> Icons.Filled.DirectionsWalk
        "Home training" -> Icons.Filled.Home
        "Divers" -> Icons.Filled.Bolt
        else -> Icons.Filled.FitnessCenter
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .shadow(glowElevation, RoundedCornerShape(24.dp), spotColor = shadowColor, ambientColor = shadowColor)
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        border = borderStroke,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(20.dp)
        ) {
            if (isDone) {
                // --- CAS : SÉANCE FAITE (Code inchangé) ---
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = subCategoryName.uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp
                        )
                        Row {
                            val flamesCount = if(intensity > 10) 5 else (intensity - 5)
                            repeat(flamesCount.coerceAtLeast(1)) {
                                Text("🔥", fontSize = 24.sp)
                            }
                        }
                    }

                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(75.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(text = "$duration min", color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            } else {
                // --- CAS : PAS DE SÉANCE (Avec la nouvelle logique d'alerte) ---
                val (titleText, subtitleText) = when (daysSinceLastSession) {
                    null -> "AUCUNE DONNÉE" to "C'est le moment de commencer !"
                    0 -> "Bravo !" to "Séance du jour terminée"
                    1 -> "DERNIÈRE SÉANCE : HIER" to "($lastSessionDayName) Garde le rythme !"
                    else -> "DERNIÈRE SÉANCE" to "Il y a $daysSinceLastSession jours ($lastSessionDayName)"
                }

                // Titre principal (reste gris ou devient rouge si très critique ?)
                // On le laisse gris pour garder la lisibilité, sauf si tu veux tout en rouge.
                val titleColor = if (days >= 4) NeonRed else TextGray.copy(alpha = 0.9f)

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // L'icône tremble ou change de couleur si critique ?
                    val iconTint = if (days >= 4) NeonRed.copy(alpha = pulseAlpha) else TextGray.copy(alpha = 0.2f)

                    Icon(
                        imageVector = Icons.Filled.FitnessCenter,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = titleText,
                        color = titleColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )

                    // C'est ici que la magie opère (Texte qui grossit et pulse)
                    Text(
                        text = subtitleText,
                        color = subtitleColor,
                        fontWeight = subtitleWeight,
                        fontSize = subtitleSize
                    )
                }
            }
        }
    }
}

@Composable
fun NutritionCard(
    entries: List<JournalEntry>,
    goalKcal: Int = 2100,
    modifier: Modifier = Modifier
) {
    val mealEntries = remember(entries) { entries.filter { it.categoryName == "Repas" } }
    val currentKcal = remember(mealEntries) { mealEntries.mapNotNull { it.mealCalories }.sum() }
    val totalProtein = remember(mealEntries) { mealEntries.mapNotNull { it.mealProtein }.sum() }
    val totalCarbs = remember(mealEntries) { mealEntries.mapNotNull { it.mealCarbs }.sum() }
    val totalLipids = remember(mealEntries) { mealEntries.mapNotNull { it.mealLipids }.sum() }

    val remainingKcal = (goalKcal - currentKcal).coerceAtLeast(0)
    val percentage = (currentKcal.toFloat() / goalKcal.toFloat()).coerceIn(0f, 1f)

    val mainColor = when {
        currentKcal == 0 -> Color.DarkGray
        currentKcal > goalKcal -> NeonRed
        else -> NeonGreen
    }
    val shadowColor = if (currentKcal == 0) Color.Transparent else mainColor
    val shadowElevation = if (currentKcal == 0) 0.dp else 15.dp

    Card(
        modifier = modifier
            .aspectRatio(1f)
            .shadow(shadowElevation, RoundedCornerShape(24.dp), spotColor = shadowColor, ambientColor = shadowColor),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(3.dp, mainColor),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            Text(
                text = "CALORIES",
                color = TextGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp)
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(80.dp).align(Alignment.Center).offset(y = 0.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawArc(color = Color.DarkGray.copy(alpha = 0.5f), startAngle = 0f, sweepAngle = 360f, useCenter = false, style = Stroke(width = 15f, cap = StrokeCap.Round))
                    if (percentage > 0) {
                        drawArc(color = mainColor, startAngle = -90f, sweepAngle = 360 * percentage, useCenter = false, style = Stroke(width = 15f, cap = StrokeCap.Round))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "$currentKcal", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(text = "kcal", color = TextGray, fontSize = 9.sp)
                }
            }

            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // UTILISATION DU NOUVEAU NOM DU COMPOSANT
                DashboardMacroText(label = "P", value = totalProtein, color = NeonGreen)
                DashboardMacroText(label = "G", value = totalCarbs, color = NeonCyan)
                DashboardMacroText(label = "L", value = totalLipids, color = NeonOrange)
            }

            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 4.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy((-2).dp)
            ) {
                Text(text = "Restant", color = TextGray, fontSize = 9.sp)
                Text(text = "$remainingKcal", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        }
    }
}

// COMPOSANT RENOMMÉ POUR ÉVITER LES CONFLITS AVEC CEUX DES AUTRES FICHIERS
@Composable
fun DashboardMacroText(label: String, value: Int, color: Color) {
    val compactStyle = androidx.compose.ui.text.TextStyle(platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false), lineHeight = 10.sp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "$label : ", color = color, fontWeight = FontWeight.Bold, fontSize = 10.sp, style = compactStyle)
        Text(text = "$value", color = TextGray, fontSize = 10.sp, fontWeight = FontWeight.Medium, style = compactStyle)
    }
}

@Composable
fun SleepCardDirect(totalMinutes: Int, bedTime: String, wakeTime: String, modifier: Modifier = Modifier) {
    val hasData = totalMinutes > 0
    val isGoodSleep = totalMinutes >= 8 * 60
    val mainColor = if (!hasData) Color.DarkGray else if (isGoodSleep) NeonCyan else NeonRed
    val glowColor = if (!hasData) Color.Transparent else mainColor
    val shadowElevation = if (!hasData) 0.dp else 20.dp
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    val displayTime = if (hasData) "%02dh %02d".format(h, m) else "--"

    Card(modifier = modifier.aspectRatio(1f).shadow(shadowElevation, RoundedCornerShape(24.dp), spotColor = glowColor, ambientColor = glowColor), shape = RoundedCornerShape(24.dp), border = BorderStroke(3.dp, mainColor.copy(alpha = if (!hasData) 1f else 0.8f)), colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty)) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.align(Alignment.TopStart).padding(top = 12.dp, start = 12.dp)) { Text(text = "SOMMEIL", color = TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp); Spacer(modifier = Modifier.height(2.dp)); Text(text = displayTime, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp) }
            if (hasData) {
                Canvas(modifier = Modifier.fillMaxWidth().height(60.dp).align(Alignment.Center).offset(y = 10.dp)) {
                    val wavePath = Path(); val width = size.width; val centerY = size.height / 2
                    val points = if (isGoodSleep) 100 else 15; val waveCount = if (isGoodSleep) 2 else 5; val stepX = width / points; val amplitude = if (isGoodSleep) 10.dp.toPx() else 15.dp.toPx()
                    wavePath.moveTo(0f, centerY)
                    for (i in 0..points) { val x = i * stepX; val progress = i.toFloat() / points; val angle = progress * (Math.PI * 2 * waveCount); var y = centerY + amplitude * sin(angle).toFloat(); if (!isGoodSleep) { y += Random.nextFloat() * 10f - 5f }; wavePath.lineTo(x, y) }
                    drawIntoCanvas { canvas -> val paint = android.graphics.Paint().apply { color = mainColor.toArgb(); style = android.graphics.Paint.Style.STROKE; strokeWidth = 6f; strokeCap = android.graphics.Paint.Cap.ROUND; isAntiAlias = true; maskFilter = android.graphics.BlurMaskFilter(15f, android.graphics.BlurMaskFilter.Blur.NORMAL) }; canvas.nativeCanvas.drawPath(wavePath.asAndroidPath(), paint) }
                    drawPath(path = wavePath, color = mainColor, style = Stroke(width = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                }
            } else { Canvas(modifier = Modifier.fillMaxWidth().height(60.dp).align(Alignment.Center).offset(y = 10.dp)) { drawLine(color = Color.DarkGray.copy(alpha = 0.5f), start = Offset(0f, size.height/2), end = Offset(size.width, size.height/2), strokeWidth = 1.dp.toPx()) } }
            Row(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.Start) { Text("Coucher", color = TextGray, fontSize = 8.sp); Text(bedTime, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                Column(horizontalAlignment = Alignment.End) { Text("Réveil", color = TextGray, fontSize = 8.sp); Text(wakeTime, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
            }
        }
    }
}

@Composable
fun ScreenTimeRadarCard(entries: List<JournalEntry>, subCategoryViewModel: SubCategoryViewModel, modifier: Modifier = Modifier) {
    val screenEntries = remember(entries) { entries.filter { it.categoryName == "Temps d'écran" } }
    val totalMinutes = remember(screenEntries) { screenEntries.mapNotNull { it.screenDurationMinutes }.sum() }
    val hours = totalMinutes / 60; val mins = totalMinutes % 60
    val timeText = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"

    // Petite fonction locale pour formater les durées des labels (ex: "1h 15m")
    fun fmt(m: Int): String {
        if (m == 0) return "" // On n'affiche rien si 0
        val h = m / 60
        val mn = m % 60
        return if (h > 0) "${h}h${mn}" else "${mn}m"
    }

    // --- VARIABLES D'ÉTAT POUR LES 4 AXES ---
    var socialMinutes by remember { mutableIntStateOf(0) }
    var gameMinutes by remember { mutableIntStateOf(0) }
    var movieMinutes by remember { mutableIntStateOf(0) }
    var youtubeMinutes by remember { mutableIntStateOf(0) }

    LaunchedEffect(screenEntries) {
        var s = 0; var g = 0; var m = 0; var y = 0
        val grouped = screenEntries.groupBy { it.subCategoryId }

        grouped.forEach { (subCatId, list) ->
            val duration = list.mapNotNull { it.screenDurationMinutes }.sum()
            if (subCatId != null) {
                subCategoryViewModel.getSubCategoryName(subCatId) { rawName ->
                    val name = rawName?.trim() ?: ""
                    when(name) {
                        "Réseaux Sociaux" -> s += duration
                        "Jeux Video", "Jeux Vidéo" -> g += duration
                        "Film" -> m += duration
                        "Youtube", "YouTube" -> y += duration
                    }
                    socialMinutes = s; gameMinutes = g; movieMinutes = m; youtubeMinutes = y
                }
            }
        }
    }

    val hasData = totalMinutes > 0
    val cardBorderColor = if (!hasData) Color.DarkGray else NeonPink
    val shadowColor = if (!hasData) Color.Transparent else NeonPink
    val shadowElevation = if (!hasData) 0.dp else 25.dp

    Card(
        modifier = modifier
            .aspectRatio(1f)
            .shadow(shadowElevation, RoundedCornerShape(24.dp), spotColor = shadowColor, ambientColor = shadowColor),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(3.dp, cardBorderColor.copy(alpha = if (!hasData) 1f else 1f)),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Titre Temps Total
            Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = timeText, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            }

            if (hasData) {
                Canvas(modifier = Modifier.fillMaxSize().padding(top = 30.dp, bottom = 20.dp, start = 20.dp, end = 20.dp)) {
                    val centerX = size.width / 2
                    val centerY = size.height / 2
                    val radius = size.width / 2.6f
                    val maxVal = maxOf(socialMinutes, gameMinutes, movieMinutes, youtubeMinutes, 60).toFloat()

                    fun getPoint(value: Int, angleDeg: Float): Offset {
                        val r = (value / maxVal) * radius
                        val angleRad = Math.toRadians(angleDeg.toDouble())
                        return Offset(x = centerX + (r * cos(angleRad)).toFloat(), y = centerY + (r * sin(angleRad)).toFloat())
                    }

                    val pSocial = getPoint(socialMinutes, -90f)   // Haut
                    val pGames = getPoint(gameMinutes, 0f)        // Droite
                    val pMovies = getPoint(movieMinutes, 90f)     // Bas
                    val pYoutube = getPoint(youtubeMinutes, 180f) // Gauche

                    val maxSocial = getPoint(maxVal.toInt(), -90f)
                    val maxGames = getPoint(maxVal.toInt(), 0f)
                    val maxMovies = getPoint(maxVal.toInt(), 90f)
                    val maxYoutube = getPoint(maxVal.toInt(), 180f)

                    val gridColor = Color.DarkGray.copy(alpha = 0.5f)

                    // GRILLE
                    for (i in 1..3) {
                        val ratio = i / 3f
                        val path = Path().apply {
                            moveTo(centerX + (maxSocial.x - centerX) * ratio, centerY + (maxSocial.y - centerY) * ratio)
                            lineTo(centerX + (maxGames.x - centerX) * ratio, centerY + (maxGames.y - centerY) * ratio)
                            lineTo(centerX + (maxMovies.x - centerX) * ratio, centerY + (maxMovies.y - centerY) * ratio)
                            lineTo(centerX + (maxYoutube.x - centerX) * ratio, centerY + (maxYoutube.y - centerY) * ratio)
                            close()
                        }
                        drawPath(path, gridColor, style = Stroke(width = 1.dp.toPx()))
                    }

                    // AXES
                    drawLine(gridColor, Offset(centerX, centerY), maxSocial, strokeWidth = 1.dp.toPx())
                    drawLine(gridColor, Offset(centerX, centerY), maxGames, strokeWidth = 1.dp.toPx())
                    drawLine(gridColor, Offset(centerX, centerY), maxMovies, strokeWidth = 1.dp.toPx())
                    drawLine(gridColor, Offset(centerX, centerY), maxYoutube, strokeWidth = 1.dp.toPx())

                    // LOSANGE NEON
                    val dataPath = Path().apply {
                        moveTo(pSocial.x, pSocial.y)
                        lineTo(pGames.x, pGames.y)
                        lineTo(pMovies.x, pMovies.y)
                        lineTo(pYoutube.x, pYoutube.y)
                        close()
                    }
                    drawIntoCanvas { canvas ->
                        val paint = android.graphics.Paint().apply {
                            color = NeonPink.toArgb()
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 10f
                            strokeCap = android.graphics.Paint.Cap.ROUND
                            isAntiAlias = true
                            maskFilter = android.graphics.BlurMaskFilter(25f, android.graphics.BlurMaskFilter.Blur.NORMAL)
                        }
                        canvas.nativeCanvas.drawPath(dataPath.asAndroidPath(), paint)
                    }
                    drawPath(dataPath, NeonPink.copy(alpha = 0.4f), style = Fill)
                    drawPath(dataPath, NeonPink, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))

                    // Points blancs
                    listOf(pSocial, pGames, pMovies, pYoutube).forEach {
                        drawCircle(Color.White, radius = 2.dp.toPx(), center = it)
                    }
                }

                // --- LABELS PERSONNALISÉS (ICI SONT TES MODIFICATIONS) ---
                Box(Modifier.fillMaxSize()) {

                    // 1. Social (Haut) -> À Droite (Row)
                    Row(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 35.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Social", color = TextGray, fontSize = 8.sp)
                        if (socialMinutes > 0) {
                            Spacer(Modifier.width(4.dp))
                            Text(fmt(socialMinutes), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // 2. Jeux (Droite) -> En dessous centré (Column)
                    Column(
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Jeux", color = TextGray, fontSize = 8.sp)
                        if (gameMinutes > 0) {
                            Text(fmt(gameMinutes), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // 3. Film (Bas) -> À Droite (Row)
                    Row(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 25.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Film", color = TextGray, fontSize = 8.sp)
                        if (movieMinutes > 0) {
                            Spacer(Modifier.width(4.dp))
                            Text(fmt(movieMinutes), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // 4. YouTube (Gauche) -> En dessous centré (Column)
                    Column(
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("YouTube", color = TextGray, fontSize = 8.sp)
                        if (youtubeMinutes > 0) {
                            Text(fmt(youtubeMinutes), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Smartphone, contentDescription = null, tint = Color.DarkGray.copy(alpha = 0.3f), modifier = Modifier.size(40.dp))
                }
            }
            Text(text = "TEMPS D'ÉCRAN", color = TextGray, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
        }
    }
}

@Composable
fun ExpensesCard(
    entries: List<JournalEntry>,
    selectedDate: Date,
    modifier: Modifier = Modifier
) {
    val weeklyData = remember(entries, selectedDate) {
        val calendar = Calendar.getInstance()
        calendar.time = selectedDate
        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
        val currentDay = calendar.get(Calendar.DAY_OF_WEEK)
        val daysToGoBack = if (currentDay == Calendar.SUNDAY) 6 else (currentDay - Calendar.MONDAY)
        calendar.add(Calendar.DAY_OF_YEAR, -daysToGoBack)
        val startOfWeek = calendar.timeInMillis
        val endOfWeek = startOfWeek + (7L * 24 * 60 * 60 * 1000)
        val weekEntries = entries.filter { it.categoryName == "Dépense" && it.date.time >= startOfWeek && it.date.time < endOfWeek }
        val days = FloatArray(7) { 0f }
        var total = 0.0
        weekEntries.forEach { entry ->
            val diffMs = entry.date.time - startOfWeek
            val dayIndex = (diffMs / (1000 * 60 * 60 * 24)).toInt()
            if (dayIndex in 0..6) { val amount = entry.depensePrice?.toFloat() ?: 0f; days[dayIndex] += amount; total += amount }
        }
        Pair(days, total)
    }

    val dailyAmounts = weeklyData.first
    val totalExpense = weeklyData.second
    val hasData = totalExpense > 0.0
    val cardBorderColor = if (!hasData) Color.DarkGray else NeonDollar
    val shadowColor = if (!hasData) Color.Transparent else NeonDollar
    val shadowElevation = if (!hasData) 0.dp else 20.dp
    val daysLabels = listOf("L", "M", "M", "J", "V", "S", "D")

    Card(
        modifier = modifier.fillMaxWidth().height(115.dp).shadow(shadowElevation, RoundedCornerShape(24.dp), spotColor = shadowColor, ambientColor = shadowColor),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(3.dp, cardBorderColor.copy(alpha = if (!hasData) 1f else 1f)),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "DÉPENSES", color = TextGray, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp)
                Text(text = "Total: ${totalExpense.toInt()} €", color = if(hasData) Color.White else TextGray, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                val barCount = 7; val spacing = 35.dp.toPx(); val barWidth = (size.width - (spacing * (barCount - 1))) / barCount
                val barRadius = CornerRadius(barWidth / 2); val topMargin = 25.dp.toPx(); val bottomLabelMargin = 20.dp.toPx()
                val baseLineY = size.height - bottomLabelMargin; val maxBarHeight = baseLineY - topMargin
                for (i in 0 until barCount) {
                    val x = i * (barWidth + spacing)
                    val amount = dailyAmounts[i]
                    val fillRatio = (amount / 60f).coerceIn(0f, 1f)
                    val fillHeight = maxBarHeight * fillRatio
                    drawRoundRect(color = Color.DarkGray.copy(alpha = 0.3f), topLeft = Offset(x, topMargin), size = Size(barWidth, maxBarHeight), cornerRadius = barRadius, style = Stroke(width = 1.dp.toPx()))
                    if (fillRatio > 0) {
                        val topY = baseLineY - fillHeight
                        drawIntoCanvas { canvas -> val paint = android.graphics.Paint().apply { color = NeonDollar.toArgb(); maskFilter = android.graphics.BlurMaskFilter(15f, android.graphics.BlurMaskFilter.Blur.NORMAL); alpha = 150 }; val rect = android.graphics.RectF(x, topY, x + barWidth, baseLineY); canvas.nativeCanvas.drawRoundRect(rect, barWidth/2, barWidth/2, paint) }
                        drawRoundRect(color = NeonDollar, topLeft = Offset(x, topY), size = Size(barWidth, fillHeight), cornerRadius = barRadius, style = Fill)
                    }
                    val textPaint = android.graphics.Paint().apply { color = TextGray.toArgb(); textSize = 30f; textAlign = android.graphics.Paint.Align.CENTER; isFakeBoldText = true }
                    drawContext.canvas.nativeCanvas.drawText(daysLabels[i], x + barWidth / 2, size.height - 5f, textPaint)
                }
            }
        }
    }
}

@Composable
fun StepsCard(entries: List<JournalEntry>, goalSteps: Int = 10000, modifier: Modifier = Modifier) {
    val totalSteps = remember(entries) { entries.filter { it.categoryName == "Nombre de pas" }.mapNotNull { it.stepsCount }.sum() }
    val hasData = totalSteps > 0; val progressColor = NeonCyan; val cardBorderColor = if (!hasData) Color.DarkGray else progressColor
    val shadowColor = if (!hasData) Color.Transparent else progressColor; val shadowElevation = if (!hasData) 0.dp else 20.dp
    val percentage = (totalSteps.toFloat() / goalSteps.toFloat()).coerceIn(0f, 1f)

    Card(modifier = modifier.aspectRatio(1f).shadow(shadowElevation, RoundedCornerShape(24.dp), spotColor = shadowColor, ambientColor = shadowColor), shape = RoundedCornerShape(24.dp), border = BorderStroke(3.dp, cardBorderColor.copy(alpha = if (!hasData) 1f else 1f)), colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty)) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(text = "PAS", color = TextGray, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.align(Alignment.TopStart).padding(12.dp))
            Canvas(modifier = Modifier.fillMaxSize().padding(20.dp).align(Alignment.Center)) {
                val center = Offset(size.width / 2, size.height / 2); val radius = (size.width / 2) - 5.dp.toPx()
                val segmentCount = 40; val segmentLength = 15f; val angleStep = 360f / segmentCount
                for (i in 0 until segmentCount) {
                    val angleDeg = -90f + (i * angleStep); val angleRad = Math.toRadians(angleDeg.toDouble())
                    val startX = center.x + (radius * cos(angleRad)).toFloat(); val startY = center.y + (radius * sin(angleRad)).toFloat()
                    val endX = center.x + ((radius - segmentLength) * cos(angleRad)).toFloat(); val endY = center.y + ((radius - segmentLength) * sin(angleRad)).toFloat()
                    val isActive = (i.toFloat() / segmentCount) < percentage
                    val segColor = if (isActive && hasData) progressColor else Color.DarkGray.copy(alpha = 0.3f); val segWidth = if (isActive && hasData) 8f else 5f
                    if (isActive && hasData) { drawIntoCanvas { canvas -> val paint = android.graphics.Paint().apply { color = segColor.toArgb(); strokeWidth = segWidth + 10f; strokeCap = android.graphics.Paint.Cap.ROUND; maskFilter = android.graphics.BlurMaskFilter(15f, android.graphics.BlurMaskFilter.Blur.NORMAL); alpha = 150 }; canvas.nativeCanvas.drawLine(startX, startY, endX, endY, paint) } }
                    drawLine(color = segColor, start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = segWidth, cap = StrokeCap.Round)
                }
                drawCircle(color = Color.DarkGray.copy(alpha = 0.2f), radius = radius - segmentLength - 10f, center = center, style = Stroke(width = 2f))
                if (hasData) { drawCircle(color = progressColor.copy(alpha = 0.1f), radius = radius - segmentLength - 5f, center = center) }
            }
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "$totalSteps", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                Text(text = "STEPS", color = if(hasData) progressColor else TextGray, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp)
            }
            Text(text = "${goalSteps/1000}k", color = TextGray, fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp))
        }
    }
}

@Composable
fun MoodCard(entries: List<JournalEntry>, selectedDate: Date, modifier: Modifier = Modifier) {

    // 1. On cherche d'abord la DATE de la dernière entrée pertinente (<= date sélectionnée)
    val targetDayDate = remember(entries, selectedDate) {
        val endOfSelectedDay = Calendar.getInstance().apply { time = selectedDate; set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }.timeInMillis

        entries
            .filter { it.categoryName == "Humeur" && it.moodScore != null && it.date.time <= endOfSelectedDay }
            .maxByOrNull { it.date }?.date // On récupère la date de l'entrée la plus récente
    }

    // 2. Si on a trouvé une date, on récupère TOUTES les entrées de CETTE journée pour faire la moyenne
    val averageScore = remember(entries, targetDayDate) {
        if (targetDayDate == null) 0f
        else {
            val cal = Calendar.getInstance().apply { time = targetDayDate }
            val day = cal.get(Calendar.DAY_OF_YEAR)
            val year = cal.get(Calendar.YEAR)

            // On filtre toutes les entrées qui correspondent exactement à ce jour-là
            val dailyEntries = entries.filter {
                val c = Calendar.getInstance().apply { time = it.date }
                it.categoryName == "Humeur" && it.moodScore != null &&
                        c.get(Calendar.DAY_OF_YEAR) == day && c.get(Calendar.YEAR) == year
            }

            val validScores = dailyEntries.mapNotNull { it.moodScore }
            if (validScores.isNotEmpty()) validScores.average().toFloat() else 0f
        }
    }

    // 3. Calcul du nombre de jours écoulés (pour afficher "Ce jour" ou "Il y a X jours")
    val daysAgo = remember(targetDayDate, selectedDate) {
        if (targetDayDate == null) null
        else {
            val current = Calendar.getInstance().apply { time = selectedDate; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
            val entryDate = Calendar.getInstance().apply { time = targetDayDate; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
            val diff = current.timeInMillis - entryDate.timeInMillis
            (diff / (1000 * 60 * 60 * 24)).toInt()
        }
    }

    // 4. Définition des couleurs et icônes basées sur la MOYENNE (Float)
    val moodColor = when {
        averageScore >= 8f -> NeonGreen
        averageScore >= 5f -> NeonOrange
        averageScore > 0f -> NeonRed
        else -> Color.DarkGray
    }

    val moodIcon = when {
        averageScore >= 8f -> Icons.Default.SentimentVerySatisfied
        averageScore >= 5f -> Icons.Default.SentimentNeutral
        averageScore > 0f -> Icons.Default.SentimentVeryDissatisfied
        else -> Icons.Default.Mood
    }

    // Gestion de l'affichage
    val isSameDay = daysAgo == 0
    val hasData = averageScore > 0f

    val contentAlpha = if (isSameDay) 1f else 0.3f
    val borderAlpha = if (isSameDay) 1f else 0.3f
    val shadowElevation = if (isSameDay && hasData) 20.dp else 0.dp
    val shadowColor = if (isSameDay && hasData) moodColor else Color.Transparent
    val iconSize = 90.dp

    Card(
        modifier = modifier
            .aspectRatio(1f)
            .shadow(shadowElevation, RoundedCornerShape(24.dp), spotColor = shadowColor, ambientColor = shadowColor),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(3.dp, if(hasData) moodColor.copy(alpha = borderAlpha) else Color.DarkGray),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "HUMEUR",
                color = TextGray,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
            )

            Box(modifier = Modifier.align(Alignment.Center)) {
                if (hasData) {
                    if (isSameDay) {
                        Icon(imageVector = moodIcon, contentDescription = null, tint = moodColor, modifier = Modifier.size(iconSize).blur(15.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded))
                    }
                    Icon(imageVector = moodIcon, contentDescription = null, tint = moodColor.copy(alpha = if (isSameDay) 0.9f else contentAlpha), modifier = Modifier.size(iconSize))
                } else {
                    Icon(imageVector = moodIcon, contentDescription = null, tint = TextGray.copy(alpha = 0.2f), modifier = Modifier.size(iconSize))
                }
            }

            if (hasData) {
                // AFFICHAGE AVEC DÉCIMALE (ex: 7.5 / 10)
                Text(
                    text = String.format(Locale.US, "%.1f/10", averageScore),
                    color = moodColor.copy(alpha = contentAlpha),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 12.dp)
                )

                val dateText = if (isSameDay) "Moy. ce jour" else "${daysAgo}j avant"
                Text(
                    text = dateText,
                    color = TextGray.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 12.dp)
                )
            } else {
                Text(text = "Aucune note", color = TextGray.copy(alpha = 0.5f), fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
            }
        }
    }
}

@Composable
fun ChallengeCard(
    entries: List<JournalEntry>,
    subCategories: List<SubCategory>, // <--- 1. On ajoute ce paramètre
    modifier: Modifier = Modifier
) {
    val lastChallengeEntry = remember(entries, subCategories) {
        entries.filter { entry ->
            // Condition 1 : C'est un défi
            val isChallenge = entry.categoryName == "Défis"

            // Condition 2 : Ce n'est pas une entrée de banque (état 0)
            val isNotBank = entry.challengeState != 0

            // Condition 3 : EXCLUSION DU PROTOCOLE
            // On cherche le nom de la sous-catégorie associée à l'entrée
            val subCatName = subCategories.find { it.id == entry.subCategoryId }?.name?.trim()
            // On garde l'entrée SEULEMENT si ce n'est PAS "Protocole d'intégrité"
            val isNotProtocol = subCatName != "Protocole d'intégrité"

            isChallenge && isNotBank && isNotProtocol
        }
            .maxByOrNull { it.date }
    }

    val hasData = lastChallengeEntry != null
    val score = lastChallengeEntry?.challengeSuccess ?: 0
    val title = lastChallengeEntry?.challengeTitle ?: "Aucun défi enregistré"

    val daysAgo = remember(lastChallengeEntry) {
        if (lastChallengeEntry == null) null
        else {
            val now = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val entryDate = Calendar.getInstance().apply {
                time = lastChallengeEntry.date
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val diff = now.timeInMillis - entryDate.timeInMillis
            (diff / (1000 * 60 * 60 * 24)).toInt()
        }
    }

    val isToday = daysAgo == 0
    val (rankColor, rankGlow) = when (score) {
        in 1..4 -> RankBronze to 0.dp
        in 5..7 -> RankSilver to 10.dp
        in 8..9 -> RankGold to 20.dp
        10 -> RankDiamond to 30.dp
        else -> Color.DarkGray to 0.dp
    }

    val borderAlpha = 1f
    val contentAlpha = 1f
    val cardBorderColor = if (!hasData) Color.DarkGray else rankColor
    val shadowColor = if (score >= 5) rankColor else Color.Transparent

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(115.dp)
            .shadow(rankGlow, RoundedCornerShape(24.dp), spotColor = shadowColor, ambientColor = shadowColor),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(3.dp, cardBorderColor.copy(alpha = if (!hasData) 1f else borderAlpha)),
        colors = CardDefaults.cardColors(containerColor = CardBackgroundEmpty)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.Start) {
                Text(text = "DÉFIS", color = TextGray, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(5.dp))
                Box(contentAlignment = Alignment.Center) {
                    if (hasData && score >= 5) {
                        Icon(imageVector = Icons.Default.EmojiEvents, contentDescription = null, tint = rankColor.copy(alpha = contentAlpha), modifier = Modifier.size(50.dp).blur(15.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded))
                    }
                    Icon(imageVector = Icons.Default.EmojiEvents, contentDescription = null, tint = if(hasData) rankColor.copy(alpha = contentAlpha) else TextGray.copy(alpha = 0.3f), modifier = Modifier.size(50.dp))
                }
            }
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.End,
                modifier = Modifier.weight(1f).padding(start = 20.dp)
            ) {
                if (hasData) {
                    Text(text = "$score/10", color = rankColor.copy(alpha = contentAlpha), fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                    Text(
                        text = title,
                        color = Color.White.copy(alpha = contentAlpha),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        lineHeight = 16.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    if (!isToday && daysAgo != null) {
                        Text(text = "Il y a $daysAgo jours", color = TextGray.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Light)
                    }
                } else {
                    Text(text = "--", color = TextGray, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    Text(text = "Aucun défi", color = TextGray.copy(alpha = 0.5f), fontSize = 12.sp)
                }
            }
        }
    }
}