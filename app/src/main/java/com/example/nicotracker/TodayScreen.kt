package com.example.nicotracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.nicotracker.data.*
import kotlinx.coroutines.flow.collectLatest

@Composable
fun TodayScreen(
    categoryViewModel: CategoryViewModel,
    journalEntryViewModel: JournalEntryViewModel,
    subCategoryViewModel: SubCategoryViewModel,
    showAddDialog: Boolean,
    onDismissDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categories by categoryViewModel.allCategories.collectAsState(initial = emptyList())
    val entries by journalEntryViewModel.allEntries.collectAsState(initial = emptyList())

    var showDeleteDialog by remember { mutableStateOf<JournalEntry?>(null) }


    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            text = "Entrées du jour (${entries.size})",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(16.dp))

        LazyColumn {
            items(entries) { entry ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Catégorie : ${entry.categoryName}")
                            Text("Commentaire : ${entry.comment ?: "-"}")
                            var subCategoryName by remember { mutableStateOf<String?>(null) }

                            LaunchedEffect(entry.subCategoryId) {
                                if (entry.subCategoryId != null) {
                                    subCategoryViewModel.getSubCategoryName(entry.subCategoryId!!) {
                                        subCategoryName = it
                                    }
                                }
                            }

                            Text("Sous-catégorie : ${subCategoryName ?: "-"}")

                        }

                        IconButton(onClick = { showDeleteDialog = entry }) {
                            Icon(Icons.Default.Delete, contentDescription = "Supprimer")
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Supprimer l’entrée ?") },
            text = { Text("Cette action est définitive.") },
            confirmButton = {
                TextButton(onClick = {
                    journalEntryViewModel.delete(showDeleteDialog!!)
                    showDeleteDialog = null
                }) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Annuler")
                }
            }
        )
    }


    if (showAddDialog && categories.isNotEmpty()) {
        AddEntryDialog(
            categories = categories,
            subCategoryViewModel = subCategoryViewModel,
            onConfirm = {
                journalEntryViewModel.insert(it)
                onDismissDialog()
            },
            onDismiss = onDismissDialog
        )
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

    // Champs fixes par type
    var sportDuration by remember { mutableStateOf("") }
    var sportIntensity by remember { mutableStateOf("") }

    var mealCalories by remember { mutableStateOf("") }
    var mealQuality by remember { mutableStateOf("") }

    var sleepBedTime by remember { mutableStateOf("") }
    var sleepWakeTime by remember { mutableStateOf("") }
    var sleepDuration by remember { mutableStateOf("") }
    var sleepQuality by remember { mutableStateOf("") }

    var productiveDuration by remember { mutableStateOf("") }
    var productiveFocus by remember { mutableStateOf("") }

    var screenDuration by remember { mutableStateOf("") }

    var selectedDate by remember { mutableStateOf(java.util.Date()) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.time
    )

    var showDatePicker by remember { mutableStateOf(false) }


    var stepsCount by remember { mutableStateOf("") }

    var moodScore by remember { mutableStateOf("") }

    var comment by remember { mutableStateOf("") }

    // Sous-catégories
    val selectedCategory = categories.first { it.name == selectedCategoryName }
    val subCategories by subCategoryViewModel
        .getSubCategoriesForCategory(selectedCategory.id)
        .collectAsState(initial = emptyList())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter une entrée") },
        text = {
            Column {

                // ------- DATE -------
                TextButton(
                    onClick = { showDatePicker = true }
                ) {
                    Text("Date : " + android.text.format.DateFormat.format("dd/MM/yyyy", selectedDate))
                }

                Spacer(Modifier.height(12.dp))

                // Catégorie
                CategoryDropdown(
                    categories = categories,
                    selectedCategoryName = selectedCategoryName,
                    onCategorySelected = {
                        selectedCategoryName = it
                        selectedSubCategoryId = null
                    }
                )

                Spacer(Modifier.height(8.dp))

                // Sous-catégorie
                SubCategoryDropdown(
                    subCategories = subCategories,
                    selectedSubCategoryId = selectedSubCategoryId,
                    onSelected = { selectedSubCategoryId = it }
                )

                Spacer(Modifier.height(12.dp))

                // ---------- SPORT ----------
                if (selectedCategoryName == "Sport") {
                    OutlinedTextField(
                        value = sportDuration,
                        onValueChange = { sportDuration = it },
                        label = { Text("Durée (minutes)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = sportIntensity,
                        onValueChange = { sportIntensity = it },
                        label = { Text("Intensité (1–10)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ---------- REPAS ----------
                if (selectedCategoryName == "Repas") {
                    OutlinedTextField(
                        value = mealCalories,
                        onValueChange = { mealCalories = it },
                        label = { Text("kcal") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = mealQuality,
                        onValueChange = { mealQuality = it },
                        label = { Text("Qualité (1–10)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ---------- SOMMEIL ----------
                if (selectedCategoryName == "Sommeil") {
                    OutlinedTextField(
                        value = sleepBedTime,
                        onValueChange = { sleepBedTime = it },
                        label = { Text("Heure de couché (hh:mm)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = sleepWakeTime,
                        onValueChange = { sleepWakeTime = it },
                        label = { Text("Heure de réveil (hh:mm)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = sleepDuration,
                        onValueChange = { sleepDuration = it },
                        label = { Text("Durée du sommeil (ex: 7h45)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = sleepQuality,
                        onValueChange = { sleepQuality = it },
                        label = { Text("Qualité du sommeil (1–10)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ---------- ACTION PRODUCTIVE ----------
                if (selectedCategoryName == "Action productive") {
                    OutlinedTextField(
                        value = productiveDuration,
                        onValueChange = { productiveDuration = it },
                        label = { Text("Durée (minutes)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = productiveFocus,
                        onValueChange = { productiveFocus = it },
                        label = { Text("Focus (1–10)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ---------- TEMPS D'ÉCRAN ----------
                if (selectedCategoryName == "Temps d'écran") {
                    OutlinedTextField(
                        value = screenDuration,
                        onValueChange = { screenDuration = it },
                        label = { Text("Durée (minutes)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ---------- NOMBRE DE PAS ----------
                if (selectedCategoryName == "Nombre de pas") {
                    OutlinedTextField(
                        value = stepsCount,
                        onValueChange = { stepsCount = it },
                        label = { Text("Nombre de pas") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ---------- HUMEUR ----------
                if (selectedCategoryName == "Humeur") {
                    OutlinedTextField(
                        value = moodScore,
                        onValueChange = { moodScore = it },
                        label = { Text("Note (1–10)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Commentaire (commun)
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Commentaire") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {

                    val entry = JournalEntry(
                        categoryName = selectedCategoryName,
                        subCategoryId = selectedSubCategoryId,

                        date = selectedDate,

                        sportDurationMinutes = sportDuration.toIntOrNull(),
                        sportIntensity = sportIntensity.toIntOrNull(),

                        mealCalories = mealCalories.toIntOrNull(),
                        mealQuality = mealQuality.toIntOrNull(),

                        sleepBedTime = sleepBedTime.ifBlank { null },
                        sleepWakeTime = sleepWakeTime.ifBlank { null },
                        sleepDuration = sleepDuration.ifBlank { null },
                        sleepQuality = sleepQuality.toIntOrNull(),

                        productiveDurationMinutes = productiveDuration.toIntOrNull(),
                        productiveFocus = productiveFocus.toIntOrNull(),

                        screenDurationMinutes = screenDuration.toIntOrNull(),

                        stepsCount = stepsCount.toIntOrNull(),

                        moodScore = moodScore.toIntOrNull(),

                        comment = comment.ifBlank { null }
                    )

                    onConfirm(entry)
                }
            ) {
                Text("Ajouter")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = datePickerState.selectedDateMillis
                        if (millis != null) selectedDate = java.util.Date(millis)
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

}
