package com.example.nicotracker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.nicotracker.data.*
import java.util.Calendar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.zIndex
import com.example.nicotracker.avatar.AvatarViewModel
import com.example.nicotracker.avatar.NeonYellow
import com.example.nicotracker.stats.NutritionScoringEngine
import java.text.Normalizer.normalize
import java.util.Map.entry
import kotlin.math.roundToInt

// --- COULEURS ET STYLES HUD ---
val HudBackground = Color(0xEB121212) // Noir à 92% d'opacité
val HudBorderGradient = Brush.horizontalGradient(listOf(NeonCyan, ProdViolet))

// -------------------------------------------------------------
//   NOUVEAUX COMPOSANTS "GAMIFIÉS"
// -------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class) // Pour FlowRow
@Composable
fun NeonChipSelector(
    label: String,
    options: List<SubCategory>,
    selectedId: Int?,
    onSelected: (Int?) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, color = TextGray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Option "Aucune" ou reset
            val isNoneSelected = selectedId == null
            FilterChip(
                selected = isNoneSelected,
                onClick = { onSelected(null) },
                label = { Text("Aucune") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color.DarkGray,
                    selectedLabelColor = Color.White,
                    containerColor = Color.Transparent,
                    labelColor = TextGray
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = if (isNoneSelected) Color.White else Color.DarkGray,
                    enabled = true, selected = isNoneSelected
                )
            )

            options.forEach { sub ->
                val isSelected = sub.id == selectedId
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelected(sub.id) },
                    label = { Text(sub.name) },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                        selectedLabelColor = NeonCyan,
                        containerColor = Color.Transparent,
                        labelColor = Color.White
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = if (isSelected) NeonCyan else Color.DarkGray,
                        enabled = true, selected = isSelected
                    )
                )
            }
        }
    }
}

@Composable
fun NeonSliderField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    isPositive: Boolean = true
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, color = TextGray, fontSize = 12.sp)
            Text(
                text = "$value/10",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        val activeColor = if (isPositive) {
            when (value) {
                in 0..4 -> NeonGreen
                in 5..7 -> NeonOrange
                else -> NeonRed
            }
        } else {
            when (value) {
                in 0..4 -> NeonRed
                in 5..7 -> NeonOrange
                else -> NeonGreen
            }
        }

        Slider(
            // On utilise roundToInt() ici pour éviter les sauts de chiffres
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9, // 9 étapes entre 0 et 10 donne bien 11 points (0,1,2,3,4,5,6,7,8,9,10)
            colors = SliderDefaults.colors(
                thumbColor = activeColor,
                activeTrackColor = activeColor,
                inactiveTrackColor = Color.DarkGray
            )
        )
    }
}


// -------------------------------------------------------------
//   NEON HUD DIALOG (GLASSMORPHISM)
// -------------------------------------------------------------
@Composable
fun NeonHudDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String = "Valider",
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp)
                .imePadding()
                .border(2.dp, HudBorderGradient, RoundedCornerShape(24.dp))
                .shadow(15.dp, RoundedCornerShape(24.dp), spotColor = NeonCyan, ambientColor = ProdViolet),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = HudBackground)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )

                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .fillMaxWidth(0.5f)
                        .height(2.dp)
                        .background(HudBorderGradient)
                )

                Box(modifier = Modifier.weight(1f, fill = false)) {
                    content()
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("ANNULER", color = TextGray, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .border(1.dp, NeonCyan, RoundedCornerShape(50))
                            .background(NeonCyan.copy(alpha = 0.1f), RoundedCornerShape(50))
                    ) {
                        Text(confirmLabel.uppercase(), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
//   COMPOSANT CARTE (INCHANGÉ)
// -------------------------------------------------------------
@Composable
fun StyledJournalEntryCard(
    entry: JournalEntry,
    subCategoryName: String?,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    // 1. DÉFINITION DE LA COULEUR DE BASE
    val accentColor = when (entry.categoryName) {
        "Sport" -> NeonOrange
        "Repas" -> NeonRed
        "Sommeil" -> DeepBlue
        "Action productive" -> ProdViolet
        "Temps d'écran" -> NeonPink
        "Nombre de pas" -> NeonCyan
        "Humeur" -> NeonGreen
        "Dépense" -> NeonDollar
        "Revenus" -> NeonGreen
        "Défis" -> Color(0xFFFFD700)
        else -> TextGray
    }

    // 2. DÉFINITION INTELLIGENTE DE L'ICÔNE (POSTURE)
    val isPostureCategory = entry.categoryName == "Action productive" || entry.categoryName == "Temps d'écran"

    val iconVector = if (isPostureCategory) {
        when {
            entry.tags?.contains("Debout", ignoreCase = true) == true -> Icons.Default.Accessibility
            entry.tags?.contains("Marche", ignoreCase = true) == true -> Icons.Default.DirectionsWalk
            entry.tags?.contains("Assis", ignoreCase = true) == true -> Icons.Default.EventSeat
            entry.categoryName == "Action productive" -> Icons.Default.TrackChanges
            else -> Icons.Default.Smartphone
        }
    } else {
        when (entry.categoryName) {
            "Sport" -> Icons.Default.FitnessCenter
            "Repas" -> Icons.Default.Restaurant
            "Sommeil" -> Icons.Default.Bed
            "Nombre de pas" -> Icons.Default.DirectionsWalk
            "Humeur" -> Icons.Default.Mood
            "Dépense" -> Icons.Default.Euro
            "Revenus" -> Icons.Default.AttachMoney
            "Défis" -> Icons.Default.EmojiEvents
            else -> Icons.Default.Circle
        }
    }

    // --- C'EST CE BLOC QUI MANQUAIT ---
    val mainDataText = when (entry.categoryName) {
        "Sport" -> entry.sportDurationMinutes?.let { "${it} min" }
        "Repas" -> entry.mealCalories?.let { "${it} kcal" }
        "Sommeil" -> entry.sleepDuration?.let { "${it}" } ?: "0h"
        "Action productive" -> {
            entry.productiveDurationMinutes?.let { formatDuration(it) } ?: ""
        }
        "Temps d'écran" -> entry.screenDurationMinutes?.let { formatDuration(it) }
        "Nombre de pas" -> entry.stepsCount?.let { "$it pas" }
        "Dépense" -> entry.depensePrice?.let { "-${it} €" }
        "Revenus" -> entry.incomeAmount?.let { "+${it} €" }
        "Humeur" -> entry.moodScore?.let { "$it/10" }
        else -> null
    }

    val title = subCategoryName ?: entry.categoryName
    // ----------------------------------

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .shadow(elevation = 12.dp, shape = RoundedCornerShape(20.dp), spotColor = accentColor, ambientColor = accentColor)
            .border(width = 2.dp, color = accentColor.copy(alpha = 0.6f), shape = RoundedCornerShape(20.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = iconVector, contentDescription = null, tint = accentColor, modifier = Modifier.size(28.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (mainDataText != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = mainDataText,
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1
                        )
                    }
                }

                if (subCategoryName != null && title == subCategoryName && entry.categoryName != "Action productive") {
                    Text(text = entry.categoryName, color = TextGray, fontSize = 12.sp)
                }

                if (entry.categoryName == "Repas" && (entry.mealProtein != null || entry.mealCarbs != null)) {
                    Text(text = "P:${entry.mealProtein ?: 0}  G:${entry.mealCarbs ?: 0}  L:${entry.mealLipids ?: 0}", color = TextGray, fontSize = 12.sp)
                }

                if (!entry.comment.isNullOrBlank()) {
                    Text(text = entry.comment, color = TextGray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            IconButton(onClick = onDelete) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Supprimer", tint = Color.DarkGray, modifier = Modifier.size(20.dp))
            }
        }
    }
}

fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h${"%02d".format(m)}" else "${m} min"
}

@Composable
fun TimerStyleDurationDialog(onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var hours by remember { mutableStateOf("00") }
    var minutes by remember { mutableStateOf("00") }
    var digits by remember { mutableStateOf("") }
    fun updateDigits(d: String) {
        val padded = d.padStart(4, '0')
        hours = padded.substring(0, 2); minutes = padded.substring(2, 4)
    }
    NeonHudDialog(title = "Durée", onDismiss = onDismiss, onConfirm = { onConfirm(hours.toInt() * 60 + minutes.toInt()) }, confirmLabel = "OK") {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text("$hours : $minutes", style = MaterialTheme.typography.displayMedium, color = NeonCyan)
            }
            Spacer(Modifier.height(20.dp))
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "←", "0", "")
            keys.chunked(3).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { key ->
                        if (key.isNotEmpty()) TextButton(onClick = { if (key == "←") { if (digits.isNotEmpty()) { digits = digits.dropLast(1); updateDigits(digits) } } else { if (digits.length < 4) { digits += key; updateDigits(digits) } } }) { Text(key, fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold) } else Spacer(Modifier.width(60.dp))
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------
//   TODAY SCREEN (FONCTION PRINCIPALE)
// -----------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    categoryViewModel: CategoryViewModel,
    journalEntryViewModel: JournalEntryViewModel,
    subCategoryViewModel: SubCategoryViewModel,
    avatarViewModel: AvatarViewModel,
    showAddDialog: Boolean,
    onDismissDialog: () -> Unit,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onNavigateToSettings: () -> Unit,
    pendingNutrition: MutableList<RawNutritionItem>
) {
    val categories by categoryViewModel.allCategories.collectAsState(initial = emptyList())
    val entries by journalEntryViewModel.allEntries.collectAsState(initial = emptyList())
    val allSubCats by subCategoryViewModel.allSubCategories.collectAsState(initial = emptyList())

    var entryToComplete by remember { mutableStateOf<JournalEntry?>(null) }
    var completionScore by remember { mutableIntStateOf(5) }

    // On garde juste les entrées du jour, pas de logique de Défi Actif ici
    val today = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0);
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
    }

    val todayEntries = remember(entries, allSubCats) { // <-- Ajoutez allSubCats ici
        entries.filter { entry ->
            val c = Calendar.getInstance().apply { time = entry.date }
            val isSameDay = c.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    c.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)

            val isHiddenBankChallenge = entry.categoryName == "Défis" && entry.challengeState == 0

            // --- NOUVELLE LOGIQUE D'EXCLUSION ---
            // On vérifie si c'est un Protocole d'intégrité
            val subName = allSubCats.find { it.id == entry.subCategoryId }?.name?.trim()
            val isProtocol = subName.equals("Protocole d'intégrité", ignoreCase = true)

            // Si c'est un Protocole ET qu'il est Terminé (2), on veut le cacher de la vue "Aujourd'hui"
            val isHiddenCompletedProtocol = isProtocol && entry.challengeState == 2

            // On l'affiche si c'est aujourd'hui, pas caché par la banque, ET pas un protocole fini
            val isTodayEntry = isSameDay && !isHiddenBankChallenge && !isHiddenCompletedProtocol

            // Condition 2 : C'est un défi EN COURS (peu importe la date), on l'affiche toujours
            val isActiveChallenge = entry.categoryName == "Défis" && entry.challengeState == 1

            isTodayEntry || isActiveChallenge
        }.sortedWith(compareByDescending<JournalEntry> {
            it.challengeState == 1
        }.thenByDescending {
            it.date
        })
    }

    var showDeleteDialog by remember { mutableStateOf<JournalEntry?>(null) }
    var entryToEdit by remember { mutableStateOf<JournalEntry?>(null) }

    Box(modifier = modifier.fillMaxSize()) {

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().background(Color(0xFF121212))
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween, // Pousse le titre à gauche et l'icône à droite
                    verticalAlignment = Alignment.CenterVertically    // Centre verticalement
                ) {
                    Text(
                        text = "Entrées du jour (${todayEntries.size})",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Paramètres",
                            tint = Color.DarkGray,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {

                    // Dans TodayScreen.kt, premier item de la LazyColumn
                    item {
                        NutritionInbox(
                            items = pendingNutrition,
                            onCommitMeal = { selectedItems, mealName ->
                                // Fusion des macros
                                val kcal = selectedItems.sumOf { it.calories }
                                val prot = selectedItems.sumOf { it.protein }
                                val carbs = selectedItems.sumOf { it.carbs }
                                val fat = selectedItems.sumOf { it.fat }

                                // Fusion des MICRO-NUTRIMENTS
                                val sugar = selectedItems.sumOf { it.sugar }
                                val sodium = selectedItems.sumOf { it.sodium }
                                val satFat = selectedItems.sumOf { it.saturatedFat }
                                val fibers = selectedItems.sumOf { it.fibers }

                                // Création du commentaire automatique
                                val foodNames = selectedItems.joinToString(" + ") { it.name }

                                // Calcul du score
                                val score = NutritionScoringEngine.computeScore(kcal, prot, fibers.toInt())

                                // --- LA FONCTION MAGIQUE EST ICI ---
                                fun normalize(s: String): String {
                                    return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                                        .replace(Regex("\\p{M}"), "")
                                }

                                // LA LISTE INTELLIGENTE DES SYNONYMES
                                // On définit que "Dîner" peut aussi être "Dinner" dans la base de données
                                val aliases = mapOf(
                                    "Dîner" to listOf("Dinner", "Diner", "Repas du soir"),
                                    "Petit Déjeuner" to listOf("Breakfast", "Petit Dejeuner"),
                                    "Déjeuner" to listOf("Lunch", "Dejeuner")
                                )

                                // On récupère les variantes possibles pour le bouton cliqué
                                val targets = (aliases[mealName] ?: emptyList()) + mealName

                                // On cherche si L'UN des noms correspond à ta sous-catégorie
                                val subId = allSubCats.find { cat ->
                                    val catNameClean = normalize(cat.name).trim()
                                    targets.any { target ->
                                        catNameClean.equals(normalize(target).trim(), ignoreCase = true)
                                    }
                                }?.id

                                val finalEntry = JournalEntry(
                                    categoryName = "Repas",
                                    subCategoryId = subId,
                                    date = java.util.Date(),
                                    mealCalories = kcal,
                                    mealProtein = prot,
                                    mealCarbs = carbs,
                                    mealLipids = fat,
                                    mealSugar = sugar,
                                    mealSodium = sodium,
                                    mealSaturatedFat = satFat,
                                    mealFibers = fibers,
                                    mealQuality = score,
                                    comment = foodNames,
                                    tags = selectedItems.joinToString(",") { it.id }
                                )

                                journalEntryViewModel.insert(finalEntry)
                                pendingNutrition.removeAll(selectedItems)
                            }
                        )
                    }
                    items(items = todayEntries) { entry ->
                        var subCategoryName by remember { mutableStateOf<String?>(null) }
                        LaunchedEffect(key1 = entry.subCategoryId) {
                            if (entry.subCategoryId != null) {
                                subCategoryViewModel.getSubCategoryName(id = entry.subCategoryId!!) {
                                    subCategoryName = it
                                }
                            } else {
                                subCategoryName = null
                            }
                        }

                        // EST-CE UN DÉFI ACTIF ?
                        val isActive = entry.categoryName == "Défis" && entry.challengeState == 1

                        // --- 1. ON DÉTERMINE LA COULEUR DYNAMIQUE ICI ---
                        val accentColor = when (entry.categoryName) {
                            "Sport" -> NeonOrange
                            "Repas" -> NeonRed
                            "Sommeil" -> DeepBlue
                            "Action productive" -> ProdViolet
                            "Temps d'écran" -> NeonPink
                            "Nombre de pas" -> NeonCyan
                            "Humeur" -> NeonGreen
                            "Dépense" -> NeonDollar
                            "Revenus" -> NeonGreen
                            "Défis" -> Color(0xFFFFD700) // Or / Jaune
                            else -> TextGray
                        }

                        Box(contentAlignment = Alignment.Center) {
                            StyledJournalEntryCard(
                                entry = entry,
                                subCategoryName = subCategoryName,
                                onClick = { entryToEdit = entry },
                                onDelete = { showDeleteDialog = entry }
                            )

                            // SI ACTIF : Le bouton "TERMINER" flotte par-dessus à droite
                            if (isActive) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .padding(end = 16.dp), // Marge légèrement ajustée
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    // --- 2. BOUTON COMPACT ET COLORÉ ---
                                    Button(
                                        onClick = {
                                            completionScore = 10
                                            entryToComplete = entry
                                        },
                                        // Fond noir presque opaque pour cacher le texte dessous
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.95f)),
                                        // La bordure prend la couleur de la carte (accentColor)
                                        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor),
                                        shape = RoundedCornerShape(8.dp), // Coins un peu moins ronds pour faire "Tactique"
                                        // Réduction de la hauteur (28dp au lieu de 36dp)
                                        modifier = Modifier.height(28.dp),
                                        // Padding interne réduit pour que le bouton soit moins large
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Check,
                                                null,
                                                tint = accentColor, // Icône de la bonne couleur
                                                modifier = Modifier.size(12.dp) // Icône plus petite
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                "TERMINER",
                                                color = accentColor, // Texte de la bonne couleur
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp // Police plus petite (10sp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showDeleteDialog != null) {
            NeonHudDialog(
                title = "SUPPRIMER ?",
                onDismiss = { showDeleteDialog = null },
                onConfirm = {
                    journalEntryViewModel.delete(showDeleteDialog!!)
                    showDeleteDialog = null
                },
                confirmLabel = "SUPPRIMER"
            ) {
                Text("Cette action est irréversible.", color = TextGray)
            }
        }

        if (entryToEdit != null) {
            EditEntryDialog(
                entry = entryToEdit!!,
                categories = categories,
                subCategoryViewModel = subCategoryViewModel,
                onConfirm = { edited ->
                    journalEntryViewModel.update(edited)
                    avatarViewModel.processEntry(edited)
                    entryToEdit = null
                },
                onDismiss = { entryToEdit = null }
            )
        }

        if (showAddDialog && categories.isNotEmpty()) {
            AddEntryDialog(
                categories = categories,
                subCategoryViewModel = subCategoryViewModel,
                onConfirm = { entry ->
                    journalEntryViewModel.insert(entry)
                    avatarViewModel.processEntry(entry)
                    onDismissDialog()
                },
                onDismiss = onDismissDialog
            )
        }
        if (entryToComplete != null) {
            NeonHudDialog(
                title = "MISSION ACCOMPLIE ?",
                onDismiss = { entryToComplete = null },
                confirmLabel = "TERMINER",
                onConfirm = {
                    // On appelle la fonction du ViewModel pour valider
                    journalEntryViewModel.completeActiveChallenge(
                        entryToComplete!!,
                        successScore = completionScore
                    )
                    entryToComplete = null
                    // Optionnel : un petit Toast ou effet sonore ici serait top
                }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = entryToComplete?.challengeTitle ?: "Défi",
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    NeonSliderField(
                        label = "Niveau de réussite",
                        value = completionScore,
                        onValueChange = { completionScore = it },
                        isPositive = true // Vert -> Rouge (ou l'inverse selon ta préférence)
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------
//   WIDGETS ET DIALOGUES (DÉFINIS HORS DE TODAYSCREEN)
// -----------------------------------------------------------------------

@Composable
fun ActiveChallengeWidget(
    activeChallenge: JournalEntry?,
    lastCompleted: JournalEntry?,
    onClick: () -> Unit,
    onComplete: (JournalEntry) -> Unit
) {
    if (activeChallenge != null) {
        // --- MODE EN COURS ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .border(2.dp, NeonCyan, RoundedCornerShape(20.dp))
                .clickable { onClick() },
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A2025))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onComplete(activeChallenge) }) {
                    Icon(
                        Icons.Default.RadioButtonUnchecked,
                        null,
                        tint = NeonCyan,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        "MISSION EN COURS",
                        color = NeonCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        activeChallenge.challengeTitle ?: "Défi",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    if (activeChallenge.challengeDurationMinutes != null) {
                        Text(
                            "${activeChallenge.challengeDurationMinutes} min",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    } else if (lastCompleted != null) {
        // --- MODE HISTORIQUE ---
        StyledJournalEntryCard(
            entry = lastCompleted,
            subCategoryName = null,
            onClick = { onClick() },
            onDelete = {}
        )
    } else {
        // --- RIEN ---
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
        ) {
            Text("DÉFINIR UN OBJECTIF", color = Color.White)
        }
    }
}

// Utilitaires de formatage
fun formatDurationToMinutes(digits: String): Int? {
    val d = digits.filter { it.isDigit() }
    if (d.isEmpty()) return null
    val padded = d.padStart(4, '0')
    val h = padded.substring(0, 2).toInt()
    val m = padded.substring(2, 4).toInt()
    return h * 60 + m
}

fun calculateSleepDuration(bed: String, wake: String): String? {
    val b = bed.filter { it.isDigit() };
    val w = wake.filter { it.isDigit() }
    if (b.length < 4 || w.length < 4) return null
    val bedTotal = b.substring(0, 2).toInt() * 60 + b.substring(2, 4).toInt()
    var wakeTotal = w.substring(0, 2).toInt() * 60 + w.substring(2, 4).toInt()
    if (wakeTotal < bedTotal) wakeTotal += 24 * 60
    val durationMin = wakeTotal - bedTotal
    return "%02d:%02d".format(durationMin / 60, durationMin % 60)
}

@Composable
fun TabButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) NeonCyan.copy(alpha = 0.3f) else Color.Transparent
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.padding(4.dp)
    ) {
        Text(text, color = if (isSelected) NeonCyan else Color.Gray)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEntryDialog(
    categories: List<Category>,
    subCategoryViewModel: SubCategoryViewModel,
    onConfirm: (JournalEntry) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategoryName by remember { mutableStateOf(categories.first().name) }
    var selectedSubCategoryId by remember { mutableStateOf<Int?>(null) }
    var isBankMode by remember { mutableStateOf(false) }

    // Champs standards
    var challengeDifficulty by remember { mutableIntStateOf(5) }
    var sportDuration by remember { mutableStateOf("") }
    var sportIntensity by remember { mutableIntStateOf(5) }
    var mealCalories by remember { mutableStateOf("") }
    var mealProtein by remember { mutableStateOf("") }
    var mealCarbs by remember { mutableStateOf("") }
    var mealLipids by remember { mutableStateOf("") }
    var mealQuality by remember { mutableIntStateOf(5) }
    var sleepBedTime by remember { mutableStateOf("") }
    var sleepWakeTime by remember { mutableStateOf("") }
    var sleepQuality by remember { mutableIntStateOf(5) }
    var sleepWokeUpWithAlarm by remember { mutableStateOf<Boolean?>(false) }
    var productiveDuration by remember { mutableStateOf("") }
    var productiveFocus by remember { mutableIntStateOf(2) }
    var screenDuration by remember { mutableStateOf("") }
    var stepsCount by remember { mutableStateOf("") }
    var moodScore by remember { mutableIntStateOf(5) }
    var depensePriceVal by remember { mutableStateOf("") }
    var incomeAmountVal by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var challengeTitle by remember { mutableStateOf("") }
    var challengeDuration by remember { mutableStateOf("") }
    var challengeQuantity by remember { mutableStateOf("") }
    var challengeSuccess by remember { mutableIntStateOf(5) }
    var posture by remember { mutableStateOf("Assis") } // Par défaut
    var selectedMuscles by remember { mutableStateOf(emptyList<String>()) }
    var isTrx by remember { mutableStateOf(false) }
    // --- AJOUT "MODE VOYAGE" (Début) ---
    val context = LocalContext.current // On récupère le contexte ici
    val prefs = remember { context.getSharedPreferences("NicoTrackerPrefs", android.content.Context.MODE_PRIVATE) }

    var isThbMode by remember { mutableStateOf(false) }
    // On charge le taux sauvegardé, sinon "37.0" par défaut
    var exchangeRate by remember {
        mutableStateOf(prefs.getString("saved_exchange_rate", "37.0") ?: "37.0")
    }
    // --- AJOUT "MODE VOYAGE" (Fin) ---

    // --- NOUVEAUX CHAMPS COPYWRITING ---
    var copyPillar by remember { mutableStateOf<String?>(null) } // Complexity
    var copyTags by remember { mutableStateOf<List<String>>(emptyList()) } // Tags
    var copyVolume by remember { mutableStateOf("") } // Volume
    var copyFormat by remember { mutableStateOf("Lecture") } // Valeur par défaut si on clique sur Formation

    val manager = remember { CopywritingConfigManager(context) }

    var selectedDate by remember { mutableStateOf(java.util.Date()) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate.time)
    var showDatePicker by remember { mutableStateOf(false) }

    val selectedCategory = categories.first { it.name == selectedCategoryName }
    val subCategories by subCategoryViewModel
        .getSubCategoriesForCategory(selectedCategory.id)
        .collectAsState(initial = emptyList())

    val scrollState = rememberScrollState()

    // Vérifie si c'est du Copywriting
    val isCopywriting = selectedCategoryName == "Action productive" &&
            subCategories.find { it.id == selectedSubCategoryId }?.name?.contains("Copy", ignoreCase = true) == true

    LaunchedEffect(mealCalories, mealProtein) {
        if (selectedCategoryName == "Repas") {
            val kcal = mealCalories.toIntOrNull() ?: 0
            val prot = mealProtein.toIntOrNull() ?: 0
            if (kcal > 0) {
                mealQuality = NutritionScoringEngine.computeScore(kcal, prot, 0)
            }
        }
    }

    NeonHudDialog(
        title = if (isBankMode) "AJOUTER À L'ARMURERIE" else "AJOUTER : ${selectedCategoryName.uppercase()}",
        onDismiss = onDismiss,
        confirmLabel = if (isBankMode) "STOCKER" else "VALIDER",
        onConfirm = {
            val selectedSubName = subCategories.find { it.id == selectedSubCategoryId }?.name?.trim()
            val isProtocol = selectedSubName.equals("Protocole d'intégrité", ignoreCase = true)
            val finalState = if (isBankMode) 0 else if (isProtocol) 1 else 2

            // --- MÉMORISATION AUTOMATIQUE COPYWRITING ---
            if (isCopywriting && !copyPillar.isNullOrBlank()) {
                manager.addSuggestion(copyPillar!!)
            }
            if (isThbMode) {
                prefs.edit().putString("saved_exchange_rate", exchangeRate).apply()
            }

            // --- CALCUL DE LA CONVERSION (NOUVEAU) ---
            // --- FIX VIRGULE & CALCUL ---
            // On remplace la virgule par un point pour que le système comprenne le chiffre
            val rawPrice = depensePriceVal.replace(',', '.').toDoubleOrNull()

            val finalPrice = if (rawPrice != null && isThbMode) {
                val rate = exchangeRate.replace(',', '.').toDoubleOrNull() ?: 37.0
                val inEur = rawPrice / rate
                (inEur * 100.0).roundToInt() / 100.0
            } else {
                rawPrice
            }

            val entry = JournalEntry(
                categoryName = selectedCategoryName,
                subCategoryId = selectedSubCategoryId,
                date = selectedDate,
                sportDurationMinutes = formatDurationToMinutes(sportDuration),
                sportIntensity = sportIntensity,
                mealCalories = mealCalories.toIntOrNull(),
                mealProtein = mealProtein.toIntOrNull(),
                mealCarbs = mealCarbs.toIntOrNull(),
                mealLipids = mealLipids.toIntOrNull(),
                mealQuality = mealQuality,
                sleepBedTime = sleepBedTime.ifBlank { null },
                sleepWakeTime = sleepWakeTime.ifBlank { null },
                sleepDuration = calculateSleepDuration(sleepBedTime, sleepWakeTime),
                sleepQuality = sleepQuality,
                sleepWokeUpWithAlarm = sleepWokeUpWithAlarm,
                productiveDurationMinutes = formatDurationToMinutes(productiveDuration),
                productiveFocus = productiveFocus,
                screenDurationMinutes = formatDurationToMinutes(screenDuration),
                stepsCount = stepsCount.toIntOrNull(),
                moodScore = moodScore,
                comment = comment.ifBlank { null },
                depensePrice = finalPrice,
                incomeAmount = incomeAmountVal.toDoubleOrNull(),
                challengeTitle = challengeTitle.ifBlank { null },
                challengeDurationMinutes = formatDurationToMinutes(challengeDuration),
                challengeQuantity = challengeQuantity.toIntOrNull(),
                challengeSuccess = challengeSuccess,
                challengeDifficulty = challengeDifficulty,
                challengeState = finalState,
                complexity = if (isCopywriting) copyPillar else null,
                tags = if (isCopywriting && copyTags.isNotEmpty()) {
                    "${copyTags.joinToString(",")}, $posture"
                } else if (selectedCategoryName == "Action productive" || selectedCategoryName == "Temps d'écran") {
                    posture
                } else if (selectedCategoryName == "Sport") {
                    val trxTag = if (isTrx) listOf("TRX") else emptyList()
                    (selectedMuscles + trxTag).joinToString(",")
                } else {
                    null
                },
                volume = if (isCopywriting) copyVolume.toDoubleOrNull() else null
            )
            onConfirm(entry)
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)) {
            if (selectedCategoryName == "Défis") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).background(Color.Black, RoundedCornerShape(12.dp)).border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp)),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TabButton("Journal", !isBankMode) { isBankMode = false }
                    TabButton("Banque", isBankMode) { isBankMode = true }
                }
            }

            TextButton(onClick = { showDatePicker = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("📅  " + android.text.format.DateFormat.format("dd/MM/yyyy", selectedDate), color = NeonCyan)
            }
            Spacer(Modifier.height(12.dp))

            CategoryDropdown(categories = categories, selectedCategoryName = selectedCategoryName, onCategorySelected = { selectedCategoryName = it; selectedSubCategoryId = null })
            Spacer(Modifier.height(8.dp))

            if (selectedCategoryName !in listOf("Sommeil", "Nombre de pas", "Humeur", "Revenus")) {
                NeonChipSelector(label = "Sous-catégorie :", options = subCategories, selectedId = selectedSubCategoryId, onSelected = { selectedSubCategoryId = it })
                Spacer(Modifier.height(12.dp))
            }

            // --- UI SPÉCIFIQUE PAR CATÉGORIE ---

            when (selectedCategoryName) {
                "Action productive" -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) {
                            DurationInputField("Durée (hh:mm)", productiveDuration) { productiveDuration = it }
                        }
                        Spacer(Modifier.width(8.dp))
                        PostureCyclicButton(posture) { posture = it }
                    }
                    Spacer(Modifier.height(12.dp))



                    // Sélecteur de Flow (Reste affiché en dessous)
                    FlowSelector(selectedState = productiveFocus, onSelection = { productiveFocus = it })
                }
                // ... LES AUTRES CATÉGORIES RESTENT EXACTEMENT PAREIL ...
                "Sport" -> {
                    DurationInputField("Durée (hh:mm)", sportDuration) { sportDuration = it }
                    Spacer(Modifier.height(12.dp))
                    NeonSliderField("Intensité", sportIntensity, { sportIntensity = it }, isPositive = true)

                    Spacer(Modifier.height(16.dp))

                    // --- 1. SÉLECTEUR DE MUSCLES ---
                    MuscleTacticalSelector(selectedMuscles) { selectedMuscles = it }

                    // --- 2. LOGIQUE TRX (Conditionnelle) ---
                    // On vérifie le nom de la sous-catégorie sélectionnée
                    val currentSubName = subCategories.find { it.id == selectedSubCategoryId }?.name ?: ""

                    // Si le nom contient "Home" (insensible à la casse), on affiche l'option TRX
                    if (currentSubName.contains("Home", ignoreCase = true)) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isTrx = !isTrx }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isTrx,
                                onCheckedChange = { isTrx = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = NeonOrange,
                                    uncheckedColor = Color.Gray,
                                    checkmarkColor = Color.Black
                                )
                            )
                            Text("TRX Suspension Protocol", color = if(isTrx) NeonOrange else Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                "Repas" -> {
                    OutlinedTextField(value = mealCalories, onValueChange = { mealCalories = it }, label = { Text("kcal") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next))
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = mealProtein, onValueChange = { if (it.length <= 3) mealProtein = it.filter { char -> char.isDigit() } }, label = { Text("P") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next))
                        OutlinedTextField(value = mealCarbs, onValueChange = { if (it.length <= 3) mealCarbs = it.filter { char -> char.isDigit() } }, label = { Text("G") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next))
                        OutlinedTextField(value = mealLipids, onValueChange = { if (it.length <= 3) mealLipids = it.filter { char -> char.isDigit() } }, label = { Text("L") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done))
                    }
                    Spacer(Modifier.height(12.dp))
                    NeonSliderField("Qualité Nutritionnelle", mealQuality, { mealQuality = it }, isPositive = false)
                }
                "Sommeil" -> {
                    TimeInputField("Couché (hh:mm)", sleepBedTime) { sleepBedTime = it }
                    Spacer(Modifier.height(8.dp))
                    TimeInputField("Réveil (hh:mm)", sleepWakeTime) { sleepWakeTime = it }
                    Spacer(Modifier.height(8.dp))
                    val autoSleep = calculateSleepDuration(sleepBedTime, sleepWakeTime) ?: ""
                    OutlinedTextField(value = autoSleep, onValueChange = {}, label = { Text("Durée totale") }, modifier = Modifier.fillMaxWidth(), readOnly = true, enabled = false)
                    Spacer(Modifier.height(12.dp))
                    AlarmWakeUpSelector(selectedState = sleepWokeUpWithAlarm, onSelection = { sleepWokeUpWithAlarm = it })
                    Spacer(Modifier.height(12.dp))
                    NeonSliderField("Qualité du sommeil", sleepQuality, { sleepQuality = it }, isPositive = false)
                }
                "Temps d'écran" -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) {
                            DurationInputField("Durée (hh:mm)", screenDuration) { screenDuration = it }
                        }
                        Spacer(Modifier.width(8.dp))
                        PostureCyclicButton(posture) { posture = it }
                    }
                }
                "Nombre de pas" -> OutlinedTextField(value = stepsCount, onValueChange = { stepsCount = it }, label = { Text("Nombre de pas") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                "Humeur" -> NeonSliderField("Humeur du jour", moodScore, { moodScore = it }, isPositive = false)
                "Dépense" -> {
                    // 1. Le champ Prix
                    OutlinedTextField(
                        value = depensePriceVal,
                        onValueChange = { depensePriceVal = it },
                        label = { Text(if (isThbMode) "Prix (฿)" else "Prix (€)") },
                        modifier = Modifier.fillMaxWidth(),
                        // CHANGEMENT ICI : Decimal pour avoir la virgule
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )

                    // 2. L'interrupteur "Mode Thaïlande"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Text("Payer en Baht (฿)", color = Color.White, modifier = Modifier.weight(1f))
                        Switch(
                            checked = isThbMode,
                            onCheckedChange = { isThbMode = it }
                        )
                    }

                    // 3. Le champ Taux de change
                    if (isThbMode) {
                        OutlinedTextField(
                            value = exchangeRate,
                            onValueChange = { exchangeRate = it },
                            label = { Text("Taux actuel (1€ = ? ฿)") },
                            // CHANGEMENT ICI AUSSI : Decimal pour un taux précis (ex: 36.5)
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                    }
                }
                "Revenus" -> OutlinedTextField(value = incomeAmountVal, onValueChange = { incomeAmountVal = it }, label = { Text("Montant reçu (€)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                "Défis" -> {
                    NeonSliderField("Difficulté (LVL)", challengeDifficulty, { challengeDifficulty = it }, isPositive = true)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = challengeTitle, onValueChange = { challengeTitle = it }, label = { Text("Intitulé") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    DurationInputField("Durée (hh:mm)", challengeDuration) { challengeDuration = it }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = challengeQuantity, onValueChange = { challengeQuantity = it }, label = { Text("Quantité") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                    Spacer(Modifier.height(12.dp))
                    NeonSliderField("Succès du défi", challengeSuccess, { challengeSuccess = it }, isPositive = false)
                }
            }

            Spacer(Modifier.height(24.dp))


            if (isCopywriting) {
                val tagParts = copyTags.firstOrNull()?.split(",") ?: emptyList()
                val currentMission = tagParts.getOrNull(0)?.trim()
                val currentPhase = tagParts.getOrNull(1)?.trim()

                CopywritingTacticalBoard(
                    client = copyPillar,
                    onClientChange = { copyPillar = it },

                    missionType = currentMission,
                    onMissionChange = { name, _ ->
                        val phase = currentPhase ?: "Draft"
                        // ON SAUVEGARDE LE FORMAT SI C'EST FORMATION
                        val formatTag = if(phase == "Formation") ", $copyFormat" else ""
                        copyTags = listOf("$name, $phase$formatTag")
                    },

                    phase = currentPhase,
                    onPhaseChange = { newPhase ->
                        val mission = currentMission ?: "Mission"
                        // ON SAUVEGARDE LE FORMAT SI C'EST FORMATION
                        val formatTag = if(newPhase == "Formation") ", $copyFormat" else ""
                        copyTags = listOf("$mission, $newPhase$formatTag")
                    },

                    // --- NOUVEAUX PARAMÈTRES ---
                    format = copyFormat,
                    onFormatChange = { newFormat ->
                        copyFormat = newFormat
                        // Mise à jour immédiate des tags
                        val mission = currentMission ?: "Mission"
                        val phase = currentPhase ?: "Formation"
                        if (phase == "Formation") {
                            copyTags = listOf("$mission, $phase, $newFormat")
                        }
                    },

                    volume = copyVolume,
                    onVolumeChange = { copyVolume = it }
                )


            } else {
                // Affichage normal pour les autres sous-catégories (Deep Work, etc.)
                // Tu peux laisser ton ancien système de tags ou ne rien mettre
                Text("Focus session standard", color = Color.Gray, fontSize = 12.sp)
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = comment, onValueChange = { comment = it }, label = { Text("Commentaire") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp, max = 150.dp)
            )

            Spacer(Modifier.height(120.dp))
        }
    }

    // ... (Bloc if(showDatePicker) inchangé avec ma correction de fuseau horaire précédente)
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        // Code du fix UTC ici...
                        val utcCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                        utcCal.timeInMillis = millis
                        val localCal = java.util.Calendar.getInstance()
                        localCal.set(java.util.Calendar.YEAR, utcCal.get(java.util.Calendar.YEAR))
                        localCal.set(java.util.Calendar.MONTH, utcCal.get(java.util.Calendar.MONTH))
                        localCal.set(java.util.Calendar.DAY_OF_MONTH, utcCal.get(java.util.Calendar.DAY_OF_MONTH))
                        localCal.set(java.util.Calendar.HOUR_OF_DAY, 0); localCal.set(java.util.Calendar.MINUTE, 0); localCal.set(java.util.Calendar.SECOND, 0); localCal.set(java.util.Calendar.MILLISECOND, 0)
                        selectedDate = localCal.time
                    }; showDatePicker = false
                }) { Text("OK") }
            }
        ) { DatePicker(state = datePickerState) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEntryDialog(
    entry: JournalEntry,
    categories: List<Category>,
    subCategoryViewModel: SubCategoryViewModel,
    onConfirm: (JournalEntry) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategoryName by remember { mutableStateOf(entry.categoryName) }
    var selectedSubCategoryId by remember { mutableStateOf(entry.subCategoryId) }

    var sportDuration by remember { mutableStateOf(entry.sportDurationMinutes?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "") }
    var sportIntensity by remember { mutableIntStateOf(entry.sportIntensity ?: 5) }
    var mealCalories by remember { mutableStateOf(entry.mealCalories?.toString() ?: "") }
    var mealProtein by remember { mutableStateOf(entry.mealProtein?.toString() ?: "") }
    var mealCarbs by remember { mutableStateOf(entry.mealCarbs?.toString() ?: "") }
    var mealLipids by remember { mutableStateOf(entry.mealLipids?.toString() ?: "") }
    var mealQuality by remember { mutableIntStateOf(entry.mealQuality ?: 5) }
    var sleepBedTime by remember { mutableStateOf(entry.sleepBedTime ?: "") }
    var sleepWakeTime by remember { mutableStateOf(entry.sleepWakeTime ?: "") }
    var sleepQuality by remember { mutableIntStateOf(entry.sleepQuality ?: 5) }
    var sleepWokeUpWithAlarm by remember { mutableStateOf(entry.sleepWokeUpWithAlarm) }
    var productiveDuration by remember { mutableStateOf(entry.productiveDurationMinutes?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "") }
    var productiveFocus by remember { mutableIntStateOf(entry.productiveFocus ?: 5) }
    var screenDuration by remember { mutableStateOf(entry.screenDurationMinutes?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "") }
    var stepsCount by remember { mutableStateOf(entry.stepsCount?.toString() ?: "") }
    var moodScore by remember { mutableIntStateOf(entry.moodScore ?: 5) }
    var depensePriceVal by remember { mutableStateOf(entry.depensePrice?.toString() ?: "") }
    var incomeAmountVal by remember { mutableStateOf(entry.incomeAmount?.toString() ?: "") }
    var comment by remember { mutableStateOf(entry.comment ?: "") }
    var challengeTitle by remember { mutableStateOf(entry.challengeTitle ?: "") }
    var challengeDuration by remember { mutableStateOf(entry.challengeDurationMinutes?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "") }
    var challengeQuantity by remember { mutableStateOf(entry.challengeQuantity?.toString() ?: "") }
    var challengeSuccess by remember { mutableIntStateOf(entry.challengeSuccess ?: 5) }
    var challengeDifficulty by remember { mutableIntStateOf(entry.challengeDifficulty ?: 5) }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("NicoTrackerPrefs", android.content.Context.MODE_PRIVATE) }

    var showAddTimeDialog by remember { mutableStateOf(false) }

    var isThbMode by remember { mutableStateOf(false) }
    // On charge le taux sauvegardé
    var exchangeRate by remember {
        mutableStateOf(prefs.getString("saved_exchange_rate", "37.0") ?: "37.0")
    }
    // Parsing Sport
    var selectedMuscles by remember {
        mutableStateOf(
            if (entry.categoryName == "Sport" && !entry.tags.isNullOrBlank()) {
                // On garde tout ce qui N'EST PAS "TRX" comme étant un muscle
                entry.tags.split(",").map { it.trim() }.filter { it != "TRX" }
            } else {
                emptyList()
            }
        )
    }

    var isTrx by remember {
        mutableStateOf(entry.tags?.contains("TRX", ignoreCase = true) == true)
    }

    // --- BLOC A : NOUVELLES VARIABLES (CORRIGÉ) ---
    var copyPillar by remember { mutableStateOf<String?>(entry.complexity) }

    // On cherche si un mot clé de posture est présent dans les tags
    var posture by remember {
        mutableStateOf(
            when {
                entry.tags?.contains("Marche", ignoreCase = true) == true -> "Marche"
                entry.tags?.contains("Debout", ignoreCase = true) == true -> "Debout"
                else -> "Assis"
            }
        )
    }

    // CORRECTION : On ne split PAS ici, on garde la string entière dans une liste pour respecter ta logique
    var copyTags by remember {
        mutableStateOf(if (entry.tags.isNullOrBlank()) emptyList() else listOf(entry.tags))
    }

    var copyVolume by remember { mutableStateOf(entry.volume?.toString() ?: "") }

    // CORRECTION : On va chercher le 3ème élément (index 2) directement dans la string brute
    var copyFormat by remember {
        mutableStateOf(
            entry.tags?.split(",")?.getOrNull(2)?.trim() ?: "Lecture"
        )
    }

    val manager = remember { CopywritingConfigManager(context) }

    // Détection pour savoir si on affiche le dashboard
    val selectedCategory = categories.find { it.name == selectedCategoryName }
    val subCategories by if (selectedCategory != null) {
        subCategoryViewModel.getSubCategoriesForCategory(selectedCategory.id).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList()) }
    }

    val isCopywriting = selectedCategoryName == "Action productive" &&
            subCategories.find { it.id == selectedSubCategoryId }?.name?.contains("Copy", ignoreCase = true) == true

    var selectedDate by remember { mutableStateOf(entry.date) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate.time)
    var showDatePicker by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    LaunchedEffect(mealCalories, mealProtein) {
        if (selectedCategoryName == "Repas") {
            val kcal = mealCalories.toIntOrNull() ?: 0
            val prot = mealProtein.toIntOrNull() ?: 0
            if (kcal > 0) {
                mealQuality = NutritionScoringEngine.computeScore(kcal, prot, 0)
            }
        }
    }

    NeonHudDialog(
        title = "MODIFIER",
        onDismiss = onDismiss,
        confirmLabel = "METTRE À JOUR",
        onConfirm = {
            // --- LOGIQUE DE CONVERSION (AJOUTÉ) ---
            // On vérifie si c'est le protocole pour forcer l'état "Actif" si besoin
            val selectedSubName = subCategories.find { it.id == selectedSubCategoryId }?.name?.trim()
            val isProtocol = selectedSubName.equals("Protocole d'intégrité", ignoreCase = true)

            // --- MÉMORISATION AUTOMATIQUE ---
            if (isCopywriting && !copyPillar.isNullOrBlank()) {
                manager.addSuggestion(copyPillar!!)
            }

            if (isThbMode) {
                prefs.edit().putString("saved_exchange_rate", exchangeRate).apply()
            }

            // Si c'est un protocole, on force l'état à 1 (Actif / En cours).
            // Sinon, on garde l'état qu'avait déjà l'entrée (pour ne pas casser les autres défis).
            val newState = if (isProtocol) 1 else entry.challengeState

            val updated = entry.copy(
                categoryName = selectedCategoryName,
                subCategoryId = selectedSubCategoryId,
                date = selectedDate,
                sportDurationMinutes = formatDurationToMinutes(sportDuration),
                sportIntensity = sportIntensity,
                mealCalories = mealCalories.toIntOrNull(),
                mealProtein = mealProtein.toIntOrNull(),
                mealCarbs = mealCarbs.toIntOrNull(),
                mealLipids = mealLipids.toIntOrNull(),
                mealQuality = mealQuality,
                sleepBedTime = sleepBedTime.ifBlank { null },
                sleepWakeTime = sleepWakeTime.ifBlank { null },
                sleepDuration = calculateSleepDuration(sleepBedTime, sleepWakeTime),
                sleepQuality = sleepQuality,
                sleepWokeUpWithAlarm = sleepWokeUpWithAlarm,
                productiveDurationMinutes = formatDurationToMinutes(productiveDuration),
                productiveFocus = productiveFocus,
                screenDurationMinutes = formatDurationToMinutes(screenDuration),
                stepsCount = stepsCount.toIntOrNull(),
                moodScore = moodScore,
                comment = comment.ifBlank { null },
                depensePrice = run {
                    // FIX VIRGULE ICI AUSSI
                    val raw = depensePriceVal.replace(',', '.').toDoubleOrNull()
                    if (raw != null && isThbMode) {
                        val rate = exchangeRate.replace(',', '.').toDoubleOrNull() ?: 37.0
                        val inEur = raw / rate
                        (inEur * 100.0).roundToInt() / 100.0
                    } else {
                        raw
                    }
                },
                incomeAmount = incomeAmountVal.toDoubleOrNull(),
                challengeTitle = challengeTitle.ifBlank { null },
                challengeDurationMinutes = formatDurationToMinutes(challengeDuration),
                challengeQuantity = challengeQuantity.toIntOrNull(),
                challengeSuccess = challengeSuccess,
                // On ajoute la difficulté et le nouvel état calculé
                challengeDifficulty = challengeDifficulty,
                challengeState = newState,
                complexity = if (isCopywriting) copyPillar else null,
                // Dans onConfirm...
                tags = if (isCopywriting && copyTags.isNotEmpty()) {
                    "${copyTags.joinToString(",")}, $posture"
                } else if (selectedCategoryName == "Action productive" || selectedCategoryName == "Temps d'écran") {
                    posture
                } else if (selectedCategoryName == "Sport") {
                    val trxTag = if (isTrx) listOf("TRX") else emptyList()
                    (selectedMuscles + trxTag).joinToString(",")
                } else {
                    null
                },
                volume = if (isCopywriting) copyVolume.toDoubleOrNull() else null
            )
            onConfirm(updated)
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)) {
            TextButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    "📅  " + android.text.format.DateFormat.format("dd/MM/yyyy", selectedDate),
                    color = NeonCyan
                )
            }
            Spacer(Modifier.height(12.dp))
            CategoryDropdown(
                categories = categories,
                selectedCategoryName = selectedCategoryName,
                onCategorySelected = { selectedCategoryName = it; selectedSubCategoryId = null }
            )
            Spacer(Modifier.height(8.dp))

            if (selectedCategoryName !in listOf("Sommeil", "Nombre de pas", "Humeur", "Revenus")) {
                NeonChipSelector(
                    label = "Sous-catégorie :",
                    options = subCategories,
                    selectedId = selectedSubCategoryId,
                    onSelected = { selectedSubCategoryId = it }
                )
                Spacer(Modifier.height(12.dp))
            }

            when (selectedCategoryName) {
                "Sport" -> {
                    DurationInputField("Durée (hh:mm)", sportDuration) { sportDuration = it }
                    Spacer(Modifier.height(12.dp))
                    NeonSliderField("Intensité", sportIntensity, { sportIntensity = it }, isPositive = true)

                    Spacer(Modifier.height(16.dp))

                    MuscleTacticalSelector(selectedMuscles) { selectedMuscles = it }

                    val currentSubName = subCategories.find { it.id == selectedSubCategoryId }?.name ?: ""
                    if (currentSubName.contains("Home", ignoreCase = true)) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isTrx = !isTrx },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = isTrx, onCheckedChange = { isTrx = it }, colors = CheckboxDefaults.colors(checkedColor = NeonOrange))
                            Text("TRX Suspension Protocol", color = if(isTrx) NeonOrange else Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                "Repas" -> {
                    OutlinedTextField(
                        value = mealCalories, onValueChange = { mealCalories = it },
                        label = { Text("kcal") }, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = mealProtein, onValueChange = { if (it.length <= 3) mealProtein = it.filter { char -> char.isDigit() } },
                            label = { Text("P") }, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next)
                        )
                        OutlinedTextField(
                            value = mealCarbs, onValueChange = { if (it.length <= 3) mealCarbs = it.filter { char -> char.isDigit() } },
                            label = { Text("G") }, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next)
                        )
                        OutlinedTextField(
                            value = mealLipids, onValueChange = { if (it.length <= 3) mealLipids = it.filter { char -> char.isDigit() } },
                            label = { Text("L") }, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    NeonSliderField("Qualité Nutritionnelle", mealQuality, { mealQuality = it }, isPositive = false)
                }

                "Sommeil" -> {
                    TimeInputField("Couché (hh:mm)", sleepBedTime) { sleepBedTime = it }
                    Spacer(Modifier.height(8.dp))
                    TimeInputField("Réveil (hh:mm)", sleepWakeTime) { sleepWakeTime = it }
                    val auto = calculateSleepDuration(sleepBedTime, sleepWakeTime) ?: ""
                    OutlinedTextField(
                        value = auto, onValueChange = {},
                        label = { Text("Durée totale") }, modifier = Modifier.fillMaxWidth(),
                        enabled = false, readOnly = true
                    )
                    Spacer(Modifier.height(12.dp))
                    AlarmWakeUpSelector(
                        selectedState = sleepWokeUpWithAlarm,
                        onSelection = { sleepWokeUpWithAlarm = it }
                    )
                    Spacer(Modifier.height(12.dp))
                    NeonSliderField("Qualité du sommeil", sleepQuality, { sleepQuality = it }, isPositive = false)
                }

                "Action productive" -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) {
                            DurationInputField("Durée (hh:mm)", productiveDuration) { productiveDuration = it }
                        }

                        // --- BOUTON AJOUT RAPIDE ---
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { showAddTimeDialog = true },
                            modifier = Modifier
                                .size(50.dp) // Même hauteur que le champ texte
                                .border(1.dp, NeonCyan, RoundedCornerShape(4.dp))
                                .background(NeonCyan.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                        ) {
                            Icon(Icons.Default.Add, null, tint = NeonCyan)
                        }
                        // ---------------------------

                        Spacer(Modifier.width(8.dp))
                        PostureCyclicButton(posture) { posture = it }
                    }
                    Spacer(Modifier.height(12.dp))




                    // LOGIQUE DE CONVERSION INTELLIGENTE (Compatible Ancien & Nouveau système)
                    val currentState = when (productiveFocus) {
                        3 -> 3 // Déjà enregistré comme FLOW
                        2 -> 2 // Déjà enregistré comme NEUTRE
                        1 -> 1 // Déjà enregistré comme LUTTE
                        else -> {
                            // Si c'est une vieille entrée (ex: 8/10), on convertit :
                            when {
                                productiveFocus >= 7 -> 3 // 7 à 10 -> FLOW
                                productiveFocus <= 4 -> 1 // 0 à 4  -> LUTTE
                                else -> 2                 // 5 à 6  -> NEUTRE
                            }
                        }
                    }

                    FlowSelector(
                        selectedState = currentState,
                        onSelection = { newState ->
                            productiveFocus = newState
                        }
                    )
                }

                "Temps d'écran" -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) {
                            DurationInputField("Durée (hh:mm)", screenDuration) { screenDuration = it }
                        }

                        // --- BOUTON AJOUT RAPIDE ---
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { showAddTimeDialog = true },
                            modifier = Modifier
                                .size(50.dp)
                                .border(1.dp, NeonPink, RoundedCornerShape(4.dp)) // Rose pour l'écran ;)
                                .background(NeonPink.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                        ) {
                            Icon(Icons.Default.Add, null, tint = NeonPink)
                        }
                        // ---------------------------

                        Spacer(Modifier.width(8.dp))
                        PostureCyclicButton(posture) { posture = it }
                    }
                }

                "Nombre de pas" -> {
                    OutlinedTextField(
                        value = stepsCount, onValueChange = { stepsCount = it },
                        label = { Text("Nombre de pas") }, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                }

                "Humeur" -> {
                    NeonSliderField("Humeur du jour", moodScore, { moodScore = it }, isPositive = false)
                }

                "Dépense" -> {
                    // 1. Le champ Prix
                    OutlinedTextField(
                        value = depensePriceVal,
                        onValueChange = { depensePriceVal = it },
                        label = { Text(if (isThbMode) "Prix (฿)" else "Prix (€)") },
                        modifier = Modifier.fillMaxWidth(),
                        // CHANGEMENT ICI : Decimal
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )

                    // 2. L'interrupteur "Mode Thaïlande"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Text("Saisir en Baht (฿)", color = Color.White, modifier = Modifier.weight(1f))
                        Switch(
                            checked = isThbMode,
                            onCheckedChange = { isThbMode = it }
                        )
                    }

                    // 3. Le champ Taux de change
                    if (isThbMode) {
                        OutlinedTextField(
                            value = exchangeRate,
                            onValueChange = { exchangeRate = it },
                            label = { Text("Taux actuel (1€ = ? ฿)") },
                            // CHANGEMENT ICI : Decimal
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                    }
                }

                "Défis" -> {
                    // --- NOUVEAU : Ajout du sélecteur de difficulté ici ---
                    NeonSliderField(
                        label = "Difficulté (LVL)",
                        value = challengeDifficulty,
                        onValueChange = { challengeDifficulty = it },
                        isPositive = true
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = challengeTitle, onValueChange = { challengeTitle = it },
                        label = { Text("Intitulé") }, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    DurationInputField("Durée (hh:mm)", challengeDuration) { challengeDuration = it }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = challengeQuantity, onValueChange = { challengeQuantity = it },
                        label = { Text("Quantité") }, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                    Spacer(Modifier.height(12.dp))
                    NeonSliderField("Succès du défi", challengeSuccess, { challengeSuccess = it }, isPositive = false)
                }

                "Revenus" -> {
                    OutlinedTextField(
                        value = incomeAmountVal, onValueChange = { incomeAmountVal = it },
                        label = { Text("Montant reçu (€)") }, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))


            if (isCopywriting) {
                // CORRECTION DE PARSING ICI
                // On récupère la chaîne complète (ex: "Email, Formation, Vidéo") et on nettoie chaque partie
                val fullTagString = copyTags.firstOrNull() ?: ""
                val parts = fullTagString.split(",").map { it.trim() }

                val currentMission = parts.getOrNull(0)
                val currentPhase = parts.getOrNull(1)

                // Note : le format (index 2) est géré par la variable 'copyFormat' initialisée plus haut

                CopywritingTacticalBoard(
                    client = copyPillar,
                    onClientChange = { copyPillar = it },

                    missionType = currentMission,
                    onMissionChange = { name, _ ->
                        val phase = currentPhase ?: "Draft"
                        // Si on est en Formation, on garde le format
                        val formatTag = if(phase == "Formation") ", $copyFormat" else ""
                        copyTags = listOf("$name, $phase$formatTag")
                    },

                    phase = currentPhase,
                    onPhaseChange = { newPhase ->
                        val mission = currentMission ?: "Mission"
                        // Si on passe en Formation, on ajoute le format, sinon rien
                        val formatTag = if(newPhase == "Formation") ", $copyFormat" else ""
                        copyTags = listOf("$mission, $newPhase$formatTag")
                    },

                    // --- NOUVEAUX PARAMÈTRES ---
                    format = copyFormat,
                    onFormatChange = { newFormat ->
                        copyFormat = newFormat
                        // Mise à jour immédiate des tags pour sauvegarder le changement
                        val mission = currentMission ?: "Mission"
                        val phase = currentPhase ?: "Formation"

                        // On force la phase à Formation si on change le format (logique métier)
                        if (phase == "Formation" || phase == "Draft") {
                            copyTags = listOf("$mission, Formation, $newFormat")
                        }
                    },

                    volume = copyVolume,
                    onVolumeChange = { copyVolume = it }
                )
            }

            Spacer(Modifier.height(12.dp))
            // 1. Auto-scroll vers le bas quand on écrit
            LaunchedEffect(comment) {
                scrollState.animateScrollTo(scrollState.maxValue)
            }
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text("Commentaire") },
                modifier = Modifier
                    .fillMaxWidth()
                    // 2. Même limitation de hauteur ici
                    .heightIn(min = 56.dp, max = 150.dp)
            )

            Spacer(Modifier.height(120.dp))
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        // CORRECTION FUSEAU HORAIRE :
                        // Le DatePicker renvoie du UTC minuit. On le convertit en Heure Locale minuit.
                        val utcCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                        utcCal.timeInMillis = millis

                        val localCal = java.util.Calendar.getInstance() // Fuseau du téléphone
                        localCal.set(java.util.Calendar.YEAR, utcCal.get(java.util.Calendar.YEAR))
                        localCal.set(java.util.Calendar.MONTH, utcCal.get(java.util.Calendar.MONTH))
                        localCal.set(java.util.Calendar.DAY_OF_MONTH, utcCal.get(java.util.Calendar.DAY_OF_MONTH))
                        localCal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                        localCal.set(java.util.Calendar.MINUTE, 0)
                        localCal.set(java.util.Calendar.SECOND, 0)
                        localCal.set(java.util.Calendar.MILLISECOND, 0)

                        selectedDate = localCal.time
                    }
                    showDatePicker = false
                }) { Text("OK") }
            }
        ) { DatePicker(state = datePickerState) }
    }
    // --- LOGIQUE DU POPUP ADDITION ---
    if (showAddTimeDialog) {
        AddTimePopup(
            onDismiss = { showAddTimeDialog = false },
            onConfirm = { addedTime ->
                // On détecte quelle catégorie est active pour mettre à jour la bonne variable
                if (selectedCategoryName == "Action productive") {
                    productiveDuration = addTimeStrings(productiveDuration, addedTime)
                } else if (selectedCategoryName == "Temps d'écran") {
                    screenDuration = addTimeStrings(screenDuration, addedTime)
                }
                showAddTimeDialog = false
            }
        )
    }
}

@Composable
fun FlowSelector(
    selectedState: Int, // 1 = Lutte, 2 = Neutre, 3 = Flow
    onSelection: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Ressenti de la session", color = TextGray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // OPTION 1 : LUTTE
            FlowOptionItem(
                label = "LUTTE",
                icon = Icons.Default.Terrain, // Montagne = Dur
                color = NeonRed,
                isSelected = selectedState == 1,
                onClick = { onSelection(1) },
                modifier = Modifier.weight(1f)
            )

            // OPTION 2 : NEUTRE
            FlowOptionItem(
                label = "NEUTRE",
                icon = Icons.Default.HorizontalRule,
                color = TextGray,
                isSelected = selectedState == 2, // ou tout autre valeur par défaut
                onClick = { onSelection(2) },
                modifier = Modifier.weight(1f)
            )

            // OPTION 3 : FLOW
            FlowOptionItem(
                label = "FLOW",
                icon = Icons.Default.Bolt,
                color = NeonCyan,
                isSelected = selectedState >= 3, // Prend en compte les anciennes valeurs élevées (7,8,9,10)
                onClick = { onSelection(3) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun FlowOptionItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) color.copy(alpha = 0.2f) else Color.Transparent
    val borderColor = if (isSelected) color else Color.DarkGray

    Box(
        modifier = modifier
            .height(60.dp)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = icon, contentDescription = null, tint = if (isSelected) color else Color.Gray, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(text = label, color = if (isSelected) Color.White else Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AlarmWakeUpSelector(
    selectedState: Boolean?, // true = Réveil, false = Naturel, null = Rien
    onSelection: (Boolean?) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Type de réveil", color = TextGray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // OPTION 1 : SANS RÉVEIL (Naturel) -> Vert
            FlowOptionItem(
                label = "NATUREL",
                icon = Icons.Default.WbSunny,
                color = NeonGreen,
                isSelected = selectedState == false,
                onClick = { onSelection(false) },
                modifier = Modifier.weight(1f)
            )

            // OPTION 2 : AVEC RÉVEIL (Alarme) -> Rouge/Orange
            FlowOptionItem(
                label = "ALARME",
                icon = Icons.Default.Alarm,
                color = NeonOrange,
                isSelected = selectedState == true,
                onClick = { onSelection(true) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun CodexDialog(onDismiss: () -> Unit) {
    NeonHudDialog(
        title = "CODEX TACTIQUE",
        onDismiss = onDismiss,
        onConfirm = onDismiss,
        confirmLabel = "COMPRIS"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 450.dp) // Limite la hauteur pour le scroll
                .verticalScroll(rememberScrollState())
        ) {
            CodexSectionTitle("INTÉGRITÉ PHYSIQUE (HP)", NeonCyan)
            CodexRow("Nutrition > 8/10", "+${AvatarConstants.HP_GAIN_NUTRITION_EXCELLENT} HP", NeonGreen)
            CodexRow("Nutrition < 4/10", "${AvatarConstants.HP_LOSS_NUTRITION_CRITICAL} HP", NeonRed)
            CodexRow("Pas > 12 000", "+${AvatarConstants.HP_GAIN_STEPS_ATHLETE} HP", NeonGreen)
            CodexRow("Pas < 4 000", "${AvatarConstants.HP_LOSS_STEPS_CRITICAL} HP", NeonRed)
            CodexRow("Bonus Consistance", "+${AvatarConstants.HP_BONUS_CONSISTENCY} HP", NeonCyan)

            Spacer(Modifier.height(20.dp))

            CodexSectionTitle("ÉNERGIE OPÉRATIONNELLE (SP)", ProdViolet)
            CodexRow("Sommeil > 8h30", "+${AvatarConstants.SP_GAIN_SLEEP_DEEP} SP", NeonGreen)
            CodexRow("Sommeil < 5h", "+${AvatarConstants.SP_GAIN_SLEEP_SURVIVAL} SP", NeonOrange)
            CodexRow("Écran > 5h", "${AvatarConstants.SP_LOSS_SCREEN_CRITICAL} SP", NeonRed)
            CodexRow("Mode FLOW (1h)", "-${AvatarConstants.SP_COST_FLOW} SP", NeonGreen)
            CodexRow("Mode LUTTE (1h)", "-${AvatarConstants.SP_COST_STRUGGLE} SP", NeonRed)

            Spacer(Modifier.height(20.dp))

            // La Règle d'Or
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NeonRed.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .border(1.dp, NeonRed.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "⚠️ RÈGLE D'OR : Ta Stamina Max est limitée par tes HP actuels. Soigne ton corps pour augmenter ta batterie.",
                    color = NeonRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(100.dp)) // Pour voir le fond derrière les boutons
        }
    }
}

@Composable
fun CodexSectionTitle(title: String, color: Color) {
    Text(
        text = title,
        color = color,
        fontSize = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun CodexRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        Text(text = value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopywritingTacticalBoard(
    client: String?,
    onClientChange: (String) -> Unit,
    missionType: String?,
    onMissionChange: (String, String) -> Unit,
    phase: String?,
    onPhaseChange: (String) -> Unit,
    // --- NOUVEAUX PARAMÈTRES ---
    format: String?, // "Lecture" ou "Vidéo"
    onFormatChange: (String) -> Unit,
    // ---------------------------
    volume: String,
    onVolumeChange: (String) -> Unit
) {
    val context = LocalContext.current
    val manager = remember { CopywritingConfigManager(context) }
    val clientsList = remember { manager.getClients() }
    val assetsList = remember { manager.getAssets() }
    var clientExpanded by remember { mutableStateOf(false) }
    var missionExpanded by remember { mutableStateOf(false) }
    val currentUnit = assetsList.find { it.name == missionType }?.unit ?: "Quantité"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
            .padding(16.dp)
    ) {
        Text(
            "MATRICE TACTIQUE",
            color = Color.Gray,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // --- 1. LES PILIERS ---
        val pillars = listOf("Écriture", "Analyse", "Formation")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            pillars.forEach { p ->
                val isSelected = phase == p
                val pillColor = when(p) {
                    "Formation" -> Color(0xFFFFD700) // Jaune
                    "Analyse" -> Color(0xFF9D00FF)   // Violet
                    else -> Color(0xFF00E5FF)        // Cyan
                }

                val finalBorderColor = if (isSelected) pillColor else Color.DarkGray
                val finalTextColor = if (isSelected) pillColor else Color.Gray

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .border(1.dp, finalBorderColor, RoundedCornerShape(8.dp))
                        .background(
                            if(isSelected) finalBorderColor.copy(alpha=0.15f) else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onPhaseChange(p) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = p.uppercase(),
                        color = finalTextColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // --- 1b. LE SWITCH (VISIBLE SEULEMENT SI FORMATION) ---
        if (phase == "Formation") {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Lecture", "Vidéo").forEach { f ->
                    val isFormatSelected = format == f
                    val formatColor = Color(0xFFFFD700) // Jaune (Thème Formation)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp) // Un peu plus petit que les piliers
                            .border(1.dp, if(isFormatSelected) formatColor else Color.DarkGray, RoundedCornerShape(4.dp))
                            .background(
                                if(isFormatSelected) formatColor else Color.Transparent,
                                RoundedCornerShape(4.dp)
                            )
                            .clickable { onFormatChange(f) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (f == "Lecture") "📖" else "📺",
                                fontSize = 10.sp,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text(
                                text = f.uppercase(),
                                color = if(isFormatSelected) Color.Black else Color.Gray, // Texte noir si sélectionné (sur fond jaune)
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        // -----------------------------------------------------

        Spacer(Modifier.height(16.dp))

        // --- 2. L'ASSET ---
        ExposedDropdownMenuBox(
            expanded = missionExpanded,
            onExpandedChange = { missionExpanded = !missionExpanded }
        ) {
            OutlinedTextField(
                value = missionType ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Catégorie") },
                placeholder = { Text("Ex: Email, Page de vente...") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = missionExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.Gray
                )
            )
            ExposedDropdownMenu(
                expanded = missionExpanded,
                onDismissRequest = { missionExpanded = false }
            ) {
                assetsList.forEach { asset ->
                    DropdownMenuItem(
                        text = { Text(asset.name) },
                        onClick = {
                            onMissionChange(asset.name, asset.unit)
                            missionExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // --- 3. LE SUJET / ENTITÉ ---
        var suggestionsExpanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = suggestionsExpanded,
            onExpandedChange = { suggestionsExpanded = !suggestionsExpanded }
        ) {
            OutlinedTextField(
                value = client ?: "",
                onValueChange = {
                    onClientChange(it)
                    suggestionsExpanded = true
                },
                label = { Text("Sujet / Entité / Marque") },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = suggestionsExpanded) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.DarkGray
                )
            )

            if (clientsList.isNotEmpty()) {
                ExposedDropdownMenu(
                    expanded = suggestionsExpanded,
                    onDismissRequest = { suggestionsExpanded = false }
                ) {
                    clientsList.forEach { suggestion ->
                        DropdownMenuItem(
                            text = { Text(suggestion) },
                            onClick = {
                                onClientChange(suggestion)
                                suggestionsExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- 4. VOLUME (On garde tel quel pour l'instant) ---
        OutlinedTextField(
            value = volume,
            onValueChange = onVolumeChange,
            label = { Text("Volume ($currentUnit)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF00E5FF),
                unfocusedBorderColor = Color.Gray
            )
        )
    }
}

@Composable
fun NutritionInbox(
    items: List<RawNutritionItem>,
    onCommitMeal: (List<RawNutritionItem>, String) -> Unit
) {
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showMealSelector by remember { mutableStateOf(false) }

    if (items.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = NeonRed.copy(alpha = 0.15f)),
        border = BorderStroke(2.dp, NeonRed.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("NUTRITION EN ATTENTE", color = NeonRed, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(12.dp))

            items.forEach { item ->
                val isSelected = selectedIds.contains(item.id)
                Row(
                    Modifier.fillMaxWidth().clickable {
                        selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                    }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { selectedIds = if (it) selectedIds + item.id else selectedIds - item.id },
                        colors = CheckboxDefaults.colors(checkedColor = NeonRed)
                    )
                    Text(text = item.name, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(text = "${item.calories} kcal", color = TextGray, fontSize = 12.sp)
                }
            }

            if (selectedIds.isNotEmpty()) {
                Button(
                    onClick = { showMealSelector = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonRed)
                ) {
                    Text("DÉFINIR COMME REPAS (${selectedIds.size})", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showMealSelector) {
        NeonHudDialog(
            title = "TYPE DE REPAS",
            onDismiss = { showMealSelector = false },
            confirmLabel = "", // On utilise les boutons internes
            onConfirm = {}
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Petit Déjeuner", "Déjeuner", "Dîner", "Snack").forEach { type ->
                    Button(
                        onClick = {
                            val selectedItems = items.filter { selectedIds.contains(it.id) }
                            onCommitMeal(selectedItems, type)
                            selectedIds = emptySet()
                            showMealSelector = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        // --- DESIGN TACTIQUE ---
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E1E1E), // Fond presque noir
                            contentColor = Color.White          // Texte BLANC FORCE
                        ),
                        border = BorderStroke(1.dp, Color.Gray), // Petite bordure grise style "HUD"
                        shape = RoundedCornerShape(8.dp)
                        // -----------------------
                    ) {
                        Text(
                            text = type.uppercase(), // En majuscules pour faire plus "Pro"
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

// --- NOUVEAU COMPOSANT : SÉLECTEUR DE POSTURE ---
@Composable
fun PostureCyclicButton(
    currentPosture: String, // "Assis", "Debout", "Marche"
    onPostureChange: (String) -> Unit
) {
    // Définition des états
    val (icon, color, nextState) = when (currentPosture) {
        "Debout" -> Triple(Icons.Default.Accessibility, NeonYellow, "Marche") // Jaune
        "Marche" -> Triple(Icons.Default.DirectionsWalk, NeonGreen, "Assis")  // Vert
        else -> Triple(Icons.Default.EventSeat, Color.Gray, "Debout")         // "Assis" par défaut (Gris/Bleu)
    }

    IconButton(
        onClick = { onPostureChange(nextState) },
        modifier = Modifier
            .size(40.dp) // Taille du bouton
            .background(color.copy(alpha = 0.2f), CircleShape) // Fond léger
            .border(1.dp, color.copy(alpha = 0.5f), CircleShape) // Bordure fine
    ) {
        Icon(
            imageVector = icon,
            contentDescription = currentPosture,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
    }
}

// --- NOUVEAU COMPOSANT : SÉLECTEUR MUSCULAIRE (TACTICAL CHIPS) ---
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuscleTacticalSelector(
    selectedMuscles: List<String>,
    onSelectionChange: (List<String>) -> Unit
) {
    val muscles = listOf("Full Body", "Chest", "Back", "Legs", "Shoulders", "Biceps", "Triceps", "Abs")

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("CIBLE MUSCULAIRE", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            muscles.forEach { muscle ->
                val isSelected = selectedMuscles.contains(muscle)
                val color = if (isSelected) NeonOrange else Color.DarkGray

                Box(
                    modifier = Modifier
                        .border(1.dp, color, RoundedCornerShape(8.dp))
                        .background(if (isSelected) color.copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable {
                            // Logique basique : On ajoute ou on retire
                            val newList = if (isSelected) selectedMuscles - muscle else selectedMuscles + muscle
                            onSelectionChange(newList)
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = muscle.uppercase(),
                        color = if (isSelected) NeonOrange else Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// --- UTILITAIRE : ADDITIONNER DU TEMPS (HH:MM + HH:MM) ---
fun addTimeStrings(current: String, toAdd: String): String {
    // 1. On convertit l'actuel en minutes
    val currentDigits = current.filter { it.isDigit() }.padEnd(4, '0')
    val currentMins = (currentDigits.take(2).toInt() * 60) + currentDigits.takeLast(2).toInt()

    // 2. On convertit l'ajout en minutes
    val addDigits = toAdd.filter { it.isDigit() }.padEnd(4, '0')
    val addMins = (addDigits.take(2).toInt() * 60) + addDigits.takeLast(2).toInt()

    // 3. On additionne
    val totalMins = currentMins + addMins

    // 4. On reformate en String HH:MM
    val h = totalMins / 60
    val m = totalMins % 60
    return "%02d:%02d".format(h, m)
}

// --- COMPOSANT : POPUP AJOUT TEMPS ---
@Composable
fun AddTimePopup(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var timeToAdd by remember { mutableStateOf("") }

    NeonHudDialog(
        title = "AJOUTER DU TEMPS",
        onDismiss = onDismiss,
        confirmLabel = "AJOUTER",
        onConfirm = { onConfirm(timeToAdd) }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Combien de temps as-tu rajouté ?", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))

            // On réutilise ton champ de saisie existant
            DurationInputField(
                label = "Temps à ajouter (hh:mm)",
                value = timeToAdd,
                onValueChange = { timeToAdd = it }
            )
        }
    }
}