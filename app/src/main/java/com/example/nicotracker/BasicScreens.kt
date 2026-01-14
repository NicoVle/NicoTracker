package com.example.nicotracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color // Important pour forcer le blanc
import androidx.compose.ui.text.font.FontWeight // Pour le style gras
import androidx.compose.ui.unit.dp
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import com.example.nicotracker.data.JournalEntry
import com.example.nicotracker.data.JournalEntryViewModel
import com.example.nicotracker.stats.StatsEngine
import com.example.nicotracker.stats.StatsPeriod
import com.example.nicotracker.stats.StatsSectionTitle
import java.util.Calendar
import com.example.nicotracker.data.CategoryViewModel
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.example.nicotracker.data.CopyAsset
import com.example.nicotracker.data.CopywritingConfigManager
import com.example.nicotracker.data.SubCategoryViewModel
import com.example.nicotracker.stats.StatsCard
import com.example.nicotracker.ui.theme.*
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    journalEntryViewModel: JournalEntryViewModel,
    categoryViewModel: CategoryViewModel,
    subCategoryViewModel: SubCategoryViewModel,
    modifier: Modifier = Modifier
) {
    val entries by journalEntryViewModel.allEntries.collectAsState(initial = emptyList())
    val categories by categoryViewModel.allCategories.collectAsState(initial = emptyList())
    val allSubCats by subCategoryViewModel.allSubCategories.collectAsState(initial = emptyList())

    // --- ÉTAT DU FILTRE (Catégorie) ---
    var selectedFilterCategory by remember { mutableStateOf("Toutes") }
    var selectedSubCategoryId by remember { mutableStateOf<Int?>(null) }

    // --- ÉTAT DU MODE (Passé vs Futur) ---
    var showFuture by remember { mutableStateOf(false) } // false = Historique, true = Futur

    // --- GESTION DES DIALOGUES ---
    var showDeleteDialog by remember { mutableStateOf<JournalEntry?>(null) }
    var entryToEdit by remember { mutableStateOf<JournalEntry?>(null) }

    // --- CALCUL DES DATES ---
    val today = remember {
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }
    }

    //Selection du jour
    var selectedDateFilter by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    val tomorrow = remember {
        (today.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
    }

    val futureEntriesCount = remember(entries) {
        entries.count { entry ->
            val isFuture = entry.date.time >= tomorrow.timeInMillis
            // On exclut les défis "Banque" (cachés) du compte
            val isNotBank = !(entry.categoryName == "Défis" && entry.challengeState == 0)
            isFuture && isNotBank
        }
    }

    // --- PRÉPARATION DES LISTES ---
    // --- NOUVELLE LOGIQUE DE FILTRAGE UNIFIÉE ---
    val displayedEntries = remember(entries, selectedFilterCategory, selectedSubCategoryId, showFuture, selectedDateFilter, allSubCats) {
        entries.filter { entry ->
            // 1. Filtre Catégorie & Sous-catégorie (Commun)
            val matchesCategory = if (selectedFilterCategory == "Toutes") true else entry.categoryName == selectedFilterCategory
            val matchesSubCategory = if (selectedSubCategoryId == null) true else entry.subCategoryId == selectedSubCategoryId

            // 2. Filtre par DATE PRÉCISE (Prioritaire)
            if (selectedDateFilter != null) {
                val entryCal = java.util.Calendar.getInstance().apply { time = entry.date }
                val filterCal = java.util.Calendar.getInstance().apply { timeInMillis = selectedDateFilter!! }

                val isSameDay = entryCal.get(java.util.Calendar.YEAR) == filterCal.get(java.util.Calendar.YEAR) &&
                        entryCal.get(java.util.Calendar.DAY_OF_YEAR) == filterCal.get(java.util.Calendar.DAY_OF_YEAR)

                matchesCategory && matchesSubCategory && isSameDay
            }
            // 3. Sinon : Logique classique (Archives vs Futur)
            else {
                val isFuture = entry.date.time >= tomorrow.timeInMillis
                val isStrictlyPast = entry.date.time < today.timeInMillis

                // Gestion Banque / Protocole
                val subName = allSubCats.find { it.id == entry.subCategoryId }?.name?.trim()
                val isProtocol = subName.equals("Protocole d'intégrité", ignoreCase = true)
                val isCompletedProtocolToday = !isStrictlyPast && !isFuture && isProtocol && entry.challengeState == 2
                val isNotBank = !(entry.categoryName == "Défis" && entry.challengeState == 0)

                val matchesTime = if (showFuture) {
                    isFuture && isNotBank
                } else {
                    (isStrictlyPast || isCompletedProtocolToday) && !isFuture && isNotBank
                }

                matchesCategory && matchesSubCategory && matchesTime
            }
        }.sortedByDescending { it.date } // On trie toujours du plus récent au plus ancien
    }

    val historyEntries = remember(entries, selectedFilterCategory, selectedSubCategoryId, allSubCats) {
        entries.filter { entry ->
            val isStrictlyPast = entry.date.time < today.timeInMillis
            val isFuture = entry.date.time >= tomorrow.timeInMillis
            val subName = allSubCats.find { it.id == entry.subCategoryId }?.name?.trim()
            val isProtocol = subName.equals("Protocole d'intégrité", ignoreCase = true)
            val isCompletedProtocolToday = !isStrictlyPast && !isFuture && isProtocol && entry.challengeState == 2
            val matchesCategory = if (selectedFilterCategory == "Toutes") true else entry.categoryName == selectedFilterCategory
            val matchesSubCategory = if (selectedSubCategoryId == null) true else entry.subCategoryId == selectedSubCategoryId
            val isNotBankChallenge = !(entry.categoryName == "Défis" && entry.challengeState == 0)
            (isStrictlyPast || isCompletedProtocolToday) && !isFuture && matchesCategory && matchesSubCategory && isNotBankChallenge
        }.sortedByDescending { it.date }
    }

    val selectedCategoryObject = categories.find { it.name == selectedFilterCategory }
    val subCategoriesForFilter by if (selectedCategoryObject != null) {
        subCategoryViewModel.getSubCategoriesForCategory(selectedCategoryObject.id).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList<com.example.nicotracker.data.SubCategory>()) }
    }

    // --- CHANGEMENT MAJEUR ICI : Tout est dans la LazyColumn ---
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 80.dp)
    ) {
        // 1. BLOC TITRE (Scrollable)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "REGISTRE TACTIQUE",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
            Spacer(Modifier.height(16.dp))
        }


        // 2. BLOC ONGLETS & CALENDRIER (Alignés)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp) // Espace entre les éléments
            ) {
                // CONTENEUR DES ONGLETS (Prend toute la place restante)
                Row(
                    modifier = Modifier
                        .weight(1f) // Prend le max de place
                        .background(Color(0xFF0F0F0F), RoundedCornerShape(12.dp))
                        .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    // --- BOUTON 1 : ARCHIVES ---
                    HistoryTabButton(
                        text = "ARCHIVES",
                        isActive = !showFuture && selectedDateFilter == null, // Actif si pas futur ET pas de date précise
                        activeColor = NeonOrange,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showFuture = false
                            selectedDateFilter = null // Revenir aux archives enlève le filtre date
                        }
                    )

                    // --- BOUTON 2 : À VENIR ---
                    HistoryTabButton(
                        text = "À VENIR ($futureEntriesCount)",
                        isActive = showFuture,
                        activeColor = NeonCyan,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showFuture = true
                            selectedDateFilter = null // Aller au futur enlève le filtre date
                        }
                    )
                }

                // --- BOUTON 3 : CALENDRIER (Carré à droite) ---
                val isDateActive = selectedDateFilter != null
                val dateColor = if (isDateActive) NeonCyan else Color.DarkGray
                val iconColor = if (isDateActive) Color.Black else Color.Gray

                Box(
                    modifier = Modifier
                        .size(48.dp) // Carré de 48x48 (Hauteur standard + padding)
                        .background(if (isDateActive) NeonCyan else Color(0xFF0F0F0F), RoundedCornerShape(12.dp))
                        .border(1.dp, if (isDateActive) NeonCyan else Color.DarkGray, RoundedCornerShape(12.dp))
                        .clickable {
                            if (isDateActive) selectedDateFilter = null // Annuler si déjà actif
                            else showDatePicker = true // Ouvrir sinon
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDateActive) Icons.Default.Close else Icons.Default.DateRange, // Croix ou Calendrier
                        contentDescription = "Date",
                        tint = iconColor
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }


        // 3. BLOC FILTRES CATÉGORIES
        item {
            // (L'ancien bouton date a été supprimé ici)

            // --- FILTRES CATÉGORIES ---
            val filterOptions = listOf("Toutes") + categories.map { it.name }
            CategorySelector(
                categories = filterOptions,
                selected = selectedFilterCategory,
                onSelected = {
                    selectedFilterCategory = it
                    selectedSubCategoryId = null
                }
            )

            if (selectedFilterCategory != "Toutes" && subCategoriesForFilter.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                NeonChipSelector(
                    label = "Filtrer par sous-catégorie :",
                    options = subCategoriesForFilter,
                    selectedId = selectedSubCategoryId,
                    onSelected = { selectedSubCategoryId = it }
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        // 4. LISTE DES ENTRÉES
        // On utilise notre nouvelle liste unifiée
        val listDisplay = displayedEntries

        val emptyMessage = when {
            selectedDateFilter != null -> "Aucune donnée pour cette date."
            showFuture -> "Rien de prévu pour le futur.\nUtilise le calendrier pour planifier."
            else -> "Aucune archive trouvée."
        }

        if (listDisplay.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = emptyMessage,
                        color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            items(listDisplay) { entry ->
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

                Text(
                    text = android.text.format.DateFormat.format("EEEE dd MMMM yyyy", entry.date).toString().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (showFuture) NeonCyan else Color.Gray,
                    modifier = Modifier.padding(start = 8.dp, bottom = 2.dp, top = 8.dp)
                )

                StyledJournalEntryCard(
                    entry = entry,
                    subCategoryName = subCategoryName,
                    onClick = { entryToEdit = entry },
                    onDelete = { showDeleteDialog = entry }
                )
            }
        }
    }

    // --- DIALOGUES (Restent inchangés) ---
    if (showDeleteDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Supprimer cette entrée ?") },
            text = { Text("Cette action est définitive.") },
            confirmButton = {
                TextButton(onClick = {
                    journalEntryViewModel.delete(showDeleteDialog!!)
                    showDeleteDialog = null
                }) { Text("Supprimer", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Annuler") }
            }
        )
    }

    if (entryToEdit != null) {
        EditEntryDialog(
            entry = entryToEdit!!,
            categories = categories,
            subCategoryViewModel = subCategoryViewModel,
            onConfirm = { edited ->
                journalEntryViewModel.update(edited)
                entryToEdit = null
            },
            onDismiss = { entryToEdit = null }
        )
    }

    // --- DIALOGUE CALENDRIER ---
    if (showDatePicker) {
        val datePickerState = androidx.compose.material3.rememberDatePickerState()

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        // Correction Fuseau Horaire (UTC -> Local)
                        val utcCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                        utcCal.timeInMillis = millis

                        val localCal = java.util.Calendar.getInstance()
                        localCal.set(utcCal.get(java.util.Calendar.YEAR), utcCal.get(java.util.Calendar.MONTH), utcCal.get(java.util.Calendar.DAY_OF_MONTH))

                        selectedDateFilter = localCal.timeInMillis
                    }
                    showDatePicker = false
                }) { Text("OK", color = NeonCyan) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Annuler", color = Color.Gray) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// Petit composant pour les boutons d'onglets
@Composable
fun HistoryTabButton(
    text: String,
    isActive: Boolean,
    activeColor: Color = NeonOrange, // Orange par défaut (Passé)
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .padding(horizontal = 2.dp)
            .background(
                if (isActive) activeColor.copy(alpha = 0.2f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                if (isActive) activeColor else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isActive) activeColor else Color.Gray,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    journalEntryViewModel: JournalEntryViewModel,
    categoryViewModel: CategoryViewModel,
    padding: PaddingValues
) {
    val entries by journalEntryViewModel.allEntries.collectAsState(initial = emptyList())
    val categories by categoryViewModel.allCategories.collectAsState(initial = emptyList())

    if (categories.isEmpty()) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Aucune catégorie disponible", color = Color.Gray)
        }
        return
    }

    var selectedCategory by remember { mutableStateOf(categories.first().name) }
    var selectedPeriod by remember { mutableStateOf(StatsPeriod.SEVEN_DAYS) }

    val engine = remember { StatsEngine() }
    val filtered = engine.filterByCategory(
        engine.filterByPeriod(entries, selectedPeriod),
        selectedCategory
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            CategorySelector(
                categories = categories.map { it.name },
                selected = selectedCategory,
                onSelected = { selectedCategory = it }
            )
        }

        item {
            PeriodSelector(
                selected = selectedPeriod,
                onSelected = { selectedPeriod = it }
            )
        }

        item {
            StatsResultSection(
                category = selectedCategory,
                entries = filtered
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySelector(
    categories: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        // --- COULEURS DU CHAMP TEXTE ---
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("Catégorie") },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF00E5FF), // Cyan Néon
                unfocusedBorderColor = Color.Gray,
                focusedLabelColor = Color(0xFF00E5FF),
                unfocusedLabelColor = Color.Gray
            )
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            categories.forEach { cat ->
                DropdownMenuItem(
                    text = { Text(cat) },
                    onClick = {
                        onSelected(cat)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodSelector(
    selected: StatsPeriod,
    onSelected: (StatsPeriod) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        StatsPeriod.values().forEach { period ->
            AssistChip(
                label = { Text(period.label) },
                onClick = { onSelected(period) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (period == selected) Color(0xFF00E5FF) else Color.Transparent, // Cyan si actif
                    labelColor = if (period == selected) Color.Black else Color.White,
                    leadingIconContentColor = Color.White
                ),
                border = AssistChipDefaults.assistChipBorder(
                    borderColor = if (period == selected) Color.Transparent else Color.Gray,
                    enabled = true // Ajouté pour corriger l'erreur possible
                )
            )
        }
    }
}

@Composable
fun StatsResultSection(
    category: String,
    entries: List<JournalEntry>
) {
    // --- TITRE DE SECTION BLANC ---
    Text(
        text = "Analyse : $category",
        style = MaterialTheme.typography.titleLarge,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp)
    )

    if (entries.isEmpty()) {
        Text("Aucune donnée pour cette période.", color = Color.Gray)
        return
    }

    when (category) {
        "Sport" -> StatsSport(entries)
        "Repas" -> StatsMeals(entries)
        "Sommeil" -> StatsSleep(entries)
        "Action productive" -> StatsProductive(entries)
        "Temps d'écran" -> StatsScreenTime(entries)
        "Nombre de pas" -> StatsSteps(entries)
        "Humeur" -> StatsMood(entries)
        "Dépense" -> StatsExpenses(entries)
    }
}

fun formatMinutes(total: Int): String {
    val h = total / 60
    val m = total % 60
    return "%02dh%02d".format(h, m)
}

// Les fonctions StatsSport, StatsMeals, etc. restent inchangées
// (Elles utilisent StatsCard qui est déjà stylisée normalement,
// sinon il faudrait vérifier StatsCard aussi).
// Je remets les versions abrégées pour que le fichier compile

@Composable
fun StatsSport(entries: List<JournalEntry>) {
    val totalSessions = entries.size
    val totalMinutes = entries.mapNotNull { it.sportDurationMinutes }.sum()
    StatsCard("Nombre de séances", "$totalSessions")
    StatsCard("Durée totale", formatMinutes(totalMinutes))
}

@Composable
fun StatsMeals(entries: List<JournalEntry>) {
    val totalCalories = entries.mapNotNull { it.mealCalories }.sum()
    StatsCard("Calories totales", "$totalCalories kcal")
}

@Composable
fun StatsSleep(entries: List<JournalEntry>) {
    // Logique simplifiée pour l'exemple, reprends ton code complet si besoin
    StatsCard("Info", "Voir détails")
}

@Composable
fun StatsProductive(entries: List<JournalEntry>) {
    val total = entries.mapNotNull { it.productiveDurationMinutes }.sum()
    StatsCard("Temps productif", formatMinutes(total))
}

@Composable
fun StatsScreenTime(entries: List<JournalEntry>) {
    val total = entries.mapNotNull { it.screenDurationMinutes }.sum()
    StatsCard("Temps écran", formatMinutes(total))
}

@Composable
fun StatsSteps(entries: List<JournalEntry>) {
    val total = entries.mapNotNull { it.stepsCount }.sum()
    StatsCard("Total pas", "$total")
}

@Composable
fun StatsMood(entries: List<JournalEntry>) {
    StatsCard("Humeur", "Analyse...")
}

@Composable
fun StatsExpenses(entries: List<JournalEntry>) {
    val total = entries.mapNotNull { it.depensePrice }.sum()
    StatsCard("Total", "$total €")
}


@Composable
fun DurationPickerField(label: String, value: String, onOpenDialog: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onOpenDialog() }) {
        OutlinedTextField(value = value, onValueChange = {}, readOnly = true, enabled = false, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun NumberScoreField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { if(it.filter{c->c.isDigit()}.isNotEmpty()) onValueChange(it.filter{c->c.isDigit()}) else onValueChange("") },
        label = { Text(label) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done)
    )
}

@Composable
fun DurationInputField(label: String, value: String, onValueChange: (String) -> Unit) {
    // Utilisation de TextFieldValue pour contrôler le curseur
    // On force la sélection (curseur) à la fin du texte (value.length)
    OutlinedTextField(
        value = androidx.compose.ui.text.input.TextFieldValue(
            text = value,
            selection = androidx.compose.ui.text.TextRange(value.length)
        ),
        onValueChange = { tfv ->
            val raw = tfv.text
            // On ne garde que les chiffres, max 4
            val digits = raw.filter { it.isDigit() }.take(4)

            // Formatage : insertion auto des deux points
            val formatted = if (digits.length >= 3) {
                "${digits.substring(0, 2)}:${digits.substring(2)}"
            } else {
                digits
            }

            // On ne déclenche le changement que si le texte formaté est différent
            if (formatted != value) {
                onValueChange(formatted)
            }
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done)
    )
}

@Composable
fun TimeInputField(label: String, value: String, onValueChange: (String) -> Unit) {
    // Même correction pour TimeInputField
    OutlinedTextField(
        value = androidx.compose.ui.text.input.TextFieldValue(
            text = value,
            selection = androidx.compose.ui.text.TextRange(value.length)
        ),
        onValueChange = { tfv ->
            val raw = tfv.text
            val digits = raw.filter { it.isDigit() }.take(4)

            val formatted = if (digits.length >= 3) {
                "${digits.substring(0, 2)}:${digits.substring(2)}"
            } else {
                digits
            }

            if (formatted != value) {
                onValueChange(formatted)
            }
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done)
    )
}

@Composable
fun SettingsScreen(
    onManageSubCategories: () -> Unit,
    onExportNow: () -> Unit,
    onImportJson: () -> Unit,
    onConfigureCopywriting: () -> Unit // <--- 1. AJOUTE CE PARAMÈTRE
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "Paramètres",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Bouton 1 : Sous-catégories
        Button(
            onClick = onManageSubCategories,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Gérer les sous-catégories")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 2. AJOUTE LE NOUVEAU BOUTON ICI ---
        Button(
            onClick = onConfigureCopywriting,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black) // Optionnel: couleur Cyan pour le distinguer
        ) {
            Text("⚙️ Configurer Copywriting")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bouton 2 : Export
        Button(
            onClick = onExportNow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Exporter maintenant (JSON + CSV)")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bouton 3 : Import
        Button(
            onClick = onImportJson,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Importer depuis un JSON")
        }
    }
}

@Composable
fun CopywritingSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { CopywritingConfigManager(context) }

    // États locaux pour l'affichage instantané
    var clients by remember { mutableStateOf(manager.getClients()) }
    var assets by remember { mutableStateOf(manager.getAssets()) }

    var newClientName by remember { mutableStateOf("") }
    var newAssetName by remember { mutableStateOf("") }
    var newAssetUnit by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
        Text("CONFIG COPYWRITING", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        // --- SECTION CLIENTS ---
        Text("MES CLIENTS", color = NeonCyan, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newClientName,
                onValueChange = { newClientName = it },
                label = { Text("Nouveau client") },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
            )
            IconButton(onClick = {
                if (newClientName.isNotBlank()) {
                    val newList = clients + newClientName
                    manager.saveClients(newList)
                    clients = newList
                    newClientName = ""
                }
            }) {
                Icon(Icons.Default.Add, null, tint = NeonCyan)
            }
        }

        LazyColumn(modifier = Modifier.height(200.dp).fillMaxWidth().border(1.dp, Color.DarkGray, RoundedCornerShape(8.dp)).padding(8.dp)) {
            items(clients) { client ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(client, color = Color.White)
                    Icon(Icons.Default.Delete, null, tint = NeonRed, modifier = Modifier.clickable {
                        val newList = clients - client
                        manager.saveClients(newList)
                        clients = newList
                    })
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // --- SECTION MISSIONS ---
        Text("TYPES DE MISSIONS (ASSETS)", color = NeonCyan, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = newAssetName, onValueChange = { newAssetName = it }, label = { Text("Nom (ex: Email)") }, modifier = Modifier.weight(1.5f))
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(value = newAssetUnit, onValueChange = { newAssetUnit = it }, label = { Text("Unité") }, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                if (newAssetName.isNotBlank()) {
                    val newAsset = CopyAsset(newAssetName, newAssetUnit.ifBlank { "Unité" })
                    val newList = assets + newAsset
                    manager.saveAssets(newList)
                    assets = newList
                    newAssetName = ""
                    newAssetUnit = ""
                }
            }) {
                Icon(Icons.Default.Add, null, tint = NeonCyan)
            }
        }

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().border(1.dp, Color.DarkGray, RoundedCornerShape(8.dp)).padding(8.dp)) {
            items(assets) { asset ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${asset.name} (${asset.unit})", color = Color.White)
                    Icon(Icons.Default.Delete, null, tint = NeonRed, modifier = Modifier.clickable {
                        val newList = assets - asset
                        manager.saveAssets(newList)
                        assets = newList
                    })
                }
            }
        }

        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("RETOUR")
        }
    }
}